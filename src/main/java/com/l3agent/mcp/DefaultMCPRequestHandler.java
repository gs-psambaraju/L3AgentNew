package com.l3agent.mcp;

import com.l3agent.mcp.model.MCPRequest;
import com.l3agent.mcp.model.MCPResponse;
import com.l3agent.mcp.model.ToolExecutionStep;
import com.l3agent.mcp.model.ToolResponse;
import com.l3agent.util.RetryUtil;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * Default implementation of the MCPRequestHandler interface.
 * Manages tool registration, execution, and lifecycle.
 */
@Service
public class DefaultMCPRequestHandler implements MCPRequestHandler, DisposableBean {
    
    private static final Logger logger = LoggerFactory.getLogger(DefaultMCPRequestHandler.class);
    
    @Autowired
    private MCPToolRegistry toolRegistry;
    
    @Autowired
    private RetryUtil retryUtil;
    
    @Autowired
    private RetryUtil.RetryConfig mcpToolRetryConfig;
    
    @Value("${l3agent.mcp.max-concurrent-executions:20}")
    private int maxConcurrentExecutions;
    
    @Value("${l3agent.mcp.tool-execution-timeout-seconds:10}")
    private int toolExecutionTimeoutSeconds;
    
    @Value("${l3agent.mcp.thread-pool-queue-capacity:50}")
    private int threadPoolQueueCapacity;
    
    private ThreadPoolExecutor executorService;
    
    /**
     * Default constructor.
     */
    public DefaultMCPRequestHandler() {
        // Empty constructor - initialization moved to PostConstruct method
    }
    
    /**
     * Initialize the executor service after all properties have been set.
     * This ensures we have the correct configuration values from Spring.
     */
    @PostConstruct
    public void init() {
        int corePoolSize = Math.max(Runtime.getRuntime().availableProcessors(), 4);
        
        // Ensure we have valid values for the thread pool
        int actualMaxExecutions = Math.max(maxConcurrentExecutions, corePoolSize);
        int actualQueueCapacity = Math.max(threadPoolQueueCapacity, 1); // Minimum capacity of 1
        
        this.executorService = new ThreadPoolExecutor(
            corePoolSize,                  // Core pool size based on available processors 
            actualMaxExecutions,           // Max pool size from configuration
            60L, TimeUnit.SECONDS,         // Keep alive time for idle threads
            new ArrayBlockingQueue<>(actualQueueCapacity), // Bounded queue to prevent resource exhaustion
            new ThreadPoolExecutor.CallerRunsPolicy()  // Slow down the caller if queue is full
        );
        
        // Add JVM shutdown hook as additional safety measure
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("JVM shutdown detected, shutting down MCP executor service");
            shutdownExecutor();
        }));
        
        logger.info("Initialized MCP thread pool: coreSize={}, maxSize={}, queueCapacity={}",
                corePoolSize, actualMaxExecutions, actualQueueCapacity);
    }
    
    /**
     * Clean shutdown of executor service on bean destruction.
     * Implements Spring DisposableBean interface for proper lifecycle management.
     */
    @Override
    public void destroy() {
        logger.info("Shutting down MCP request handler");
        shutdownExecutor();
    }
    
    /**
     * Handles graceful shutdown of the executor service.
     * Attempts orderly shutdown first, then forces shutdown if needed.
     */
    private void shutdownExecutor() {
        executorService.shutdown(); // Disable new tasks from being submitted
        try {
            // Wait for existing tasks to terminate
            if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                // Cancel currently executing tasks
                executorService.shutdownNow();
                // Wait a while for tasks to respond to being cancelled
                if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                    logger.error("MCP executor service did not terminate");
                }
            }
        } catch (InterruptedException ie) {
            // (Re-)Cancel if current thread also interrupted
            executorService.shutdownNow();
            // Preserve interrupt status
            Thread.currentThread().interrupt();
        }
        logger.info("MCP executor service shutdown complete");
    }
    
    /**
     * Returns current thread pool metrics for monitoring purposes.
     * 
     * @return Map of thread pool metrics
     */
    public Map<String, Object> getThreadPoolMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("activeThreadCount", executorService.getActiveCount());
        metrics.put("poolSize", executorService.getPoolSize());
        metrics.put("corePoolSize", executorService.getCorePoolSize());
        metrics.put("maxPoolSize", executorService.getMaximumPoolSize());
        metrics.put("queueSize", executorService.getQueue().size());
        metrics.put("queueRemainingCapacity", executorService.getQueue().remainingCapacity());
        metrics.put("completedTaskCount", executorService.getCompletedTaskCount());
        metrics.put("taskCount", executorService.getTaskCount());
        return metrics;
    }
    
    @Override
    public MCPResponse process(MCPRequest request) {
        if (request == null) {
            logger.warn("Received null request");
            return createErrorResponse("Invalid request: Request cannot be null");
        }
        
        if (request.getQuery() == null || request.getQuery().trim().isEmpty()) {
            logger.warn("Received request with null or empty query");
            return createErrorResponse("Invalid request: Query cannot be null or empty");
        }
        
        logger.info("Processing request: {}", request.getQuery());
        
        List<ToolExecutionStep> executionPlan = request.getExecutionPlan();
        if (executionPlan == null || executionPlan.isEmpty()) {
            logger.info("Request has no execution plan, returning empty response");
            return new MCPResponse("No tools executed for query: " + request.getQuery());
        }
        
        // Sort the execution plan by priority (higher priority first)
        executionPlan.sort(Comparator.comparingInt(ToolExecutionStep::getPriority).reversed());
        
        List<ToolResponse> results = new ArrayList<>();
        boolean hadFailure = false;
        
        // Execute each step in the plan
        for (ToolExecutionStep step : executionPlan) {
            String toolName = step.getToolName();
            Optional<MCPToolInterface> toolOpt = toolRegistry.getTool(toolName);
            
            if (toolOpt.isEmpty()) {
                String errorMsg = "Tool not found: " + toolName;
                logger.warn(errorMsg);
                
                // Create error response for this step
                ToolResponse errorResponse = new ToolResponse(false, errorMsg, null);
                results.add(errorResponse);
                
                // Skip to next step if this one failed but was not required
                if (step.isRequired()) {
                    hadFailure = true;
                    break;
                } else {
                    continue;
                }
            }
            
            MCPToolInterface tool = toolOpt.get();
            Map<String, Object> params = step.getParameters() != null ? 
                                         step.getParameters() : 
                                         new HashMap<>();
            
            try {
                ToolResponse response = executeToolWithRetryAndTimeout(tool, params);
                results.add(response);
                
                // If the tool execution failed and it was required, stop processing
                if (!response.isSuccess() && step.isRequired()) {
                    hadFailure = true;
                    break;
                }
            } catch (RejectedExecutionException e) {
                String errorMsg = "Tool execution rejected (system overloaded): " + toolName;
                logger.error(errorMsg, e);
                
                ToolResponse errorResponse = new ToolResponse(false, errorMsg, null);
                errorResponse.addError("System overloaded: " + e.getMessage());
                errorResponse.addError("SYSTEM_OVERLOADED");
                results.add(errorResponse);
                
                if (step.isRequired()) {
                    hadFailure = true;
                    break;
                }
            } catch (Exception e) {
                String errorMsg = "Error executing tool " + toolName + ": " + e.getMessage();
                logger.error(errorMsg, e);
                
                ToolResponse errorResponse = new ToolResponse(false, errorMsg, null);
                errorResponse.addError(e.toString());
                errorResponse.addError(determineErrorCategory(e));
                results.add(errorResponse);
                
                if (step.isRequired()) {
                    hadFailure = true;
                    break;
                }
            }
        }
        
        // Build the final response
        MCPResponse response = new MCPResponse();
        response.setToolResults(results);
        
        if (hadFailure) {
            response.setAnswer("Failed to execute required tool");
            response.addMetadata("status", "failure");
            response.addMetadata("completedSteps", results.size());
            response.addMetadata("totalSteps", executionPlan.size());
        } else {
            response.setAnswer("Successfully executed all tools");
            response.addMetadata("status", "success");
            response.addMetadata("completedSteps", results.size());
            response.addMetadata("totalSteps", executionPlan.size());
            
            // Include thread pool metrics in metadata
            response.addMetadata("threadPoolMetrics", getThreadPoolMetrics());
        }
        
        return response;
    }
    
    /**
     * Categorizes exceptions into standardized error types for consistent handling.
     * 
     * @param e The exception to categorize
     * @return Standardized error category
     */
    private String determineErrorCategory(Exception e) {
        if (e instanceof TimeoutException) {
            return "EXECUTION_TIMEOUT";
        } else if (e instanceof RejectedExecutionException) {
            return "SYSTEM_OVERLOADED";
        } else if (e instanceof InterruptedException) {
            return "EXECUTION_INTERRUPTED";
        } else if (e instanceof IllegalArgumentException) {
            return "INVALID_PARAMETERS";
        } else if (e.getCause() instanceof OutOfMemoryError) {
            return "RESOURCE_EXHAUSTION";
        } else {
            return "EXECUTION_ERROR";
        }
    }
    
    /**
     * Determines if an exception is retryable based on its type and cause.
     * 
     * @param e The exception to check
     * @return true if the exception is retryable, false otherwise
     */
    private boolean isRetryableException(Exception e) {
        // Don't retry parameter validation errors
        if (e instanceof IllegalArgumentException) {
            return false;
        }
        
        // Don't retry if we're out of resources
        if (e instanceof RejectedExecutionException || 
            (e.getCause() instanceof OutOfMemoryError)) {
            return false;
        }
        
        // Don't retry timeout exceptions (already timed out once)
        if (e instanceof TimeoutException) {
            return false;
        }
        
        // Retry all other exceptions (network issues, temporary failures, etc.)
        return true;
    }
    
    /**
     * Executes a tool with retry logic and timeout.
     * 
     * @param tool The tool to execute
     * @param params The parameters to pass to the tool
     * @return The result of the tool execution
     * @throws Exception If the tool execution fails after all retries
     */
    private ToolResponse executeToolWithRetryAndTimeout(MCPToolInterface tool, Map<String, Object> params) throws Exception {
        try {
            return retryUtil.executeWithRetry(
                () -> executeToolWithTimeout(tool, params),
                mcpToolRetryConfig,
                this::isRetryableException
            );
        } catch (Exception e) {
            logger.error("Tool execution failed after retries: {}", tool.getName(), e);
            throw e;
        }
    }
    
    private ToolResponse executeToolWithTimeout(MCPToolInterface tool, Map<String, Object> params) throws Exception {
        CompletableFuture<ToolResponse> future = CompletableFuture.supplyAsync(() -> {
            try {
                logger.debug("Executing tool: {}", tool.getName());
                return tool.execute(params);
            } catch (Exception e) {
                logger.error("Exception in tool execution: {}", tool.getName(), e);
                ToolResponse errorResponse = new ToolResponse(false, "Exception during execution: " + e.getMessage(), null);
                errorResponse.addError(e.toString());
                errorResponse.addError(determineErrorCategory(e));
                return errorResponse;
            }
        }, executorService);
        
        try {
            return future.get(toolExecutionTimeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            String timeoutMsg = "Tool execution timed out after " + toolExecutionTimeoutSeconds + " seconds: " + tool.getName();
            logger.warn(timeoutMsg);
            ToolResponse timeoutResponse = new ToolResponse(false, timeoutMsg, null);
            timeoutResponse.addError("Execution timed out");
            timeoutResponse.addError("EXECUTION_TIMEOUT");
            return timeoutResponse;
        }
    }
    
    private MCPResponse createErrorResponse(String errorMessage) {
        MCPResponse response = new MCPResponse();
        response.setAnswer(errorMessage);
        response.addMetadata("status", "error");
        return response;
    }
    
    @Override
    public void registerTool(MCPToolInterface tool) {
        toolRegistry.registerTool(tool);
    }
    
    @Override
    public List<MCPToolInterface> getAvailableTools() {
        return toolRegistry.getAllTools();
    }
} 