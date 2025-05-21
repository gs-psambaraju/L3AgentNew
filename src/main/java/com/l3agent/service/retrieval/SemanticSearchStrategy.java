package com.l3agent.service.retrieval;

import com.l3agent.model.EmbeddingMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Implementation of RetrievalStrategy using semantic search with vector similarity.
 * Uses dynamic thresholds based on query type and boosts results with descriptions.
 */
@Component
public class SemanticSearchStrategy implements RetrievalStrategy {
    
    private static final Logger logger = LoggerFactory.getLogger(SemanticSearchStrategy.class);
    
    // Dynamic similarity thresholds based on query type
    private static final float DEFAULT_SIMILARITY_THRESHOLD = 0.65f;
    private static final float CONCEPTUAL_SIMILARITY_THRESHOLD = 0.55f; // Lower threshold for conceptual
    private static final float IMPLEMENTATION_SIMILARITY_THRESHOLD = 0.70f; // Higher for implementation
    
    // Minimum vector dimension to avoid calculation errors
    private static final int MIN_VECTOR_DIMENSION = 64;
    
    // Boost factor for results with descriptions when query is conceptual
    private static final float DESCRIPTION_BOOST = 1.1f;
    
    @Override
    public List<String> retrieveContent(
            RetrievalQuery query,
            Map<String, float[]> embeddings,
            Map<String, EmbeddingMetadata> metadataMap,
            int maxResults) {
        
        // Validate query has embeddings
        if (!query.hasEmbedding()) {
            logger.warn("Semantic search attempted without an embedding for content type: {}", 
                    query.getContentType());
            return Collections.emptyList();
        }
        
        float[] queryEmbedding = query.getEmbedding();
        
        // Select appropriate similarity threshold based on query type
        float similarityThreshold;
        switch (query.getQueryType()) {
            case CONCEPTUAL:
                similarityThreshold = CONCEPTUAL_SIMILARITY_THRESHOLD;
                logger.debug("Using conceptual similarity threshold: {}", similarityThreshold);
                break;
            case IMPLEMENTATION:
                similarityThreshold = IMPLEMENTATION_SIMILARITY_THRESHOLD;
                logger.debug("Using implementation similarity threshold: {}", similarityThreshold);
                break;
            default:
                similarityThreshold = DEFAULT_SIMILARITY_THRESHOLD;
                logger.debug("Using default similarity threshold: {}", similarityThreshold);
                break;
        }
        
        // Calculate similarities and filter by threshold
        List<Map.Entry<String, Float>> similarities = new ArrayList<>();
        
        for (Map.Entry<String, float[]> entry : embeddings.entrySet()) {
            String id = entry.getKey();
            float similarity = cosineSimilarity(queryEmbedding, entry.getValue());
            
            // Apply boost for results with descriptions in conceptual queries
            if (query.getQueryType() == RetrievalQuery.QueryType.CONCEPTUAL) {
                EmbeddingMetadata metadata = metadataMap.get(id);
                if (metadata != null && metadata.getDescription() != null 
                        && !metadata.getDescription().isEmpty()) {
                    similarity *= DESCRIPTION_BOOST;
                    logger.debug("Boosted similarity for {} with description: {} -> {}", 
                            id, similarity / DESCRIPTION_BOOST, similarity);
                }
            }
            
            if (similarity >= similarityThreshold) {
                similarities.add(Map.entry(id, similarity));
            }
        }
        
        // Sort by similarity score (descending)
        similarities.sort((a, b) -> Float.compare(b.getValue(), a.getValue()));
        
        // Limit to maximum results
        List<String> results = similarities.stream()
                .limit(maxResults)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        
        logger.info("Found {} semantically similar results for {}", 
                results.size(), query.getContentType());
        
        if (!results.isEmpty()) {
            logger.debug("Top similarity score: {}", similarities.get(0).getValue());
        }
        
        return results;
    }
    
    @Override
    public String getStrategyName() {
        return "semantic";
    }
    
    /**
     * Calculates cosine similarity between two embedding vectors.
     */
    private float cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length || a.length < MIN_VECTOR_DIMENSION) {
            logger.warn("Invalid vectors for similarity calculation: a={}, b={}", 
                    a == null ? "null" : a.length, 
                    b == null ? "null" : b.length);
            return 0.0f;
        }
        
        float dotProduct = 0.0f;
        float normA = 0.0f;
        float normB = 0.0f;
        
        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        
        if (normA <= 0 || normB <= 0) {
            return 0.0f;
        }
        
        return dotProduct / (float) Math.sqrt(normA * normB);
    }
} 