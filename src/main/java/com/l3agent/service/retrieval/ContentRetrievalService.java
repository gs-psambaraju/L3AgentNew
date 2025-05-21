package com.l3agent.service.retrieval;

import com.l3agent.model.EmbeddingMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for retrieving relevant content using configurable search strategies.
 * Manages different retrieval strategies and provides a unified interface.
 */
@Service
public class ContentRetrievalService {
    
    private static final Logger logger = LoggerFactory.getLogger(ContentRetrievalService.class);
    
    private final Map<String, RetrievalStrategy> strategies = new HashMap<>();
    
    // Default strategy to use if none specified
    private static final String DEFAULT_STRATEGY = "hybrid";
    
    @Autowired
    public ContentRetrievalService(List<RetrievalStrategy> availableStrategies) {
        // Register all available strategies
        for (RetrievalStrategy strategy : availableStrategies) {
            strategies.put(strategy.getStrategyName(), strategy);
            logger.info("Registered retrieval strategy: {}", strategy.getStrategyName());
        }
        
        if (!strategies.containsKey(DEFAULT_STRATEGY)) {
            logger.warn("Default retrieval strategy '{}' not found. Will use first available strategy if needed.", 
                    DEFAULT_STRATEGY);
        }
    }
    
    /**
     * Retrieves content using the specified or default strategy.
     * 
     * @param query The retrieval query
     * @param embeddings Map of IDs to embedding vectors
     * @param metadataMap Map of IDs to metadata
     * @param maxResults Maximum number of results to return
     * @param strategyName Name of strategy to use (optional)
     * @return List of content IDs matching the query
     */
    public List<String> retrieveContent(
            RetrievalQuery query,
            Map<String, float[]> embeddings,
            Map<String, EmbeddingMetadata> metadataMap,
            int maxResults,
            String strategyName) {
        
        // Enhanced logging to show data types being searched
        if (query.hasEmbedding()) {
            logger.info("Searching for query: '{}' using embeddings across {} candidates", 
                    query.getText(), embeddings.size());
        } else {
            logger.info("Searching for query: '{}' using text matching across {} candidates", 
                    query.getText(), metadataMap.size());
        }
        
        // Log metadata types being searched
        Map<String, Integer> metadataTypeCount = new HashMap<>();
        metadataMap.values().forEach(metadata -> {
            String type = metadata.getType();
            metadataTypeCount.put(type, metadataTypeCount.getOrDefault(type, 0) + 1);
        });
        
        logger.info("Content being searched: Code Snippets={}, Knowledge Graph={}, Code Explanations={}", 
                metadataTypeCount.getOrDefault("method", 0) + metadataTypeCount.getOrDefault("class", 0),
                metadataTypeCount.getOrDefault("entity", 0) + metadataTypeCount.getOrDefault("relationship", 0),
                metadataTypeCount.getOrDefault("explanation", 0) + metadataTypeCount.getOrDefault("description", 0));
        
        // Select strategy
        RetrievalStrategy strategy;
        if (strategyName != null && strategies.containsKey(strategyName)) {
            strategy = strategies.get(strategyName);
        } else {
            if (strategyName != null) {
                logger.warn("Strategy '{}' not found, using default", strategyName);
            }
            
            // Use DEFAULT_STRATEGY if available, otherwise use first available
            strategy = strategies.getOrDefault(DEFAULT_STRATEGY, 
                    strategies.values().iterator().next());
        }
        
        try {
            logger.info("Using retrieval strategy: {}", strategy.getStrategyName());
            List<String> results = strategy.retrieveContent(query, embeddings, metadataMap, maxResults);
            
            // Log a summary of what was found
            logger.info("Search completed - found {} relevant items for query: '{}'", 
                    results.size(), query.getText());
            
            return results;
        } catch (Exception e) {
            logger.error("Error during content retrieval with strategy {}: {}", 
                    strategy.getStrategyName(), e.getMessage(), e);
            throw new RuntimeException("Content retrieval failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Retrieves content using the default strategy.
     */
    public List<String> retrieveContent(
            RetrievalQuery query,
            Map<String, float[]> embeddings,
            Map<String, EmbeddingMetadata> metadataMap,
            int maxResults) {
        return retrieveContent(query, embeddings, metadataMap, maxResults, null);
    }
    
    /**
     * Get a list of available retrieval strategy names.
     */
    public List<String> getAvailableStrategies() {
        return List.copyOf(strategies.keySet());
    }
} 