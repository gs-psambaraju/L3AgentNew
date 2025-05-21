package com.l3agent.mcp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Centralized configuration for paths used across L3Agent tools.
 * This ensures all tools reference the same directories for consistency.
 */
@Configuration
public class L3AgentPathConfig {
    
    /**
     * Base path for code analysis (source code, implementation classes)
     */
    @Value("${l3agent.paths.code-base:src/main/java,src/test/java}")
    private String codeBasePaths;
    
    /**
     * Base path for resources (config files, properties, etc.)
     */
    @Value("${l3agent.paths.resources:src/main/resources}")
    private String resourcePaths;
    
    /**
     * Base package to scan for components
     */
    @Value("${l3agent.paths.base-package:com.l3agent}")
    private String basePackage;
    
    /**
     * Path to external repositories
     */
    @Value("${l3agent.paths.external-repos:./data/code}")
    private String externalRepoPath;
    
    /**
     * Gets the code base paths.
     * 
     * @return The code base paths
     */
    public String getCodeBasePaths() {
        return codeBasePaths;
    }
    
    /**
     * Gets the resource paths.
     * 
     * @return The resource paths
     */
    public String getResourcePaths() {
        return resourcePaths;
    }
    
    /**
     * Gets the base package.
     * 
     * @return The base package
     */
    public String getBasePackage() {
        return basePackage;
    }
    
    /**
     * Gets the external repository path.
     * 
     * @return The external repository path
     */
    public String getExternalRepoPath() {
        return externalRepoPath;
    }
} 