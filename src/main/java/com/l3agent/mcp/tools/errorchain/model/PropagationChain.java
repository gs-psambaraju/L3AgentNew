package com.l3agent.mcp.tools.errorchain.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a chain of exception propagation through a system.
 * Tracks how an exception flows from its source through various components.
 */
public class PropagationChain {
    
    private String exceptionClass;
    private List<Map<String, String>> nodes;
    private String chainId;
    
    /**
     * Creates a new propagation chain for an exception.
     * 
     * @param exceptionClass The exception class being propagated
     */
    public PropagationChain(String exceptionClass) {
        this.exceptionClass = exceptionClass;
        this.nodes = new ArrayList<>();
        this.chainId = exceptionClass + "_" + System.currentTimeMillis();
    }
    
    /**
     * Adds a node to the propagation chain.
     * 
     * @param component The component name (e.g., class, method, or layer)
     * @param action The action taken on the exception (e.g., thrown, caught, wrapped)
     */
    public void addNode(String component, String action) {
        Map<String, String> node = new LinkedHashMap<>();
        node.put("component", component);
        node.put("action", action);
        nodes.add(node);
    }
    
    /**
     * Adds a detailed node to the propagation chain.
     * 
     * @param component The component name
     * @param action The action taken
     * @param location The code location
     * @param details Additional details about the exception handling
     */
    public void addDetailedNode(String component, String action, String location, String details) {
        Map<String, String> node = new LinkedHashMap<>();
        node.put("component", component);
        node.put("action", action);
        node.put("location", location);
        node.put("details", details);
        nodes.add(node);
    }
    
    /**
     * Gets the exception class for this chain.
     * 
     * @return The exception class
     */
    public String getExceptionClass() {
        return exceptionClass;
    }
    
    /**
     * Gets the nodes in the propagation chain.
     * 
     * @return List of propagation nodes
     */
    public List<Map<String, String>> getNodes() {
        return nodes;
    }
    
    /**
     * Gets the chain ID.
     * 
     * @return The chain ID
     */
    public String getChainId() {
        return chainId;
    }
    
    /**
     * Gets the length of the propagation chain.
     * 
     * @return The number of nodes in the chain
     */
    public int getLength() {
        return nodes.size();
    }
    
    /**
     * Gets a summary of this propagation chain.
     * 
     * @return A summary description
     */
    public String getSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append(exceptionClass).append(": ");
        
        for (int i = 0; i < nodes.size(); i++) {
            Map<String, String> node = nodes.get(i);
            summary.append(node.get("component")).append(" ").append(node.get("action"));
            
            if (i < nodes.size() - 1) {
                summary.append(" → ");
            }
        }
        
        return summary.toString();
    }
} 