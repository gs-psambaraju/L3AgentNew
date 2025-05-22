package com.l3agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for confidence calculations.
 * Allows customization of threshold values and weights.
 */
@Configuration
@ConfigurationProperties(prefix = "l3agent.confidence")
public class ConfidenceConfig {
    
    // Weights for confidence calculation components
    private double vectorSearchWeight = 0.40;
    private double toolExecutionWeight = 0.30;
    private double evidenceQualityWeight = 0.20;
    private double queryClairtyWeight = 0.10;
    
    // Thresholds for confidence rating categories
    private double veryHighThreshold = 0.90;
    private double highThreshold = 0.75;
    private double mediumThreshold = 0.50;
    private double lowThreshold = 0.25;
    
    // Getters and setters for Spring property binding
    
    public double getVectorSearchWeight() {
        return vectorSearchWeight;
    }
    
    public void setVectorSearchWeight(double vectorSearchWeight) {
        this.vectorSearchWeight = vectorSearchWeight;
    }
    
    public double getToolExecutionWeight() {
        return toolExecutionWeight;
    }
    
    public void setToolExecutionWeight(double toolExecutionWeight) {
        this.toolExecutionWeight = toolExecutionWeight;
    }
    
    public double getEvidenceQualityWeight() {
        return evidenceQualityWeight;
    }
    
    public void setEvidenceQualityWeight(double evidenceQualityWeight) {
        this.evidenceQualityWeight = evidenceQualityWeight;
    }
    
    public double getQueryClairtyWeight() {
        return queryClairtyWeight;
    }
    
    public void setQueryClairtyWeight(double queryClairtyWeight) {
        this.queryClairtyWeight = queryClairtyWeight;
    }
    
    public double getVeryHighThreshold() {
        return veryHighThreshold;
    }
    
    public void setVeryHighThreshold(double veryHighThreshold) {
        this.veryHighThreshold = veryHighThreshold;
    }
    
    public double getHighThreshold() {
        return highThreshold;
    }
    
    public void setHighThreshold(double highThreshold) {
        this.highThreshold = highThreshold;
    }
    
    public double getMediumThreshold() {
        return mediumThreshold;
    }
    
    public void setMediumThreshold(double mediumThreshold) {
        this.mediumThreshold = mediumThreshold;
    }
    
    public double getLowThreshold() {
        return lowThreshold;
    }
    
    public void setLowThreshold(double lowThreshold) {
        this.lowThreshold = lowThreshold;
    }
} 