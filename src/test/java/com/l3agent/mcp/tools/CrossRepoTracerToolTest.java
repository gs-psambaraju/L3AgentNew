package com.l3agent.mcp.tools;

import com.l3agent.mcp.model.ToolParameter;
import com.l3agent.mcp.model.ToolResponse;
import com.l3agent.mcp.tools.crossrepo.CodeSearcher;
import com.l3agent.mcp.tools.crossrepo.RepositoryScanner;
import com.l3agent.mcp.tools.crossrepo.model.CodeReference;
import com.l3agent.mcp.tools.crossrepo.model.CrossRepoResult;
import com.l3agent.mcp.tools.crossrepo.model.RepositoryInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the CrossRepoTracerTool class.
 */
class CrossRepoTracerToolTest {

    @Mock
    private RepositoryScanner repositoryScanner;

    @Mock
    private CodeSearcher codeSearcher;

    @InjectMocks
    private CrossRepoTracerTool tracerTool;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        ReflectionTestUtils.setField(tracerTool, "defaultExtensions", "java,xml,properties");
    }

    @Test
    void testGetParametersContainsRequiredParameters() {
        List<ToolParameter> parameters = tracerTool.getParameters();
        
        assertNotNull(parameters);
        assertFalse(parameters.isEmpty());
        
        // Verify required parameters are present
        assertTrue(parameters.stream().anyMatch(p -> "searchTerm".equals(p.getName())));
        assertTrue(parameters.stream().anyMatch(p -> "useRegex".equals(p.getName())));
        assertTrue(parameters.stream().anyMatch(p -> "caseSensitive".equals(p.getName())));
        assertTrue(parameters.stream().anyMatch(p -> "extensions".equals(p.getName())));
        assertTrue(parameters.stream().anyMatch(p -> "repositories".equals(p.getName())));
        assertTrue(parameters.stream().anyMatch(p -> "operation".equals(p.getName())));
        
        // Verify searchTerm is required
        assertTrue(parameters.stream()
            .filter(p -> "searchTerm".equals(p.getName()))
            .findFirst()
            .orElseThrow()
            .isRequired());
    }

    @Test
    void testExecuteSearchReturnsResults() {
        // Set up mock data
        List<CodeReference> references = new ArrayList<>();
        references.add(new CodeReference("repo1", "src/main/java/Test.java", 10, "public void test() {", 
                Arrays.asList("import org.junit.Test;", "", "public class Test {")));
        
        CrossRepoResult mockResult = new CrossRepoResult("test");
        mockResult.setReferences(references);
        
        // Configure mocks
        when(codeSearcher.search(anyString(), anyBoolean(), anyBoolean(), any(String[].class)))
            .thenReturn(mockResult);
        
        // Create parameters
        Map<String, Object> params = new HashMap<>();
        params.put("searchTerm", "test");
        params.put("operation", "search");
        
        // Execute
        ToolResponse response = tracerTool.execute(params);
        
        // Verify
        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertNotNull(response.getData());
        
        @SuppressWarnings("unchecked")
        Map<String, Object> resultData = (Map<String, Object>) response.getData();
        assertEquals("test", resultData.get("searchTerm"));
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> resultRefs = (List<Map<String, Object>>) resultData.get("references");
        assertNotNull(resultRefs);
        assertEquals(1, resultRefs.size());
        assertEquals("repo1", resultRefs.get(0).get("repository"));
    }

    @Test
    void testListRepositoriesOperation() {
        // Set up mock data
        List<RepositoryInfo> mockRepos = new ArrayList<>();
        mockRepos.add(new RepositoryInfo("repo1", "/path/to/repo1", "Test repo 1"));
        mockRepos.add(new RepositoryInfo("repo2", "/path/to/repo2", "Test repo 2"));
        
        // Configure mocks
        when(repositoryScanner.getRepositories()).thenReturn(mockRepos);
        
        // Create parameters
        Map<String, Object> params = new HashMap<>();
        params.put("operation", "listRepositories");
        
        // Execute
        ToolResponse response = tracerTool.execute(params);
        
        // Verify
        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertNotNull(response.getData());
        
        @SuppressWarnings("unchecked")
        Map<String, Object> resultData = (Map<String, Object>) response.getData();
        assertEquals(2, resultData.get("count"));
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> repos = (List<Map<String, Object>>) resultData.get("repositories");
        assertNotNull(repos);
        assertEquals(2, repos.size());
        assertEquals("repo1", repos.get(0).get("name"));
        assertEquals("repo2", repos.get(1).get("name"));
    }

    @Test
    void testInvalidOperationReturnsError() {
        // Create parameters with invalid operation
        Map<String, Object> params = new HashMap<>();
        params.put("operation", "invalidOperation");
        
        // Execute
        ToolResponse response = tracerTool.execute(params);
        
        // Verify
        assertNotNull(response);
        assertFalse(response.isSuccess());
        assertNotNull(response.getErrors());
        assertFalse(response.getErrors().isEmpty());
        assertTrue(response.getErrors().get(0).contains("Unknown operation"));
    }

    @Test
    void testMissingSearchTermReturnsError() {
        // Create parameters without searchTerm
        Map<String, Object> params = new HashMap<>();
        params.put("operation", "search");
        
        // Execute
        ToolResponse response = tracerTool.execute(params);
        
        // Verify
        assertNotNull(response);
        assertFalse(response.isSuccess());
        assertNotNull(response.getErrors());
        assertFalse(response.getErrors().isEmpty());
        assertTrue(response.getErrors().get(0).contains("Missing required parameter"));
    }
} 