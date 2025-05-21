package com.l3agent.mcp.config;

import com.l3agent.util.RetryUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class for MCP retry settings.
 * Loads retry configuration from application properties.
 */
@Configuration
public class MCPRetryConfig {

    @Value("${l3agent.mcp.retry.enabled:true}")
    private boolean retryEnabled;
    
    @Value("${l3agent.mcp.retry.max-attempts:3}")
    private int maxAttempts;
    
    @Value("${l3agent.mcp.retry.initial-backoff-ms:200}")
    private long initialBackoffMs;
    
    @Value("${l3agent.mcp.retry.backoff-multiplier:2.0}")
    private double backoffMultiplier;
    
    @Value("${l3agent.mcp.retry.max-backoff-ms:2000}")
    private long maxBackoffMs;
    
    @Value("${l3agent.mcp.retry.exponential-backoff:true}")
    private boolean exponentialBackoff;
    
    @Value("${l3agent.mcp.retry.jitter:true}")
    private boolean jitter;
    
    /**
     * Creates a RetryConfig bean for MCP tool execution.
     * 
     * @return The configured RetryConfig
     */
    @Bean
    public RetryUtil.RetryConfig mcpToolRetryConfig() {
        if (!retryEnabled) {
            // If retry is disabled, use a config with just 1 attempt
            return RetryUtil.RetryConfig.builder()
                    .maxAttempts(1)
                    .build();
        }
        
        return RetryUtil.RetryConfig.builder()
                .maxAttempts(maxAttempts)
                .initialBackoffMs(initialBackoffMs)
                .backoffMultiplier(backoffMultiplier)
                .maxBackoffMs(maxBackoffMs)
                .exponentialBackoff(exponentialBackoff)
                .jitter(jitter)
                .build();
    }
} 