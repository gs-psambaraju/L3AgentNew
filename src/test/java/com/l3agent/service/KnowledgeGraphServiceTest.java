package com.l3agent.service;

import com.l3agent.service.impl.InMemoryKnowledgeGraphService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for the KnowledgeGraphService implementation.
 */
public class KnowledgeGraphServiceTest {

    private InMemoryKnowledgeGraphService knowledgeGraphService;
    
    @Mock
    private CodeChunkingService codeChunkingService;
    
    private Path tempDir;
    
    @BeforeEach
    public void setUp() throws IOException {
        MockitoAnnotations.openMocks(this);
        
        // Create temporary directory for test data
        tempDir = Files.createTempDirectory("test-knowledge-graph");
        
        // Initialize the service
        knowledgeGraphService = new InMemoryKnowledgeGraphService();
        ReflectionTestUtils.setField(knowledgeGraphService, "codeChunkingService", codeChunkingService);
        ReflectionTestUtils.setField(knowledgeGraphService, "dataDir", tempDir.toString());
        
        // Call init method to initialize the service
        knowledgeGraphService.init();
    }
    
    @Test
    public void testServiceInitialization() {
        // Verify that the service was initialized correctly
        assertTrue(knowledgeGraphService.isAvailable());
        assertEquals(0, knowledgeGraphService.getEntityCount());
        assertEquals(0, knowledgeGraphService.getRelationshipCount());
    }
    
    @Test
    public void testBuildKnowledgeGraph() throws IOException {
        // Create a test Java file
        Path testDir = Files.createDirectory(tempDir.resolve("test-src"));
        Path testFile = testDir.resolve("TestClass.java");
        String testCode = "package com.example;\n\n" +
                "public class TestClass {\n" +
                "    private String field;\n\n" +
                "    public String getField() {\n" +
                "        return field;\n" +
                "    }\n\n" +
                "    public void setField(String field) {\n" +
                "        this.field = field;\n" +
                "    }\n" +
                "}";
        Files.writeString(testFile, testCode);
        
        // Build the knowledge graph
        Map<String, Object> result = knowledgeGraphService.buildKnowledgeGraph(
                testDir.toString(), true);
        
        // Verify the result
        assertEquals("success", result.get("status"));
        assertTrue((int)result.get("files_processed") > 0);
        assertTrue((int)result.get("entities_created") > 0);
        
        // Verify entity count
        assertTrue(knowledgeGraphService.getEntityCount() > 0);
        
        // Search for the class
        List<KnowledgeGraphService.CodeEntity> entities = 
                knowledgeGraphService.searchEntities("TestClass", 10);
        assertFalse(entities.isEmpty());
        
        // Verify the class properties
        KnowledgeGraphService.CodeEntity entity = entities.get(0);
        assertEquals("TestClass", entity.getName());
        assertEquals("class", entity.getType());
        assertTrue(entity.getFullyQualifiedName().contains("TestClass"));
    }
    
    @Test
    public void testFindRelatedEntities() throws IOException {
        // Create test Java files with relationships
        Path testDir = Files.createDirectory(tempDir.resolve("test-relationships"));
        
        // Interface file
        Path interfaceFile = testDir.resolve("TestInterface.java");
        String interfaceCode = "package com.example;\n\n" +
                "public interface TestInterface {\n" +
                "    void doSomething();\n" +
                "}";
        Files.writeString(interfaceFile, interfaceCode);
        
        // Implementation class
        Path implFile = testDir.resolve("TestImplementation.java");
        String implCode = "package com.example;\n\n" +
                "public class TestImplementation implements TestInterface {\n" +
                "    @Override\n" +
                "    public void doSomething() {\n" +
                "        // Implementation\n" +
                "    }\n" +
                "}";
        Files.writeString(implFile, implCode);
        
        // Build the knowledge graph
        knowledgeGraphService.buildKnowledgeGraph(testDir.toString(), true);
        
        // Get the interface entity
        List<KnowledgeGraphService.CodeEntity> interfaces = 
                knowledgeGraphService.searchEntities("TestInterface", 1);
        assertFalse(interfaces.isEmpty());
        
        // Find related entities for the interface
        List<KnowledgeGraphService.CodeRelationship> relationships = 
                knowledgeGraphService.findRelatedEntities(interfaces.get(0).getId(), 1);
        
        // Verify we found relationships
        assertFalse(relationships.isEmpty());
        
        // Check for IMPLEMENTS relationship
        boolean foundImplementsRelationship = relationships.stream()
                .anyMatch(rel -> "IMPLEMENTS".equals(rel.getType()));
        assertTrue(foundImplementsRelationship, "Should find IMPLEMENTS relationship");
    }
    
    @Test
    public void testSearchEntities() throws IOException {
        // Create test Java files
        Path testDir = Files.createDirectory(tempDir.resolve("test-search"));
        
        // Class file
        Path classFile = testDir.resolve("SearchableClass.java");
        String classCode = "package com.example;\n\n" +
                "public class SearchableClass {\n" +
                "    private String searchField;\n\n" +
                "    public String getSearchField() {\n" +
                "        return searchField;\n" +
                "    }\n" +
                "}";
        Files.writeString(classFile, classCode);
        
        // Build the knowledge graph
        knowledgeGraphService.buildKnowledgeGraph(testDir.toString(), true);
        
        // Search with exact term
        List<KnowledgeGraphService.CodeEntity> exactResults = 
                knowledgeGraphService.searchEntities("SearchableClass", 10);
        assertFalse(exactResults.isEmpty());
        assertEquals("SearchableClass", exactResults.get(0).getName());
        
        // Search with partial term
        List<KnowledgeGraphService.CodeEntity> partialResults = 
                knowledgeGraphService.searchEntities("Searchable", 10);
        assertFalse(partialResults.isEmpty());
        
        // Search with method name
        List<KnowledgeGraphService.CodeEntity> methodResults = 
                knowledgeGraphService.searchEntities("getSearchField", 10);
        assertFalse(methodResults.isEmpty());
        assertEquals("method", methodResults.get(0).getType());
    }
} 