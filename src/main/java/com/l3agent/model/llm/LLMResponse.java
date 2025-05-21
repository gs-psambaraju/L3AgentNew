package com.l3agent.model.llm;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Represents a response from a Large Language Model.
 * Encapsulates the response content, metadata, and relevant metrics.
 */
public class LLMResponse {

    private String responseId;
    private String requestId;
    private String content;
    private Map<String, Object> metadata;
    private Instant timestamp;
    private long latencyMs;
    private int promptTokens;
    private int completionTokens;
    private int totalTokens;
    private ModelInfo modelInfo;
    private double confidence;
    private UsageMetrics usageMetrics;
    private boolean error;
    private String errorMessage;
    private String finishReason;
    private java.util.List<String> knowledgeSources;
    
    // Default constructor
    public LLMResponse() {
        this.responseId = UUID.randomUUID().toString();
        this.timestamp = Instant.now();
        this.metadata = new HashMap<>();
    }
    
    // Constructor with content
    public LLMResponse(String content) {
        this();
        this.content = content;
    }
    
    // Builder pattern methods for fluent API
    
    public LLMResponse withRequestId(String requestId) {
        this.requestId = requestId;
        return this;
    }
    
    public LLMResponse withContent(String content) {
        this.content = content;
        return this;
    }
    
    public LLMResponse withMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
        return this;
    }
    
    public LLMResponse addMetadata(String key, Object value) {
        if (this.metadata == null) {
            this.metadata = new HashMap<>();
        }
        this.metadata.put(key, value);
        return this;
    }
    
    public LLMResponse withLatencyMs(long latencyMs) {
        this.latencyMs = latencyMs;
        return this;
    }
    
    public LLMResponse withPromptTokens(int promptTokens) {
        this.promptTokens = promptTokens;
        return this;
    }
    
    public LLMResponse withCompletionTokens(int completionTokens) {
        this.completionTokens = completionTokens;
        return this;
    }
    
    public LLMResponse withTotalTokens(int totalTokens) {
        this.totalTokens = totalTokens;
        return this;
    }
    
    public LLMResponse withModelInfo(ModelInfo modelInfo) {
        this.modelInfo = modelInfo;
        return this;
    }
    
    public LLMResponse withConfidence(double confidence) {
        this.confidence = confidence;
        return this;
    }
    
    public LLMResponse withUsageMetrics(UsageMetrics usageMetrics) {
        this.usageMetrics = usageMetrics;
        return this;
    }
    
    public LLMResponse withFinishReason(String finishReason) {
        this.finishReason = finishReason;
        return this;
    }
    
    public LLMResponse withKnowledgeSources(java.util.List<String> knowledgeSources) {
        this.knowledgeSources = knowledgeSources;
        return this;
    }
    
    // Getters and setters
    
    public String getResponseId() {
        return responseId;
    }
    
    public void setResponseId(String responseId) {
        this.responseId = responseId;
    }
    
    public String getRequestId() {
        return requestId;
    }
    
    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
    
    public String getContent() {
        return content;
    }
    
    public void setContent(String content) {
        this.content = content;
    }
    
    public Map<String, Object> getMetadata() {
        return metadata;
    }
    
    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
    
    public Instant getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
    
    public long getLatencyMs() {
        return latencyMs;
    }
    
    public void setLatencyMs(long latencyMs) {
        this.latencyMs = latencyMs;
    }
    
    public int getPromptTokens() {
        return promptTokens;
    }
    
    public void setPromptTokens(int promptTokens) {
        this.promptTokens = promptTokens;
    }
    
    public int getCompletionTokens() {
        return completionTokens;
    }
    
    public void setCompletionTokens(int completionTokens) {
        this.completionTokens = completionTokens;
    }
    
    public int getTotalTokens() {
        return totalTokens;
    }
    
    public void setTotalTokens(int totalTokens) {
        this.totalTokens = totalTokens;
    }
    
    public ModelInfo getModelInfo() {
        return modelInfo;
    }
    
    public void setModelInfo(ModelInfo modelInfo) {
        this.modelInfo = modelInfo;
    }
    
    public double getConfidence() {
        return confidence;
    }
    
    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }
    
    public UsageMetrics getUsageMetrics() {
        return usageMetrics;
    }
    
    public void setUsageMetrics(UsageMetrics usageMetrics) {
        this.usageMetrics = usageMetrics;
    }
    
    public boolean isError() {
        return error || (metadata != null && metadata.containsKey("error") && 
                (boolean) metadata.get("error"));
    }
    
    public void setError(boolean error) {
        this.error = error;
    }
    
    public String getErrorMessage() {
        return isError() ? content : null;
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
        this.error = true;
    }
    
    public String getFinishReason() {
        return finishReason;
    }
    
    public void setFinishReason(String finishReason) {
        this.finishReason = finishReason;
    }
    
    public java.util.List<String> getKnowledgeSources() {
        return knowledgeSources;
    }
    
    public void setKnowledgeSources(java.util.List<String> knowledgeSources) {
        this.knowledgeSources = knowledgeSources;
    }
    
    /**
     * Creates a new LLMResponse with content and request ID.
     * 
     * @param content The response content
     * @param requestId The ID of the request that generated this response
     * @return A new LLMResponse instance
     */
    public static LLMResponse create(String content, String requestId) {
        return new LLMResponse(content).withRequestId(requestId);
    }
    
    /**
     * Creates an error response.
     * 
     * @param errorMessage The error message
     * @param requestId The ID of the request that generated the error
     * @return A new LLMResponse instance representing an error
     */
    public static LLMResponse createError(String errorMessage, String requestId) {
        LLMResponse response = new LLMResponse()
                .withRequestId(requestId)
                .withContent(errorMessage)
                .addMetadata("error", true);
        response.setError(true);
        response.setErrorMessage(errorMessage);
        return response;
    }
    
    /**
     * Information about the model used for generating the response.
     */
    public static class ModelInfo {
        private String name;
        private String version;
        private String provider;
        
        public ModelInfo() {
        }
        
        public ModelInfo(String name, String version, String provider) {
            this.name = name;
            this.version = version;
            this.provider = provider;
        }
        
        public String getName() {
            return name;
        }
        
        public void setName(String name) {
            this.name = name;
        }
        
        public String getVersion() {
            return version;
        }
        
        public void setVersion(String version) {
            this.version = version;
        }
        
        public String getProvider() {
            return provider;
        }
        
        public void setProvider(String provider) {
            this.provider = provider;
        }
    }
    
    /**
     * Usage metrics for the LLM interaction.
     */
    public static class UsageMetrics {
        private int promptTokens;
        private int completionTokens;
        private int totalTokens;
        private double estimatedCost;
        
        public UsageMetrics() {
        }
        
        public UsageMetrics(int promptTokens, int completionTokens) {
            this.promptTokens = promptTokens;
            this.completionTokens = completionTokens;
            this.totalTokens = promptTokens + completionTokens;
        }
        
        public int getPromptTokens() {
            return promptTokens;
        }
        
        public void setPromptTokens(int promptTokens) {
            this.promptTokens = promptTokens;
            this.totalTokens = promptTokens + completionTokens;
        }
        
        public int getCompletionTokens() {
            return completionTokens;
        }
        
        public void setCompletionTokens(int completionTokens) {
            this.completionTokens = completionTokens;
            this.totalTokens = promptTokens + completionTokens;
        }
        
        public int getTotalTokens() {
            return totalTokens;
        }
        
        public void setTotalTokens(int totalTokens) {
            this.totalTokens = totalTokens;
        }
        
        public double getEstimatedCost() {
            return estimatedCost;
        }
        
        public void setEstimatedCost(double estimatedCost) {
            this.estimatedCost = estimatedCost;
        }
    }
} 