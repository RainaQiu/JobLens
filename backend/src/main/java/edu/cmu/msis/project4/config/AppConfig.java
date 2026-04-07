package edu.cmu.msis.project4.config;

/**
 * Author: Raina Qiu (yuluq)
 * Environment-backed configuration helper.
 */
public final class AppConfig {

    private AppConfig() {
    }

    public static String get(String key, String defaultValue) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value;
    }

    public static String getRequired(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required environment variable: " + key);
        }
        return value;
    }
}
