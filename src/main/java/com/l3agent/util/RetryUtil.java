package com.l3agent.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.Callable;
import java.util.function.Predicate;

/**
 * Utility class for handling retries with configurable backoff.
 * Provides a standardized way to implement retry logic across the application.
 */
@Component
public class RetryUtil {
    private static final Logger logger = LoggerFactory.getLogger(RetryUtil.class);

    /**
     * Execute a callable with retry logic.
     *
     * @param operation The operation to execute
     * @param retryConfig The retry configuration
     * @param retryableExceptionPredicate Predicate to determine if an exception is retryable
     * @param <T> The return type of the operation
     * @return The result of the operation
     * @throws Exception If the operation fails after all retries
     */
    public <T> T executeWithRetry(
            Callable<T> operation,
            RetryConfig retryConfig,
            Predicate<Exception> retryableExceptionPredicate) throws Exception {
        
        int attempts = 0;
        Exception lastException = null;
        
        while (attempts < retryConfig.getMaxAttempts()) {
            try {
                if (attempts > 0) {
                    logger.info("Retry attempt {} of {}", attempts, retryConfig.getMaxAttempts() - 1);
                }
                return operation.call();
            } catch (Exception e) {
                lastException = e;
                
                // Check if the exception is retryable
                if (!retryableExceptionPredicate.test(e)) {
                    logger.info("Exception is not retryable, aborting retry: {}", e.getMessage());
                    throw e;
                }
                
                attempts++;
                
                // If we've reached the max attempts, throw the last exception
                if (attempts >= retryConfig.getMaxAttempts()) {
                    logger.warn("Failed after {} retry attempts", retryConfig.getMaxAttempts() - 1);
                    throw e;
                }
                
                // Calculate backoff time
                long backoffTime = calculateBackoffTime(attempts, retryConfig);
                logger.info("Retrying in {} ms due to: {}", backoffTime, e.getMessage());
                
                try {
                    Thread.sleep(backoffTime);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Retry interrupted", ie);
                }
            }
        }
        
        // This should never happen, but just in case
        throw lastException != null ? lastException : new RuntimeException("Retry failed for unknown reason");
    }
    
    /**
     * Calculate the backoff time for the current retry attempt.
     *
     * @param attempt The current attempt number (1-based)
     * @param retryConfig The retry configuration
     * @return The backoff time in milliseconds
     */
    private long calculateBackoffTime(int attempt, RetryConfig retryConfig) {
        if (retryConfig.isExponentialBackoff()) {
            // Calculate exponential backoff with jitter
            double exponentialFactor = Math.pow(retryConfig.getBackoffMultiplier(), attempt - 1);
            long backoffTime = (long) (retryConfig.getInitialBackoffMs() * exponentialFactor);
            
            // Add jitter (0-20% randomness)
            if (retryConfig.isJitter()) {
                double jitterFactor = 1.0 + (Math.random() * 0.2);
                backoffTime = (long) (backoffTime * jitterFactor);
            }
            
            // Ensure we don't exceed max backoff
            return Math.min(backoffTime, retryConfig.getMaxBackoffMs());
        } else {
            // Linear backoff
            return retryConfig.getInitialBackoffMs();
        }
    }
    
    /**
     * Configuration class for retry parameters.
     */
    public static class RetryConfig {
        private final int maxAttempts;
        private final long initialBackoffMs;
        private final double backoffMultiplier;
        private final long maxBackoffMs;
        private final boolean exponentialBackoff;
        private final boolean jitter;
        
        private RetryConfig(Builder builder) {
            this.maxAttempts = builder.maxAttempts;
            this.initialBackoffMs = builder.initialBackoffMs;
            this.backoffMultiplier = builder.backoffMultiplier;
            this.maxBackoffMs = builder.maxBackoffMs;
            this.exponentialBackoff = builder.exponentialBackoff;
            this.jitter = builder.jitter;
        }
        
        public int getMaxAttempts() {
            return maxAttempts;
        }
        
        public long getInitialBackoffMs() {
            return initialBackoffMs;
        }
        
        public double getBackoffMultiplier() {
            return backoffMultiplier;
        }
        
        public long getMaxBackoffMs() {
            return maxBackoffMs;
        }
        
        public boolean isExponentialBackoff() {
            return exponentialBackoff;
        }
        
        public boolean isJitter() {
            return jitter;
        }
        
        public static Builder builder() {
            return new Builder();
        }
        
        public static class Builder {
            private int maxAttempts = 3;
            private long initialBackoffMs = 200;
            private double backoffMultiplier = 2.0;
            private long maxBackoffMs = 2000;
            private boolean exponentialBackoff = true;
            private boolean jitter = true;
            
            public Builder maxAttempts(int maxAttempts) {
                this.maxAttempts = maxAttempts;
                return this;
            }
            
            public Builder initialBackoffMs(long initialBackoffMs) {
                this.initialBackoffMs = initialBackoffMs;
                return this;
            }
            
            public Builder backoffMultiplier(double backoffMultiplier) {
                this.backoffMultiplier = backoffMultiplier;
                return this;
            }
            
            public Builder maxBackoffMs(long maxBackoffMs) {
                this.maxBackoffMs = maxBackoffMs;
                return this;
            }
            
            public Builder exponentialBackoff(boolean exponentialBackoff) {
                this.exponentialBackoff = exponentialBackoff;
                return this;
            }
            
            public Builder jitter(boolean jitter) {
                this.jitter = jitter;
                return this;
            }
            
            public RetryConfig build() {
                return new RetryConfig(this);
            }
        }
    }
} 