package com.gp.slotsync.e2e.base;

import com.aventstack.extentreports.ExtentTest;
import com.microsoft.playwright.*;
import com.gp.slotsync.e2e.config.ConfigReader;
import com.gp.slotsync.e2e.utils.ExtentReportManager;
import com.gp.slotsync.e2e.utils.EmbeddedServerManager;
import org.testng.ITestResult;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeSuite;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Base test class managing Playwright instances, browser contexts,
 * page creation, tracing, and video recording lifecycles.
 */
public abstract class BaseTest {

    private static final ThreadLocal<Playwright> tlPlaywright = new ThreadLocal<>();
    private static final ThreadLocal<Browser> tlBrowser = new ThreadLocal<>();
    private static final ThreadLocal<BrowserContext> tlContext = new ThreadLocal<>();
    private static final ThreadLocal<Page> tlPage = new ThreadLocal<>();
    private static final ThreadLocal<Path> tlVideoPath = new ThreadLocal<>();
    private static final ThreadLocal<Path> tlTracePath = new ThreadLocal<>();

    @BeforeSuite(alwaysRun = true)
    public void setupSuite() {
        // Ensure recording and reports directories exist
        new File(ConfigReader.getVideoDir()).mkdirs();
        new File(ConfigReader.getTraceDir()).mkdirs();
        new File(ConfigReader.getReportDir()).mkdirs();
        new File(ConfigReader.getScreenshotDir()).mkdirs();

        // Start embedded server if port 8088 is not already occupied by running backend
        EmbeddedServerManager.startIfNotRunning(8088);
    }

    @AfterSuite(alwaysRun = true)
    public void tearDownSuite() {
        EmbeddedServerManager.stopIfStarted();
    }

    @BeforeMethod(alwaysRun = true)
    public void setUp(Method method) {
        Map<String, String> env = new HashMap<>();
        env.put("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1");
        Playwright playwright = Playwright.create(new Playwright.CreateOptions().setEnv(env));
        tlPlaywright.set(playwright);

        String browserName = ConfigReader.getBrowser();
        BrowserType browserType;
        switch (browserName) {
            case "firefox":
                browserType = playwright.firefox();
                break;
            case "webkit":
                browserType = playwright.webkit();
                break;
            case "chromium":
            default:
                browserType = playwright.chromium();
                break;
        }

        BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions()
                .setHeadless(ConfigReader.isHeadless());
        String channel = ConfigReader.getChannel();
        if (channel != null && !channel.trim().isEmpty() && !channel.equalsIgnoreCase("none")) {
            launchOptions.setChannel(channel.trim());
        }

        Browser browser = browserType.launch(launchOptions);
        tlBrowser.set(browser);

        Browser.NewContextOptions contextOptions = new Browser.NewContextOptions()
                .setViewportSize(1280, 720);

        if (ConfigReader.isRecordVideo()) {
            contextOptions.setRecordVideoDir(Paths.get(ConfigReader.getVideoDir()))
                          .setRecordVideoSize(1280, 720);
        }

        BrowserContext context = browser.newContext(contextOptions);
        context.setDefaultTimeout(ConfigReader.getTimeout());
        tlContext.set(context);

        if (ConfigReader.isRecordTrace()) {
            context.tracing().start(new Tracing.StartOptions()
                    .setScreenshots(true)
                    .setSnapshots(true)
                    .setSources(true));
        }

        Page page = context.newPage();
        tlPage.set(page);
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown(Method method, ITestResult result) {
        Page page = tlPage.get();
        BrowserContext context = tlContext.get();
        Browser browser = tlBrowser.get();
        Playwright playwright = tlPlaywright.get();

        Video video = (page != null && !page.isClosed()) ? page.video() : null;

        String testName = method.getName();
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));

        // Finalize Tracing if enabled
        if (context != null && ConfigReader.isRecordTrace()) {
            try {
                Path tracePath = Paths.get(ConfigReader.getTraceDir(), testName + "_" + timestamp + ".zip");
                context.tracing().stop(new Tracing.StopOptions().setPath(tracePath));
                tlTracePath.set(tracePath);
                
                ExtentTest extentTest = ExtentReportManager.getTest();
                if (extentTest != null) {
                    extentTest.info("Playwright Trace captured: " + tracePath.getFileName());
                }
            } catch (Exception e) {
                System.err.println("Failed to save Playwright trace: " + e.getMessage());
            }
        }

        // Close page and context to finalize video recording
        if (page != null && !page.isClosed()) {
            page.close();
        }

        if (context != null) {
            context.close();
        }

        // Inspect finalized video path
        if (video != null) {
            try {
                Path videoPath = video.path();
                tlVideoPath.set(videoPath);
                ExtentTest extentTest = ExtentReportManager.getTest();
                if (extentTest != null && videoPath != null) {
                    extentTest.info("Video Recording saved: " + videoPath.getFileName());
                }
            } catch (Exception e) {
                System.err.println("Could not resolve video path: " + e.getMessage());
            }
        }

        if (browser != null) {
            browser.close();
        }

        if (playwright != null) {
            playwright.close();
        }

        tlPage.remove();
        tlContext.remove();
        tlBrowser.remove();
        tlPlaywright.remove();
    }

    public static Page getPage() {
        return tlPage.get();
    }

    public static BrowserContext getContext() {
        return tlContext.get();
    }

    public static Path getVideoPath() {
        return tlVideoPath.get();
    }

    public static Path getTracePath() {
        return tlTracePath.get();
    }
}
