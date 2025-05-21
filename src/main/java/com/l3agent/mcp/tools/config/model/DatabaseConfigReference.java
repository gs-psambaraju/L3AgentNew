package com.l3agent.mcp.tools.config.model;

/**
 * Specialized property reference for database configuration properties.
 * Extends the base PropertyReference with database-specific fields.
 */
public class DatabaseConfigReference extends PropertyReference {

    private String databaseType; // MySQL, PostgreSQL, etc.
    private String propertyType; // connection, authentication, driver, etc.
    private boolean isSensitive;
    private boolean isCritical;
    
    /**
     * Creates a new database config reference from an existing property reference.
     * 
     * @param ref The base property reference
     */
    public DatabaseConfigReference(PropertyReference ref) {
        super(ref.getClassName(), ref.getComponentType());
        this.setPropertyName(ref.getPropertyName());
        this.setFieldName(ref.getFieldName());
        this.setMethodName(ref.getMethodName());
        this.setLineNumber(ref.getLineNumber());
        this.setReferenceType(ref.getReferenceType());
        this.setValue(ref.getValue());
        this.setAccessPattern(ref.getAccessPattern());
        this.setNotes(ref.getNotes());
        this.setCriticalComponent(ref.isCriticalComponent());
    }
    
    /**
     * Creates a new database config reference.
     * 
     * @param className The name of the class
     * @param componentType The component type
     */
    public DatabaseConfigReference(String className, String componentType) {
        super(className, componentType);
    }
    
    /**
     * Gets the database type (e.g., MySQL, PostgreSQL).
     * 
     * @return The database type
     */
    public String getDatabaseType() {
        return databaseType;
    }
    
    /**
     * Sets the database type.
     * 
     * @param databaseType The database type
     * @return This reference for chaining
     */
    public DatabaseConfigReference setDatabaseType(String databaseType) {
        this.databaseType = databaseType;
        return this;
    }
    
    /**
     * Gets the property type (e.g., connection, authentication).
     * 
     * @return The property type
     */
    public String getPropertyType() {
        return propertyType;
    }
    
    /**
     * Sets the property type.
     * 
     * @param propertyType The property type
     * @return This reference for chaining
     */
    public DatabaseConfigReference setPropertyType(String propertyType) {
        this.propertyType = propertyType;
        return this;
    }
    
    /**
     * Checks if this property contains sensitive information.
     * 
     * @return true if sensitive
     */
    public boolean isSensitive() {
        return isSensitive;
    }
    
    /**
     * Sets whether this property contains sensitive information.
     * 
     * @param isSensitive true if sensitive
     * @return This reference for chaining
     */
    public DatabaseConfigReference setIsSensitive(boolean isSensitive) {
        this.isSensitive = isSensitive;
        return this;
    }
    
    /**
     * Checks if this property is critical for the database connection.
     * 
     * @return true if critical
     */
    public boolean isCritical() {
        return isCritical;
    }
    
    /**
     * Sets whether this property is critical for the database connection.
     * 
     * @param isCritical true if critical
     * @return This reference for chaining
     */
    public DatabaseConfigReference setIsCritical(boolean isCritical) {
        this.isCritical = isCritical;
        return this;
    }
} 