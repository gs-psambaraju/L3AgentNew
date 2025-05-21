package com.l3agent.mcp.util;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Utility class for executing operations with retry logic and timeouts.
 */
public class RetryUtil {
    
    /**
     * Executes a callable with a timeout.
     * 
     * @param <T> The return type of the callable
     * @param callable The callable to execute
     * @param timeoutSeconds The timeout in seconds
     * @return The result of the callable
     * @throws Exception If the callable throws an exception or times out
     */
    public static <T> T withTimeout(Callable<T> callable, int timeoutSeconds) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<T> future = executor.submit(callable);
        
        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new Exception("Operation timed out after " + timeoutSeconds + " seconds");
        } catch (ExecutionException e) {
            // If the callable threw an exception, unwrap it
            Throwable cause = e.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            throw new Exception("Execution failed: " + e.getMessage(), cause);
        } finally {
            executor.shutdownNow();
        }
    }
    
    /**
     * Executes a callable with retries.
     * 
     * @param <T> The return type of the callable
     * @param callable The callable to execute
     * @param maxRetries The maximum number of retries
     * @param retryDelayMs The delay between retries in milliseconds
     * @return The result of the callable
     * @throws Exception If the callable throws an exception after all retries
     */
    public static <T> T withRetry(Callable<T> callable, int maxRetries, long retryDelayMs) throws Exception {
        int retries = 0;
        Exception lastException = null;
        
        while (retries <= maxRetries) {
            try {
                return callable.call();
            } catch (Exception e) {
                lastException = e;
                retries++;
                
                if (retries <= maxRetries) {
                    Thread.sleep(retryDelayMs);
                }
            }
        }
        
        throw new Exception("Operation failed after " + maxRetries + " retries", lastException);
    }
    
    /**
     * Executes a callable with both retries and a timeout.
     * 
     * @param <T> The return type of the callable
     * @param callable The callable to execute
     * @param maxRetries The maximum number of retries
     * @param retryDelayMs The delay between retries in milliseconds
     * @param timeoutSeconds The timeout for each attempt in seconds
     * @return The result of the callable
     * @throws Exception If the callable throws an exception after all retries or times out
     */
    public static <T> T withRetryAndTimeout(Callable<T> callable, int maxRetries, long retryDelayMs, int timeoutSeconds) throws Exception {
        return withRetry(() -> withTimeout(callable, timeoutSeconds), maxRetries, retryDelayMs);
    }
} 