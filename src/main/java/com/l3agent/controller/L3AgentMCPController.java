package com.l3agent.controller;

import com.l3agent.mcp.model.MCPRequest;
import com.l3agent.mcp.model.MCPResponse;
import com.l3agent.service.L3AgentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * REST Controller for L3Agent MCP functionality.
 * Provides endpoints for querying the MCP system.
 */
@RestController
@RequestMapping("/api/v1/l3agent/mcp")
public class L3AgentMCPController {
    
    private static final Logger logger = LoggerFactory.getLogger(L3AgentMCPController.class);
    
    @Autowired
    private L3AgentService l3AgentService;
    
    /**
     * Process a query using the MCP framework.
     * 
     * @param query The query to process
     * @return ResponseEntity containing the MCP response
     */
    @PostMapping("/query")
    public ResponseEntity<MCPResponse> processQuery(@RequestParam String query) {
        logger.info("Received MCP query: {}", query);
        
        try {
            MCPResponse response = l3AgentService.processMCPQuery(query);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error processing MCP query: {}", query, e);
            
            MCPResponse errorResponse = new MCPResponse();
            errorResponse.setAnswer("Error processing query: " + e.getMessage());
            errorResponse.addMetadata("status", "error");
            errorResponse.addMetadata("exception", e.getClass().getName());
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
    
    /**
     * Process a custom MCP request with a specific execution plan.
     * 
     * @param request The custom MCP request to process
     * @return ResponseEntity containing the MCP response
     */
    @PostMapping("/request")
    public ResponseEntity<MCPResponse> processRequest(@RequestBody MCPRequest request) {
        if (request == null || request.getQuery() == null || request.getQuery().trim().isEmpty()) {
            logger.warn("Received invalid MCP request");
            
            MCPResponse errorResponse = new MCPResponse();
            errorResponse.setAnswer("Invalid request: Request or query cannot be null or empty");
            errorResponse.addMetadata("status", "error");
            
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        }
        
        logger.info("Received custom MCP request for query: {}", request.getQuery());
        
        try {
            MCPResponse response = l3AgentService.processMCPRequest(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error processing custom MCP request", e);
            
            MCPResponse errorResponse = new MCPResponse();
            errorResponse.setAnswer("Error processing request: " + e.getMessage());
            errorResponse.addMetadata("status", "error");
            errorResponse.addMetadata("exception", e.getClass().getName());
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
    
    /**
     * Simple health check endpoint for the MCP system.
     * 
     * @return ResponseEntity containing the health status
     */
    @PostMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        logger.info("Received MCP health check request");
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "up");
        response.put("timestamp", System.currentTimeMillis());
        response.put("service", "l3agent-mcp");
        
        return ResponseEntity.ok(response);
    }
} 