package edu.cmu.msis.project4.service;

/**
 * Author: Raina Qiu (yuluq)
 */
public class ThirdPartyApiException extends Exception {
    private final int statusCode;
    private final long latencyMs;
    private final String errorType;

    public ThirdPartyApiException(String message, int statusCode, long latencyMs, String errorType) {
        super(message);
        this.statusCode = statusCode;
        this.latencyMs = latencyMs;
        this.errorType = errorType;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public long getLatencyMs() {
        return latencyMs;
    }

    public String getErrorType() {
        return errorType;
    }
}

