package com.l3agent.repository;

import com.l3agent.model.llm.LLMMetadata;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for LLM metadata operations.
 */
@Repository
@ConditionalOnProperty(name = "l3agent.database.enabled", havingValue = "true", matchIfMissing = true)
public interface LLMMetadataRepository extends JpaRepository<LLMMetadata, String> {
    
    /**
     * Finds metadata by request ID.
     * 
     * @param requestId The request ID to search for
     * @return The metadata if found
     */
    Optional<LLMMetadata> findByRequestId(String requestId);
    
    /**
     * Finds metadata by response ID.
     * 
     * @param responseId The response ID to search for
     * @return The metadata if found
     */
    Optional<LLMMetadata> findByResponseId(String responseId);
    
    /**
     * Finds all metadata for a specific ticket.
     * 
     * @param ticketId The ticket ID to search for
     * @return A list of metadata entries for the ticket
     */
    List<LLMMetadata> findByTicketId(String ticketId);
    
    /**
     * Finds all metadata for a specific conversation.
     * 
     * @param conversationId The conversation ID to search for
     * @return A list of metadata entries for the conversation
     */
    List<LLMMetadata> findByConversationId(String conversationId);
    
    /**
     * Finds all metadata for a specific message.
     * 
     * @param messageId The message ID to search for
     * @return A list of metadata entries for the message
     */
    List<LLMMetadata> findByMessageId(String messageId);
    
    /**
     * Finds all metadata for a specific model.
     * 
     * @param modelName The model name to search for
     * @return A list of metadata entries for the model
     */
    List<LLMMetadata> findByModelName(String modelName);
    
    /**
     * Finds all metadata for a specific provider.
     * 
     * @param provider The provider name to search for
     * @return A list of metadata entries for the provider
     */
    List<LLMMetadata> findByProvider(String provider);
    
    /**
     * Finds all metadata within a time range.
     * 
     * @param start The start of the time range
     * @param end The end of the time range
     * @return A list of metadata entries within the time range
     */
    List<LLMMetadata> findByTimestampBetween(Instant start, Instant end);
    
    /**
     * Calculates the average latency for a specific model.
     * 
     * @param modelName The model name to calculate for
     * @return The average latency in milliseconds
     */
    @Query("SELECT AVG(m.latencyMs) FROM LLMMetadata m WHERE m.modelName = :modelName")
    Double calculateAverageLatencyForModel(@Param("modelName") String modelName);
    
    /**
     * Calculates the average token usage for a specific model.
     * 
     * @param modelName The model name to calculate for
     * @return The average total tokens used
     */
    @Query("SELECT AVG(m.totalTokens) FROM LLMMetadata m WHERE m.modelName = :modelName")
    Double calculateAverageTokensForModel(@Param("modelName") String modelName);
    
    /**
     * Finds metadata with a specific feedback rating.
     * 
     * @param rating The feedback rating to search for
     * @return A list of metadata entries with the specified rating
     */
    List<LLMMetadata> findByFeedbackRating(Integer rating);
    
    /**
     * Calculates the average feedback rating for a specific model.
     * 
     * @param modelName The model name to calculate for
     * @return The average feedback rating
     */
    @Query("SELECT AVG(m.feedbackRating) FROM LLMMetadata m WHERE m.modelName = :modelName AND m.feedbackRating IS NOT NULL")
    Double calculateAverageFeedbackRatingForModel(@Param("modelName") String modelName);
    
    /**
     * Searches for metadata entries containing a specific text in the prompt.
     * 
     * @param text The text to search for
     * @return A list of metadata entries matching the search
     */
    @Query("SELECT m FROM LLMMetadata m WHERE m.prompt LIKE %:text%")
    List<LLMMetadata> searchPromptText(@Param("text") String text);
    
    /**
     * Searches for metadata entries containing a specific text in the response.
     * 
     * @param text The text to search for
     * @return A list of metadata entries matching the search
     */
    @Query("SELECT m FROM LLMMetadata m WHERE m.response LIKE %:text%")
    List<LLMMetadata> searchResponseText(@Param("text") String text);
} 