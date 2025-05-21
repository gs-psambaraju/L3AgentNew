package com.l3agent.mcp.tools.internet.cache;

import com.l3agent.mcp.tools.internet.model.WebContent;

/**
 * Interface for caching web content.
 */
public interface InternetDataCache {
    
    /**
     * Gets content from the cache.
     * 
     * @param url The URL to get content for
     * @return The cached content, or null if not in cache or expired
     */
    WebContent get(String url);
    
    /**
     * Puts content in the cache.
     * 
     * @param url The URL to cache content for
     * @param content The content to cache
     */
    void put(String url, WebContent content);
    
    /**
     * Removes content from the cache.
     * 
     * @param url The URL to remove content for
     */
    void remove(String url);
    
    /**
     * Clears all content from the cache.
     */
    void clear();
    
    /**
     * Gets the number of items in the cache.
     * 
     * @return The number of items
     */
    int size();
} 