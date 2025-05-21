package com.l3agent.mcp.tools.config.model;

/**
 * Represents a source of a configuration property.
 */
public class PropertySource {
    
    private String propertyName;
    private String value;
    private String sourceFile;
    private int position;
    
    /**
     * Creates a new property source.
     * 
     * @param propertyName The name of the property
     * @param value The value of the property
     * @param sourceFile The source file containing the property
     * @param position The position in the file
     */
    public PropertySource(String propertyName, String value, String sourceFile, int position) {
        this.propertyName = propertyName;
        this.value = value;
        this.sourceFile = sourceFile;
        this.position = position;
    }
    
    /**
     * Gets the property name.
     * 
     * @return The property name
     */
    public String getPropertyName() {
        return propertyName;
    }
    
    /**
     * Gets the property value.
     * 
     * @return The property value
     */
    public String getValue() {
        return value;
    }
    
    /**
     * Gets the source file.
     * 
     * @return The source file
     */
    public String getSourceFile() {
        return sourceFile;
    }
    
    /**
     * Gets the position in the file.
     * 
     * @return The position
     */
    public int getPosition() {
        return position;
    }
} 