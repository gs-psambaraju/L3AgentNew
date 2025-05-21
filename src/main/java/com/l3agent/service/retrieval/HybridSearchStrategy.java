package com.l3agent.service.retrieval;

import com.l3agent.model.EmbeddingMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementation of RetrievalStrategy using a hybrid approach.
 * Combines results from both semantic and keyword search strategies
 * with dynamic weighting based on query type.
 */
@Component
public class HybridSearchStrategy implements RetrievalStrategy {
    
    private static final Logger logger = LoggerFactory.getLogger(HybridSearchStrategy.class);
    
    private final SemanticSearchStrategy semanticStrategy;
    private final KeywordSearchStrategy keywordStrategy;
    
    // Default weights
    private static final float DEFAULT_SEMANTIC_WEIGHT = 0.7f;
    private static final float DEFAULT_KEYWORD_WEIGHT = 0.3f;
    
    // Dynamic weights based on query type
    private static final float CONCEPTUAL_SEMANTIC_WEIGHT = 0.8f;
    private static final float IMPLEMENTATION_SEMANTIC_WEIGHT = 0.6f;
    
    @Autowired
    public HybridSearchStrategy(
            SemanticSearchStrategy semanticStrategy,
            KeywordSearchStrategy keywordStrategy) {
        this.semanticStrategy = semanticStrategy;
        this.keywordStrategy = keywordStrategy;
    }
    
    @Override
    public List<String> retrieveContent(
            RetrievalQuery query,
            Map<String, float[]> embeddings,
            Map<String, EmbeddingMetadata> metadataMap,
            int maxResults) {
        
        // If we only have one type of query data, delegate to the appropriate strategy
        if (query.hasEmbedding() && !query.hasText()) {
            return semanticStrategy.retrieveContent(query, embeddings, metadataMap, maxResults);
        } else if (query.hasText() && !query.hasEmbedding()) {
            return keywordStrategy.retrieveContent(query, embeddings, metadataMap, maxResults);
        }
        
        // For hybrid search, get results from both strategies with a higher max
        // to ensure we have enough candidates to re-rank
        int extendedMaxResults = maxResults * 2;
        
        List<String> semanticResults = Collections.emptyList();
        List<String> keywordResults = Collections.emptyList();
        
        // Get semantic search results if embedding is available
        if (query.hasEmbedding()) {
            semanticResults = semanticStrategy.retrieveContent(
                    query, embeddings, metadataMap, extendedMaxResults);
            logger.debug("Semantic strategy returned {} results", semanticResults.size());
        }
        
        // Get keyword search results if text is available
        if (query.hasText()) {
            keywordResults = keywordStrategy.retrieveContent(
                    query, embeddings, metadataMap, extendedMaxResults);
            logger.debug("Keyword strategy returned {} results", keywordResults.size());
        }
        
        // Determine weights based on query type
        float semanticWeight;
        float keywordWeight;
        
        switch (query.getQueryType()) {
            case CONCEPTUAL:
                semanticWeight = CONCEPTUAL_SEMANTIC_WEIGHT;
                keywordWeight = 1.0f - CONCEPTUAL_SEMANTIC_WEIGHT;
                logger.debug("Using conceptual query weights: semantic={}, keyword={}", 
                        semanticWeight, keywordWeight);
                break;
            case IMPLEMENTATION:
                semanticWeight = IMPLEMENTATION_SEMANTIC_WEIGHT;
                keywordWeight = 1.0f - IMPLEMENTATION_SEMANTIC_WEIGHT;
                logger.debug("Using implementation query weights: semantic={}, keyword={}", 
                        semanticWeight, keywordWeight);
                break;
            default:
                semanticWeight = DEFAULT_SEMANTIC_WEIGHT;
                keywordWeight = DEFAULT_KEYWORD_WEIGHT;
                logger.debug("Using default query weights: semantic={}, keyword={}", 
                        semanticWeight, keywordWeight);
                break;
        }
        
        // Combine and re-rank results
        Map<String, Float> combinedScores = new HashMap<>();
        Set<String> uniqueResults = new HashSet<>();
        
        // Score semantic results
        for (int i = 0; i < semanticResults.size(); i++) {
            String id = semanticResults.get(i);
            // Use reverse ranking score (higher rank = higher score)
            float score = (semanticResults.size() - i) * semanticWeight;
            combinedScores.put(id, score);
            uniqueResults.add(id);
        }
        
        // Score and combine keyword results
        for (int i = 0; i < keywordResults.size(); i++) {
            String id = keywordResults.get(i);
            // Use reverse ranking score (higher rank = higher score)
            float score = (keywordResults.size() - i) * keywordWeight;
            // Add to existing score if already present
            combinedScores.put(id, 
                    combinedScores.getOrDefault(id, 0.0f) + score);
            uniqueResults.add(id);
        }
        
        // Final ranking by combined score
        List<String> results = uniqueResults.stream()
                .sorted((a, b) -> Float.compare(
                        combinedScores.getOrDefault(b, 0.0f), 
                        combinedScores.getOrDefault(a, 0.0f)))
                .limit(maxResults)
                .collect(Collectors.toList());
        
        logger.info("Hybrid search found {} results (semantic: {}, keyword: {})", 
                results.size(), semanticResults.size(), keywordResults.size());
        
        if (!results.isEmpty() && !combinedScores.isEmpty()) {
            String topId = results.get(0);
            logger.debug("Top hybrid score: {}", combinedScores.get(topId));
        }
        
        return results;
    }
    
    @Override
    public String getStrategyName() {
        return "hybrid";
    }
} 