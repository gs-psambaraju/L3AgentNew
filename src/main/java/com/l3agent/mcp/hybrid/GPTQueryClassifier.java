package com.l3agent.mcp.hybrid;

import com.l3agent.model.llm.LLMRequest;
import com.l3agent.model.llm.LLMResponse;
import com.l3agent.model.llm.ModelParameters;
import com.l3agent.service.LLMService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Uses GPT models to classify user queries for the hybrid query execution engine.
 * This helps determine which tools are most appropriate for a given query.
 */
@Component
public class GPTQueryClassifier {
    
    private static final Logger logger = LoggerFactory.getLogger(GPTQueryClassifier.class);
    
    private final LLMService llmService;
    
    @Autowired
    public GPTQueryClassifier(LLMService llmService) {
        this.llmService = llmService;
    }
    
    /**
     * Classifies a user query to determine the appropriate analysis path.
     * 
     * @param query The user query to classify
     * @return The analysis path with tool recommendations
     */
    public AnalysisPath classifyQuery(String query) {
        String prompt = createClassificationPrompt(query);
        
        // Create LLM request with appropriate parameters for classification
        ModelParameters parameters = new ModelParameters(llmService.getDefaultModelName())
                .withTemperature(0.1) // Low temperature for more deterministic responses
                .withMaxTokens(100);   // Classification is a short response
        
        // Create the request
        LLMRequest request = new LLMRequest(prompt, parameters);
        
        // Add metadata
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("operation", "query_classification");
        request.withMetadata(metadata);
        
        try {
            // Send the request to the LLM
            LLMResponse response = llmService.processRequest(request);
            
            // Parse the response to get the classification
            return parseClassificationResponse(response.getContent(), query);
        } catch (Exception e) {
            logger.error("Error classifying query: {}", e.getMessage(), e);
            // Return a fallback analysis path focused on code search (most basic capability)
            return AnalysisPath.builder()
                    .pathType("STATIC")
                    .confidence(0.5)
                    .addRequiredTool("vector_search")
                    .query(query)
                    .build();
        }
    }
    
    /**
     * Creates a prompt for the LLM to classify the query.
     * 
     * @param query The user query to classify
     * @return The formatted prompt
     */
    private String createClassificationPrompt(String query) {
        return """
            Classify the following support engineer query into ONE of these categories:
            - CODE_SEARCH: Looking for specific code or implementation details
            - CALL_PATH: Analyzing method invocations, tracing execution flows, or understanding how components interact
            - CONFIG_IMPACT: Understanding configuration property effects, settings, or environmental variables
            - ERROR_CHAIN: Investigating exception handling, error flows, or debugging issues
            - CROSS_REPO: Tracing patterns across multiple repositories or finding cross-component usage
            - CODE_STRUCTURE: Understanding class hierarchies, dependencies, inheritance, or architectural relationships
            - GENERAL: General questions not requiring specific tools
            
            For each category, also estimate a confidence score between 0.0 and 1.0.
            
            Query: %s
            
            Response format:
            CATEGORY|confidence_score|required_tools
            
            Example:
            CODE_SEARCH|0.85|vector_search,cross_repo_tracer
            """.formatted(query);
    }
    
    /**
     * Parses the LLM response into a structured analysis path.
     * 
     * @param response The raw response from the LLM
     * @param originalQuery The original user query
     * @return The parsed analysis path
     */
    private AnalysisPath parseClassificationResponse(String response, String originalQuery) {
        try {
            // Basic string parsing - in production, you'd want to make this more robust
            String[] parts = response.trim().split("\\|");
            
            if (parts.length < 2) {
                logger.warn("Invalid classification response format: {}", response);
                return createFallbackPath(originalQuery);
            }
            
            String category = parts[0].trim();
            double confidence = 0.7; // Default confidence
            
            try {
                confidence = Double.parseDouble(parts[1].trim());
            } catch (NumberFormatException e) {
                logger.warn("Invalid confidence score format: {}", parts[1]);
            }
            
            // Start building the analysis path
            AnalysisPath.Builder builder = AnalysisPath.builder()
                    .pathType(mapCategoryToPathType(category))
                    .confidence(confidence)
                    .query(originalQuery);
            
            // Add required tools based on category
            if (parts.length >= 3 && parts[2] != null && !parts[2].isEmpty()) {
                String[] tools = parts[2].split(",");
                for (String tool : tools) {
                    builder.addRequiredTool(tool.trim());
                }
            } else {
                // Default tools based on category
                addDefaultTools(builder, category);
            }
            
            return builder.build();
        } catch (Exception e) {
            logger.error("Error parsing classification response: {}", e.getMessage(), e);
            return createFallbackPath(originalQuery);
        }
    }
    
    /**
     * Maps the classification category to a path type.
     * 
     * @param category The classification category
     * @return The corresponding path type
     */
    private String mapCategoryToPathType(String category) {
        return switch (category.toUpperCase()) {
            case "CODE_SEARCH" -> "STATIC";
            case "GENERAL" -> "STATIC";
            case "CODE_STRUCTURE" -> "STATIC";
            case "CALL_PATH", "CONFIG_IMPACT", "ERROR_CHAIN", "CROSS_REPO" -> "HYBRID";
            default -> "STATIC";
        };
    }
    
    /**
     * Adds default tools to the analysis path based on the category.
     * 
     * @param builder The analysis path builder
     * @param category The classification category
     */
    private void addDefaultTools(AnalysisPath.Builder builder, String category) {
        switch (category.toUpperCase()) {
            case "CODE_SEARCH":
                builder.addRequiredTool("vector_search");
                break;
            case "CALL_PATH":
                builder.addRequiredTool("vector_search")
                       .addRequiredTool("call_path_analyzer");
                break;
            case "CONFIG_IMPACT":
                builder.addRequiredTool("vector_search")
                       .addRequiredTool("config_impact_analyzer");
                break;
            case "ERROR_CHAIN":
                builder.addRequiredTool("vector_search")
                       .addRequiredTool("error_chain_mapper");
                break;
            case "CROSS_REPO":
                builder.addRequiredTool("vector_search")
                       .addRequiredTool("cross_repo_tracer");
                break;
            case "CODE_STRUCTURE":
                builder.addRequiredTool("vector_search")
                       .addFlag("use_knowledge_graph", true);
                break;
            case "GENERAL":
            default:
                builder.addRequiredTool("vector_search");
                break;
        }
    }
    
    /**
     * Creates a fallback analysis path for when classification fails.
     * 
     * @param query The original user query
     * @return A basic static analysis path
     */
    private AnalysisPath createFallbackPath(String query) {
        return AnalysisPath.builder()
                .pathType("STATIC")
                .confidence(0.5)
                .addRequiredTool("vector_search")
                .query(query)
                .build();
    }
} 