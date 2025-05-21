package com.l3agent.mcp.tools.errorchain.model;

/**
 * Represents a strategy for handling exceptions in the system.
 * Contains information about how a specific exception is (or should be) handled.
 */
public class HandlingStrategy {
    
    private String exceptionClass;
    private String handlerComponent;
    private String description;
    private String effectiveness;
    
    /**
     * Creates a new handling strategy.
     * 
     * @param exceptionClass The exception class being handled
     * @param handlerComponent The component that handles the exception
     * @param description Description of how the exception is handled
     * @param effectiveness Subjective assessment of the handling effectiveness (High, Medium, Low)
     */
    public HandlingStrategy(String exceptionClass, String handlerComponent, String description, String effectiveness) {
        this.exceptionClass = exceptionClass;
        this.handlerComponent = handlerComponent;
        this.description = description;
        this.effectiveness = effectiveness;
    }
    
    /**
     * Gets the exception class.
     * 
     * @return The exception class name
     */
    public String getExceptionClass() {
        return exceptionClass;
    }
    
    /**
     * Gets the handler component.
     * 
     * @return The component that handles the exception
     */
    public String getHandlerComponent() {
        return handlerComponent;
    }
    
    /**
     * Gets the description of the handling strategy.
     * 
     * @return The description
     */
    public String getDescription() {
        return description;
    }
    
    /**
     * Gets the effectiveness assessment.
     * 
     * @return The effectiveness assessment
     */
    public String getEffectiveness() {
        return effectiveness;
    }
    
    /**
     * Returns a string representation of this handling strategy.
     * 
     * @return A string representation
     */
    @Override
    public String toString() {
        return "HandlingStrategy{" +
                "exception='" + exceptionClass + '\'' +
                ", handler='" + handlerComponent + '\'' +
                ", description='" + description + '\'' +
                ", effectiveness='" + effectiveness + '\'' +
                '}';
    }
} 