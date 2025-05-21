package com.l3agent.service;

import java.util.List;
import java.util.Map;

/**
 * Service interface for parsing Java code.
 * Provides utilities for extracting structural information from Java source files.
 */
public interface JavaParserService {
    
    /**
     * Parses a Java file to extract its structure.
     * 
     * @param filePath Path to the Java file
     * @return Map containing parsed file structure
     */
    Map<String, Object> parseJavaFile(String filePath);
    
    /**
     * Extracts method calls from a Java file.
     * 
     * @param filePath Path to the Java file
     * @return List of method call information
     */
    List<Map<String, Object>> extractMethodCalls(String filePath);
    
    /**
     * Extracts import statements from a Java file.
     * 
     * @param filePath Path to the Java file
     * @return List of import statements
     */
    List<String> extractImports(String filePath);
    
    /**
     * Extracts class inheritance relationships from a Java file.
     * 
     * @param filePath Path to the Java file
     * @return Map containing inheritance relationships
     */
    Map<String, Object> extractClassHierarchy(String filePath);
    
    /**
     * Resolves a class or interface name to its source file.
     * 
     * @param className Fully qualified class name
     * @param searchPaths Paths to search for the class
     * @return Path to the source file, or null if not found
     */
    String resolveClassToSourceFile(String className, List<String> searchPaths);
} 