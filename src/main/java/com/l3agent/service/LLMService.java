package com.l3agent.service;

import com.l3agent.model.llm.LLMRequest;
import com.l3agent.model.llm.LLMResponse;
import com.l3agent.model.llm.ModelParameters;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Service interface for Large Language Model (LLM) operations.
 * Abstracts different LLM providers behind a common interface.
 */
public interface LLMService {
    
    /**
     * Processes a prompt through the configured LLM and returns the response.
     * 
     * @param prompt The prompt text to send to the LLM
     * @param parameters Model parameters to customize the request
     * @param metadata Additional metadata to store with the request
     * @return The LLM response with content and metadata
     */
    LLMResponse processPrompt(String prompt, ModelParameters parameters, Map<String, Object> metadata);
    
    /**
     * Processes a prompt asynchronously and returns a future that will complete when the response is ready.
     * 
     * @param prompt The prompt text to send to the LLM
     * @param parameters Model parameters to customize the request
     * @param metadata Additional metadata to store with the request
     * @return A CompletableFuture that will complete with the LLM response
     */
    CompletableFuture<LLMResponse> processPromptAsync(String prompt, ModelParameters parameters, Map<String, Object> metadata);
    
    /**
     * Processes a structured LLM request and returns the response.
     * 
     * @param request The structured request containing prompt, parameters, and metadata
     * @return The LLM response with content and metadata
     */
    LLMResponse processRequest(LLMRequest request);
    
    /**
     * Processes a structured LLM request asynchronously.
     * 
     * @param request The structured request containing prompt, parameters, and metadata
     * @return A CompletableFuture that will complete with the LLM response
     */
    CompletableFuture<LLMResponse> processRequestAsync(LLMRequest request);
    
    /**
     * Gets the name of the currently configured LLM provider.
     * 
     * @return The provider name (e.g., "OpenAI", "Anthropic", etc.)
     */
    String getProviderName();
    
    /**
     * Gets the default model name for the current provider.
     * 
     * @return The default model name
     */
    String getDefaultModelName();
    
    /**
     * Checks if the LLM service is properly configured and available.
     * 
     * @return true if the service is available, false otherwise
     */
    boolean isAvailable();
} 