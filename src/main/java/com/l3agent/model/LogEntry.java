package com.l3agent.model;

import java.time.Instant;

/**
 * Represents a log entry in the system.
 * This model aligns with the log input format specified in the data flow documentation.
 */
public class LogEntry {
    private Instant timestamp;
    private LogLevel level;
    private String service;
    private String message;
    
    /**
     * Enumeration of possible log levels.
     */
    public enum LogLevel {
        TRACE, DEBUG, INFO, WARN, ERROR, FATAL
    }
    
    // Getters and Setters
    public Instant getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
    
    public LogLevel getLevel() {
        return level;
    }
    
    public void setLevel(LogLevel level) {
        this.level = level;
    }
    
    public String getService() {
        return service;
    }
    
    public void setService(String service) {
        this.service = service;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
} 