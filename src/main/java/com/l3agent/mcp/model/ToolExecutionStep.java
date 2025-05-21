package com.l3agent.mcp.model;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a single step in a tool execution plan.
 */
public class ToolExecutionStep {
    private String toolName;
    private Map<String, Object> parameters;
    private int priority;
    private boolean required;

    public ToolExecutionStep() {
        this.parameters = new HashMap<>();
        this.priority = 0;
        this.required = true;
    }

    public ToolExecutionStep(String toolName, Map<String, Object> parameters, int priority, boolean required) {
        this.toolName = toolName;
        this.parameters = parameters != null ? parameters : new HashMap<>();
        this.priority = priority;
        this.required = required;
    }

    public String getToolName() {
        return toolName;
    }

    public void setToolName(String toolName) {
        this.toolName = toolName;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, Object> parameters) {
        this.parameters = parameters;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }
    
    public void addParameter(String name, Object value) {
        if (this.parameters == null) {
            this.parameters = new HashMap<>();
        }
        this.parameters.put(name, value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ToolExecutionStep that = (ToolExecutionStep) o;
        return priority == that.priority && 
               required == that.required && 
               Objects.equals(toolName, that.toolName) && 
               Objects.equals(parameters, that.parameters);
    }

    @Override
    public int hashCode() {
        return Objects.hash(toolName, parameters, priority, required);
    }

    @Override
    public String toString() {
        return "ToolExecutionStep{" +
                "toolName='" + toolName + '\'' +
                ", parameters=" + parameters +
                ", priority=" + priority +
                ", required=" + required +
                '}';
    }
} 