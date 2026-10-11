package com.gp.slotsync.e2e.utils;

import com.microsoft.playwright.Page;
import com.gp.slotsync.e2e.config.ConfigReader;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

/**
 * Utility for capturing screenshots via Playwright for report attachments.
 */
public class ScreenshotUtils {

    public static String captureBase64Screenshot(Page page) {
        if (page == null || page.isClosed()) {
            return null;
        }
        byte[] screenshotBytes = page.screenshot(new Page.ScreenshotOptions().setFullPage(true));
        return Base64.getEncoder().encodeToString(screenshotBytes);
    }

    public static String captureFileScreenshot(Page page, String testName) {
        if (page == null || page.isClosed()) {
            return null;
        }
        try {
            String dirPath = ConfigReader.getScreenshotDir();
            File dir = new File(dirPath);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String fileName = testName + "_" + timestamp + ".png";
            Path destination = Paths.get(dirPath, fileName);

            page.screenshot(new Page.ScreenshotOptions().setPath(destination).setFullPage(true));
            return destination.toAbsolutePath().toString();
        } catch (Exception e) {
            System.err.println("Failed to capture screenshot file: " + e.getMessage());
            return null;
        }
    }
}
