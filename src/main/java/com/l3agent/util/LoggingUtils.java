package com.l3agent.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Utility methods for secure logging to prevent sensitive information from being exposed in logs.
 */
public class LoggingUtils {
    private static final Logger logger = LoggerFactory.getLogger(LoggingUtils.class);
    
    // List of header names that contain sensitive information
    private static final Set<String> SENSITIVE_HEADERS = Set.of(
            "access_key", 
            "access-key",
            "accesskey",
            "authorization", 
            "auth-token", 
            "authtoken",
            "secret",
            "api-key",
            "apikey",
            "password",
            "credentials",
            "token"
    );
    
    /**
     * Masks sensitive information in HTTP headers for logging.
     * 
     * @param headers The headers map to sanitize
     * @return A sanitized copy of the headers map safe for logging
     */
    public static Map<String, String> maskSensitiveHeaders(Map<String, String> headers) {
        if (headers == null) {
            return null;
        }
        
        Map<String, String> sanitized = new HashMap<>();
        
        headers.forEach((key, value) -> {
            if (key != null && SENSITIVE_HEADERS.contains(key.toLowerCase())) {
                if (value != null && value.length() > 0) {
                    sanitized.put(key, value.substring(0, Math.min(10, value.length())) + "...");
                } else {
                    sanitized.put(key, "null");
                }
            } else {
                sanitized.put(key, value);
            }
        });
        
        return sanitized;
    }
    
    /**
     * Truncates text content for logging purposes.
     * 
     * @param text The original text
     * @param maxLength Maximum length to show in logs
     * @return Truncated text suitable for logging
     */
    public static String truncateText(String text, int maxLength) {
        if (text == null) {
            return "null";
        }
        
        if (text.length() <= maxLength) {
            return text;
        }
        
        return text.substring(0, maxLength) + "... [" + (text.length() - maxLength) + " more chars]";
    }
} 