package com.l3agent.mcp.tools;

import com.l3agent.mcp.MCPToolInterface;
import com.l3agent.mcp.model.ToolParameter;
import com.l3agent.mcp.model.ToolResponse;
import com.l3agent.mcp.tools.callpath.BytecodeAnalyzer;
import com.l3agent.mcp.tools.callpath.model.CallGraph;

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
 * MCP tool for analyzing method call paths.
 * Constructs a call graph showing method invocations.
 */
@Component
public class CallPathAnalyzerTool implements MCPToolInterface {
    
    private static final Logger logger = LoggerFactory.getLogger(CallPathAnalyzerTool.class);
    
    private static final String TOOL_NAME = "call_path_analyzer";
    
    @Autowired
    private BytecodeAnalyzer bytecodeAnalyzer;
    
    @Value("${l3agent.callpath.timeout-seconds:10}")
    private int timeoutSeconds;
    
    @Override
    public String getName() {
        return TOOL_NAME;
    }
    
    @Override
    public String getDescription() {
        return "Analyzes Java method call paths to construct a call graph";
    }
    
    @Override
    public List<ToolParameter> getParameters() {
        List<ToolParameter> params = new ArrayList<>();
        
        // Required parameter: method path to analyze
        ToolParameter methodPathParam = new ToolParameter(
            "methodPath",
            "Fully qualified method path (e.g., com.example.Service.method)",
            "string",
            true,
            null
        );
        params.add(methodPathParam);
        
        // Optional parameter: maximum depth 
        ToolParameter maxDepthParam = new ToolParameter(
            "maxDepth",
            "Maximum levels of method calls to analyze in the call hierarchy (default: 10)",
            "integer",
            false,
            null
        );
        params.add(maxDepthParam);
        
        // Optional parameter: include libraries
        ToolParameter includeLibrariesParam = new ToolParameter(
            "includeLibraries",
            "Whether to include third-party library method calls in the analysis (default: false)",
            "boolean",
            false,
            false
        );
        params.add(includeLibrariesParam);
        
        // Optional parameter: output format
        ToolParameter outputFormatParam = new ToolParameter(
            "outputFormat",
            "Format of the output: 'json' for JSON Graph Format or 'hierarchy' for tree structure (default: json)",
            "string",
            false,
            "json"
        );
        params.add(outputFormatParam);
        
        return params;
    }
    
    @Override
    public ToolResponse execute(Map<String, Object> parameters) {
        // Validate required parameters
        String methodPath = (String) parameters.get("methodPath");
        if (methodPath == null || methodPath.trim().isEmpty()) {
            logger.warn("Missing required parameter: methodPath");
            return new ToolResponse(false, "Missing required parameter: methodPath", null);
        }
        
        try {
            // Get optional parameters
            Integer maxDepth = null;
            Object maxDepthObj = parameters.get("maxDepth");
            if (maxDepthObj != null) {
                if (maxDepthObj instanceof Integer) {
                    maxDepth = (Integer) maxDepthObj;
                } else if (maxDepthObj instanceof String) {
                    try {
                        maxDepth = Integer.parseInt((String) maxDepthObj);
                    } catch (NumberFormatException e) {
                        logger.warn("Invalid maxDepth parameter: {}", maxDepthObj);
                    }
                }
            }
            
            // Output format (json or hierarchy)
            String outputFormat = "json";
            Object outputFormatObj = parameters.get("outputFormat");
            if (outputFormatObj != null && outputFormatObj instanceof String) {
                String format = ((String) outputFormatObj).toLowerCase();
                if ("hierarchy".equals(format) || "json".equals(format)) {
                    outputFormat = format;
                }
            }
            
            // Execute the analysis
            logger.info("Analyzing call path for method: {}, maxDepth: {}", methodPath, maxDepth);
            long startTime = System.currentTimeMillis();
            
            CallGraph callGraph = bytecodeAnalyzer.analyzeMethod(methodPath, maxDepth);
            
            long duration = System.currentTimeMillis() - startTime;
            logger.info("Call path analysis completed in {} ms", duration);
            
            // Build the response
            Map<String, Object> resultData = new HashMap<>();
            
            // Add the call graph data in the requested format
            if ("hierarchy".equals(outputFormat)) {
                resultData.put("callHierarchy", callGraph.toHierarchy(maxDepth != null ? maxDepth : 10));
            } else {
                resultData.put("callGraph", callGraph.toJsonGraph());
            }
            
            // Add analysis metrics
            resultData.put("nodeCount", callGraph.getNodeCount());
            resultData.put("edgeCount", callGraph.getEdgeCount());
            resultData.put("analysisDurationMs", duration);
            resultData.put("rootMethod", callGraph.getRootNode().getFullyQualifiedName());
            
            return new ToolResponse(true, "Call path analysis completed successfully", resultData);
        } catch (BytecodeAnalyzer.AnalysisException e) {
            logger.error("Error analyzing call path", e);
            ToolResponse response = new ToolResponse(false, "Error analyzing call path: " + e.getMessage(), null);
            response.addError(e.toString());
            return response;
        } catch (Exception e) {
            logger.error("Unexpected error in call path analysis", e);
            ToolResponse response = new ToolResponse(false, "Unexpected error in call path analysis: " + e.getMessage(), null);
            response.addError(e.toString());
            return response;
        }
    }
} 