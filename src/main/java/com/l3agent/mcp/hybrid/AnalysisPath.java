package com.l3agent.mcp.hybrid;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Represents an analysis path determined by the query classifier.
 * Contains information about the type of analysis to perform and the tools required.
 */
public class AnalysisPath {
    
    private String pathType;
    private double confidence;
    private List<String> requiredTools;
    private Map<String, Object> flags;
    private String query;
    
    private AnalysisPath(Builder builder) {
        this.pathType = builder.pathType;
        this.confidence = builder.confidence;
        this.requiredTools = builder.requiredTools;
        this.flags = builder.flags;
        this.query = builder.query;
    }
    
    /**
     * Gets the path type for this analysis.
     * 
     * @return The path type ("STATIC", "DYNAMIC", or "HYBRID")
     */
    public String getPathType() {
        return pathType;
    }
    
    /**
     * Gets the confidence score for this analysis path classification.
     * 
     * @return The confidence score (0.0 to 1.0)
     */
    public double getConfidence() {
        return confidence;
    }
    
    /**
     * Gets the list of required tools for this analysis path.
     * 
     * @return The list of tool names
     */
    public List<String> getRequiredTools() {
        return requiredTools;
    }
    
    /**
     * Gets the original query that led to this analysis path.
     * 
     * @return The query
     */
    public String getQuery() {
        return query;
    }
    
    /**
     * Gets the flags for special processing.
     * 
     * @return The flags
     */
    public Map<String, Object> getFlags() {
        return flags;
    }
    
    /**
     * Gets a specific flag value.
     * 
     * @param key The flag key
     * @return The flag value, or null if not found
     */
    public Object getFlag(String key) {
        return flags.get(key);
    }
    
    /**
     * Checks if a boolean flag is set to true.
     * 
     * @param key The flag key
     * @return True if the flag exists and is true, false otherwise
     */
    public boolean isFlagEnabled(String key) {
        Object value = flags.get(key);
        return value instanceof Boolean && (Boolean) value;
    }
    
    /**
     * Creates a new builder for AnalysisPath.
     * 
     * @return A new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Builder for creating AnalysisPath instances.
     */
    public static class Builder {
        private String pathType = "STATIC";
        private double confidence = 0.7;
        private List<String> requiredTools = new ArrayList<>();
        private Map<String, Object> flags = new HashMap<>();
        private String query;
        
        /**
         * Sets the path type.
         * 
         * @param pathType The path type
         * @return This builder
         */
        public Builder pathType(String pathType) {
            this.pathType = pathType;
            return this;
        }
        
        /**
         * Sets the confidence score.
         * 
         * @param confidence The confidence score
         * @return This builder
         */
        public Builder confidence(double confidence) {
            this.confidence = confidence;
            return this;
        }
        
        /**
         * Sets the required tools.
         * 
         * @param requiredTools The list of required tools
         * @return This builder
         */
        public Builder requiredTools(List<String> requiredTools) {
            this.requiredTools = requiredTools;
            return this;
        }
        
        /**
         * Adds a required tool.
         * 
         * @param tool The tool name
         * @return This builder
         */
        public Builder addRequiredTool(String tool) {
            this.requiredTools.add(tool);
            return this;
        }
        
        /**
         * Sets the original query.
         * 
         * @param query The query
         * @return This builder
         */
        public Builder query(String query) {
            this.query = query;
            return this;
        }
        
        /**
         * Sets a flag for special processing.
         * 
         * @param key The flag key
         * @param value The flag value
         * @return This builder
         */
        public Builder addFlag(String key, Object value) {
            this.flags.put(key, value);
            return this;
        }
        
        /**
         * Builds the AnalysisPath.
         * 
         * @return A new AnalysisPath instance
         */
        public AnalysisPath build() {
            return new AnalysisPath(this);
        }
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AnalysisPath that = (AnalysisPath) o;
        return Double.compare(that.confidence, confidence) == 0 &&
               Objects.equals(pathType, that.pathType) &&
               Objects.equals(requiredTools, that.requiredTools) &&
               Objects.equals(query, that.query) &&
               Objects.equals(flags, that.flags);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(pathType, confidence, requiredTools, query, flags);
    }
    
    @Override
    public String toString() {
        return "AnalysisPath{" +
               "pathType='" + pathType + '\'' +
               ", confidence=" + confidence +
               ", requiredTools=" + requiredTools +
               ", query='" + query + '\'' +
               ", flags=" + flags +
               '}';
    }
} 