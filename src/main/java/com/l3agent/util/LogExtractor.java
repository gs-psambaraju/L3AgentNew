package com.l3agent.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility for extracting log statements from code.
 * Used to enrich code context with logging information.
 */
public class LogExtractor {
    private static final Logger logger = LoggerFactory.getLogger(LogExtractor.class);
    
    // Regex for standard logging framework statements
    private static final Pattern LOG_PATTERN = Pattern.compile(
        "\\b(log|logger)\\.(debug|info|warn|error|trace)\\s*\\(([^;]+)\\);",
        Pattern.CASE_INSENSITIVE | Pattern.MULTILINE
    );
    
    // Regex for Camel logging statements
    private static final Pattern CAMEL_LOG_PATTERN = Pattern.compile(
        "\\.log\\s*\\(\\s*LoggingLevel\\.(INFO|DEBUG|WARN|ERROR)\\s*,([^;]+)\\);",
        Pattern.CASE_INSENSITIVE | Pattern.MULTILINE
    );
    
    // Regex for method signature (simple, Java-style)
    private static final Pattern METHOD_PATTERN = Pattern.compile(
        "(public|private|protected|static|final|synchronized|abstract|native|transient|volatile|strictfp|\\s)+[\\w\\<\\>\\[\\]]+\\s+(\\w+)\\s*\\([^)]*\\)\\s*\\{"
    );
    
    /**
     * Represents a log statement found in code.
     */
    public static class LogMetadata {
        private String type;      // debug, info, warn, error, trace
        private String message;   // Log message content
        private int line;         // Line number
        private String method;    // Method containing the log
        private String codeContext; // Surrounding code lines
        
        // Getters and setters
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        
        public int getLine() { return line; }
        public void setLine(int line) { this.line = line; }
        
        public String getMethod() { return method; }
        public void setMethod(String method) { this.method = method; }
        
        public String getCodeContext() { return codeContext; }
        public void setCodeContext(String codeContext) { this.codeContext = codeContext; }
        
        // Alias methods for better naming
        public String getLevel() { return type; }
        public int getLineNumber() { return line; }
    }
    
    /**
     * Extracts log statements from code content.
     *
     * @param content The code content to extract logs from
     * @return List of log metadata
     */
    public static List<LogMetadata> extractLogs(String content) {
        List<LogMetadata> logs = new ArrayList<>();
        
        try {
            // Split content into lines for line number tracking
            String[] lines = content.split("\n");
            List<Integer> lineOffsets = new ArrayList<>();
            int offset = 0;
            for (String line : lines) {
                lineOffsets.add(offset);
                offset += line.length() + 1; // +1 for newline
            }
            
            // Process standard log statements
            Matcher logMatcher = LOG_PATTERN.matcher(content);
            while (logMatcher.find()) {
                LogMetadata log = new LogMetadata();
                log.setType(logMatcher.group(2).toUpperCase());
                log.setMessage(logMatcher.group(3).trim());
                log.setLine(getLineNumber(logMatcher.start(), lineOffsets));
                log.setMethod(findEnclosingMethod(content, logMatcher.start()));
                log.setCodeContext(extractCodeContext(lines, log.getLine(), 2));
                logs.add(log);
            }
            
            // Process Camel log statements
            Matcher camelMatcher = CAMEL_LOG_PATTERN.matcher(content);
            while (camelMatcher.find()) {
                LogMetadata log = new LogMetadata();
                log.setType(camelMatcher.group(1).toUpperCase());
                log.setMessage(camelMatcher.group(2).trim());
                log.setLine(getLineNumber(camelMatcher.start(), lineOffsets));
                log.setMethod(findEnclosingMethod(content, camelMatcher.start()));
                log.setCodeContext(extractCodeContext(lines, log.getLine(), 2));
                logs.add(log);
            }
            
            if (!logs.isEmpty()) {
                logger.debug("Extracted {} log statements", logs.size());
            }
        } catch (Exception e) {
            logger.error("Error extracting logs: {}", e.getMessage(), e);
        }
        
        return logs;
    }
    
    /**
     * Finds the line number for a character offset.
     */
    private static int getLineNumber(int offset, List<Integer> lineOffsets) {
        for (int i = 0; i < lineOffsets.size(); i++) {
            if (i == lineOffsets.size() - 1 || lineOffsets.get(i + 1) > offset) {
                return i + 1; // 1-based line number
            }
        }
        return 1; // Default to first line if not found
    }
    
    /**
     * Finds the enclosing method name for a position in the code.
     */
    private static String findEnclosingMethod(String content, int position) {
        // Look backward from position to find the last method declaration
        String contentBefore = content.substring(0, position);
        Matcher methodMatcher = METHOD_PATTERN.matcher(contentBefore);
        String methodName = null;
        
        while (methodMatcher.find()) {
            methodName = methodMatcher.group(2);
        }
        
        return methodName;
    }
    
    /**
     * Extracts code context around a line.
     */
    private static String extractCodeContext(String[] lines, int lineNumber, int contextSize) {
        StringBuilder context = new StringBuilder();
        int start = Math.max(0, lineNumber - contextSize - 1);
        int end = Math.min(lines.length, lineNumber + contextSize);
        
        for (int i = start; i < end; i++) {
            if (i == lineNumber - 1) {
                // Mark the actual log line
                context.append("-> ");
            } else {
                context.append("   ");
            }
            context.append(lines[i]).append("\n");
        }
        
        return context.toString();
    }
} 