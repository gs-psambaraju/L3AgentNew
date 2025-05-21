package com.l3agent.service.impl;

import com.l3agent.model.EmbeddingMetadata;
import com.l3agent.service.VectorStoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Migrates existing vector data to the new JSON format.
 * Can be run with:
 * --l3agent.runner=vector-data-migrator
 * --l3agent.vector-migrator.namespace=<namespace> (optional)
 * --l3agent.vector-migrator.batch-size=50 (optional)
 * --l3agent.vector-migrator.threads=4 (optional)
 */
@Component
@ConditionalOnProperty(name = "l3agent.runner", havingValue = "vector-data-migrator")
public class VectorDataMigrator implements CommandLineRunner {
    private static final Logger logger = LoggerFactory.getLogger(VectorDataMigrator.class);
    
    @Autowired
    private VectorStoreService vectorStoreService;
    
    @Autowired
    private JsonVectorStorage vectorStorage;
    
    @Value("${l3agent.vector-migrator.namespace:}")
    private String targetNamespace;
    
    @Value("${l3agent.vector-migrator.batch-size:50}")
    private int batchSize;
    
    @Value("${l3agent.vector-migrator.threads:4}")
    private int threadCount;
    
    @Override
    public void run(String... args) {
        logger.info("Starting vector data migration with {} threads, batch size: {}", threadCount, batchSize);
        
        // Get all repository namespaces
        if (targetNamespace != null && !targetNamespace.isEmpty()) {
            logger.info("Migrating data for target namespace: {}", targetNamespace);
            migrateNamespace(targetNamespace);
        } else {
            for (String namespace : vectorStoreService.getRepositoryNamespaces()) {
                logger.info("Migrating data for namespace: {}", namespace);
                migrateNamespace(namespace);
            }
        }
        
        logger.info("Vector data migration completed");
    }
    
    /**
     * Migrate data for a specific namespace.
     */
    private void migrateNamespace(String namespace) {
        // Get all metadata for the namespace
        Map<String, EmbeddingMetadata> metadataMap = getMetadataForNamespace(namespace);
        if (metadataMap.isEmpty()) {
            logger.info("No metadata found for namespace: {}", namespace);
            return;
        }
        
        logger.info("Found {} metadata entries for namespace: {}", metadataMap.size(), namespace);
        
        // Create thread pool
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        AtomicInteger skippedCount = new AtomicInteger(0);
        
        // Process metadata entries in batches
        AtomicInteger processedCount = new AtomicInteger(0);
        for (Map.Entry<String, EmbeddingMetadata> entry : metadataMap.entrySet()) {
            String id = entry.getKey();
            EmbeddingMetadata metadata = entry.getValue();
            
            // Skip if vector already exists in new storage
            if (vectorStorage.vectorExists(id, namespace)) {
                skippedCount.incrementAndGet();
                
                // Log progress periodically
                int processed = processedCount.incrementAndGet();
                if (processed % batchSize == 0 || processed == metadataMap.size()) {
                    logger.info("Progress: {}/{} ({} successful, {} errors, {} skipped)",
                             processed, metadataMap.size(), successCount.get(), errorCount.get(), skippedCount.get());
                }
                
                continue;
            }
            
            // Submit task to generate and store vector
            executor.submit(() -> {
                try {
                    // Generate embedding from content
                    String content = metadata.getContent();
                    if (content == null || content.isEmpty()) {
                        errorCount.incrementAndGet();
                        logger.error("Empty content for ID {} in namespace {}", id, namespace);
                        return;
                    }
                    
                    float[] vector = vectorStoreService.generateEmbedding(content);
                    
                    if (vector != null && vector.length > 0) {
                        // Store vector in new format
                        boolean success = vectorStorage.storeVector(id, vector, namespace);
                        if (success) {
                            successCount.incrementAndGet();
                        } else {
                            errorCount.incrementAndGet();
                            logger.error("Failed to store vector for ID {} in namespace {}", id, namespace);
                        }
                    } else {
                        errorCount.incrementAndGet();
                        logger.error("Failed to generate vector for ID {} in namespace {}", id, namespace);
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                    logger.error("Error migrating vector for ID {} in namespace {}: {}", 
                              id, namespace, e.getMessage(), e);
                } finally {
                    // Log progress periodically
                    int processed = processedCount.incrementAndGet();
                    if (processed % batchSize == 0 || processed == metadataMap.size()) {
                        logger.info("Progress: {}/{} ({} successful, {} errors, {} skipped)",
                              processed, metadataMap.size(), successCount.get(), errorCount.get(), skippedCount.get());
                    }
                }
            });
            
            // Throttle submissions to avoid overwhelming the embedding API
            if (processedCount.get() % batchSize == 0) {
                try {
                    // Short pause between batches
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        
        // Shutdown executor and wait for completion
        executor.shutdown();
        try {
            if (!executor.awaitTermination(1, TimeUnit.HOURS)) {
                executor.shutdownNow();
                logger.warn("Migration timed out after 1 hour");
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        logger.info("Migration completed for namespace {}: {} successful, {} errors, {} skipped",
                  namespace, successCount.get(), errorCount.get(), skippedCount.get());
    }
    
    /**
     * Get metadata for a specific namespace.
     */
    private Map<String, EmbeddingMetadata> getMetadataForNamespace(String namespace) {
        Map<String, EmbeddingMetadata> allMetadata = vectorStoreService.getAllMetadata();
        Map<String, EmbeddingMetadata> namespaceMetadata = new HashMap<>();
        
        // Filter metadata by namespace
        for (Map.Entry<String, EmbeddingMetadata> entry : allMetadata.entrySet()) {
            EmbeddingMetadata metadata = entry.getValue();
            if (namespace.equals(metadata.getRepositoryNamespace())) {
                namespaceMetadata.put(entry.getKey(), metadata);
            }
        }
        
        return namespaceMetadata;
    }
} 