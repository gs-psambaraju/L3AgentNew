package com.l3agent.mcp.tools.crossrepo.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Represents a reference to code in a repository.
 * Contains information about where a match was found including file path,
 * line number, matched content, and surrounding context lines.
 */
public class CodeReference {
    
    private String repository;
    private String filePath;
    private int lineNumber;
    private String matchedLine;
    private List<String> context;
    
    /**
     * Creates a new code reference.
     * 
     * @param repository The repository name where the match was found
     * @param filePath The file path relative to the repository root
     * @param lineNumber The 1-based line number where the match was found
     * @param matchedLine The line content that matched
     * @param context Context lines surrounding the match
     */
    public CodeReference(String repository, String filePath, int lineNumber, String matchedLine, List<String> context) {
        this.repository = repository;
        this.filePath = filePath;
        this.lineNumber = lineNumber;
        this.matchedLine = matchedLine;
        this.context = context != null ? context : new ArrayList<>();
    }
    
    /**
     * Gets the repository name where this reference was found.
     * 
     * @return The repository name
     */
    public String getRepository() {
        return repository;
    }
    
    /**
     * Sets the repository name.
     * 
     * @param repository The repository name
     */
    public void setRepository(String repository) {
        this.repository = repository;
    }
    
    /**
     * Gets the file path relative to the repository root.
     * 
     * @return The file path
     */
    public String getFilePath() {
        return filePath;
    }
    
    /**
     * Sets the file path.
     * 
     * @param filePath The file path
     */
    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }
    
    /**
     * Gets the 1-based line number where the match was found.
     * 
     * @return The line number
     */
    public int getLineNumber() {
        return lineNumber;
    }
    
    /**
     * Sets the line number.
     * 
     * @param lineNumber The line number
     */
    public void setLineNumber(int lineNumber) {
        this.lineNumber = lineNumber;
    }
    
    /**
     * Gets the matched line content.
     * 
     * @return The matched line
     */
    public String getMatchedLine() {
        return matchedLine;
    }
    
    /**
     * Sets the matched line content.
     * 
     * @param matchedLine The matched line
     */
    public void setMatchedLine(String matchedLine) {
        this.matchedLine = matchedLine;
    }
    
    /**
     * Gets the context lines surrounding the match.
     * 
     * @return The context lines
     */
    public List<String> getContext() {
        return context;
    }
    
    /**
     * Sets the context lines.
     * 
     * @param context The context lines
     */
    public void setContext(List<String> context) {
        this.context = context != null ? context : new ArrayList<>();
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CodeReference that = (CodeReference) o;
        return lineNumber == that.lineNumber &&
               Objects.equals(repository, that.repository) &&
               Objects.equals(filePath, that.filePath);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(repository, filePath, lineNumber);
    }
    
    @Override
    public String toString() {
        return repository + ":" + filePath + ":" + lineNumber;
    }
} 