package com.l3agent.controller;

import com.l3agent.mcp.hybrid.HybridQueryExecutionEngine;
import com.l3agent.mcp.hybrid.QueryResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * REST controller for the Hybrid Query Engine.
 * Provides an endpoint for executing queries using the hybrid approach.
 */
@RestController
@RequestMapping("/api/v1/hybrid")
public class HybridQueryController {
    
    private static final Logger logger = LoggerFactory.getLogger(HybridQueryController.class);
    
    @Autowired
    private HybridQueryExecutionEngine hybridQueryEngine;
    
    /**
     * Executes a query using the hybrid query engine.
     * 
     * @param request The query request
     * @return The query result
     */
    @PostMapping("/query")
    public ResponseEntity<Map<String, Object>> executeQuery(@RequestBody Map<String, Object> request) {
        String query = (String) request.get("query");
        @SuppressWarnings("unchecked")
        Map<String, Object> context = (Map<String, Object>) request.getOrDefault("context", new HashMap<>());
        
        logger.info("Executing hybrid query: {}", query);
        
        try {
            QueryResult result = hybridQueryEngine.executeQuery(query, context);
            
            Map<String, Object> response = new HashMap<>();
            response.put("query", result.getQuery());
            response.put("success", result.isSuccess());
            
            if (result.isSuccess()) {
                response.put("tool_responses", result.getToolResponses());
                response.put("requested_tools", result.getRequestedTools());
                response.put("fallback_used", result.isFallbackUsed());
                
                // Add knowledge graph data if available
                if (!result.getKnowledgeGraphEntities().isEmpty()) {
                    response.put("knowledge_graph_entities", result.getKnowledgeGraphEntities());
                    response.put("knowledge_graph_relationships", result.getKnowledgeGraphRelationships());
                }
            } else {
                response.put("error_message", result.getErrorMessage());
                response.put("tool_errors", result.getToolErrors());
            }
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error executing query: {}", e.getMessage(), e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("query", query);
            errorResponse.put("success", false);
            errorResponse.put("error_message", "Error executing query: " + e.getMessage());
            
            return ResponseEntity.ok(errorResponse);
        }
    }
    
    /**
     * Gets a list of available tools.
     * 
     * @return The available tools
     */
    @PostMapping("/tools")
    public ResponseEntity<Map<String, Object>> getAvailableTools() {
        logger.info("Getting available tools");
        
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("tools", hybridQueryEngine.getAvailableTools());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting available tools: {}", e.getMessage(), e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error_message", "Error getting available tools: " + e.getMessage());
            
            return ResponseEntity.ok(errorResponse);
        }
    }
} 