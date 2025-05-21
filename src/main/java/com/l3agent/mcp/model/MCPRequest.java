package com.l3agent.mcp.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a request to the Model Control Plane.
 */
public class MCPRequest {
    private String query;
    private List<ToolExecutionStep> executionPlan;
    private Map<String, Object> contextData;

    public MCPRequest() {
        this.executionPlan = new ArrayList<>();
        this.contextData = new HashMap<>();
    }

    public MCPRequest(String query) {
        this.query = query;
        this.executionPlan = new ArrayList<>();
        this.contextData = new HashMap<>();
    }

    public MCPRequest(String query, List<ToolExecutionStep> executionPlan, Map<String, Object> contextData) {
        this.query = query;
        this.executionPlan = executionPlan != null ? executionPlan : new ArrayList<>();
        this.contextData = contextData != null ? contextData : new HashMap<>();
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public List<ToolExecutionStep> getExecutionPlan() {
        return executionPlan;
    }

    public void setExecutionPlan(List<ToolExecutionStep> executionPlan) {
        this.executionPlan = executionPlan;
    }

    public Map<String, Object> getContextData() {
        return contextData;
    }

    public void setContextData(Map<String, Object> contextData) {
        this.contextData = contextData;
    }

    public void addExecutionStep(ToolExecutionStep step) {
        if (this.executionPlan == null) {
            this.executionPlan = new ArrayList<>();
        }
        this.executionPlan.add(step);
    }

    public void addContextData(String key, Object value) {
        if (this.contextData == null) {
            this.contextData = new HashMap<>();
        }
        this.contextData.put(key, value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MCPRequest that = (MCPRequest) o;
        return Objects.equals(query, that.query) && 
               Objects.equals(executionPlan, that.executionPlan) && 
               Objects.equals(contextData, that.contextData);
    }

    @Override
    public int hashCode() {
        return Objects.hash(query, executionPlan, contextData);
    }

    @Override
    public String toString() {
        return "MCPRequest{" +
                "query='" + query + '\'' +
                ", executionPlan=" + executionPlan +
                ", contextData=" + contextData +
                '}';
    }
} 