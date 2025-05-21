package com.l3agent.service;

import com.l3agent.model.LogEntry;

import java.time.Instant;
import java.util.List;

/**
 * Service interface for retrieving and analyzing logs.
 */
public interface LogService {
    
    /**
     * Searches for log entries matching the given criteria.
     * 
     * @param query The search query
     * @param startTime The start of the time range to search in (inclusive)
     * @param endTime The end of the time range to search in (inclusive)
     * @param maxResults The maximum number of results to return
     * @return A list of matching log entries
     */
    List<LogEntry> searchLogs(String query, Instant startTime, Instant endTime, int maxResults);
    
    /**
     * Retrieves log entries for a specific service.
     * 
     * @param service The service name
     * @param startTime The start of the time range to search in (inclusive)
     * @param endTime The end of the time range to search in (inclusive)
     * @param maxResults The maximum number of results to return
     * @return A list of matching log entries
     */
    List<LogEntry> getServiceLogs(String service, Instant startTime, Instant endTime, int maxResults);
    
    /**
     * Retrieves log entries of a specific level.
     * 
     * @param level The log level
     * @param startTime The start of the time range to search in (inclusive)
     * @param endTime The end of the time range to search in (inclusive)
     * @param maxResults The maximum number of results to return
     * @return A list of matching log entries
     */
    List<LogEntry> getLogsByLevel(LogEntry.LogLevel level, Instant startTime, Instant endTime, int maxResults);
    
    /**
     * Analyzes logs to identify error patterns.
     * 
     * @param logs The log entries to analyze
     * @return A list of identified error patterns
     */
    List<ErrorPattern> analyzeErrors(List<LogEntry> logs);
    
    /**
     * Represents an error pattern identified in logs.
     */
    class ErrorPattern {
        private String pattern;
        private int frequency;
        private List<LogEntry> examples;
        
        // Constructors
        public ErrorPattern() {}
        
        public ErrorPattern(String pattern, int frequency, List<LogEntry> examples) {
            this.pattern = pattern;
            this.frequency = frequency;
            this.examples = examples;
        }
        
        // Getters and Setters
        public String getPattern() {
            return pattern;
        }
        
        public void setPattern(String pattern) {
            this.pattern = pattern;
        }
        
        public int getFrequency() {
            return frequency;
        }
        
        public void setFrequency(int frequency) {
            this.frequency = frequency;
        }
        
        public List<LogEntry> getExamples() {
            return examples;
        }
        
        public void setExamples(List<LogEntry> examples) {
            this.examples = examples;
        }
    }
} 