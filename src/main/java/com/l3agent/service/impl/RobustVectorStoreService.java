package com.l3agent.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.l3agent.service.VectorStoreService;
import com.l3agent.util.LoggingUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Qualifier;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A robust implementation of the VectorStoreService that separates vector storage from indexing.
 * This implementation provides reliable persistence by storing vectors in JSON format and rebuilding
 * indexes in memory on startup.
 */
@Service
@Primary
public class RobustVectorStoreService implements VectorStoreService {
    private static final Logger logger = LoggerFactory.getLogger(RobustVectorStoreService.class);
    
    private static final String DEFAULT_NAMESPACE = "default";
    private static final String METADATA_FILE_NAME = "embedding_metadata.json";
    private static final String FAILURES_FILE_NAME = "embedding_failures.json";
    private static final String NAMESPACES_FILE_NAME = "namespaces.json";
    private static final int MAX_RETRIES = 3;
    private static final int MAX_CONTINUOUS_FAILURES = 5;
    
    private final String dataDir;
    private final String embeddingEndpoint;
    private final int embeddingDimension;
    private final int batchSize;
    private final String gainsightApiKey;
    private final ObjectMapper objectMapper;
    private final JsonVectorStorage vectorStorage;
    private final CloseableHttpClient httpClient;
    
    // Maps namespace to its in-memory index
    private final Map<String, InMemoryHnswIndex> indexes = new ConcurrentHashMap<>();
    
    // Maps namespace to its metadata map
    private final Map<String, Map<String, EmbeddingMetadata>> namespaceToMetadataMap = new ConcurrentHashMap<>();
    
    private final Map<String, EmbeddingFailure> failureMap = new ConcurrentHashMap<>();
    private final List<String> repositoryNamespaces = new ArrayList<>();
    
    // Track continuous failures to allow for early termination
    private final AtomicInteger continuousFailureCount = new AtomicInteger(0);
    
    @Autowired
    public RobustVectorStoreService(
            @Value("${l3agent.vector-store.data-dir:./data/vector-store}") String dataDir,
            @Qualifier("embeddingUrl") String embeddingEndpoint,
            @Value("${l3agent.vector-store.embedding-dimension:3072}") int embeddingDimension,
            @Value("${l3agent.vector-store.batch-size:10}") int batchSize,
            @Value("${l3agent.llm.gainsight.access-key:}") String gainsightApiKey,
            ObjectMapper objectMapper,
            JsonVectorStorage vectorStorage,
            CloseableHttpClient httpClient) {
        this.dataDir = dataDir;
        this.embeddingEndpoint = embeddingEndpoint;
        this.embeddingDimension = embeddingDimension;
        this.batchSize = batchSize;
        this.gainsightApiKey = gainsightApiKey;
        this.objectMapper = objectMapper;
        this.vectorStorage = vectorStorage;
        this.httpClient = httpClient;
    }
    
    @PostConstruct
    public void initialize() {
        long startTime = System.currentTimeMillis();
        logger.info("Initializing RobustVectorStoreService with storage path: {}", dataDir);
        
        // Create data directory if it doesn't exist
        File dataDirFile = new File(dataDir);
        if (!dataDirFile.exists()) {
            logger.info("Vector store directory does not exist, creating: {}", dataDir);
            if (dataDirFile.mkdirs()) {
                logger.info("Successfully created vector store directory");
            } else {
                logger.error("Failed to create vector store directory: {}", dataDir);
            }
        } else {
            logger.info("Vector store directory exists: {}", dataDir);
        }
        
        // Load namespaces
        loadNamespaces();
        
        // Initialize namespaces
        logger.info("Beginning to initialize {} namespaces", repositoryNamespaces.size());
        int totalEmbeddingCount = 0;
        
        for (String namespace : repositoryNamespaces) {
            long namespaceStartTime = System.currentTimeMillis();
            logger.info("Initializing namespace: {}", namespace);
            
            int embeddingsInNamespace = initializeNamespace(namespace);
            totalEmbeddingCount += embeddingsInNamespace;
            
            long namespaceElapsed = System.currentTimeMillis() - namespaceStartTime;
            logger.info("Namespace {} initialization completed in {}ms", namespace, namespaceElapsed);
        }
        
        // Load embedding failures
        loadEmbeddingFailures();
        
        long totalElapsed = System.currentTimeMillis() - startTime;
        Runtime runtime = Runtime.getRuntime();
        long usedMemoryMB = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        
        logger.info("RobustVectorStoreService initialized with {} namespaces, {} total embeddings, {} embedding failures in {}ms",
                    repositoryNamespaces.size(), totalEmbeddingCount, failureMap.size(), totalElapsed);
        logger.info("Vector store memory usage: {}MB", usedMemoryMB);
    }
    
    @PreDestroy
    public void shutdown() {
        logger.info("Shutting down RobustVectorStoreService...");
        
        // Save all metadata and namespaces
        for (String namespace : repositoryNamespaces) {
            saveMetadata(namespace);
        }
        
        saveNamespaces();
        saveEmbeddingFailures();
    }
    
    /**
     * Load repository namespaces from file.
     */
    private void loadNamespaces() {
        File namespacesFile = new File(dataDir, NAMESPACES_FILE_NAME);
        if (namespacesFile.exists()) {
            try {
                long fileSize = namespacesFile.length();
                logger.info("Loading namespaces from file: {} (size: {} bytes)", namespacesFile.getAbsolutePath(), fileSize);
                
                String[] namespaces = objectMapper.readValue(namespacesFile, String[].class);
                repositoryNamespaces.clear();
                Collections.addAll(repositoryNamespaces, namespaces);
                logger.info("Loaded {} repository namespaces: {}", repositoryNamespaces.size(), 
                           String.join(", ", repositoryNamespaces));
            } catch (IOException e) {
                logger.error("Error loading namespaces from {}: {}", namespacesFile.getAbsolutePath(), e.getMessage(), e);
            }
        } else {
            logger.info("Namespaces file not found at {}, initializing with default namespace", namespacesFile.getAbsolutePath());
            // Add default namespace if not present
            repositoryNamespaces.add(DEFAULT_NAMESPACE);
        }
        
        // Add any existing directories as namespaces
        File[] subdirs = new File(dataDir).listFiles(File::isDirectory);
        if (subdirs != null) {
            for (File subdir : subdirs) {
                String namespace = subdir.getName();
                if (!repositoryNamespaces.contains(namespace)) {
                    logger.info("Found additional namespace directory: {}", namespace);
                    repositoryNamespaces.add(namespace);
                }
            }
        } else {
            logger.warn("Unable to list subdirectories in {}", dataDir);
        }
    }
    
    /**
     * Save repository namespaces to file.
     */
    private void saveNamespaces() {
        File namespacesFile = new File(dataDir, NAMESPACES_FILE_NAME);
        try {
            objectMapper.writeValue(namespacesFile, repositoryNamespaces.toArray(new String[0]));
            logger.info("Saved {} repository namespaces", repositoryNamespaces.size());
        } catch (IOException e) {
            logger.error("Error saving namespaces", e);
        }
    }
    
    /**
     * Load embedding failures from file.
     */
    private void loadEmbeddingFailures() {
        File failuresFile = new File(dataDir, FAILURES_FILE_NAME);
        if (failuresFile.exists()) {
            try {
                EmbeddingFailure[] failures = objectMapper.readValue(failuresFile, EmbeddingFailure[].class);
                failureMap.clear();
                for (EmbeddingFailure failure : failures) {
                    failureMap.put(failure.getTextHash(), failure);
                }
                logger.info("Loaded {} embedding failures", failureMap.size());
            } catch (IOException e) {
                logger.error("Error loading embedding failures", e);
            }
        }
    }
    
    /**
     * Save embedding failures to file.
     */
    private void saveEmbeddingFailures() {
        File failuresFile = new File(dataDir, FAILURES_FILE_NAME);
        try {
            objectMapper.writeValue(failuresFile, failureMap.values().toArray());
            logger.info("Saved {} embedding failures", failureMap.size());
        } catch (IOException e) {
            logger.error("Error saving embedding failures", e);
        }
    }
    
    /**
     * Initialize a namespace, loading its metadata and rebuilding its index.
     */
    private int initializeNamespace(String namespace) {
        String namespacePath = getNamespacePath(namespace);
        File metadataFile = new File(namespacePath, METADATA_FILE_NAME);
        
        logger.info("Initializing namespace {} with path: {}", namespace, namespacePath);
        
        // Create namespace directory if it doesn't exist
        File namespaceDirFile = new File(namespacePath);
        if (!namespaceDirFile.exists()) {
            logger.info("Namespace directory does not exist, creating: {}", namespacePath);
            if (!namespaceDirFile.mkdirs()) {
                logger.error("Failed to create namespace directory: {}", namespacePath);
            }
        }
        
        // Initialize metadata map for namespace
        Map<String, EmbeddingMetadata> metadataMap = new ConcurrentHashMap<>();
        namespaceToMetadataMap.put(namespace, metadataMap);
        
        // Load metadata if it exists
        if (metadataFile.exists()) {
            logger.info("Found metadata file for namespace {}: {} (size: {} bytes)", 
                      namespace, metadataFile.getAbsolutePath(), metadataFile.length());
            loadMetadata(namespace, metadataFile);
        } else {
            logger.info("No metadata file found for namespace {}", namespace);
        }
        
        // Create an in-memory index for this namespace
        int initialCapacity = Math.max(10000, metadataMap.size() * 2);
        logger.info("Creating in-memory HNSW index for namespace {} with initial capacity: {}", 
                   namespace, initialCapacity);
                   
        InMemoryHnswIndex index = new InMemoryHnswIndex(embeddingDimension, initialCapacity);
        indexes.put(namespace, index);
        
        // Build the index from vector storage
        logger.info("Building index from storage for namespace {}", namespace);
        long buildStartTime = System.currentTimeMillis();
        index.buildIndex(vectorStorage, namespace);
        long buildTime = System.currentTimeMillis() - buildStartTime;
        
        logger.info("Initialized namespace {} with {} metadata entries and {} indexed vectors in {}ms",
                   namespace, metadataMap.size(), index.size(), buildTime);
                   
        // Validate index and metadata consistency
        if (metadataMap.size() != index.size()) {
            logger.warn("Inconsistency detected in namespace {}: metadata count ({}) differs from index size ({})",
                       namespace, metadataMap.size(), index.size());
        }
        
        return metadataMap.size();
    }
    
    /**
     * Load metadata from file.
     */
    private void loadMetadata(String namespace, File metadataFile) {
        try {
            long startTime = System.currentTimeMillis();
            logger.info("Loading metadata for namespace {} from {}", namespace, metadataFile.getAbsolutePath());
            
            Map<String, EmbeddingMetadata> metadataMap = namespaceToMetadataMap.get(namespace);
            if (metadataMap == null) {
                metadataMap = new ConcurrentHashMap<>();
                namespaceToMetadataMap.put(namespace, metadataMap);
            }
            
            EmbeddingMetadata[] metadataArray = objectMapper.readValue(metadataFile, EmbeddingMetadata[].class);
            
            if (metadataArray != null) {
                int validCount = 0;
                int invalidCount = 0;
                
                for (EmbeddingMetadata metadata : metadataArray) {
                    if (metadata != null && metadata.getSource() != null) {
                        // Check if vector file exists
                        boolean vectorExists = vectorStorage.vectorExists(metadata.getSource(), namespace);
                        if (vectorExists) {
                            metadataMap.put(metadata.getSource(), metadata);
                            validCount++;
                        } else {
                            logger.warn("Metadata entry {} has no corresponding vector file in namespace {}", 
                                      metadata.getSource(), namespace);
                            invalidCount++;
                        }
                    } else {
                        invalidCount++;
                    }
                }
                
                long elapsedTime = System.currentTimeMillis() - startTime;
                logger.info("Loaded metadata for namespace {}: {} valid entries, {} invalid entries in {}ms", 
                          namespace, validCount, invalidCount, elapsedTime);
            } else {
                logger.warn("No metadata entries found in file for namespace {}", namespace);
            }
        } catch (IOException e) {
            logger.error("Error loading metadata for namespace {} from {}: {}", 
                       namespace, metadataFile.getAbsolutePath(), e.getMessage(), e);
        }
    }
    
    /**
     * Save metadata to file.
     */
    private void saveMetadata(String namespace) {
        try {
            Map<String, EmbeddingMetadata> metadataMap = namespaceToMetadataMap.get(namespace);
            if (metadataMap == null || metadataMap.isEmpty()) {
                logger.warn("No metadata to save for namespace {}", namespace);
                return;
            }
            
            String namespacePath = getNamespacePath(namespace);
            File metadataFile = new File(namespacePath, METADATA_FILE_NAME);
            
            objectMapper.writeValue(metadataFile, metadataMap.values().toArray());
            
            logger.info("Saved metadata for {} embeddings in namespace {}",
                       metadataMap.size(), namespace);
        } catch (IOException e) {
            logger.error("Error saving metadata for namespace {}", namespace, e);
        }
    }
    
    /**
     * Create a new namespace.
     */
    private boolean createNamespace(String namespace) {
        return createNamespace(namespace, embeddingDimension);
    }
    
    /**
     * Create a new namespace with specified dimensions.
     */
    private boolean createNamespace(String namespace, int dimensions) {
        if (repositoryNamespaces.contains(namespace)) {
            logger.info("Namespace {} already exists", namespace);
            return true;
        }
        
        try {
            // Create namespace directory
            String namespacePath = getNamespacePath(namespace);
            Files.createDirectories(Paths.get(namespacePath));
            
            // Create an empty metadata map
            namespaceToMetadataMap.put(namespace, new ConcurrentHashMap<>());
            
            // Create an empty index
            InMemoryHnswIndex index = new InMemoryHnswIndex(dimensions, 10000);
            indexes.put(namespace, index);
            
            // Add to namespaces list and save
            repositoryNamespaces.add(namespace);
            saveNamespaces();
            
            logger.info("Created new namespace: {}", namespace);
            return true;
        } catch (Exception e) {
            logger.error("Error creating namespace {}", namespace, e);
            return false;
        }
    }
    
    /**
     * Get the namespaces path.
     */
    private String getNamespacePath(String namespace) {
        return dataDir + File.separator + namespace;
    }
    
    /**
     * Store an embedding in the default namespace.
     */
    @Override
    public boolean storeEmbedding(String id, float[] vector, EmbeddingMetadata metadata) {
        return storeEmbedding(id, vector, metadata, DEFAULT_NAMESPACE);
    }
    
    /**
     * Store an embedding in a specific namespace.
     */
    @Override
    public boolean storeEmbedding(String id, float[] vector, EmbeddingMetadata metadata, String repositoryNamespace) {
        String namespace = repositoryNamespace != null ? repositoryNamespace : DEFAULT_NAMESPACE;
        
        // Ensure namespace exists
        if (!repositoryNamespaces.contains(namespace)) {
            boolean created = createNamespace(namespace, vector.length);
            if (!created) {
                logger.error("Failed to create namespace {}", namespace);
                return false;
            }
        }
        
        try {
            // Set the repository namespace in metadata
            metadata.setRepositoryNamespace(namespace);
            
            // Get or create the metadata map for this namespace
            Map<String, EmbeddingMetadata> metadataMap = namespaceToMetadataMap.computeIfAbsent(
                    namespace, k -> new ConcurrentHashMap<>());
            
            // Store the embedding in the JSON storage
            boolean storedVector = vectorStorage.storeVector(id, vector, namespace);
            if (!storedVector) {
                logger.error("Failed to store vector for ID {} in namespace {}", id, namespace);
                return false;
            }
            
            // Store the metadata
            metadataMap.put(id, metadata);
            
            // Update the in-memory index
            InMemoryHnswIndex index = indexes.get(namespace);
            if (index != null) {
                boolean addedToIndex = index.add(id, vector);
                if (!addedToIndex) {
                    logger.warn("Failed to add vector to index for ID {} in namespace {}", id, namespace);
                    // Continue anyway as the vector is stored in JSON
                }
            } else {
                logger.warn("Index not found for namespace {}", namespace);
                // Create a new index
                InMemoryHnswIndex newIndex = new InMemoryHnswIndex(vector.length, 10000);
                newIndex.add(id, vector);
                indexes.put(namespace, newIndex);
            }
            
            // Periodically save metadata (every 100 embeddings)
            if (metadataMap.size() % 100 == 0) {
                saveMetadata(namespace);
            }
            
            // Reset continuous failure count on success
            continuousFailureCount.set(0);
            
            return true;
        } catch (Exception e) {
            logger.error("Error storing embedding in namespace {}", namespace, e);
            return false;
        }
    }
    
    /**
     * Find similar embeddings across all namespaces.
     */
    @Override
    public List<SimilarityResult> findSimilar(float[] queryVector, int maxResults, float minSimilarity) {
        return findSimilar(queryVector, maxResults, minSimilarity, Collections.emptyList());
    }
    
    /**
     * Find similar embeddings in specific namespaces.
     */
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
            InMemoryHnswIndex index = indexes.get(namespace);
            if (index == null) {
                logger.warn("Index not found for namespace: {}", namespace);
                continue;
            }
            
            // Get the metadata map for this namespace
            Map<String, EmbeddingMetadata> metadataMap = namespaceToMetadataMap.get(namespace);
            if (metadataMap == null) {
                logger.warn("Metadata map not found for namespace: {}", namespace);
                continue;
            }
            
            try {
                // Search this namespace
                List<InMemoryHnswIndex.SearchResult> searchResults = 
                        index.findSimilar(queryVector, maxResults, minSimilarity);
                
                // Process results
                for (InMemoryHnswIndex.SearchResult result : searchResults) {
                    String id = result.getId();
                    float similarity = result.getSimilarity();
                    
                    // Get metadata
                    EmbeddingMetadata metadata = metadataMap.get(id);
                    if (metadata == null) {
                        logger.warn("Metadata not found for ID {} in namespace {}", id, namespace);
                        continue;
                    }
                    
                    // Create similarity result
                    SimilarityResult similarityResult = new SimilarityResult();
                    similarityResult.setId(id);
                    similarityResult.setMetadata(metadata);
                    similarityResult.setSimilarityScore(similarity);
                    allResults.add(similarityResult);
                }
            } catch (Exception e) {
                logger.error("Error searching in namespace {}", namespace, e);
            }
        }
        
        // Sort by similarity score (descending) and limit to maxResults
        allResults.sort((a, b) -> Float.compare(b.getSimilarityScore(), a.getSimilarityScore()));
        
        return allResults.size() <= maxResults ? allResults : 
               allResults.subList(0, maxResults);
    }
    
    /**
     * Find embeddings by file path.
     */
    @Override
    public List<SimilarityResult> findEmbeddingsByFilePath(String filePath, String repositoryNamespace) {
        List<SimilarityResult> results = new ArrayList<>();
        List<String> namespacesToSearch;
        
        // If no specific namespace is provided, search all namespaces
        if (repositoryNamespace == null || repositoryNamespace.isEmpty()) {
            namespacesToSearch = this.repositoryNamespaces;
        } else {
            namespacesToSearch = Collections.singletonList(repositoryNamespace);
        }
        
        for (String namespace : namespacesToSearch) {
            // Get the metadata map for this namespace
            Map<String, EmbeddingMetadata> metadataMap = namespaceToMetadataMap.get(namespace);
            if (metadataMap == null) {
                logger.warn("Metadata map not found for namespace: {}", namespace);
                continue;
            }
            
            // Find embeddings with matching file path
            for (Map.Entry<String, EmbeddingMetadata> entry : metadataMap.entrySet()) {
                EmbeddingMetadata metadata = entry.getValue();
                
                if (filePath.equals(metadata.getFilePath())) {
                    SimilarityResult result = new SimilarityResult();
                    result.setId(entry.getKey());
                    result.setMetadata(metadata);
                    result.setSimilarityScore(1.0f); // Perfect match
                    results.add(result);
                }
            }
        }
        
        return results;
    }
    
    /**
     * Generate an embedding for a text.
     */
    @Override
    public float[] generateEmbedding(String text) {
        if (text == null || text.trim().isEmpty()) {
            logger.warn("Cannot generate embedding for empty text");
            return null;
        }
        
        // Try to generate an embedding with retries
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                // Call the embedding API
                HttpPost post = new HttpPost(embeddingEndpoint);
                post.setHeader("Content-Type", "application/json");
                post.setHeader("access_key", gainsightApiKey);
                
                // Add debug logging
                // logger.info("Making embedding API request to: {} with API key: {}", embeddingEndpoint, 
                //            gainsightApiKey != null ? (gainsightApiKey.substring(0, Math.min(5, gainsightApiKey.length())) + "...") : "null"); // This line will be removed/commented
                
                // Prepare the request body
                Map<String, Object> requestBody = new HashMap<>();
                requestBody.put("text", text);
                requestBody.put("model", "text-embedding-3-large");
                requestBody.put("modelVersion", "1");
                
                String requestJson = objectMapper.writeValueAsString(requestBody);
                post.setEntity(new StringEntity(requestJson));
                
                try (CloseableHttpResponse response = httpClient.execute(post)) {
                    int statusCode = response.getStatusLine().getStatusCode();
                    HttpEntity entity = response.getEntity();
                    String responseBody = EntityUtils.toString(entity);
                    
                    if (statusCode == 200) {
                        // Parse the response
                        Map<String, Object> responseMap = objectMapper.readValue(responseBody, Map.class);
                        Object dataObj = responseMap.get("data");
                        
                        // Handle different response formats
                        List<Object> embeddingValues = null;
                        
                        if (dataObj instanceof List) {
                            // Original format: List<List<Float>>
                            List<List<Float>> data = (List<List<Float>>) dataObj;
                            if (data != null && !data.isEmpty()) {
                                embeddingValues = new ArrayList<>();
                                // Convert List<Float> to List<Object>
                                for (Object val : data.get(0)) {
                                    embeddingValues.add(val);
                                }
                            }
                        } else if (dataObj instanceof Map) {
                            // New format: Map with embedding data
                            Map<String, Object> dataMap = (Map<String, Object>) dataObj;
                            
                            // Handle potential nested structure in the response
                            if (dataMap.containsKey("embedding")) {
                                embeddingValues = (List<Object>) dataMap.get("embedding");
                            } else if (dataMap.containsKey("data")) {
                                Object innerData = dataMap.get("data");
                                
                                if (innerData instanceof List) {
                                    List<Object> dataList = (List<Object>) innerData;
                                    
                                    if (!dataList.isEmpty() && dataList.get(0) instanceof Map) {
                                        Map<String, Object> firstEmbedding = (Map<String, Object>) dataList.get(0);
                                        
                                        if (firstEmbedding.containsKey("embedding")) {
                                            embeddingValues = (List<Object>) firstEmbedding.get("embedding");
                                        }
                                    }
                                }
                            }
                        }
                        
                        if (embeddingValues != null && !embeddingValues.isEmpty()) {
                            // Convert List<Double> or List<Float> to float[]
                            float[] result = new float[embeddingValues.size()];
                            for (int i = 0; i < embeddingValues.size(); i++) {
                                // Handle both Float and Double objects by explicitly converting to float
                                Object value = embeddingValues.get(i);
                                if (value instanceof Double) {
                                    result[i] = ((Double) value).floatValue();
                                } else if (value instanceof Float) {
                                    result[i] = (Float) value;
                                } else {
                                    // For any other numeric type, convert via Number
                                    result[i] = ((Number) value).floatValue();
                                }
                            }
                            
                            // Reset continuous failure count on success
                            continuousFailureCount.set(0);
                            
                            return result;
                        } else {
                            logger.warn("Empty embedding data received or unrecognized format: {}", dataObj.getClass().getName());
                        }
                    } else {
                        // Log error
                        String errorPreview = responseBody.length() > 100 ? responseBody.substring(0, 100) + "..." : responseBody;
                        logger.warn("Error generating embedding for text (attempt {}/{}): Error calling API: {} - {}",
                                  attempt, MAX_RETRIES, statusCode, errorPreview);
                        
                        // Short pause before retrying
                        Thread.sleep(1000);
                    }
                }
            } catch (Exception e) {
                logger.warn("Error generating embedding for text (attempt {}/{}): {}",
                           attempt, MAX_RETRIES, e.getMessage());
                
                // Add detailed error logging with stack trace
                logger.debug("Full exception details for embedding generation failure:", e);
                
                // Short pause before retrying
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        
        // If we get here, all attempts failed
        String textPreview = LoggingUtils.truncateText(text, 100);
        String textHash = Integer.toHexString(text.hashCode());
        
        // Record the failure
        EmbeddingFailure failure = new EmbeddingFailure();
        failure.setTextHash(textHash);
        failure.setTextPreview(textPreview);
        failure.setFailureCount(1);
        failure.setLastFailureTime(System.currentTimeMillis());
        failure.setLastErrorMessage("Max retries exceeded");
        
        failureMap.put(textHash, failure);
        
        // Increment continuous failure count
        int failCount = continuousFailureCount.incrementAndGet();
        if (failCount >= MAX_CONTINUOUS_FAILURES) {
            logger.error("Continuous failure threshold reached: {} failures", failCount);
        }
        
        // Save failures periodically
        if (failureMap.size() % 10 == 0) {
            saveEmbeddingFailures();
        }
        
        return null;
    }
    
    /**
     * Get all repository namespaces.
     */
    @Override
    public List<String> getRepositoryNamespaces() {
        return new ArrayList<>(repositoryNamespaces);
    }
    
    /**
     * Get all embedding failures.
     */
    @Override
    public Map<String, ?> getEmbeddingFailures() {
        return new HashMap<>(failureMap);
    }
    
    /**
     * Get continuous failure count.
     */
    @Override
    public int getContinuousFailureCount() {
        return continuousFailureCount.get();
    }
    
    /**
     * Get max continuous failures.
     */
    @Override
    public int getMaxContinuousFailures() {
        return MAX_CONTINUOUS_FAILURES;
    }
    
    /**
     * Get the size of a namespace index.
     */
    @Override
    public int size(String namespace) {
        InMemoryHnswIndex index = indexes.get(namespace);
        return index != null ? index.size() : 0;
    }
    
    /**
     * Perform embedding pre-check.
     */
    @Override
    public boolean performEmbeddingPreCheck() {
        // Simple pre-check: try to generate a test embedding
        float[] testEmbedding = generateEmbedding("This is a test embedding");
        return testEmbedding != null && testEmbedding.length > 0;
    }
    
    /**
     * Update metadata for an embedding.
     */
    @Override
    public boolean updateMetadata(String id, EmbeddingMetadata metadata) {
        if (id == null || metadata == null) {
            return false;
        }
        
        String namespace = metadata.getRepositoryNamespace();
        if (namespace == null || namespace.isEmpty()) {
            namespace = DEFAULT_NAMESPACE;
        }
        
        Map<String, EmbeddingMetadata> metadataMap = namespaceToMetadataMap.get(namespace);
        if (metadataMap == null) {
            logger.warn("Metadata map not found for namespace: {}", namespace);
            return false;
        }
        
        metadataMap.put(id, metadata);
        return true;
    }
    
    /**
     * Get metadata for an embedding.
     */
    @Override
    public EmbeddingMetadata getMetadata(String id) {
        for (String namespace : repositoryNamespaces) {
            Map<String, EmbeddingMetadata> metadataMap = namespaceToMetadataMap.get(namespace);
            if (metadataMap != null && metadataMap.containsKey(id)) {
                return metadataMap.get(id);
            }
        }
        return null;
    }
    
    /**
     * Calculate similarity between two embeddings.
     */
    @Override
    public float getSimilarity(String id, String query) {
        // First get the vector for the id
        float[] vectorById = null;
        
        for (String namespace : repositoryNamespaces) {
            vectorById = vectorStorage.loadVector(id, namespace);
            if (vectorById != null) {
                break;
            }
        }
        
        if (vectorById == null) {
            return 0.0f;
        }
        
        // Generate embedding for the query
        float[] queryVector = generateEmbedding(query);
        if (queryVector == null) {
            return 0.0f;
        }
        
        // Calculate cosine similarity
        float dotProduct = 0.0f;
        float normA = 0.0f;
        float normB = 0.0f;
        
        for (int i = 0; i < vectorById.length; i++) {
            dotProduct += vectorById[i] * queryVector[i];
            normA += vectorById[i] * vectorById[i];
            normB += queryVector[i] * queryVector[i];
        }
        
        if (normA == 0 || normB == 0) {
            return 0.0f;
        }
        
        return dotProduct / ((float) Math.sqrt(normA) * (float) Math.sqrt(normB));
    }
    
    /**
     * Delete an embedding from the default namespace.
     */
    @Override
    public boolean deleteEmbedding(String id) {
        return deleteEmbedding(id, DEFAULT_NAMESPACE);
    }
    
    /**
     * Delete an embedding from a specific namespace.
     */
    @Override
    public boolean deleteEmbedding(String id, String repositoryNamespace) {
        String namespace = repositoryNamespace != null ? repositoryNamespace : DEFAULT_NAMESPACE;
        
        try {
            // Remove from metadata
            Map<String, EmbeddingMetadata> metadataMap = namespaceToMetadataMap.get(namespace);
            if (metadataMap != null) {
                metadataMap.remove(id);
            }
            
            // Remove from index
            InMemoryHnswIndex index = indexes.get(namespace);
            if (index != null) {
                index.remove(id);
            }
            
            // Remove from storage
            return vectorStorage.deleteVector(id, namespace);
        } catch (Exception e) {
            logger.error("Error deleting embedding {} from namespace {}", id, namespace, e);
            return false;
        }
    }
    
    /**
     * Generate embeddings in batch.
     */
    @Override
    public float[][] generateEmbeddingsBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return new float[0][];
        }
        
        float[][] results = new float[texts.size()][];
        
        for (int i = 0; i < texts.size(); i++) {
            results[i] = generateEmbedding(texts.get(i));
        }
        
        return results;
    }
    
    /**
     * Store embeddings in batch.
     */
    @Override
    public int storeEmbeddingsBatch(List<String> ids, float[][] vectors, List<EmbeddingMetadata> metadataList, String repositoryNamespace) {
        if (ids == null || vectors == null || metadataList == null || 
            ids.size() != vectors.length || ids.size() != metadataList.size()) {
            return 0;
        }
        
        int successCount = 0;
        
        for (int i = 0; i < ids.size(); i++) {
            boolean success = storeEmbedding(ids.get(i), vectors[i], metadataList.get(i), repositoryNamespace);
            if (success) {
                successCount++;
            }
        }
        
        return successCount;
    }
    
    /**
     * Clear embedding failures.
     */
    @Override
    public void clearEmbeddingFailures() {
        failureMap.clear();
        saveEmbeddingFailures();
    }
    
    /**
     * Reset continuous failure count.
     */
    @Override
    public void resetContinuousFailureCount() {
        continuousFailureCount.set(0);
    }
    
    /**
     * Get total embedding count across all namespaces.
     */
    @Override
    public int getEmbeddingCount() {
        int total = 0;
        for (String namespace : repositoryNamespaces) {
            total += getEmbeddingCount(namespace);
        }
        return total;
    }
    
    /**
     * Get embedding count for a specific namespace.
     */
    @Override
    public int getEmbeddingCount(String namespace) {
        Map<String, EmbeddingMetadata> metadataMap = namespaceToMetadataMap.get(namespace);
        return metadataMap != null ? metadataMap.size() : 0;
    }
    
    /**
     * Check if the service is available.
     */
    @Override
    public boolean isAvailable() {
        // Service is available if embedding generation is working
        return performEmbeddingPreCheck();
    }
    
    /**
     * Get the total size across all namespaces.
     */
    @Override
    public int size() {
        int total = 0;
        for (String namespace : repositoryNamespaces) {
            total += size(namespace);
        }
        return total;
    }
    
    /**
     * Convert model EmbeddingMetadata to service EmbeddingMetadata.
     */
    private EmbeddingMetadata convertFromModelMetadata(com.l3agent.model.EmbeddingMetadata modelMetadata) {
        if (modelMetadata == null) {
            return null;
        }
        
        EmbeddingMetadata metadata = new EmbeddingMetadata();
        metadata.setSource(modelMetadata.getId());
        metadata.setType(modelMetadata.getType());
        metadata.setFilePath(modelMetadata.getFilePath());
        metadata.setStartLine(modelMetadata.getStartLine());
        metadata.setEndLine(modelMetadata.getEndLine());
        metadata.setContent(modelMetadata.getContent());
        metadata.setLanguage(modelMetadata.getLanguage());
        metadata.setRepositoryNamespace(modelMetadata.getRepositoryNamespace());
        metadata.setDescription(modelMetadata.getDescription());
        metadata.setPurposeSummary(modelMetadata.getPurposeSummary());
        metadata.setCapabilities(modelMetadata.getCapabilities());
        metadata.setUsageExamples(modelMetadata.getUsageExamples());
        
        return metadata;
    }
    
    /**
     * Convert service EmbeddingMetadata to model EmbeddingMetadata.
     */
    private com.l3agent.model.EmbeddingMetadata convertToModelEmbeddingMetadata(EmbeddingMetadata metadata) {
        if (metadata == null) {
            return null;
        }
        
        com.l3agent.model.EmbeddingMetadata modelMetadata = new com.l3agent.model.EmbeddingMetadata();
        modelMetadata.setId(metadata.getSource());
        modelMetadata.setSource(metadata.getSource());
        modelMetadata.setType(metadata.getType());
        modelMetadata.setFilePath(metadata.getFilePath());
        modelMetadata.setStartLine(metadata.getStartLine());
        modelMetadata.setEndLine(metadata.getEndLine());
        modelMetadata.setContent(metadata.getContent());
        modelMetadata.setLanguage(metadata.getLanguage());
        modelMetadata.setRepositoryNamespace(metadata.getRepositoryNamespace());
        modelMetadata.setDescription(metadata.getDescription());
        modelMetadata.setPurposeSummary(metadata.getPurposeSummary());
        modelMetadata.setCapabilities(metadata.getCapabilities());
        modelMetadata.setUsageExamples(metadata.getUsageExamples());
        
        return modelMetadata;
    }
    
    /**
     * Get all metadata.
     */
    @Override
    public Map<String, com.l3agent.model.EmbeddingMetadata> getAllMetadata() {
        Map<String, com.l3agent.model.EmbeddingMetadata> allMetadata = new HashMap<>();
        
        // Combine metadata from all namespaces
        for (String namespace : repositoryNamespaces) {
            Map<String, EmbeddingMetadata> namespaceMetadata = namespaceToMetadataMap.get(namespace);
            if (namespaceMetadata != null) {
                for (Map.Entry<String, EmbeddingMetadata> entry : namespaceMetadata.entrySet()) {
                    allMetadata.put(entry.getKey(), convertToModelEmbeddingMetadata(entry.getValue()));
                }
            }
        }
        
        return allMetadata;
    }
    
    /**
     * Get all embeddings.
     */
    @Override
    public Map<String, float[]> getAllEmbeddings() {
        Map<String, float[]> allEmbeddings = new HashMap<>();
        
        // Combine embeddings from all namespaces
        for (String namespace : repositoryNamespaces) {
            Map<String, EmbeddingMetadata> metadataMap = namespaceToMetadataMap.get(namespace);
            if (metadataMap != null) {
                for (String id : metadataMap.keySet()) {
                    float[] vector = vectorStorage.loadVector(id, namespace);
                    if (vector != null) {
                        allEmbeddings.put(id, vector);
                    }
                }
            }
        }
        
        return allEmbeddings;
    }
    
    /**
     * Embedding failure class for tracking and persisting failed embedding generation attempts.
     */
    public static class EmbeddingFailure {
        private String textHash;
        private String textPreview;
        private int failureCount;
        private long lastFailureTime;
        private String lastErrorMessage;
        
        // Default constructor for serialization/deserialization
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
        
        // Getters and setters
        public String getTextHash() {
            return textHash;
        }
        
        public void setTextHash(String textHash) {
            this.textHash = textHash;
        }
        
        public String getTextPreview() {
            return textPreview;
        }
        
        public void setTextPreview(String textPreview) {
            this.textPreview = textPreview;
        }
        
        public int getFailureCount() {
            return failureCount;
        }
        
        public void setFailureCount(int failureCount) {
            this.failureCount = failureCount;
        }
        
        public long getLastFailureTime() {
            return lastFailureTime;
        }
        
        public void setLastFailureTime(long lastFailureTime) {
            this.lastFailureTime = lastFailureTime;
        }
        
        public String getLastErrorMessage() {
            return lastErrorMessage;
        }
        
        public void setLastErrorMessage(String lastErrorMessage) {
            this.lastErrorMessage = lastErrorMessage;
        }
    }
} 