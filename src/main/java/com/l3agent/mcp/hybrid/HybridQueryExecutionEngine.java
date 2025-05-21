package com.l3agent.mcp.hybrid;

import com.l3agent.mcp.MCPRequestHandler;
import com.l3agent.mcp.MCPToolInterface;
import com.l3agent.mcp.MCPToolRegistry;
import com.l3agent.mcp.model.MCPRequest;
import com.l3agent.mcp.model.MCPResponse;
import com.l3agent.mcp.model.ToolExecutionStep;
import com.l3agent.mcp.model.ToolResponse;
import com.l3agent.mcp.util.RetryUtil;
import com.l3agent.service.KnowledgeGraphService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * The Hybrid Query Execution Engine is responsible for intelligently processing user queries
 * by determining when to use pre-computed knowledge versus dynamic analysis tools.
 * 
 * It uses GPT-based classification to determine the most appropriate tools and execution strategy
 * for answering a user's question.
 */
@Component
public class HybridQueryExecutionEngine {
    
    private static final Logger logger = LoggerFactory.getLogger(HybridQueryExecutionEngine.class);
    
    private final GPTQueryClassifier queryClassifier;
    private final MCPRequestHandler mcpRequestHandler;
    private final MCPToolRegistry toolRegistry;
    private final KnowledgeGraphService knowledgeGraphService;
    
    @Value("${l3agent.hybrid.enable-dynamic-tools:true}")
    private boolean enableDynamicTools;
    
    @Value("${l3agent.hybrid.max-execution-time-seconds:30}")
    private int maxExecutionTimeSeconds;
    
    @Value("${l3agent.hybrid.fallback-to-static:true}")
    private boolean fallbackToStatic;
    
    @Value("${l3agent.mcp.retry.max-retries:3}")
    private int maxRetries;
    
    @Value("${l3agent.mcp.retry.delay-ms:1000}")
    private long retryDelayMs;
    
    @Value("${l3agent.hybrid.use-knowledge-graph:true}")
    private boolean useKnowledgeGraph;
    
    /**
     * Creates a new HybridQueryExecutionEngine.
     * 
     * @param queryClassifier The GPT-based query classifier
     * @param mcpRequestHandler The MCP request handler
     * @param toolRegistry The MCP tool registry
     * @param knowledgeGraphService The knowledge graph service
     */
    @Autowired
    public HybridQueryExecutionEngine(
            GPTQueryClassifier queryClassifier,
            MCPRequestHandler mcpRequestHandler,
            MCPToolRegistry toolRegistry,
            KnowledgeGraphService knowledgeGraphService) {
        this.queryClassifier = queryClassifier;
        this.mcpRequestHandler = mcpRequestHandler;
        this.toolRegistry = toolRegistry;
        this.knowledgeGraphService = knowledgeGraphService;
    }
    
    /**
     * Executes a user query and returns the response.
     * Uses the appropriate strategy based on query classification.
     * 
     * @param query The user query
     * @param context Additional context for the query
     * @return The execution result
     */
    public QueryResult executeQuery(String query, Map<String, Object> context) {
        logger.info("Executing query: {}", query);
        
        try {
            // Classify the query to determine the analysis path
            AnalysisPath analysisPath = queryClassifier.classifyQuery(query);
            logger.info("Query classified as {} with confidence {}", 
                    analysisPath.getPathType(), analysisPath.getConfidence());
            
            // Create an execution plan based on the analysis path
            ExecutionPlan plan = createExecutionPlan(analysisPath, context);
            
            // Add knowledge graph data to the plan context if available and enabled
            boolean useKnowledgeGraphForQuery = analysisPath.isFlagEnabled("use_knowledge_graph");
            if ((useKnowledgeGraph || useKnowledgeGraphForQuery) && knowledgeGraphService.isAvailable()) {
                logger.info("Using knowledge graph for query: use_knowledge_graph={}, useKnowledgeGraphForQuery={}",
                         useKnowledgeGraph, useKnowledgeGraphForQuery);
                enrichPlanWithKnowledgeGraph(plan, query);
            }
            
            // Execute the plan
            QueryResult result = executeExecutionPlan(plan);
            result.setRequestedTools(analysisPath.getRequiredTools());
            
            return result;
        } catch (Exception e) {
            logger.error("Error executing query: {}", e.getMessage(), e);
            
            // Create a fallback result
            QueryResult fallbackResult = new QueryResult();
            fallbackResult.setSuccess(false);
            fallbackResult.setErrorMessage("Error executing query: " + e.getMessage());
            
            if (fallbackToStatic) {
                logger.info("Falling back to static analysis");
                try {
                    // Execute a static-only analysis as fallback
                    MCPResponse staticResponse = executeStaticAnalysis(query, context);
                    
                    // Extract data from the tool results
                    if (staticResponse.getToolResults() != null && !staticResponse.getToolResults().isEmpty()) {
                        ToolResponse toolResponse = staticResponse.getToolResults().get(0);
                        if (toolResponse != null && toolResponse.getData() != null) {
                            // Convert the data to Map<String, Object> if possible
                            Map<String, Object> responseData = convertToolResponseData(toolResponse.getData());
                            fallbackResult.addToolResponse("vector_search", responseData);
                            fallbackResult.setSuccess(true);
                            fallbackResult.setFallbackUsed(true);
                        }
                    }
                } catch (Exception fallbackError) {
                    logger.error("Error executing fallback static analysis: {}", 
                            fallbackError.getMessage(), fallbackError);
                }
            }
            
            return fallbackResult;
        }
    }
    
    /**
     * Converts a tool response data object to a Map<String, Object>.
     * 
     * @param data The data object
     * @return The converted map
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> convertToolResponseData(Object data) {
        if (data instanceof Map) {
            try {
                return (Map<String, Object>) data;
            } catch (ClassCastException e) {
                logger.warn("Could not cast tool response data to Map<String, Object>: {}", e.getMessage());
            }
        }
        
        // Create a simple wrapper map if the data isn't already a map
        Map<String, Object> result = new HashMap<>();
        result.put("data", data);
        return result;
    }
    
    /**
     * Creates an execution plan based on the analysis path.
     * 
     * @param analysisPath The analysis path from query classification
     * @param context Additional context for the query
     * @return The execution plan
     */
    private ExecutionPlan createExecutionPlan(AnalysisPath analysisPath, Map<String, Object> context) {
        ExecutionPlan plan = new ExecutionPlan();
        plan.setQuery(analysisPath.getQuery());
        plan.setPathType(analysisPath.getPathType());
        
        // Copy context to the shared context
        if (context != null) {
            plan.getSharedContext().putAll(context);
        }
        
        // Check if knowledge graph is specifically requested in the analysis path
        boolean useKnowledgeGraphForQuery = analysisPath.isFlagEnabled("use_knowledge_graph");
        
        // If knowledge graph is specifically requested, add a flag to the plan context
        if (useKnowledgeGraphForQuery) {
            plan.getSharedContext().put("requires_knowledge_graph", true);
        }
        
        // Always start with static analysis (vector search)
        if (analysisPath.getRequiredTools().contains("vector_search")) {
            addVectorSearchStep(plan);
        }
        
        // If dynamic tools are enabled and the path type is HYBRID or DYNAMIC,
        // add the dynamic analysis steps
        if (enableDynamicTools && 
                (analysisPath.getPathType().equals("HYBRID") || 
                 analysisPath.getPathType().equals("DYNAMIC"))) {
            // Add dynamic tools
            for (String tool : analysisPath.getRequiredTools()) {
                if (!tool.equals("vector_search")) {
                    addToolStep(plan, tool);
                }
            }
        }
        
        return plan;
    }
    
    /**
     * Adds a vector search step to the execution plan.
     * 
     * @param plan The execution plan
     */
    private void addVectorSearchStep(ExecutionPlan plan) {
        // Create a tool execution step for vector search
        ToolExecutionStep step = new ToolExecutionStep();
        step.setToolName("vector_search");
        step.addParameter("query", plan.getQuery());
        step.addParameter("limit", 10);
        step.setPriority(0); // Highest priority, execute first
        step.setRequired(true);
        
        plan.addStep(step);
    }
    
    /**
     * Adds a tool step to the execution plan.
     * 
     * @param plan The execution plan
     * @param toolName The name of the tool to add
     */
    private void addToolStep(ExecutionPlan plan, String toolName) {
        // Check if the tool exists
        Optional<MCPToolInterface> toolOpt = toolRegistry.getTool(toolName);
        if (toolOpt.isEmpty()) {
            logger.warn("Tool not found: {}", toolName);
            return;
        }
        
        // Create a tool execution step
        ToolExecutionStep step = new ToolExecutionStep();
        step.setToolName(toolName);
        step.addParameter("query", plan.getQuery());
        
        // Different tools get different priorities
        switch (toolName) {
            case "call_path_analyzer":
                step.setPriority(1);
                break;
            case "config_impact_analyzer":
                step.setPriority(1);
                break;
            case "error_chain_mapper":
                step.setPriority(1);
                break;
            case "cross_repo_tracer":
                step.setPriority(2);
                break;
            default:
                step.setPriority(3);
                break;
        }
        
        // Add the step to the plan
        plan.addStep(step);
    }
    
    /**
     * Executes the execution plan and returns the result.
     * 
     * @param plan The execution plan
     * @return The execution result
     */
    private QueryResult executeExecutionPlan(ExecutionPlan plan) {
        QueryResult result = new QueryResult();
        result.setQuery(plan.getQuery());
        
        try {
            // Execute each step in the plan
            for (ToolExecutionStep step : plan.getSteps()) {
                try {
                    // Create an MCP request for this step
                    MCPRequest mcpRequest = new MCPRequest(plan.getQuery());
                    mcpRequest.addExecutionStep(step);
                    mcpRequest.setContextData(plan.getSharedContext());
                    
                    // Execute the request with a timeout and retry
                    MCPResponse mcpResponse = executeWithRetryAndTimeout(() -> 
                            mcpRequestHandler.process(mcpRequest), maxRetries, retryDelayMs, maxExecutionTimeSeconds);
                    
                    // Add the response to the result
                    if (mcpResponse.getToolResults() != null && !mcpResponse.getToolResults().isEmpty()) {
                        ToolResponse toolResponse = mcpResponse.getToolResults().get(0);
                        if (toolResponse.isSuccess()) {
                            // Convert the data to Map<String, Object>
                            Map<String, Object> responseData = convertToolResponseData(toolResponse.getData());
                            result.addToolResponse(step.getToolName(), responseData);
                            result.setSuccess(true);
                            
                            // Update shared context with the results
                            updateSharedContext(plan, step.getToolName(), responseData);
                        } else {
                            logger.warn("Tool execution failed: {} - {}", 
                                    step.getToolName(), toolResponse.getMessage());
                            result.addToolError(step.getToolName(), toolResponse.getMessage());
                            
                            // If this is a required step and it failed, mark the result as failed
                            if (step.isRequired()) {
                                result.setSuccess(false);
                                result.setErrorMessage("Required tool execution failed: " + 
                                        step.getToolName());
                            }
                        }
                    } else {
                        logger.warn("No tool results in response for tool: {}", step.getToolName());
                        result.addToolError(step.getToolName(), "No results returned");
                    }
                } catch (Exception e) {
                    logger.error("Error executing tool {}: {}", 
                            step.getToolName(), e.getMessage(), e);
                    result.addToolError(step.getToolName(), e.getMessage());
                    
                    // If this is a required step and it failed, mark the result as failed
                    if (step.isRequired()) {
                        result.setSuccess(false);
                        result.setErrorMessage("Required tool execution failed: " + 
                                step.getToolName());
                    }
                }
            }
            
            // Add knowledge graph data to the result if available in the shared context
            addKnowledgeGraphDataToResult(plan, result);
        } catch (Exception e) {
            logger.error("Error executing plan: {}", e.getMessage(), e);
            result.setSuccess(false);
            result.setErrorMessage("Error executing plan: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * Adds knowledge graph data from the shared context to the result.
     * 
     * @param plan The execution plan
     * @param result The query result
     */
    private void addKnowledgeGraphDataToResult(ExecutionPlan plan, QueryResult result) {
        try {
            // Get knowledge graph entities from the shared context
            @SuppressWarnings("unchecked")
            List<KnowledgeGraphService.CodeEntity> entities = 
                    (List<KnowledgeGraphService.CodeEntity>) plan.getSharedContext().get("knowledge_graph_entities");
            
            if (entities != null && !entities.isEmpty()) {
                // Add entities to the result
                for (KnowledgeGraphService.CodeEntity entity : entities) {
                    result.addKnowledgeGraphEntity(entity);
                }
                
                // Get enriched entities with relationships
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> enrichedEntities = 
                        (List<Map<String, Object>>) plan.getSharedContext().get("knowledge_graph_enriched_entities");
                
                if (enrichedEntities != null) {
                    // Add relationships to the result
                    for (Map<String, Object> enrichedEntity : enrichedEntities) {
                        @SuppressWarnings("unchecked")
                        List<KnowledgeGraphService.CodeRelationship> relationships = 
                                (List<KnowledgeGraphService.CodeRelationship>) enrichedEntity.get("relationships");
                        
                        if (relationships != null) {
                            for (KnowledgeGraphService.CodeRelationship relationship : relationships) {
                                // Convert relationship to a map for easier serialization
                                Map<String, Object> relationshipMap = new HashMap<>();
                                relationshipMap.put("sourceId", relationship.getSourceId());
                                relationshipMap.put("targetId", relationship.getTargetId());
                                relationshipMap.put("type", relationship.getType());
                                relationshipMap.put("properties", relationship.getProperties());
                                
                                result.addKnowledgeGraphRelationship(relationshipMap);
                            }
                        }
                    }
                }
                
                logger.info("Added knowledge graph data to result: {} entities, {} relationships", 
                        result.getKnowledgeGraphEntities().size(),
                        result.getKnowledgeGraphRelationships().size());
            }
        } catch (Exception e) {
            logger.warn("Error adding knowledge graph data to result: {}", e.getMessage());
            // Continue without knowledge graph data in the result
        }
    }
    
    /**
     * A wrapper around RetryUtil's methods to simplify usage.
     * 
     * @param <T> The return type
     * @param callable The callable to execute
     * @param maxRetries The maximum number of retries
     * @param retryDelayMs The delay between retries in milliseconds
     * @param timeoutSeconds The timeout for each attempt in seconds
     * @return The result of the callable
     * @throws Exception If execution fails
     */
    private <T> T executeWithRetryAndTimeout(Callable<T> callable, int maxRetries, 
            long retryDelayMs, int timeoutSeconds) throws Exception {
        return RetryUtil.withRetryAndTimeout(callable, maxRetries, retryDelayMs, timeoutSeconds);
    }
    
    /**
     * Updates the shared context with the results of a tool execution.
     * 
     * @param plan The execution plan
     * @param toolName The name of the tool
     * @param results The results of the tool execution
     */
    private void updateSharedContext(ExecutionPlan plan, String toolName, Map<String, Object> results) {
        // Add the results to the shared context under a key specific to the tool
        plan.getSharedContext().put(toolName + "_results", results);
        
        // Also flatten the results for easier access by other tools
        if (results != null) {
            for (Map.Entry<String, Object> entry : results.entrySet()) {
                plan.getSharedContext().put(toolName + "_" + entry.getKey(), entry.getValue());
            }
        }
    }
    
    /**
     * Executes a static analysis as a fallback.
     * 
     * @param query The user query
     * @param context Additional context for the query
     * @return The MCP response
     */
    private MCPResponse executeStaticAnalysis(String query, Map<String, Object> context) throws Exception {
        // Create a simple execution plan with just the vector search step
        MCPRequest mcpRequest = new MCPRequest(query);
        
        ToolExecutionStep step = new ToolExecutionStep();
        step.setToolName("vector_search");
        step.addParameter("query", query);
        step.addParameter("limit", 10);
        
        mcpRequest.addExecutionStep(step);
        
        if (context != null) {
            mcpRequest.setContextData(context);
        }
        
        // Execute the request with timeout and retry
        return executeWithRetryAndTimeout(() -> mcpRequestHandler.process(mcpRequest), 
                maxRetries, retryDelayMs, maxExecutionTimeSeconds);
    }
    
    /**
     * Enriches the execution plan with knowledge graph data.
     * This adds related entities and relationships to the shared context.
     * 
     * @param plan The execution plan to enrich
     * @param query The user query
     */
    private void enrichPlanWithKnowledgeGraph(ExecutionPlan plan, String query) {
        try {
            // Search for entities related to the query
            List<KnowledgeGraphService.CodeEntity> entities = knowledgeGraphService.searchEntities(query, 5);
            
            if (entities != null && !entities.isEmpty()) {
                // Store the entities in the shared context
                plan.getSharedContext().put("knowledge_graph_entities", entities);
                
                // For each entity, find its relationships
                List<Map<String, Object>> enrichedEntities = new ArrayList<>();
                
                for (KnowledgeGraphService.CodeEntity entity : entities) {
                    Map<String, Object> enrichedEntity = new HashMap<>();
                    enrichedEntity.put("entity", entity);
                    
                    // Find related entities with a depth of 1 (direct relationships only)
                    List<KnowledgeGraphService.CodeRelationship> relationships = 
                            knowledgeGraphService.findRelatedEntities(entity.getId(), 1);
                    
                    enrichedEntity.put("relationships", relationships);
                    enrichedEntities.add(enrichedEntity);
                }
                
                // Store the enriched entities in the shared context
                plan.getSharedContext().put("knowledge_graph_enriched_entities", enrichedEntities);
                
                logger.info("Enriched execution plan with knowledge graph data: {} entities, {} relationships total",
                        entities.size(), 
                        enrichedEntities.stream()
                                .mapToInt(e -> ((List<?>)e.get("relationships")).size())
                                .sum());
            } else {
                logger.info("No relevant knowledge graph entities found for query: {}", query);
            }
        } catch (Exception e) {
            logger.warn("Error enriching execution plan with knowledge graph data: {}", e.getMessage());
            // Continue without knowledge graph data
        }
    }
    
    /**
     * Gets a list of available tools.
     * 
     * @return The list of available tool names
     */
    public List<String> getAvailableTools() {
        return toolRegistry.getAllTools().stream()
                .map(MCPToolInterface::getName)
                .collect(Collectors.toList());
    }
} 