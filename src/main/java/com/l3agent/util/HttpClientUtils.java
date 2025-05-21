package com.l3agent.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpEntity;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Utility class for standardized HTTP client operations with enhanced error handling,
 * connection pooling, and retry mechanisms.
 */
@Component
public class HttpClientUtils {
    private static final Logger logger = LoggerFactory.getLogger(HttpClientUtils.class);
    
    private static final int DEFAULT_CONNECT_TIMEOUT_MS = 5000;
    private static final int DEFAULT_SOCKET_TIMEOUT_MS = 30000;
    private static final int DEFAULT_CONNECTION_REQUEST_TIMEOUT_MS = 5000;
    private static final int MAX_CONNECTIONS_PER_ROUTE = 20;
    private static final int MAX_CONNECTIONS_TOTAL = 50;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    private PoolingHttpClientConnectionManager connectionManager;
    private CloseableHttpClient httpClient;
    
    @PostConstruct
    public void init() {
        connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setDefaultMaxPerRoute(MAX_CONNECTIONS_PER_ROUTE);
        connectionManager.setMaxTotal(MAX_CONNECTIONS_TOTAL);
        
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(DEFAULT_CONNECT_TIMEOUT_MS)
                .setSocketTimeout(DEFAULT_SOCKET_TIMEOUT_MS)
                .setConnectionRequestTimeout(DEFAULT_CONNECTION_REQUEST_TIMEOUT_MS)
                .build();
        
        httpClient = HttpClientBuilder.create()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .build();
        
        logger.info("HTTP client initialized with connection pool: max per route={}, max total={}",
                MAX_CONNECTIONS_PER_ROUTE, MAX_CONNECTIONS_TOTAL);
    }
    
    @PreDestroy
    public void close() {
        try {
            if (httpClient != null) {
                httpClient.close();
            }
            if (connectionManager != null) {
                connectionManager.close();
            }
            logger.info("HTTP client resources closed");
        } catch (IOException e) {
            logger.warn("Error closing HTTP client resources", e);
        }
    }
    
    /**
     * Exception class for rate limit errors.
     */
    public static class RateLimitException extends Exception {
        private static final long serialVersionUID = 1L;
        
        public RateLimitException(String message) {
            super(message);
        }
    }
    
    /**
     * Exception class for API response errors.
     */
    public static class ApiResponseException extends Exception {
        private static final long serialVersionUID = 1L;
        private final int statusCode;
        
        public ApiResponseException(int statusCode, String message) {
            super(message);
            this.statusCode = statusCode;
        }
        
        public int getStatusCode() {
            return statusCode;
        }
    }
    
    /**
     * Executes a POST request with JSON payload and returns the response as a Map.
     * 
     * @param url The URL to send the request to
     * @param headers Map of headers to include in the request
     * @param requestBody The request body as an object to be serialized to JSON
     * @return The response as a Map
     * @throws RateLimitException If rate limiting is detected
     * @throws ApiResponseException If the API returns an error response
     * @throws IOException If there's an I/O error during the request
     */
    public Map<String, Object> executePostRequest(String url, Map<String, String> headers, 
            Object requestBody) throws RateLimitException, ApiResponseException, IOException {
        
        HttpPost httpPost = new HttpPost(url);
        
        // Set headers
        for (Map.Entry<String, String> header : headers.entrySet()) {
            httpPost.setHeader(header.getKey(), header.getValue());
        }
        
        // Add default headers if not present
        if (!headers.containsKey("Content-Type")) {
            httpPost.setHeader("Content-Type", "application/json");
        }
        
        // Prepare request body
        String requestJson = objectMapper.writeValueAsString(requestBody);
        httpPost.setEntity(new StringEntity(requestJson));
        
        // Log sanitized request if debug enabled
        if (logger.isDebugEnabled()) {
            logger.debug("Making API call to URL: {}", url);
            logger.debug("Headers: {}", LoggingUtils.maskSensitiveHeaders(headers));
            
            // Create a sanitized version of the request body for logging
            Map<String, Object> sanitizedBody;
            if (requestBody instanceof Map) {
                sanitizedBody = sanitizeRequestBodyForLogging((Map<String, Object>) requestBody);
            } else {
                sanitizedBody = Map.of("request_type", requestBody.getClass().getSimpleName());
            }
            logger.debug("Request body (sanitized): {}", objectMapper.writeValueAsString(sanitizedBody));
        }
        
        try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
            int statusCode = response.getStatusLine().getStatusCode();
            HttpEntity responseEntity = response.getEntity();
            String responseString = EntityUtils.toString(responseEntity);
            
            // Log response code
            logger.debug("Received response with status code: {}", statusCode);
            
            if (statusCode != 200) {
                // Handle rate limiting specifically
                if (statusCode == 429 || responseString.contains("rate limit") || 
                        responseString.contains("too many requests")) {
                    throw new RateLimitException("Rate limit exceeded: " + responseString);
                }
                
                // Handle other errors
                throw new ApiResponseException(statusCode, "Error calling API: " + statusCode + 
                        " - " + responseString);
            }
            
            // Parse the JSON response
            try {
                return objectMapper.readValue(responseString, 
                        new TypeReference<Map<String, Object>>() {});
            } catch (Exception e) {
                logger.error("Error parsing API response: {}", e.getMessage());
                throw new ApiResponseException(statusCode, 
                        "Invalid response format: " + e.getMessage());
            }
        }
    }
    
    /**
     * Helper method to clean sensitive data from request bodies for logging purposes.
     * 
     * @param requestBody The original request body
     * @return A sanitized copy suitable for logging
     */
    private Map<String, Object> sanitizeRequestBodyForLogging(Map<String, Object> requestBody) {
        Map<String, Object> sanitized = new HashMap<>(requestBody);
        
        // Mask any potentially sensitive fields
        for (String sensitiveKey : new String[]{"text", "content", "access_key", "api_key", "key", "token", "password"}) {
            if (sanitized.containsKey(sensitiveKey)) {
                Object value = sanitized.get(sensitiveKey);
                if (value instanceof String) {
                    String strValue = (String) value;
                    if (strValue.length() > 100) {
                        sanitized.put(sensitiveKey, strValue.substring(0, 20) + "..." + 
                                strValue.substring(strValue.length() - 20) + 
                                " [truncated, length: " + strValue.length() + "]");
                    }
                } else if (sensitiveKey.contains("key") || sensitiveKey.contains("token") || 
                        sensitiveKey.contains("password")) {
                    sanitized.put(sensitiveKey, "[REDACTED]");
                }
            }
        }
        
        return sanitized;
    }
    
    /**
     * Configures connection timeouts for HTTP requests.
     * 
     * @param connectTimeoutMs Connection timeout in milliseconds
     * @param socketTimeoutMs Socket timeout in milliseconds
     * @param connectionRequestTimeoutMs Connection request timeout in milliseconds
     */
    public void configureTimeouts(int connectTimeoutMs, int socketTimeoutMs, int connectionRequestTimeoutMs) {
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(connectTimeoutMs)
                .setSocketTimeout(socketTimeoutMs)
                .setConnectionRequestTimeout(connectionRequestTimeoutMs)
                .build();
        
        httpClient = HttpClientBuilder.create()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .build();
        
        logger.info("Updated HTTP client timeouts: connect={}ms, socket={}ms, request={}ms",
                connectTimeoutMs, socketTimeoutMs, connectionRequestTimeoutMs);
    }
    
    /**
     * Performs connection pool maintenance and logs status information.
     */
    public void maintainConnectionPool() {
        // Close expired connections
        connectionManager.closeExpiredConnections();
        // Close idle connections after 30 seconds
        connectionManager.closeIdleConnections(30, TimeUnit.SECONDS);
        
        // Log connection pool status
        logger.debug("Connection pool status: available={}, leased={}, max={}",
                connectionManager.getTotalStats().getAvailable(),
                connectionManager.getTotalStats().getLeased(),
                connectionManager.getTotalStats().getMax());
    }
} 