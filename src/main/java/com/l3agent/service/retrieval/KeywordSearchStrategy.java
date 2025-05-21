package com.l3agent.service.retrieval;

import com.l3agent.model.EmbeddingMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Implementation of RetrievalStrategy using keyword/text-based search.
 * Searches for text matches in code content and descriptions.
 */
@Component
public class KeywordSearchStrategy implements RetrievalStrategy {
    
    private static final Logger logger = LoggerFactory.getLogger(KeywordSearchStrategy.class);
    
    // Boost factor for matches in descriptions for conceptual queries
    private static final float DESCRIPTION_MATCH_BOOST = 1.5f;
    
    // Minimum length for fuzzy matching to avoid noise
    private static final int MIN_FUZZY_TERM_LENGTH = 3;
    
    @Override
    public List<String> retrieveContent(
            RetrievalQuery query,
            Map<String, float[]> embeddings,
            Map<String, EmbeddingMetadata> metadataMap,
            int maxResults) {
        
        if (!query.hasText()) {
            logger.warn("Keyword search attempted without text for content type: {}", 
                    query.getContentType());
            return Collections.emptyList();
        }
        
        String searchText = query.getText().toLowerCase();
        boolean isConceptualQuery = query.getQueryType() == RetrievalQuery.QueryType.CONCEPTUAL;
        
        // Extract search terms (skip common words)
        List<String> searchTerms = extractSearchTerms(searchText);
        
        // Calculate scores for each content item
        Map<String, Float> scores = new HashMap<>();
        
        for (Map.Entry<String, EmbeddingMetadata> entry : metadataMap.entrySet()) {
            String id = entry.getKey();
            EmbeddingMetadata metadata = entry.getValue();
            
            if (metadata.getContent() == null) {
                continue;
            }
            
            float score = 0;
            
            // Score based on content matches
            String content = metadata.getContent().toLowerCase();
            for (String term : searchTerms) {
                // Exact match
                if (content.contains(term)) {
                    score += 2.0f * countOccurrences(content, term);
                }
            }
            
            // Additional scoring for description matches
            if (metadata.getDescription() != null && !metadata.getDescription().isEmpty()) {
                String description = metadata.getDescription().toLowerCase();
                for (String term : searchTerms) {
                    if (description.contains(term)) {
                        float boost = isConceptualQuery ? DESCRIPTION_MATCH_BOOST : 1.0f;
                        score += boost * countOccurrences(description, term);
                    }
                }
            }
            
            // Additional scoring for purpose summary matches
            if (metadata.getPurposeSummary() != null && !metadata.getPurposeSummary().isEmpty()) {
                String purpose = metadata.getPurposeSummary().toLowerCase();
                for (String term : searchTerms) {
                    if (purpose.contains(term)) {
                        float boost = isConceptualQuery ? DESCRIPTION_MATCH_BOOST : 1.0f;
                        score += boost * 2.0f * countOccurrences(purpose, term);
                    }
                }
            }
            
            // Additional scoring for capability matches
            if (metadata.getCapabilities() != null && !metadata.getCapabilities().isEmpty()) {
                for (String capability : metadata.getCapabilities()) {
                    String lowerCapability = capability.toLowerCase();
                    for (String term : searchTerms) {
                        if (lowerCapability.contains(term)) {
                            float boost = isConceptualQuery ? DESCRIPTION_MATCH_BOOST : 1.0f;
                            score += boost * countOccurrences(lowerCapability, term);
                        }
                    }
                }
            }
            
            // If we have matching score, add to results
            if (score > 0) {
                scores.put(id, score);
            }
        }
        
        // Sort by score (descending) and limit results
        List<String> results = scores.entrySet().stream()
                .sorted((a, b) -> Float.compare(b.getValue(), a.getValue()))
                .limit(maxResults)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        
        logger.info("Found {} keyword matches for {} using {} search terms", 
                results.size(), query.getContentType(), searchTerms.size());
        
        if (!results.isEmpty() && !scores.isEmpty()) {
            String topId = results.get(0);
            logger.debug("Top keyword match score: {}", scores.get(topId));
        }
        
        return results;
    }
    
    @Override
    public String getStrategyName() {
        return "keyword";
    }
    
    /**
     * Extracts meaningful search terms from the query text.
     */
    private List<String> extractSearchTerms(String text) {
        if (text == null || text.isEmpty()) {
            return Collections.emptyList();
        }
        
        // Split by non-alphanumeric characters
        String[] parts = text.toLowerCase().split("[^a-z0-9]+");
        
        // Filter out common words and short terms
        Set<String> stopWords = new HashSet<>(Arrays.asList(
                "the", "a", "an", "and", "or", "but", "is", "are", "was", "were", 
                "in", "on", "at", "to", "for", "with", "by", "about", "like", 
                "from", "of", "how", "what", "why", "when", "where", "who", "which"));
        
        List<String> terms = new ArrayList<>();
        for (String part : parts) {
            if (!stopWords.contains(part) && !part.isEmpty() && part.length() >= MIN_FUZZY_TERM_LENGTH) {
                terms.add(part);
            }
        }
        
        logger.debug("Extracted search terms: {}", terms);
        return terms;
    }
    
    /**
     * Counts occurrences of a substring in a string.
     */
    private int countOccurrences(String text, String substring) {
        Pattern pattern = Pattern.compile(Pattern.quote(substring));
        Matcher matcher = pattern.matcher(text);
        
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        
        return count;
    }
} 