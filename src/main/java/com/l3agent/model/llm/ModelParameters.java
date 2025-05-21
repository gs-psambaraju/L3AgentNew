package com.l3agent.model.llm;

/**
 * Parameters for configuring a request to a Language Model.
 * These parameters influence the behavior and output of the model.
 */
public class ModelParameters {

    private String modelName;
    private double temperature = 0.7;
    private int maxTokens = 1000;
    private double topP = 1.0;
    private double presencePenalty = 0.0;
    private double frequencyPenalty = 0.0;
    private boolean stream = false;

    // Default constructor
    public ModelParameters() {
    }
    
    // Constructor with model name
    public ModelParameters(String modelName) {
        this.modelName = modelName;
    }
    
    // Builder pattern methods for fluent API
    
    public ModelParameters withModelName(String modelName) {
        this.modelName = modelName;
        return this;
    }
    
    public ModelParameters withTemperature(double temperature) {
        this.temperature = temperature;
        return this;
    }
    
    public ModelParameters withMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
        return this;
    }
    
    public ModelParameters withTopP(double topP) {
        this.topP = topP;
        return this;
    }
    
    public ModelParameters withPresencePenalty(double presencePenalty) {
        this.presencePenalty = presencePenalty;
        return this;
    }
    
    public ModelParameters withFrequencyPenalty(double frequencyPenalty) {
        this.frequencyPenalty = frequencyPenalty;
        return this;
    }
    
    public ModelParameters withStream(boolean stream) {
        this.stream = stream;
        return this;
    }
    
    // Getters and setters
    
    public String getModelName() {
        return modelName;
    }
    
    public void setModelName(String modelName) {
        this.modelName = modelName;
    }
    
    public double getTemperature() {
        return temperature;
    }
    
    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }
    
    public int getMaxTokens() {
        return maxTokens;
    }
    
    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }
    
    public double getTopP() {
        return topP;
    }
    
    public void setTopP(double topP) {
        this.topP = topP;
    }
    
    public double getPresencePenalty() {
        return presencePenalty;
    }
    
    public void setPresencePenalty(double presencePenalty) {
        this.presencePenalty = presencePenalty;
    }
    
    public double getFrequencyPenalty() {
        return frequencyPenalty;
    }
    
    public void setFrequencyPenalty(double frequencyPenalty) {
        this.frequencyPenalty = frequencyPenalty;
    }
    
    public boolean isStream() {
        return stream;
    }
    
    public void setStream(boolean stream) {
        this.stream = stream;
    }
    
    /**
     * Creates a new instance of ModelParameters with default values.
     * 
     * @return A new ModelParameters instance
     */
    public static ModelParameters defaults() {
        return new ModelParameters();
    }
    
    /**
     * Creates a new instance of ModelParameters for creative tasks.
     * Sets a higher temperature for more diversity in responses.
     * 
     * @param modelName The name of the model to use
     * @return A new ModelParameters instance configured for creative tasks
     */
    public static ModelParameters forCreativeTask(String modelName) {
        return new ModelParameters(modelName)
                .withTemperature(0.8)
                .withMaxTokens(2000)
                .withTopP(1.0)
                .withPresencePenalty(0.0)
                .withFrequencyPenalty(0.0);
    }
    
    /**
     * Creates a new instance of ModelParameters for analytical tasks.
     * Sets a lower temperature for more deterministic responses.
     * 
     * @param modelName The name of the model to use
     * @return A new ModelParameters instance configured for analytical tasks
     */
    public static ModelParameters forAnalyticalTask(String modelName) {
        return new ModelParameters(modelName)
                .withTemperature(0.2)
                .withMaxTokens(2000)
                .withTopP(1.0)
                .withPresencePenalty(0.0)
                .withFrequencyPenalty(0.0);
    }
} 