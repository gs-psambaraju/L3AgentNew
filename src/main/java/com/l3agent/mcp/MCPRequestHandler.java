package com.l3agent.mcp;

import com.l3agent.mcp.model.MCPRequest;
import com.l3agent.mcp.model.MCPResponse;

import java.util.List;

/**
 * Interface for the MCP request handler which processes requests and manages tools.
 */
public interface MCPRequestHandler {
    
    /**
     * Processes an MCP request and returns a response.
     * 
     * @param request The request to process
     * @return The response containing the results of execution
     */
    MCPResponse process(MCPRequest request);
    
    /**
     * Registers a tool with the MCP server.
     * 
     * @param tool The tool to register
     */
    void registerTool(MCPToolInterface tool);
    
    /**
     * Returns a list of all registered tools.
     * 
     * @return List of available tools
     */
    List<MCPToolInterface> getAvailableTools();
} 