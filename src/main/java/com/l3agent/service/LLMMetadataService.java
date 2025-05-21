package com.l3agent.service;

import com.l3agent.model.llm.LLMMetadata;
import com.l3agent.model.llm.LLMRequest;
import com.l3agent.model.llm.LLMResponse;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Service interface for managing LLM metadata.
 */
public interface LLMMetadataService {
    
    /**
     * Records metadata from a request and response pair.
     * 
     * @param request The LLM request
     * @param response The LLM response
     * @return The saved metadata
     */
    LLMMetadata recordInteraction(LLMRequest request, LLMResponse response);
    
    /**
     * Updates feedback for a specific interaction.
     * 
     * @param requestId The request ID of the interaction
     * @param rating The feedback rating (e.g., 1-5)
     * @param comments Additional feedback comments
     * @return The updated metadata if found, empty otherwise
     */
    Optional<LLMMetadata> updateFeedback(String requestId, int rating, String comments);
    
    /**
     * Retrieves metadata for a specific interaction by request ID.
     * 
     * @param requestId The request ID to search for
     * @return The metadata if found, empty otherwise
     */
    Optional<LLMMetadata> getMetadataByRequestId(String requestId);
    
    /**
     * Retrieves metadata for a specific interaction by response ID.
     * 
     * @param responseId The response ID to search for
     * @return The metadata if found, empty otherwise
     */
    Optional<LLMMetadata> getMetadataByResponseId(String responseId);
    
    /**
     * Retrieves all metadata for a specific ticket.
     * 
     * @param ticketId The ticket ID to search for
     * @return A list of metadata entries for the ticket
     */
    List<LLMMetadata> getMetadataForTicket(String ticketId);
    
    /**
     * Retrieves all metadata for a specific conversation.
     * 
     * @param conversationId The conversation ID to search for
     * @return A list of metadata entries for the conversation
     */
    List<LLMMetadata> getMetadataForConversation(String conversationId);
    
    /**
     * Retrieves all metadata for a specific model.
     * 
     * @param modelName The model name to search for
     * @return A list of metadata entries for the model
     */
    List<LLMMetadata> getMetadataForModel(String modelName);
    
    /**
     * Retrieves all metadata within a time range.
     * 
     * @param start The start of the time range
     * @param end The end of the time range
     * @return A list of metadata entries within the time range
     */
    List<LLMMetadata> getMetadataInTimeRange(Instant start, Instant end);
    
    /**
     * Calculates the average latency for a specific model.
     * 
     * @param modelName The model name to calculate for
     * @return The average latency in milliseconds
     */
    double getAverageLatencyForModel(String modelName);
    
    /**
     * Calculates the average token usage for a specific model.
     * 
     * @param modelName The model name to calculate for
     * @return The average total tokens used
     */
    double getAverageTokensForModel(String modelName);
    
    /**
     * Calculates the average feedback rating for a specific model.
     * 
     * @param modelName The model name to calculate for
     * @return The average feedback rating
     */
    double getAverageFeedbackRatingForModel(String modelName);
    
    /**
     * Searches for metadata entries based on prompt or response content.
     * 
     * @param searchText The text to search for
     * @param searchInPrompt Whether to search in prompts
     * @param searchInResponse Whether to search in responses
     * @return A list of metadata entries matching the search
     */
    List<LLMMetadata> searchMetadata(String searchText, boolean searchInPrompt, boolean searchInResponse);
} 