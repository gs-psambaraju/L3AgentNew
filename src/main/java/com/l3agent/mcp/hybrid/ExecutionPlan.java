package com.l3agent.mcp.hybrid;

import com.l3agent.mcp.model.ToolExecutionStep;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a plan for executing a query.
 * Contains the steps to execute and shared context between steps.
 */
public class ExecutionPlan {
    
    private String query;
    private String pathType;
    private List<ToolExecutionStep> steps;
    private Map<String, Object> sharedContext;
    
    /**
     * Creates a new execution plan.
     */
    public ExecutionPlan() {
        this.steps = new ArrayList<>();
        this.sharedContext = new HashMap<>();
    }
    
    /**
     * Gets the query for this execution plan.
     * 
     * @return The query
     */
    public String getQuery() {
        return query;
    }
    
    /**
     * Sets the query for this execution plan.
     * 
     * @param query The query
     */
    public void setQuery(String query) {
        this.query = query;
    }
    
    /**
     * Gets the path type for this execution plan.
     * 
     * @return The path type
     */
    public String getPathType() {
        return pathType;
    }
    
    /**
     * Sets the path type for this execution plan.
     * 
     * @param pathType The path type
     */
    public void setPathType(String pathType) {
        this.pathType = pathType;
    }
    
    /**
     * Gets the steps in this execution plan.
     * 
     * @return The steps
     */
    public List<ToolExecutionStep> getSteps() {
        return steps;
    }
    
    /**
     * Sets the steps in this execution plan.
     * 
     * @param steps The steps
     */
    public void setSteps(List<ToolExecutionStep> steps) {
        this.steps = steps;
    }
    
    /**
     * Adds a step to this execution plan.
     * 
     * @param step The step to add
     */
    public void addStep(ToolExecutionStep step) {
        this.steps.add(step);
    }
    
    /**
     * Gets the shared context for this execution plan.
     * 
     * @return The shared context
     */
    public Map<String, Object> getSharedContext() {
        return sharedContext;
    }
    
    /**
     * Sets the shared context for this execution plan.
     * 
     * @param sharedContext The shared context
     */
    public void setSharedContext(Map<String, Object> sharedContext) {
        this.sharedContext = sharedContext;
    }
    
    /**
     * Adds a value to the shared context.
     * 
     * @param key The key
     * @param value The value
     */
    public void addToSharedContext(String key, Object value) {
        this.sharedContext.put(key, value);
    }
} 