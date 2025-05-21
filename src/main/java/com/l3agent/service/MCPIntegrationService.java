package com.l3agent.service;

import com.l3agent.mcp.model.MCPRequest;
import com.l3agent.mcp.model.MCPResponse;

/**
 * Service for integrating the L3Agent core services with the MCP framework.
 */
public interface MCPIntegrationService {
    
    /**
     * Processes a user query using the MCP framework.
     * 
     * @param query The user query
     * @return The MCP response containing the results
     */
    MCPResponse processQuery(String query);
    
    /**
     * Enhances an MCP request with context data from available L3Agent services.
     * 
     * @param request The original MCP request
     * @return The enhanced request with additional context data
     */
    MCPRequest enhanceRequest(MCPRequest request);
    
    /**
     * Creates a new MCP request for a user query.
     * 
     * @param query The user query
     * @return A new MCP request with default settings
     */
    MCPRequest createRequest(String query);
} 