package com.l3agent.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.Arrays;
import java.util.List;

/**
 * Utility class for detecting boilerplate code in various languages.
 * Used to filter out low-value code snippets from embeddings.
 */
@Component
public class BoilerplateFilter {
    
    private static final Logger logger = LoggerFactory.getLogger(BoilerplateFilter.class);
    
    // Static instance for use by static methods
    private static BoilerplateFilter instance;
    
    // Configuration properties with default values
    @Value("${l3agent.boilerplate.threshold.java:0.7}")
    private double javaBoilerplateThreshold = 0.7;
    
    @Value("${l3agent.boilerplate.threshold.generic:0.6}")
    private double genericBoilerplateThreshold = 0.6;
    
    @Value("${l3agent.boilerplate.min-lines:3}")
    private int minLinesForBoilerplate = 3;
    
    @Value("${l3agent.boilerplate.min-code-length:50}")
    private int minCodeLength = 50;
    
    @Value("${l3agent.boilerplate.comment-ratio:0.7}")
    private double commentRatioThreshold = 0.7;
    
    // Common patterns for getters/setters in Java
    private static final Pattern JAVA_GETTER_PATTERN = 
            Pattern.compile("public\\s+\\w+\\s+get\\w+\\s*\\(\\s*\\)\\s*\\{\\s*return\\s+\\w+;\\s*\\}");
    
    private static final Pattern JAVA_SETTER_PATTERN = 
            Pattern.compile("public\\s+void\\s+set\\w+\\s*\\(\\s*\\w+\\s+\\w+\\s*\\)\\s*\\{\\s*this\\.\\w+\\s*=\\s*\\w+;\\s*\\}");
    
    // Common patterns for equals, hashCode, and toString methods
    private static final Pattern JAVA_EQUALS_PATTERN = 
            Pattern.compile("@Override\\s+public\\s+boolean\\s+equals\\s*\\(\\s*Object\\s+\\w+\\s*\\)\\s*\\{[^\\}]{10,300}\\}");
    
    private static final Pattern JAVA_HASHCODE_PATTERN = 
            Pattern.compile("@Override\\s+public\\s+int\\s+hashCode\\s*\\(\\s*\\)\\s*\\{[^\\}]{5,200}\\}");
    
    private static final Pattern JAVA_TOSTRING_PATTERN = 
            Pattern.compile("@Override\\s+public\\s+String\\s+toString\\s*\\(\\s*\\)\\s*\\{[^\\}]{5,300}\\}");
    
    // Common boilerplate patterns for other languages
    private static final Pattern JAVASCRIPT_EXPORT_PATTERN = 
            Pattern.compile("(export\\s+(default\\s+)?|module\\.exports\\s+=\\s+)");
    
    // Keywords that suggest the code might be important logic and not boilerplate
    private static final List<String> NON_BOILERPLATE_KEYWORDS = Arrays.asList(
            "business", "logic", "algorithm", "calculate", "process", "validate", 
            "transform", "convert", "generate", "analyze", "extract", "complex"
    );
    
    /**
     * Initialize the static instance after dependency injection.
     */
    @PostConstruct
    public void init() {
        instance = this;
        logger.info("BoilerplateFilter initialized with Java threshold: {}, Generic threshold: {}", 
                javaBoilerplateThreshold, genericBoilerplateThreshold);
    }
    
    /**
     * Checks if the provided code is likely boilerplate based on language-specific patterns.
     * Instance method that uses configured thresholds.
     * 
     * @param code The code to analyze
     * @param language The programming language of the code
     * @return True if the code is detected as boilerplate, false otherwise
     */
    public boolean isBoilerplateCode(String code, String language) {
        if (code == null || code.trim().isEmpty()) {
            return false;
        }
        
        // Check if the code contains keywords that suggest it's not boilerplate
        for (String keyword : NON_BOILERPLATE_KEYWORDS) {
            if (code.toLowerCase().contains(keyword)) {
                return false;
            }
        }
        
        // Apply language-specific detection
        if ("java".equalsIgnoreCase(language)) {
            return isJavaBoilerplate(code);
        } else if ("javascript".equalsIgnoreCase(language) || "typescript".equalsIgnoreCase(language)) {
            return isJavaScriptBoilerplate(code);
        }
        
        // For other languages, apply more conservative detection
        return isGenericBoilerplate(code);
    }
    
    /**
     * Static method for backward compatibility with existing code.
     * Uses the Spring-managed instance if available, or default values if not.
     * 
     * @param code The code to analyze
     * @param language The programming language of the code
     * @return True if the code is detected as boilerplate, false otherwise
     */
    public static boolean isBoilerplate(String code, String language) {
        if (instance != null) {
            return instance.isBoilerplateCode(code, language);
        } else {
            // Fallback for static contexts when Spring hasn't initialized the bean
            logger.warn("BoilerplateFilter static instance not initialized, using default thresholds");
            BoilerplateFilter fallback = new BoilerplateFilter();
            return fallback.isBoilerplateCode(code, language);
        }
    }
    
    /**
     * Checks if Java code is likely boilerplate.
     */
    private boolean isJavaBoilerplate(String code) {
        // Calculate the percentage of the code that matches boilerplate patterns
        int totalLength = code.length();
        int boilerplateLength = 0;
        
        // Check for getters and setters
        Matcher getterMatcher = JAVA_GETTER_PATTERN.matcher(code);
        while (getterMatcher.find()) {
            boilerplateLength += getterMatcher.group().length();
        }
        
        Matcher setterMatcher = JAVA_SETTER_PATTERN.matcher(code);
        while (setterMatcher.find()) {
            boilerplateLength += setterMatcher.group().length();
        }
        
        // Check for equals, hashCode, and toString methods
        Matcher equalsMatcher = JAVA_EQUALS_PATTERN.matcher(code);
        while (equalsMatcher.find()) {
            boilerplateLength += equalsMatcher.group().length();
        }
        
        Matcher hashCodeMatcher = JAVA_HASHCODE_PATTERN.matcher(code);
        while (hashCodeMatcher.find()) {
            boilerplateLength += hashCodeMatcher.group().length();
        }
        
        Matcher toStringMatcher = JAVA_TOSTRING_PATTERN.matcher(code);
        while (toStringMatcher.find()) {
            boilerplateLength += toStringMatcher.group().length();
        }
        
        // Calculate percentage of boilerplate code
        double boilerplatePercentage = (double) boilerplateLength / totalLength;
        
        // Log the results if near threshold for debugging
        double threshold = javaBoilerplateThreshold;
        if (Math.abs(boilerplatePercentage - threshold) < 0.1) {
            logger.debug("Java code boilerplate percentage: {}, threshold: {}", 
                    String.format("%.2f", boilerplatePercentage), threshold);
        }
        
        // Consider it boilerplate if percentage exceeds threshold
        return boilerplatePercentage > threshold;
    }
    
    /**
     * Checks if JavaScript/TypeScript code is likely boilerplate.
     */
    private boolean isJavaScriptBoilerplate(String code) {
        // For JavaScript, focus on export patterns and simple functions
        Matcher exportMatcher = JAVASCRIPT_EXPORT_PATTERN.matcher(code);
        
        // If it's just a simple export statement, it's likely boilerplate
        if (exportMatcher.find() && code.trim().length() < 200) {
            return true;
        }
        
        // For JavaScript, apply more conservative detection
        return isGenericBoilerplate(code);
    }
    
    /**
     * Applies generic boilerplate detection for languages without specific patterns.
     */
    private boolean isGenericBoilerplate(String code) {
        // For generic code, consider very short code to be boilerplate
        if (code.trim().length() < minCodeLength) {
            return true;
        }
        
        // Check for common import/include patterns
        if (code.matches("(?s)^\\s*(import|include|require|using)\\s+.*$") && code.trim().length() < 150) {
            return true;
        }
        
        // Count lines with actual code vs total lines
        String[] lines = code.split("\n");
        
        // Skip very small snippets (fewer than minLinesForBoilerplate)
        if (lines.length < minLinesForBoilerplate) {
            return false;
        }
        
        int codeLines = 0;
        int commentLines = 0;
        
        for (String line : lines) {
            String trimmedLine = line.trim();
            if (trimmedLine.isEmpty()) {
                continue;
            }
            if (trimmedLine.startsWith("//") || trimmedLine.startsWith("*") || trimmedLine.startsWith("/*")) {
                commentLines++;
            } else {
                codeLines++;
            }
        }
        
        // If most of the content is comments, it might be boilerplate
        if (lines.length > 10 && (double) commentLines / lines.length > commentRatioThreshold) {
            return true;
        }
        
        // Default to considering it not boilerplate
        return false;
    }
    
    /**
     * Allows updating thresholds at runtime if needed.
     * 
     * @param threshold The new boilerplate threshold for Java code
     */
    public void setJavaBoilerplateThreshold(double threshold) {
        this.javaBoilerplateThreshold = threshold;
        logger.info("Updated Java boilerplate threshold to {}", threshold);
    }
    
    /**
     * Allows updating thresholds at runtime if needed.
     * 
     * @param threshold The new boilerplate threshold for generic code
     */
    public void setGenericBoilerplateThreshold(double threshold) {
        this.genericBoilerplateThreshold = threshold;
        logger.info("Updated generic boilerplate threshold to {}", threshold);
    }
    
    /**
     * Allows updating minimum code length threshold at runtime.
     * 
     * @param length The new minimum length for code to be considered
     */
    public void setMinCodeLength(int length) {
        this.minCodeLength = length;
        logger.info("Updated minimum code length to {}", length);
    }
    
    /**
     * Gets the current Java boilerplate threshold.
     * 
     * @return The current threshold
     */
    public double getJavaBoilerplateThreshold() {
        return javaBoilerplateThreshold;
    }
    
    /**
     * Gets the current generic boilerplate threshold.
     * 
     * @return The current threshold
     */
    public double getGenericBoilerplateThreshold() {
        return genericBoilerplateThreshold;
    }
} 