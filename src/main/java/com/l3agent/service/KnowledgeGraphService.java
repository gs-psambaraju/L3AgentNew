package com.l3agent.service;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * Service interface for knowledge graph operations.
 * Provides functionality for building, querying, and traversing the code knowledge graph.
 */
public interface KnowledgeGraphService {
    
    /**
     * Builds or updates the knowledge graph for the specified path.
     * 
     * @param path Path to build the knowledge graph for. If null or empty, builds for all code.
     * @param recursive Whether to process subdirectories recursively.
     * @return Map containing information about the build operation.
     */
    Map<String, Object> buildKnowledgeGraph(String path, boolean recursive);
    
    /**
     * Retrieves entities related to the specified entity ID.
     * 
     * @param entityId The ID of the entity to find relationships for
     * @param depth The maximum traversal depth (1 for direct relationships only)
     * @return A list of related entities with relationship information
     */
    List<CodeRelationship> findRelatedEntities(String entityId, int depth);
    
    /**
     * Finds entities that match the given query.
     * 
     * @param query The search query
     * @param maxResults Maximum number of results to return
     * @return List of matching entities
     */
    List<CodeEntity> searchEntities(String query, int maxResults);
    
    /**
     * Finds entities associated with a specific file path.
     * Useful for inspection and debugging of knowledge graph data.
     * 
     * @param filePath The file path to search for
     * @return List of entities associated with the file path
     */
    List<CodeEntity> findEntitiesByFilePath(String filePath);
    
    /**
     * Checks if the knowledge graph service is available.
     * 
     * @return True if available, false otherwise
     */
    boolean isAvailable();
    
    /**
     * Returns the number of entities in the knowledge graph.
     * 
     * @return The number of entities
     */
    int getEntityCount();
    
    /**
     * Returns the number of relationships in the knowledge graph.
     * 
     * @return The number of relationships
     */
    int getRelationshipCount();
    
    /**
     * Rebuilds the entire knowledge graph.
     */
    void rebuildEntireKnowledgeGraph();
    
    /**
     * Represents a code entity in the knowledge graph.
     */
    class CodeEntity implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private String id;
        private String type; // "class", "method", "interface", etc.
        private String name;
        private String fullyQualifiedName;
        private String filePath;
        private int startLine;
        private int endLine;
        private Map<String, String> properties;
        
        public CodeEntity() {}
        
        public CodeEntity(String id, String type, String name, String fullyQualifiedName, 
                          String filePath, int startLine, int endLine) {
            this.id = id;
            this.type = type;
            this.name = name;
            this.fullyQualifiedName = fullyQualifiedName;
            this.filePath = filePath;
            this.startLine = startLine;
            this.endLine = endLine;
        }
        
        // Getters and setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public String getFullyQualifiedName() { return fullyQualifiedName; }
        public void setFullyQualifiedName(String fullyQualifiedName) { this.fullyQualifiedName = fullyQualifiedName; }
        
        public String getFilePath() { return filePath; }
        public void setFilePath(String filePath) { this.filePath = filePath; }
        
        public int getStartLine() { return startLine; }
        public void setStartLine(int startLine) { this.startLine = startLine; }
        
        public int getEndLine() { return endLine; }
        public void setEndLine(int endLine) { this.endLine = endLine; }
        
        public Map<String, String> getProperties() { return properties; }
        public void setProperties(Map<String, String> properties) { this.properties = properties; }
    }
    
    /**
     * Represents a relationship between code entities.
     */
    class CodeRelationship implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private String sourceId;
        private String targetId;
        private String type; // "calls", "extends", "implements", "uses", etc.
        private Map<String, String> properties;
        
        public CodeRelationship() {}
        
        public CodeRelationship(String sourceId, String targetId, String type) {
            this.sourceId = sourceId;
            this.targetId = targetId;
            this.type = type;
        }
        
        // Getters and setters
        public String getSourceId() { return sourceId; }
        public void setSourceId(String sourceId) { this.sourceId = sourceId; }
        
        public String getTargetId() { return targetId; }
        public void setTargetId(String targetId) { this.targetId = targetId; }
        
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        
        public Map<String, String> getProperties() { return properties; }
        public void setProperties(Map<String, String> properties) { this.properties = properties; }
    }
} 