package com.l3agent.mcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for MCP tools that manages tool registration and retrieval.
 */
@Service
public class MCPToolRegistry {
    
    private static final Logger logger = LoggerFactory.getLogger(MCPToolRegistry.class);
    
    private final Map<String, MCPToolInterface> tools = new ConcurrentHashMap<>();
    
    /**
     * Registers a tool with the registry.
     * 
     * @param tool The tool to register
     * @return true if the tool was registered successfully, false if a tool with the same name already exists
     */
    public boolean registerTool(MCPToolInterface tool) {
        if (tool == null) {
            logger.warn("Attempted to register null tool");
            return false;
        }
        
        String toolName = tool.getName();
        if (toolName == null || toolName.trim().isEmpty()) {
            logger.warn("Attempted to register tool with null or empty name");
            return false;
        }
        
        if (tools.containsKey(toolName)) {
            logger.warn("Tool with name '{}' is already registered", toolName);
            return false;
        }
        
        tools.put(toolName, tool);
        logger.info("Registered tool: {}", toolName);
        return true;
    }
    
    /**
     * Unregisters a tool from the registry.
     * 
     * @param toolName The name of the tool to unregister
     * @return true if the tool was unregistered, false if no tool with the given name was found
     */
    public boolean unregisterTool(String toolName) {
        if (toolName == null || toolName.trim().isEmpty()) {
            logger.warn("Attempted to unregister tool with null or empty name");
            return false;
        }
        
        MCPToolInterface removedTool = tools.remove(toolName);
        if (removedTool != null) {
            logger.info("Unregistered tool: {}", toolName);
            return true;
        } else {
            logger.warn("No tool found with name '{}' to unregister", toolName);
            return false;
        }
    }
    
    /**
     * Gets a tool by name.
     * 
     * @param toolName The name of the tool to retrieve
     * @return The tool if found, otherwise empty
     */
    public Optional<MCPToolInterface> getTool(String toolName) {
        if (toolName == null || toolName.trim().isEmpty()) {
            logger.warn("Attempted to get tool with null or empty name");
            return Optional.empty();
        }
        
        MCPToolInterface tool = tools.get(toolName);
        if (tool == null) {
            logger.warn("No tool found with name: {}", toolName);
            return Optional.empty();
        }
        
        return Optional.of(tool);
    }
    
    /**
     * Gets a list of all registered tools.
     * 
     * @return An unmodifiable list of all registered tools
     */
    public List<MCPToolInterface> getAllTools() {
        return Collections.unmodifiableList(new ArrayList<>(tools.values()));
    }
    
    /**
     * Gets the number of registered tools.
     * 
     * @return The number of tools in the registry
     */
    public int getToolCount() {
        return tools.size();
    }
    
    /**
     * Checks if a tool with the given name is registered.
     * 
     * @param toolName The name of the tool to check
     * @return true if the tool is registered, false otherwise
     */
    public boolean hasTool(String toolName) {
        if (toolName == null || toolName.trim().isEmpty()) {
            return false;
        }
        
        return tools.containsKey(toolName);
    }
    
    /**
     * Clears all tools from the registry.
     */
    public void clearAllTools() {
        int count = tools.size();
        tools.clear();
        logger.info("Cleared all {} tools from registry", count);
    }
} 