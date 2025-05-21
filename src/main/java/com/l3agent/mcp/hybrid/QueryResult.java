package com.l3agent.mcp.hybrid;

import com.l3agent.service.KnowledgeGraphService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents the result of a hybrid query execution.
 * Includes the results from various tools and metadata about the execution.
 */
public class QueryResult {
    
    private String query;
    private boolean success = true;
    private boolean fallbackUsed = false;
    private String errorMessage;
    private Map<String, Map<String, Object>> toolResponses = new HashMap<>();
    private Map<String, String> toolErrors = new HashMap<>();
    private List<String> requestedTools = new ArrayList<>();
    private List<KnowledgeGraphService.CodeEntity> knowledgeGraphEntities = new ArrayList<>();
    private List<Map<String, Object>> knowledgeGraphRelationships = new ArrayList<>();
    
    /**
     * Gets the original query.
     * 
     * @return The query
     */
    public String getQuery() {
        return query;
    }
    
    /**
     * Sets the original query.
     * 
     * @param query The query
     */
    public void setQuery(String query) {
        this.query = query;
    }
    
    /**
     * Checks if the execution was successful.
     * 
     * @return True if successful, false otherwise
     */
    public boolean isSuccess() {
        return success;
    }
    
    /**
     * Sets the success status.
     * 
     * @param success The success status
     */
    public void setSuccess(boolean success) {
        this.success = success;
    }
    
    /**
     * Checks if a fallback was used.
     * 
     * @return True if a fallback was used, false otherwise
     */
    public boolean isFallbackUsed() {
        return fallbackUsed;
    }
    
    /**
     * Sets the fallback used status.
     * 
     * @param fallbackUsed The fallback used status
     */
    public void setFallbackUsed(boolean fallbackUsed) {
        this.fallbackUsed = fallbackUsed;
    }
    
    /**
     * Gets the error message.
     * 
     * @return The error message
     */
    public String getErrorMessage() {
        return errorMessage;
    }
    
    /**
     * Sets the error message.
     * 
     * @param errorMessage The error message
     */
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
    
    /**
     * Gets the tool responses.
     * 
     * @return The tool responses
     */
    public Map<String, Map<String, Object>> getToolResponses() {
        return toolResponses;
    }
    
    /**
     * Gets the tool errors.
     * 
     * @return The tool errors
     */
    public Map<String, String> getToolErrors() {
        return toolErrors;
    }
    
    /**
     * Adds a tool response.
     * 
     * @param toolName The name of the tool
     * @param response The response
     */
    public void addToolResponse(String toolName, Map<String, Object> response) {
        this.toolResponses.put(toolName, response);
    }
    
    /**
     * Adds a tool error.
     * 
     * @param toolName The name of the tool
     * @param error The error message
     */
    public void addToolError(String toolName, String error) {
        this.toolErrors.put(toolName, error);
    }
    
    /**
     * Gets the requested tools.
     * 
     * @return The requested tools
     */
    public List<String> getRequestedTools() {
        return requestedTools;
    }
    
    /**
     * Sets the requested tools.
     * 
     * @param requestedTools The requested tools
     */
    public void setRequestedTools(List<String> requestedTools) {
        this.requestedTools = requestedTools;
    }
    
    /**
     * Gets the knowledge graph entities found for this query.
     * 
     * @return The knowledge graph entities
     */
    public List<KnowledgeGraphService.CodeEntity> getKnowledgeGraphEntities() {
        return knowledgeGraphEntities;
    }
    
    /**
     * Sets the knowledge graph entities.
     * 
     * @param knowledgeGraphEntities The knowledge graph entities
     */
    public void setKnowledgeGraphEntities(List<KnowledgeGraphService.CodeEntity> knowledgeGraphEntities) {
        this.knowledgeGraphEntities = knowledgeGraphEntities;
    }
    
    /**
     * Adds a knowledge graph entity.
     * 
     * @param entity The entity to add
     */
    public void addKnowledgeGraphEntity(KnowledgeGraphService.CodeEntity entity) {
        this.knowledgeGraphEntities.add(entity);
    }
    
    /**
     * Gets the knowledge graph relationships for this query.
     * 
     * @return The knowledge graph relationships
     */
    public List<Map<String, Object>> getKnowledgeGraphRelationships() {
        return knowledgeGraphRelationships;
    }
    
    /**
     * Sets the knowledge graph relationships.
     * 
     * @param knowledgeGraphRelationships The knowledge graph relationships
     */
    public void setKnowledgeGraphRelationships(List<Map<String, Object>> knowledgeGraphRelationships) {
        this.knowledgeGraphRelationships = knowledgeGraphRelationships;
    }
    
    /**
     * Adds a knowledge graph relationship.
     * 
     * @param relationship The relationship to add
     */
    public void addKnowledgeGraphRelationship(Map<String, Object> relationship) {
        this.knowledgeGraphRelationships.add(relationship);
    }
} 