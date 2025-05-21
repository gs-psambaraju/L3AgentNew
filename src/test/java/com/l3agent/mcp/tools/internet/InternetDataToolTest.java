package com.l3agent.mcp.tools.internet;

import com.l3agent.mcp.model.ToolResponse;
import com.l3agent.mcp.tools.internet.config.InternetDataConfig;
import com.l3agent.mcp.tools.internet.domain.DomainValidator;
import com.l3agent.mcp.tools.internet.fetcher.DataFetcher;
import com.l3agent.mcp.tools.internet.model.WebContent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class InternetDataToolTest {

    private InternetDataTool internetDataTool;
    
    @Mock
    private InternetDataConfig config;
    
    @Mock
    private DataFetcher dataFetcher;
    
    @Mock
    private com.l3agent.mcp.tools.internet.cache.InternetDataCache cache;
    
    @Mock
    private DomainValidator domainValidator;
    
    @Mock
    private com.l3agent.mcp.tools.internet.filter.ContentFilter contentFilter;
    
    @Mock
    private com.l3agent.mcp.tools.internet.ratelimit.RateLimiter rateLimiter;
    
    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        
        internetDataTool = new InternetDataTool(
            config, dataFetcher, cache, domainValidator, contentFilter, rateLimiter
        );
        
        // Set up common mock behavior
        when(config.getTrustedDomains()).thenReturn(Set.of("docs.oracle.com", "github.com"));
        when(config.getMaxRetries()).thenReturn(3);
        when(config.getRetryDelayMs()).thenReturn(1000L);
        when(config.getTimeoutSeconds()).thenReturn(10);
    }
    
    @Test
    public void testGetName() {
        assertEquals("internet_data", internetDataTool.getName());
    }
    
    @Test
    public void testGetParameters() {
        var parameters = internetDataTool.getParameters();
        assertEquals(4, parameters.size());
        
        // Verify parameter names
        var parameterNames = parameters.stream().map(p -> p.getName()).toList();
        assertTrue(parameterNames.contains("url"));
        assertTrue(parameterNames.contains("query"));
        assertTrue(parameterNames.contains("filter"));
        assertTrue(parameterNames.contains("force_refresh"));
    }
    
    @Test
    public void testExecute_MissingUrl() {
        Map<String, Object> params = new HashMap<>();
        
        ToolResponse response = internetDataTool.execute(params);
        
        assertFalse(response.isSuccess());
        assertEquals("URL parameter is required", response.getMessage());
    }
    
    @Test
    public void testExecute_DomainNotAllowed() {
        Map<String, Object> params = new HashMap<>();
        params.put("url", "https://example.com/docs");
        
        when(domainValidator.isAllowed(anyString())).thenReturn(false);
        
        ToolResponse response = internetDataTool.execute(params);
        
        assertFalse(response.isSuccess());
        assertTrue(response.getMessage().startsWith("Domain not in trusted list"));
    }
    
    @Test
    public void testExecute_RateLimitExceeded() {
        Map<String, Object> params = new HashMap<>();
        params.put("url", "https://docs.oracle.com/javase/17/docs/api/");
        
        when(domainValidator.isAllowed(anyString())).thenReturn(true);
        when(rateLimiter.allowRequest(anyString())).thenReturn(false);
        
        ToolResponse response = internetDataTool.execute(params);
        
        assertFalse(response.isSuccess());
        assertTrue(response.getMessage().startsWith("Rate limit exceeded for domain"));
    }
    
    @Test
    public void testExecute_CacheHit() throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("url", "https://docs.oracle.com/javase/17/docs/api/");
        
        when(domainValidator.isAllowed(anyString())).thenReturn(true);
        when(rateLimiter.allowRequest(anyString())).thenReturn(true);
        
        WebContent cachedContent = new WebContent(
            "https://docs.oracle.com/javase/17/docs/api/",
            "Java 17 API",
            "Java API documentation content",
            "Oracle"
        );
        when(cache.get(anyString())).thenReturn(cachedContent);
        
        ToolResponse response = internetDataTool.execute(params);
        
        assertTrue(response.isSuccess());
        assertEquals("Successfully retrieved data", response.getMessage());
        
        @SuppressWarnings("unchecked")
        Map<String, Object> responseData = (Map<String, Object>) response.getData();
        assertEquals("Java 17 API", responseData.get("title"));
        assertEquals("Java API documentation content", responseData.get("content"));
        
        // Verify no fetch was attempted
        verify(dataFetcher, never()).fetch(anyString());
    }
} 