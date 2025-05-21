package com.l3agent.service.retrieval;

import com.l3agent.model.EmbeddingMetadata;

import java.util.List;
import java.util.Map;

/**
 * Interface for content retrieval strategies.
 * Part of a strategy pattern for flexible search implementations.
 */
public interface RetrievalStrategy {
    
    /**
     * Retrieves content based on the query and available embeddings.
     * 
     * @param query The query containing search text and/or embedding
     * @param embeddings Map of content IDs to embedding vectors
     * @param metadataMap Map of content IDs to metadata
     * @param maxResults Maximum number of results to return
     * @return List of content IDs ordered by relevance
     */
    List<String> retrieveContent(
            RetrievalQuery query,
            Map<String, float[]> embeddings,
            Map<String, EmbeddingMetadata> metadataMap,
            int maxResults);
    
    /**
     * Gets the name of this retrieval strategy.
     * 
     * @return The strategy name
     */
    String getStrategyName();
} 