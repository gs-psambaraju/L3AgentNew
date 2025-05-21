package com.l3agent.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.l3agent.model.llm.LLMRequest;
import com.l3agent.model.llm.LLMResponse;
import com.l3agent.model.llm.ModelParameters;
import com.l3agent.service.LLMMetadataService;
import com.l3agent.service.LLMService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Implementation of the LLMService using Gainsight's LLM API.
 * This is the primary LLM provider for L3Agent.
 */
@Service
public class GainsightLLMService implements LLMService {
    
    private static final Logger logger = LoggerFactory.getLogger(GainsightLLMService.class);
    private static final String PROVIDER_NAME = "Gainsight";
    
    // Rate limit retry configuration
    private static final int MAX_RETRIES = 5;
    private static final long INITIAL_RETRY_DELAY_MS = 1000; // 1 second
    private static final double RETRY_BACKOFF_MULTIPLIER = 2.0;
    
    private final String accessKey;
    private final String chatCompletionUrl;
    private final String embeddingUrl;
    private final String defaultChatModel;
    private final String defaultChatModelVersion;
    private final String defaultEmbeddingModel;
    private final String defaultEmbeddingModelVersion;
    private final int timeoutSeconds;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final ExecutorService executorService;
    
    @Autowired
    private LLMMetadataService metadataService;
    
    @Autowired(required = false)
    @Qualifier("fallbackLLMService")
    private LLMService fallbackService;
    
    /**
     * Constructs a new GainsightLLMService with configuration values.
     */
    public GainsightLLMService(
            @Value("${l3agent.llm.gainsight.access-key}") String accessKey,
            @Value("${l3agent.llm.gainsight.chat-completion-url}") String chatCompletionUrl,
            @Value("${l3agent.llm.gainsight.embedding-url}") String embeddingUrl,
            @Value("${l3agent.llm.gainsight.default-chat-model}") String defaultChatModel,
            @Value("${l3agent.llm.gainsight.default-chat-model-version}") String defaultChatModelVersion,
            @Value("${l3agent.llm.gainsight.default-embedding-model}") String defaultEmbeddingModel,
            @Value("${l3agent.llm.gainsight.default-embedding-model-version}") String defaultEmbeddingModelVersion,
            @Value("${l3agent.llm.timeout-seconds:60}") int timeoutSeconds) {
        
        this.accessKey = accessKey;
        this.chatCompletionUrl = chatCompletionUrl;
        this.embeddingUrl = embeddingUrl;
        this.defaultChatModel = defaultChatModel;
        this.defaultChatModelVersion = defaultChatModelVersion;
        this.defaultEmbeddingModel = defaultEmbeddingModel;
        this.defaultEmbeddingModelVersion = defaultEmbeddingModelVersion;
        this.timeoutSeconds = timeoutSeconds;
        
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
        this.executorService = Executors.newCachedThreadPool();
        
        logger.info("Gainsight LLM service initialized with default model: {} (version: {})", 
                defaultChatModel, defaultChatModelVersion);
        
        // Verify initialization parameters
        if (accessKey == null || accessKey.isBlank()) {
            logger.warn("Gainsight LLM service initialized without access key - service will not be available");
        }
        if (chatCompletionUrl == null || chatCompletionUrl.isBlank()) {
            logger.warn("Gainsight LLM service initialized without chat completion URL");
        }
    }
    
    @Override
    public LLMResponse processPrompt(String prompt, ModelParameters parameters, Map<String, Object> metadata) {
        if (metadata == null) {
            metadata = new HashMap<>();
        }
        
        // Add knowledge sources and provenance information if available
        if (!metadata.containsKey("knowledge_sources")) {
            metadata.put("knowledge_sources", new ArrayList<String>());
        }
        if (!metadata.containsKey("provenance")) {
            metadata.put("provenance", new HashMap<String, Object>());
        }
        
        LLMRequest request = new LLMRequest(prompt, parameters)
                .withMetadata(metadata);
        
        return processRequest(request);
    }
    
    @Override
    public CompletableFuture<LLMResponse> processPromptAsync(String prompt, ModelParameters parameters, Map<String, Object> metadata) {
        if (metadata == null) {
            metadata = new HashMap<>();
        }
        
        // Add knowledge sources and provenance information if available
        if (!metadata.containsKey("knowledge_sources")) {
            metadata.put("knowledge_sources", new ArrayList<String>());
        }
        if (!metadata.containsKey("provenance")) {
            metadata.put("provenance", new HashMap<String, Object>());
        }
        
        LLMRequest request = new LLMRequest(prompt, parameters)
                .withMetadata(metadata);
        
        return processRequestAsync(request);
    }
    
    @Override
    public LLMResponse processRequest(LLMRequest request) {
        logger.info("Processing LLM request: requestId={}", request.getRequestId());
        
        if (!isAvailable()) {
            logger.warn("Gainsight LLM service is not available, attempting to use fallback service");
            return useFallbackIfAvailable(request);
        }
        
        // Track retry attempts
        int retryCount = 0;
        long retryDelay = INITIAL_RETRY_DELAY_MS;
        
        while (true) {
            try {
                // Record the start time for latency calculation
                Instant startTime = Instant.now();
                
                // Prepare HTTP headers
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.set("access_key", accessKey);
                
                // Create the request body
                ObjectNode requestBody = createChatCompletionRequest(request);
                
                // Log the request details
                logger.debug("Making API call to URL: {}", chatCompletionUrl);
                
                // Create a sanitized copy of headers for logging
                HttpHeaders sanitizedHeaders = new HttpHeaders();
                headers.forEach((key, value) -> {
                    sanitizedHeaders.addAll(key, value); // Log all header values as is for now
                });
                
                logger.debug("Headers: {}", sanitizedHeaders);
                logger.debug("Request body: {}", objectMapper.writeValueAsString(requestBody));
                
                // Create the HTTP entity
                HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(requestBody), headers);
                
                // Call the Gainsight API
                ResponseEntity<String> response = restTemplate.postForEntity(chatCompletionUrl, entity, String.class);
                
                // Calculate the latency
                long latencyMs = Duration.between(startTime, Instant.now()).toMillis();
                
                // Validate response status code
                if (!response.getStatusCode().is2xxSuccessful()) {
                    logger.error("Received non-successful status code: {}", response.getStatusCode());
                    return useFallbackIfAvailable(request);
                }
                
                // Validate response body is not null or empty
                if (response.getBody() == null || response.getBody().isEmpty()) {
                    logger.error("Received empty response body from Gainsight API");
                    return useFallbackIfAvailable(request);
                }
                
                // Parse and convert the result
                JsonNode responseJson = objectMapper.readTree(response.getBody());
                LLMResponse llmResponse = createResponse(responseJson, request.getRequestId(), latencyMs);
                
                // Record the interaction metadata
                try {
                    metadataService.recordInteraction(request, llmResponse);
                } catch (Exception e) {
                    logger.error("Error recording metadata for LLM interaction: {}", e.getMessage(), e);
                    // Continue processing even if metadata recording fails
                }
                
                // Verify the response wasn't an error response
                if (llmResponse.isError()) {
                    logger.warn("Received error response from Gainsight API: {}", llmResponse.getErrorMessage());
                    return useFallbackIfAvailable(request);
                }
                
                return llmResponse;
                
            } catch (HttpClientErrorException e) {
                // Check specifically for rate limit errors (HTTP 429)
                if (e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                    if (retryCount < MAX_RETRIES) {
                        retryCount++;
                        logger.warn("Rate limit exceeded (429 TOO_MANY_REQUESTS). Retry attempt {}/{} after {} ms",
                                retryCount, MAX_RETRIES, retryDelay);
                        
                        try {
                            // Implement exponential backoff
                            Thread.sleep(retryDelay);
                            // Increase delay for next retry (exponential backoff)
                            retryDelay = (long) (retryDelay * RETRY_BACKOFF_MULTIPLIER);
                            // Continue the loop to retry
                            continue;
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            logger.error("Retry interrupted: {}", ie.getMessage());
                            break;
                        }
                    } else {
                        logger.error("Rate limit retry attempts exhausted ({}/{})", 
                                retryCount, MAX_RETRIES);
                    }
                }
                
                // Handle other client errors or when retries are exhausted
                logger.error("Client error calling Gainsight API: {} - {}", 
                        e.getStatusCode(), e.getResponseBodyAsString(), e);
                return useFallbackIfAvailable(request);
                
            } catch (HttpServerErrorException e) {
                logger.error("Server error from Gainsight API: {} - {}", e.getStatusCode(), e.getResponseBodyAsString(), e);
                return useFallbackIfAvailable(request);
            } catch (ResourceAccessException e) {
                logger.error("Network error accessing Gainsight API: {}", e.getMessage(), e);
                return useFallbackIfAvailable(request);
            } catch (Exception e) {
                logger.error("Unexpected error processing LLM request: {}", e.getMessage(), e);
                return LLMResponse.createError("Error calling LLM: " + e.getMessage(), request.getRequestId());
            }
        }
        
        // This should never be reached due to the returns in the catch blocks
        // but is needed to satisfy the compiler
        return useFallbackIfAvailable(request);
    }
    
    /**
     * Uses the fallback service if available, otherwise returns an error response.
     * 
     * @param request The LLM request
     * @return The LLM response from the fallback service or an error response
     */
    private LLMResponse useFallbackIfAvailable(LLMRequest request) {
        if (fallbackService != null && fallbackService.isAvailable()) {
            logger.info("Using fallback LLM service: {}", fallbackService.getProviderName());
            
            // Add fallback metadata
            Map<String, Object> metadata = request.getMetadata();
            if (metadata == null) {
                metadata = new HashMap<>();
                request.setMetadata(metadata);
            }
            metadata.put("fallback_used", true);
            metadata.put("original_provider", PROVIDER_NAME);
            
            return fallbackService.processRequest(request);
        }
        
        return LLMResponse.createError("Gainsight LLM service unavailable and no fallback available", request.getRequestId());
    }
    
    @Override
    public CompletableFuture<LLMResponse> processRequestAsync(LLMRequest request) {
        return CompletableFuture.supplyAsync(() -> processRequest(request), executorService);
    }
    
    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }
    
    @Override
    public String getDefaultModelName() {
        return defaultChatModel + " (" + defaultChatModelVersion + ")";
    }
    
    @Override
    public boolean isAvailable() {
        return accessKey != null && !accessKey.isBlank() && 
               chatCompletionUrl != null && !chatCompletionUrl.isBlank();
    }
    
    /**
     * Creates a chat completion request for the Gainsight API.
     * 
     * @param request The LLM request
     * @return The JSON request body for the Gainsight API
     */
    private ObjectNode createChatCompletionRequest(LLMRequest request) {
        ModelParameters params = request.getParameters();
        String modelName = defaultChatModel;
        String modelVersion = defaultChatModelVersion;
        int maxTokens = 1000;
        double temperature = 0.7;
        
        // Use parameters if provided
        if (params != null) {
            if (params.getModelName() != null && !params.getModelName().isBlank()) {
                // Check if the model name includes a version in parentheses
                String requestedModel = params.getModelName();
                if (requestedModel.contains("(") && requestedModel.contains(")")) {
                    int startPos = requestedModel.indexOf("(");
                    int endPos = requestedModel.indexOf(")");
                    modelName = requestedModel.substring(0, startPos).trim();
                    modelVersion = requestedModel.substring(startPos + 1, endPos).trim();
                } else {
                    modelName = requestedModel;
                }
            }
            
            if (params.getMaxTokens() > 0) {
                maxTokens = params.getMaxTokens();
            }
            
            if (params.getTemperature() >= 0) {
                temperature = params.getTemperature();
            }
        }
        
        // Create the request body
        ObjectNode requestBody = objectMapper.createObjectNode();
        
        // Create the messages array
        ArrayNode messagesArray = objectMapper.createArrayNode();
        
        // Add system message if present in metadata
        String systemMessage = "You are a helpful AI assistant that helps developers debug their code by analyzing logs and code.";
        if (request.getMetadata() != null && request.getMetadata().containsKey("system_message")) {
            systemMessage = (String) request.getMetadata().get("system_message");
        }
        
        // Add the system message
        ObjectNode systemMessageNode = objectMapper.createObjectNode();
        systemMessageNode.put("role", "system");
        systemMessageNode.put("content", systemMessage);
        messagesArray.add(systemMessageNode);
        
        // Add the user message
        ObjectNode userMessageNode = objectMapper.createObjectNode();
        userMessageNode.put("role", "user");
        userMessageNode.put("content", request.getPrompt());
        messagesArray.add(userMessageNode);
        
        // Add messages to the request body
        requestBody.set("messages", messagesArray);
        
        // Add model details
        requestBody.put("model", modelName);
        requestBody.put("version", modelVersion);
        requestBody.put("max_tokens", maxTokens);
        requestBody.put("temperature", temperature);
        
        // Add request ID for tracing
        requestBody.put("request_id", request.getRequestId());
        
        return requestBody;
    }
    
    /**
     * Creates a LLMResponse from the Gainsight API response.
     * 
     * @param responseJson The JSON response from the Gainsight API
     * @param requestId The ID of the original request
     * @param latencyMs The latency in milliseconds
     * @return The LLM response
     */
    private LLMResponse createResponse(JsonNode responseJson, String requestId, long latencyMs) {
        // Validate API response structure
        if (!responseJson.has("result") || !responseJson.get("result").asBoolean() || 
                !responseJson.has("data")) {
            String errorMessage = "Invalid response from Gainsight API";
            if (responseJson.has("error") && responseJson.get("error").isTextual()) {
                errorMessage = responseJson.get("error").asText();
            }
            logger.error("API response validation failed: {}", errorMessage);
            return LLMResponse.createError(errorMessage, requestId);
        }
        
        JsonNode data = responseJson.get("data");
        
        // Validate choices array
        if (!data.has("choices") || data.get("choices").isEmpty()) {
            logger.error("No choices returned in response");
            return LLMResponse.createError("No choices returned in response", requestId);
        }
        
        try {
            // Get content from the first choice
            JsonNode firstChoice = data.get("choices").get(0);
            
            // Validate message structure
            if (!firstChoice.has("message") || !firstChoice.get("message").has("content")) {
                logger.error("Invalid message structure in response");
                return LLMResponse.createError("Invalid message structure in response", requestId);
            }
            
            String content = firstChoice.get("message").get("content").asText();
            
            // Get model information
            String modelName = data.has("model") ? data.get("model").asText() : defaultChatModel;
            String modelVersion = data.has("version") ? data.get("version").asText() : defaultChatModelVersion;
            
            // Create model info
            LLMResponse.ModelInfo modelInfo = new LLMResponse.ModelInfo(
                    modelName,
                    modelVersion,
                    PROVIDER_NAME
            );
            
            // Create usage metrics if available
            LLMResponse.UsageMetrics usageMetrics = null;
            if (data.has("usage")) {
                JsonNode usage = data.get("usage");
                int promptTokens = usage.has("prompt_tokens") ? usage.get("prompt_tokens").asInt() : 0;
                int completionTokens = usage.has("completion_tokens") ? usage.get("completion_tokens").asInt() : 0;
                int totalTokens = usage.has("total_tokens") ? usage.get("total_tokens").asInt() : (promptTokens + completionTokens);
                double cost = usage.has("cost") ? usage.get("cost").asDouble() : 0.0;
                
                usageMetrics = new LLMResponse.UsageMetrics(promptTokens, completionTokens);
                usageMetrics.setTotalTokens(totalTokens);
                usageMetrics.setEstimatedCost(cost);
            }
            
            // Create and return the response
            LLMResponse response = new LLMResponse(content)
                    .withRequestId(requestId)
                    .withLatencyMs(latencyMs)
                    .withModelInfo(modelInfo);
            
            // Add usage metrics if available
            if (usageMetrics != null) {
                response = response.withUsageMetrics(usageMetrics);
            }
            
            // Add knowledge sources and provenance if available in the response
            if (data.has("knowledge_sources") && data.get("knowledge_sources").isArray()) {
                JsonNode sourceNodes = data.get("knowledge_sources");
                List<String> sources = new ArrayList<>();
                for (JsonNode node : sourceNodes) {
                    if (node.isTextual()) {
                        sources.add(node.asText());
                    }
                }
                response = response.withKnowledgeSources(sources);
            }
            
            // Add any available finish reason
            if (firstChoice.has("finish_reason") && firstChoice.get("finish_reason").isTextual()) {
                response = response.withFinishReason(firstChoice.get("finish_reason").asText());
            }
            
            return response;
        } catch (Exception e) {
            logger.error("Error parsing Gainsight API response: {}", e.getMessage(), e);
            return LLMResponse.createError("Error parsing response: " + e.getMessage(), requestId);
        }
    }
} 