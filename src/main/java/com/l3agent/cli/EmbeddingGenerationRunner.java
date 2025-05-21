package com.l3agent.cli;

import com.l3agent.service.L3AgentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Command line runner that executes embedding generation when specific command line arguments are provided.
 * This allows direct embedding generation without starting a web server.
 */
@Component
public class EmbeddingGenerationRunner implements CommandLineRunner {
    private static final Logger logger = LoggerFactory.getLogger(EmbeddingGenerationRunner.class);
    
    @Autowired
    private L3AgentService l3AgentService;
    
    @Autowired
    private ApplicationContext applicationContext;
    
    @Override
    public void run(String... args) throws Exception {
        boolean isEmbeddingGenerationMode = false;
        String path = null;
        boolean recursive = false;
        
        // Parse command line arguments
        for (int i = 0; i < args.length; i++) {
            if ("--generate-embeddings".equals(args[i])) {
                isEmbeddingGenerationMode = true;
            } else if ("--path".equals(args[i]) && i + 1 < args.length) {
                path = args[i + 1];
                i++; // Skip the next argument since we just processed it
            } else if ("--recursive".equals(args[i])) {
                recursive = true;
            }
        }
        
        // Only execute if explicit embedding generation was requested
        if (isEmbeddingGenerationMode) {
            logger.info("Starting embedding generation in command line mode");
            logger.info("Path: {}, Recursive: {}", path, recursive);
            
            try {
                // Generate embeddings
                Map<String, Object> result = l3AgentService.generateEmbeddingsOnDemand(path, recursive);
                
                // Print results
                logger.info("Embedding generation completed");
                logger.info("Files processed: {}", result.get("files_processed"));
                logger.info("Total chunks: {}", result.get("total_chunks"));
                logger.info("Successful embeddings: {}", result.get("successful_embeddings"));
                logger.info("Failed embeddings in this batch: {}", result.get("failed_embeddings"));
                
                // Report total failures if available
                if (result.containsKey("total_failures")) {
                    logger.info("Total failures (including previous runs): {}", result.get("total_failures"));
                }
                
                logger.info("Processing time (ms): {}", result.get("duration_ms"));
                
                // Exit the application with success code
                exitApplication(0);
            } catch (Exception e) {
                logger.error("Error generating embeddings", e);
                // Exit the application with error code
                exitApplication(1);
            }
        }
    }
    
    /**
     * Exit the application with the specified code.
     * Uses a small delay to allow for log messages to be flushed.
     *
     * @param code The exit code
     */
    private void exitApplication(int code) {
        // Delay to allow logs to flush
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Exit with the specified code
        SpringApplication.exit(applicationContext, () -> code);
    }
} 