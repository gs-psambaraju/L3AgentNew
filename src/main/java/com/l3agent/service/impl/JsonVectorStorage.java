package com.l3agent.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Storage layer for vector embeddings that uses JSON files for reliable persistence.
 * Each vector is stored in a separate JSON file to prevent serialization issues.
 */
@Component
public class JsonVectorStorage {
    private static final Logger logger = LoggerFactory.getLogger(JsonVectorStorage.class);
    
    private final String baseDir;
    private final ObjectMapper objectMapper;
    
    public JsonVectorStorage(
            @Value("${l3agent.vector-store.data-dir:./data/vector-store}") String baseDir,
            ObjectMapper objectMapper) {
        this.baseDir = baseDir;
        this.objectMapper = objectMapper;
        
        // Ensure base directory exists
        new File(baseDir).mkdirs();
        logger.info("JsonVectorStorage initialized with base directory: {}", baseDir);
    }
    
    /**
     * Store a vector in a JSON file.
     * 
     * @param id The vector ID
     * @param vector The vector data
     * @param namespace The namespace
     * @return true if successful, false otherwise
     */
    public boolean storeVector(String id, float[] vector, String namespace) {
        try {
            File baseVectorDir = new File(baseDir + "/" + namespace + "/vectors");
            // The ID can contain path separators, so the actual file might be in a subdirectory of baseVectorDir
            File vectorFile = new File(baseVectorDir, id + ".json");

            // Ensure parent directories for the specific vector file exist
            File parentDir = vectorFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                if (!parentDir.mkdirs()) {
                    logger.error("Failed to create parent directories for vector file: {}", vectorFile.getAbsolutePath());
                    return false;
                }
            }
            
            VectorEntry entry = new VectorEntry(id, vector);
            objectMapper.writeValue(vectorFile, entry);
            
            return true;
        } catch (IOException e) {
            logger.error("Error storing vector {}: {}", id, e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Load a vector from a JSON file.
     * 
     * @param id The vector ID
     * @param namespace The namespace
     * @return The vector data, or null if not found
     */
    public float[] loadVector(String id, String namespace) {
        try {
            File file = new File(baseDir + "/" + namespace + "/vectors/" + id + ".json");
            if (!file.exists()) {
                logger.debug("Vector file not found: {}", file.getAbsolutePath());
                return null;
            }
            
            long startTime = System.currentTimeMillis();
            VectorEntry entry = objectMapper.readValue(file, VectorEntry.class);
            long loadTime = System.currentTimeMillis() - startTime;
            
            if (loadTime > 100) {  // Only log slow operations
                logger.warn("Slow vector loading detected for {} in namespace {}: {}ms", 
                           id, namespace, loadTime);
            }
            
            return entry.getVector();
        } catch (IOException e) {
            logger.error("Error loading vector {} from namespace {}: {}", 
                        id, namespace, e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Check if a vector exists.
     * 
     * @param id The vector ID
     * @param namespace The namespace
     * @return true if the vector exists, false otherwise
     */
    public boolean vectorExists(String id, String namespace) {
        File file = new File(baseDir + "/" + namespace + "/vectors/" + id + ".json");
        return file.exists();
    }
    
    /**
     * Get all vector IDs in a namespace.
     * 
     * @param namespace The namespace
     * @return List of vector IDs
     */
    public List<String> getAllIds(String namespace) {
        try {
            File dir = new File(baseDir + "/" + namespace + "/vectors");
            if (!dir.exists()) {
                logger.warn("Vectors directory does not exist for namespace {}: {}", 
                           namespace, dir.getAbsolutePath());
                return new ArrayList<>();
            }
            
            logger.info("Scanning for vector files in {}", dir.getAbsolutePath());
            long startTime = System.currentTimeMillis();
            
            List<String> results;
            try (Stream<Path> paths = Files.walk(Paths.get(dir.getPath()))) {
                results = paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".json"))
                    .map(p -> {
                        // Extract relative path from namespace vectors directory
                        String fullPath = p.toString();
                        String relativePath = fullPath.substring(
                            dir.getPath().length() + 1, // +1 for the trailing slash
                            fullPath.length() - ".json".length()
                        );
                        return relativePath;
                    })
                    .collect(Collectors.toList());
            }
            
            long scanTime = System.currentTimeMillis() - startTime;
            logger.info("Found {} vector files for namespace {} in {}ms", 
                       results.size(), namespace, scanTime);
            
            return results;
        } catch (IOException e) {
            logger.error("Error getting vector IDs for namespace {}: {}", 
                        namespace, e.getMessage(), e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Delete a vector.
     * 
     * @param id The vector ID
     * @param namespace The namespace
     * @return true if successful, false otherwise
     */
    public boolean deleteVector(String id, String namespace) {
        File file = new File(baseDir + "/" + namespace + "/vectors/" + id + ".json");
        if (file.exists()) {
            return file.delete();
        }
        return false;
    }
    
    /**
     * Simple data class for vector storage with no serialization complexity.
     */
    public static class VectorEntry {
        private String id;
        private float[] vector;
        
        // Default constructor for Jackson
        public VectorEntry() {
        }
        
        public VectorEntry(String id, float[] vector) {
            this.id = id;
            this.vector = vector;
        }
        
        public String getId() {
            return id;
        }
        
        public void setId(String id) {
            this.id = id;
        }
        
        public float[] getVector() {
            return vector;
        }
        
        public void setVector(float[] vector) {
            this.vector = vector;
        }
    }
} 