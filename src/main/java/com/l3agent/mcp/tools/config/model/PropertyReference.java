package com.l3agent.mcp.tools.config.model;

/**
 * Represents a reference to a configuration property in the codebase.
 */
public class PropertyReference {
    
    private String className;
    private String methodName;
    private String fieldName;
    private String componentType;
    private String referenceType;
    private Integer lineNumber;
    private boolean criticalComponent;
    private String accessPattern;
    private String notes;
    private String propertyName;  // The name of the property (e.g., spring.datasource.url)
    private String value;         // The value of the property if known
    private PropertyUsageContext context; // Context information about how the property is used
    
    /**
     * Creates a new property reference.
     * 
     * @param className The fully qualified class name
     * @param componentType The type of component (e.g., Repository, Service, Controller)
     */
    public PropertyReference(String className, String componentType) {
        this.className = className;
        this.componentType = componentType;
        this.criticalComponent = false;
    }
    
    /**
     * Gets the fully qualified class name.
     * 
     * @return The class name
     */
    public String getClassName() {
        return className;
    }
    
    /**
     * Gets the method name where the property is referenced.
     * 
     * @return The method name or null if referenced at class level
     */
    public String getMethodName() {
        return methodName;
    }
    
    /**
     * Sets the method name where the property is referenced.
     * 
     * @param methodName The method name
     * @return This property reference for chaining
     */
    public PropertyReference setMethodName(String methodName) {
        this.methodName = methodName;
        return this;
    }
    
    /**
     * Gets the field name where the property is injected.
     * 
     * @return The field name or null if not injected into a field
     */
    public String getFieldName() {
        return fieldName;
    }
    
    /**
     * Sets the field name where the property is injected.
     * 
     * @param fieldName The field name
     * @return This property reference for chaining
     */
    public PropertyReference setFieldName(String fieldName) {
        this.fieldName = fieldName;
        return this;
    }
    
    /**
     * Gets the component type.
     * 
     * @return The component type (e.g., Repository, Service, Controller)
     */
    public String getComponentType() {
        return componentType;
    }
    
    /**
     * Gets the type of reference.
     * 
     * @return The reference type (e.g., @Value, environment.getProperty)
     */
    public String getReferenceType() {
        return referenceType;
    }
    
    /**
     * Sets the type of reference.
     * 
     * @param referenceType The reference type
     * @return This property reference for chaining
     */
    public PropertyReference setReferenceType(String referenceType) {
        this.referenceType = referenceType;
        return this;
    }
    
    /**
     * Gets the line number where the property is referenced.
     * 
     * @return The line number or null if not available
     */
    public Integer getLineNumber() {
        return lineNumber;
    }
    
    /**
     * Sets the line number where the property is referenced.
     * 
     * @param lineNumber The line number
     * @return This property reference for chaining
     */
    public PropertyReference setLineNumber(Integer lineNumber) {
        this.lineNumber = lineNumber;
        return this;
    }
    
    /**
     * Checks if the component is critical.
     * 
     * @return true if this is a critical component
     */
    public boolean isCriticalComponent() {
        return criticalComponent;
    }
    
    /**
     * Sets whether the component is critical.
     * 
     * @param criticalComponent true if this is a critical component
     * @return This property reference for chaining
     */
    public PropertyReference setCriticalComponent(boolean criticalComponent) {
        this.criticalComponent = criticalComponent;
        return this;
    }
    
    /**
     * Gets the access pattern used to access the property.
     * 
     * @return The access pattern (e.g., direct, fallback, conditional)
     */
    public String getAccessPattern() {
        return accessPattern;
    }
    
    /**
     * Sets the access pattern used to access the property.
     * 
     * @param accessPattern The access pattern
     * @return This property reference for chaining
     */
    public PropertyReference setAccessPattern(String accessPattern) {
        this.accessPattern = accessPattern;
        return this;
    }
    
    /**
     * Gets additional notes about this reference.
     * 
     * @return Additional notes
     */
    public String getNotes() {
        return notes;
    }
    
    /**
     * Sets additional notes about this reference.
     * 
     * @param notes Additional notes
     * @return This property reference for chaining
     */
    public PropertyReference setNotes(String notes) {
        this.notes = notes;
        return this;
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
     * Sets the property name.
     * 
     * @param propertyName The property name
     * @return This property reference for chaining
     */
    public PropertyReference setPropertyName(String propertyName) {
        this.propertyName = propertyName;
        return this;
    }
    
    /**
     * Gets the property value if known.
     * 
     * @return The property value
     */
    public String getValue() {
        return value;
    }
    
    /**
     * Sets the property value.
     * 
     * @param value The property value
     * @return This property reference for chaining
     */
    public PropertyReference setValue(String value) {
        this.value = value;
        return this;
    }
    
    /**
     * Gets a display name for this reference.
     * 
     * @return A display name in the format className.methodName or className.fieldName
     */
    public String getDisplayName() {
        if (methodName != null && !methodName.isEmpty()) {
            return className + "." + methodName + "()";
        } else if (fieldName != null && !fieldName.isEmpty()) {
            return className + "." + fieldName;
        } else {
            return className;
        }
    }
    
    /**
     * Gets the property usage context.
     * 
     * @return Context information about how the property is used
     */
    public PropertyUsageContext getContext() {
        return context;
    }
    
    /**
     * Sets the property usage context.
     * 
     * @param context Context information about how the property is used
     * @return This property reference for chaining
     */
    public PropertyReference setContext(PropertyUsageContext context) {
        this.context = context;
        return this;
    }
} 