package com.l3agent.service.impl;

import com.l3agent.mcp.MCPRequestHandler;
import com.l3agent.mcp.model.MCPRequest;
import com.l3agent.mcp.model.MCPResponse;
import com.l3agent.mcp.model.ToolExecutionStep;
import com.l3agent.mcp.util.VectorServiceIntegration;
import com.l3agent.service.MCPIntegrationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Default implementation of the MCPIntegrationService interface.
 */
@Service
public class DefaultMCPIntegrationService implements MCPIntegrationService {
    
    private static final Logger logger = LoggerFactory.getLogger(DefaultMCPIntegrationService.class);
    
    @Autowired
    private MCPRequestHandler mcpRequestHandler;
    
    @Autowired
    private VectorServiceIntegration vectorServiceIntegration;
    
    @Override
    public MCPResponse processQuery(String query) {
        if (query == null || query.trim().isEmpty()) {
            logger.warn("Received empty query");
            MCPResponse response = new MCPResponse();
            response.setAnswer("Invalid query: Query cannot be empty");
            return response;
        }
        
        logger.info("Processing query: {}", query);
        
        // Create and enhance request
        MCPRequest request = createRequest(query);
        request = enhanceRequest(request);
        
        // Process request
        try {
            return mcpRequestHandler.process(request);
        } catch (Exception e) {
            logger.error("Error processing query: {}", query, e);
            
            MCPResponse errorResponse = new MCPResponse();
            errorResponse.setAnswer("Error processing query: " + e.getMessage());
            errorResponse.addMetadata("error", e.getMessage());
            errorResponse.addMetadata("errorType", e.getClass().getName());
            
            return errorResponse;
        }
    }
    
    @Override
    public MCPRequest enhanceRequest(MCPRequest request) {
        if (request == null) {
            logger.warn("Cannot enhance null request");
            return new MCPRequest();
        }
        
        logger.info("Enhancing request: {}", request.getQuery());
        
        // Add vector service context
        request = vectorServiceIntegration.enhanceRequestWithCodeContext(request);
        
        // Add execution plan if none exists
        if (request.getExecutionPlan() == null || request.getExecutionPlan().isEmpty()) {
            logger.info("Adding default execution plan to request");
            request = addDefaultExecutionPlan(request);
        }
        
        return request;
    }
    
    @Override
    public MCPRequest createRequest(String query) {
        logger.info("Creating request for query: {}", query);
        
        MCPRequest request = new MCPRequest();
        request.setQuery(query);
        
        // Add additional context that may be helpful
        Map<String, Object> contextData = new HashMap<>();
        contextData.put("timestamp", System.currentTimeMillis());
        contextData.put("queryType", "user_query");
        
        request.setContextData(contextData);
        
        return request;
    }
    
    /**
     * Adds a default execution plan to the request based on the query.
     * 
     * @param request The request to add an execution plan to
     * @return The request with a default execution plan
     */
    private MCPRequest addDefaultExecutionPlan(MCPRequest request) {
        // Create a step to search code
        ToolExecutionStep searchStep = new ToolExecutionStep();
        searchStep.setToolName("code_search");
        searchStep.setPriority(10);
        searchStep.setRequired(true);
        
        Map<String, Object> searchParams = new HashMap<>();
        searchParams.put("query", request.getQuery());
        searchParams.put("maxResults", 5);
        searchStep.setParameters(searchParams);
        
        request.addExecutionStep(searchStep);
        
        return request;
    }
} 