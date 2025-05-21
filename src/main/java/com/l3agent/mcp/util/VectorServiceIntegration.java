package com.l3agent.mcp.util;

import com.l3agent.mcp.model.MCPRequest;
import com.l3agent.mcp.model.ToolResponse;
import com.l3agent.service.CodeRepositoryService;
import com.l3agent.service.CodeRepositoryService.CodeSnippet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Utility class for integrating the VectorBasedCodeRepositoryService with MCP.
 */
@Component
public class VectorServiceIntegration {
    
    private static final Logger logger = LoggerFactory.getLogger(VectorServiceIntegration.class);
    
    @Autowired
    private CodeRepositoryService codeRepositoryService;
    
    /**
     * Enhances an MCP request with context data from the vector-based code repository.
     * 
     * @param request The original MCP request
     * @return The enhanced request with additional context data
     */
    public MCPRequest enhanceRequestWithCodeContext(MCPRequest request) {
        if (request == null || request.getQuery() == null || request.getQuery().trim().isEmpty()) {
            logger.warn("Cannot enhance null or empty request");
            return request;
        }
        
        try {
            logger.info("Enhancing MCP request with code context for query: {}", request.getQuery());
            
            // Search for relevant code snippets
            List<CodeSnippet> snippets = codeRepositoryService.searchCode(request.getQuery(), 5);
            
            if (snippets == null || snippets.isEmpty()) {
                logger.info("No relevant code snippets found for query: {}", request.getQuery());
                return request;
            }
            
            // Add the snippets to the request's context data
            request.addContextData("relevantCodeSnippets", snippets);
            
            // Add snippet sources as separate context data for easier access
            List<String> snippetSources = snippets.stream()
                .map(CodeSnippet::getFilePath)
                .collect(Collectors.toList());
            
            request.addContextData("relevantCodeFiles", snippetSources);
            
            logger.info("Enhanced request with {} code snippets", snippets.size());
            
            return request;
        } catch (Exception e) {
            logger.error("Error enhancing request with code context", e);
            return request;
        }
    }
    
    /**
     * Retrieves code content for a given file path.
     * 
     * @param filePath The path of the file to retrieve
     * @return A ToolResponse containing the file content if found
     */
    public ToolResponse getFileContent(String filePath) {
        if (filePath == null || filePath.trim().isEmpty()) {
            return new ToolResponse(false, "Invalid file path", null);
        }
        
        try {
            Optional<String> contentOpt = codeRepositoryService.getFileContent(filePath);
            
            if (contentOpt.isPresent()) {
                Map<String, Object> resultData = new HashMap<>();
                resultData.put("filePath", filePath);
                resultData.put("content", contentOpt.get());
                
                return new ToolResponse(true, "File content retrieved successfully", resultData);
            } else {
                return new ToolResponse(false, "File not found: " + filePath, null);
            }
        } catch (Exception e) {
            logger.error("Error retrieving file content for: {}", filePath, e);
            ToolResponse response = new ToolResponse(false, "Error retrieving file content: " + e.getMessage(), null);
            response.addError(e.toString());
            return response;
        }
    }
    
    /**
     * Searches for code snippets matching the given query.
     * 
     * @param query The search query
     * @param maxResults The maximum number of results to return
     * @return A ToolResponse containing the search results
     */
    public ToolResponse searchCode(String query, int maxResults) {
        if (query == null || query.trim().isEmpty()) {
            return new ToolResponse(false, "Invalid query", null);
        }
        
        if (maxResults <= 0) {
            maxResults = 10; // Default to 10 results
        }
        
        try {
            List<CodeSnippet> snippets = codeRepositoryService.searchCode(query, maxResults);
            
            if (snippets != null && !snippets.isEmpty()) {
                Map<String, Object> resultData = new HashMap<>();
                resultData.put("snippets", snippets);
                resultData.put("count", snippets.size());
                
                return new ToolResponse(true, "Found " + snippets.size() + " code snippets", resultData);
            } else {
                return new ToolResponse(true, "No code snippets found for query: " + query, 
                        Collections.singletonMap("snippets", Collections.emptyList()));
            }
        } catch (Exception e) {
            logger.error("Error searching code with query: {}", query, e);
            ToolResponse response = new ToolResponse(false, "Error searching code: " + e.getMessage(), null);
            response.addError(e.toString());
            return response;
        }
    }
} 