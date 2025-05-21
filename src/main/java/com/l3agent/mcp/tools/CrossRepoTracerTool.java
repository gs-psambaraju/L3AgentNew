package com.l3agent.mcp.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.l3agent.mcp.MCPToolInterface;
import com.l3agent.mcp.model.ToolParameter;
import com.l3agent.mcp.model.ToolResponse;
import com.l3agent.mcp.tools.crossrepo.CodeSearcher;
import com.l3agent.mcp.tools.crossrepo.RepositoryScanner;
import com.l3agent.mcp.tools.crossrepo.model.CodeReference;
import com.l3agent.mcp.tools.crossrepo.model.CrossRepoResult;
import com.l3agent.mcp.tools.crossrepo.model.RepositoryInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Cross-Repository Tracer Tool - Traces code usage across multiple repositories.
 * 
 * This tool helps support engineers understand how certain code patterns, functions, or configurations
 * are used across multiple codebases. It performs searches across static repositories located in a
 * specified directory.
 */
@Component
public class CrossRepoTracerTool implements MCPToolInterface {
    
    private static final Logger logger = LoggerFactory.getLogger(CrossRepoTracerTool.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final String TOOL_NAME = "cross_repo_tracer";
    
    @Value("${l3agent.crossrepo.default-extensions:java,xml,properties,yaml,yml}")
    private String defaultExtensions;
    
    private final RepositoryScanner repositoryScanner;
    private final CodeSearcher codeSearcher;
    
    /**
     * Creates a new cross-repository tracer tool.
     * 
     * @param repositoryScanner The repository scanner
     * @param codeSearcher The code searcher
     */
    @Autowired
    public CrossRepoTracerTool(RepositoryScanner repositoryScanner, CodeSearcher codeSearcher) {
        this.repositoryScanner = repositoryScanner;
        this.codeSearcher = codeSearcher;
    }
    
    @Override
    public String getName() {
        return TOOL_NAME;
    }
    
    @Override
    public String getDescription() {
        return "Traces code patterns, functions, or configurations across multiple codebases";
    }
    
    @Override
    public List<ToolParameter> getParameters() {
        List<ToolParameter> parameters = new ArrayList<>();
        
        parameters.add(new ToolParameter(
                "searchTerm",
                "The code pattern, function, or string to search for across repositories",
                "string",
                true,
                null));
        
        parameters.add(new ToolParameter(
                "useRegex",
                "Whether to interpret the search term as a regular expression",
                "boolean",
                false,
                false));
        
        parameters.add(new ToolParameter(
                "caseSensitive",
                "Whether the search should be case-sensitive",
                "boolean",
                false,
                false));
        
        parameters.add(new ToolParameter(
                "extensions",
                "File extensions to search (comma-separated: java,xml,etc.)",
                "string",
                false,
                defaultExtensions));
        
        parameters.add(new ToolParameter(
                "repositories",
                "Specific repositories to search (comma-separated, empty for all)",
                "string",
                false,
                ""));
        
        parameters.add(new ToolParameter(
                "operation",
                "Operation to perform: search, listRepositories, or scanRepositories",
                "string",
                false,
                "search"));
        
        return parameters;
    }
    
    @Override
    public ToolResponse execute(Map<String, Object> parameters) {
        try {
            String operation = getStringParameter(parameters, "operation", "search");
            
            switch (operation) {
                case "search":
                    return executeSearch(parameters);
                case "listRepositories":
                    return executeListRepositories();
                case "scanRepositories":
                    return executeScanRepositories();
                default:
                    return createErrorResponse("Unknown operation: " + operation);
            }
        } catch (Exception e) {
            logger.error("Error executing Cross-Repository Tracer: {}", e.getMessage(), e);
            return createErrorResponse("Error during execution: " + e.getMessage());
        }
    }
    
    /**
     * Executes a search operation across repositories.
     * 
     * @param parameters The search parameters
     * @return The search results
     */
    private ToolResponse executeSearch(Map<String, Object> parameters) {
        // Get search parameters
        String searchTerm = getStringParameter(parameters, "searchTerm", null);
        if (searchTerm == null || searchTerm.isEmpty()) {
            return createErrorResponse("Missing required parameter: searchTerm");
        }
        
        boolean useRegex = getBooleanParameter(parameters, "useRegex", false);
        boolean caseSensitive = getBooleanParameter(parameters, "caseSensitive", false);
        
        // Get file extensions to search
        Set<String> extensions = new HashSet<>();
        String extensionsParam = getStringParameter(parameters, "extensions", defaultExtensions);
        if (extensionsParam != null && !extensionsParam.isEmpty()) {
            extensions.addAll(Arrays.asList(extensionsParam.split(","))
                    .stream()
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList()));
        }
        
        // Get repository filter
        List<String> repositories = new ArrayList<>();
        String reposParam = getStringParameter(parameters, "repositories", "");
        if (reposParam != null && !reposParam.isEmpty()) {
            repositories.addAll(Arrays.asList(reposParam.split(","))
                    .stream()
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList()));
        }
        
        // Perform the search
        CrossRepoResult result;
        if (!repositories.isEmpty()) {
            // Search only in specified repositories
            result = searchInRepositories(searchTerm, useRegex, caseSensitive, 
                    repositories, extensions.toArray(new String[0]));
        } else {
            // Search in all repositories
            result = codeSearcher.search(searchTerm, useRegex, caseSensitive, 
                    extensions.toArray(new String[0]));
        }
        
        // Convert result to a format for the response
        Map<String, Object> resultData = convertResultToMap(result);
        
        return new ToolResponse(
                true,
                String.format("Found %d matches in %d repositories", 
                        result.getReferenceCount(), result.getMatchedRepositoryCount()),
                resultData);
    }
    
    /**
     * Executes a repository listing operation.
     * 
     * @return The repository listing
     */
    private ToolResponse executeListRepositories() {
        List<RepositoryInfo> repositories = repositoryScanner.getRepositories();
        
        List<Map<String, Object>> reposList = new ArrayList<>();
        for (RepositoryInfo repo : repositories) {
            Map<String, Object> repoMap = new HashMap<>();
            repoMap.put("name", repo.getName());
            repoMap.put("path", repo.getPath());
            if (repo.getDescription() != null) {
                repoMap.put("description", repo.getDescription());
            }
            reposList.add(repoMap);
        }
        
        Map<String, Object> resultData = new HashMap<>();
        resultData.put("repositories", reposList);
        resultData.put("count", repositories.size());
        
        return new ToolResponse(
                true,
                String.format("Found %d repositories", repositories.size()),
                resultData);
    }
    
    /**
     * Executes a repository scan operation.
     * 
     * @return The scan results
     */
    private ToolResponse executeScanRepositories() {
        int count = repositoryScanner.scanRepositories();
        
        Map<String, Object> resultData = new HashMap<>();
        resultData.put("repositoriesFound", count);
        
        return new ToolResponse(
                true,
                String.format("Found %d repositories during scan", count),
                resultData);
    }
    
    /**
     * Searches for a term in specific repositories.
     * 
     * @param searchTerm The search term
     * @param useRegex Whether to use regex
     * @param caseSensitive Whether to be case-sensitive
     * @param repositories The repositories to search
     * @param extensions The file extensions to include
     * @return The search results
     */
    private CrossRepoResult searchInRepositories(String searchTerm, boolean useRegex, 
            boolean caseSensitive, List<String> repositories, String[] extensions) {
        
        CrossRepoResult result = new CrossRepoResult(searchTerm);
        result.setUseRegex(useRegex);
        result.setCaseSensitive(caseSensitive);
        
        long startTime = System.currentTimeMillis();
        
        for (String repoName : repositories) {
            RepositoryInfo repo = repositoryScanner.getRepository(repoName);
            if (repo != null) {
                // Search this specific repository
                CrossRepoResult singleResult = codeSearcher.search(
                    searchTerm, useRegex, caseSensitive, extensions);
                
                // Filter to only this repository's results
                singleResult.getReferences().stream()
                    .filter(ref -> ref.getRepository().equals(repoName))
                    .forEach(result::addReference);
                
                result.addSearchedRepository(repoName);
            } else {
                logger.warn("Repository not found: {}", repoName);
            }
        }
        
        result.setSearchTimeMs(System.currentTimeMillis() - startTime);
        return result;
    }
    
    /**
     * Converts a search result to a map.
     * 
     * @param result The search result
     * @return The result as a map
     */
    private Map<String, Object> convertResultToMap(CrossRepoResult result) {
        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("searchTerm", result.getSearchTerm());
        resultMap.put("useRegex", result.isUseRegex());
        resultMap.put("caseSensitive", result.isCaseSensitive());
        resultMap.put("searchTimeMs", result.getSearchTimeMs());
        resultMap.put("totalMatches", result.getReferenceCount());
        resultMap.put("repositoriesSearched", result.getSearchedRepositories().size());
        resultMap.put("repositoriesWithMatches", result.getMatchedRepositoryCount());
        
        // Add extensions
        resultMap.put("extensions", new ArrayList<>(result.getIncludedExtensions()));
        
        // Add repositories searched
        resultMap.put("searchedRepositories", result.getSearchedRepositories());
        
        // Add references
        List<Map<String, Object>> referencesList = new ArrayList<>();
        for (CodeReference ref : result.getReferences()) {
            Map<String, Object> refMap = new HashMap<>();
            refMap.put("repository", ref.getRepository());
            refMap.put("filePath", ref.getFilePath());
            refMap.put("lineNumber", ref.getLineNumber());
            refMap.put("matchedLine", ref.getMatchedLine());
            refMap.put("context", ref.getContext());
            referencesList.add(refMap);
        }
        resultMap.put("references", referencesList);
        
        return resultMap;
    }
    
    /**
     * Creates an error response.
     * 
     * @param message The error message
     * @return The error response
     */
    private ToolResponse createErrorResponse(String message) {
        ToolResponse response = new ToolResponse(false, message, null);
        response.addError(message);
        return response;
    }
    
    /**
     * Gets a string parameter from the parameters map.
     * 
     * @param parameters The parameters map
     * @param name The parameter name
     * @param defaultValue The default value if not found
     * @return The parameter value
     */
    private String getStringParameter(Map<String, Object> parameters, String name, String defaultValue) {
        Object value = parameters.get(name);
        if (value == null) {
            return defaultValue;
        }
        return value.toString();
    }
    
    /**
     * Gets a boolean parameter from the parameters map.
     * 
     * @param parameters The parameters map
     * @param name The parameter name
     * @param defaultValue The default value if not found
     * @return The parameter value
     */
    private boolean getBooleanParameter(Map<String, Object> parameters, String name, boolean defaultValue) {
        Object value = parameters.get(name);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String) {
            return Boolean.parseBoolean((String) value);
        }
        return defaultValue;
    }
    
    public JsonNode getCapabilities() {
        ObjectNode capabilities = objectMapper.createObjectNode();
        
        // Search capabilities
        capabilities.put("staticCodeSearch", true);
        capabilities.put("supportsRegex", true);
        capabilities.put("supportsCaseSensitivity", true);
        
        // Add default extensions
        ArrayNode extensions = capabilities.putArray("defaultExtensions");
        for (String ext : Arrays.asList(defaultExtensions.split(","))) {
            if (!ext.trim().isEmpty()) {
                extensions.add(ext.trim());
            }
        }
        
        // Repository management
        capabilities.put("scansLocalRepos", true);
        
        return capabilities;
    }
} 