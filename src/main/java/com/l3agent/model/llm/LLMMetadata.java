package com.l3agent.model.llm;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Entity class for storing metadata about LLM interactions.
 * Used for persistent storage and analysis of LLM usage.
 */
@Entity
@Table(name = "llm_metadata")
public class LLMMetadata {

    @Id
    private String id;
    
    @Column(name = "request_id")
    private String requestId;
    
    @Column(name = "response_id")
    private String responseId;
    
    @Column(name = "model_name")
    private String modelName;
    
    @Column(name = "model_version")
    private String modelVersion;
    
    @Column(name = "provider")
    private String provider;
    
    @Lob
    @Column(name = "prompt")
    private String prompt;
    
    @Lob
    @Column(name = "response")
    private String response;
    
    @Column(name = "ticket_id")
    private String ticketId;
    
    @Column(name = "conversation_id")
    private String conversationId;
    
    @Column(name = "message_id")
    private String messageId;
    
    @Column(name = "timestamp")
    private Instant timestamp;
    
    @Column(name = "latency_ms")
    private long latencyMs;
    
    @Column(name = "prompt_tokens")
    private int promptTokens;
    
    @Column(name = "completion_tokens")
    private int completionTokens;
    
    @Column(name = "total_tokens")
    private int totalTokens;
    
    @Column(name = "estimated_cost")
    private double estimatedCost;
    
    @Column(name = "temperature")
    private double temperature;
    
    @Column(name = "max_tokens")
    private int maxTokens;
    
    @Column(name = "top_p")
    private double topP;
    
    @Column(name = "confidence")
    private double confidence;
    
    @Lob
    @Column(name = "knowledge_sources")
    private String knowledgeSources;
    
    @Column(name = "feedback_rating")
    private Integer feedbackRating;
    
    @Lob
    @Column(name = "feedback_comments")
    private String feedbackComments;
    
    // Default constructor
    public LLMMetadata() {
    }
    
    // Builder pattern methods for fluent API
    
    public LLMMetadata withId(String id) {
        this.id = id;
        return this;
    }
    
    public LLMMetadata withRequestId(String requestId) {
        this.requestId = requestId;
        return this;
    }
    
    public LLMMetadata withResponseId(String responseId) {
        this.responseId = responseId;
        return this;
    }
    
    public LLMMetadata withModelName(String modelName) {
        this.modelName = modelName;
        return this;
    }
    
    public LLMMetadata withModelVersion(String modelVersion) {
        this.modelVersion = modelVersion;
        return this;
    }
    
    public LLMMetadata withProvider(String provider) {
        this.provider = provider;
        return this;
    }
    
    public LLMMetadata withPrompt(String prompt) {
        this.prompt = prompt;
        return this;
    }
    
    public LLMMetadata withResponse(String response) {
        this.response = response;
        return this;
    }
    
    public LLMMetadata withTicketId(String ticketId) {
        this.ticketId = ticketId;
        return this;
    }
    
    public LLMMetadata withConversationId(String conversationId) {
        this.conversationId = conversationId;
        return this;
    }
    
    public LLMMetadata withMessageId(String messageId) {
        this.messageId = messageId;
        return this;
    }
    
    public LLMMetadata withTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
        return this;
    }
    
    public LLMMetadata withLatencyMs(long latencyMs) {
        this.latencyMs = latencyMs;
        return this;
    }
    
    public LLMMetadata withPromptTokens(int promptTokens) {
        this.promptTokens = promptTokens;
        return this;
    }
    
    public LLMMetadata withCompletionTokens(int completionTokens) {
        this.completionTokens = completionTokens;
        return this;
    }
    
    public LLMMetadata withTotalTokens(int totalTokens) {
        this.totalTokens = totalTokens;
        return this;
    }
    
    public LLMMetadata withEstimatedCost(double estimatedCost) {
        this.estimatedCost = estimatedCost;
        return this;
    }
    
    public LLMMetadata withTemperature(double temperature) {
        this.temperature = temperature;
        return this;
    }
    
    public LLMMetadata withMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
        return this;
    }
    
    public LLMMetadata withTopP(double topP) {
        this.topP = topP;
        return this;
    }
    
    public LLMMetadata withConfidence(double confidence) {
        this.confidence = confidence;
        return this;
    }
    
    public LLMMetadata withKnowledgeSources(String knowledgeSources) {
        this.knowledgeSources = knowledgeSources;
        return this;
    }
    
    public LLMMetadata withFeedbackRating(Integer feedbackRating) {
        this.feedbackRating = feedbackRating;
        return this;
    }
    
    public LLMMetadata withFeedbackComments(String feedbackComments) {
        this.feedbackComments = feedbackComments;
        return this;
    }
    
    // Getters and setters
    
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getRequestId() {
        return requestId;
    }
    
    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
    
    public String getResponseId() {
        return responseId;
    }
    
    public void setResponseId(String responseId) {
        this.responseId = responseId;
    }
    
    public String getModelName() {
        return modelName;
    }
    
    public void setModelName(String modelName) {
        this.modelName = modelName;
    }
    
    public String getModelVersion() {
        return modelVersion;
    }
    
    public void setModelVersion(String modelVersion) {
        this.modelVersion = modelVersion;
    }
    
    public String getProvider() {
        return provider;
    }
    
    public void setProvider(String provider) {
        this.provider = provider;
    }
    
    public String getPrompt() {
        return prompt;
    }
    
    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }
    
    public String getResponse() {
        return response;
    }
    
    public void setResponse(String response) {
        this.response = response;
    }
    
    public String getTicketId() {
        return ticketId;
    }
    
    public void setTicketId(String ticketId) {
        this.ticketId = ticketId;
    }
    
    public String getConversationId() {
        return conversationId;
    }
    
    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }
    
    public String getMessageId() {
        return messageId;
    }
    
    public void setMessageId(String messageId) {
        this.messageId = messageId;
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
    
    public double getEstimatedCost() {
        return estimatedCost;
    }
    
    public void setEstimatedCost(double estimatedCost) {
        this.estimatedCost = estimatedCost;
    }
    
    public double getTemperature() {
        return temperature;
    }
    
    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }
    
    public int getMaxTokens() {
        return maxTokens;
    }
    
    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }
    
    public double getTopP() {
        return topP;
    }
    
    public void setTopP(double topP) {
        this.topP = topP;
    }
    
    public double getConfidence() {
        return confidence;
    }
    
    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }
    
    public String getKnowledgeSources() {
        return knowledgeSources;
    }
    
    public void setKnowledgeSources(String knowledgeSources) {
        this.knowledgeSources = knowledgeSources;
    }
    
    public Integer getFeedbackRating() {
        return feedbackRating;
    }
    
    public void setFeedbackRating(Integer feedbackRating) {
        this.feedbackRating = feedbackRating;
    }
    
    public String getFeedbackComments() {
        return feedbackComments;
    }
    
    public void setFeedbackComments(String feedbackComments) {
        this.feedbackComments = feedbackComments;
    }
    
    /**
     * Factory method to create an LLMMetadata instance from a request and response.
     * 
     * @param request The LLM request
     * @param response The LLM response
     * @return A new LLMMetadata instance
     */
    public static LLMMetadata fromRequestAndResponse(LLMRequest request, LLMResponse response) {
        LLMMetadata metadata = new LLMMetadata()
                .withId(request.getRequestId() + "-" + response.getResponseId())
                .withRequestId(request.getRequestId())
                .withResponseId(response.getResponseId())
                .withPrompt(request.getPrompt())
                .withResponse(response.getContent())
                .withTicketId(request.getTicketId())
                .withConversationId(request.getConversationId())
                .withMessageId(request.getMessageId())
                .withTimestamp(response.getTimestamp())
                .withLatencyMs(response.getLatencyMs())
                .withPromptTokens(response.getPromptTokens())
                .withCompletionTokens(response.getCompletionTokens())
                .withTotalTokens(response.getTotalTokens())
                .withConfidence(response.getConfidence());
        
        if (request.getParameters() != null) {
            metadata.withTemperature(request.getParameters().getTemperature())
                   .withMaxTokens(request.getParameters().getMaxTokens())
                   .withTopP(request.getParameters().getTopP());
        }
        
        if (response.getModelInfo() != null) {
            metadata.withModelName(response.getModelInfo().getName())
                   .withModelVersion(response.getModelInfo().getVersion())
                   .withProvider(response.getModelInfo().getProvider());
        }
        
        if (response.getUsageMetrics() != null) {
            metadata.withEstimatedCost(response.getUsageMetrics().getEstimatedCost());
        }
        
        if (request.getKnowledgeSources() != null) {
            metadata.withKnowledgeSources(String.join(",", request.getKnowledgeSources()));
        }
        
        return metadata;
    }
} 