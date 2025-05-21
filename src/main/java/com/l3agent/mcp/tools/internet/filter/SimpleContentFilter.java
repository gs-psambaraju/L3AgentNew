package com.l3agent.mcp.tools.internet.filter;

import com.l3agent.mcp.tools.internet.model.WebContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Simple implementation of ContentFilter that uses regex and keyword matching.
 */
@Component
public class SimpleContentFilter implements ContentFilter {
    
    private static final Logger logger = LoggerFactory.getLogger(SimpleContentFilter.class);
    
    private static final int CONTEXT_CHARS_BEFORE = 200;
    private static final int CONTEXT_CHARS_AFTER = 200;
    private static final String SECTION_SEPARATOR = "\n\n[...]\n\n";
    
    @Override
    public WebContent filter(WebContent content, String criteria) {
        if (content == null || criteria == null || criteria.trim().isEmpty()) {
            return content;
        }
        
        logger.debug("Filtering content with criteria: {}", criteria);
        
        // Split criteria by commas or spaces
        List<String> terms = Arrays.stream(criteria.split("[,\\s]+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
        
        if (terms.isEmpty()) {
            return content;
        }
        
        // Extract sections containing the criteria
        String originalContent = content.getContent();
        StringBuilder filteredContent = new StringBuilder();
        int matchCount = 0;
        
        for (String term : terms) {
            // Create case-insensitive pattern
            Pattern pattern = Pattern.compile(Pattern.quote(term), Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(originalContent);
            
            while (matcher.find()) {
                matchCount++;
                
                // Get match position
                int start = matcher.start();
                int end = matcher.end();
                
                // Calculate context boundaries
                int contextStart = Math.max(0, start - CONTEXT_CHARS_BEFORE);
                int contextEnd = Math.min(originalContent.length(), end + CONTEXT_CHARS_AFTER);
                
                // Find paragraph boundaries
                while (contextStart > 0 && !Character.isWhitespace(originalContent.charAt(contextStart - 1))) {
                    contextStart--;
                }
                
                while (contextEnd < originalContent.length() && !Character.isWhitespace(originalContent.charAt(contextEnd))) {
                    contextEnd++;
                }
                
                // Extract the context
                String context = originalContent.substring(contextStart, contextEnd);
                
                // Add the context to the filtered content
                if (filteredContent.length() > 0) {
                    filteredContent.append(SECTION_SEPARATOR);
                }
                filteredContent.append(context);
            }
        }
        
        // If no matches found, return a message
        if (matchCount == 0) {
            logger.debug("No matches found for criteria: {}", criteria);
            return content.createFilteredCopy(
                    "No content matching the criteria: " + criteria, 
                    criteria);
        }
        
        logger.debug("Found {} matches for criteria: {}", matchCount, criteria);
        return content.createFilteredCopy(filteredContent.toString(), criteria);
    }
    
    @Override
    public WebContent filterByQuery(WebContent content, String query) {
        if (content == null || query == null || query.trim().isEmpty()) {
            return content;
        }
        
        logger.debug("Filtering content by query: {}", query);
        
        // Prepare search terms: split by spaces, remove common words
        List<String> searchTerms = prepareSearchTerms(query);
        if (searchTerms.isEmpty()) {
            return content;
        }
        
        // Search for the terms in the content
        String originalContent = content.getContent();
        
        // Track sentences that contain search terms
        List<MatchedSegment> matchedSegments = new ArrayList<>();
        
        // Split content into sentences or paragraphs
        String[] segments = originalContent.split("[.!?\\n]");
        
        for (int i = 0; i < segments.length; i++) {
            String segment = segments[i].trim();
            if (segment.isEmpty()) {
                continue;
            }
            
            // Count term appearances in this segment
            int matchCount = 0;
            for (String term : searchTerms) {
                Pattern pattern = Pattern.compile("\\b" + Pattern.quote(term) + "\\b", 
                        Pattern.CASE_INSENSITIVE);
                Matcher matcher = pattern.matcher(segment);
                
                while (matcher.find()) {
                    matchCount++;
                }
            }
            
            // If the segment has matches, add it to the list
            if (matchCount > 0) {
                matchedSegments.add(new MatchedSegment(segment, matchCount, i));
            }
        }
        
        // Sort segments by match count (descending) and position (ascending)
        matchedSegments.sort((a, b) -> {
            if (a.matchCount != b.matchCount) {
                return Integer.compare(b.matchCount, a.matchCount); // Most matches first
            }
            return Integer.compare(a.position, b.position); // Earlier position first
        });
        
        // If no matches found, return a message
        if (matchedSegments.isEmpty()) {
            logger.debug("No matches found for query: {}", query);
            return content.createFilteredCopy(
                    "No content matching the query: " + query, 
                    query);
        }
        
        // Take the top matches and build the filtered content
        int maxSegments = Math.min(10, matchedSegments.size());
        StringBuilder filteredContent = new StringBuilder();
        
        for (int i = 0; i < maxSegments; i++) {
            if (i > 0) {
                filteredContent.append(SECTION_SEPARATOR);
            }
            filteredContent.append(matchedSegments.get(i).segment).append(".");
        }
        
        logger.debug("Found {} matching segments for query: {}", matchedSegments.size(), query);
        return content.createFilteredCopy(filteredContent.toString(), query);
    }
    
    /**
     * Prepares search terms from a query by removing common words and normalizing.
     * 
     * @param query The query to prepare
     * @return List of search terms
     */
    private List<String> prepareSearchTerms(String query) {
        // Define common words to ignore
        List<String> stopWords = Arrays.asList(
                "a", "an", "the", "and", "or", "but", "in", "on", "at", "to", "for", "with", 
                "about", "from", "by", "as", "of", "is", "are", "was", "were", "be", "been", 
                "being", "have", "has", "had", "do", "does", "did", "will", "would", "shall", 
                "should", "can", "could", "may", "might", "must", "that", "this", "these", 
                "those", "how", "what", "where", "when", "who", "which", "why");
        
        // Split query by spaces and filter
        return Arrays.stream(query.toLowerCase().split("\\s+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .filter(s -> s.length() > 2) // Skip very short words
                .filter(s -> !stopWords.contains(s.toLowerCase()))
                .collect(Collectors.toList());
    }
    
    /**
     * Represents a segment of text that matched search terms.
     */
    private static class MatchedSegment {
        private final String segment;
        private final int matchCount;
        private final int position;
        
        /**
         * Creates a new matched segment.
         * 
         * @param segment The matched text
         * @param matchCount The number of matches
         * @param position The original position
         */
        public MatchedSegment(String segment, int matchCount, int position) {
            this.segment = segment;
            this.matchCount = matchCount;
            this.position = position;
        }
    }
} 