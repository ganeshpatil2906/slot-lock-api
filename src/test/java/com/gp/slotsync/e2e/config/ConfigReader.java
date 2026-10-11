package com.gp.slotsync.e2e.config;

import java.io.InputStream;
import java.util.Properties;

/**
 * Utility class to read configuration properties from config.properties file,
 * with support for System property overrides (e.g. -DbaseUrl=http://localhost:8088).
 */
public class ConfigReader {

    private static final Properties properties = new Properties();

    static {
        try (InputStream input = ConfigReader.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (input != null) {
                properties.load(input);
            } else {
                System.err.println("Warning: config.properties file not found in test resources.");
            }
        } catch (Exception e) {
            System.err.println("Failed to load config.properties: " + e.getMessage());
        }
    }

    public static String get(String key, String defaultValue) {
        // System property takes precedence (e.g., -DbaseUrl=...)
        String systemProp = System.getProperty(key);
        if (systemProp != null && !systemProp.trim().isEmpty()) {
            return systemProp.trim();
        }
        return properties.getProperty(key, defaultValue);
    }

    public static String getBaseUrl() {
        return get("baseUrl", "http://localhost:8088");
    }

    public static String getBrowser() {
        return get("browser", "chromium").toLowerCase();
    }

    public static String getChannel() {
        return get("channel", "chrome");
    }

    public static boolean isHeadless() {
        return Boolean.parseBoolean(get("headless", "true"));
    }

    public static int getTimeout() {
        return Integer.parseInt(get("timeout", "10000"));
    }

    public static boolean isRecordVideo() {
        return Boolean.parseBoolean(get("recordVideo", "true"));
    }

    public static String getVideoDir() {
        return get("videoDir", "test-recordings");
    }

    public static boolean isRecordTrace() {
        return Boolean.parseBoolean(get("recordTrace", "true"));
    }

    public static String getTraceDir() {
        return get("traceDir", "test-recordings/traces");
    }

    public static String getReportDir() {
        return get("reportDir", "test-reports");
    }

    public static String getScreenshotDir() {
        return get("screenshotDir", "test-reports/screenshots");
    }
}
