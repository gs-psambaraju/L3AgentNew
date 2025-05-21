package com.l3agent.mcp.tools.errorchain.model;

/**
 * Represents a logging pattern detected for an exception.
 * Contains information about how exceptions are logged throughout the system.
 */
public class LoggingPattern {
    
    private String exceptionClass;
    private String loggingLevel;
    private int occurrences;
    private String messagePattern;
    
    /**
     * Creates a new logging pattern.
     * 
     * @param exceptionClass The exception class being logged
     * @param loggingLevel The logging level (error, warn, info, etc.)
     * @param occurrences Number of occurrences of this pattern
     */
    public LoggingPattern(String exceptionClass, String loggingLevel, int occurrences) {
        this.exceptionClass = exceptionClass;
        this.loggingLevel = loggingLevel;
        this.occurrences = occurrences;
    }
    
    /**
     * Creates a new logging pattern with a message pattern.
     * 
     * @param exceptionClass The exception class being logged
     * @param loggingLevel The logging level (error, warn, info, etc.)
     * @param occurrences Number of occurrences of this pattern
     * @param messagePattern The common pattern of log messages
     */
    public LoggingPattern(String exceptionClass, String loggingLevel, int occurrences, String messagePattern) {
        this(exceptionClass, loggingLevel, occurrences);
        this.messagePattern = messagePattern;
    }
    
    /**
     * Gets the exception class.
     * 
     * @return The exception class name
     */
    public String getExceptionClass() {
        return exceptionClass;
    }
    
    /**
     * Gets the logging level.
     * 
     * @return The logging level
     */
    public String getLoggingLevel() {
        return loggingLevel;
    }
    
    /**
     * Gets the number of occurrences.
     * 
     * @return The number of occurrences
     */
    public int getOccurrences() {
        return occurrences;
    }
    
    /**
     * Gets the message pattern.
     * 
     * @return The message pattern, or null if not available
     */
    public String getMessagePattern() {
        return messagePattern;
    }
    
    /**
     * Sets the message pattern.
     * 
     * @param messagePattern The message pattern to set
     */
    public void setMessagePattern(String messagePattern) {
        this.messagePattern = messagePattern;
    }
    
    /**
     * Returns a string representation of this logging pattern.
     * 
     * @return A string representation
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("LoggingPattern{")
          .append(exceptionClass)
          .append(" logged at ")
          .append(loggingLevel)
          .append(" level, ")
          .append(occurrences)
          .append(" occurrences");
        
        if (messagePattern != null) {
            sb.append(", pattern: ").append(messagePattern);
        }
        
        sb.append("}");
        return sb.toString();
    }
} 