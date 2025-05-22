package com.l3agent.model;

/**
 * Metrics used for calculating confidence in L3Agent responses.
 * Collects all data points needed for confidence algorithm.
 */
public class ConfidenceMetrics {
    private double vectorSearchScore;
    private int toolExecutionCount;
    private int successfulToolExecutions;
    private int evidenceCount;
    private int relevantEvidenceCount;
    private double averageEvidenceQuality;
    private double queryClarity;

    // Default constructor
    public ConfidenceMetrics() {
        this.vectorSearchScore = 0.0;
        this.toolExecutionCount = 0;
        this.successfulToolExecutions = 0;
        this.evidenceCount = 0;
        this.relevantEvidenceCount = 0;
        this.averageEvidenceQuality = 0.0;
        this.queryClarity = 0.0;
    }
    
    // Constructor with all fields
    public ConfidenceMetrics(double vectorSearchScore, int toolExecutionCount, int successfulToolExecutions,
                           int evidenceCount, int relevantEvidenceCount, double averageEvidenceQuality,
                           double queryClarity) {
        this.vectorSearchScore = vectorSearchScore;
        this.toolExecutionCount = toolExecutionCount;
        this.successfulToolExecutions = successfulToolExecutions;
        this.evidenceCount = evidenceCount;
        this.relevantEvidenceCount = relevantEvidenceCount;
        this.averageEvidenceQuality = averageEvidenceQuality;
        this.queryClarity = queryClarity;
    }

    // Builder pattern methods
    public ConfidenceMetrics withVectorSearchScore(double vectorSearchScore) {
        this.vectorSearchScore = vectorSearchScore;
        return this;
    }

    public ConfidenceMetrics withToolExecutionCount(int toolExecutionCount) {
        this.toolExecutionCount = toolExecutionCount;
        return this;
    }

    public ConfidenceMetrics withSuccessfulToolExecutions(int successfulToolExecutions) {
        this.successfulToolExecutions = successfulToolExecutions;
        return this;
    }

    public ConfidenceMetrics withEvidenceCount(int evidenceCount) {
        this.evidenceCount = evidenceCount;
        return this;
    }

    public ConfidenceMetrics withRelevantEvidenceCount(int relevantEvidenceCount) {
        this.relevantEvidenceCount = relevantEvidenceCount;
        return this;
    }

    public ConfidenceMetrics withAverageEvidenceQuality(double averageEvidenceQuality) {
        this.averageEvidenceQuality = averageEvidenceQuality;
        return this;
    }

    public ConfidenceMetrics withQueryClarity(double queryClarity) {
        this.queryClarity = queryClarity;
        return this;
    }

    // Getters and setters
    public double getVectorSearchScore() {
        return vectorSearchScore;
    }

    public void setVectorSearchScore(double vectorSearchScore) {
        this.vectorSearchScore = vectorSearchScore;
    }

    public int getToolExecutionCount() {
        return toolExecutionCount;
    }

    public void setToolExecutionCount(int toolExecutionCount) {
        this.toolExecutionCount = toolExecutionCount;
    }

    public int getSuccessfulToolExecutions() {
        return successfulToolExecutions;
    }

    public void setSuccessfulToolExecutions(int successfulToolExecutions) {
        this.successfulToolExecutions = successfulToolExecutions;
    }

    public int getEvidenceCount() {
        return evidenceCount;
    }

    public void setEvidenceCount(int evidenceCount) {
        this.evidenceCount = evidenceCount;
    }

    public int getRelevantEvidenceCount() {
        return relevantEvidenceCount;
    }

    public void setRelevantEvidenceCount(int relevantEvidenceCount) {
        this.relevantEvidenceCount = relevantEvidenceCount;
    }

    public double getAverageEvidenceQuality() {
        return averageEvidenceQuality;
    }

    public void setAverageEvidenceQuality(double averageEvidenceQuality) {
        this.averageEvidenceQuality = averageEvidenceQuality;
    }

    public double getQueryClarity() {
        return queryClarity;
    }

    public void setQueryClarity(double queryClarity) {
        this.queryClarity = queryClarity;
    }

    // Helper methods
    public double getToolSuccessRate() {
        if (toolExecutionCount == 0) {
            return 0.0;
        }
        return (double) successfulToolExecutions / toolExecutionCount;
    }

    public double getEvidenceRelevanceRate() {
        if (evidenceCount == 0) {
            return 0.0;
        }
        return (double) relevantEvidenceCount / evidenceCount;
    }
} 