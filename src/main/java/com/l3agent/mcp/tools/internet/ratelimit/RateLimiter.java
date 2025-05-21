package com.l3agent.mcp.tools.internet.ratelimit;

import com.l3agent.mcp.tools.internet.config.InternetDataConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiter for HTTP requests to external domains.
 */
@Component
public class RateLimiter {
    
    private static final Logger logger = LoggerFactory.getLogger(RateLimiter.class);
    
    private final InternetDataConfig config;
    private final Map<String, DomainStats> domainStats;
    
    /**
     * Creates a new RateLimiter with the specified configuration.
     * 
     * @param config The configuration
     */
    @Autowired
    public RateLimiter(InternetDataConfig config) {
        this.config = config;
        this.domainStats = new ConcurrentHashMap<>();
        logger.info("Initialized rate limiter with limit of {} requests per minute per domain", 
                config.getMaxRequestsPerMinute());
    }
    
    /**
     * Checks if a request to the specified URL is allowed based on rate limits.
     * If allowed, the request count is incremented.
     * 
     * @param url The URL to check
     * @return true if allowed, false if rate limited
     */
    public synchronized boolean allowRequest(String url) {
        if (url == null || url.trim().isEmpty()) {
            return false;
        }
        
        // Get the domain from the URL
        String domain = extractDomain(url);
        if (domain.isEmpty()) {
            return false;
        }
        
        // Get or create stats for the domain
        DomainStats stats = domainStats.computeIfAbsent(domain, k -> new DomainStats());
        
        // Check if the domain has exceeded its rate limit
        Instant now = Instant.now();
        boolean allowed = stats.allowRequest(now, config.getMaxRequestsPerMinute());
        
        if (allowed) {
            logger.debug("Request to '{}' allowed, current count: {}", domain, stats.getRequestCount());
        } else {
            logger.warn("Request to '{}' denied due to rate limiting, current count: {}", 
                    domain, stats.getRequestCount());
        }
        
        return allowed;
    }
    
    /**
     * Scheduled task to log rate limiting statistics.
     * Runs every 5 minutes.
     */
    @Scheduled(fixedRate = 300000) // 5 minutes
    public void logStatistics() {
        if (domainStats.isEmpty()) {
            return;
        }
        
        logger.info("Rate limiting statistics:");
        for (Map.Entry<String, DomainStats> entry : domainStats.entrySet()) {
            logger.info("  Domain: {}, Requests: {}, Last reset: {}", 
                    entry.getKey(), entry.getValue().getRequestCount(), entry.getValue().getLastResetTime());
        }
    }
    
    /**
     * Extracts the domain from a URL.
     * 
     * @param url The URL to extract the domain from
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
    
    /**
     * Tracks request statistics for a domain.
     */
    private static class DomainStats {
        private int requestCount;
        private Instant lastResetTime;
        
        /**
         * Creates new domain statistics.
         */
        public DomainStats() {
            this.requestCount = 0;
            this.lastResetTime = Instant.now();
        }
        
        /**
         * Checks if a new request is allowed based on rate limits.
         * 
         * @param now The current time
         * @param maxRequestsPerMinute The maximum number of requests allowed per minute
         * @return true if allowed, false otherwise
         */
        public boolean allowRequest(Instant now, int maxRequestsPerMinute) {
            // Check if we need to reset the counter
            if (now.getEpochSecond() - lastResetTime.getEpochSecond() >= 60) {
                requestCount = 0;
                lastResetTime = now;
            }
            
            // Check if we're under the limit
            if (requestCount < maxRequestsPerMinute) {
                requestCount++;
                return true;
            }
            
            return false;
        }
        
        /**
         * Gets the current request count.
         * 
         * @return The request count
         */
        public int getRequestCount() {
            return requestCount;
        }
        
        /**
         * Gets the last reset time.
         * 
         * @return The last reset time
         */
        public Instant getLastResetTime() {
            return lastResetTime;
        }
    }
} 