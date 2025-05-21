package com.l3agent.mcp.tools;

import com.l3agent.mcp.MCPToolInterface;
import com.l3agent.mcp.model.ToolParameter;
import com.l3agent.mcp.model.ToolResponse;
import com.l3agent.mcp.tools.errorchain.ExceptionAnalyzer;
import com.l3agent.mcp.tools.errorchain.model.ErrorChainResult;
import com.l3agent.mcp.tools.errorchain.model.ExceptionNode;
import com.l3agent.mcp.util.RetryUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP tool for analyzing exception chains and propagation patterns.
 * Maps how exceptions flow through the system to help diagnose root causes.
 */
@Component
public class ErrorChainMapperTool implements MCPToolInterface {
    
    private static final Logger logger = LoggerFactory.getLogger(ErrorChainMapperTool.class);
    
    private static final String TOOL_NAME = "error_chain_mapper";
    
    @Autowired
    private ExceptionAnalyzer exceptionAnalyzer;
    
    @Value("${l3agent.errorchain.timeout-seconds:30}")
    private int timeoutSeconds;
    
    @Override
    public String getName() {
        return TOOL_NAME;
    }
    
    @Override
    public String getDescription() {
        return "Analyzes exception propagation chains, hierarchy, and handling patterns";
    }
    
    @Override
    public List<ToolParameter> getParameters() {
        List<ToolParameter> parameters = new ArrayList<>();
        
        parameters.add(new ToolParameter(
                "exceptionClass",
                "Fully qualified name of the exception to analyze (e.g., java.lang.IllegalArgumentException)",
                "string",
                true,
                null));
        
        parameters.add(new ToolParameter(
                "includeHierarchy",
                "Whether to analyze the exception hierarchy using bytecode analysis",
                "boolean",
                false,
                true));
        
        parameters.add(new ToolParameter(
                "analyzeWrapping",
                "Whether to analyze exception wrapping patterns",
                "boolean",
                false,
                true));
        
        parameters.add(new ToolParameter(
                "identifyLogging",
                "Whether to identify logging patterns for this exception",
                "boolean",
                false,
                true));
        
        parameters.add(new ToolParameter(
                "detectAntiPatterns",
                "Whether to detect error handling anti-patterns",
                "boolean",
                false,
                true));
        
        return parameters;
    }
    
    @Override
    public ToolResponse execute(Map<String, Object> parameters) {
        // Validate required parameters
        String exceptionClass = (String) parameters.get("exceptionClass");
        if (exceptionClass == null || exceptionClass.trim().isEmpty()) {
            logger.warn("Missing required parameter: exceptionClass");
            return new ToolResponse(false, "Missing required parameter: exceptionClass", null);
        }
        
        try {
            logger.info("Executing error chain analysis for exception: {}", exceptionClass);
            
            // Get optional parameters
            boolean includeHierarchy = getBooleanParameter(parameters, "includeHierarchy", true);
            boolean analyzeWrapping = getBooleanParameter(parameters, "analyzeWrapping", true);
            boolean identifyLogging = getBooleanParameter(parameters, "identifyLogging", true);
            boolean detectAntiPatterns = getBooleanParameter(parameters, "detectAntiPatterns", true);
            
            // Execute the analysis with timeout protection
            ErrorChainResult result = RetryUtil.withTimeout(
                    () -> exceptionAnalyzer.analyzeExceptionChain(
                            exceptionClass,
                            includeHierarchy,
                            analyzeWrapping,
                            identifyLogging,
                            detectAntiPatterns),
                    timeoutSeconds);
            
            // Build the response data
            Map<String, Object> resultData = new HashMap<>();
            
            // Add the analysis results
            if (result.getExceptionHierarchy() != null && !result.getExceptionHierarchy().isEmpty()) {
                resultData.put("exceptionHierarchy", convertHierarchyToMap(result.getExceptionHierarchy()));
            }
            
            if (result.getPropagationChains() != null && !result.getPropagationChains().isEmpty()) {
                resultData.put("propagationChains", result.getPropagationChains());
            }
            
            if (result.getWrappingPatterns() != null && !result.getWrappingPatterns().isEmpty()) {
                resultData.put("wrappingPatterns", result.getWrappingPatterns());
            }
            
            if (result.getLoggingPatterns() != null && !result.getLoggingPatterns().isEmpty()) {
                resultData.put("loggingPatterns", result.getLoggingPatterns());
            }
            
            if (result.getDetectedAntiPatterns() != null && !result.getDetectedAntiPatterns().isEmpty()) {
                resultData.put("detectedAntiPatterns", result.getDetectedAntiPatterns());
            }
            
            if (result.getCommonErrorMessages() != null && !result.getCommonErrorMessages().isEmpty()) {
                resultData.put("commonErrorMessages", result.getCommonErrorMessages());
            }
            
            if (result.getThrowLocations() != null && !result.getThrowLocations().isEmpty()) {
                resultData.put("throwLocations", result.getThrowLocations());
            }
            
            if (result.getCatchLocations() != null && !result.getCatchLocations().isEmpty()) {
                resultData.put("catchLocations", result.getCatchLocations());
            }
            
            if (result.getRethrowLocations() != null && !result.getRethrowLocations().isEmpty()) {
                resultData.put("rethrowLocations", result.getRethrowLocations());
            }
            
            if (result.getHandlingStrategies() != null && !result.getHandlingStrategies().isEmpty()) {
                resultData.put("handlingStrategies", result.getHandlingStrategies());
            }
            
            if (result.getAnalysisNotes() != null && !result.getAnalysisNotes().isEmpty()) {
                resultData.put("analysisNotes", result.getAnalysisNotes());
            }
            
            if (result.getRecommendations() != null && !result.getRecommendations().isEmpty()) {
                resultData.put("recommendations", result.getRecommendations());
            }
            
            resultData.put("exceptionClass", exceptionClass);
            
            // Add metadata
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("includeHierarchy", includeHierarchy);
            metadata.put("analyzeWrapping", analyzeWrapping);
            metadata.put("identifyLogging", identifyLogging);
            metadata.put("detectAntiPatterns", detectAntiPatterns);
            resultData.put("metadata", metadata);
            
            logger.info("Completed error chain analysis for exception: {}", exceptionClass);
            return new ToolResponse(true, "Error chain analysis completed successfully", resultData);
        } catch (Exception e) {
            logger.error("Error analyzing exception chain", e);
            ToolResponse response = new ToolResponse(false, 
                    "Error analyzing exception chain: " + e.getMessage(), null);
            response.addError(e.toString());
            return response;
        }
    }
    
    /**
     * Converts the exception hierarchy to a map representation.
     */
    private List<Map<String, Object>> convertHierarchyToMap(List<ExceptionNode> hierarchy) {
        List<Map<String, Object>> result = new ArrayList<>();
        
        for (ExceptionNode node : hierarchy) {
            result.add(convertNodeToMap(node));
        }
        
        return result;
    }
    
    /**
     * Converts an exception node to a map representation.
     */
    private Map<String, Object> convertNodeToMap(ExceptionNode node) {
        Map<String, Object> nodeMap = new HashMap<>();
        nodeMap.put("className", node.getClassName());
        nodeMap.put("isChecked", node.isChecked());
        
        if (node.getCommonMessages() != null && !node.getCommonMessages().isEmpty()) {
            nodeMap.put("commonMessages", node.getCommonMessages());
        }
        
        if (node.getParents() != null && !node.getParents().isEmpty()) {
            List<Map<String, Object>> parents = new ArrayList<>();
            for (ExceptionNode parent : node.getParents()) {
                Map<String, Object> parentMap = new HashMap<>();
                parentMap.put("className", parent.getClassName());
                parents.add(parentMap);
            }
            nodeMap.put("parents", parents);
        }
        
        if (node.getChildren() != null && !node.getChildren().isEmpty()) {
            List<Map<String, Object>> children = new ArrayList<>();
            for (ExceptionNode child : node.getChildren()) {
                Map<String, Object> childMap = new HashMap<>();
                childMap.put("className", child.getClassName());
                children.add(childMap);
            }
            nodeMap.put("children", children);
        }
        
        return nodeMap;
    }
    
    /**
     * Gets a boolean parameter value, using the default if not specified.
     */
    private boolean getBooleanParameter(Map<String, Object> parameters, String name, boolean defaultValue) {
        Object value = parameters.get(name);
        if (value == null) {
            return defaultValue;
        }
        
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        
        if (value instanceof String) {
            return Boolean.parseBoolean((String) value);
        }
        
        return defaultValue;
    }
} 