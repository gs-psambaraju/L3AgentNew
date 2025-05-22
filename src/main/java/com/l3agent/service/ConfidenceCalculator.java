package com.l3agent.service;

import com.l3agent.config.ConfidenceConfig;
import com.l3agent.model.ConfidenceMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for calculating response confidence ratings.
 * Uses a weighted algorithm based on various metrics to determine confidence.
 */
@Service
public class ConfidenceCalculator {
    private static final Logger logger = LoggerFactory.getLogger(ConfidenceCalculator.class);
    
    @Autowired
    private ConfidenceConfig confidenceConfig;
    
    /**
     * Calculates the confidence score based on the provided metrics.
     * 
     * @param metrics The metrics to use for confidence calculation
     * @return A confidence score between 0.0 and 1.0
     */
    public double calculateConfidence(ConfidenceMetrics metrics) {
        if (metrics == null) {
            logger.warn("Null metrics provided for confidence calculation, returning default low confidence");
            return 0.1; // Default low confidence
        }
        
        // Calculate each component score
        double vectorSearchComponent = calculateVectorSearchComponent(metrics);
        double toolExecutionComponent = calculateToolExecutionComponent(metrics);
        double evidenceQualityComponent = calculateEvidenceQualityComponent(metrics);
        double queryComponent = calculateQueryComponent(metrics);
        
        // Apply weights to each component
        double weightedScore = 
            (vectorSearchComponent * confidenceConfig.getVectorSearchWeight()) +
            (toolExecutionComponent * confidenceConfig.getToolExecutionWeight()) +
            (evidenceQualityComponent * confidenceConfig.getEvidenceQualityWeight()) +
            (queryComponent * confidenceConfig.getQueryClairtyWeight());
        
        // Ensure score is between 0.0 and 1.0
        double finalScore = Math.max(0.0, Math.min(1.0, weightedScore));
        
        logger.debug("Calculated confidence score: {} (vector: {}, tools: {}, evidence: {}, query: {})",
                finalScore, vectorSearchComponent, toolExecutionComponent,
                evidenceQualityComponent, queryComponent);
        
        return finalScore;
    }
    
    /**
     * Calculates the vector search component of the confidence score.
     * Based on the quality of vector search matches.
     */
    private double calculateVectorSearchComponent(ConfidenceMetrics metrics) {
        // Simple pass-through as the vector score should already be normalized
        return Math.max(0.0, Math.min(1.0, metrics.getVectorSearchScore()));
    }
    
    /**
     * Calculates the tool execution component of the confidence score.
     * Based on the success rate of tool executions.
     */
    private double calculateToolExecutionComponent(ConfidenceMetrics metrics) {
        if (metrics.getToolExecutionCount() == 0) {
            return 0.5; // Neutral score if no tools were executed
        }
        
        // Success rate of tool executions
        return metrics.getToolSuccessRate();
    }
    
    /**
     * Calculates the evidence quality component of the confidence score.
     * Based on the relevance and quality of evidence.
     */
    private double calculateEvidenceQualityComponent(ConfidenceMetrics metrics) {
        if (metrics.getEvidenceCount() == 0) {
            return 0.3; // Low-medium score if no evidence was found
        }
        
        // Combine evidence relevance rate with average evidence quality
        double relevanceWeight = 0.6;
        double qualityWeight = 0.4;
        
        return (metrics.getEvidenceRelevanceRate() * relevanceWeight) +
               (metrics.getAverageEvidenceQuality() * qualityWeight);
    }
    
    /**
     * Calculates the query component of the confidence score.
     * Based on the clarity and specificity of the query.
     */
    private double calculateQueryComponent(ConfidenceMetrics metrics) {
        // Simple pass-through as the query clarity should already be normalized
        return Math.max(0.0, Math.min(1.0, metrics.getQueryClarity()));
    }
    
    /**
     * Gets a human-readable confidence rating based on a numerical score.
     * 
     * @param confidenceScore The numerical confidence score between 0.0 and 1.0
     * @return A human-readable confidence rating
     */
    public String getConfidenceRating(double confidenceScore) {
        if (confidenceScore >= confidenceConfig.getVeryHighThreshold()) {
            return "Very High";
        } else if (confidenceScore >= confidenceConfig.getHighThreshold()) {
            return "High";
        } else if (confidenceScore >= confidenceConfig.getMediumThreshold()) {
            return "Medium";
        } else if (confidenceScore >= confidenceConfig.getLowThreshold()) {
            return "Low";
        } else {
            return "Very Low";
        }
    }
    
    /**
     * Generates an explanation for the confidence score.
     * 
     * @param metrics The metrics used to calculate the confidence
     * @param confidenceScore The calculated confidence score
     * @return A map containing explanation details
     */
    public Map<String, Object> generateConfidenceExplanation(ConfidenceMetrics metrics, double confidenceScore) {
        Map<String, Object> explanation = new HashMap<>();
        
        // Add overall confidence information
        explanation.put("confidence_score", confidenceScore);
        explanation.put("confidence_rating", getConfidenceRating(confidenceScore));
        
        // Add component contributions
        Map<String, Object> components = new HashMap<>();
        
        double vectorComponent = calculateVectorSearchComponent(metrics) * confidenceConfig.getVectorSearchWeight();
        double toolComponent = calculateToolExecutionComponent(metrics) * confidenceConfig.getToolExecutionWeight();
        double evidenceComponent = calculateEvidenceQualityComponent(metrics) * confidenceConfig.getEvidenceQualityWeight();
        double queryComponent = calculateQueryComponent(metrics) * confidenceConfig.getQueryClairtyWeight();
        
        components.put("vector_search", Map.of(
            "raw_score", metrics.getVectorSearchScore(), 
            "weighted_contribution", vectorComponent,
            "percentage", Math.round((vectorComponent / confidenceScore) * 100) + "%"
        ));
        
        components.put("tool_execution", Map.of(
            "raw_score", metrics.getToolExecutionCount() > 0 ? metrics.getToolSuccessRate() : 0.5,
            "tool_success_rate", metrics.getToolSuccessRate(),
            "weighted_contribution", toolComponent,
            "percentage", Math.round((toolComponent / confidenceScore) * 100) + "%"
        ));
        
        components.put("evidence_quality", Map.of(
            "raw_score", metrics.getEvidenceCount() > 0 ? 
                        (metrics.getEvidenceRelevanceRate() * 0.6 + metrics.getAverageEvidenceQuality() * 0.4) : 0.3,
            "evidence_count", metrics.getEvidenceCount(),
            "relevant_evidence", metrics.getRelevantEvidenceCount(),
            "weighted_contribution", evidenceComponent,
            "percentage", Math.round((evidenceComponent / confidenceScore) * 100) + "%"
        ));
        
        components.put("query_clarity", Map.of(
            "raw_score", metrics.getQueryClarity(),
            "weighted_contribution", queryComponent,
            "percentage", Math.round((queryComponent / confidenceScore) * 100) + "%"
        ));
        
        explanation.put("components", components);
        
        // Add factors that affected the score
        List<String> factors = generateFactors(metrics, confidenceScore);
        explanation.put("key_factors", factors);
        
        return explanation;
    }
    
    /**
     * Generates a list of key factors that influenced the confidence score.
     */
    private List<String> generateFactors(ConfidenceMetrics metrics, double confidenceScore) {
        List<String> factors = new ArrayList<>();
        
        // Vector search factors
        if (metrics.getVectorSearchScore() > 0.8) {
            factors.add("High-quality vector search matches found");
        } else if (metrics.getVectorSearchScore() < 0.3) {
            factors.add("Few or low-quality vector search matches");
        }
        
        // Tool execution factors
        if (metrics.getToolExecutionCount() > 0) {
            if (metrics.getToolSuccessRate() > 0.8) {
                factors.add("Most or all tool executions completed successfully");
            } else if (metrics.getToolSuccessRate() < 0.5) {
                factors.add("Several tool executions failed");
            }
        } else {
            factors.add("No code analysis tools were executed");
        }
        
        // Evidence factors
        if (metrics.getEvidenceCount() > 10) {
            factors.add("Large amount of supporting evidence available");
        } else if (metrics.getEvidenceCount() < 3) {
            factors.add("Limited supporting evidence available");
        }
        
        if (metrics.getEvidenceCount() > 0) {
            if (metrics.getEvidenceRelevanceRate() > 0.8) {
                factors.add("Evidence is highly relevant to the query");
            } else if (metrics.getEvidenceRelevanceRate() < 0.4) {
                factors.add("Evidence has limited relevance to the query");
            }
        }
        
        // Query clarity factors
        if (metrics.getQueryClarity() > 0.8) {
            factors.add("Query is clear and specific");
        } else if (metrics.getQueryClarity() < 0.4) {
            factors.add("Query is ambiguous or too general");
        }
        
        // If no specific factors identified, add a generic one
        if (factors.isEmpty()) {
            if (confidenceScore > 0.7) {
                factors.add("Overall strong performance across multiple metrics");
            } else if (confidenceScore < 0.4) {
                factors.add("Multiple metrics indicating limited confidence");
            } else {
                factors.add("Mixed results across confidence metrics");
            }
        }
        
        return factors;
    }
} 