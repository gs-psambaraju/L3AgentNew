package com.l3agent.mcp.tools.internet.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Configuration for the Internet Data Tool.
 */
@Configuration
@PropertySource("classpath:internet-data.properties")
public class InternetDataConfig {
    
    @Value("${l3agent.internet.trusted-domains}")
    private String trustedDomainsString;
    
    @Value("${l3agent.internet.max-cache-size:1000}")
    private int maxCacheSize;
    
    @Value("${l3agent.internet.cache-ttl-hours:24}")
    private int cacheTtlHours;
    
    @Value("${l3agent.internet.max-content-length:10485760}") // 10MB
    private int maxContentLength;
    
    @Value("${l3agent.internet.timeout-seconds:10}")
    private int timeoutSeconds;
    
    @Value("${l3agent.internet.max-retries:3}")
    private int maxRetries;
    
    @Value("${l3agent.internet.retry-delay-ms:1000}")
    private long retryDelayMs;
    
    @Value("${l3agent.internet.max-requests-per-minute:60}")
    private int maxRequestsPerMinute;
    
    @Value("${l3agent.internet.use-proxy:false}")
    private boolean useProxy;
    
    @Value("${l3agent.internet.proxy-host:}")
    private String proxyHost;
    
    @Value("${l3agent.internet.proxy-port:0}")
    private int proxyPort;
    
    @Value("${l3agent.internet.user-agent:L3Agent/1.0}")
    private String userAgent;
    
    /**
     * Gets the set of trusted domains that the tool is allowed to access.
     * 
     * @return The set of trusted domains
     */
    public Set<String> getTrustedDomains() {
        if (trustedDomainsString == null || trustedDomainsString.trim().isEmpty()) {
            return new HashSet<>();
        }
        
        String[] domains = trustedDomainsString.split(",");
        Set<String> trustedDomains = new HashSet<>();
        for (String domain : domains) {
            if (domain != null && !domain.trim().isEmpty()) {
                trustedDomains.add(domain.trim().toLowerCase());
            }
        }
        return trustedDomains;
    }
    
    /**
     * Gets the maximum number of items to store in the cache.
     * 
     * @return The maximum cache size
     */
    public int getMaxCacheSize() {
        return maxCacheSize;
    }
    
    /**
     * Gets the time-to-live for cached items in hours.
     * 
     * @return The cache TTL in hours
     */
    public int getCacheTtlHours() {
        return cacheTtlHours;
    }
    
    /**
     * Gets the maximum content length to retrieve in bytes.
     * 
     * @return The maximum content length
     */
    public int getMaxContentLength() {
        return maxContentLength;
    }
    
    /**
     * Gets the timeout for HTTP requests in seconds.
     * 
     * @return The timeout in seconds
     */
    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }
    
    /**
     * Gets the maximum number of retries for failed requests.
     * 
     * @return The maximum number of retries
     */
    public int getMaxRetries() {
        return maxRetries;
    }
    
    /**
     * Gets the delay between retries in milliseconds.
     * 
     * @return The retry delay in milliseconds
     */
    public long getRetryDelayMs() {
        return retryDelayMs;
    }
    
    /**
     * Gets the maximum number of requests allowed per minute.
     * 
     * @return The maximum number of requests per minute
     */
    public int getMaxRequestsPerMinute() {
        return maxRequestsPerMinute;
    }
    
    /**
     * Checks if a proxy should be used for HTTP requests.
     * 
     * @return true if a proxy should be used, false otherwise
     */
    public boolean isUseProxy() {
        return useProxy;
    }
    
    /**
     * Gets the proxy host.
     * 
     * @return The proxy host
     */
    public String getProxyHost() {
        return proxyHost;
    }
    
    /**
     * Gets the proxy port.
     * 
     * @return The proxy port
     */
    public int getProxyPort() {
        return proxyPort;
    }
    
    /**
     * Gets the User-Agent header to use for HTTP requests.
     * 
     * @return The User-Agent header value
     */
    public String getUserAgent() {
        return userAgent;
    }
} 