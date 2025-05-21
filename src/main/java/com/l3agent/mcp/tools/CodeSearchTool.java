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
 * Tool for searching code in the codebase based on semantic queries.
 */
@Component
public class CodeSearchTool implements MCPToolInterface {
    
    private static final Logger logger = LoggerFactory.getLogger(CodeSearchTool.class);
    
    private static final String TOOL_NAME = "code_search";
    
    @Autowired
    private VectorServiceIntegration vectorServiceIntegration;
    
    @Override
    public String getName() {
        return TOOL_NAME;
    }
    
    @Override
    public String getDescription() {
        return "Searches the codebase for code snippets matching a semantic query";
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
        
        // Max results parameter
        ToolParameter maxResultsParam = new ToolParameter(
            "maxResults",
            "Maximum number of results to return (default: 10)",
            "integer",
            false,
            10
        );
        params.add(maxResultsParam);
        
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
        Object maxResultsObj = parameters.get("maxResults");
        if (maxResultsObj != null) {
            if (maxResultsObj instanceof Integer) {
                maxResults = (Integer) maxResultsObj;
            } else if (maxResultsObj instanceof String) {
                try {
                    maxResults = Integer.parseInt((String) maxResultsObj);
                } catch (NumberFormatException e) {
                    logger.warn("Invalid maxResults parameter: {}", maxResultsObj);
                }
            }
        }
        
        // Execute the search
        logger.info("Executing code search with query: {}, maxResults: {}", query, maxResults);
        return vectorServiceIntegration.searchCode(query, maxResults);
    }
} 