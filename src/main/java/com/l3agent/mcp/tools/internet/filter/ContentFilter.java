package com.l3agent.mcp.tools.internet.filter;

import com.l3agent.mcp.tools.internet.model.WebContent;

/**
 * Interface for filtering web content.
 */
public interface ContentFilter {
    
    /**
     * Filters the content based on the specified criteria.
     * 
     * @param content The content to filter
     * @param criteria The filter criteria
     * @return The filtered content
     */
    WebContent filter(WebContent content, String criteria);
    
    /**
     * Filters the content based on a search query.
     * 
     * @param content The content to filter
     * @param query The search query
     * @return The filtered content
     */
    WebContent filterByQuery(WebContent content, String query);
} 