package com.gp.slotsync.e2e.listeners;

import com.aventstack.extentreports.ExtentTest;
import com.aventstack.extentreports.MediaEntityBuilder;
import com.aventstack.extentreports.Status;
import com.microsoft.playwright.Page;
import com.gp.slotsync.e2e.base.BaseTest;
import com.gp.slotsync.e2e.utils.ExtentReportManager;
import com.gp.slotsync.e2e.utils.ScreenshotUtils;
import org.testng.ITestContext;
import org.testng.ITestListener;
import org.testng.ITestResult;

/**
 * TestNG Listener integrating Playwright execution events with ExtentReports.
 * Automatically captures screenshots on failure and flushes reports on suite completion.
 */
public class TestListener implements ITestListener {

    @Override
    public void onTestStart(ITestResult result) {
        String testName = result.getMethod().getMethodName();
        String description = result.getMethod().getDescription();
        if (description == null || description.isEmpty()) {
            description = "Execution of test method: " + testName;
        }
        ExtentReportManager.createTest(testName, description);
    }

    @Override
    public void onTestSuccess(ITestResult result) {
        ExtentTest test = ExtentReportManager.getTest();
        if (test != null) {
            test.log(Status.PASS, "Test passed successfully.");
        }
    }

    @Override
    public void onTestFailure(ITestResult result) {
        ExtentTest test = ExtentReportManager.getTest();
        if (test != null) {
            test.log(Status.FAIL, "Test failed with exception: " + result.getThrowable());

            Page page = BaseTest.getPage();
            if (page != null && !page.isClosed()) {
                try {
                    String base64Screenshot = ScreenshotUtils.captureBase64Screenshot(page);
                    if (base64Screenshot != null) {
                        test.fail("Screenshot on failure:",
                                MediaEntityBuilder.createScreenCaptureFromBase64String(base64Screenshot).build());
                    }
                    ScreenshotUtils.captureFileScreenshot(page, result.getMethod().getMethodName());
                } catch (Exception e) {
                    test.info("Failed to capture screenshot: " + e.getMessage());
                }
            }
        }
    }

    @Override
    public void onTestSkipped(ITestResult result) {
        ExtentTest test = ExtentReportManager.getTest();
        if (test != null) {
            test.log(Status.SKIP, "Test skipped: " + result.getThrowable());
        }
    }

    @Override
    public void onFinish(ITestContext context) {
        ExtentReportManager.flush();
    }
}
