package com.l3agent.controller;

import com.l3agent.service.CodeRepositoryService;
import com.l3agent.service.KnowledgeGraphService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

public class L3AgentControllerTest {

    @InjectMocks
    private L3AgentController controller;
    
    @Mock
    private KnowledgeGraphService knowledgeGraphService;
    
    @Mock
    private CodeRepositoryService codeRepositoryService;
    
    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }
    
    @Test
    public void testHybridSearch() {
        // Mock data
        String query = "user authentication";
        int maxResults = 10;
        
        // Mock semantic search results
        List<CodeRepositoryService.CodeSnippet> semanticResults = new ArrayList<>();
        CodeRepositoryService.CodeSnippet snippet = new CodeRepositoryService.CodeSnippet();
        snippet.setFilePath("/src/auth/UserAuthService.java");
        snippet.setSnippet("public User authenticate(String username, String password) { ... }");
        snippet.setStartLine(45);
        snippet.setEndLine(50);
        snippet.setRelevance(0.92f);
        semanticResults.add(snippet);
        
        // Mock knowledge graph search results
        List<KnowledgeGraphService.CodeEntity> structuralResults = new ArrayList<>();
        KnowledgeGraphService.CodeEntity entity = new KnowledgeGraphService.CodeEntity(
                "java:com.example.UserAuthService", 
                "class", 
                "UserAuthService", 
                "com.example.UserAuthService",
                "/src/auth/UserAuthService.java",
                10,
                100
        );
        structuralResults.add(entity);
        
        // Set up mocks
        when(codeRepositoryService.searchCode(anyString(), anyInt())).thenReturn(semanticResults);
        when(knowledgeGraphService.searchEntities(anyString(), anyInt())).thenReturn(structuralResults);
        
        // Perform the hybrid search
        ResponseEntity<Map<String, Object>> response = 
                controller.hybridSearch(query, maxResults, 0.7f, 0.3f);
        
        // Verify the response
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        
        Map<String, Object> responseBody = response.getBody();
        assertEquals(query, responseBody.get("query"));
        
        Map<String, Object> combinedResults = (Map<String, Object>) responseBody.get("results");
        assertNotNull(combinedResults);
        assertEquals(1, combinedResults.get("semantic_count"));
        assertEquals(1, combinedResults.get("structural_count"));
        
        // Verify processing time is included
        assertTrue(responseBody.containsKey("processing_time_ms"));
    }
    
    @Test
    public void testKnowledgeGraphVisualization() {
        // Mock data
        String entityId = "java:com.example.UserService";
        int depth = 2;
        
        // Mock relationships
        List<KnowledgeGraphService.CodeRelationship> relationships = new ArrayList<>();
        KnowledgeGraphService.CodeRelationship relationship = 
                new KnowledgeGraphService.CodeRelationship(
                        entityId,
                        "java:com.example.User",
                        "CONTAINS"
                );
        relationships.add(relationship);
        
        // Set up mock
        when(knowledgeGraphService.findRelatedEntities(anyString(), anyInt())).thenReturn(relationships);
        
        // Perform the visualization request
        ResponseEntity<Map<String, Object>> response = controller.visualizeGraph(entityId, depth);
        
        // Verify the response
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        
        Map<String, Object> responseBody = response.getBody();
        assertTrue(responseBody.containsKey("nodes"));
        assertTrue(responseBody.containsKey("edges"));
        
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) responseBody.get("nodes");
        List<Map<String, Object>> edges = (List<Map<String, Object>>) responseBody.get("edges");
        
        assertFalse(nodes.isEmpty());
        assertFalse(edges.isEmpty());
        
        // Verify totals are included
        assertEquals(nodes.size(), responseBody.get("total_nodes"));
        assertEquals(edges.size(), responseBody.get("total_edges"));
    }
    
    @Test
    public void testSystemMetrics() {
        // Set up mocks
        when(knowledgeGraphService.getEntityCount()).thenReturn(100);
        when(knowledgeGraphService.getRelationshipCount()).thenReturn(150);
        when(knowledgeGraphService.isAvailable()).thenReturn(true);
        
        // Perform the metrics request
        ResponseEntity<Map<String, Object>> response = controller.getSystemMetrics();
        
        // Verify the response
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        
        Map<String, Object> responseBody = response.getBody();
        assertTrue(responseBody.containsKey("knowledge_graph"));
        
        Map<String, Object> graphMetrics = (Map<String, Object>) responseBody.get("knowledge_graph");
        assertEquals(100, graphMetrics.get("entity_count"));
        assertEquals(150, graphMetrics.get("relationship_count"));
        assertEquals(true, graphMetrics.get("available"));
    }
} 