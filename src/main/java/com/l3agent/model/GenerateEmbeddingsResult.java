package com.l3agent.model;

import java.util.HashMap;

/**
 * Represents the result of a code embedding generation operation.
 * Extends HashMap to store various metrics and information about the embedding process.
 */
public class GenerateEmbeddingsResult extends HashMap<String, Object> {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * Get the number of successfully generated embeddings.
     * 
     * @return The number of successful embeddings
     */
    public int getSuccess_count() {
        return getIntValue("successful_embeddings", 0);
    }
    
    /**
     * Get the number of failed embedding generations.
     * 
     * @return The number of failures in the current batch
     */
    public int getFailure_count() {
        return getIntValue("failed_embeddings", 0);
    }
    
    /**
     * Get the total number of files processed.
     * 
     * @return The number of files processed
     */
    public int getFiles_processed() {
        return getIntValue("files_processed", 0);
    }
    
    /**
     * Get the total number of chunks processed.
     * 
     * @return The number of code chunks processed
     */
    public int getTotal_chunks() {
        return getIntValue("total_chunks", 0);
    }
    
    /**
     * Get the total number of boilerplate chunks skipped.
     * 
     * @return The number of boilerplate chunks skipped
     */
    public int getSkipped_boilerplate() {
        return getIntValue("skipped_boilerplate", 0);
    }
    
    /**
     * Get the processing time in milliseconds.
     * 
     * @return The processing time in milliseconds
     */
    public long getDuration_ms() {
        Object value = get("duration_ms");
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }
    
    /**
     * Get the status of the embedding generation process.
     * 
     * @return The status (e.g., "success", "error")
     */
    public String getStatus() {
        Object value = get("status");
        return value instanceof String ? (String) value : "unknown";
    }
    
    /**
     * Helper method to get an integer value from the map.
     * 
     * @param key The key to look up
     * @param defaultValue The default value to return if the key is not found
     * @return The integer value
     */
    private int getIntValue(String key, int defaultValue) {
        Object value = get(key);
        return value instanceof Number ? ((Number) value).intValue() : defaultValue;
    }
} 