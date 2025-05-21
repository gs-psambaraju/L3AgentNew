package com.l3agent.mcp.model;

import java.util.Objects;

/**
 * Defines a parameter that a tool accepts.
 */
public class ToolParameter {
    private String name;
    private String description;
    private String type;
    private boolean required;
    private Object defaultValue;

    public ToolParameter() {
    }

    public ToolParameter(String name, String description, String type, boolean required, Object defaultValue) {
        this.name = name;
        this.description = description;
        this.type = type;
        this.required = required;
        this.defaultValue = defaultValue;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    public Object getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(Object defaultValue) {
        this.defaultValue = defaultValue;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ToolParameter that = (ToolParameter) o;
        return required == that.required && 
               Objects.equals(name, that.name) && 
               Objects.equals(description, that.description) && 
               Objects.equals(type, that.type) && 
               Objects.equals(defaultValue, that.defaultValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, description, type, required, defaultValue);
    }

    @Override
    public String toString() {
        return "ToolParameter{" +
                "name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", type='" + type + '\'' +
                ", required=" + required +
                ", defaultValue=" + defaultValue +
                '}';
    }
} 