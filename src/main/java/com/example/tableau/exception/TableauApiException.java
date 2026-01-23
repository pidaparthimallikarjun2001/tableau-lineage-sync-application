package com.example.tableau.exception;

/**
 * Custom exception for Tableau API related errors.
 */
public class TableauApiException extends RuntimeException {

    private final int statusCode;
    private final String errorCode;

    public TableauApiException(String message) {
        super(message);
        this.statusCode = 500;
        this.errorCode = "TABLEAU_API_ERROR";
    }

    public TableauApiException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 500;
        this.errorCode = "TABLEAU_API_ERROR";
    }

    public TableauApiException(String message, int statusCode, String errorCode) {
        super(message);
        this.statusCode = statusCode;
        this.errorCode = errorCode;
    }

    public TableauApiException(String message, int statusCode, String errorCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        this.errorCode = errorCode;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
