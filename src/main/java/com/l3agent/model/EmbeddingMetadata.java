package com.l3agent.model;

import com.l3agent.util.LogExtractor;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents metadata for a code embedding.
 * Enhanced with description, purpose summary, capabilities, and log information
 * to improve semantic search capabilities.
 */
public class EmbeddingMetadata {
    private String id;
    private String source;
    private String type;
    private String filePath;
    private int startLine;
    private int endLine;
    private String content;
    private String language;
    private String repositoryNamespace;
    private String repositoryPath;
    
    // Enhanced description fields for semantic search
    private String description;        // Natural language description of the code
    private String purposeSummary;     // One-line purpose statement
    private List<String> capabilities = new ArrayList<>(); // Key capabilities
    private List<String> usageExamples = new ArrayList<>(); // Example usage scenarios
    
    // Log information extracted from the code
    private List<LogExtractor.LogMetadata> logs = new ArrayList<>();
    
    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    
    public int getStartLine() { return startLine; }
    public void setStartLine(int startLine) { this.startLine = startLine; }
    
    public int getEndLine() { return endLine; }
    public void setEndLine(int endLine) { this.endLine = endLine; }
    
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    
    public String getRepositoryNamespace() { return repositoryNamespace; }
    public void setRepositoryNamespace(String repositoryNamespace) { this.repositoryNamespace = repositoryNamespace; }
    
    public String getRepositoryPath() { return repositoryPath; }
    public void setRepositoryPath(String repositoryPath) { this.repositoryPath = repositoryPath; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public String getPurposeSummary() { return purposeSummary; }
    public void setPurposeSummary(String purposeSummary) { this.purposeSummary = purposeSummary; }
    
    public List<String> getCapabilities() { return capabilities; }
    public void setCapabilities(List<String> capabilities) { this.capabilities = capabilities; }
    
    public List<String> getUsageExamples() { return usageExamples; }
    public void setUsageExamples(List<String> usageExamples) { this.usageExamples = usageExamples; }
    
    public List<LogExtractor.LogMetadata> getLogs() { return logs; }
    public void setLogs(List<LogExtractor.LogMetadata> logs) { this.logs = logs; }
    
    /**
     * Creates a combined text of code and description for enhanced embedding.
     * This improves semantic matching by incorporating the natural language description
     * with the actual code.
     *
     * @return Combined text for embedding generation
     */
    public String getCombinedTextForEmbedding() {
        StringBuilder combinedText = new StringBuilder();
        
        // Add description if available
        if (description != null && !description.isEmpty()) {
            combinedText.append("Description: ").append(description).append("\n\n");
        }
        
        // Add purpose summary if available
        if (purposeSummary != null && !purposeSummary.isEmpty()) {
            combinedText.append("Purpose: ").append(purposeSummary).append("\n\n");
        }
        
        // Add capabilities if available
        if (capabilities != null && !capabilities.isEmpty()) {
            combinedText.append("Capabilities:\n");
            for (String capability : capabilities) {
                combinedText.append("- ").append(capability).append("\n");
            }
            combinedText.append("\n");
        }
        
        // Add the code content
        combinedText.append("Code:\n").append(content);
        
        return combinedText.toString();
    }
} 