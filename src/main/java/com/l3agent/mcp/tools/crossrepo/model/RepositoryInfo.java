package com.l3agent.mcp.tools.crossrepo.model;

import java.util.Objects;

/**
 * Contains information about a code repository.
 * Used by the cross-repository tracer tool to identify and manage repositories.
 */
public class RepositoryInfo {
    
    private String name;
    private String path;
    private String description;
    
    /**
     * Creates a new repository info.
     * 
     * @param name The repository name
     * @param path The absolute path to the repository on the filesystem
     */
    public RepositoryInfo(String name, String path) {
        this.name = name;
        this.path = path;
    }
    
    /**
     * Creates a new repository info.
     * 
     * @param name The repository name
     * @param path The absolute path to the repository on the filesystem
     * @param description A brief description of the repository
     */
    public RepositoryInfo(String name, String path, String description) {
        this.name = name;
        this.path = path;
        this.description = description;
    }
    
    /**
     * Gets the repository name.
     * 
     * @return The repository name
     */
    public String getName() {
        return name;
    }
    
    /**
     * Sets the repository name.
     * 
     * @param name The repository name
     */
    public void setName(String name) {
        this.name = name;
    }
    
    /**
     * Gets the absolute path to the repository on the filesystem.
     * 
     * @return The repository path
     */
    public String getPath() {
        return path;
    }
    
    /**
     * Sets the absolute path to the repository.
     * 
     * @param path The repository path
     */
    public void setPath(String path) {
        this.path = path;
    }
    
    /**
     * Gets the repository description.
     * 
     * @return The repository description
     */
    public String getDescription() {
        return description;
    }
    
    /**
     * Sets the repository description.
     * 
     * @param description The repository description
     */
    public void setDescription(String description) {
        this.description = description;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RepositoryInfo that = (RepositoryInfo) o;
        return Objects.equals(name, that.name) &&
               Objects.equals(path, that.path);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(name, path);
    }
    
    @Override
    public String toString() {
        return name + " (" + path + ")";
    }
} 