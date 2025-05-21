package com.l3agent.cli;

import com.l3agent.model.GenerateEmbeddingsResult;
import com.l3agent.service.L3AgentService;
import com.l3agent.service.VectorStoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Command-line runner to retry failed embeddings.
 * Can be run with:
 * --l3agent.runner=retry-failed-embeddings
 * 
 * Additional options:
 * --l3agent.embedding.retry.namespace=<namespace> (optional - to target a specific namespace)
 * --l3agent.embedding.retry.regenerate-namespace=true (optional - to force regeneration of an entire namespace)
 */
@Component
@ConditionalOnProperty(name = "l3agent.runner", havingValue = "retry-failed-embeddings")
public class RetryFailedEmbeddingsRunner implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(RetryFailedEmbeddingsRunner.class);

    @Autowired
    private L3AgentService l3AgentService;
    
    @Autowired
    private VectorStoreService vectorStoreService;
    
    @Autowired
    private java.util.Optional<String> namespace;
    
    @Autowired 
    private java.util.Optional<Boolean> regenerateNamespace;

    public RetryFailedEmbeddingsRunner(
            @org.springframework.beans.factory.annotation.Value("${l3agent.embedding.retry.namespace:#{null}}") 
            java.util.Optional<String> namespace,
            @org.springframework.beans.factory.annotation.Value("${l3agent.embedding.retry.regenerate-namespace:false}") 
            java.util.Optional<Boolean> regenerateNamespace) {
        this.namespace = namespace;
        this.regenerateNamespace = regenerateNamespace;
    }

    @Override
    public void run(String... args) throws Exception {
        logger.info("Starting RetryFailedEmbeddingsRunner");
        
        if (namespace.isPresent() && regenerateNamespace.isPresent() && regenerateNamespace.get()) {
            // Regenerate all embeddings for a specific namespace
            regenerateNamespaceEmbeddings(namespace.get());
        } else {
            // Retry only failed embeddings
            retryFailedEmbeddings();
        }
        
        logger.info("RetryFailedEmbeddingsRunner completed");
    }
    
    private void retryFailedEmbeddings() {
        // Get all embedding failures
        Map<String, ?> failures = vectorStoreService.getEmbeddingFailures();
        
        if (failures == null || failures.isEmpty()) {
            logger.info("No failed embeddings found to retry");
            return;
        }
        
        logger.info("Found {} failed embeddings to retry", failures.size());
        
        // Filter by namespace if specified
        Map<String, Object> options = new HashMap<>();
        if (namespace.isPresent()) {
            options.put("repository_namespace", namespace.get());
            logger.info("Filtering failed embeddings for namespace: {}", namespace.get());
        }
        
        // Call the service to retry failed embeddings
        Map<String, Object> result = l3AgentService.retryFailedEmbeddings(options);
        // Convert to GenerateEmbeddingsResult for convenience
        GenerateEmbeddingsResult embedResult = new GenerateEmbeddingsResult();
        embedResult.putAll(result);
        
        // Log the results
        logger.info("Retry completed. Results: {} successful, {} failed", 
                embedResult.getSuccess_count(), embedResult.getFailure_count());
        
        if (embedResult.getFailure_count() > 0) {
            logger.warn("Some embeddings still failed. Consider checking API credentials and retrying again.");
        }
    }
    
    private void regenerateNamespaceEmbeddings(String namespaceToRegenerate) {
        logger.info("Regenerating all embeddings for namespace: {}", namespaceToRegenerate);
        
        // Check if the namespace exists
        if (!vectorStoreService.getRepositoryNamespaces().contains(namespaceToRegenerate)) {
            logger.error("Namespace {} does not exist", namespaceToRegenerate);
            return;
        }
        
        // Get all metadata for this namespace to extract the repository paths
        Map<String, com.l3agent.model.EmbeddingMetadata> allMetadata = vectorStoreService.getAllMetadata();
        
        if (allMetadata.isEmpty()) {
            logger.error("No metadata found for namespace {}", namespaceToRegenerate);
            return;
        }
        
        // Extract unique repository paths from the metadata
        Map<String, String> repositoryPaths = new HashMap<>();
        
        // Collect distinct repository paths from metadata
        for (Map.Entry<String, com.l3agent.model.EmbeddingMetadata> entry : allMetadata.entrySet()) {
            com.l3agent.model.EmbeddingMetadata metadata = entry.getValue();
            if (namespaceToRegenerate.equals(metadata.getRepositoryNamespace())) {
                String path = metadata.getRepositoryPath();
                if (path != null && !path.isEmpty()) {
                    repositoryPaths.put(path, namespaceToRegenerate);
                }
            }
        }
        
        if (repositoryPaths.isEmpty()) {
            logger.error("Could not determine repository paths for namespace {}", namespaceToRegenerate);
            return;
        }
        
        logger.info("Found {} repository paths for namespace {}", repositoryPaths.size(), namespaceToRegenerate);
        
        // Regenerate embeddings for each repository path
        for (Map.Entry<String, String> entry : repositoryPaths.entrySet()) {
            String repoPath = entry.getKey();
            
            logger.info("Regenerating embeddings for repository at: {}", repoPath);
            
            Map<String, Object> options = new HashMap<>();
            options.put("repository_path", repoPath);
            options.put("repository_namespace", namespaceToRegenerate);
            options.put("force_regenerate", true);
            
            Map<String, Object> result = l3AgentService.generateEmbeddingsOnDemand(options);
            // Convert to GenerateEmbeddingsResult for convenience
            GenerateEmbeddingsResult embedResult = new GenerateEmbeddingsResult();
            embedResult.putAll(result);
            
            logger.info("Regeneration for repository {} completed. Results: {} successful, {} failed, {} total failures", 
                    repoPath, embedResult.getSuccess_count(), embedResult.getFailure_count(), 
                    result.containsKey("total_failures") ? result.get("total_failures") : embedResult.getFailure_count());
        }
    }
} 