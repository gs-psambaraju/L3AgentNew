package com.l3agent.mcp.tools.config.model;

/**
 * Stores information about how a configuration property is used in the codebase.
 * This provides deeper context about the property's impact on the system.
 */
public class PropertyUsageContext {
    
    private String injectionType;    // How the property is injected (field, method, etc.)
    private int readCount;           // Number of times the property is read
    private int writeCount;          // Number of times the property is written
    private int conditionalUses;     // Number of times the property is used in conditional statements
    private int usageInLoops;        // Number of times the property is used in loops
    private int mutatingOperations;  // Number of operations that modify the property
    
    /**
     * Creates a new property usage context.
     */
    public PropertyUsageContext() {
        this.readCount = 0;
        this.writeCount = 0;
        this.conditionalUses = 0;
        this.usageInLoops = 0;
        this.mutatingOperations = 0;
    }
    
    /**
     * Gets the injection type.
     * 
     * @return The injection type (field, method, etc.)
     */
    public String getInjectionType() {
        return injectionType;
    }
    
    /**
     * Sets the injection type.
     * 
     * @param injectionType The injection type
     */
    public void setInjectionType(String injectionType) {
        this.injectionType = injectionType;
    }
    
    /**
     * Gets the number of times the property is read.
     * 
     * @return The read count
     */
    public int getReadCount() {
        return readCount;
    }
    
    /**
     * Sets the number of times the property is read.
     * 
     * @param readCount The read count
     */
    public void setReadCount(int readCount) {
        this.readCount = readCount;
    }
    
    /**
     * Gets the number of times the property is written.
     * 
     * @return The write count
     */
    public int getWriteCount() {
        return writeCount;
    }
    
    /**
     * Sets the number of times the property is written.
     * 
     * @param writeCount The write count
     */
    public void setWriteCount(int writeCount) {
        this.writeCount = writeCount;
    }
    
    /**
     * Gets the number of times the property is used in conditional statements.
     * 
     * @return The conditional uses count
     */
    public int getConditionalUses() {
        return conditionalUses;
    }
    
    /**
     * Sets the number of times the property is used in conditional statements.
     * 
     * @param conditionalUses The conditional uses count
     */
    public void setConditionalUses(int conditionalUses) {
        this.conditionalUses = conditionalUses;
    }
    
    /**
     * Gets the number of times the property is used in loops.
     * 
     * @return The loop usage count
     */
    public int getUsageInLoops() {
        return usageInLoops;
    }
    
    /**
     * Sets the number of times the property is used in loops.
     * 
     * @param usageInLoops The loop usage count
     */
    public void setUsageInLoops(int usageInLoops) {
        this.usageInLoops = usageInLoops;
    }
    
    /**
     * Gets the number of operations that modify the property.
     * 
     * @return The mutating operations count
     */
    public int getMutatingOperations() {
        return mutatingOperations;
    }
    
    /**
     * Sets the number of operations that modify the property.
     * 
     * @param mutatingOperations The mutating operations count
     */
    public void setMutatingOperations(int mutatingOperations) {
        this.mutatingOperations = mutatingOperations;
    }
    
    /**
     * Determines if the property is critical based on its usage patterns.
     * 
     * @return true if the property is critical
     */
    public boolean isCritical() {
        // Property is critical if it's used in conditions or loops frequently
        return conditionalUses > 1 || usageInLoops > 0;
    }
    
    /**
     * Gets a summary of how the property is used.
     * 
     * @return A summary string
     */
    public String getUsageSummary() {
        StringBuilder summary = new StringBuilder();
        
        summary.append("Injected via ").append(injectionType).append(". ");
        summary.append("Read ").append(readCount).append(" times. ");
        
        if (writeCount > 0) {
            summary.append("Written ").append(writeCount).append(" times. ");
        }
        
        if (conditionalUses > 0) {
            summary.append("Used in ").append(conditionalUses).append(" conditional statements. ");
        }
        
        if (usageInLoops > 0) {
            summary.append("Used in ").append(usageInLoops).append(" loops. ");
        }
        
        if (mutatingOperations > 0) {
            summary.append("Modified by ").append(mutatingOperations).append(" operations. ");
        }
        
        return summary.toString();
    }
} 