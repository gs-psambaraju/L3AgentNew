package com.l3agent.service;

import com.l3agent.model.ConfidenceMetrics;
import com.l3agent.model.llm.LLMResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Service for enhancing responses with confidence ratings.
 * Extracts metrics from various data sources and calculates confidence.
 */
@Service
public class ConfidenceEnhancer {
    private static final Logger logger = LoggerFactory.getLogger(ConfidenceEnhancer.class);
    
    @Autowired
    private ConfidenceCalculator confidenceCalculator;
    
    /**
     * Enhances an LLM response with confidence metrics and ratings.
     * 
     * @param response The LLM response to enhance
     * @param codeSnippets List of code snippets used for the response
     * @param sourceCounts Map of source counts (knowledge articles, etc.)
     * @param mcpResults Results from MCP tool executions
     * @param query The original query
     * @return The enhanced response with confidence metrics
     */
    public LLMResponse enhanceWithConfidence(
            LLMResponse response,
            List<CodeRepositoryService.CodeSnippet> codeSnippets,
            Map<String, Integer> sourceCounts,
            Map<String, Object> mcpResults,
            String query) {
        
        if (response == null) {
            logger.warn("Cannot enhance null response with confidence");
            return null;
        }
        
        // Extract all metrics needed for confidence calculation
        ConfidenceMetrics metrics = extractMetrics(codeSnippets, sourceCounts, mcpResults, query);
        
        // Calculate the confidence score
        double confidenceScore = confidenceCalculator.calculateConfidence(metrics);
        
        // Add the confidence score to the response
        response.withConfidence(confidenceScore);
        
        // Add the confidence rating to the metadata
        response.addMetadata("confidence_rating", confidenceCalculator.getConfidenceRating(confidenceScore));
        
        // Generate and add confidence explanation
        Map<String, Object> explanation = confidenceCalculator.generateConfidenceExplanation(metrics, confidenceScore);
        response.addMetadata("confidence_explanation", explanation);
        
        // Add detailed metrics to the metadata for transparency
        addMetricsToMetadata(response, metrics);
        
        logger.info("Enhanced response with confidence score: {} ({})", 
                confidenceScore, 
                confidenceCalculator.getConfidenceRating(confidenceScore));
        
        return response;
    }
    
    /**
     * Extracts confidence metrics from various data sources.
     */
    private ConfidenceMetrics extractMetrics(
            List<CodeRepositoryService.CodeSnippet> codeSnippets,
            Map<String, Integer> sourceCounts,
            Map<String, Object> mcpResults,
            String query) {
        
        ConfidenceMetrics metrics = new ConfidenceMetrics();
        
        // 1. Vector search metrics
        metrics.withVectorSearchScore(calculateVectorSearchScore(codeSnippets));
        
        // 2. Tool execution metrics
        if (mcpResults != null) {
            extractToolMetrics(metrics, mcpResults);
        }
        
        // 3. Evidence quality metrics
        extractEvidenceMetrics(metrics, codeSnippets, sourceCounts);
        
        // 4. Query clarity metrics
        metrics.withQueryClarity(calculateQueryClarity(query));
        
        return metrics;
    }
    
    /**
     * Calculates a vector search score based on code snippet relevance.
     */
    private double calculateVectorSearchScore(List<CodeRepositoryService.CodeSnippet> codeSnippets) {
        if (codeSnippets == null || codeSnippets.isEmpty()) {
            return 0.0;
        }
        
        // Calculate average similarity score from top snippets
        double totalScore = 0.0;
        int count = 0;
        
        for (CodeRepositoryService.CodeSnippet snippet : codeSnippets) {
            if (snippet.getRelevance() > 0) {
                totalScore += snippet.getRelevance();
                count++;
            }
        }
        
        if (count == 0) {
            return 0.3; // Default low-medium score if no scores available
        }
        
        return totalScore / count;
    }
    
    /**
     * Extracts tool execution metrics from MCP results.
     */
    private void extractToolMetrics(ConfidenceMetrics metrics, Map<String, Object> mcpResults) {
        // Count total tool executions
        int toolExecutionCount = 0;
        int successfulExecutions = 0;
        
        if (mcpResults.containsKey("tools_used")) {
            List<?> toolsUsed = (List<?>) mcpResults.get("tools_used");
            toolExecutionCount = toolsUsed.size();
        }
        
        if (mcpResults.containsKey("tools_succeeded")) {
            List<?> toolsSucceeded = (List<?>) mcpResults.get("tools_succeeded");
            successfulExecutions = toolsSucceeded.size();
        }
        
        metrics.withToolExecutionCount(toolExecutionCount)
               .withSuccessfulToolExecutions(successfulExecutions);
    }
    
    /**
     * Extracts evidence quality metrics from code snippets and source counts.
     */
    private void extractEvidenceMetrics(
            ConfidenceMetrics metrics,
            List<CodeRepositoryService.CodeSnippet> codeSnippets,
            Map<String, Integer> sourceCounts) {
        
        int totalEvidenceCount = 0;
        int relevantEvidenceCount = 0;
        double totalQualityScore = 0.0;
        
        // Count code snippets
        if (codeSnippets != null) {
            totalEvidenceCount += codeSnippets.size();
            
            // Count relevant snippets (with relevance score above threshold)
            double relevanceThreshold = 0.5;
            for (CodeRepositoryService.CodeSnippet snippet : codeSnippets) {
                if (snippet.getRelevance() >= relevanceThreshold) {
                    relevantEvidenceCount++;
                    totalQualityScore += snippet.getRelevance();
                }
            }
        }
        
        // Add other evidence sources from source counts
        if (sourceCounts != null) {
            if (sourceCounts.containsKey("knowledge_articles")) {
                totalEvidenceCount += sourceCounts.get("knowledge_articles");
                relevantEvidenceCount += sourceCounts.get("knowledge_articles"); // Assume all are relevant
                totalQualityScore += sourceCounts.get("knowledge_articles") * 0.8; // Assume high quality
            }
            
            if (sourceCounts.containsKey("related_entities")) {
                totalEvidenceCount += sourceCounts.get("related_entities");
                relevantEvidenceCount += sourceCounts.get("related_entities"); // Assume all are relevant
                totalQualityScore += sourceCounts.get("related_entities") * 0.7; // Assume medium-high quality
            }
        }
        
        // Calculate average evidence quality
        double averageQuality = totalEvidenceCount > 0 ? 
                totalQualityScore / totalEvidenceCount : 0.0;
        
        metrics.withEvidenceCount(totalEvidenceCount)
               .withRelevantEvidenceCount(relevantEvidenceCount)
               .withAverageEvidenceQuality(averageQuality);
    }
    
    /**
     * Calculates query clarity based on the query.
     * This is a simple heuristic - in a production system, this might use more
     * sophisticated NLP techniques.
     */
    private double calculateQueryClarity(String query) {
        if (query == null || query.trim().isEmpty()) {
            return 0.1; // Very low clarity for empty query
        }
        
        // Simple heuristics based on query length and structure
        double lengthScore = Math.min(1.0, query.length() / 50.0); // Longer queries are generally clearer
        
        // Check for specific keywords that indicate clear queries
        double keywordScore = 0.0;
        String lowerQuery = query.toLowerCase();
        if (lowerQuery.contains("how") || lowerQuery.contains("what") || 
            lowerQuery.contains("why") || lowerQuery.contains("where") ||
            lowerQuery.contains("when") || lowerQuery.contains("which")) {
            keywordScore += 0.2;
        }
        
        if (lowerQuery.contains("code") || lowerQuery.contains("function") || 
            lowerQuery.contains("class") || lowerQuery.contains("method") ||
            lowerQuery.contains("error")) {
            keywordScore += 0.3;
        }
        
        // Combine scores with weights
        double weightedScore = (lengthScore * 0.4) + (keywordScore * 0.6);
        
        // Limit to range [0.1, 1.0] - even the worst query gets a small non-zero score
        return Math.max(0.1, Math.min(1.0, weightedScore));
    }
    
    /**
     * Adds confidence metrics to the response metadata for transparency.
     */
    private void addMetricsToMetadata(LLMResponse response, ConfidenceMetrics metrics) {
        Map<String, Object> confidenceMetadata = Map.of(
            "vector_search_score", metrics.getVectorSearchScore(),
            "tool_execution_count", metrics.getToolExecutionCount(),
            "successful_tool_executions", metrics.getSuccessfulToolExecutions(),
            "tool_success_rate", metrics.getToolSuccessRate(),
            "evidence_count", metrics.getEvidenceCount(),
            "relevant_evidence_count", metrics.getRelevantEvidenceCount(),
            "evidence_relevance_rate", metrics.getEvidenceRelevanceRate(),
            "average_evidence_quality", metrics.getAverageEvidenceQuality(),
            "query_clarity", metrics.getQueryClarity()
        );
        
        response.addMetadata("confidence_metrics", confidenceMetadata);
    }
} 