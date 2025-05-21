package com.l3agent.mcp.tools.internet.cache;

import com.l3agent.mcp.tools.internet.config.InternetDataConfig;
import com.l3agent.mcp.tools.internet.model.WebContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of InternetDataCache that stores content in memory.
 */
@Component
public class InMemoryInternetDataCache implements InternetDataCache {
    
    private static final Logger logger = LoggerFactory.getLogger(InMemoryInternetDataCache.class);
    
    private final InternetDataConfig config;
    private final Map<String, CacheEntry> cache;
    
    /**
     * Creates a new InMemoryInternetDataCache with the specified configuration.
     * 
     * @param config The configuration
     */
    @Autowired
    public InMemoryInternetDataCache(InternetDataConfig config) {
        this.config = config;
        this.cache = new ConcurrentHashMap<>();
        logger.info("Initialized in-memory internet data cache with max size: {}, TTL: {} hours", 
                config.getMaxCacheSize(), config.getCacheTtlHours());
    }
    
    @Override
    public WebContent get(String url) {
        // Normalize the URL
        String normalizedUrl = normalizeUrl(url);
        
        // Get the cache entry
        CacheEntry entry = cache.get(normalizedUrl);
        if (entry == null) {
            logger.debug("Cache miss for URL: {}", url);
            return null;
        }
        
        // Check if the entry is expired
        if (isExpired(entry)) {
            logger.debug("Cache entry expired for URL: {}", url);
            cache.remove(normalizedUrl);
            return null;
        }
        
        // Return a copy of the cached content
        logger.debug("Cache hit for URL: {}", url);
        WebContent content = entry.getContent();
        content.setFromCache(true);
        return content;
    }
    
    @Override
    public void put(String url, WebContent content) {
        // Normalize the URL
        String normalizedUrl = normalizeUrl(url);
        
        // Check if the cache is full
        if (cache.size() >= config.getMaxCacheSize() && !cache.containsKey(normalizedUrl)) {
            // Remove the oldest entry
            removeOldest();
        }
        
        // Add the entry to the cache
        CacheEntry entry = new CacheEntry(content);
        cache.put(normalizedUrl, entry);
        logger.debug("Added entry to cache for URL: {}, cache size: {}", url, cache.size());
    }
    
    @Override
    public void remove(String url) {
        // Normalize the URL
        String normalizedUrl = normalizeUrl(url);
        
        // Remove the entry
        cache.remove(normalizedUrl);
        logger.debug("Removed entry from cache for URL: {}, cache size: {}", url, cache.size());
    }
    
    @Override
    public void clear() {
        // Clear the cache
        cache.clear();
        logger.info("Cleared cache");
    }
    
    @Override
    public int size() {
        return cache.size();
    }
    
    /**
     * Scheduled task to clean up expired entries.
     * Runs every hour.
     */
    @Scheduled(fixedRate = 3600000) // 1 hour
    public void cleanupExpiredEntries() {
        logger.debug("Running cleanup of expired cache entries");
        
        int removedCount = 0;
        for (Map.Entry<String, CacheEntry> entry : cache.entrySet()) {
            if (isExpired(entry.getValue())) {
                cache.remove(entry.getKey());
                removedCount++;
            }
        }
        
        logger.info("Removed {} expired entries from cache, remaining: {}", 
                removedCount, cache.size());
    }
    
    /**
     * Normalizes a URL for use as a cache key.
     * 
     * @param url The URL to normalize
     * @return The normalized URL
     */
    private String normalizeUrl(String url) {
        if (url == null) {
            return "";
        }
        
        // Convert to lowercase
        String normalized = url.toLowerCase();
        
        // Remove trailing slash
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        
        return normalized;
    }
    
    /**
     * Checks if a cache entry is expired.
     * 
     * @param entry The cache entry
     * @return true if expired, false otherwise
     */
    private boolean isExpired(CacheEntry entry) {
        if (entry == null) {
            return true;
        }
        
        // Calculate expiration time
        Instant expirationTime = entry.getTimestamp()
                .plus(config.getCacheTtlHours(), ChronoUnit.HOURS);
        
        return expirationTime.isBefore(Instant.now());
    }
    
    /**
     * Removes the oldest entry from the cache.
     */
    private void removeOldest() {
        if (cache.isEmpty()) {
            return;
        }
        
        // Find the oldest entry
        String oldestKey = null;
        Instant oldestTime = Instant.now();
        
        for (Map.Entry<String, CacheEntry> entry : cache.entrySet()) {
            if (entry.getValue().getTimestamp().isBefore(oldestTime)) {
                oldestTime = entry.getValue().getTimestamp();
                oldestKey = entry.getKey();
            }
        }
        
        // Remove the oldest entry
        if (oldestKey != null) {
            cache.remove(oldestKey);
            logger.debug("Removed oldest cache entry with timestamp: {}", oldestTime);
        }
    }
    
    /**
     * Represents an entry in the cache.
     */
    private static class CacheEntry {
        private final WebContent content;
        private final Instant timestamp;
        
        /**
         * Creates a new cache entry.
         * 
         * @param content The web content
         */
        public CacheEntry(WebContent content) {
            this.content = content;
            this.timestamp = Instant.now();
        }
        
        /**
         * Gets the web content.
         * 
         * @return The web content
         */
        public WebContent getContent() {
            return content;
        }
        
        /**
         * Gets the timestamp when the entry was created.
         * 
         * @return The timestamp
         */
        public Instant getTimestamp() {
            return timestamp;
        }
    }
} 