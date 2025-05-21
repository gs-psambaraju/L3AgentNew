package com.l3agent.mcp.tools.internet;

import com.l3agent.mcp.MCPToolInterface;
import com.l3agent.mcp.model.ToolParameter;
import com.l3agent.mcp.model.ToolResponse;
import com.l3agent.mcp.tools.internet.cache.InternetDataCache;
import com.l3agent.mcp.tools.internet.config.InternetDataConfig;
import com.l3agent.mcp.tools.internet.domain.DomainValidator;
import com.l3agent.mcp.tools.internet.fetcher.DataFetcher;
import com.l3agent.mcp.tools.internet.filter.ContentFilter;
import com.l3agent.mcp.tools.internet.model.WebContent;
import com.l3agent.mcp.tools.internet.ratelimit.RateLimiter;
import com.l3agent.mcp.util.RetryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Tool for retrieving data from trusted internet sources.
 * Provides access to up-to-date API documentation and reference materials.
 */
@Component
public class InternetDataTool implements MCPToolInterface {
    
    private static final Logger logger = LoggerFactory.getLogger(InternetDataTool.class);
    private static final String TOOL_NAME = "internet_data";
    
    private final InternetDataConfig config;
    private final DataFetcher dataFetcher;
    private final InternetDataCache cache;
    private final DomainValidator domainValidator;
    private final ContentFilter contentFilter;
    private final RateLimiter rateLimiter;
    
    /**
     * Creates a new InternetDataTool with the required dependencies.
     */
    @Autowired
    public InternetDataTool(InternetDataConfig config, 
                           DataFetcher dataFetcher,
                           InternetDataCache cache,
                           DomainValidator domainValidator,
                           ContentFilter contentFilter,
                           RateLimiter rateLimiter) {
        this.config = config;
        this.dataFetcher = dataFetcher;
        this.cache = cache;
        this.domainValidator = domainValidator;
        this.contentFilter = contentFilter;
        this.rateLimiter = rateLimiter;
    }
    
    /**
     * Register the tool with the MCP.
     */
    @PostConstruct
    public void init() {
        logger.info("Initializing Internet Data Tool with {} trusted domains", 
                config.getTrustedDomains().size());
    }
    
    @Override
    public String getName() {
        return TOOL_NAME;
    }
    
    @Override
    public String getDescription() {
        return "Retrieves data from trusted internet sources to provide up-to-date API " +
               "documentation and reference materials. Security controls ensure only " +
               "trusted domains are accessed.";
    }
    
    @Override
    public List<ToolParameter> getParameters() {
        List<ToolParameter> parameters = new ArrayList<>();
        
        parameters.add(new ToolParameter(
                "url",
                "The URL to retrieve data from, must be from a trusted domain",
                "string",
                true,
                null
        ));
        
        parameters.add(new ToolParameter(
                "query",
                "Search query to find the relevant documentation",
                "string",
                false,
                null
        ));
        
        parameters.add(new ToolParameter(
                "filter",
                "Sections or keywords to extract from the content",
                "string",
                false,
                null
        ));
        
        parameters.add(new ToolParameter(
                "force_refresh",
                "Force fetching fresh data instead of using cache",
                "boolean",
                false,
                false
        ));
        
        return parameters;
    }
    
    @Override
    public ToolResponse execute(Map<String, Object> parameters) {
        logger.info("Executing Internet Data Tool with parameters: {}", parameters);
        
        try {
            // Parameter validation
            String url = getParameterAsString(parameters, "url");
            if (url == null || url.trim().isEmpty()) {
                return createErrorResponse("URL parameter is required");
            }
            
            String query = getParameterAsString(parameters, "query");
            String filter = getParameterAsString(parameters, "filter");
            boolean forceRefresh = getParameterAsBoolean(parameters, "force_refresh", false);
            
            // Validate domain for security
            if (!domainValidator.isAllowed(url)) {
                String errorMsg = "Domain not in trusted list: " + extractDomain(url);
                logger.warn(errorMsg);
                return createErrorResponse(errorMsg, Arrays.asList(
                        "Only trusted domains are allowed",
                        "Currently trusted domains: " + config.getTrustedDomains()
                ));
            }
            
            // Check rate limits
            if (!rateLimiter.allowRequest(url)) {
                String errorMsg = "Rate limit exceeded for domain: " + extractDomain(url);
                logger.warn(errorMsg);
                return createErrorResponse(errorMsg, Arrays.asList(
                        "Please try again later",
                        "Rate limits: " + config.getMaxRequestsPerMinute() + " requests per minute per domain"
                ));
            }
            
            // Try to get from cache if not forced to refresh
            WebContent content = null;
            if (!forceRefresh) {
                content = cache.get(url);
                if (content != null) {
                    logger.info("Cache hit for URL: {}", url);
                }
            }
            
            // Fetch data if not in cache or forced refresh
            if (content == null) {
                logger.info("Fetching data from URL: {}", url);
                try {
                    content = fetchWithRetry(url);
                    if (content != null) {
                        // Store in cache
                        cache.put(url, content);
                    }
                } catch (Exception e) {
                    logger.error("Error fetching data from URL: {}", url, e);
                    return createErrorResponse("Error fetching data: " + e.getMessage());
                }
            }
            
            // Handle case where content couldn't be fetched
            if (content == null) {
                return createErrorResponse("Failed to retrieve content from URL: " + url);
            }
            
            // Apply filtering if requested
            if (filter != null && !filter.trim().isEmpty()) {
                content = contentFilter.filter(content, filter);
            }
            
            // Apply query filtering if provided
            if (query != null && !query.trim().isEmpty()) {
                content = contentFilter.filterByQuery(content, query);
            }
            
            // Create the response with the data
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("url", url);
            responseData.put("title", content.getTitle());
            responseData.put("content", content.getContent());
            responseData.put("timestamp", content.getTimestamp().toString());
            responseData.put("source", content.getSource());
            responseData.put("fromCache", content.isFromCache());
            
            if (content.isFiltered()) {
                responseData.put("filteredBy", content.getFilterCriteria());
            }
            
            return new ToolResponse(true, "Successfully retrieved data", responseData);
            
        } catch (Exception e) {
            logger.error("Unexpected error executing Internet Data Tool", e);
            return createErrorResponse("Unexpected error: " + e.getMessage());
        }
    }
    
    /**
     * Fetches data from the given URL with retry logic.
     * 
     * @param url The URL to fetch data from
     * @return The web content if successful, null otherwise
     */
    private WebContent fetchWithRetry(String url) throws Exception {
        Callable<WebContent> fetchTask = () -> dataFetcher.fetch(url);
        
        return RetryUtil.withRetryAndTimeout(
                fetchTask,
                config.getMaxRetries(),
                config.getRetryDelayMs(),
                config.getTimeoutSeconds()
        );
    }
    
    /**
     * Creates an error response with the given message.
     * 
     * @param message The error message
     * @return A ToolResponse with success=false and the error message
     */
    private ToolResponse createErrorResponse(String message) {
        logger.warn("Internet Data Tool error: {}", message);
        return new ToolResponse(false, message, null);
    }
    
    /**
     * Creates an error response with the given message and warnings.
     * 
     * @param message The error message
     * @param warnings List of warnings to include
     * @return A ToolResponse with success=false, the error message, and warnings
     */
    private ToolResponse createErrorResponse(String message, List<String> warnings) {
        logger.warn("Internet Data Tool error: {}", message);
        ToolResponse response = new ToolResponse(false, message, null);
        warnings.forEach(response::addWarning);
        return response;
    }
    
    /**
     * Gets a parameter as a string from the parameters map.
     * 
     * @param parameters The parameters map
     * @param paramName The parameter name
     * @return The parameter value as a string, or null if not present
     */
    private String getParameterAsString(Map<String, Object> parameters, String paramName) {
        Object value = parameters.get(paramName);
        return value != null ? value.toString() : null;
    }
    
    /**
     * Gets a parameter as a boolean from the parameters map.
     * 
     * @param parameters The parameters map
     * @param paramName The parameter name
     * @param defaultValue The default value to return if parameter is not present
     * @return The parameter value as a boolean, or the default value if not present
     */
    private boolean getParameterAsBoolean(Map<String, Object> parameters, String paramName, boolean defaultValue) {
        Object value = parameters.get(paramName);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return Boolean.parseBoolean(value.toString());
    }
    
    /**
     * Extracts the domain from a URL.
     * 
     * @param url The URL to extract the domain from
     * @return The domain part of the URL
     */
    private String extractDomain(String url) {
        if (url == null || url.isEmpty()) {
            return "";
        }
        
        // Remove protocol if present
        String domain = url.toLowerCase();
        if (domain.startsWith("http://")) {
            domain = domain.substring(7);
        } else if (domain.startsWith("https://")) {
            domain = domain.substring(8);
        }
        
        // Remove path and query parameters
        int pathStart = domain.indexOf('/');
        if (pathStart > 0) {
            domain = domain.substring(0, pathStart);
        }
        
        return domain;
    }
} 