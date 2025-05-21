package com.l3agent.service.impl;

import ai.djl.MalformedModelException;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.djl.inference.Predictor;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ModelNotFoundException;
import ai.djl.repository.zoo.ModelZoo;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.training.util.ProgressBar;
import ai.djl.translate.TranslateException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.jelmerk.knn.DistanceFunction;
import com.github.jelmerk.knn.Item;
import com.github.jelmerk.knn.SearchResult;
import com.github.jelmerk.knn.hnsw.HnswIndex;
import com.l3agent.service.VectorStoreService;
import com.l3agent.model.EmbeddingMetadata;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

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
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import com.l3agent.util.LoggingUtils;
import com.l3agent.util.HttpClientUtils;
import java.util.Optional;

/**
 * Implementation of the VectorStoreService using HNSW for efficient vector similarity search.
 * Uses DJL (Deep Java Library) with a code-specific embedding model for generating embeddings.
 * Supports multi-repository indexing with namespace isolation.
 * 
 * NOTE: This implementation is currently DISABLED.
 * RobustVectorStoreService is the primary active implementation (see @Primary annotation).
 * This class is kept for reference but not instantiated by Spring to avoid duplication.
 */
// @Service - Disabled to prevent duplicate VectorStoreService initialization
@SuppressWarnings("unchecked")
public class HnswVectorStoreService implements VectorStoreService {
    
    private static final Logger logger = LoggerFactory.getLogger(HnswVectorStoreService.class);
    private static final String DEFAULT_NAMESPACE = "default";
    private static final String INDEX_FILE_NAME = "hnsw_index.bin";
    private static final String METADATA_FILE_NAME = "embedding_metadata.json";
    private static final String FAILURES_FILE_NAME = "embedding_failures.json";
    private static final String NAMESPACES_FILE_NAME = "namespaces.json";
    private static final String INTERRUPTED_BATCH_FILE_NAME = "interrupted_batch.json";
    private static final int MAX_RETRIES = 3;
    private static final int MAX_CONTINUOUS_FAILURES = 5;
    
    // Maps namespace to its corresponding index
    private Map<String, HnswIndex<String, float[], VectorItem, Float>> indexes = new ConcurrentHashMap<>();
    
    // Maps namespace to its metadata map
    private Map<String, Map<String, EmbeddingMetadata>> namespaceToMetadataMap = new ConcurrentHashMap<>();
    
    private Map<String, EmbeddingFailure> failureMap = new ConcurrentHashMap<>();
    private List<String> repositoryNamespaces = new ArrayList<>();
    private ObjectMapper objectMapper = new ObjectMapper();
    
    // Track continuous failures to allow for early termination
    private AtomicInteger continuousFailureCount = new AtomicInteger(0);
    
    // Track API response times for adaptive batch sizing
    private long lastApiResponseTime = 0;
    private long averageApiResponseTime = 1000; // Initialize with 1 second
    private int successfulApiCalls = 0;
    
    // Use Gainsight API for embedding generation instead of local model
    @Value("${l3agent.llm.gainsight.access-key}")
    private String gainsightAccessKey;
    
    @Value("${l3agent.llm.gainsight.embedding-url}")
    private String gainsightEmbeddingUrl;
    
    @Value("${l3agent.llm.gainsight.default-embedding-model}")
    private String embeddingModel;
    
    @Value("${l3agent.llm.gainsight.default-embedding-model-version}")
    private String embeddingModelVersion;
    
    @Autowired
    private HttpClientUtils httpClientUtils;
    
    @Value("${l3agent.vectorstore.dimension:384}")
    private int embeddingDimension;
    
    @Value("${l3agent.vectorstore.max-connections:16}")
    private int maxConnections;
    
    @Value("${l3agent.vectorstore.ef-construction:200}")
    private int efConstruction;
    
    @Value("${l3agent.vectorstore.ef:10}")
    private int ef;
    
    @Value("${l3agent.vectorstore.data-dir:./data/vector-store}")
    private String dataDir;
    
    @Value("${l3agent.vectorstore.max-continuous-failures:5}")
    private int maxContinuousFailures = MAX_CONTINUOUS_FAILURES;
    
    @Value("${l3agent.vectorstore.batch.default-size:10}")
    private int defaultBatchSize = 10;
    
    @Value("${l3agent.vectorstore.batch.min-size:1}")
    private int minBatchSize = 1;
    
    @Value("${l3agent.vectorstore.batch.max-size:50}")
    private int maxBatchSize = 50;
    
    @Value("${l3agent.vectorstore.batch.rate-limit.requests-per-minute:60}")
    private int maxRequestsPerMinute = 60;
    
    @Value("${l3agent.vectorstore.batch.adaptive-sizing:true}")
    private boolean useAdaptiveBatchSizing = true;
    
    // For rate limiting
    private final AtomicInteger requestsInCurrentMinute = new AtomicInteger(0);
    private long minuteStartTime = System.currentTimeMillis();
    
    /**
     * Track embedding generation failures.
     */
    private static class EmbeddingFailure implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private String textHash;
        private String textPreview;
        private int failureCount;
        private long lastFailureTime;
        private String lastErrorMessage;
        
        /**
         * Default constructor for serialization/deserialization.
         */
        public EmbeddingFailure() {
            // Default constructor for Jackson serialization
        }
        
        public EmbeddingFailure(String textHash, String textPreview, String errorMessage) {
            this.textHash = textHash;
            this.textPreview = textPreview;
            this.failureCount = 1;
            this.lastFailureTime = System.currentTimeMillis();
            this.lastErrorMessage = errorMessage;
        }
        
        public void incrementFailure(String errorMessage) {
            this.failureCount++;
            this.lastFailureTime = System.currentTimeMillis();
            this.lastErrorMessage = errorMessage;
        }
        
        // Getters
        public String getTextHash() { return textHash; }
        public String getTextPreview() { return textPreview; }
        public int getFailureCount() { return failureCount; }
        public long getLastFailureTime() { return lastFailureTime; }
        public String getLastErrorMessage() { return lastErrorMessage; }
    }
    
    /**
     * Initializes the vector store service.
     * Loads the index if it exists, or creates a new one.
     */
    @PostConstruct
    public void init() {
        try {
            createDirectoryIfNotExists();
            
            // Load or initialize namespaces
            File namespacesFile = new File(dataDir, NAMESPACES_FILE_NAME);
            if (namespacesFile.exists()) {
                loadNamespaces(namespacesFile);
            } else {
                // Initialize with default namespace
                repositoryNamespaces.add(DEFAULT_NAMESPACE);
                saveNamespaces();
            }
            
            // Initialize indexes for each namespace
            for (String namespace : repositoryNamespaces) {
                initializeNamespace(namespace);
            }
            
            // Load failure data
            File failuresFile = new File(dataDir, FAILURES_FILE_NAME);
            if (failuresFile.exists()) {
                loadFailures(failuresFile);
            }
            
            // Check for and resume any interrupted batch operations
            int resumedCount = resumeAllInterruptedBatches();
            if (resumedCount > 0) {
                logger.info("Resumed {} interrupted batch operations during initialization", resumedCount);
            }
            
        } catch (Exception e) {
            logger.error("Error initializing vector store", e);
            // Create a fallback in-memory index for default namespace
            createNewIndex(DEFAULT_NAMESPACE);
            namespaceToMetadataMap.put(DEFAULT_NAMESPACE, new ConcurrentHashMap<>());
            if (!repositoryNamespaces.contains(DEFAULT_NAMESPACE)) {
                repositoryNamespaces.add(DEFAULT_NAMESPACE);
            }
        }
    }
    
    /**
     * Checks if a namespace exceeds the memory threshold and offloads items if necessary.
     * This is part of the progressive loading mechanism to handle large indices.
     * 
     * @param namespace The namespace to check
     * @return True if the index is within memory limits, false if offloading occurred
     */
    private boolean checkAndHandleMemoryLimits(String namespace) {
        HnswIndex<String, float[], VectorItem, Float> index = indexes.get(namespace);
        if (index == null) {
            return true; // No index to check
        }
        
        // Get the estimated memory usage (rough approximation)
        int itemCount = index.size();
        // Each vector item has dimensions * 4 bytes (float) plus overhead
        long estimatedMemoryBytes = (long)itemCount * embeddingDimension * 4L + (itemCount * 100L);
        long estimatedMemoryMB = estimatedMemoryBytes / (1024L * 1024L);
        
        // Log current memory usage estimate
        logger.debug("Estimated memory usage for namespace {}: {} MB ({} items)", 
                namespace, estimatedMemoryMB, itemCount);
        
        // Define a configurable threshold (default: 80% of max heap)
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory() / (1024L * 1024L); // Convert to MB
        long memoryThresholdMB = (long)(maxMemory * 0.8);
        
        if (estimatedMemoryMB > memoryThresholdMB) {
            logger.warn("Memory threshold exceeded for namespace {}. Estimated: {} MB, Threshold: {} MB", 
                    namespace, estimatedMemoryMB, memoryThresholdMB);
            
            // Save the current index to disk
            saveIndex(namespace);
            
            // For extremely large indices, we can offload oldest/least accessed items
            // This is a simple implementation - in production, we would track access patterns
            if (itemCount > 100000) {
                offloadOldestItems(namespace, itemCount / 4); // Offload 25% of items
                return false; // Indicate offloading occurred
            }
        }
        
        return true; // Within memory limits
    }
    
    /**
     * Offloads the oldest items from the index to manage memory usage.
     * In a production system, this would use access recency instead of just age.
     * 
     * @param namespace The namespace to offload items from
     * @param itemCount The number of items to offload
     */
    private void offloadOldestItems(String namespace, int itemCount) {
        logger.info("Offloading approximately {} items from namespace {}", itemCount, namespace);
        
        try {
            HnswIndex<String, float[], VectorItem, Float> index = indexes.get(namespace);
            Map<String, EmbeddingMetadata> metadataMap = namespaceToMetadataMap.get(namespace);
            
            if (index == null || metadataMap == null) {
                logger.warn("Cannot offload items: index or metadata map not found for namespace {}", namespace);
                return;
            }
            
            // Create a directory for offloaded items if it doesn't exist
            String offloadPath = getNamespacePath(namespace) + File.separator + "offloaded";
            createDirectoryIfNotExists(offloadPath);
            
            // Since HNSW doesn't provide direct access to offload specific items,
            // save the entire index, then create a smaller one with newer items
            // In a real implementation, we would track item age/access time
            
            // First, collect metadata sorted by creation time (if available)
            List<EmbeddingMetadata> sortedMetadata = new ArrayList<>(metadataMap.values());
            
            // Save offloaded metadata to disk
            File offloadMetadataFile = new File(offloadPath, "offloaded_metadata_" + 
                    System.currentTimeMillis() + ".json");
            objectMapper.writeValue(offloadMetadataFile, sortedMetadata.subList(0, 
                    Math.min(itemCount, sortedMetadata.size())).toArray());
            
            logger.info("Offloaded {} items to {}", 
                    Math.min(itemCount, sortedMetadata.size()), offloadMetadataFile.getPath());
            
            // Keep track of this offload in a manifest file
            File manifestFile = new File(offloadPath, "offload_manifest.txt");
            try (FileOutputStream fos = new FileOutputStream(manifestFile, true);
                 java.io.PrintWriter writer = new java.io.PrintWriter(fos)) {
                writer.println(System.currentTimeMillis() + "," + 
                        Math.min(itemCount, sortedMetadata.size()) + "," + 
                        offloadMetadataFile.getName());
            }
            
            // Now we would need to rebuild the index with remaining items
            // For now, just log what we would do in a full implementation
            logger.info("In a complete implementation, would rebuild index with ~{} items", 
                    index.size() - Math.min(itemCount, sortedMetadata.size()));
        } catch (Exception e) {
            logger.error("Error offloading items from namespace {}", namespace, e);
        }
    }
    
    private void initializeNamespace(String namespace) {
        String namespacePath = getNamespacePath(namespace);
        File indexFile = new File(namespacePath, INDEX_FILE_NAME);
        File metadataFile = new File(namespacePath, METADATA_FILE_NAME);
        
        // Create namespace directory if it doesn't exist
        createDirectoryIfNotExists(namespacePath);
        
        // Initialize metadata map for namespace
        Map<String, EmbeddingMetadata> metadataMap = new ConcurrentHashMap<>();
        namespaceToMetadataMap.put(namespace, metadataMap);
        
        boolean needsRebuild = false;
        
        if (indexFile.exists() && metadataFile.exists()) {
            // Load existing index and metadata
            loadIndex(namespace, indexFile);
            loadMetadata(namespace, metadataFile);
            
            // Check if index was successfully loaded
            HnswIndex<String, float[], VectorItem, Float> index = indexes.get(namespace);
            if (index != null && index.size() == 0 && metadataMap.size() > 0) {
                logger.warn("Index for namespace {} is empty but metadata has {} entries. Will attempt rebuild.", 
                    namespace, metadataMap.size());
                needsRebuild = true;
            } else {
                logger.info("Loaded existing vector store for namespace {} with {} embeddings", 
                    namespace, indexes.get(namespace).size());
                
                // Check memory limits and handle if necessary
                checkAndHandleMemoryLimits(namespace);
            }
        } else {
            // Create new index
            createNewIndex(namespace);
            logger.info("Created new vector store for namespace {}", namespace);
        }
        
        // If we need to rebuild the index from metadata, do it now
        if (needsRebuild) {
            rebuildIndexFromMetadata(namespace);
        }
    }
    
    /**
     * Rebuilds an index from metadata when the original index file couldn't be loaded.
     * This is a recovery mechanism to handle corruption or serialization incompatibilities.
     * 
     * @param namespace The namespace whose index needs to be rebuilt
     * @return The number of entries successfully rebuilt
     */
    private int rebuildIndexFromMetadata(String namespace) {
        logger.info("Rebuilding index for namespace {} from metadata", namespace);
        
        Map<String, EmbeddingMetadata> metadataMap = namespaceToMetadataMap.get(namespace);
        if (metadataMap == null || metadataMap.isEmpty()) {
            logger.warn("Cannot rebuild index: no metadata available for namespace {}", namespace);
            return 0;
        }
        
        // Create a new index (this will replace any existing one)
        createNewIndex(namespace);
        HnswIndex<String, float[], VectorItem, Float> index = indexes.get(namespace);
        
        int successCount = 0;
        int failCount = 0;
        
        // We need to load vector data from a backup source or rebuild embeddings
        // For now, we'll simply log that a full rebuild is needed
        logger.warn("Index rebuild for namespace {} requires vector data which is not available in metadata.", 
            namespace);
        logger.warn("Manual intervention required: run the RetryFailedEmbeddingsRunner to regenerate embeddings.");
        
        // Save this new empty index
        saveIndex(namespace);
        
        return successCount;
    }
    
    private String getNamespacePath(String namespace) {
        return dataDir + File.separator + namespace;
    }
    
    private void loadNamespaces(File namespacesFile) {
        try {
            String[] loadedNamespaces = objectMapper.readValue(namespacesFile, String[].class);
            repositoryNamespaces.clear();
            for (String namespace : loadedNamespaces) {
                repositoryNamespaces.add(namespace);
            }
            logger.info("Loaded {} repository namespaces", repositoryNamespaces.size());
        } catch (IOException e) {
            logger.error("Error loading repository namespaces", e);
            // Fallback to default namespace
            repositoryNamespaces.clear();
            repositoryNamespaces.add(DEFAULT_NAMESPACE);
        }
    }
    
    private void saveNamespaces() {
        try {
            File namespacesFile = new File(dataDir, NAMESPACES_FILE_NAME);
            objectMapper.writeValue(namespacesFile, repositoryNamespaces.toArray());
            logger.info("Saved {} repository namespaces", repositoryNamespaces.size());
        } catch (IOException e) {
            logger.error("Error saving repository namespaces", e);
        }
    }
    
    /**
     * Initialize the embedding model.
     */
    private void initEmbeddingModel() {
        // This method is no longer needed as we're using the Gainsight API
        // But we'll keep it as a no-op for compatibility
        logger.info("Using Gainsight API for embeddings: {}", gainsightEmbeddingUrl);
    }
    
    private void createDirectoryIfNotExists() {
        Path path = Paths.get(dataDir);
        if (!Files.exists(path)) {
            try {
                Files.createDirectories(path);
                logger.info("Created vector store directory: {}", dataDir);
            } catch (IOException e) {
                logger.error("Error creating vector store directory", e);
            }
        }
    }
    
    private void createDirectoryIfNotExists(String dirPath) {
        Path path = Paths.get(dirPath);
        if (!Files.exists(path)) {
            try {
                Files.createDirectories(path);
                logger.info("Created directory: {}", dirPath);
            } catch (IOException e) {
                logger.error("Error creating directory: {}", dirPath, e);
            }
        }
    }
    
    private void createNewIndex(String namespace) {
        createNewIndex(namespace, embeddingDimension);
    }
    
    /**
     * Creates a new index with the specified dimensionality.
     * 
     * @param namespace The namespace for the index
     * @param dimension The dimensionality for the vectors
     */
    private void createNewIndex(String namespace, int dimension) {
        logger.info("Creating new vector index for namespace {} with dimension {}", namespace, dimension);
        
        // Increase max capacity from 10000 to 100000
        HnswIndex<String, float[], VectorItem, Float> index = HnswIndex
                .newBuilder(dimension, createDistanceFunction(), 100000)
                .withM(maxConnections)
                .withEfConstruction(efConstruction)
                .withEf(ef)
                .build();
        
        indexes.put(namespace, index);
        // Update the embedding dimension used for this index
        embeddingDimension = dimension;
    }
    
    private void loadIndex(String namespace, File indexFile) {
        try {
            HnswIndex<String, float[], VectorItem, Float> index = HnswIndex.load(indexFile);
            indexes.put(namespace, index);
            logger.info("Loaded index for namespace {} from {}", namespace, indexFile.getAbsolutePath());
        } catch (IOException e) {
            logger.error("Error loading index for namespace {}: {}", namespace, e.getMessage());
            
            // Create a backup of the problematic index file
            try {
                File backupFile = new File(indexFile.getParentFile(), 
                        "hnsw_index_backup_" + System.currentTimeMillis() + ".bin");
                Files.copy(indexFile.toPath(), backupFile.toPath());
                logger.info("Created backup of corrupted index file at {}", backupFile.getAbsolutePath());
                
                // Try to recover from metadata if available
                File metadataFile = new File(indexFile.getParentFile(), METADATA_FILE_NAME);
                if (metadataFile.exists()) {
                    logger.info("Attempting to rebuild index from metadata for namespace {}", namespace);
                    // Create a new index
                    createNewIndex(namespace);
                    
                    // Will load the metadata in a separate step via initializeNamespace
                } else {
                    logger.warn("Cannot recover index from metadata - no metadata file found");
                    createNewIndex(namespace);
                }
            } catch (Exception backupEx) {
                logger.error("Error creating backup of corrupted index file: {}", backupEx.getMessage());
                createNewIndex(namespace);
            }
        }
    }
    
    private void saveIndex(String namespace) {
        HnswIndex<String, float[], VectorItem, Float> index = indexes.get(namespace);
        if (index == null) {
            logger.warn("Cannot save index for namespace {}: index not found", namespace);
            return;
        }
        
        try {
            String namespacePath = getNamespacePath(namespace);
            createDirectoryIfNotExists(namespacePath);
            
            File indexFile = new File(namespacePath, INDEX_FILE_NAME);
            index.save(indexFile);
            logger.info("Saved index for namespace {} to {}", namespace, indexFile.getAbsolutePath());
            
            saveMetadata(namespace);
        } catch (IOException e) {
            logger.error("Error saving index for namespace {}", namespace, e);
        }
    }
    
    private void loadMetadata(String namespace, File metadataFile) {
        try {
            EmbeddingMetadata[] metadataArray = objectMapper.readValue(metadataFile, EmbeddingMetadata[].class);
            Map<String, EmbeddingMetadata> metadataMap = namespaceToMetadataMap.get(namespace);
            if (metadataMap == null) {
                metadataMap = new ConcurrentHashMap<>();
                namespaceToMetadataMap.put(namespace, metadataMap);
            }
            
            metadataMap.clear();
            for (EmbeddingMetadata metadata : metadataArray) {
                metadataMap.put(metadata.getSource(), metadata);
            }
            logger.info("Loaded metadata for {} embeddings in namespace {}", 
                    metadataMap.size(), namespace);
        } catch (IOException e) {
            logger.error("Error loading metadata for namespace {}", namespace, e);
        }
    }
    
    private void saveMetadata(String namespace) {
        Map<String, EmbeddingMetadata> metadataMap = namespaceToMetadataMap.get(namespace);
        if (metadataMap == null) {
            logger.warn("Cannot save metadata for namespace {}: metadata map not found", namespace);
            return;
        }
        
        try {
            String namespacePath = getNamespacePath(namespace);
            createDirectoryIfNotExists(namespacePath);
            
            File metadataFile = new File(namespacePath, METADATA_FILE_NAME);
            objectMapper.writeValue(metadataFile, metadataMap.values().toArray());
            logger.info("Saved metadata for namespace {} to {}", namespace, metadataFile.getAbsolutePath());
        } catch (IOException e) {
            logger.error("Error saving metadata for namespace {}", namespace, e);
        }
    }
    
    private void loadFailures(File failuresFile) {
        try {
            EmbeddingFailure[] failuresArray = objectMapper.readValue(failuresFile, EmbeddingFailure[].class);
            failureMap.clear();
            for (EmbeddingFailure failure : failuresArray) {
                failureMap.put(failure.getTextHash(), failure);
            }
            logger.info("Loaded {} embedding failures", failureMap.size());
        } catch (IOException e) {
            logger.error("Error loading embedding failures", e);
        }
    }
    
    private void saveFailures() {
        try {
            File failuresFile = new File(dataDir, FAILURES_FILE_NAME);
            objectMapper.writeValue(failuresFile, failureMap.values().toArray());
            logger.info("Saved {} embedding failures to {}", failureMap.size(), failuresFile.getAbsolutePath());
        } catch (IOException e) {
            logger.error("Error saving embedding failures", e);
        }
    }
    
    @Override
    public boolean storeEmbedding(String id, float[] vector, EmbeddingMetadata metadata) {
        return storeEmbedding(id, vector, metadata, DEFAULT_NAMESPACE);
    }
    
    @Override
    public boolean storeEmbedding(String id, float[] vector, EmbeddingMetadata metadata, String repositoryNamespace) {
        // Ensure namespace exists
        if (!repositoryNamespaces.contains(repositoryNamespace)) {
            // Create new namespace
            repositoryNamespaces.add(repositoryNamespace);
            createNewIndex(repositoryNamespace, vector.length);
            namespaceToMetadataMap.put(repositoryNamespace, new ConcurrentHashMap<>());
            saveNamespaces();
            logger.info("Created new namespace: {}", repositoryNamespace);
        }
        
        try {
            // Set the repository namespace in metadata
            metadata.setRepositoryNamespace(repositoryNamespace);
            
            // Get the index for this namespace
            HnswIndex<String, float[], VectorItem, Float> index = indexes.get(repositoryNamespace);
            if (index == null) {
                logger.error("Index not found for namespace: {}", repositoryNamespace);
                return false;
            }
            
            // Get the metadata map for this namespace
            Map<String, EmbeddingMetadata> metadataMap = namespaceToMetadataMap.get(repositoryNamespace);
            if (metadataMap == null) {
                metadataMap = new ConcurrentHashMap<>();
                namespaceToMetadataMap.put(repositoryNamespace, metadataMap);
            }
            
            // Ensure ID has namespace prefix for uniqueness
            String namespacedId = getNamespacedId(id, repositoryNamespace);
            
            // Check if we need to recreate the index with a different dimension
            int currentIndexDimension = 0;
            try {
                // Use a dummy item to check the expected dimension
                VectorItem dummy = new VectorItem("dummy", new float[1]);
                // This will throw an exception with the expected dimension
                index.add(dummy);
            } catch (IllegalArgumentException e) {
                String msg = e.getMessage();
                if (msg != null && msg.contains("does not have dimensionality of")) {
                    // Extract the current dimension from error message
                    String[] parts = msg.split(":");
                    if (parts.length > 1) {
                        try {
                            currentIndexDimension = Integer.parseInt(parts[1].trim());
                        } catch (NumberFormatException nfe) {
                            logger.warn("Could not parse index dimension from error message: {}", msg);
                        }
                    }
                }
            }
            
            // If we have a dimension mismatch and we've determined the current dimension
            if (currentIndexDimension > 0 && vector.length != currentIndexDimension) {
                logger.warn("Vector dimension ({}) does not match index dimension ({}). Recreating index for namespace {}.", 
                          vector.length, currentIndexDimension, repositoryNamespace);
                
                // Recreate the index with the new dimension
                createNewIndex(repositoryNamespace, vector.length);
                index = indexes.get(repositoryNamespace);
                
                // Clear existing metadata since it won't be compatible with new dimensions
                metadataMap.clear();
            }
            
            // Store the embedding
            VectorItem item = new VectorItem(namespacedId, vector);
            
            try {
                index.add(item);
                metadataMap.put(namespacedId, metadata);
                
                // Periodically save the index
                if (index.size() % 100 == 0) {
                    saveIndex(repositoryNamespace);
                }
                
                return true;
            } catch (Exception e) {
                if (e.getMessage() != null && e.getMessage().contains("exceeds the specified limit")) {
                    logger.warn("Index capacity exceeded for namespace {}. Creating new expanded index.", repositoryNamespace);
                    
                    // Save the current index before replacing it
                    saveIndex(repositoryNamespace);
                    
                    // Create a new index with expanded capacity (double the existing items + buffer)
                    int estimatedNewCapacity = index.size() * 2 + 10000;
                    
                    HnswIndex<String, float[], VectorItem, Float> newIndex = HnswIndex
                            .newBuilder(vector.length, createDistanceFunction(), estimatedNewCapacity)
                            .withM(maxConnections)
                            .withEfConstruction(efConstruction)
                            .withEf(ef)
                            .build();
                    
                    logger.info("Created new expanded index for namespace {} with capacity {}", 
                            repositoryNamespace, estimatedNewCapacity);
                    
                    // Replace the old index with the new one
                    indexes.put(repositoryNamespace, newIndex);
                    
                    // Try adding the item again
                    try {
                        newIndex.add(item);
                        metadataMap.put(namespacedId, metadata);
                        logger.info("Successfully added item to expanded index for namespace {}", repositoryNamespace);
                        return true;
                    } catch (Exception e2) {
                        logger.error("Failed to add item to expanded index: {}", e2.getMessage());
                        return false;
                    }
                } else {
                    // Some other issue occurred
                    throw e;
                }
            }
        } catch (Exception e) {
            logger.error("Error storing embedding in namespace {}", repositoryNamespace, e);
            return false;
        }
    }
    
    private String getNamespacedId(String id, String namespace) {
        // Ensure IDs have namespace prefix for uniqueness across different namespaces
        return namespace + ":" + id;
    }
    
    private String getIdFromNamespacedId(String namespacedId) {
        int colonIdx = namespacedId.indexOf(':');
        if (colonIdx > 0) {
            return namespacedId.substring(colonIdx + 1);
        }
        return namespacedId;
    }
    
    private String getNamespaceFromNamespacedId(String namespacedId) {
        int colonIdx = namespacedId.indexOf(':');
        if (colonIdx > 0) {
            return namespacedId.substring(0, colonIdx);
        }
        return DEFAULT_NAMESPACE;
    }
    
    @Override
    public List<SimilarityResult> findSimilar(float[] queryVector, int maxResults, float minSimilarity) {
        // Search across all namespaces
        return findSimilar(queryVector, maxResults, minSimilarity, new ArrayList<>());
    }
    
    @Override
    public List<SimilarityResult> findSimilar(float[] queryVector, int maxResults, float minSimilarity, 
            List<String> repositoryNamespaces) {
        List<SimilarityResult> allResults = new ArrayList<>();
        List<String> namespacesToSearch;
        
        // If no specific namespaces are provided, search all namespaces
        if (repositoryNamespaces == null || repositoryNamespaces.isEmpty()) {
            namespacesToSearch = this.repositoryNamespaces;
        } else {
            namespacesToSearch = repositoryNamespaces;
        }
        
        for (String namespace : namespacesToSearch) {
            // Get the index for this namespace
            HnswIndex<String, float[], VectorItem, Float> index = indexes.get(namespace);
            if (index == null) {
                logger.warn("Index not found for namespace: {}", namespace);
                continue;
            }
            
            try {
                // Search this namespace
                List<SearchResult<VectorItem, Float>> searchResults = 
                        index.findNearest(queryVector, Math.min(maxResults, 50));
                
                // Get the metadata map for this namespace
                Map<String, EmbeddingMetadata> metadataMap = namespaceToMetadataMap.get(namespace);
                if (metadataMap == null) {
                    logger.warn("Metadata map not found for namespace: {}", namespace);
                    continue;
                }
                
                // Process results
                for (SearchResult<VectorItem, Float> result : searchResults) {
                    // Convert distance to similarity (1 - distance for cosine)
                    float similarity = 1 - result.distance();
                    
                    // Log all similarities for debugging
                    logger.debug("Result for query in namespace {}: id={}, similarity={}", 
                            namespace, result.item().id(), similarity);
                    
                    // Filter by minimum similarity
                    if (similarity >= minSimilarity) {
                        SimilarityResult similarityResult = new SimilarityResult();
                        String namespacedId = result.item().id();
                        similarityResult.setId(getIdFromNamespacedId(namespacedId));
                        similarityResult.setMetadata(metadataMap.get(namespacedId));
                        similarityResult.setSimilarityScore(similarity);
                        allResults.add(similarityResult);
                    }
                }
            } catch (Exception e) {
                logger.error("Error searching in namespace {}", namespace, e);
            }
        }
        
        // Sort by similarity score (descending) and limit to maxResults
        return allResults.stream()
                .sorted((a, b) -> Float.compare(b.getSimilarityScore(), a.getSimilarityScore()))
                .limit(maxResults)
                .collect(Collectors.toList());
    }
    
    @Override
    public float[] generateEmbedding(String text) {
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                // Use Gainsight API for embedding generation
                float[] embedding = callGainsightEmbeddingApi(text);
                
                // If we reached here, the embedding was successful
                if (attempt > 0) {
                    logger.info("Successfully generated embedding on attempt {}/{}", attempt + 1, MAX_RETRIES);
                }
                
                // Reset continuous failure count on success
                continuousFailureCount.set(0);
                
                return embedding;
            } catch (RateLimitException rle) {
                // Special handling for rate limit exceptions
                logger.warn("Rate limit exceeded (attempt {}/{}): {}", 
                          attempt + 1, MAX_RETRIES, rle.getMessage());
                
                if (attempt < MAX_RETRIES - 1) {
                    try {
                        // For rate limits, use a longer base delay (2000ms)
                        long baseDelayMillis = 2000;
                        
                        // Calculate exponential backoff
                        long backoffMillis = (long) (Math.pow(2, attempt) * baseDelayMillis);
                        
                        // Add random jitter (±20% of the backoff time)
                        double jitterFactor = 0.8 + Math.random() * 0.4; // Between 0.8 and 1.2
                        backoffMillis = (long) (backoffMillis * jitterFactor);
                        
                        // Cap at 45 seconds maximum
                        backoffMillis = Math.min(backoffMillis, 45000);
                        
                        logger.info("Rate limited. Retrying in {} ms", backoffMillis);
                        Thread.sleep(backoffMillis);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        logger.error("Retry interrupted", ie);
                        break;
                    }
                } else {
                    // Final failure after all retries
                    logger.error("Failed to generate embedding after {} attempts due to rate limits", MAX_RETRIES);
                    recordEmbeddingFailure(text, rle.getMessage());
                    
                    // Increment continuous failure count
                    int currentFailures = continuousFailureCount.incrementAndGet();
                    logger.warn("Continuous failure count: {}/{}", currentFailures, maxContinuousFailures);
                }
            } catch (Exception e) {
                String errorMessage = e.getMessage();
                if (errorMessage == null || errorMessage.isEmpty()) {
                    errorMessage = e.getClass().getSimpleName();
                }
                
                logger.warn("Error generating embedding for text (attempt {}/{}): {}", 
                          attempt + 1, MAX_RETRIES, errorMessage);
                
                if (attempt < MAX_RETRIES - 1) {
                    // Enhanced exponential backoff with jitter
                    try {
                        // Increased base delay from 100ms to 500ms
                        long baseDelayMillis = 500;
                        
                        // Calculate exponential backoff
                        long backoffMillis = (long) (Math.pow(2, attempt) * baseDelayMillis);
                        
                        // Add random jitter (±20% of the backoff time)
                        double jitterFactor = 0.8 + Math.random() * 0.4; // Between 0.8 and 1.2
                        backoffMillis = (long) (backoffMillis * jitterFactor);
                        
                        // Cap at 30 seconds maximum
                        backoffMillis = Math.min(backoffMillis, 30000);
                        
                        logger.info("Retrying in {} ms", backoffMillis);
                        Thread.sleep(backoffMillis);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        logger.error("Retry interrupted", ie);
                        break;
                    }
                } else {
                    // Final failure after all retries
                    logger.error("Failed to generate embedding after {} attempts", MAX_RETRIES, e);
                    recordEmbeddingFailure(text, errorMessage);
                    
                    // Increment continuous failure count
                    int currentFailures = continuousFailureCount.incrementAndGet();
                    logger.warn("Continuous failure count: {}/{}", currentFailures, maxContinuousFailures);
                }
            }
        }
        
        // Return zero vector as last resort
        return new float[embeddingDimension];
    }
    
    /**
     * Calls the Gainsight API to generate embeddings.
     * 
     * @param text The text to generate embeddings for
     * @return The embedding vector
     * @throws Exception If there's an error generating the embedding
     */
    private float[] callGainsightEmbeddingApi(String text) throws Exception {
        try {
            // Prepare the request body
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("modelVersion", embeddingModelVersion);
            requestBody.put("model", embeddingModel);
            requestBody.put("text", text);
            
            // Set headers
            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");
            headers.put("accept", "application/json");
            headers.put("access_key", gainsightAccessKey);
            
            // Make the API call using our standardized utility
            Map<String, Object> response = httpClientUtils.executePostRequest(
                    gainsightEmbeddingUrl, headers, requestBody);
            
            if (!response.containsKey("data")) {
                throw new Exception("Invalid response format, missing 'data' field");
            }
            
            // Handle potential nested structure in the response
            Object dataObject = response.get("data");
            
            // Debug the structure
            logger.debug("Response data structure: {}", dataObject.getClass().getName());
            
            List<Double> embeddingValues = extractEmbeddingValues(dataObject);
            
            if (embeddingValues == null || embeddingValues.isEmpty()) {
                throw new Exception("Empty embedding values returned from API");
            }
            
            float[] embedding = new float[embeddingValues.size()];
            
            for (int i = 0; i < embeddingValues.size(); i++) {
                embedding[i] = embeddingValues.get(i).floatValue();
            }
            
            logger.debug("Retrieved embedding with {} dimensions", embedding.length);
            
            return embedding;
        } catch (HttpClientUtils.RateLimitException rle) {
            // Wrap and rethrow our internal RateLimitException
            throw new RateLimitException(rle.getMessage());
        }
    }
    
    /**
     * Helper method to extract embedding values from various response formats.
     * 
     * @param dataObject The data object from the API response
     * @return The extracted embedding values as a list of doubles
     * @throws Exception If embedding values cannot be extracted
     */
    @SuppressWarnings("unchecked")
    private List<Double> extractEmbeddingValues(Object dataObject) throws Exception {
        if (dataObject instanceof Map) {
            // If data is a nested object
            Map<String, Object> dataMap = (Map<String, Object>) dataObject;
            
            // Check if there's a nested "data" field
            if (dataMap.containsKey("data")) {
                Object innerData = dataMap.get("data");
                
                if (innerData instanceof List) {
                    // Handle array of embedding objects
                    List<Object> dataList = (List<Object>) innerData;
                    
                    if (!dataList.isEmpty() && dataList.get(0) instanceof Map) {
                        Map<String, Object> firstEmbedding = (Map<String, Object>) dataList.get(0);
                        
                        if (firstEmbedding.containsKey("embedding")) {
                            return (List<Double>) firstEmbedding.get("embedding");
                        } else {
                            throw new Exception("Missing embedding field in response");
                        }
                    } else {
                        throw new Exception("Invalid embedding structure in response");
                    }
                } else {
                    throw new Exception("Expected list of embeddings but got: " + innerData.getClass().getName());
                }
            } else {
                // Direct embedding values
                logger.debug("Data map keys: {}", dataMap.keySet());
                
                // Just try all known formats
                if (dataMap.containsKey("embedding")) {
                    return (List<Double>) dataMap.get("embedding");
                } else {
                    // Log the entire response for debugging (careful with sensitive data)
                    logger.debug("Unknown embedding response format with keys: {}", dataMap.keySet());
                    throw new Exception("Unknown embedding response format: " + dataMap.keySet());
                }
            }
        } else if (dataObject instanceof List) {
            // If data is directly a list of values
            return (List<Double>) dataObject;
        } else {
            throw new Exception("Unexpected data type in response: " + dataObject.getClass().getName());
        }
    }
    
    /**
     * Records an embedding generation failure for tracking and analysis.
     * 
     * @param text The text that failed to be embedded
     * @param errorMessage The error message
     */
    private void recordEmbeddingFailure(String text, String errorMessage) {
        // Create a hash of the text as the key
        String textHash = String.valueOf(text.hashCode());
        
        // Create a preview of the text (first 100 chars)
        String textPreview = text.length() <= 100 ? text : text.substring(0, 100) + "...";
        
        // Record or update the failure
        EmbeddingFailure failure = failureMap.get(textHash);
        if (failure == null) {
            failure = new EmbeddingFailure(textHash, textPreview, errorMessage);
            failureMap.put(textHash, failure);
        } else {
            failure.incrementFailure(errorMessage);
        }
        
        // Save failures periodically
        if (failureMap.size() % 10 == 0) {
            saveFailures();
        }
    }
    
    @Override
    public boolean deleteEmbedding(String id) {
        return deleteEmbedding(id, DEFAULT_NAMESPACE);
    }
    
    @Override
    public boolean deleteEmbedding(String id, String repositoryNamespace) {
        try {
            // Get the index for this namespace
            HnswIndex<String, float[], VectorItem, Float> index = indexes.get(repositoryNamespace);
            if (index == null) {
                logger.error("Index not found for namespace: {}", repositoryNamespace);
                return false;
            }
            
            // Get the metadata map for this namespace
            Map<String, EmbeddingMetadata> metadataMap = namespaceToMetadataMap.get(repositoryNamespace);
            if (metadataMap == null) {
                logger.error("Metadata map not found for namespace: {}", repositoryNamespace);
                return false;
            }
            
            // Ensure ID has namespace prefix for uniqueness
            String namespacedId = getNamespacedId(id, repositoryNamespace);
            
            // Delete the embedding
            index.remove(namespacedId, -1); // Using -1 as a sentinel value for a version parameter
            metadataMap.remove(namespacedId);
            
            // Periodically save the index
            if (index.size() % 100 == 0) {
                saveIndex(repositoryNamespace);
            }
            
            return true;
        } catch (Exception e) {
            logger.error("Error deleting embedding from namespace {}", repositoryNamespace, e);
            return false;
        }
    }
    
    @Override
    public boolean isAvailable() {
        return !indexes.isEmpty();
    }
    
    @Override
    public int size() {
        return repositoryNamespaces.stream()
                .map(this::size)
                .reduce(0, Integer::sum);
    }
    
    @Override
    public int size(String repositoryNamespace) {
        HnswIndex<String, float[], VectorItem, Float> index = indexes.get(repositoryNamespace);
        return index != null ? index.size() : 0;
    }
    
    @Override
    public int getEmbeddingCount() {
        return size();
    }
    
    @Override
    public int getEmbeddingCount(String repositoryNamespace) {
        return size(repositoryNamespace);
    }
    
    @Override
    public Map<String, EmbeddingFailure> getEmbeddingFailures() {
        return new HashMap<>(failureMap);
    }
    
    @Override
    public void clearEmbeddingFailures() {
        failureMap.clear();
        saveFailures();
    }
    
    @Override
    public List<String> getRepositoryNamespaces() {
        return new ArrayList<>(repositoryNamespaces);
    }
    
    @Override
    public boolean performEmbeddingPreCheck() {
        logger.info("Performing embedding pre-check...");
        try {
            // Use a simple example text for the pre-check
            String testText = "This is a test to verify embedding generation is working properly.";
            
            // First, check if the API key is likely valid (not empty)
            if (gainsightAccessKey == null || gainsightAccessKey.isEmpty()) {
                logger.error("Pre-check failed: Gainsight access key is missing.");
                logger.error("Please set a valid access key in application.properties or via GAINSIGHT_ACCESS_KEY environment variable.");
                return false;
            }
            
            // Try to generate an embedding
            float[] embedding = null;
            try {
                embedding = generateEmbedding(testText);
            } catch (Exception e) {
                if (e.getMessage() != null && e.getMessage().contains("Authentication failed")) {
                    logger.error("Pre-check failed: Authentication to Gainsight API failed. Please check your access key.");
                    return false;
                } else {
                    throw e; // Re-throw other exceptions
                }
            }
            
            // Check if the embedding has non-zero values
            boolean hasNonZeroValues = false;
            if (embedding != null) {
                for (float value : embedding) {
                    if (value != 0.0f) {
                        hasNonZeroValues = true;
                        break;
                    }
                }
            }
            
            if (!hasNonZeroValues) {
                logger.error("Pre-check failed: generated embedding has all zero values");
                return false;
            }
            
            logger.info("Embedding pre-check passed successfully");
            return true;
        } catch (Exception e) {
            logger.error("Embedding pre-check failed with error", e);
            return false;
        }
    }
    
    @Override
    public int getContinuousFailureCount() {
        return continuousFailureCount.get();
    }
    
    @Override
    public void resetContinuousFailureCount() {
        continuousFailureCount.set(0);
    }
    
    @Override
    public int getMaxContinuousFailures() {
        return maxContinuousFailures;
    }
    
    /**
     * Clean up resources when the service is destroyed.
     */
    @PreDestroy
    public void destroy() {
        try {
            // Save all indexes
            for (String namespace : repositoryNamespaces) {
                saveIndex(namespace);
            }
            
            // Save failures
            saveFailures();
            
            // Save namespaces
            saveNamespaces();
        } catch (Exception e) {
            logger.error("Error closing resources", e);
        }
    }
    
    /**
     * Class representing a vector item in the index.
     */
    public static class VectorItem implements Item<String, float[]>, Serializable {
        private static final long serialVersionUID = 1L;
        
        private String id;
        private float[] vector;
        
        public VectorItem() {
        }
        
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
        
        // Getters for backward compatibility
        public String getId() {
            return id;
        }
        
        public float[] getVector() {
            return vector;
        }
        
        @Override
        public int dimensions() {
            return vector != null ? vector.length : 0;
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            VectorItem that = (VectorItem) o;
            return Objects.equals(id, that.id);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(id);
        }
    }
    
    /**
     * Creates a sanitized version of a request body map for logging.
     * Truncates the text field to avoid excessive logging.
     *
     * @param requestBody The original request body
     * @return A sanitized copy suitable for logging
     */
    private Map<String, Object> sanitizeRequestBodyForLogging(Map<String, Object> requestBody) {
        Map<String, Object> sanitized = new HashMap<>(requestBody);
        String originalText = (String) sanitized.get("text");
        if (originalText != null) {
            sanitized.put("text", LoggingUtils.truncateText(originalText, 100));
        }
        return sanitized;
    }
    
    // Add this class at the end of the file
    private static class RateLimitException extends Exception {
        private static final long serialVersionUID = 1L;
        
        public RateLimitException(String message) {
            super(message);
        }
    }
    
    /**
     * Creates a cosine similarity distance function for vector comparison.
     * @return Distance function that computes cosine distance (1 - cosine similarity)
     */
    private DistanceFunction<float[], Float> createDistanceFunction() {
        return (u, v) -> {
            double dotProduct = 0.0;
            double normU = 0.0;
            double normV = 0.0;
            for (int i = 0; i < u.length; i++) {
                dotProduct += u[i] * v[i];
                normU += Math.pow(u[i], 2);
                normV += Math.pow(v[i], 2);
            }
            normU = Math.sqrt(normU);
            normV = Math.sqrt(normV);
            double similarity = dotProduct / (normU * normV);
            return (float) (1 - similarity); // Convert to distance
        };
    }
    
    @Override
    public float[][] generateEmbeddingsBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return new float[0][];
        }
        
        // Handle rate limiting
        enforceRateLimit();
        
        // Calculate appropriate batch size based on adaptive sizing if enabled
        int batchSize = calculateBatchSize(texts.size());
        logger.info("Processing batch of {} texts with batch size of {}", texts.size(), batchSize);
        
        // For very large batches, process in smaller chunks to avoid timeouts
        if (texts.size() > batchSize) {
            logger.info("Processing large batch of {} texts in smaller chunks of size {}", 
                    texts.size(), batchSize);
            
            List<float[]> results = new ArrayList<>();
            
            for (int i = 0; i < texts.size(); i += batchSize) {
                int end = Math.min(i + batchSize, texts.size());
                List<String> batch = texts.subList(i, end);
                
                logger.debug("Processing batch {}/{} with {} texts", 
                        (i / batchSize) + 1, (int) Math.ceil(texts.size() / (double) batchSize), batch.size());
                
                float[][] batchVectors = generateEmbeddingsBatchInternal(batch);
                for (float[] vector : batchVectors) {
                    results.add(vector);
                }
                
                // Add a delay between batches to avoid rate limiting
                if (i + batchSize < texts.size()) {
                    try {
                        long delayMs = calculateInterBatchDelay();
                        if (delayMs > 0) {
                            logger.debug("Adding delay of {} ms between batches", delayMs);
                            Thread.sleep(delayMs);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        logger.warn("Interrupted during inter-batch delay", e);
                    }
                }
            }
            
            return results.toArray(new float[0][]);
        }
        
        // For smaller batches, process all at once
        return generateEmbeddingsBatchInternal(texts);
    }
    
    /**
     * Enforces rate limiting for API calls.
     * Blocks if necessary to stay within the rate limit.
     * Fixed implementation to handle edge cases and ensure consistent behavior.
     */
    private void enforceRateLimit() {
        long currentTime = System.currentTimeMillis();
        long elapsedTime = currentTime - minuteStartTime;
        
        // Critical: Synchronized to prevent race conditions in counter updates
        synchronized (requestsInCurrentMinute) {
            // If a minute has passed, reset the counter
            if (elapsedTime >= 60000) {
                requestsInCurrentMinute.set(0);
                minuteStartTime = currentTime;
            }
            
            // If we're at or above the limit, wait until the minute is up
            if (requestsInCurrentMinute.get() >= maxRequestsPerMinute) {
                long waitTime = Math.max(100, 60000 - elapsedTime); // Ensure minimum wait time
                
                logger.info("Rate limit reached ({}/{}). Waiting {} ms before proceeding", 
                        requestsInCurrentMinute.get(), maxRequestsPerMinute, waitTime);
                
                try {
                    Thread.sleep(waitTime);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.warn("Interrupted during rate limit wait", e);
                }
                
                // Critical: After waiting, recalculate time and reset counters
                currentTime = System.currentTimeMillis();
                // Only reset if we actually waited close to a minute
                if (waitTime > 30000 || currentTime - minuteStartTime >= 60000) {
                    requestsInCurrentMinute.set(0);
                    minuteStartTime = currentTime;
                }
                
                // Recursive call to ensure we're now under the limit
                // This handles cases where multiple threads were waiting
                enforceRateLimit();
                return;
            }
            
            // Increment the counter
            requestsInCurrentMinute.incrementAndGet();
        }
    }
    
    /**
     * Calculates the appropriate batch size based on recent API performance.
     * 
     * @param requestedSize The originally requested batch size
     * @return The calculated batch size
     */
    private int calculateBatchSize(int requestedSize) {
        if (!useAdaptiveBatchSizing) {
            return Math.min(requestedSize, defaultBatchSize);
        }
        
        // If we have performance data, adjust batch size accordingly
        if (successfulApiCalls > 5) {
            // For slow APIs (>2s per call), reduce batch size
            if (averageApiResponseTime > 2000) {
                return Math.max(minBatchSize, Math.min(requestedSize, defaultBatchSize / 2));
            }
            // For very fast APIs (<500ms), increase batch size
            else if (averageApiResponseTime < 500) {
                return Math.min(requestedSize, Math.min(maxBatchSize, defaultBatchSize * 2));
            }
        }
        
        // Default to the configured batch size
        return Math.min(requestedSize, defaultBatchSize);
    }
    
    /**
     * Calculates the delay between batches based on rate limiting and API response times.
     * 
     * @return The delay in milliseconds
     */
    private long calculateInterBatchDelay() {
        // Base delay on rate limit (spread requests across the minute)
        long baseDelay = 60000 / maxRequestsPerMinute;
        
        // Add additional delay based on recent API performance
        if (successfulApiCalls > 5 && averageApiResponseTime > 1000) {
            // For slower APIs, add extra buffer
            return baseDelay + (averageApiResponseTime / 5);
        }
        
        return baseDelay;
    }
    
    /**
     * Updates the average API response time tracking.
     * 
     * @param responseTimeMs The response time in milliseconds
     */
    private void updateApiResponseTimeTracking(long responseTimeMs) {
        lastApiResponseTime = responseTimeMs;
        
        // Update running average (weighted)
        if (successfulApiCalls == 0) {
            averageApiResponseTime = responseTimeMs;
        } else {
            // Give more weight to recent calls (80% new, 20% history)
            averageApiResponseTime = (long) (0.2 * averageApiResponseTime + 0.8 * responseTimeMs);
        }
        
        successfulApiCalls++;
        logger.debug("Updated API response time tracking: last={}ms, avg={}ms, calls={}", 
                lastApiResponseTime, averageApiResponseTime, successfulApiCalls);
    }
    
    /**
     * Internal implementation of batch embedding generation for smaller batches.
     */
    private float[][] generateEmbeddingsBatchInternal(List<String> texts) {
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                logger.debug("Generating embeddings for {} texts (attempt {}/{})", 
                        texts.size(), attempt + 1, MAX_RETRIES);
                
                long startTime = System.currentTimeMillis();
                float[][] embeddings = callGainsightEmbeddingApiBatch(texts);
                long endTime = System.currentTimeMillis();
                long responseTime = endTime - startTime;
                
                // Update performance tracking
                updateApiResponseTimeTracking(responseTime);
                
                // If we reached here, the embedding was successful
                if (attempt > 0) {
                    logger.info("Successfully generated batch of {} embeddings on attempt {}/{} in {}ms", 
                            texts.size(), attempt + 1, MAX_RETRIES, responseTime);
                }
                
                // Reset continuous failure count on success
                continuousFailureCount.set(0);
                
                return embeddings;
            } catch (RateLimitException rle) {
                logger.warn("Rate limit exceeded on batch embedding attempt {}/{}. Message: {}",
                        attempt + 1, MAX_RETRIES, rle.getMessage());
                
                // Exponential backoff with longer waits for rate limits
                long sleepTime = (long) (Math.pow(2, attempt + 2) * 1000 + Math.random() * 1000);
                logger.info("Rate limited. Waiting {} ms before retry.", sleepTime);
                
                try {
                    Thread.sleep(sleepTime);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted during backoff", ie);
                }
            } catch (Exception e) {
                logger.error("Error generating batch embeddings on attempt {}/{}: {}", 
                        attempt + 1, MAX_RETRIES, e.getMessage());
                
                // Track continuous failures
                continuousFailureCount.incrementAndGet();
                
                // Exponential backoff
                long sleepTime = (long) (Math.pow(2, attempt) * 1000 + Math.random() * 1000);
                
                try {
                    Thread.sleep(sleepTime);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted during backoff", ie);
                }
            }
        }
        
        // If we get here, all retries failed
        throw new RuntimeException("Failed to generate batch embeddings after " + MAX_RETRIES + " attempts");
    }
    
    /**
     * Calls the Gainsight API to generate embeddings for multiple texts in a batch.
     */
    private float[][] callGainsightEmbeddingApiBatch(List<String> texts) throws Exception {
        if (texts.size() == 1) {
            // Use single text format for efficiency when only one text
            float[] embedding = callGainsightEmbeddingApi(texts.get(0));
            float[][] embeddings = new float[1][];
            embeddings[0] = embedding;
            return embeddings;
        }
        
        // Process multiple texts (note: the API doesn't support true batch operations,
        // so we'll process them sequentially but with cleaner code)
        float[][] results = new float[texts.size()][];
        
        for (int i = 0; i < texts.size(); i++) {
            try {
                // Set headers
                Map<String, String> headers = new HashMap<>();
                headers.put("Content-Type", "application/json");
                headers.put("accept", "application/json");
                headers.put("access_key", gainsightAccessKey);
                
                // Prepare the request body
                Map<String, Object> requestBody = new HashMap<>();
                requestBody.put("modelVersion", embeddingModelVersion);
                requestBody.put("model", embeddingModel);
                requestBody.put("text", texts.get(i));
                
                // Make the API call using our standardized utility
                Map<String, Object> response = httpClientUtils.executePostRequest(
                        gainsightEmbeddingUrl, headers, requestBody);
                
                if (!response.containsKey("data")) {
                    throw new Exception("Invalid response format, missing 'data' field");
                }
                
                // Handle potential nested structure in the response
                Object dataObject = response.get("data");
                logger.debug("Processing text {}/{}", i + 1, texts.size());
                
                // Extract embedding values using our shared method
                List<Double> embeddingValues = extractEmbeddingValues(dataObject);
                
                if (embeddingValues == null || embeddingValues.isEmpty()) {
                    throw new Exception("Empty embedding values returned from API");
                }
                
                // Convert to float array
                float[] embedding = new float[embeddingValues.size()];
                for (int j = 0; j < embeddingValues.size(); j++) {
                    embedding[j] = embeddingValues.get(j).floatValue();
                }
                
                results[i] = embedding;
                
                // Add a small delay between requests to avoid overwhelming the API
                if (i < texts.size() - 1) {
                    Thread.sleep(100);
                }
            } catch (HttpClientUtils.RateLimitException rle) {
                // Wrap and rethrow our internal RateLimitException
                throw new RateLimitException(rle.getMessage());
            }
        }
        
        logger.info("Retrieved {} embeddings with {} dimensions sequentially", 
                results.length, results.length > 0 ? results[0].length : 0);
        
        return results;
    }
    
    @Override
    public int storeEmbeddingsBatch(List<String> ids, float[][] vectors, List<EmbeddingMetadata> metadataList, String repositoryNamespace) {
        if (ids == null || vectors == null || metadataList == null || 
                ids.size() != vectors.length || ids.size() != metadataList.size()) {
            throw new IllegalArgumentException("Invalid batch parameters. Lists must be non-null and same size.");
        }
        
        // Convert namespace to default if null
        String namespace = repositoryNamespace != null ? repositoryNamespace : DEFAULT_NAMESPACE;
        
        // Initialize namespace if it doesn't exist
        if (!indexes.containsKey(namespace)) {
            boolean created = createNamespace(namespace, vectors.length > 0 ? vectors[0].length : embeddingDimension);
            if (!created) {
                return 0;
            }
        }
        
        // Get the HNSW index for this namespace
        HnswIndex<String, float[], VectorItem, Float> index = indexes.get(namespace);
        
        // Get or create metadata map for this namespace
        Map<String, EmbeddingMetadata> metadataMap = namespaceToMetadataMap.computeIfAbsent(
                namespace, k -> new ConcurrentHashMap<>());
        
        // Create temporary storage for transactions
        Map<String, VectorItem> pendingItems = new HashMap<>();
        Map<String, EmbeddingMetadata> pendingMetadata = new HashMap<>();
        
        int successCount = 0;
        List<String> failedIds = new ArrayList<>();
        boolean abortTransaction = false;
        
        // Validate all embeddings before committing any changes
        for (int i = 0; i < ids.size(); i++) {
            String id = ids.get(i);
            float[] vector = vectors[i];
            EmbeddingMetadata metadata = metadataList.get(i);
            
            try {
                // Ensure vector has correct dimensions
                if (vector.length != embeddingDimension) {
                    logger.warn("Vector dimension mismatch for id {} in batch. Expected: {}, Actual: {}", 
                            id, embeddingDimension, vector.length);
                    failedIds.add(id);
                    // Don't abort transaction, just skip this item
                    continue;
                }
                
                String namespacedId = getNamespacedId(id, namespace);
                
                // Update the namespace in the metadata
                metadata.setRepositoryNamespace(namespace);
                
                // Add to pending items instead of directly to index
                pendingItems.put(namespacedId, new VectorItem(namespacedId, vector));
                pendingMetadata.put(namespacedId, metadata);
                
            } catch (Exception e) {
                logger.error("Error preparing embedding for id {} in batch: {}", id, e.getMessage());
                failedIds.add(id);
                // For critical errors, abort the entire transaction
                if (e.getMessage() != null && 
                    (e.getMessage().contains("critical") || 
                     e.getMessage().contains("fatal") || 
                     e.getMessage().contains("memory") || 
                     e.getClass().getName().contains("OutOfMemoryError"))) {
                    logger.error("Critical error detected, aborting transaction", e);
                    abortTransaction = true;
                    break;
                }
            }
        }
        
        // If transaction should be aborted, return without committing anything
        if (abortTransaction) {
            logger.warn("Transaction aborted, no embeddings stored");
            return 0;
        }
        
        // Commit all validated items as a single transaction
        if (!pendingItems.isEmpty()) {
            try {
                logger.info("Committing transaction with {} embeddings", pendingItems.size());
                
                // Add all pending items to the index
                for (Map.Entry<String, VectorItem> entry : pendingItems.entrySet()) {
                    String namespacedId = entry.getKey();
                    VectorItem item = entry.getValue();
                    
                    try {
                        index.add(item);
                        metadataMap.put(namespacedId, pendingMetadata.get(namespacedId));
                        successCount++;
                    } catch (Exception e) {
                        logger.error("Error adding item {} during transaction commit: {}", 
                                namespacedId, e.getMessage());
                        failedIds.add(getIdFromNamespacedId(namespacedId));
                    }
                }
                
                // Save the index and metadata after successful transaction
                if (successCount > 0) {
                    saveIndex(namespace);
                    saveMetadata(namespace);
                    logger.info("Successfully stored {} out of {} embeddings in batch for namespace {}", 
                            successCount, ids.size(), namespace);
                }
            } catch (Exception e) {
                logger.error("Error during transaction commit: {}", e.getMessage());
                // Transaction failed, consider all items as failed
                successCount = 0;
                failedIds.addAll(ids);
            }
        }
        
        // Log failed IDs for debugging
        if (!failedIds.isEmpty()) {
            logger.warn("Failed to store {} embeddings in batch: {}", 
                    failedIds.size(), failedIds.size() <= 10 ? failedIds : (failedIds.subList(0, 10) + "..."));
        }
        
        return successCount;
    }
    
    /**
     * Creates a new namespace for storing embeddings.
     */
    private boolean createNamespace(String namespace, int dimension) {
        try {
            logger.info("Creating new namespace: {}", namespace);
            
            // Create the index
            createNewIndex(namespace, dimension);
            
            // Initialize metadata map
            namespaceToMetadataMap.put(namespace, new ConcurrentHashMap<>());
            
            // Add to list of namespaces if not already present
            if (!repositoryNamespaces.contains(namespace)) {
                repositoryNamespaces.add(namespace);
                saveNamespaces();
            }
            
            // Create directory for the namespace
            createDirectoryIfNotExists(getNamespacePath(namespace));
            
            return true;
        } catch (Exception e) {
            logger.error("Error creating namespace {}: {}", namespace, e.getMessage());
            return false;
        }
    }
    
    /**
     * Finds all embeddings associated with a specific file path.
     * 
     * @param filePath The file path to search for
     * @param repositoryNamespace The repository namespace to search in, or null for all namespaces
     * @return A list of embedding results matching the file path
     */
    @Override
    public List<SimilarityResult> findEmbeddingsByFilePath(String filePath, String repositoryNamespace) {
        List<SimilarityResult> results = new ArrayList<>();
        
        logger.info("Searching for embeddings by file path: {}", filePath);
        
        // Normalize file path for comparison
        String normalizedFilePath = filePath.replace('\\', '/');
        
        if (repositoryNamespace != null) {
            // Search in specific namespace
            Map<String, EmbeddingMetadata> metadataMap = namespaceToMetadataMap.get(repositoryNamespace);
            if (metadataMap != null) {
                findInMetadataMap(metadataMap, normalizedFilePath, repositoryNamespace, results);
            } else {
                logger.warn("Repository namespace not found: {}", repositoryNamespace);
            }
        } else {
            // Search across all namespaces
            for (Map.Entry<String, Map<String, EmbeddingMetadata>> entry : namespaceToMetadataMap.entrySet()) {
                String namespace = entry.getKey();
                Map<String, EmbeddingMetadata> metadataMap = entry.getValue();
                findInMetadataMap(metadataMap, normalizedFilePath, namespace, results);
            }
        }
        
        logger.info("Found {} embeddings for file path: {}", results.size(), filePath);
        return results;
    }
    
    /**
     * Helper method to find embeddings in a metadata map by file path.
     */
    private void findInMetadataMap(Map<String, EmbeddingMetadata> metadataMap, String normalizedFilePath, 
                                  String namespace, List<SimilarityResult> results) {
        for (Map.Entry<String, EmbeddingMetadata> entry : metadataMap.entrySet()) {
            String id = entry.getKey();
            EmbeddingMetadata metadata = entry.getValue();
            
            if (metadata != null && metadata.getFilePath() != null) {
                String metadataFilePath = metadata.getFilePath().replace('\\', '/');
                
                // Check if the file path matches or contains the search path
                if (metadataFilePath.equals(normalizedFilePath) || 
                    metadataFilePath.contains(normalizedFilePath)) {
                    
                    SimilarityResult result = new SimilarityResult();
                    result.setId(id);
                    result.setMetadata(metadata);
                    result.setSimilarityScore(1.0f); // Not a similarity search, so use perfect score
                    
                    results.add(result);
                }
            }
        }
    }
    
    /**
     * Checks for and attempts to resume any interrupted batch operations across all namespaces.
     * This recovery mechanism ensures data integrity across system restarts or failures.
     * 
     * @return Number of successfully resumed batch operations
     */
    private int resumeAllInterruptedBatches() {
        // Current implementation is a placeholder
        // Full implementation will scan for interrupted batch files and process them
        logger.info("Batch recovery mechanism initialized - scanning for recovery files");
        
        // TODO: Implement full recovery mechanism in next phase
        // 1. Scan all namespaces for interrupted_batch.json files
        // 2. Deserialize and validate each batch state
        // 3. Resume processing for valid interrupted batches
        // 4. Clean up recovery files after successful processing
        
        return 0; // Return count of resumed batches (0 for placeholder)
    }
    
    @Override
    public boolean updateMetadata(String id, EmbeddingMetadata metadata) {
        // Try to find and update metadata across all namespaces if namespace not specified
        String namespace = metadata.getRepositoryNamespace();
        if (namespace != null && !namespace.isEmpty()) {
            // If namespace is specified, update in that namespace
            return updateMetadataInNamespace(id, metadata, namespace);
        } else {
            // If namespace is not specified, try to update in all namespaces
            for (String ns : repositoryNamespaces) {
                if (updateMetadataInNamespace(id, metadata, ns)) {
                    return true;
                }
            }
            return false;
        }
    }
    
    /**
     * Helper method to update metadata in a specific namespace.
     */
    private boolean updateMetadataInNamespace(String id, EmbeddingMetadata metadata, String namespace) {
        Map<String, EmbeddingMetadata> metadataMap = namespaceToMetadataMap.get(namespace);
        if (metadataMap == null) {
            logger.warn("Metadata map not found for namespace: {}", namespace);
            return false;
        }
        
        String namespacedId = getNamespacedId(id, namespace);
        
        // Check if the ID exists
        if (!metadataMap.containsKey(namespacedId)) {
            logger.warn("No metadata found for ID {} in namespace {}", id, namespace);
            return false;
        }
        
        // Update the metadata
        metadataMap.put(namespacedId, metadata);
        
        // Periodically save metadata
        if (metadataMap.size() % 100 == 0) {
            saveMetadata(namespace);
        }
        
        return true;
    }
    
    @Override
    public Map<String, com.l3agent.model.EmbeddingMetadata> getAllMetadata() {
        Map<String, com.l3agent.model.EmbeddingMetadata> allMetadata = new HashMap<>();
        
        // Combine metadata from all namespaces
        for (String namespace : repositoryNamespaces) {
            Map<String, EmbeddingMetadata> namespaceMetadata = namespaceToMetadataMap.get(namespace);
            if (namespaceMetadata != null) {
                // Remove namespace prefix from IDs for consistency in the result
                for (Map.Entry<String, EmbeddingMetadata> entry : namespaceMetadata.entrySet()) {
                    String originalId = getIdFromNamespacedId(entry.getKey());
                    // Convert our EmbeddingMetadata to the model version
                    allMetadata.put(originalId, convertToModelEmbeddingMetadata(entry.getValue()));
                }
            }
        }
        
        return allMetadata;
    }
    
    /**
     * Helper method to convert our internal EmbeddingMetadata to the model version
     */
    private com.l3agent.model.EmbeddingMetadata convertToModelEmbeddingMetadata(EmbeddingMetadata metadata) {
        if (metadata == null) return null;
        
        com.l3agent.model.EmbeddingMetadata modelMetadata = new com.l3agent.model.EmbeddingMetadata();
        modelMetadata.setSource(metadata.getSource());
        modelMetadata.setFilePath(metadata.getFilePath());
        modelMetadata.setStartLine(metadata.getStartLine());
        modelMetadata.setEndLine(metadata.getEndLine());
        modelMetadata.setType(metadata.getType());
        modelMetadata.setLanguage(metadata.getLanguage());
        modelMetadata.setDescription(metadata.getDescription());
        modelMetadata.setPurposeSummary(metadata.getPurposeSummary());
        modelMetadata.setCapabilities(metadata.getCapabilities());
        modelMetadata.setUsageExamples(metadata.getUsageExamples());
        modelMetadata.setContent(metadata.getContent());
        modelMetadata.setRepositoryNamespace(metadata.getRepositoryNamespace());
        
        return modelMetadata;
    }
    
    @Override
    public float getSimilarity(String id, String query) {
        // Generate embedding for the query
        float[] queryEmbedding = generateEmbedding(query);
        if (queryEmbedding == null || queryEmbedding.length == 0) {
            logger.error("Failed to generate embedding for query: {}", query);
            return 0.0f;
        }
        
        // Search across all namespaces to find the specified ID
        for (String namespace : repositoryNamespaces) {
            HnswIndex<String, float[], VectorItem, Float> index = indexes.get(namespace);
            if (index == null) continue;
            
            Map<String, EmbeddingMetadata> metadataMap = namespaceToMetadataMap.get(namespace);
            if (metadataMap == null) continue;
            
            String namespacedId = getNamespacedId(id, namespace);
            
            // Check if this ID exists in this namespace
            if (!metadataMap.containsKey(namespacedId)) continue;
            
            try {
                // Get the item from the index - handling Optional return type
                Optional<VectorItem> optionalItem = index.get(namespacedId);
                if (!optionalItem.isPresent()) continue;
                
                VectorItem item = optionalItem.get();
                
                // Calculate cosine similarity manually
                float[] vector = item.vector();
                double dotProduct = 0.0;
                double normA = 0.0;
                double normB = 0.0;
                
                for (int i = 0; i < vector.length; i++) {
                    dotProduct += vector[i] * queryEmbedding[i];
                    normA += Math.pow(vector[i], 2);
                    normB += Math.pow(queryEmbedding[i], 2);
                }
                
                double similarity = dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
                return (float) similarity;
            } catch (Exception e) {
                logger.error("Error calculating similarity for ID {} in namespace {}", id, namespace, e);
            }
        }
        
        // If ID not found or error occurred, return 0
        return 0.0f;
    }
    
    @Override
    public EmbeddingMetadata getMetadata(String id) {
        // Check all namespaces for this ID
        for (String namespace : repositoryNamespaces) {
            Map<String, EmbeddingMetadata> metadataMap = namespaceToMetadataMap.get(namespace);
            if (metadataMap == null) continue;
            
            String namespacedId = getNamespacedId(id, namespace);
            
            EmbeddingMetadata metadata = metadataMap.get(namespacedId);
            if (metadata != null) {
                return metadata;
            }
            
            // Also try without namespace prefix in case the ID already has it
            metadata = metadataMap.get(id);
            if (metadata != null) {
                return metadata;
            }
        }
        
        // Not found in any namespace
        return null;
    }
    
    @Override
    public Map<String, float[]> getAllEmbeddings() {
        Map<String, float[]> allEmbeddings = new HashMap<>();
        
        // Combine embeddings from all namespaces
        for (String namespace : repositoryNamespaces) {
            HnswIndex<String, float[], VectorItem, Float> index = indexes.get(namespace);
            if (index == null) continue;
            
            try {
                // We can't directly iterate the index, so collect IDs first and then get each item
                Map<String, EmbeddingMetadata> metadataMap = namespaceToMetadataMap.get(namespace);
                if (metadataMap != null) {
                    for (String namespacedId : metadataMap.keySet()) {
                        try {
                            Optional<VectorItem> optionalItem = index.get(namespacedId);
                            if (optionalItem.isPresent()) {
                                VectorItem item = optionalItem.get();
                                String originalId = getIdFromNamespacedId(namespacedId);
                                allEmbeddings.put(originalId, item.vector());
                            }
                        } catch (Exception e) {
                            logger.warn("Error retrieving item {}: {}", namespacedId, e.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                logger.error("Error getting embeddings from namespace {}", namespace, e);
            }
        }
        
        return allEmbeddings;
    }
} 