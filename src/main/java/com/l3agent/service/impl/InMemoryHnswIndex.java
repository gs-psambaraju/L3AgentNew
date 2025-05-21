package com.l3agent.service.impl;

import com.github.jelmerk.knn.hnsw.HnswIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * In-memory HNSW index implementation that doesn't use serialization for persistence.
 * The index is built in memory from the vector storage on startup.
 */
public class InMemoryHnswIndex {
    private static final Logger logger = LoggerFactory.getLogger(InMemoryHnswIndex.class);
    
    private HnswIndex<String, float[], VectorItem, Float> index;
    private final int dimensions;
    private int size = 0;
    
    /**
     * Create a new in-memory HNSW index.
     * 
     * @param dimensions The dimensionality of vectors
     * @param maxItemCount The maximum number of items to store
     */
    public InMemoryHnswIndex(int dimensions, int maxItemCount) {
        this.dimensions = dimensions;
        createNewIndex(maxItemCount);
    }
    
    /**
     * Create a new empty index with the specified capacity.
     * 
     * @param maxItemCount The maximum number of items
     */
    private void createNewIndex(int maxItemCount) {
        index = HnswIndex.newBuilder(dimensions, this::calculateDistance, maxItemCount)
                .withM(16)
                .withEfConstruction(200)
                .withEf(10)
                .build();
    }
    
    /**
     * Build or rebuild the index from the vector storage.
     * 
     * @param storage The vector storage
     * @param namespace The namespace
     */
    public void buildIndex(JsonVectorStorage storage, String namespace) {
        logger.info("Building in-memory HNSW index for namespace {} from storage", namespace);
        
        List<String> ids = storage.getAllIds(namespace);
        if (ids.isEmpty()) {
            logger.warn("No vectors found in storage for namespace {}", namespace);
            return;
        }
        
        logger.info("Found {} vectors in storage for namespace {}", ids.size(), namespace);
        
        // Create a new index with appropriate capacity
        int capacity = Math.max(ids.size() * 2, 10000); // Double capacity for growth
        logger.info("Creating new index with capacity {} for namespace {}", capacity, namespace);
        createNewIndex(capacity); 
        
        int successCount = 0;
        int errorCount = 0;
        long startTime = System.currentTimeMillis();
        
        // Add all vectors to the index
        for (String id : ids) {
            try {
                float[] vector = storage.loadVector(id, namespace);
                if (vector != null) {
                    add(id, vector);
                    successCount++;
                    
                    // Log progress periodically
                    if (successCount % 1000 == 0) {
                        long currentTime = System.currentTimeMillis();
                        double elapsedSeconds = (currentTime - startTime) / 1000.0;
                        double vectorsPerSecond = successCount / Math.max(elapsedSeconds, 0.001);
                        double percentComplete = (successCount * 100.0) / ids.size();
                        
                        logger.info("Progress: Added {}/{} vectors ({}%) to index for namespace {} - {} vectors/sec",
                            successCount, ids.size(), String.format("%.2f", percentComplete), 
                            namespace, String.format("%.2f", vectorsPerSecond));
                    }
                } else {
                    errorCount++;
                    logger.warn("No vector data found for ID {} in namespace {}", id, namespace);
                }
            } catch (Exception e) {
                errorCount++;
                logger.error("Error adding vector {} to index: {}", id, e.getMessage(), e);
            }
        }
        
        size = successCount;
        long totalTimeMs = System.currentTimeMillis() - startTime;
        
        logger.info("Completed building index for namespace {}: {} vectors added, {} errors, took {}ms ({} vectors/sec)", 
                   namespace, successCount, errorCount, totalTimeMs, 
                   (successCount * 1000.0) / Math.max(totalTimeMs, 1));
        
        // Log memory usage
        Runtime runtime = Runtime.getRuntime();
        long usedMemoryMB = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        logger.info("Memory usage after building index for namespace {}: {}MB", namespace, usedMemoryMB);
    }
    
    /**
     * Add a vector to the index.
     * 
     * @param id The vector ID
     * @param vector The vector data
     * @return true if successful, false otherwise
     */
    public boolean add(String id, float[] vector) {
        try {
            if (vector == null || vector.length != dimensions) {
                logger.warn("Vector for ID {} has wrong dimensions: expected {}, got {}", 
                           id, dimensions, vector == null ? "null" : vector.length);
                return false;
            }
            
            VectorItem item = new VectorItem(id, vector);
            index.add(item);
            size++;
            return true;
        } catch (Exception e) {
            logger.error("Error adding item {} to index: {}", id, e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Remove a vector from the index.
     * 
     * @param id The vector ID
     * @return true if successful, false otherwise
     */
    public boolean remove(String id) {
        try {
            // The HNSW index requires a version parameter for remove
            boolean removed = index.remove(id, 0);
            if (removed) {
                size--;
            }
            return removed;
        } catch (Exception e) {
            logger.error("Error removing item {} from index: {}", id, e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * A simple result class to hold similarity search results.
     */
    public static class SearchResult {
        private final String id;
        private final float similarity;
        
        public SearchResult(String id, float similarity) {
            this.id = id;
            this.similarity = similarity;
        }
        
        public String getId() {
            return id;
        }
        
        public float getSimilarity() {
            return similarity;
        }
    }
    
    /**
     * Find similar vectors in the index.
     * 
     * @param queryVector The query vector
     * @param k The maximum number of results
     * @return List of similarity results
     */
    public List<SearchResult> findSimilar(float[] queryVector, int k) {
        try {
            if (size == 0) {
                logger.warn("Index is empty, returning empty results");
                return Collections.emptyList();
            }
            
            // Convert from HnswIndex results to our SearchResult objects
            return index.findNearest(queryVector, k)
                    .stream()
                    .map(result -> {
                        // Convert distance to similarity score (1 - distance for cosine distance)
                        float similarity = 1.0f - result.distance();
                        return new SearchResult(result.item().id(), similarity);
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            logger.error("Error searching index: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }
    
    /**
     * Find similar vectors in the index with a minimum similarity threshold.
     * 
     * @param queryVector The query vector
     * @param k The maximum number of results
     * @param minSimilarity The minimum similarity threshold (0-1)
     * @return List of similarity results
     */
    public List<SearchResult> findSimilar(float[] queryVector, int k, float minSimilarity) {
        try {
            if (size == 0) {
                logger.warn("Index is empty, returning empty results");
                return Collections.emptyList();
            }
            
            // Filter results by similarity threshold
            return index.findNearest(queryVector, k)
                    .stream()
                    .map(result -> {
                        // Convert distance to similarity score (1 - distance for cosine distance)
                        float similarity = 1.0f - result.distance();
                        return new SearchResult(result.item().id(), similarity);
                    })
                    .filter(result -> result.getSimilarity() >= minSimilarity)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            logger.error("Error searching index: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }
    
    /**
     * Get a vector by ID.
     * 
     * @param id The vector ID
     * @return The vector item, or empty if not found
     */
    public Optional<VectorItem> get(String id) {
        try {
            return index.get(id);
        } catch (Exception e) {
            logger.error("Error getting item {}: {}", id, e.getMessage(), e);
            return Optional.empty();
        }
    }
    
    /**
     * Calculate the cosine distance between two vectors.
     * 
     * @param v1 First vector
     * @param v2 Second vector
     * @return The cosine distance (0-2, where 0 is identical)
     */
    private float calculateDistance(float[] v1, float[] v2) {
        // Cosine distance = 1 - cosine similarity
        float dotProduct = 0.0f;
        float normA = 0.0f;
        float normB = 0.0f;
        
        for (int i = 0; i < v1.length; i++) {
            dotProduct += v1[i] * v2[i];
            normA += v1[i] * v1[i];
            normB += v2[i] * v2[i];
        }
        
        if (normA == 0 || normB == 0) {
            return 1.0f; // Maximum distance for zero vectors
        }
        
        float similarity = dotProduct / ((float) Math.sqrt(normA) * (float) Math.sqrt(normB));
        
        // Clamp to handle floating point errors
        similarity = Math.max(-1.0f, Math.min(1.0f, similarity));
        
        // Convert similarity to distance
        return 1.0f - similarity;
    }
    
    /**
     * Get the size of the index.
     * 
     * @return The number of items in the index
     */
    public int size() {
        return size;
    }
    
    /**
     * Vector item class for the HNSW index.
     */
    public static class VectorItem implements com.github.jelmerk.knn.Item<String, float[]> {
        private final String id;
        private final float[] vector;
        
        public VectorItem(String id, float[] vector) {
            this.id = id;
            this.vector = vector;
        }
        
        @Override
        public String id() {
            return id;
        }
        
        @Override
        public float[] vector() {
            return vector;
        }
        
        @Override
        public int dimensions() {
            return vector.length;
        }
    }
} 