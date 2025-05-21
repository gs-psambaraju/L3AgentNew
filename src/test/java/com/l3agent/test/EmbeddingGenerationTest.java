package com.l3agent.test;

import com.l3agent.service.LLMService;
import com.l3agent.service.VectorStoreService;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test class to verify embedding generation works with database disabled.
 */
@SpringBootTest
@ActiveProfiles("test-no-db")
public class EmbeddingGenerationTest {
    
    private static final Logger logger = LoggerFactory.getLogger(EmbeddingGenerationTest.class);
    
    @Autowired
    private VectorStoreService vectorStoreService;
    
    @Test
    public void testEmbeddingGenerationWithoutDatabase() throws Exception {
        // Create a test directory
        Path testDir = Path.of("./data/test");
        Files.createDirectories(testDir);
        
        // Create a test file
        Path testFile = testDir.resolve("test-content.txt");
        String testContent = "This is a test content for embedding generation.";
        Files.writeString(testFile, testContent);
        
        // Generate embeddings
        logger.info("Generating embeddings for test content");
        float[] embeddings = vectorStoreService.generateEmbedding(testContent);
        
        // Verify embeddings were generated
        assertNotNull(embeddings);
        assertTrue(embeddings.length > 0);
        
        // Verify embeddings have non-zero values (not fallback empty embeddings)
        boolean hasNonZeroValues = false;
        for (float value : embeddings) {
            if (value != 0.0f) {
                hasNonZeroValues = true;
                break;
            }
        }
        assertTrue(hasNonZeroValues, "Embeddings should have non-zero values");
        
        // Store embeddings
        logger.info("Storing embeddings in vector store");
        String id = "test-embedding-" + System.currentTimeMillis();
        VectorStoreService.EmbeddingMetadata metadata = new VectorStoreService.EmbeddingMetadata();
        metadata.setContent(testContent);
        metadata.setFilePath(testFile.toString());
        metadata.setSource("test");
        boolean storeResult = vectorStoreService.storeEmbedding(id, embeddings, metadata);
        assertTrue(storeResult, "Embedding should be stored successfully");
        
        // Search with embeddings
        logger.info("Searching with embeddings");
        List<VectorStoreService.SimilarityResult> results = vectorStoreService.findSimilar(embeddings, 1, 0.7f);
        
        // Verify search results
        assertNotNull(results);
        assertTrue(results.size() > 0, "Should find at least one result");
        
        // Clean up
        Files.deleteIfExists(testFile);
        logger.info("Test completed successfully");
    }
} 