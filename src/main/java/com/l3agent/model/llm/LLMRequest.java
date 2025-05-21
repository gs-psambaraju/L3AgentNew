package com.l3agent.model.llm;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Represents a request to a Large Language Model.
 * Encapsulates the prompt, parameters, and additional metadata.
 */
public class LLMRequest {

    private String requestId;
    private String prompt;
    private ModelParameters parameters;
    private Map<String, Object> metadata;
    private List<String> knowledgeSources;
    private Instant timestamp;
    private String ticketId;
    private String conversationId;
    private String messageId;
    
    // Default constructor
    public LLMRequest() {
        this.requestId = UUID.randomUUID().toString();
        this.timestamp = Instant.now();
        this.metadata = new HashMap<>();
    }
    
    // Constructor with prompt and parameters
    public LLMRequest(String prompt, ModelParameters parameters) {
        this();
        this.prompt = prompt;
        this.parameters = parameters;
    }
    
    // Builder pattern methods for fluent API
    
    public LLMRequest withPrompt(String prompt) {
        this.prompt = prompt;
        return this;
    }
    
    public LLMRequest withParameters(ModelParameters parameters) {
        this.parameters = parameters;
        return this;
    }
    
    public LLMRequest withMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
        return this;
    }
    
    public LLMRequest addMetadata(String key, Object value) {
        if (this.metadata == null) {
            this.metadata = new HashMap<>();
        }
        this.metadata.put(key, value);
        return this;
    }
    
    public LLMRequest withKnowledgeSources(List<String> knowledgeSources) {
        this.knowledgeSources = knowledgeSources;
        return this;
    }
    
    public LLMRequest withTicketId(String ticketId) {
        this.ticketId = ticketId;
        return this;
    }
    
    public LLMRequest withConversationId(String conversationId) {
        this.conversationId = conversationId;
        return this;
    }
    
    public LLMRequest withMessageId(String messageId) {
        this.messageId = messageId;
        return this;
    }
    
    // Getters and setters
    
    public String getRequestId() {
        return requestId;
    }
    
    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
    
    public String getPrompt() {
        return prompt;
    }
    
    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }
    
    public ModelParameters getParameters() {
        return parameters;
    }
    
    public void setParameters(ModelParameters parameters) {
        this.parameters = parameters;
    }
    
    public Map<String, Object> getMetadata() {
        return metadata;
    }
    
    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
    
    public List<String> getKnowledgeSources() {
        return knowledgeSources;
    }
    
    public void setKnowledgeSources(List<String> knowledgeSources) {
        this.knowledgeSources = knowledgeSources;
    }
    
    public Instant getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
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
    
    /**
     * Creates a new instance of LLMRequest with default parameters.
     * 
     * @param prompt The prompt to send to the LLM
     * @return A new LLMRequest instance
     */
    public static LLMRequest create(String prompt) {
        return new LLMRequest(prompt, ModelParameters.defaults());
    }
    
    /**
     * Creates a new instance of LLMRequest with specified model and parameters.
     * 
     * @param prompt The prompt to send to the LLM
     * @param modelName The name of the model to use
     * @return A new LLMRequest instance
     */
    public static LLMRequest create(String prompt, String modelName) {
        return new LLMRequest(prompt, new ModelParameters(modelName));
    }
    
    /**
     * Creates a new instance of LLMRequest for analytical tasks.
     * 
     * @param prompt The prompt to send to the LLM
     * @param modelName The name of the model to use
     * @return A new LLMRequest instance configured for analytical tasks
     */
    public static LLMRequest createAnalytical(String prompt, String modelName) {
        return new LLMRequest(prompt, ModelParameters.forAnalyticalTask(modelName));
    }
} 