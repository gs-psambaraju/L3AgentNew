package com.l3agent.service;

import java.util.List;
import java.util.Map;

/**
 * Service interface for vector store operations.
 * Provides functionality for storing and retrieving vector embeddings.
 */
public interface VectorStoreService {
    
    /**
     * Stores a vector embedding with associated metadata.
     * 
     * @param id The unique identifier for the embedding
     * @param vector The embedding vector
     * @param metadata Associated metadata for the embedding
     * @return True if successful, false otherwise
     */
    boolean storeEmbedding(String id, float[] vector, EmbeddingMetadata metadata);
    
    /**
     * Stores a vector embedding with associated metadata in a specific repository namespace.
     * 
     * @param id The unique identifier for the embedding
     * @param vector The embedding vector
     * @param metadata Associated metadata for the embedding
     * @param repositoryNamespace The repository namespace to store the embedding in
     * @return True if successful, false otherwise
     */
    boolean storeEmbedding(String id, float[] vector, EmbeddingMetadata metadata, String repositoryNamespace);
    
    /**
     * Finds similar embeddings based on vector similarity.
     * 
     * @param queryVector The query vector
     * @param maxResults The maximum number of results to return
     * @param minSimilarity The minimum similarity score (0.0-1.0)
     * @return A list of similarity results
     */
    List<SimilarityResult> findSimilar(float[] queryVector, int maxResults, float minSimilarity);
    
    /**
     * Finds similar embeddings based on vector similarity within specific repository namespaces.
     * 
     * @param queryVector The query vector
     * @param maxResults The maximum number of results to return
     * @param minSimilarity The minimum similarity score (0.0-1.0)
     * @param repositoryNamespaces The repository namespaces to search in, empty for all namespaces
     * @return A list of similarity results
     */
    List<SimilarityResult> findSimilar(float[] queryVector, int maxResults, float minSimilarity, 
            List<String> repositoryNamespaces);
    
    /**
     * Generates an embedding vector for a text input.
     * 
     * @param text The input text
     * @return The embedding vector
     */
    float[] generateEmbedding(String text);
    
    /**
     * Deletes an embedding by ID.
     * 
     * @param id The ID of the embedding to delete
     * @return True if successful, false otherwise
     */
    boolean deleteEmbedding(String id);
    
    /**
     * Deletes an embedding by ID from a specific repository namespace.
     * 
     * @param id The ID of the embedding to delete
     * @param repositoryNamespace The repository namespace to delete from
     * @return True if successful, false otherwise
     */
    boolean deleteEmbedding(String id, String repositoryNamespace);
    
    /**
     * Checks if the vector store is available.
     * 
     * @return True if available, false otherwise
     */
    boolean isAvailable();
    
    /**
     * Returns the size of the vector store.
     * 
     * @return The number of embeddings in the store
     */
    int size();
    
    /**
     * Returns the size of the vector store for a specific repository namespace.
     * 
     * @param repositoryNamespace The repository namespace to get the size for
     * @return The number of embeddings in the specified namespace
     */
    int size(String repositoryNamespace);
    
    /**
     * Returns the total number of embeddings in the store.
     * This is equivalent to size() but with a more descriptive name.
     * 
     * @return The number of embeddings in the store
     */
    int getEmbeddingCount();
    
    /**
     * Returns the total number of embeddings in the store for a specific repository namespace.
     * 
     * @param repositoryNamespace The repository namespace to get the count for
     * @return The number of embeddings in the specified namespace
     */
    int getEmbeddingCount(String repositoryNamespace);
    
    /**
     * Gets all embedding generation failures.
     * 
     * @return Map of embedding failures
     */
    Map<String, ?> getEmbeddingFailures();
    
    /**
     * Clears all embedding failure records.
     */
    void clearEmbeddingFailures();
    
    /**
     * Gets all available repository namespaces.
     * 
     * @return List of repository namespaces
     */
    List<String> getRepositoryNamespaces();
    
    /**
     * Validates that the embedding generation system is working properly.
     *
     * @return True if the check passed, false otherwise
     */
    boolean performEmbeddingPreCheck();
    
    /**
     * Gets the current continuous failure count.
     * 
     * @return The number of continuous failures
     */
    int getContinuousFailureCount();
    
    /**
     * Resets the continuous failure count to zero.
     */
    void resetContinuousFailureCount();
    
    /**
     * Gets the maximum allowed continuous failures before aborting.
     * 
     * @return The maximum allowed continuous failures
     */
    int getMaxContinuousFailures();
    
    /**
     * Finds all embeddings associated with a specific file path.
     * Useful for inspection and debugging of embedding data.
     * 
     * @param filePath The file path to search for
     * @param repositoryNamespace Optional repository namespace to limit the search
     * @return A list of embedding results matching the file path
     */
    List<SimilarityResult> findEmbeddingsByFilePath(String filePath, String repositoryNamespace);
    
    /**
     * Generates embedding vectors for multiple text inputs in a batch operation.
     * This is more efficient than calling generateEmbedding multiple times.
     * 
     * @param texts The list of input texts to embed
     * @return An array of embedding vectors, with the same order as the input texts
     */
    float[][] generateEmbeddingsBatch(List<String> texts);
    
    /**
     * Stores multiple embeddings in a batch operation.
     * 
     * @param ids List of unique identifiers for the embeddings
     * @param vectors Array of embedding vectors
     * @param metadataList List of associated metadata for the embeddings
     * @param repositoryNamespace The repository namespace to store the embeddings in
     * @return Number of successfully stored embeddings
     */
    int storeEmbeddingsBatch(List<String> ids, float[][] vectors, List<EmbeddingMetadata> metadataList, String repositoryNamespace);
    
    /**
     * Gets all embeddings from the vector store.
     * 
     * @return Map of embedding IDs to embedding vectors
     */
    Map<String, float[]> getAllEmbeddings();
    
    /**
     * Gets all metadata from the vector store.
     * 
     * @return Map of embedding IDs to embedding metadata
     */
    Map<String, com.l3agent.model.EmbeddingMetadata> getAllMetadata();
    
    /**
     * Gets metadata for a specific embedding ID.
     * 
     * @param id The ID of the embedding
     * @return The metadata for the embedding, or null if not found
     */
    EmbeddingMetadata getMetadata(String id);
    
    /**
     * Updates metadata for an existing embedding.
     * 
     * @param id The ID of the embedding
     * @param metadata The new metadata
     * @return True if successful, false otherwise
     */
    boolean updateMetadata(String id, EmbeddingMetadata metadata);
    
    /**
     * Calculates the similarity between a query string and an embedding ID.
     * 
     * @param id The ID of the embedding
     * @param query The query string
     * @return The similarity score between 0.0 and 1.0
     */
    float getSimilarity(String id, String query);
    
    /**
     * Represents metadata for an embedding.
     */
    class EmbeddingMetadata {
        private String source;
        private String type;
        private String filePath;
        private int startLine;
        private int endLine;
        private String content;
        private String language;
        private String repositoryNamespace;
        private String description;        // Natural language description
        private String purposeSummary;     // One-line purpose statement
        private List<String> capabilities; // List of key capabilities
        private List<String> usageExamples; // Sample usage examples
        
        // Getters and setters
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
        
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        
        public String getPurposeSummary() { return purposeSummary; }
        public void setPurposeSummary(String purposeSummary) { this.purposeSummary = purposeSummary; }
        
        public List<String> getCapabilities() { return capabilities; }
        public void setCapabilities(List<String> capabilities) { this.capabilities = capabilities; }
        
        public List<String> getUsageExamples() { return usageExamples; }
        public void setUsageExamples(List<String> usageExamples) { this.usageExamples = usageExamples; }
    }
    
    /**
     * Represents a similarity search result.
     */
    class SimilarityResult {
        private String id;
        private EmbeddingMetadata metadata;
        private float similarityScore;
        
        // Getters and setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        
        public EmbeddingMetadata getMetadata() { return metadata; }
        public void setMetadata(EmbeddingMetadata metadata) { this.metadata = metadata; }
        
        public float getSimilarityScore() { return similarityScore; }
        public void setSimilarityScore(float similarityScore) { this.similarityScore = similarityScore; }
    }
} 