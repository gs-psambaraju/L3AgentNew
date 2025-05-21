package com.l3agent.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.l3agent.model.llm.LLMRequest;
import com.l3agent.model.llm.LLMResponse;
import com.l3agent.model.llm.ModelParameters;
import com.l3agent.service.LLMMetadataService;
import com.l3agent.service.LLMService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class GainsightLLMServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private LLMMetadataService metadataService;

    @Mock
    private LLMService fallbackService;

    @InjectMocks
    private GainsightLLMService gainsightLLMService;

    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // Set required fields via reflection
        ReflectionTestUtils.setField(gainsightLLMService, "accessKey", "test-access-key");
        ReflectionTestUtils.setField(gainsightLLMService, "chatCompletionUrl", "https://test-api.com/chat");
        ReflectionTestUtils.setField(gainsightLLMService, "embeddingUrl", "https://test-api.com/embed");
        ReflectionTestUtils.setField(gainsightLLMService, "defaultChatModel", "gpt-4o");
        ReflectionTestUtils.setField(gainsightLLMService, "defaultChatModelVersion", "2024-05-13");
        ReflectionTestUtils.setField(gainsightLLMService, "defaultEmbeddingModel", "text-embedding-ada-002");
        ReflectionTestUtils.setField(gainsightLLMService, "defaultEmbeddingModelVersion", "2");
        ReflectionTestUtils.setField(gainsightLLMService, "timeoutSeconds", 60);
        ReflectionTestUtils.setField(gainsightLLMService, "objectMapper", objectMapper);
    }

    @Test
    void testSuccessfulResponse() throws Exception {
        // Create a successful response
        ObjectNode responseJson = createSuccessfulResponse();
        
        // Mock RestTemplate to return the response
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>(objectMapper.writeValueAsString(responseJson), HttpStatus.OK));
        
        // Set up request and call the service
        ModelParameters params = new ModelParameters();
        LLMRequest request = new LLMRequest("Test prompt", params);
        LLMResponse response = gainsightLLMService.processRequest(request);
        
        // Verify the response
        assertNotNull(response);
        assertFalse(response.isError());
        assertEquals("This is a test response", response.getContent());
        assertEquals(10, response.getUsageMetrics().getPromptTokens());
        assertEquals(20, response.getUsageMetrics().getCompletionTokens());
        assertEquals(30, response.getUsageMetrics().getTotalTokens());
        assertEquals(0.005, response.getUsageMetrics().getEstimatedCost());
        assertEquals("gpt-4o", response.getModelInfo().getName());
        assertEquals("2024-05-13", response.getModelInfo().getVersion());
        assertEquals("Gainsight", response.getModelInfo().getProvider());
        assertEquals("stop", response.getFinishReason());
        
        // Verify metadata service was called
        verify(metadataService).recordInteraction(eq(request), any(LLMResponse.class));
    }
    
    @Test
    void testErrorHandling() throws Exception {
        // Mock RestTemplate to throw an exception
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST, "Bad request"));
        
        // Configure fallback service
        when(fallbackService.isAvailable()).thenReturn(true);
        when(fallbackService.getProviderName()).thenReturn("FallbackProvider");
        when(fallbackService.processRequest(any())).thenReturn(new LLMResponse("Fallback response"));
        
        // Set up request and call the service
        LLMRequest request = new LLMRequest("Test prompt", new ModelParameters());
        LLMResponse response = gainsightLLMService.processRequest(request);
        
        // Verify fallback was used
        assertNotNull(response);
        assertFalse(response.isError());
        assertEquals("Fallback response", response.getContent());
        
        // Verify the request metadata was updated with fallback info
        Map<String, Object> metadata = request.getMetadata();
        assertTrue(metadata.containsKey("fallback_used"));
        assertTrue((Boolean) metadata.get("fallback_used"));
        assertEquals("Gainsight", metadata.get("original_provider"));
        
        // Verify fallback service was called
        verify(fallbackService).processRequest(eq(request));
    }
    
    @Test
    void testInvalidResponseFormat() throws Exception {
        // Create an invalid response
        ObjectNode responseJson = objectMapper.createObjectNode();
        responseJson.put("result", false);
        responseJson.put("error", "Invalid request");
        
        // Mock RestTemplate to return the response
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>(objectMapper.writeValueAsString(responseJson), HttpStatus.OK));
        
        // Set up request and call the service
        LLMRequest request = new LLMRequest("Test prompt", new ModelParameters());
        LLMResponse response = gainsightLLMService.processRequest(request);
        
        // Verify the response is an error
        assertNotNull(response);
        assertTrue(response.isError());
        assertEquals("Invalid request", response.getErrorMessage());
    }
    
    @Test
    void testKnowledgeSourcesAndProvenanceTracking() throws Exception {
        // Create a response with knowledge sources
        ObjectNode responseJson = createSuccessfulResponse();
        responseJson.with("data").putArray("knowledge_sources")
            .add("document1.pdf")
            .add("document2.pdf");
        
        // Mock RestTemplate to return the response
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>(objectMapper.writeValueAsString(responseJson), HttpStatus.OK));
        
        // Set up request with provenance info
        Map<String, Object> metadata = new HashMap<>();
        Map<String, Object> provenance = new HashMap<>();
        provenance.put("source", "user_query");
        metadata.put("provenance", provenance);
        
        LLMRequest request = new LLMRequest("Test prompt", new ModelParameters())
                .withMetadata(metadata);
        
        LLMResponse response = gainsightLLMService.processRequest(request);
        
        // Verify knowledge sources were captured
        assertNotNull(response);
        assertNotNull(response.getKnowledgeSources());
        assertEquals(2, response.getKnowledgeSources().size());
        assertTrue(response.getKnowledgeSources().contains("document1.pdf"));
        assertTrue(response.getKnowledgeSources().contains("document2.pdf"));
    }
    
    @Test
    void testNetworkErrorFallback() throws Exception {
        // Mock RestTemplate to throw a network exception
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenThrow(new ResourceAccessException("Network error"));
        
        // No fallback service available
        ReflectionTestUtils.setField(gainsightLLMService, "fallbackService", null);
        
        // Set up request and call the service
        LLMRequest request = new LLMRequest("Test prompt", new ModelParameters());
        LLMResponse response = gainsightLLMService.processRequest(request);
        
        // Verify error response
        assertNotNull(response);
        assertTrue(response.isError());
        assertEquals("Error calling LLM: Network error", response.getErrorMessage());
    }
    
    @Test
    void testUnavailableService() {
        // Make service unavailable by setting null access key
        ReflectionTestUtils.setField(gainsightLLMService, "accessKey", null);
        
        // Set up request and call the service
        LLMRequest request = new LLMRequest("Test prompt", new ModelParameters());
        LLMResponse response = gainsightLLMService.processRequest(request);
        
        // Verify error response due to unavailable service
        assertNotNull(response);
        assertTrue(response.isError());
        assertTrue(response.getErrorMessage().contains("Gainsight LLM service unavailable"));
    }
    
    /**
     * Helper method to create a successful response JSON
     */
    private ObjectNode createSuccessfulResponse() {
        ObjectNode responseJson = objectMapper.createObjectNode();
        responseJson.put("result", true);
        
        ObjectNode dataNode = responseJson.putObject("data");
        dataNode.put("model", "gpt-4o");
        dataNode.put("version", "2024-05-13");
        
        ObjectNode usageNode = dataNode.putObject("usage");
        usageNode.put("prompt_tokens", 10);
        usageNode.put("completion_tokens", 20);
        usageNode.put("total_tokens", 30);
        usageNode.put("cost", 0.005);
        
        ObjectNode choiceNode = objectMapper.createObjectNode();
        ObjectNode messageNode = objectMapper.createObjectNode();
        messageNode.put("content", "This is a test response");
        messageNode.put("role", "assistant");
        choiceNode.set("message", messageNode);
        choiceNode.put("finish_reason", "stop");
        
        dataNode.putArray("choices").add(choiceNode);
        
        return responseJson;
    }
} 