package com.l3agent.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service interface for accessing and searching code repositories.
 */
public interface CodeRepositoryService {
    
    /**
     * Searches for code snippets matching the given query.
     * 
     * @param query The search query
     * @param maxResults The maximum number of results to return
     * @return A list of matching code snippets with their file paths
     */
    List<CodeSnippet> searchCode(String query, int maxResults);
    
    /**
     * Retrieves a file from the code repository.
     * 
     * @param filePath The path of the file to retrieve
     * @return An Optional containing the file content if found, empty otherwise
     */
    Optional<String> getFileContent(String filePath);
    
    /**
     * Represents a code snippet from the repository.
     */
    class CodeSnippet {
        private String filePath;
        private String snippet;
        private int startLine;
        private int endLine;
        private Map<String, String> metadata;
        private float relevance;
        
        // Constructors
        public CodeSnippet() {}
        
        public CodeSnippet(String filePath, String snippet, int startLine, int endLine) {
            this.filePath = filePath;
            this.snippet = snippet;
            this.startLine = startLine;
            this.endLine = endLine;
        }
        
        // Getters and Setters
        public String getFilePath() {
            return filePath;
        }
        
        public void setFilePath(String filePath) {
            this.filePath = filePath;
        }
        
        public String getSnippet() {
            return snippet;
        }
        
        public void setSnippet(String snippet) {
            this.snippet = snippet;
        }
        
        public int getStartLine() {
            return startLine;
        }
        
        public void setStartLine(int startLine) {
            this.startLine = startLine;
        }
        
        public int getEndLine() {
            return endLine;
        }
        
        public void setEndLine(int endLine) {
            this.endLine = endLine;
        }
        
        public Map<String, String> getMetadata() {
            return metadata;
        }
        
        public void setMetadata(Map<String, String> metadata) {
            this.metadata = metadata;
        }
        
        public float getRelevance() {
            return relevance;
        }
        
        public void setRelevance(float relevance) {
            this.relevance = relevance;
        }
    }
} 