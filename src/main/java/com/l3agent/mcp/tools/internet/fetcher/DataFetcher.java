package com.l3agent.mcp.tools.internet.fetcher;

import com.l3agent.mcp.tools.internet.model.WebContent;

/**
 * Interface for fetching data from the internet.
 */
public interface DataFetcher {
    
    /**
     * Fetches content from the specified URL.
     * 
     * @param url The URL to fetch content from
     * @return The web content
     * @throws Exception If an error occurs while fetching the content
     */
    WebContent fetch(String url) throws Exception;
} 