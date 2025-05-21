package com.l3agent.service.impl;

import com.l3agent.service.KnowledgeGraphService;
import com.l3agent.service.CodeChunkingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Implementation of KnowledgeGraphService that stores the graph in memory.
 * Provides basic graph operations and persistence to disk.
 */
@Service
public class InMemoryKnowledgeGraphService implements KnowledgeGraphService {
    
    private static final Logger logger = LoggerFactory.getLogger(InMemoryKnowledgeGraphService.class);
    private static final String GRAPH_FILE_NAME = "knowledge_graph.bin";
    
    private final Map<String, CodeEntity> entities = new ConcurrentHashMap<>();
    private final Map<String, List<CodeRelationship>> relationships = new ConcurrentHashMap<>();
    
    @Value("${l3agent.knowledgegraph.data-dir:./data/knowledge-graph}")
    private String dataDir;
    
    @Autowired
    private CodeChunkingService codeChunkingService;
    
    /**
     * Initializes the knowledge graph service.
     * Loads the graph if it exists, or creates a new one.
     */
    @PostConstruct
    public void init() {
        try {
            createDirectoryIfNotExists();
            
            // Check if graph file exists, load it if it does
            File graphFile = new File(dataDir, GRAPH_FILE_NAME);
            
            if (graphFile.exists()) {
                loadGraph(graphFile);
                logger.info("Loaded existing knowledge graph with {} entities and {} relationships", 
                        entities.size(), countRelationships());
            } else {
                logger.info("No existing knowledge graph found. Starting with empty graph.");
            }
        } catch (Exception e) {
            logger.error("Error initializing knowledge graph", e);
        }
    }
    
    @Override
    public Map<String, Object> buildKnowledgeGraph(String path, boolean recursive) {
        logger.info("Building knowledge graph for path: {}, recursive: {}", path, recursive);
        
        Map<String, Object> result = new HashMap<>();
        long startTime = System.currentTimeMillis();
        
        try {
            // Get code to process based on path and recursive flag
            List<File> filesToProcess = new ArrayList<>();
            collectCodeFiles(path, filesToProcess, recursive);
            
            int totalFiles = filesToProcess.size();
            logger.info("Found {} files to process", totalFiles);
            
            // Group files by extension for reporting
            Map<String, Long> filesByExtension = filesToProcess.stream()
                    .collect(Collectors.groupingBy(
                            file -> getFileExtension(file.getName()),
                            Collectors.counting()));
            
            // Log the breakdown of file types
            filesByExtension.forEach((ext, count) -> 
                    logger.info("File type breakdown: {} files with extension '{}'", count, ext));
            
            int processedFiles = 0;
            int entitiesCreated = 0;
            int relationshipsCreated = 0;
            
            // Process each file and extract entities and relationships
            for (int fileIndex = 0; fileIndex < totalFiles; fileIndex++) {
                File file = filesToProcess.get(fileIndex);
                try {
                    String filePath = file.getAbsolutePath();
                    String fileContent = new String(Files.readAllBytes(file.toPath()));
                    String extension = getFileExtension(filePath);
                    
                    // Log the current file being processed
                    logger.info("Processing file {}/{}: {}", fileIndex + 1, totalFiles, filePath);
                    
                    // For now, only process Java files with a simple approach
                    // In a real implementation, this would use proper parsers for each language
                    if ("java".equals(extension)) {
                        Map<String, Object> fileResults = processJavaFile(filePath, fileContent);
                        int newEntities = (int) fileResults.get("entities_created");
                        int newRelationships = (int) fileResults.get("relationships_created");
                        
                        entitiesCreated += newEntities;
                        relationshipsCreated += newRelationships;
                        
                        // Log detailed results for this file
                        logger.info("File {} created {} entities and {} relationships", 
                                file.getName(), newEntities, newRelationships);
                    } else {
                        logger.debug("Skipping non-Java file: {}", filePath);
                    }
                    
                    processedFiles++;
                    
                    // Calculate and log progress after each file
                    double progressPercent = (double) processedFiles / totalFiles * 100.0;
                    
                    if (processedFiles % 10 == 0 || processedFiles == totalFiles) {
                        logger.info("Knowledge graph progress: {}/{} files ({}%), {} entities, {} relationships created",
                                processedFiles, totalFiles, String.format("%.2f", progressPercent),
                                entitiesCreated, relationshipsCreated);
                    }
                } catch (Exception e) {
                    logger.error("Error processing file: {}", file.getAbsolutePath(), e);
                }
            }
            
            // Save the graph to disk
            saveGraph();
            
            long duration = System.currentTimeMillis() - startTime;
            
            // Prepare result
            result.put("status", "success");
            result.put("files_processed", processedFiles);
            result.put("entities_created", entitiesCreated);
            result.put("relationships_created", relationshipsCreated);
            result.put("processing_time_ms", duration);
            
            logger.info("Knowledge graph building completed. Processed {} files, created {} entities, {} relationships, in {} ms",
                    processedFiles, entitiesCreated, relationshipsCreated, duration);
            
            // Run a final sanity check
            logger.info("Final knowledge graph state: {} entities and {} relationships", 
                    entities.size(), countRelationships());
            
        } catch (Exception e) {
            logger.error("Error building knowledge graph", e);
            result.put("status", "error");
            result.put("message", "Error building knowledge graph: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * Helper method to analyze a specific Java file in depth to debug entity detection issues.
     * This method provides detailed logging about what it finds in the file.
     * 
     * @param filePath The path to the Java file to analyze
     * @return A summary of what was found
     */
    public Map<String, Object> analyzeJavaFile(String filePath) {
        logger.info("Performing detailed analysis of Java file: {}", filePath);
        Map<String, Object> results = new HashMap<>();
        
        try {
            File file = new File(filePath);
            if (!file.exists() || !file.isFile()) {
                logger.error("File does not exist or is not a file: {}", filePath);
                results.put("error", "File not found or is not a file");
                return results;
            }
            
            String content = new String(Files.readAllBytes(file.toPath()));
            logger.info("File size: {} bytes, {} lines", content.length(), content.split("\\n").length);
            
            // First, let's check the package declaration
            String packageName = null;
            boolean hasPackageDeclaration = false;
            
            String[] lines = content.split("\\n");
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.startsWith("package ") && trimmed.endsWith(";")) {
                    hasPackageDeclaration = true;
                    packageName = trimmed.substring(8, trimmed.length() - 1).trim();
                    logger.info("Found package declaration: {}", packageName);
                    break;
                }
            }
            
            if (!hasPackageDeclaration) {
                logger.warn("No package declaration found in file");
                results.put("package_declaration", false);
            } else {
                results.put("package_declaration", true);
                results.put("package_name", packageName);
            }
            
            // Next, let's look for class definitions
            int publicClassCount = 0;
            int classCount = 0;
            int interfaceCount = 0;
            
            for (String line : lines) {
                String trimmed = line.trim();
                
                // Look for class declarations
                if (trimmed.contains("class ")) {
                    if (trimmed.contains("public class ")) {
                        publicClassCount++;
                        logger.info("Found public class declaration: {}", trimmed);
                    } else {
                        classCount++;
                        logger.info("Found non-public class declaration: {}", trimmed);
                    }
                }
                
                // Look for interface declarations
                if (trimmed.contains("interface ")) {
                    interfaceCount++;
                    logger.info("Found interface declaration: {}", trimmed);
                }
            }
            
            results.put("public_classes", publicClassCount);
            results.put("other_classes", classCount);
            results.put("interfaces", interfaceCount);
            
            logger.info("Summary - Public classes: {}, Other classes: {}, Interfaces: {}", 
                    publicClassCount, classCount, interfaceCount);
            
            // Now let's try to extract entities using our normal approach
            Map<String, Object> processResults = processJavaFile(filePath, content);
            int entitiesCreated = (int) processResults.get("entities_created");
            int relationshipsCreated = (int) processResults.get("relationships_created");
            
            results.put("entities_created", entitiesCreated);
            results.put("relationships_created", relationshipsCreated);
            
            logger.info("Entity extraction results - Entities: {}, Relationships: {}", 
                    entitiesCreated, relationshipsCreated);
            
            if (entitiesCreated == 0) {
                logger.warn("No entities were created from this file. This indicates a parsing problem.");
                
                // Do more detailed analysis of why entities might not be created
                boolean hasPublicClassKeyword = content.contains("public class ");
                boolean hasNonPublicClassKeyword = content.contains(" class ") && !content.equals(content.replace("public class ", ""));
                
                results.put("has_public_class_keyword", hasPublicClassKeyword);
                results.put("has_non_public_class_keyword", hasNonPublicClassKeyword);
                
                logger.info("Keyword analysis - Has 'public class': {}, Has non-public 'class': {}", 
                        hasPublicClassKeyword, hasNonPublicClassKeyword);
            }
            
            return results;
        } catch (Exception e) {
            logger.error("Error analyzing Java file: {}", filePath, e);
            results.put("error", "Exception: " + e.getMessage());
            return results;
        }
    }
    
    @Override
    public List<CodeRelationship> findRelatedEntities(String entityId, int depth) {
        if (!entities.containsKey(entityId)) {
            return new ArrayList<>();
        }
        
        List<CodeRelationship> result = new ArrayList<>();
        
        // Add direct relationships where entity is the source
        if (relationships.containsKey(entityId)) {
            result.addAll(relationships.get(entityId));
        }
        
        // Add direct relationships where entity is the target
        for (String sourceId : relationships.keySet()) {
            List<CodeRelationship> sourceRels = relationships.get(sourceId);
            if (sourceRels != null) {
                for (CodeRelationship rel : sourceRels) {
                    if (entityId.equals(rel.getTargetId())) {
                        // Create a reverse relationship to represent the connection
                        CodeRelationship reverseRel = new CodeRelationship(
                                rel.getTargetId(), rel.getSourceId(), rel.getType());
                        result.add(reverseRel);
                    }
                }
            }
        }
        
        // For higher depths, do a breadth-first traversal
        if (depth > 1) {
            List<String> frontier = result.stream()
                    .map(CodeRelationship::getTargetId)
                    .collect(Collectors.toList());
            
            for (int i = 1; i < depth; i++) {
                List<String> nextFrontier = new ArrayList<>();
                
                for (String id : frontier) {
                    if (relationships.containsKey(id)) {
                        List<CodeRelationship> nextRels = relationships.get(id);
                        result.addAll(nextRels);
                        nextFrontier.addAll(nextRels.stream()
                                .map(CodeRelationship::getTargetId)
                                .collect(Collectors.toList()));
                    }
                }
                
                frontier = nextFrontier;
            }
        }
        
        return result;
    }
    
    @Override
    public List<CodeEntity> searchEntities(String query, int maxResults) {
        String lowerQuery = query.toLowerCase();
        
        return entities.values().stream()
                .filter(entity -> {
                    // Prioritize exact name matches first
                    if (entity.getName().equalsIgnoreCase(query)) {
                        return true;
                    }
                    
                    // Then check for contained matches
                    return entity.getName().toLowerCase().contains(lowerQuery) ||
                           (entity.getFullyQualifiedName() != null && 
                            entity.getFullyQualifiedName().toLowerCase().contains(lowerQuery));
                })
                .sorted((a, b) -> {
                    // Sort by: exact match first, then type (class before method), then alphabetically
                    if (a.getName().equalsIgnoreCase(query) && !b.getName().equalsIgnoreCase(query)) {
                        return -1;
                    }
                    if (!a.getName().equalsIgnoreCase(query) && b.getName().equalsIgnoreCase(query)) {
                        return 1;
                    }
                    
                    // Classes before methods
                    if ("class".equals(a.getType()) && !"class".equals(b.getType())) {
                        return -1;
                    }
                    if (!"class".equals(a.getType()) && "class".equals(b.getType())) {
                        return 1;
                    }
                    
                    // Finally sort by name
                    return a.getName().compareTo(b.getName());
                })
                .limit(maxResults)
                .collect(Collectors.toList());
    }
    
    /**
     * Finds entities associated with a specific file path.
     * Useful for inspection and debugging of knowledge graph data.
     * 
     * @param filePath The file path to search for
     * @return List of entities associated with the file path
     */
    @Override
    public List<CodeEntity> findEntitiesByFilePath(String filePath) {
        List<CodeEntity> results = new ArrayList<>();
        
        if (filePath == null || filePath.isEmpty()) {
            logger.warn("Cannot search for null or empty file path");
            return results;
        }
        
        logger.info("Searching for entities by file path: {}", filePath);
        
        // Normalize the file path for comparison
        String normalizedSearchPath = filePath.replace('\\', '/');
        
        // Search all entities for matching file paths
        for (CodeEntity entity : entities.values()) {
            if (entity.getFilePath() != null) {
                String normalizedEntityPath = entity.getFilePath().replace('\\', '/');
                
                // Check if the entity's file path contains the search path
                if (normalizedEntityPath.contains(normalizedSearchPath)) {
                    results.add(entity);
                }
            }
        }
        
        logger.info("Found {} entities for file path: {}", results.size(), filePath);
        return results;
    }
    
    @Override
    public boolean isAvailable() {
        return true; // In-memory implementation is always available
    }
    
    @Override
    public int getEntityCount() {
        return entities.size();
    }
    
    @Override
    public int getRelationshipCount() {
        return countRelationships();
    }
    
    private int countRelationships() {
        return relationships.values().stream()
                .mapToInt(List::size)
                .sum();
    }
    
    /**
     * Process a Java file to extract entities and relationships.
     * This is a simplified implementation that doesn't use a proper parser.
     * In a production implementation, this would use a full-featured Java parser.
     */
    private Map<String, Object> processJavaFile(String filePath, String content) {
        Map<String, Object> result = new HashMap<>();
        int entitiesCreated = 0;
        int relationshipsCreated = 0;
        
        logger.debug("Processing Java file: {}", filePath);
        
        try {
            // Extract package name
            String packageName = "";
            String[] lines = content.split("\\n");
            logger.debug("File has {} lines", lines.length);
            
            for (String line : lines) {
                line = line.trim();
                if (line.startsWith("package ")) {
                    packageName = line.substring(8, line.indexOf(';')).trim();
                    logger.debug("Found package name: {}", packageName);
                    break;
                }
            }
            
            // Extract class/interface names and create entities
            Map<String, CodeEntity> fileEntities = new HashMap<>();
            int lineNumber = 0;
            
            logger.debug("Starting to scan for classes and interfaces...");
            
            for (String line : lines) {
                lineNumber++;
                String trimmedLine = line.trim();
                
                // Detect class or interface declarations
                boolean isClassDeclaration = trimmedLine.contains("class ") && 
                    (trimmedLine.startsWith("public class ") || 
                     trimmedLine.startsWith("private class ") || 
                     trimmedLine.startsWith("protected class ") || 
                     trimmedLine.startsWith("class "));
                
                boolean isInterfaceDeclaration = trimmedLine.contains("interface ") && 
                    (trimmedLine.startsWith("public interface ") || 
                     trimmedLine.startsWith("private interface ") || 
                     trimmedLine.startsWith("protected interface ") || 
                     trimmedLine.startsWith("interface "));
                
                if (isClassDeclaration || isInterfaceDeclaration) {
                    logger.debug("Line {} may contain class/interface definition: {}", lineNumber, trimmedLine);
                    
                    String type = isClassDeclaration ? "class" : "interface";
                    String name = extractName(trimmedLine, type);
                    
                    if (name != null && !name.isEmpty()) {
                        String fullyQualifiedName = packageName.isEmpty() ? name : packageName + "." + name;
                        String id = "java:" + fullyQualifiedName;
                        
                        logger.debug("Found {}: {} (ID: {})", type, name, id);
                        
                        CodeEntity entity = new CodeEntity(id, type, name, fullyQualifiedName, filePath, lineNumber, 0);
                        fileEntities.put(name, entity);
                        entities.put(id, entity);
                        entitiesCreated++;
                    } else {
                        logger.debug("Failed to extract name from line: {}", trimmedLine);
                    }
                }
                
                // Extract method definitions
                if (line.contains("(") && line.contains(")") && !line.contains("=") && !line.contains("if") && 
                    !line.contains("while") && !line.contains("for") && !line.contains("switch")) {
                    
                    logger.debug("Line {} may contain method definition: {}", lineNumber, trimmedLine);
                    
                    String methodName = extractMethodName(trimmedLine);
                    if (methodName != null) {
                        // Try to determine the parent class/interface
                        String parentName = getParentEntityName(fileEntities.keySet());
                        if (parentName != null) {
                            String parentId = "java:" + (packageName.isEmpty() ? parentName : packageName + "." + parentName);
                            String methodId = parentId + "#" + methodName;
                            
                            logger.debug("Found method: {} in parent: {} (ID: {})", methodName, parentName, methodId);
                            
                            CodeEntity methodEntity = new CodeEntity(
                                    methodId, "method", methodName, parentId + "#" + methodName, 
                                    filePath, lineNumber, 0);
                            entities.put(methodId, methodEntity);
                            entitiesCreated++;
                            
                            // Create relationship between parent and method
                            CodeRelationship relationship = new CodeRelationship(parentId, methodId, "CONTAINS");
                            addRelationship(relationship);
                            relationshipsCreated++;
                        } else {
                            logger.debug("Could not determine parent for method: {}", methodName);
                        }
                    } else {
                        logger.debug("Failed to extract method name from line: {}", trimmedLine);
                    }
                }
                
                // Extract relationships from extends/implements
                if (line.contains("extends ") || line.contains("implements ")) {
                    logger.debug("Line {} may contain extends/implements: {}", lineNumber, trimmedLine);
                    
                    String sourceName = null;
                    
                    if (line.contains("class ")) {
                        sourceName = extractName(trimmedLine, "class");
                    } else if (line.contains("interface ")) {
                        sourceName = extractName(trimmedLine, "interface");
                    }
                    
                    if (sourceName != null) {
                        String sourceId = "java:" + (packageName.isEmpty() ? sourceName : packageName + "." + sourceName);
                        logger.debug("Source entity for relationship: {} (ID: {})", sourceName, sourceId);
                        
                        // Process extends
                        if (line.contains("extends ")) {
                            int extendsStart = line.indexOf("extends ") + 8;
                            int extendsEnd = line.contains("implements ") 
                                    ? line.indexOf("implements ")
                                    : (line.contains("{") ? line.indexOf("{") : line.length());
                            
                            String target = line.substring(extendsStart, extendsEnd).trim();
                            if (target.contains("<")) {
                                target = target.substring(0, target.indexOf("<")).trim();
                            }
                            
                            String targetId = "java:" + (target.contains(".") ? target : 
                                    (packageName.isEmpty() ? target : packageName + "." + target));
                            
                            logger.debug("Found 'extends' relationship: {} -> {}", sourceId, targetId);
                            
                            CodeRelationship relationship = new CodeRelationship(sourceId, targetId, "EXTENDS");
                            addRelationship(relationship);
                            relationshipsCreated++;
                        }
                        
                        // Process implements
                        if (line.contains("implements ")) {
                            int implementsStart = line.indexOf("implements ") + 11;
                            int implementsEnd = line.contains("{") ? line.indexOf("{") : line.length();
                            
                            String implementsList = line.substring(implementsStart, implementsEnd).trim();
                            String[] interfaces = implementsList.split(",");
                            
                            logger.debug("Found 'implements' clause with {} interfaces", interfaces.length);
                            
                            for (String interfaceName : interfaces) {
                                interfaceName = interfaceName.trim();
                                
                                if (interfaceName.contains("<")) {
                                    interfaceName = interfaceName.substring(0, interfaceName.indexOf("<")).trim();
                                }
                                
                                String targetId = "java:" + (interfaceName.contains(".") ? interfaceName : 
                                        (packageName.isEmpty() ? interfaceName : packageName + "." + interfaceName));
                                
                                logger.debug("Found 'implements' relationship: {} -> {}", sourceId, targetId);
                                
                                CodeRelationship relationship = new CodeRelationship(sourceId, targetId, "IMPLEMENTS");
                                addRelationship(relationship);
                                relationshipsCreated++;
                            }
                        }
                    } else {
                        logger.debug("Could not extract source name for relationship from line: {}", trimmedLine);
                    }
                }
            }
            
            logger.debug("Completed processing file. Entities created: {}, Relationships created: {}", 
                    entitiesCreated, relationshipsCreated);
            
        } catch (Exception e) {
            logger.error("Error processing Java file: {} - {}", filePath, e.getMessage(), e);
        }
        
        result.put("entities_created", entitiesCreated);
        result.put("relationships_created", relationshipsCreated);
        return result;
    }
    
    private String extractName(String line, String type) {
        logger.debug("Extracting {} name from line: '{}'", type, line);
        
        // Find the index of the type keyword in the line
        String keyword = type + " ";
        int typeIndex = line.indexOf(keyword);
        if (typeIndex < 0) {
            logger.debug("Type '{}' not found in line", keyword);
            return null;
        }
        
        // Find the starting position of the name (after the type keyword)
        int nameStart = typeIndex + keyword.length();
        
        // Skip any whitespace
        while (nameStart < line.length() && Character.isWhitespace(line.charAt(nameStart))) {
            nameStart++;
        }
        
        if (nameStart >= line.length()) {
            logger.debug("No name found after type keyword");
            return null;
        }
        
        // Find the end of the name - it's a word, so it ends at whitespace or special characters
        int nameEnd = nameStart;
        while (nameEnd < line.length() && 
               (Character.isLetterOrDigit(line.charAt(nameEnd)) || line.charAt(nameEnd) == '_')) {
            nameEnd++;
        }
        
        if (nameStart < nameEnd) {
            String name = line.substring(nameStart, nameEnd).trim();
            logger.debug("Extracted name: '{}'", name);
            return name;
        }
        
        logger.debug("Failed to extract name, invalid range: {} to {}", nameStart, nameEnd);
        return null;
    }
    
    private String extractMethodName(String line) {
        // This is a very simplistic approach and doesn't handle all cases
        int parenIndex = line.indexOf('(');
        if (parenIndex < 0) return null;
        
        String beforeParen = line.substring(0, parenIndex).trim();
        String[] parts = beforeParen.split("\\s+");
        
        if (parts.length > 0) {
            return parts[parts.length - 1];
        }
        
        return null;
    }
    
    private String getParentEntityName(java.util.Set<String> entities) {
        if (entities.isEmpty()) return null;
        return entities.iterator().next(); // Get the first entity
    }
    
    private void addRelationship(CodeRelationship relationship) {
        if (!relationships.containsKey(relationship.getSourceId())) {
            relationships.put(relationship.getSourceId(), new ArrayList<>());
        }
        relationships.get(relationship.getSourceId()).add(relationship);
    }
    
    private void saveGraph() {
        try {
            File graphFile = new File(dataDir, GRAPH_FILE_NAME);
            try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(graphFile))) {
                // Create a serializable structure
                Map<String, Object> graphData = new HashMap<>();
                graphData.put("entities", new HashMap<>(entities));
                graphData.put("relationships", new HashMap<>(relationships));
                
                oos.writeObject(graphData);
            }
            logger.info("Saved knowledge graph to {}", graphFile.getAbsolutePath());
        } catch (IOException e) {
            logger.error("Error saving knowledge graph", e);
        }
    }
    
    @SuppressWarnings("unchecked")
    private void loadGraph(File graphFile) {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(graphFile))) {
            Map<String, Object> graphData = (Map<String, Object>) ois.readObject();
            
            entities.clear();
            entities.putAll((Map<String, CodeEntity>) graphData.get("entities"));
            
            relationships.clear();
            relationships.putAll((Map<String, List<CodeRelationship>>) graphData.get("relationships"));
            
            logger.info("Loaded knowledge graph from {}", graphFile.getAbsolutePath());
        } catch (IOException | ClassNotFoundException e) {
            logger.error("Error loading knowledge graph", e);
        }
    }
    
    private void createDirectoryIfNotExists() {
        Path path = Paths.get(dataDir);
        if (!Files.exists(path)) {
            try {
                Files.createDirectories(path);
                logger.info("Created knowledge graph directory: {}", dataDir);
            } catch (IOException e) {
                logger.error("Error creating knowledge graph directory", e);
            }
        }
    }
    
    private void collectCodeFiles(String path, List<File> files, boolean recursive) {
        logger.info("Collecting code files from path: '{}', recursive: {}", path, recursive);
        
        if (path == null || path.isEmpty()) {
            // Process all code in the default repository location
            File codeDir = new File("./data/code");
            if (codeDir.exists() && codeDir.isDirectory()) {
                logger.info("Processing all code repositories in: {}", codeDir.getAbsolutePath());
                File[] repos = codeDir.listFiles();
                if (repos != null) {
                    logger.info("Found {} repositories in code directory", repos.length);
                    for (File repo : repos) {
                        if (repo.isDirectory()) {
                            logger.info("Processing repository: {}", repo.getName());
                            collectFilesFromDirectory(repo, files, recursive);
                        }
                    }
                } else {
                    logger.warn("No repositories found in code directory: {}", codeDir.getAbsolutePath());
                }
            } else {
                logger.warn("Default code directory ./data/code not found or is not a directory. Absolute path: {}", 
                        new File("./data/code").getAbsolutePath());
            }
        } else {
            // First check if the path exists directly
            File pathFile = new File(path);
            
            // If it doesn't exist directly, check if it's a repository name in the code directory
            if (!pathFile.exists()) {
                // Try to interpret as a repository in code directory
                File repositoryFile = new File("./data/code/" + path);
                logger.info("Path not found directly, trying as repository name: {}", repositoryFile.getAbsolutePath());
                
                if (repositoryFile.exists()) {
                    pathFile = repositoryFile;
                    logger.info("Found repository: {}", pathFile.getAbsolutePath());
                } else {
                    logger.warn("Specified path does not exist as direct path or repository name: {}", path);
                    logger.warn("Attempted paths: {} and {}", 
                            new File(path).getAbsolutePath(),
                            repositoryFile.getAbsolutePath());
                    return;
                }
            }
            
            if (pathFile.isDirectory()) {
                logger.info("Processing directory: {}", pathFile.getAbsolutePath());
                collectFilesFromDirectory(pathFile, files, recursive);
            } else if (isCodeFile(pathFile.getName())) {
                logger.info("Processing single file: {}", pathFile.getAbsolutePath());
                files.add(pathFile);
            } else {
                logger.warn("Path is neither a directory nor a recognized code file: {}", pathFile.getAbsolutePath());
            }
        }
        
        logger.info("Collected {} files for processing", files.size());
    }
    
    private void collectFilesFromDirectory(File directory, List<File> files, boolean recursive) {
        logger.debug("Scanning directory: {}", directory.getAbsolutePath());
        
        File[] fileList = directory.listFiles();
        if (fileList == null) {
            logger.warn("Could not list files in directory: {}", directory.getAbsolutePath());
            return;
        }
        
        int dirCount = 0;
        int fileCount = 0;
        
        for (File file : fileList) {
            if (file.isDirectory()) {
                dirCount++;
                if (recursive) {
                    collectFilesFromDirectory(file, files, true);
                }
            } else if (isCodeFile(file.getName())) {
                fileCount++;
                logger.debug("Adding code file: {}", file.getAbsolutePath());
                files.add(file);
            }
        }
        
        logger.debug("Directory {} contains {} subdirectories and {} code files", 
                directory.getName(), dirCount, fileCount);
    }
    
    private boolean isCodeFile(String fileName) {
        String extension = getFileExtension(fileName);
        return "java".equals(extension) || 
               "js".equals(extension) || 
               "py".equals(extension) || 
               "ts".equals(extension) || 
               "jsx".equals(extension) || 
               "tsx".equals(extension) || 
               "c".equals(extension) || 
               "cpp".equals(extension) || 
               "h".equals(extension) || 
               "cs".equals(extension) || 
               "go".equals(extension) || 
               "rb".equals(extension) || 
               "php".equals(extension);
    }
    
    private String getFileExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0) {
            return fileName.substring(lastDot + 1).toLowerCase();
        }
        return "";
    }
    
    /**
     * Clean up resources when the service is destroyed.
     */
    @PreDestroy
    public void destroy() {
        try {
            saveGraph();
        } catch (Exception e) {
            logger.error("Error closing resources", e);
        }
    }

    /**
     * Command to rebuild the knowledge graph for all repositories
     */
    @Override
    public void rebuildEntireKnowledgeGraph() {
        logger.info("Rebuilding entire knowledge graph for all repositories");
        
        // First clear existing graph
        entities.clear();
        relationships.clear();
        logger.info("Cleared existing knowledge graph. Starting rebuild...");
        
        // Build graph from all repositories
        Map<String, Object> result = buildKnowledgeGraph(null, true);
        
        logger.info("Knowledge graph rebuild complete. Created {} entities and {} relationships", 
                entities.size(), countRelationships());
    }
} 