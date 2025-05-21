package com.l3agent.mcp.tools;

import com.l3agent.mcp.MCPToolInterface;
import com.l3agent.mcp.model.ToolParameter;
import com.l3agent.mcp.model.ToolResponse;
import com.l3agent.mcp.util.VectorServiceIntegration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Tool for searching code in the codebase using vector embeddings.
 * Implements the vector_search tool that is expected by the HybridQueryExecutionEngine.
 */
@Component
public class VectorSearchTool implements MCPToolInterface {
    
    private static final Logger logger = LoggerFactory.getLogger(VectorSearchTool.class);
    
    private static final String TOOL_NAME = "vector_search";
    
    @Autowired
    private VectorServiceIntegration vectorServiceIntegration;
    
    @Override
    public String getName() {
        return TOOL_NAME;
    }
    
    @Override
    public String getDescription() {
        return "Searches the codebase using vector embeddings for semantic similarity";
    }
    
    @Override
    public List<ToolParameter> getParameters() {
        List<ToolParameter> params = new ArrayList<>();
        
        // Main query parameter
        ToolParameter queryParam = new ToolParameter(
            "query",
            "The semantic query to search for in the codebase",
            "string",
            true,
            null
        );
        params.add(queryParam);
        
        // Limit parameter - to match the expected parameter name in HybridQueryExecutionEngine
        ToolParameter limitParam = new ToolParameter(
            "limit",
            "Maximum number of results to return (default: 10)",
            "integer",
            false,
            10
        );
        params.add(limitParam);
        
        return params;
    }
    
    @Override
    public ToolResponse execute(Map<String, Object> parameters) {
        // Validate required parameters
        String query = (String) parameters.get("query");
        if (query == null || query.trim().isEmpty()) {
            logger.warn("Missing required parameter: query");
            return new ToolResponse(false, "Missing required parameter: query", null);
        }
        
        // Get optional parameters with defaults
        int maxResults = 10;
        Object limitObj = parameters.get("limit");
        if (limitObj != null) {
            if (limitObj instanceof Integer) {
                maxResults = (Integer) limitObj;
            } else if (limitObj instanceof String) {
                try {
                    maxResults = Integer.parseInt((String) limitObj);
                } catch (NumberFormatException e) {
                    logger.warn("Invalid limit parameter: {}", limitObj);
                }
            }
        }
        
        // Execute the search using the same underlying service as CodeSearchTool
        logger.info("Executing vector search with query: {}, maxResults: {}", query, maxResults);
        return vectorServiceIntegration.searchCode(query, maxResults);
    }
} 