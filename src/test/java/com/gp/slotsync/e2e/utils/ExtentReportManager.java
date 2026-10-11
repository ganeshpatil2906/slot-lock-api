package com.gp.slotsync.e2e.utils;

import com.aventstack.extentreports.ExtentReports;
import com.aventstack.extentreports.ExtentTest;
import com.aventstack.extentreports.reporter.ExtentSparkReporter;
import com.aventstack.extentreports.reporter.configuration.Theme;
import com.gp.slotsync.e2e.config.ConfigReader;

import java.io.File;
import java.nio.file.Paths;

/**
 * Manages ExtentReports lifecycle and ThreadLocal ExtentTest instances
 * for thread-safe parallel and sequential execution reporting.
 */
public class ExtentReportManager {

    private static ExtentReports extent;
    private static final ThreadLocal<ExtentTest> testThreadLocal = new ThreadLocal<>();

    public static synchronized ExtentReports getReporter() {
        if (extent == null) {
            String reportDir = ConfigReader.getReportDir();
            File dir = new File(reportDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            String reportFilePath = Paths.get(reportDir, "ExtentReport.html").toString();
            ExtentSparkReporter sparkReporter = new ExtentSparkReporter(reportFilePath);
            sparkReporter.config().setDocumentTitle("SlotSync E2E Test Report");
            sparkReporter.config().setReportName("SlotSync Automation Suite Execution");
            sparkReporter.config().setTheme(Theme.STANDARD);
            sparkReporter.config().setTimeStampFormat("MMM dd, yyyy HH:mm:ss");

            extent = new ExtentReports();
            extent.attachReporter(sparkReporter);
            extent.setSystemInfo("Application", "SlotSync");
            extent.setSystemInfo("Environment", "Local / Test");
            extent.setSystemInfo("User", System.getProperty("user.name"));
            extent.setSystemInfo("OS", System.getProperty("os.name"));
            extent.setSystemInfo("Java Version", System.getProperty("java.version"));
            extent.setSystemInfo("Browser", ConfigReader.getBrowser());
            extent.setSystemInfo("Headless", String.valueOf(ConfigReader.isHeadless()));
        }
        return extent;
    }

    public static ExtentTest createTest(String testName, String description) {
        ExtentTest test = getReporter().createTest(testName, description);
        testThreadLocal.set(test);
        return test;
    }

    public static ExtentTest getTest() {
        return testThreadLocal.get();
    }

    public static void flush() {
        if (extent != null) {
            extent.flush();
        }
    }
}
