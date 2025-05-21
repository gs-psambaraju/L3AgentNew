package com.l3agent.mcp.tools.internet.fetcher;

import com.l3agent.mcp.tools.internet.config.InternetDataConfig;
import com.l3agent.mcp.tools.internet.model.WebContent;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.time.Instant;

/**
 * Implementation of DataFetcher that uses HTTP to fetch content.
 */
@Component
public class HttpDataFetcher implements DataFetcher {
    
    private static final Logger logger = LoggerFactory.getLogger(HttpDataFetcher.class);
    
    private final InternetDataConfig config;
    
    /**
     * Creates a new HttpDataFetcher with the specified configuration.
     * 
     * @param config The configuration
     */
    @Autowired
    public HttpDataFetcher(InternetDataConfig config) {
        this.config = config;
    }
    
    @Override
    public WebContent fetch(String url) throws Exception {
        logger.debug("Fetching data from URL: {}", url);
        
        // Build the connection
        Document doc;
        try {
            if (config.isUseProxy() && config.getProxyHost() != null && !config.getProxyHost().isEmpty()) {
                // Use proxy if configured
                Proxy proxy = new Proxy(Proxy.Type.HTTP, 
                        new InetSocketAddress(config.getProxyHost(), config.getProxyPort()));
                
                doc = Jsoup.connect(url)
                        .userAgent(config.getUserAgent())
                        .timeout(config.getTimeoutSeconds() * 1000)
                        .maxBodySize(config.getMaxContentLength())
                        .proxy(proxy)
                        .get();
            } else {
                // No proxy
                doc = Jsoup.connect(url)
                        .userAgent(config.getUserAgent())
                        .timeout(config.getTimeoutSeconds() * 1000)
                        .maxBodySize(config.getMaxContentLength())
                        .get();
            }
        } catch (Exception e) {
            logger.error("Error fetching data from URL: {}", url, e);
            throw new Exception("Failed to fetch content: " + e.getMessage(), e);
        }
        
        // Extract the important parts of the document
        String title = doc.title();
        String content = extractContent(doc);
        String source = extractSource(doc, url);
        
        logger.debug("Successfully fetched data from URL: {}, title: {}, content length: {}", 
                url, title, content.length());
        
        // Create the WebContent object
        WebContent webContent = new WebContent();
        webContent.setUrl(url);
        webContent.setTitle(title);
        webContent.setContent(content);
        webContent.setSource(source);
        webContent.setTimestamp(Instant.now());
        
        return webContent;
    }
    
    /**
     * Extracts the main content from the document.
     * 
     * @param doc The document
     * @return The main content
     */
    private String extractContent(Document doc) {
        // Remove script and style elements
        doc.select("script, style, noscript, iframe, img").remove();
        
        // Try to find the main content container
        String content;
        
        // Try common content containers
        if (doc.select("main").size() > 0) {
            content = doc.select("main").text();
        } else if (doc.select("article").size() > 0) {
            content = doc.select("article").text();
        } else if (doc.select("#content").size() > 0) {
            content = doc.select("#content").text();
        } else if (doc.select(".content").size() > 0) {
            content = doc.select(".content").text();
        } else if (doc.select("#main").size() > 0) {
            content = doc.select("#main").text();
        } else if (doc.select(".main").size() > 0) {
            content = doc.select(".main").text();
        } else {
            // Fall back to body content
            content = doc.body().text();
        }
        
        return content;
    }
    
    /**
     * Extracts the source of the content.
     * 
     * @param doc The document
     * @param url The URL
     * @return The source
     */
    private String extractSource(Document doc, String url) {
        // Try to get the source from meta tags
        String source = null;
        
        // Try common meta tags
        if (doc.select("meta[property=og:site_name]").size() > 0) {
            source = doc.select("meta[property=og:site_name]").attr("content");
        } else if (doc.select("meta[name=application-name]").size() > 0) {
            source = doc.select("meta[name=application-name]").attr("content");
        }
        
        // If no source found, extract from URL
        if (source == null || source.isEmpty()) {
            String domain = extractDomain(url);
            source = domain.isEmpty() ? url : domain;
        }
        
        return source;
    }
    
    /**
     * Extracts the domain from a URL.
     * 
     * @param url The URL
     * @return The domain
     */
    private String extractDomain(String url) {
        if (url == null || url.isEmpty()) {
            return "";
        }
        
        // Remove protocol if present
        String domain = url.toLowerCase();
        if (domain.startsWith("http://")) {
            domain = domain.substring(7);
        } else if (domain.startsWith("https://")) {
            domain = domain.substring(8);
        }
        
        // Remove path and query parameters
        int pathStart = domain.indexOf('/');
        if (pathStart > 0) {
            domain = domain.substring(0, pathStart);
        }
        
        return domain;
    }
} 