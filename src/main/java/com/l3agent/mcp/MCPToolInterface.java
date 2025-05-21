package com.l3agent.mcp;

import com.l3agent.mcp.model.ToolParameter;
import com.l3agent.mcp.model.ToolResponse;

import java.util.List;
import java.util.Map;

/**
 * Interface that all MCP tools must implement to be registrable with the MCP server.
 */
public interface MCPToolInterface {
    
    /**
     * Returns the unique name of the tool.
     * 
     * @return A string identifier for the tool
     */
    String getName();
    
    /**
     * Returns a human-readable description of what the tool does.
     * 
     * @return A string description of the tool's capabilities
     */
    String getDescription();
    
    /**
     * Returns a list of parameters that this tool accepts.
     * 
     * @return List of parameter definitions
     */
    List<ToolParameter> getParameters();
    
    /**
     * Executes the tool with the given parameters and returns a response.
     * 
     * @param parameters A map of parameter names to values
     * @return The result of executing the tool
     */
    ToolResponse execute(Map<String, Object> parameters);
} 