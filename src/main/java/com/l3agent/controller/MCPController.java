package com.l3agent.controller;

import com.l3agent.mcp.MCPRequestHandler;
import com.l3agent.mcp.MCPToolInterface;
import com.l3agent.mcp.model.MCPRequest;
import com.l3agent.mcp.model.MCPResponse;
import com.l3agent.mcp.model.ToolParameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST Controller for the Model Control Plane (MCP) API.
 */
@RestController
@RequestMapping("/api/v1/mcp")
public class MCPController {
    
    private static final Logger logger = LoggerFactory.getLogger(MCPController.class);
    
    @Autowired
    private MCPRequestHandler mcpRequestHandler;
    
    /**
     * Process an MCP request.
     * 
     * @param request The request to process
     * @return The response containing the results of execution
     */
    @PostMapping("/process")
    public ResponseEntity<MCPResponse> process(@RequestBody MCPRequest request) {
        logger.info("Received MCP process request: {}", request.getQuery());
        
        try {
            MCPResponse response = mcpRequestHandler.process(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error processing MCP request", e);
            
            MCPResponse errorResponse = new MCPResponse();
            errorResponse.setAnswer("Error processing request: " + e.getMessage());
            errorResponse.addMetadata("status", "error");
            errorResponse.addMetadata("exception", e.getClass().getName());
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
    
    /**
     * Get a list of all available tools.
     * 
     * @return List of available tools with their parameters
     */
    @GetMapping("/tools")
    public ResponseEntity<Map<String, Object>> getAvailableTools() {
        logger.info("Received request for available tools");
        
        try {
            List<MCPToolInterface> tools = mcpRequestHandler.getAvailableTools();
            
            List<Map<String, Object>> toolInfoList = tools.stream()
                .map(this::convertToolToMap)
                .collect(Collectors.toList());
            
            Map<String, Object> response = new HashMap<>();
            response.put("tools", toolInfoList);
            response.put("count", toolInfoList.size());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting available tools", e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Error getting available tools: " + e.getMessage());
            errorResponse.put("status", "error");
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
    
    private Map<String, Object> convertToolToMap(MCPToolInterface tool) {
        Map<String, Object> toolInfo = new HashMap<>();
        toolInfo.put("name", tool.getName());
        toolInfo.put("description", tool.getDescription());
        
        List<Map<String, Object>> parameterList = tool.getParameters().stream()
            .map(this::convertParameterToMap)
            .collect(Collectors.toList());
        
        toolInfo.put("parameters", parameterList);
        return toolInfo;
    }
    
    private Map<String, Object> convertParameterToMap(ToolParameter parameter) {
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("name", parameter.getName());
        paramMap.put("description", parameter.getDescription());
        paramMap.put("type", parameter.getType());
        paramMap.put("required", parameter.isRequired());
        
        if (parameter.getDefaultValue() != null) {
            paramMap.put("defaultValue", parameter.getDefaultValue());
        }
        
        return paramMap;
    }
} 