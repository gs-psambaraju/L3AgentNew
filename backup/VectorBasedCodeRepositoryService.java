package com.l3agent.service.impl;

import com.l3agent.service.CodeChunkingService;
import com.l3agent.service.CodeRepositoryService;
import com.l3agent.service.VectorStoreService;
import com.l3agent.service.retrieval.ContentRetrievalService;
import com.l3agent.service.retrieval.RetrievalQuery;
import com.l3agent.util.LogExtractor;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Implementation of CodeRepositoryService using vector embeddings for semantic search.
 * Uses the VectorStoreService for storing and retrieving embeddings and
 * CodeChunkingService for breaking code into appropriate chunks.
 */
@Service
@Primary
public class VectorBasedCodeRepositoryService implements CodeRepositoryService {
    
    private static final Logger logger = LoggerFactory.getLogger(VectorBasedCodeRepositoryService.class);
    
    @Autowired
    private VectorStoreService vectorStoreService;
    
    @Autowired
    private CodeChunkingService codeChunkingService;
    
    @Autowired(required = false)
    private ContentRetrievalService contentRetrievalService;
    
    @Value("${l3agent.code-repository.base-path:./data/code}")
    private String baseCodePath;
    
    @Value("${l3agent.code-repository.min-similarity:0.7}")
    private float minSimilarity;
    
    @Value("${l3agent.code-repository.max-results:10}")
    private int maxResults;
    
    @Value("${l3agent.code-repository.index-on-startup:true}")
    private boolean indexOnStartup;
    
    @Value("${l3agent.code-repository.generate-descriptions:true}")
    private boolean generateDescriptions;
    
    @PostConstruct
    public void init() {
        // Create the base code directory if it doesn't exist
        File baseDir = new File(baseCodePath);
        if (!baseDir.exists()) {
            baseDir.mkdirs();
            logger.info("Created base code directory: {}", baseCodePath);
        }
        
        // Index all code files on startup if enabled
        if (indexOnStartup) {
            try {
                indexCodeRepository();
            } catch (Exception e) {
                logger.error("Error indexing code repository", e);
            }
        }
    }
    
    /**
     * Indexes all code files in the repository.
     */
    public void indexCodeRepository() {
        logger.info("Indexing code repository at {}", baseCodePath);
        
        try {
            // Get all code files
            List<Path> codeFiles = findAllCodeFiles();
            
            int fileCount = 0;
            int chunkCount = 0;
            
            // Index each file
            for (Path filePath : codeFiles) {
                try {
                    String relativePath = Paths.get(baseCodePath).relativize(filePath).toString();
                    
                    // Skip files that are likely binary
                    if (isBinaryFile(filePath)) {
                        logger.debug("Skipping binary file: {}", relativePath);
                        continue;
                    }
                    
                    String content;
                    try {
                        content = Files.readString(filePath);
                    } catch (IOException e) {
                        logger.warn("Could not read file as text (likely binary): {}", relativePath);
                        continue;
                    }
                    
                    // Skip empty files
                    if (content.trim().isEmpty()) {
                        continue;
                    }
                    
                    // Determine code type from file extension
                    String codeType = determineCodeType(relativePath);
                    
                    // Chunk the file
                    List<CodeChunkingService.CodeChunk> chunks = codeChunkingService.chunkCodeFile(relativePath, content);
                    
                    // Generate descriptions if enabled
                    Map<String, Object> fileDescription = generateFileDescription(relativePath, content, codeType);
                    
                    // Store embeddings for each chunk
                    for (CodeChunkingService.CodeChunk chunk : chunks) {
                        // Extract logs using our enhanced extractor
                        List<LogExtractor.LogMetadata> extractedLogs = LogExtractor.extractLogs(chunk.getContent());
                        
                        // Generate embedding for the chunk
                        float[] embedding = vectorStoreService.generateEmbedding(chunk.getContent());
                        
                        // Convert code chunk to embedding metadata
                        VectorStoreService.EmbeddingMetadata metadata = new VectorStoreService.EmbeddingMetadata();
                        metadata.setSource(chunk.getId());
                        metadata.setType(chunk.getType());
                        metadata.setFilePath(chunk.getFilePath());
                        metadata.setStartLine(chunk.getStartLine());
                        metadata.setEndLine(chunk.getEndLine());
                        metadata.setContent(chunk.getContent());
                        metadata.setLanguage(chunk.getLanguage());
                        
                        // Add description information if available
                        if (fileDescription != null) {
                            metadata.setDescription((String) fileDescription.get("description"));
                            metadata.setPurposeSummary((String) fileDescription.get("purposeSummary"));
                            
                            @SuppressWarnings("unchecked")
                            List<String> capabilities = (List<String>) fileDescription.get("capabilities");
                            metadata.setCapabilities(capabilities);
                            
                            @SuppressWarnings("unchecked")
                            List<String> usageExamples = (List<String>) fileDescription.get("usageExamples");
                            metadata.setUsageExamples(usageExamples);
                        }
                        
                        // If we have a description, create an enhanced embedding by combining code and description
                        if (fileDescription != null && fileDescription.get("description") != null) {
                            // Create combined text with description and code
                            StringBuilder combinedText = new StringBuilder();
                            combinedText.append("Description: ").append(fileDescription.get("description")).append("\n\n");
                            
                            if (fileDescription.get("purposeSummary") != null) {
                                combinedText.append("Purpose: ").append(fileDescription.get("purposeSummary")).append("\n\n");
                            }
                            
                            combinedText.append("Code:\n").append(chunk.getContent());
                            
                            // Generate enhanced embedding
                            float[] enhancedEmbedding = vectorStoreService.generateEmbedding(combinedText.toString());
                            
                            // Store with enhanced embedding that includes description context
                            vectorStoreService.storeEmbedding(chunk.getId(), enhancedEmbedding, metadata);
                        } else {
                            // Store with regular code embedding
                            vectorStoreService.storeEmbedding(chunk.getId(), embedding, metadata);
                        }
                        
                        chunkCount++;
                    }
                    
                    fileCount++;
                    
                    // Log progress periodically
                    if (fileCount % 100 == 0) {
                        logger.info("Indexed {} files, {} chunks so far", fileCount, chunkCount);
                    }
                } catch (Exception e) {
                    logger.error("Error indexing file: {}", filePath, e);
                }
            }
            
            logger.info("Finished indexing code repository. Indexed {} files, {} chunks total", fileCount, chunkCount);
        } catch (Exception e) {
            logger.error("Error indexing code repository", e);
        }
    }
    
    /**
     * Determines the code type based on file extension.
     */
    private String determineCodeType(String filePath) {
        if (filePath.endsWith(".java")) {
            if (filePath.contains("/interface/") || filePath.contains("Interface.java")) {
                return "interface";
            } else if (filePath.contains("/enum/") || filePath.contains("Enum.java")) {
                return "enum";
            } else if (filePath.contains("Exception.java")) {
                return "exception";
            } else {
                return "class";
            }
        } else if (filePath.endsWith(".xml")) {
            return "xml configuration";
        } else if (filePath.endsWith(".properties")) {
            return "properties configuration";
        } else if (filePath.endsWith(".yml") || filePath.endsWith(".yaml")) {
            return "yaml configuration";
        } else {
            return "code file";
        }
    }
    
    /**
     * Finds all code files in the repository.
     */
    private List<Path> findAllCodeFiles() throws IOException {
        Path basePath = Paths.get(baseCodePath);
        
        if (!Files.exists(basePath)) {
            return new ArrayList<>();
        }
        
        try (Stream<Path> walk = Files.walk(basePath)) {
            return walk
                    .filter(Files::isRegularFile)
                    .filter(this::isCodeFile)
                    .collect(Collectors.toList());
        }
    }
    
    /**
     * Determines if a file is a code file based on extension.
     */
    private boolean isCodeFile(Path filePath) {
        String fileName = filePath.getFileName().toString();
        
        // Skip hidden files and directories
        if (fileName.startsWith(".")) {
            return false;
        }
        
        // Check for code file extensions
        return fileName.endsWith(".java") ||
                fileName.endsWith(".py") ||
                fileName.endsWith(".js") ||
                fileName.endsWith(".ts") ||
                fileName.endsWith(".jsx") ||
                fileName.endsWith(".tsx") ||
                fileName.endsWith(".html") ||
                fileName.endsWith(".xml") ||
                fileName.endsWith(".properties") ||
                fileName.endsWith(".yml") ||
                fileName.endsWith(".yaml") ||
                fileName.endsWith(".json") ||
                fileName.endsWith(".sql") ||
                fileName.endsWith(".c") ||
                fileName.endsWith(".cpp") ||
                fileName.endsWith(".h") ||
                fileName.endsWith(".cs") ||
                fileName.endsWith(".go") ||
                fileName.endsWith(".rs") ||
                fileName.endsWith(".kt") ||
                fileName.endsWith(".kts") ||
                fileName.endsWith(".groovy") ||
                fileName.endsWith(".scala") ||
                fileName.endsWith(".rb") ||
                fileName.endsWith(".php");
    }
    
    /**
     * Determines if a file is a binary file.
     */
    private boolean isBinaryFile(Path filePath) {
        String fileName = filePath.getFileName().toString().toLowerCase();
        if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg") || 
            fileName.endsWith(".png") || fileName.endsWith(".gif") || 
            fileName.endsWith(".pdf") || fileName.endsWith(".zip") || 
            fileName.endsWith(".jar") || fileName.endsWith(".class") ||
            fileName.endsWith(".exe") || fileName.endsWith(".bin") ||
            fileName.endsWith(".so") || fileName.endsWith(".dll") ||
            fileName.endsWith(".jks") || fileName.endsWith(".war")) {
            return true;
        }
        
        // For files without clear binary extensions, check content
        try {
            byte[] bytes = Files.readAllBytes(filePath);
            // Small sample size for efficiency
            int sampleSize = Math.min(bytes.length, 1000);
            
            // Check for null bytes or high number of non-ASCII chars
            int nonTextChars = 0;
            for (int i = 0; i < sampleSize; i++) {
                // Null byte is a strong indicator of binary content
                if (bytes[i] == 0) {
                    return true;
                }
                
                // Count characters outside normal text range
                if (bytes[i] < 7 || bytes[i] > 127) {
                    nonTextChars++;
                }
            }
            
            // If more than 30% of the sample characters are non-text, consider it binary
            return (nonTextChars * 100 / sampleSize) > 30;
            
        } catch (IOException e) {
            logger.warn("Error checking if file is binary: {}", filePath, e);
            // Assume binary if we can't read it properly
            return true;
        }
    }
    
    /**
     * Searches for code snippets matching the query.
     * 
     * @param query The search query
     * @param maxResults Maximum number of results to return
     * @return List of matching code snippets
     */
    @Override
    public List<CodeSnippet> searchCode(String query, int maxResults) {
        if (contentRetrievalService != null) {
            logger.info("Using enhanced retrieval for code search: {}", query);
            return searchCodeWithEnhancedRetrieval(query, maxResults);
        } else {
            logger.info("Using default approach for code search: {}", query);
            return searchCodeWithDefaultApproach(query, maxResults);
        }
    }
    
    /**
     * Enhanced search approach using the ContentRetrievalService with path-level context.
     */
    private List<CodeSnippet> searchCodeWithEnhancedRetrieval(String query, int maxResults) {
        try {
            logger.info("Enhanced retrieval search for: {} with max results: {}", query, maxResults);
            
            // Preprocess query for better results
            String preprocessedQuery = preprocessQuery(query);
            
            // Create a proper retrieval query
            RetrievalQuery retrievalQuery = RetrievalQuery.textOnly(
                    preprocessedQuery, "code");
            
            // Execute the search using the content retrieval service
            List<String> resultIds = contentRetrievalService.retrieveContent(
                    retrievalQuery, 
                    vectorStoreService.getAllEmbeddings(),
                    vectorStoreService.getAllMetadata(),
                    maxResults, 
                    "hybrid");  // Use hybrid search by default
            
            List<CodeSnippet> results = new ArrayList<>();
            
            // Track any unresolved file paths that need full resolution
            Set<String> unresolvedPaths = new HashSet<>();
            
            // Convert the results to code snippets
            for (String id : resultIds) {
                VectorStoreService.EmbeddingMetadata metadata = vectorStoreService.getMetadata(id);
                if (metadata != null) {
                    CodeSnippet snippet = new CodeSnippet();
                    snippet.setFilePath(metadata.getFilePath());
                    
                    // Check if file path seems to be unknown or incomplete
                    if (metadata.getFilePath() == null || 
                        metadata.getFilePath().isEmpty() || 
                        metadata.getFilePath().contains("[unknown]")) {
                        unresolvedPaths.add(id);
                    }
                    
                    snippet.setSnippet(metadata.getContent());
                    snippet.setStartLine(metadata.getStartLine());
                    snippet.setEndLine(metadata.getEndLine());
                    
                    // Get similarity score
                    float similarity = vectorStoreService.getSimilarity(id, preprocessedQuery);
                    snippet.setRelevance(similarity);
                    
                    // Copy metadata to the snippet
                    Map<String, String> snippetMetadata = new HashMap<>();
                    
                    // Include description information if available
                    if (metadata.getDescription() != null) {
                        snippetMetadata.put("description", metadata.getDescription());
                    }
                    
                    if (metadata.getPurposeSummary() != null) {
                        snippetMetadata.put("purposeSummary", metadata.getPurposeSummary());
                    }
                    
                    if (metadata.getCapabilities() != null) {
                        snippetMetadata.put("capabilities", String.join(", ", metadata.getCapabilities()));
                    }
                    
                    if (metadata.getUsageExamples() != null && !metadata.getUsageExamples().isEmpty()) {
                        snippetMetadata.put("usageExamples", String.join("\n\n", metadata.getUsageExamples()));
                    }
                    
                    // Add path-level explanation and hierarchy information
                    if (metadata.getFilePath() != null && !metadata.getFilePath().isEmpty() && 
                            !metadata.getFilePath().contains("[unknown]")) {
                        try {
                            // Generate path-level explanation if not already available
                            if (metadata.getDescription() == null || metadata.getDescription().isEmpty()) {
                                Map<String, Object> pathExplanation = 
                                
                                // Add architectural explanation
                                if (pathExplanation.containsKey("architecturalExplanation")) {
                                    snippetMetadata.put("architecturalExplanation", 
                                        pathExplanation.get("architecturalExplanation").toString());
                                }
                                
                                // Add architectural role
                                if (pathExplanation.containsKey("architecturalRole")) {
                                    snippetMetadata.put("architecturalRole", 
                                        pathExplanation.get("architecturalRole").toString());
                                }
                                
                                // If we didn't have a description before, add it from the path explanation
                                if ((metadata.getDescription() == null || metadata.getDescription().isEmpty()) && 
                                        pathExplanation.containsKey("description")) {
                                    snippetMetadata.put("description", pathExplanation.get("description").toString());
                                }
                            }
                            
                            // Add path hierarchy information
                            Map<String, Object> pathHierarchy = 
                            
                            if (pathExplanation.containsKey("isParentComponent")) {
                                snippetMetadata.put("isParentComponent", 
                                    pathExplanation.get("isParentComponent").toString());
                            }
                            
                            if (pathExplanation.containsKey("pathComponents")) {
                                @SuppressWarnings("unchecked")
                                List<String> components = (List<String>)pathExplanation.get("pathComponents");
                                snippetMetadata.put("pathHierarchy", String.join(" > ", components));
                            }
                        } catch (Exception e) {
                            logger.warn("Error generating path-level explanation for {}: {}", 
                                metadata.getFilePath(), e.getMessage());
                        }
                    }
                    
                    // Add the full path context
                    if (metadata.getFilePath() != null && !metadata.getFilePath().isEmpty()) {
                        // Get the full file content to provide complete context
                        Optional<String> fullFileContent = getFileContent(metadata.getFilePath());
                        if (fullFileContent.isPresent()) {
                            snippetMetadata.put("fullPathContext", "Full path: " + metadata.getFilePath());
                            
                            // Don't add the entire file content as it could be too large,
                            // but store a reference that we have the full file available
                            snippetMetadata.put("hasFullFile", "true");
                        }
                    }
                    
                    // Extract and add log information
                    try {
                        List<LogExtractor.LogMetadata> extractedLogs = LogExtractor.extractLogs(metadata.getContent());
                        if (!extractedLogs.isEmpty()) {
                            StringBuilder logBuilder = new StringBuilder();
                            for (LogExtractor.LogMetadata log : extractedLogs) {
                                logBuilder.append("- ")
                                        .append("[").append(log.getType()).append("] ")
                                        .append("Line ").append(log.getLine()).append(": ")
                                        .append(log.getMessage()).append("\n");
                            }
                            snippetMetadata.put("logs", logBuilder.toString());
                        }
                    } catch (Exception e) {
                        logger.warn("Error extracting logs: {}", e.getMessage());
                    }
                    
                    snippet.setMetadata(snippetMetadata);
                    results.add(snippet);
                }
            }
            
            // Attempt to resolve any unknown file paths by checking for matching code
            if (!unresolvedPaths.isEmpty()) {
                resolveUnknownFilePaths(unresolvedPaths, results);
            }
            
            // Calculate confidence scores based on multiple factors
            recalculateConfidenceScores(results, query);
            
            // Sort by relevance
            results.sort((a, b) -> Float.compare(b.getRelevance(), a.getRelevance()));
            
            logger.info("Enhanced retrieval found {} relevant code snippets", results.size());
            return results;
        } catch (Exception e) {
            logger.error("Error in enhanced code search", e);
            // Fallback to the default approach
            return searchCodeWithDefaultApproach(query, maxResults);
        }
    }
    
    /**
     * Attempts to resolve unknown file paths by matching code content against the repository.
     */
    private void resolveUnknownFilePaths(Set<String> unresolvedPaths, List<CodeSnippet> results) {
        logger.info("Attempting to resolve {} unknown file paths", unresolvedPaths.size());
        
        for (String id : unresolvedPaths) {
            VectorStoreService.EmbeddingMetadata metadata = vectorStoreService.getMetadata(id);
            if (metadata != null && metadata.getContent() != null) {
                String codeToMatch = metadata.getContent().trim();
                
                // Skip if too small to reliably match
                if (codeToMatch.length() < 20) {
                    continue;
                }
                
                try {
                    // Search for matching code in the repository
                    Optional<String> resolvedPath = findFilePathByCodeContent(codeToMatch);
                    
                    if (resolvedPath.isPresent()) {
                        String path = resolvedPath.get();
                        logger.info("Resolved unknown path for ID {} to {}", id, path);
                        
                        // Update metadata in vector store
                        metadata.setFilePath(path);
                        vectorStoreService.updateMetadata(id, metadata);
                        
                        // Update results
                        for (CodeSnippet snippet : results) {
                            if (snippet.getSnippet().trim().equals(codeToMatch)) {
                                snippet.setFilePath(path);
                                
                                // Update the metadata as well
                                Map<String, String> snippetMetadata = snippet.getMetadata();
                                if (snippetMetadata == null) {
                                    snippetMetadata = new HashMap<>();
                                }
                                snippetMetadata.put("fullPathContext", "Full path: " + path);
                                snippetMetadata.put("pathResolutionMethod", "content_matching");
                                
                                // Get the full file content
                                Optional<String> fullFileContent = getFileContent(path);
                                if (fullFileContent.isPresent()) {
                                    snippetMetadata.put("hasFullFile", "true");
                                }
                                
                                snippet.setMetadata(snippetMetadata);
                            }
                        }
                    }
                } catch (Exception e) {
                    logger.warn("Error resolving file path for {}: {}", id, e.getMessage());
                }
            }
        }
    }
    
    /**
     * Searches the repository to find a file containing the given code content.
     */
    private Optional<String> findFilePathByCodeContent(String codeContent) throws IOException {
        String normalizedContent = codeContent.trim().replaceAll("\\s+", " ");
        
        List<Path> codeFiles = findAllCodeFiles();
        for (Path filePath : codeFiles) {
            try {
                String fileContent = Files.readString(filePath);
                String normalizedFileContent = fileContent.replaceAll("\\s+", " ");
                
                if (normalizedFileContent.contains(normalizedContent)) {
                    String relativePath = Paths.get(baseCodePath).relativize(filePath).toString();
                    return Optional.of(relativePath);
                }
            } catch (IOException e) {
                // Skip files that can't be read
                continue;
            }
        }
        
        return Optional.empty();
    }
    
    /**
     * Recalculates confidence scores for search results based on multiple factors.
     */
    private void recalculateConfidenceScores(List<CodeSnippet> results, String query) {
        // Extract key terms from the query for keyword matching
        Set<String> queryTerms = extractQueryTerms(query);
        
        for (CodeSnippet snippet : results) {
            float baseScore = snippet.getRelevance();
            
            // Start with the base similarity score
            float confidence = baseScore;
            
            // 1. Adjust based on description match quality
            Map<String, String> metadata = snippet.getMetadata();
            if (metadata != null) {
                if (metadata.containsKey("description") && metadata.get("description") != null) {
                    String description = metadata.get("description");
                    // Count matching terms in the description
                    int termMatches = countTermMatches(description, queryTerms);
                    confidence += (termMatches * 0.03f); // Small boost per matched term
                }
                
                // 2. Boost if we have logs that might be relevant
                if (metadata.containsKey("logs") && metadata.get("logs") != null && 
                    !metadata.get("logs").isEmpty()) {
                    confidence += 0.05f;
                }
                
                // 3. Boost for full path context availability
                if (metadata.containsKey("fullPathContext")) {
                    confidence += 0.1f;
                }
                
                // 4. Boost for resolved paths (previously unknown)
                if (metadata.containsKey("pathResolutionMethod")) {
                    confidence += 0.05f;
                }
            }
            
            // 5. Keyword match boost
            int codeTermMatches = countTermMatches(snippet.getSnippet(), queryTerms);
            confidence += (codeTermMatches * 0.02f);
            
            // Ensure confidence is between 0 and 1
            confidence = Math.max(0.0f, Math.min(1.0f, confidence));
            
            // Set the adjusted confidence score
            snippet.setRelevance(confidence);
        }
    }
    
    /**
     * Extracts significant terms from a query.
     */
    private Set<String> extractQueryTerms(String query) {
        // Split by non-word characters and filter out common words and short terms
        Set<String> stopWords = Set.of("a", "an", "the", "in", "on", "of", "for", "to", "is", "are", "how", "what");
        
        return Stream.of(query.toLowerCase().split("[\\W_]+"))
                .filter(term -> term.length() > 2)
                .filter(term -> !stopWords.contains(term))
                .collect(Collectors.toSet());
    }
    
    /**
     * Counts how many query terms appear in the text.
     */
    private int countTermMatches(String text, Set<String> terms) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        
        String lowerText = text.toLowerCase();
        int matches = 0;
        
        for (String term : terms) {
            if (lowerText.contains(term)) {
                matches++;
            }
        }
        
        return matches;
    }
    
    /**
     * The original search method renamed for fallback purposes.
     */
    private List<CodeSnippet> searchCodeWithDefaultApproach(String query, int maxResults) {
        logger.info("Using default retrieval approach for query: {}", query);
        
        // Log the configured minSimilarity for debugging
        logger.info("Using similarity threshold: {}", minSimilarity);
        
        // Perform query preprocessing to improve matching
        String enhancedQuery = preprocessQuery(query);
        if (!enhancedQuery.equals(query)) {
            logger.info("Enhanced query: {}", enhancedQuery);
        }
        
        // Determine if this is a conceptual/purpose query or an implementation query
        boolean isConceptualQuery = isConceptualQuery(query);
        logger.info("Query type: {}", isConceptualQuery ? "conceptual" : "implementation");
        
        // Generate embedding for the query
        float[] queryEmbedding = vectorStoreService.generateEmbedding(enhancedQuery);
        
        // Check if we got a valid embedding
        if (queryEmbedding == null || queryEmbedding.length == 0) {
            logger.warn("Failed to generate embedding for query: {}", query);
            return new ArrayList<>();
        }
        
        // For conceptual queries, use a lower similarity threshold to improve recall
        float effectiveThreshold = isConceptualQuery ? Math.max(0.5f, minSimilarity - 0.15f) : minSimilarity;
        logger.info("Using effective similarity threshold: {}", effectiveThreshold);
        
        // Find similar code snippets
        List<VectorStoreService.SimilarityResult> similarResults = 
                vectorStoreService.findSimilar(queryEmbedding, maxResults * 2, effectiveThreshold);
        
        // Convert similarity results to code snippets
        List<CodeSnippet> snippets = new ArrayList<>();
        
        for (VectorStoreService.SimilarityResult result : similarResults) {
            VectorStoreService.EmbeddingMetadata metadata = result.getMetadata();
            
            // Skip if metadata is missing
            if (metadata == null) {
                continue;
            }
            
            CodeSnippet snippet = new CodeSnippet();
            snippet.setFilePath(metadata.getFilePath());
            snippet.setStartLine(metadata.getStartLine());
            snippet.setEndLine(metadata.getEndLine());
            snippet.setSnippet(metadata.getContent());
            snippet.setRelevance(result.getSimilarityScore());
            
            // Add description if available
            if (metadata.getDescription() != null) {
                // Store description in custom fields to make it available to the caller
                snippet.setMetadata(new HashMap<>());
                snippet.getMetadata().put("description", metadata.getDescription());
                snippet.getMetadata().put("purposeSummary", metadata.getPurposeSummary());
                
                if (metadata.getCapabilities() != null) {
                    // Convert list to string for the metadata map
                    snippet.getMetadata().put("capabilities", 
                            String.join(", ", metadata.getCapabilities()));
                }
                
                if (metadata.getUsageExamples() != null) {
                    // Convert list to string for the metadata map
                    snippet.getMetadata().put("usageExamples", 
                            String.join("\n\n", metadata.getUsageExamples()));
                }
                
                // For conceptual queries, boost score of results with good descriptions
                if (isConceptualQuery && metadata.getDescription().length() > 100) {
                    // Boost by up to 15% for detailed descriptions
                    float boost = Math.min(0.15f, metadata.getDescription().length() / 10000f);
                    float boostedScore = Math.min(1.0f, result.getSimilarityScore() + boost);
                    snippet.setRelevance(boostedScore);
                    logger.debug("Boosted score for {} from {} to {} (description length: {})",
                            metadata.getFilePath(), result.getSimilarityScore(), boostedScore, 
                            metadata.getDescription().length());
                }
            }
            
            snippets.add(snippet);
        }
        
        // Sort by relevance (highest first)
        snippets.sort((a, b) -> Float.compare(b.getRelevance(), a.getRelevance()));
        
        // Limit to requested number of results
        if (snippets.size() > maxResults) {
            snippets = snippets.subList(0, maxResults);
        }
        
        logger.debug("Found {} code snippets for query", snippets.size());
        
        return snippets;
    }
    
    /**
     * Preprocesses the query to improve semantic matching.
     * Adds relevant technical terms and rephrases natural language questions.
     * 
     * @param query The original user query
     * @return Enhanced query for better semantic matching
     */
    private String preprocessQuery(String query) {
        if (query == null || query.trim().isEmpty()) {
            return query;
        }
        
        String enhancedQuery = query;
        
        // If query asks about purpose/functionality, enhance with technical terms
        if (enhancedQuery.toLowerCase().contains("what is") || 
            enhancedQuery.toLowerCase().contains("how does") ||
            enhancedQuery.toLowerCase().contains("purpose of") ||
            enhancedQuery.toLowerCase().contains("functionality of")) {
            
            // Extract the subject (likely a class or method name)
            String subject = extractSubjectFromQuery(enhancedQuery);
            
            if (subject != null && !subject.isEmpty()) {
                // Add variations with more technical terms that will match code better
                enhancedQuery = String.format("%s; %s class implementation; %s functionality; %s purpose; code for %s", 
                        query, subject, subject, subject, subject);
            }
        }
        
        return enhancedQuery;
    }
    
    /**
     * Extracts the subject (likely a class or method name) from a query.
     */
    private String extractSubjectFromQuery(String query) {
        // Simple regex to extract potential class or method names
        // Matches CamelCase words that might be class names
        Pattern classPattern = Pattern.compile("\\b([A-Z][a-z0-9]+)+\\b");
        Matcher matcher = classPattern.matcher(query);
        
        if (matcher.find()) {
            return matcher.group(0);
        }
        
        // Try to find anything that looks like it could be a code identifier
        Pattern identifierPattern = Pattern.compile("\\b([a-zA-Z][a-zA-Z0-9_]*)\\b");
        matcher = identifierPattern.matcher(query);
        
        // Skip common words
        Set<String> commonWords = Set.of("what", "is", "the", "purpose", "of", "how", "does", "work", "function", "code");
        
        while (matcher.find()) {
            String potential = matcher.group(0);
            if (!commonWords.contains(potential.toLowerCase())) {
                return potential;
            }
        }
        
        return null;
    }
    
    /**
     * Determines if a query is conceptual (about purpose, why, how) or implementation-focused.
     */
    private boolean isConceptualQuery(String query) {
        String lowerQuery = query.toLowerCase();
        
        // Check for keywords that suggest a conceptual query
        return lowerQuery.contains("what is") ||
               lowerQuery.contains("purpose") ||
               lowerQuery.contains("why") ||
               lowerQuery.contains("how does") ||
               lowerQuery.contains("when to use") ||
               lowerQuery.contains("functionality") ||
               lowerQuery.contains("explain") ||
               lowerQuery.contains("describe");
    }
    
    @Override
    public Optional<String> getFileContent(String filePath) {
        Path path = Paths.get(baseCodePath, filePath);
        
        try {
            String content = Files.readString(path);
            return Optional.of(content);
        } catch (IOException e) {
            logger.error("Error reading file: {}", path, e);
            return Optional.empty();
        }
    }
    
    /**
     * Gets the configured minimum similarity threshold.
     * 
     * @return The minimum similarity threshold (0.0-1.0)
     */
    public float getMinSimilarity() {
        return minSimilarity;
    }
    
    /**
     * Generate a basic file description
     * 
     * @param filePath the file path
     * @param content the file content
     * @param codeType the type of code
     * @return a map containing the description information
     */
    private Map<String, Object> generateFileDescription(String filePath, String content, String codeType) {
        // Create a basic file description map with empty values
        Map<String, Object> description = new HashMap<>();
        description.put("description", "");
        description.put("purposeSummary", "");
        description.put("capabilities", new ArrayList<String>());
        description.put("usageExamples", new ArrayList<String>());
        return description;
    }
    
    /**
     * Generate path level explanation for a file
     * 
     * @param filePath the file path to analyze
     * @return a map containing architectural explanation
     */
    private Map<String, Object> generatePathLevelExplanation(String filePath) {
        Map<String, Object> explanation = new HashMap<>();
        explanation.put("architecturalExplanation", "");
        explanation.put("architecturalRole", "");
        explanation.put("description", "");
        return explanation;
    }
    
    /**
     * Analyze path hierarchy for a file
     * 
     * @param filePath the file path to analyze
     * @return a map containing path hierarchy information
     */
    private Map<String, Object> analyzePathHierarchy(String filePath) {
        Map<String, Object> hierarchy = new HashMap<>();
        hierarchy.put("isParentComponent", "false");
        
        // Extract path components
        String[] components = filePath.split("/");
        List<String> pathComponents = new ArrayList<>();
        for (String component : components) {
            pathComponents.add(component);
        }
        
        hierarchy.put("pathComponents", pathComponents);
        return hierarchy;
    }
} 
