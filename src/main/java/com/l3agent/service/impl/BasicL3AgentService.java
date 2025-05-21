package com.l3agent.service.impl;

import com.l3agent.mcp.model.MCPRequest;
import com.l3agent.mcp.model.MCPResponse;
import com.l3agent.model.Ticket;
import com.l3agent.model.TicketMessage;
import com.l3agent.model.llm.LLMRequest;
import com.l3agent.model.llm.LLMResponse;
import com.l3agent.model.llm.ModelParameters;
import com.l3agent.service.*;
// import com.l3agent.util.BoilerplateFilter; // Commented out
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value; // Added for batchSize
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.io.File;
import java.nio.file.Files;
import java.util.concurrent.ConcurrentHashMap;
import java.io.IOException; // Added
import java.nio.file.Path; // Added
import java.nio.file.Paths; // Added
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A basic implementation of the L3AgentService interface.
 * Integrates with knowledge sources to process tickets and generate responses using LLM.
 */
@Service
public class BasicL3AgentService implements L3AgentService {
    private static final Logger logger = LoggerFactory.getLogger(BasicL3AgentService.class);
    private static final Map<String, Integer> FAILURE_CAUSES = new ConcurrentHashMap<>(); // Re-declared
    
    @Value("${l3agent.vector-store.batch-size:10}") // Added to get batch size
    private int embeddingBatchSize;
    
    @Autowired
    private TicketService ticketService;
    
    @Autowired
    private CodeRepositoryService codeRepositoryService;
    
    @Autowired
    private LogService logService;
    
    @Autowired
    private KnowledgeBaseService knowledgeBaseService;
    
    @Autowired
    private LLMService llmService;
    
    @Autowired
    private VectorStoreService vectorStoreService;
    
    @Autowired
    private CodeChunkingService codeChunkingService;
    
    @Autowired
    private KnowledgeGraphService knowledgeGraphService;
    
    @Autowired
    private CodeWorkflowService codeWorkflowService;
    
    @Autowired
    private MCPIntegrationService mcpIntegrationService;
    
    @Override
    public Optional<TicketMessage> processTicketMessage(String ticketId, TicketMessage message) {
        logger.info("Processing ticket message for ticket: {}", ticketId);
        
        // Get the ticket
        Optional<Ticket> optionalTicket = ticketService.getTicket(ticketId);
        if (optionalTicket.isEmpty()) {
            logger.error("Ticket not found: {}", ticketId);
            return Optional.empty();
        }
        
        Ticket ticket = optionalTicket.get();
        
        // Add the message to the ticket
        ticketService.addMessageToTicket(ticketId, message);
        
        // Process the message
        String query = message.getContent();
        
        try {
            // Step 1: Use semantic search to find relevant knowledge articles
            List<KnowledgeBaseService.RelevantArticle> relevantArticles = 
                    knowledgeBaseService.findRelevantArticles(query, 3);
            
            // Step 2: Use semantic search (vector embeddings) to find relevant code snippets
            List<CodeRepositoryService.CodeSnippet> codeSnippets = 
                    codeRepositoryService.searchCode(query, 5);

            // Step 2.5: Enhance with knowledge graph relationships for more context
            List<KnowledgeGraphService.CodeRelationship> relatedEntities = new ArrayList<>();
            if (!codeSnippets.isEmpty() && knowledgeGraphService.isAvailable()) {
                // For each code snippet, try to find related entities in the knowledge graph
                for (CodeRepositoryService.CodeSnippet snippet : codeSnippets) {
                    // Try to find an entity ID from the snippet's fully qualified name
                    String potentialEntityId = extractEntityIdFromSnippet(snippet);
                    if (potentialEntityId != null) {
                        // Find related entities with depth 1 (direct relationships only for now)
                        List<KnowledgeGraphService.CodeRelationship> relationships = 
                                knowledgeGraphService.findRelatedEntities(potentialEntityId, 1);
                        if (!relationships.isEmpty()) {
                            logger.debug("Found {} relationships for entity {}", 
                                    relationships.size(), potentialEntityId);
                            relatedEntities.addAll(relationships);
                        }
                    }
                }
                
                logger.debug("Enhanced code context with {} structural relationships from knowledge graph", 
                        relatedEntities.size());
            }
            
            // Step 3: Search for relevant logs (still using pattern matching for now)
            List<LogService.ErrorPattern> errorPatterns = new ArrayList<>();
            if (query.toLowerCase().contains("error") || query.toLowerCase().contains("exception")) {
                // Only search logs if the query is about errors
                Instant oneWeekAgo = Instant.now().minusSeconds(7 * 24 * 60 * 60);
                errorPatterns = logService.analyzeErrors(
                        logService.searchLogs(query, oneWeekAgo, Instant.now(), 100));
            }
            
            // Log the retrieved context for debugging
            logger.debug("Found {} relevant articles", relevantArticles.size());
            logger.debug("Found {} relevant code snippets", codeSnippets.size());
            logger.debug("Found {} error patterns", errorPatterns.size());
            
            // Include the semantic search relevance scores in the logs
            if (!codeSnippets.isEmpty()) {
                logger.debug("Top code snippet relevance: {} ({})", 
                        codeSnippets.get(0).getRelevance(),
                        codeSnippets.get(0).getFilePath());
            }
            
            // Step 4: Generate a prompt for the LLM with all the relevant context
            String prompt = buildPromptWithContext(query, ticket, relevantArticles, codeSnippets, 
                    errorPatterns, relatedEntities);
            
            // Step 5: Send the prompt to the LLM and get the response
            Map<String, Object> metadata = buildMetadata(ticket, message);
            ModelParameters parameters = ModelParameters.forAnalyticalTask(llmService.getDefaultModelName())
                    .withMaxTokens(2000);
            
            LLMRequest llmRequest = new LLMRequest(prompt, parameters)
                    .withTicketId(ticketId)
                    .withMessageId(message.getMessageId())
                    .withConversationId(ticketId)
                    .withMetadata(metadata)
                    .withKnowledgeSources(getKnowledgeSources(relevantArticles, codeSnippets, errorPatterns));
            
            LLMResponse llmResponse = llmService.processRequest(llmRequest);
            
            // Step 6: Create the response message
            TicketMessage responseMessage = new TicketMessage();
            responseMessage.setMessageId(UUID.randomUUID().toString());
            responseMessage.setContent(llmResponse.getContent());
            responseMessage.setSource(TicketMessage.MessageSource.L3AGENT);
            responseMessage.setTimestamp(Instant.now());
            responseMessage.setType(TicketMessage.MessageType.ANSWER);
            
            // Add the response to the ticket
            ticketService.addMessageToTicket(ticketId, responseMessage);
            
            return Optional.of(responseMessage);
        } catch (Exception e) {
            logger.error("Error processing ticket message", e);
            return Optional.empty();
        }
    }
    
    @Override
    public Optional<TicketMessage> analyzeTicket(Ticket ticket) {
        logger.info("Analyzing ticket: {}", ticket.getTicketId());
        
        try {
            // Combine the subject and description for analysis
            String query = ticket.getSubject() + " " + ticket.getDescription();
            
            // Step 1: Search for relevant knowledge articles
            List<KnowledgeBaseService.RelevantArticle> relevantArticles = 
                    knowledgeBaseService.findRelevantArticles(query, 3);
            
            // Step 2: Search for relevant code snippets
            List<CodeRepositoryService.CodeSnippet> codeSnippets = 
                    codeRepositoryService.searchCode(query, 3);
            
            // Step 3: Generate a prompt for the LLM with the ticket info and context
            String prompt = buildAnalysisPrompt(ticket, relevantArticles, codeSnippets);
            
            // Step 4: Send the prompt to the LLM and get the response
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("analysis_type", "initial_ticket_analysis");
            metadata.put("ticket_id", ticket.getTicketId());
            metadata.put("ticket_subject", ticket.getSubject());
            
            ModelParameters parameters = ModelParameters.forAnalyticalTask(llmService.getDefaultModelName())
                    .withMaxTokens(1500);
            
            LLMRequest llmRequest = new LLMRequest(prompt, parameters)
                    .withTicketId(ticket.getTicketId())
                    .withConversationId(ticket.getTicketId())
                    .withMetadata(metadata)
                    .withKnowledgeSources(getKnowledgeSources(relevantArticles, codeSnippets, null));
            
            LLMResponse llmResponse = llmService.processRequest(llmRequest);
            
            // Step 5: Create the response message
            TicketMessage responseMessage = new TicketMessage();
            responseMessage.setMessageId(UUID.randomUUID().toString());
            responseMessage.setContent(llmResponse.getContent());
            responseMessage.setSource(TicketMessage.MessageSource.L3AGENT);
            responseMessage.setTimestamp(Instant.now());
            responseMessage.setType(TicketMessage.MessageType.INFORMATION);
            
            // Add the response to the ticket
            ticketService.addMessageToTicket(ticket.getTicketId(), responseMessage);
            
            return Optional.of(responseMessage);
        } catch (Exception e) {
            logger.error("Error analyzing ticket", e);
            return Optional.empty();
        }
    }
    
    /**
     * Builds a prompt for the LLM with all the relevant context.
     * 
     * @param query The user query
     * @param ticket The ticket being processed
     * @param relevantArticles Relevant knowledge articles
     * @param codeSnippets Relevant code snippets
     * @param errorPatterns Relevant error patterns
     * @param relatedEntities Related entities from knowledge graph
     * @return A prompt string for the LLM
     */
    private String buildPromptWithContext(
            String query, 
            Ticket ticket, 
            List<KnowledgeBaseService.RelevantArticle> relevantArticles,
            List<CodeRepositoryService.CodeSnippet> codeSnippets,
            List<LogService.ErrorPattern> errorPatterns,
            List<KnowledgeGraphService.CodeRelationship> relatedEntities) {
        
        StringBuilder promptBuilder = new StringBuilder();
        
        // Add system instructions
        promptBuilder.append("You are L3Agent, an AI assistant for software support engineers. ")
                .append("You help resolve technical issues by providing clear, accurate information ")
                .append("based on the context provided. Answer the user's question using ONLY the ")
                .append("information in the provided context. If you cannot find the answer in the ")
                .append("context, admit that you don't know and suggest what additional information ")
                .append("might be needed.\n\n");
        
        // Add ticket info
        promptBuilder.append("TICKET INFORMATION:\n")
                .append("Ticket ID: ").append(ticket.getTicketId()).append("\n")
                .append("Subject: ").append(ticket.getSubject()).append("\n")
                .append("Description: ").append(ticket.getDescription()).append("\n")
                .append("Status: ").append(ticket.getStatus()).append("\n")
                .append("Priority: ").append(ticket.getPriority()).append("\n\n");
        
        // Add knowledge articles
        if (!relevantArticles.isEmpty()) {
            promptBuilder.append("KNOWLEDGE ARTICLES:\n");
            
            for (int i = 0; i < relevantArticles.size(); i++) {
                KnowledgeBaseService.RelevantArticle article = relevantArticles.get(i);
                promptBuilder.append("Article ").append(i + 1).append(": ").append(article.getArticle().getTitle()).append("\n")
                        .append("ID: ").append(article.getArticle().getArticleId()).append("\n")
                        .append("Relevance: ").append(String.format("%.2f", article.getRelevanceScore())).append("\n")
                        .append("Content: ").append(article.getArticle().getContent()).append("\n\n");
            }
        }
        
        // Add code snippets
        if (!codeSnippets.isEmpty()) {
            promptBuilder.append("CODE SNIPPETS (ordered by semantic relevance):\n");
            
            for (CodeRepositoryService.CodeSnippet snippet : codeSnippets) {
                promptBuilder.append("File: ").append(snippet.getFilePath()).append("\n")
                        .append("Lines ").append(snippet.getStartLine())
                        .append("-").append(snippet.getEndLine()).append("\n")
                        .append("Relevance: ").append(String.format("%.2f", snippet.getRelevance())).append("\n")
                        .append("```\n")
                        .append(snippet.getSnippet()).append("\n```\n\n");
            }
        }
        
        // Add related entities
        if (!relatedEntities.isEmpty()) {
            promptBuilder.append("CODE RELATIONSHIPS (from knowledge graph):\n");
            
            for (KnowledgeGraphService.CodeRelationship relationship : relatedEntities) {
                promptBuilder.append("Source: ").append(relationship.getSourceId()).append("\n")
                        .append("Target: ").append(relationship.getTargetId()).append("\n")
                        .append("Relationship Type: ").append(relationship.getType()).append("\n\n");
            }
        }
        
        // Add error patterns
        if (!errorPatterns.isEmpty()) {
            promptBuilder.append("ERROR PATTERNS:\n");
            
            for (LogService.ErrorPattern pattern : errorPatterns) {
                promptBuilder.append("Pattern: ").append(pattern.getPattern()).append("\n")
                        .append("Frequency: ").append(pattern.getFrequency()).append(" times\n")
                        .append("Sample: ").append(pattern.getPattern()).append("\n\n");
            }
        }
        
        // Add the user's query
        promptBuilder.append("USER QUERY: ").append(query).append("\n\n")
                .append("Please provide a detailed answer to the user's query based on the information above. ")
                .append("Include specific references to relevant code, knowledge articles, or error patterns. ")
                .append("When referencing code, include file paths and line numbers. ")
                .append("Pay attention to relevance scores to focus on the most semantically similar content. ")
                .append("If the information is insufficient, state what additional details are needed.");
        
        return promptBuilder.toString();
    }
    
    /**
     * Builds a prompt for analyzing a ticket.
     * 
     * @param ticket The ticket to analyze
     * @param relevantArticles Relevant knowledge articles
     * @param codeSnippets Relevant code snippets
     * @return A prompt string for the LLM
     */
    private String buildAnalysisPrompt(
            Ticket ticket, 
            List<KnowledgeBaseService.RelevantArticle> relevantArticles,
            List<CodeRepositoryService.CodeSnippet> codeSnippets) {
        
        StringBuilder promptBuilder = new StringBuilder();
        
        // Add system instructions
        promptBuilder.append("You are L3Agent, an AI assistant for software support engineers. ")
                .append("Analyze the ticket information below and provide a summary of the issue, ")
                .append("potential causes, and initial recommendations for investigation. Use the ")
                .append("provided knowledge articles and code snippets where relevant.\n\n");
        
        // Add ticket info
        promptBuilder.append("TICKET INFORMATION:\n")
                .append("Ticket ID: ").append(ticket.getTicketId()).append("\n")
                .append("Subject: ").append(ticket.getSubject()).append("\n")
                .append("Description: ").append(ticket.getDescription()).append("\n")
                .append("Status: ").append(ticket.getStatus()).append("\n")
                .append("Priority: ").append(ticket.getPriority()).append("\n\n");
        
        // Add knowledge articles
        if (!relevantArticles.isEmpty()) {
            promptBuilder.append("KNOWLEDGE ARTICLES:\n");
            
            for (KnowledgeBaseService.RelevantArticle article : relevantArticles) {
                promptBuilder.append("Article: ").append(article.getArticle().getTitle()).append("\n")
                        .append("ID: ").append(article.getArticle().getArticleId()).append("\n")
                        .append("Content: ").append(article.getArticle().getContent()).append("\n\n");
            }
        }
        
        // Add code snippets
        if (!codeSnippets.isEmpty()) {
            promptBuilder.append("CODE SNIPPETS:\n");
            
            for (CodeRepositoryService.CodeSnippet snippet : codeSnippets) {
                promptBuilder.append("File: ").append(snippet.getFilePath()).append("\n")
                        .append("Lines ").append(snippet.getStartLine())
                        .append("-").append(snippet.getEndLine()).append(":\n```\n")
                        .append(snippet.getSnippet()).append("\n```\n\n");
            }
        }
        
        // Add the analysis request
        promptBuilder.append("Please analyze this ticket and provide the following:\n")
                .append("1. A clear summary of the issue\n")
                .append("2. Potential causes based on the description and context\n")
                .append("3. Recommended next steps for investigation\n")
                .append("4. Any relevant knowledge articles or code snippets that may help\n")
                .append("5. Any additional information needed to resolve the issue\n\n")
                .append("Format your response in a structured manner with clear headings for each section.");
        
        return promptBuilder.toString();
    }
    
    /**
     * Builds metadata for the LLM request based on the ticket and message.
     * 
     * @param ticket The ticket being processed
     * @param message The message being processed
     * @return A metadata map
     */
    private Map<String, Object> buildMetadata(Ticket ticket, TicketMessage message) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("ticket_id", ticket.getTicketId());
        metadata.put("ticket_subject", ticket.getSubject());
        metadata.put("ticket_status", ticket.getStatus().toString());
        metadata.put("ticket_priority", ticket.getPriority().toString());
        metadata.put("message_id", message.getMessageId());
        metadata.put("message_type", message.getType().toString());
        metadata.put("message_source", message.getSource().toString());
        
        return metadata;
    }
    
    /**
     * Gets a list of knowledge sources used for the response.
     * 
     * @param relevantArticles Relevant knowledge articles
     * @param codeSnippets Relevant code snippets
     * @param errorPatterns Relevant error patterns
     * @return A list of knowledge sources
     */
    private List<String> getKnowledgeSources(
            List<KnowledgeBaseService.RelevantArticle> relevantArticles,
            List<CodeRepositoryService.CodeSnippet> codeSnippets,
            List<LogService.ErrorPattern> errorPatterns) {
        
        List<String> sources = new ArrayList<>();
        
        // Add knowledge articles
        if (relevantArticles != null) {
            sources.addAll(relevantArticles.stream()
                    .map(article -> "KnowledgeArticle:" + article.getArticle().getArticleId())
                    .collect(Collectors.toList()));
        }
        
        // Add code snippets
        if (codeSnippets != null) {
            sources.addAll(codeSnippets.stream()
                    .map(snippet -> "CodeSnippet:" + snippet.getFilePath())
                    .collect(Collectors.toList()));
        }
        
        // Add error patterns
        if (errorPatterns != null) {
            sources.addAll(errorPatterns.stream()
                    .map(pattern -> "ErrorPattern:" + pattern.getPattern().substring(0, Math.min(20, pattern.getPattern().length())))
                    .collect(Collectors.toList()));
        }
        
        return sources;
    }
    
    /**
     * Extracts a potential entity ID from a code snippet.
     * This attempts to convert a file path and code snippet into a knowledge graph entity ID.
     * 
     * @param snippet The code snippet to extract an entity ID from
     * @return A potential entity ID, or null if none can be determined
     */
    private String extractEntityIdFromSnippet(CodeRepositoryService.CodeSnippet snippet) {
        String filePath = snippet.getFilePath();
        // Only process Java files for now
        if (!filePath.endsWith(".java")) {
            return null;
        }
        
        try {
            // Extract potential class name from file path
            String fileName = new File(filePath).getName();
            String className = fileName.substring(0, fileName.lastIndexOf("."));
            
            // Look for package declaration in the snippet
            String packageName = null;
            String[] lines = snippet.getSnippet().split("\n");
            for (String line : lines) {
                line = line.trim();
                if (line.startsWith("package ") && line.endsWith(";")) {
                    packageName = line.substring(8, line.length() - 1).trim();
                    break;
                }
            }
            
            // Construct potential fully qualified name
            if (packageName != null) {
                return "java:" + packageName + "." + className;
            }
            
            return null;
        } catch (Exception e) {
            logger.debug("Error extracting entity ID from snippet: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public Map<String, Object> generateEmbeddingsOnDemand(String path, boolean recursive) {
        // Path validation and setup
        File basePath = new File(path);
        if (!basePath.exists()) {
            logger.error("Path does not exist: {}", path);
            return Map.of("status", "Error: Path does not exist", "processed_files", 0, "successful_embeddings", 0, "failed_embeddings", 0);
        }

        List<File> directoriesToProcess = new ArrayList<>();
        if (basePath.isDirectory()) {
            directoriesToProcess.add(basePath);
                                } else {
            // If a single file is given, process its parent directory (non-recursively for now)
            // This helps in determining a "namespace" if the file is part of a larger structure
            logger.warn("Processing parent directory for single file: {}. Non-recursive.", basePath.getParentFile().getAbsolutePath());
            directoriesToProcess.add(basePath.getParentFile());
            recursive = false; // Force non-recursive for single file's parent
        }

        logger.info("Starting on-demand embedding generation for path: {}, recursive: {}", path, recursive);

        // Configuration for batching
        int batchSize = this.embeddingBatchSize; // Use injected value

        // Initialize counters and results
        int totalFilesProcessed = 0;
        int totalSuccessfulEmbeddings = 0;
        int totalFailedEmbeddings = 0;
        int totalSkippedBoilerplate = 0;
        Map<String, Object> results = new HashMap<>();
        List<Map<String, Object>> repositoryResults = new ArrayList<>();
        clearFailureCauses(); // Clear previous failure statistics
        
        // Track total files for global progress
        AtomicInteger totalFilesFound = new AtomicInteger(0);
        Map<String, Integer> fileCountsByNamespace = new HashMap<>();

        // First pass to count files for accurate progress reporting
        for (File dir : directoriesToProcess) {
            String namespace = dir.getName(); // Use directory name as namespace
            List<File> files = new ArrayList<>();
            
            if (basePath.isDirectory()) {
                collectFiles(dir, files, recursive);
            } else { // Single file case
                files.add(basePath);
            }
            
            int filesInNamespace = files.size();
            totalFilesFound.addAndGet(filesInNamespace);
            fileCountsByNamespace.put(namespace, filesInNamespace);
        }
        
        logger.info("Found a total of {} files to process across all namespaces", totalFilesFound.get());

        // Process each top-level directory (or the single directory if path was a dir)
        for (File dir : directoriesToProcess) {
            String namespace = dir.getName(); // Use directory name as namespace
            logger.info("Processing repository/namespace: {}", namespace);

            List<File> files = new ArrayList<>();
            if (basePath.isDirectory()) {
                collectFiles(dir, files, recursive);
            } else { // Single file case
                files.add(basePath);
            }
            
            logger.info("Found {} files to process in namespace '{}'", files.size(), namespace);
            if (files.isEmpty()) {
                                continue;
                            }
                            
            // Estimate total chunks for progress logging
            long estimatedTotalChunks = 0;
            for (File file : files) {
                try {
                    String content = Files.readString(file.toPath());
                    // Rough estimate, actual chunking might differ
                    estimatedTotalChunks += Math.max(1, content.length() / 500); 
                } catch (IOException e) {
                    // ignore here, will be handled later
                }
            }
            logger.info("Estimated total chunks to process: {}", estimatedTotalChunks);


            int filesProcessedInNamespace = 0;
            int successfulEmbeddingsInNamespace = 0;
            int failedEmbeddingsInNamespace = 0;
            int skippedBoilerplateInNamespace = 0;
            long currentChunkOverall = 0;


            for (File file : files) {
                logger.info("Processing file for chunking and embedding: {}", file.getAbsolutePath()); // Added log
                if (!isCodeFile(file.getName())) {
                    logger.debug("Skipping non-code file: {}", file.getName());
                    continue;
                }

                try {
                    String content = Files.readString(file.toPath());
                    if (content.trim().isEmpty()) {
                        logger.debug("Skipping empty file: {}", file.getName());
                        continue;
                    }

                    // Boilerplate detection (example)
                    // if (BoilerplateFilter.isBoilerplate(file.getName(), content)) { // Commented out
                    //     logger.info("Skipping boilerplate file: {}\", file.getName());
                    //     skippedBoilerplateInNamespace++;
                    //     continue;
                    // }

                    String relativePath = new File(path).toURI().relativize(file.toURI()).getPath();
                    List<CodeChunkingService.CodeChunk> chunks = codeChunkingService.chunkCodeFile(relativePath, content);
                    
                    if (chunks.isEmpty()) {
                        logger.debug("No chunks generated for file: {}", relativePath);
                        continue;
                    }
                            
                    logger.info("Processing {} chunks for file: {}", chunks.size(), relativePath);
                    
                    Map<CodeChunkingService.CodeChunk, File> chunkToFileMap = new HashMap<>();
                    for(CodeChunkingService.CodeChunk chunk : chunks) {
                        chunkToFileMap.put(chunk, file);
                    }
                            
                    // Process chunks in batches
                    int[] batchResult = processFileChunksInBatches(chunks, namespace, batchSize, chunkToFileMap, relativePath);
                    successfulEmbeddingsInNamespace += batchResult[0];
                    failedEmbeddingsInNamespace += batchResult[1];
                    currentChunkOverall += chunks.size();

                    filesProcessedInNamespace++;
                    totalFilesProcessed++;
                    
                    // Calculate overall progress percentage
                    double percentComplete = (double)totalFilesProcessed / totalFilesFound.get() * 100;
                    
                    logger.info("Progress: processed {}/{} chunks from file {}, {} successful, {} failed in this batch, {} total failures, {} skipped",
                                chunks.size(), chunks.size(), file.getName(), batchResult[0], batchResult[1], totalFailedEmbeddings + failedEmbeddingsInNamespace, skippedBoilerplateInNamespace);
                    
                    // Log global progress every 10 files or 5% progress, whichever is more frequent
                    if (totalFilesProcessed % 10 == 0 || totalFilesProcessed % Math.max((int)(totalFilesFound.get() * 0.05), 1) == 0) {
                        logger.info("Global progress: {}/{} files processed ({}%)", 
                                   totalFilesProcessed, totalFilesFound.get(), String.format("%.1f", percentComplete));
                    }

                } catch (IOException e) {
                    logger.error("Error reading file {}: {}", file.getAbsolutePath(), e.getMessage());
                    failedEmbeddingsInNamespace++; 
            } catch (Exception e) {
                    logger.error("Unexpected error processing file {}: {}", file.getAbsolutePath(), e.getMessage(), e);
                    failedEmbeddingsInNamespace++;
                }
            }
            
            // Store results for this namespace
            Map<String, Object> nsResult = new HashMap<>();
            nsResult.put("namespace", namespace);
            nsResult.put("processed_files", filesProcessedInNamespace);
            nsResult.put("successful_embeddings", successfulEmbeddingsInNamespace);
            nsResult.put("failed_embeddings", failedEmbeddingsInNamespace);
            nsResult.put("skipped_boilerplate_files", skippedBoilerplateInNamespace);
            repositoryResults.add(nsResult);

            totalSuccessfulEmbeddings += successfulEmbeddingsInNamespace;
            totalFailedEmbeddings += failedEmbeddingsInNamespace;
            totalSkippedBoilerplate += skippedBoilerplateInNamespace;
            
            // Log final progress for this namespace
            logger.info("Completed namespace '{}': {}/{} files processed, {} embeddings successful, {} failed, {} skipped",
                       namespace, filesProcessedInNamespace, fileCountsByNamespace.get(namespace),
                       successfulEmbeddingsInNamespace, failedEmbeddingsInNamespace, skippedBoilerplateInNamespace);
        }
        
        // Final summary
        String overallStatus = determineOverallStatus(totalSuccessfulEmbeddings, totalFailedEmbeddings, totalSkippedBoilerplate);
        results.put("status", overallStatus);
        results.put("total_processed_files", totalFilesProcessed);
        results.put("total_successful_embeddings", totalSuccessfulEmbeddings);
        results.put("total_failed_embeddings", totalFailedEmbeddings);
        results.put("total_skipped_boilerplate_files", totalSkippedBoilerplate);
        results.put("repository_results", repositoryResults);
        results.put("failure_causes", getFailureCauses());
        results.put("total_files_found", totalFilesFound.get());

        logger.info("Embedding generation complete. Status: {}, Total files found: {}, Files processed: {}, Successful: {}, Failed: {}, Skipped: {}", 
                overallStatus, totalFilesFound.get(), totalFilesProcessed, totalSuccessfulEmbeddings, totalFailedEmbeddings, totalSkippedBoilerplate);
        if (totalFailedEmbeddings > 0) {
            logger.warn("Failure causes: {}", getFailureCauses());
        }
        
        // Persist any pending metadata or index changes
        if (vectorStoreService instanceof RobustVectorStoreService) {
            RobustVectorStoreService robustService = (RobustVectorStoreService) vectorStoreService;
            logger.info("Persisting changes to RobustVectorStoreService...");
            for (Map<String, Object> nsResult : repositoryResults) {
                String namespace = (String) nsResult.get("namespace");
                if (namespace != null) {
                    // robustService.saveMetadata(namespace); // This method is private
                }
            }
            // robustService.saveNamespaces(); // This method is private
            // robustService.saveEmbeddingFailures(); // This method is private
            // For now, let's rely on the @PreDestroy shutdown hook in RobustVectorStoreService
            // or if specific save methods for public use are added later.
             logger.warn("Relying on RobustVectorStoreService's shutdown hook to save data as specific save methods are private or not suitable here.");

            } else {
            logger.warn("VectorStoreService is not an instance of RobustVectorStoreService, manual save calls might be needed or data might not persist as expected outside shutdown.");
        }

        return results;
    }
    
    /**
     * Recursively collects files to process.
     * 
     * @param directory The directory to process
     * @param files List to collect files in
     * @param recursive Whether to process subdirectories
     */
    private void collectFiles(File directory, List<File> files, boolean recursive) {
        File[] fileList = directory.listFiles();
        if (fileList == null) {
            return;
        }
        
        for (File file : fileList) {
            if (file.isDirectory()) {
                if (recursive) {
                    collectFiles(file, files, true);
                }
            } else if (isCodeFile(file.getName())) {
                files.add(file);
            }
        }
    }
    
    private boolean isCodeFile(String fileName) {
        String lowerCaseName = fileName.toLowerCase();
        
        // Exclude known binary file types
        if (lowerCaseName.endsWith(".jks") || 
            lowerCaseName.endsWith(".bin") ||
            lowerCaseName.endsWith(".jar") ||
            lowerCaseName.endsWith(".zip") ||
            lowerCaseName.endsWith(".class") ||
            lowerCaseName.endsWith(".so") ||
            lowerCaseName.endsWith(".dll") ||
            lowerCaseName.endsWith(".exe") ||
            lowerCaseName.endsWith(".dat") ||
            lowerCaseName.endsWith(".png") ||
            lowerCaseName.endsWith(".jpg") ||
            lowerCaseName.endsWith(".gif")) {
            return false;
        }
        
        return lowerCaseName.endsWith(".java") || 
               lowerCaseName.endsWith(".js") || 
               lowerCaseName.endsWith(".py") || 
               lowerCaseName.endsWith(".ts") || 
               lowerCaseName.endsWith(".jsx") || 
               lowerCaseName.endsWith(".tsx") || 
               lowerCaseName.endsWith(".c") || 
               lowerCaseName.endsWith(".cpp") || 
               lowerCaseName.endsWith(".h") || 
               lowerCaseName.endsWith(".cs") || 
               lowerCaseName.endsWith(".go") || 
               lowerCaseName.endsWith(".rb") || 
               lowerCaseName.endsWith(".php");
    }
    
    @Override
    public Map<String, Object> generateEmbeddingsOnDemand(Map<String, Object> options) {
        String path = (String) options.getOrDefault("repository_path", null);
        boolean recursive = (boolean) options.getOrDefault("recursive", false);
        
        return generateEmbeddingsOnDemand(path, recursive);
    }
    
    @Override
    public Map<String, Object> retryFailedEmbeddings(Map<String, Object> options) {
        logger.info("Retrying failed embeddings with options: {}", options);
        
        // Get the namespace filter if specified
        String namespaceFilter = (String) options.get("repository_namespace");
        
        // Create result object
        Map<String, Object> result = new HashMap<>();
        long startTime = System.currentTimeMillis();
        
        // Get all embedding failures
        Map<String, ?> failures = vectorStoreService.getEmbeddingFailures();
        
        if (failures == null || failures.isEmpty()) {
            logger.info("No failed embeddings found to retry");
            result.put("status", "success");
            result.put("message", "No failed embeddings to retry");
            result.put("duration_ms", System.currentTimeMillis() - startTime);
            result.put("successful_embeddings", 0);
            result.put("failed_embeddings", 0);
        return result;
    }

        logger.info("Found {} failed embeddings to retry", failures.size());
        
        int totalFailures = failures.size();
        int successCount = 0;
        int failedCount = 0;
        
        // Process each failure
        for (Object failureObj : failures.values()) {
            try {
                // Need to unwrap the failure object properly
                if (!(failureObj instanceof Map)) {
                    logger.warn("Unexpected failure object type: {}", failureObj.getClass().getName());
                    failedCount++;
                    continue;
                }
                
                Map<String, Object> failure = (Map<String, Object>) failureObj;
                
                // Extract text hash and preview for logging
                String textHash = (String) failure.get("textHash");
                String textPreview = (String) failure.get("textPreview");
                
                logger.info("Retrying embedding for: {} ({})", textPreview, textHash);
                
                // Create metadata for this embedding
                VectorStoreService.EmbeddingMetadata metadata = new VectorStoreService.EmbeddingMetadata();
                metadata.setSource("retry:" + textHash);
                metadata.setType("code");
                
                // If namespace filter is specified, set it in the metadata
                if (namespaceFilter != null) {
                    metadata.setRepositoryNamespace(namespaceFilter);
                }
                
                // Generate the embedding
                float[] vector = vectorStoreService.generateEmbedding(textPreview);
                
                // Validate the generated embedding
                if (vector == null || vector.length == 0) {
                    logger.warn("Generated empty vector for: {}", textPreview);
                    failedCount++;
                    continue;
                }
                
                // Store the embedding in the appropriate namespace
                String namespace = namespaceFilter != null ? namespaceFilter : "default";
                boolean success = vectorStoreService.storeEmbedding(textHash, vector, metadata, namespace);
                
                if (success) {
                    successCount++;
                    logger.info("Successfully retried embedding for: {}", textPreview);
                } else {
                    failedCount++;
                    logger.warn("Failed to store retried embedding for: {}", textPreview);
                }
                
        } catch (Exception e) {
                failedCount++;
                logger.error("Error retrying embedding: {}", e.getMessage(), e);
            }
        }
        
        // Build the result
        long duration = System.currentTimeMillis() - startTime;
        
        result.put("status", failedCount == 0 ? "success" : (successCount > 0 ? "partial" : "error"));
        result.put("message", String.format("Processed %d failed embeddings: %d succeeded, %d failed", 
                totalFailures, successCount, failedCount));
        result.put("duration_ms", duration);
        result.put("total_failures", totalFailures);
        result.put("successful_embeddings", successCount);
        result.put("failed_embeddings", failedCount);
        
        return result;
    }
    
    /**
     * Prioritizes repositories for batch processing based on access patterns and characteristics.
     * Orders repositories by importance for processing, ensuring critical repositories are
     * processed first, while balancing efficiency concerns.
     * 
     * @param repositories List of repository namespaces to prioritize
     * @return Ordered list of repository namespaces by processing priority
     */
    private List<String> prioritizeRepositories(List<String> repositories) {
        // Skip if list is empty or has only one item
        if (repositories == null || repositories.size() <= 1) {
            return repositories;
        }
        
        // Create a map to track repository priorities
        Map<String, Integer> priorityMap = new HashMap<>();
        
        // Calculate priority score based on size, type, and importance
        for (String repo : repositories) {
            int priorityScore = 0;
            
            // Get the repository size from vector store service
            int repoSize = vectorStoreService.size(repo);
            
            // Smaller repos get higher priority (faster to process)
            if (repoSize < 1000) {
                priorityScore += 30;
            } else if (repoSize < 10000) {
                priorityScore += 20;
            } else if (repoSize < 50000) {
                priorityScore += 10;
            }
            
            // Custom priority rules for specific repository types
            if (repo.contains("api") || repo.contains("core")) {
                // Core and API repositories get higher priority
                priorityScore += 50;
            } else if (repo.contains("test") || repo.contains("mock")) {
                // Test/mock repositories get lower priority
                priorityScore -= 20;
            } else if (repo.contains("doc") || repo.contains("example")) {
                // Documentation/examples get medium priority
                priorityScore += 10;
            }
            
            priorityMap.put(repo, priorityScore);
        }
        
        // Sort repositories by priority score (descending)
        List<String> prioritizedList = new ArrayList<>(repositories);
        prioritizedList.sort((repo1, repo2) -> {
            int priority1 = priorityMap.getOrDefault(repo1, 0);
            int priority2 = priorityMap.getOrDefault(repo2, 0);
            return Integer.compare(priority2, priority1); // Descending order
        });
        
        logger.debug("Repository processing priority: {}", prioritizedList);
        return prioritizedList;
    }
    
    /**
     * Sorts files by modification time in descending order (newest first).
     * Used to prioritize recently modified files during batch processing.
     *
     * @param files List of files to sort
     * @return Sorted list with most recently modified files first
     */
    private List<File> sortFilesByModTime(List<File> files) {
        List<File> sortedFiles = new ArrayList<>(files);
        sortedFiles.sort((f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
        return sortedFiles;
    }
    
    /**
     * Finds files to process in the given path with their relative paths.
     * 
     * @param directoryPath Absolute path to the directory containing files
     * @param relativePathPrefix Prefix to prepend to all relative paths
     * @param recursive Whether to search recursively
     * @return Map of files to their relative paths
     */
    private Map<File, String> findFilesToProcess(String directoryPath, String relativePathPrefix, boolean recursive) {
        Map<File, String> filesToProcess = new HashMap<>();
        File directory = new File(directoryPath);
        
        if (!directory.exists() || !directory.isDirectory()) {
            logger.error("Directory does not exist or is not a directory: {}", directoryPath);
            return filesToProcess;
        }
        
        File[] files = directory.listFiles();
        if (files == null) {
            return filesToProcess;
        }
        
        for (File file : files) {
            if (file.isDirectory() && recursive) {
                // For directories, recursively add files
                String newPrefix = relativePathPrefix + "/" + file.getName();
                filesToProcess.putAll(findFilesToProcess(file.getAbsolutePath(), newPrefix, true));
            } else if (isCodeFile(file.getName())) {
                // For files, add with appropriate relative path
                String relativePath = relativePathPrefix + "/" + file.getName();
                // Remove any leading slashes for consistency
                while (relativePath.startsWith("/")) {
                    relativePath = relativePath.substring(1);
                }
                filesToProcess.put(file, relativePath);
            }
        }
        
        return filesToProcess;
    }
    
    /**
     * Chunks a file into processable segments using the code chunking service.
     * 
     * @param file The file to chunk
     * @param relativePath The relative path to use for the chunk IDs
     * @return List of code chunks
     */
    private List<CodeChunkingService.CodeChunk> chunkFile(File file, String relativePath) {
        try {
            if (!file.exists() || !file.isFile()) {
                logger.error("File does not exist or is not a regular file: {}", file.getAbsolutePath());
                return new ArrayList<>();
            }
            
            String content = Files.readString(file.toPath());
            if (content.trim().isEmpty()) {
                logger.debug("Empty file, skipping: {}", file.getName());
                return new ArrayList<>();
            }
            
            return codeChunkingService.chunkCodeFile(relativePath, content);
        } catch (IOException e) {
            logger.error("Error reading file {}: {}", file.getAbsolutePath(), e.getMessage());
            return new ArrayList<>();
        }
    }
    
    /**
     * Generates embeddings for all files in the specified repositories.
     * Prioritizes repositories and implements batch processing for efficiency.
     *
     * @param directories List of repository directories to process
     * @param batchSize Number of chunks to process in each batch
     * @param recursive Whether to process subdirectories
     * @return Result array [totalSuccessCount, totalFailedCount, totalSkippedCount]
     */
    public int[] generateEmbeddingsForRepositories(List<File> directories, int batchSize, boolean recursive) {
        // Initialize result counters
        int totalSuccessCount = 0;
        int totalFailedCount = 0;
        int totalSkippedCount = 0;
        
        // Get a list of all repositories to process
        List<String> repositoriesToProcess = new ArrayList<>();
        
        for (File dir : directories) {
            if (dir.exists() && dir.isDirectory()) {
                repositoriesToProcess.add(dir.getName());
            } else {
                logger.warn("Skipping non-existent or non-directory: {}", dir.getAbsolutePath());
            }
        }
        
        // Prioritize repositories for processing
        repositoriesToProcess = prioritizeRepositories(repositoriesToProcess);
        
        // Process each repository in priority order
        for (String repoName : repositoriesToProcess) {
            logger.info("Processing repository: {}", repoName);
            
            // Find the directory for this repository
            Optional<File> repoDir = directories.stream()
                .filter(dir -> dir.getName().equals(repoName))
                .findFirst();
            
            if (repoDir.isEmpty()) {
                logger.warn("Could not find directory for repository: {}", repoName);
                continue;
            }
            
            // Find all files to process in this repository
            Map<File, String> filesToProcess = findFilesToProcess(
                repoDir.get().getAbsolutePath(), 
                repoName, 
                recursive
            );
            
            // Sort files by modification time (newest first) for quicker feedback
            List<File> sortedFiles = sortFilesByModTime(new ArrayList<>(filesToProcess.keySet()));
            
            // Process files
            int filesInRepo = sortedFiles.size();
            int successCount = 0;
            int failedCount = 0;
            int skippedCount = 0;
            
            logger.info("Found {} files to process in repository {}", filesInRepo, repoName);
            
            for (int i = 0; i < sortedFiles.size(); i++) {
                File file = sortedFiles.get(i);
                String relativePath = filesToProcess.get(file);
                
                try {
                    logger.info("Processing file {}/{} ({}%): {}", 
                            i + 1, 
                            filesInRepo, 
                            String.format("%.1f", ((i + 1) / (double) filesInRepo) * 100),
                            relativePath);
                    
                    String content = Files.readString(file.toPath());
                    if (content.trim().isEmpty()) {
                        logger.debug("Skipping empty file: {}", relativePath);
                        skippedCount++;
                        continue;
                    }
                    
                    // Get chunks for this file
                    List<CodeChunkingService.CodeChunk> chunks = codeChunkingService.chunkCodeFile(relativePath, content);
                    
                    if (chunks.isEmpty()) {
                        logger.debug("No chunks generated for file: {}", relativePath);
                        skippedCount++;
                        continue;
                    }
                    
                    // Create chunk-to-file mapping
                    Map<CodeChunkingService.CodeChunk, File> chunkMap = new HashMap<>();
                    for (CodeChunkingService.CodeChunk chunk : chunks) {
                        chunkMap.put(chunk, file);
                    }
                    
                    // Process chunks in batches
                    int[] results = processFileChunksInBatches(chunks, repoName, batchSize, chunkMap, relativePath);
                    
                    successCount += results[0];
                    failedCount += results[1];
                    
                    // Log progress
                    logger.info("Repository {} progress: {}/{} files processed, {} successful, {} failed, {} skipped",
                            repoName,
                            i + 1,
                            filesInRepo,
                            successCount,
                            failedCount,
                            skippedCount);
                    
                } catch (Exception e) {
                    logger.error("Error processing file {}: {}", relativePath, e.getMessage(), e);
                    failedCount++;
                }
            }
            
            // Add repository results to totals
            totalSuccessCount += successCount;
            totalFailedCount += failedCount;
            totalSkippedCount += skippedCount;
            
            logger.info("Completed repository {}: {} successful, {} failed, {} skipped",
                    repoName,
                    successCount,
                    failedCount,
                    skippedCount);
        }
        
        // Return combined results
        logger.info("Completed all repositories: {} successful, {} failed, {} skipped",
                totalSuccessCount,
                totalFailedCount,
                totalSkippedCount);
                
        return new int[] { totalSuccessCount, totalFailedCount, totalSkippedCount };
    }

    /**
     * A convenience method for generating and storing a single embedding.
     * 
     * @param content The content to generate an embedding for
     * @param metadata The metadata to store with the embedding
     * @param namespace The namespace to store the embedding in
     * @return True if successful, false otherwise
     */
    private boolean generateAndStoreEmbedding(String content, VectorStoreService.EmbeddingMetadata metadata, String namespace) {
        try {
            float[] vector = vectorStoreService.generateEmbedding(content);
            String id = metadata.getSource(); // Use source as ID
            return vectorStoreService.storeEmbedding(id, vector, metadata, namespace);
        } catch (Exception e) {
            logger.error("Failed to generate or store embedding: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Determines the namespace to use for a given file path.
     * 
     * @param filePath The file path
     * @return The namespace to use
     */
    private String determineNamespace(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return "default";
        }
        
        // Try to extract a repository name from the path
        // This is a simplified approach - in a real implementation, this would be more sophisticated
        String[] pathParts = filePath.split("/");
        if (pathParts.length > 0) {
            return pathParts[0];
        }
        
        return "default";
    }

    private Map<String, Integer> generateEmbeddingsForRepositories(String pathArg, boolean recursive, String baseCodePath) {
        Map<String, Object> result = new HashMap<>();
        int totalFiles = 0;
        int filesProcessed = 0;
        int successfulEmbeddingsCount = 0;
        int failedEmbeddingsCount = 0;
        int skippedFileCount = 0;
        long startTime = System.currentTimeMillis();

        // New summary map
        Map<String, Integer> summary = new HashMap<>();
        int totalFilesProcessed = 0;
        AtomicInteger totalSuccessfulEmbeddings = new AtomicInteger(0);
        AtomicInteger totalFailedEmbeddings = new AtomicInteger(0);
        AtomicInteger totalSkippedFiles = new AtomicInteger(0);

        try {
            File directory = new File(baseCodePath, pathArg); // pathArg is like "gs-integrations" or "l3agent"
            if (!directory.exists() || !directory.isDirectory()) {
                logger.error("Directory does not exist or is not a directory: {}", directory.getAbsolutePath());
                summary.put("status", 0); // Error status
                return summary;
            }
            String namespace = directory.getName(); // This will be "gs-integrations" or "l3agent"

            Map<File, String> filesToProcessWithRelativePath = findFilesToProcess(directory.getAbsolutePath(), pathArg, recursive);
            totalFiles = filesToProcessWithRelativePath.size();
            logger.info("Found {} files to process in namespace '{}' (path: {})", totalFiles, namespace, pathArg);

            for (Map.Entry<File, String> entry : filesToProcessWithRelativePath.entrySet()) {
                File file = entry.getKey();
                String fullRelativePath = entry.getValue(); // e.g., "gs-integrations/core/src/main/java/com/gainsight/integration/bean/GainsightExternalRequest.java"
                String pathInNamespace = Paths.get(directory.getPath()).relativize(file.toPath()).toString(); // e.g., "core/src/main/java/com/gainsight/integration/bean/GainsightExternalRequest.java"

                if (Thread.currentThread().isInterrupted()) {
                    logger.warn("Embedding generation was interrupted. Stopping further processing.");
                    break;
                }

                totalFilesProcessed++;
                final int currentFileNum = totalFilesProcessed;
                logger.info("[{}/{}] Processing file: {}", currentFileNum, totalFiles, fullRelativePath);

                try {
                    String content = Files.readString(file.toPath());
                    if (content.trim().isEmpty()) {
                        logger.info("Skipping empty file: {}", fullRelativePath);
                        totalSkippedFiles.incrementAndGet();
                        continue;
                    }
                    
                    List<CodeChunkingService.CodeChunk> chunks = chunkFile(file, pathInNamespace);
                    if (chunks.isEmpty()) {
                        logger.info("No chunks generated for file: {}. Skipping.", fullRelativePath);
                        totalSkippedFiles.incrementAndGet();
                        continue;
                    }
                    logger.info("Processing {} chunks for file: {}", chunks.size(), pathInNamespace);

                    Map<CodeChunkingService.CodeChunk, File> chunkToFileMap = new HashMap<>();
                    for (CodeChunkingService.CodeChunk chunk : chunks) {
                        chunkToFileMap.put(chunk, file);
                    }
                    
                    int[] batchResult = processFileChunksInBatches(chunks, namespace, embeddingBatchSize, chunkToFileMap, fullRelativePath);
                    totalSuccessfulEmbeddings.addAndGet(batchResult[0]);
                    totalFailedEmbeddings.addAndGet(batchResult[1]);

                    // Log overall progress periodically (every 10 files or 5%)
                    if (currentFileNum % 10 == 0 || currentFileNum % Math.max((int)(totalFiles * 0.05), 1) == 0) {
                        double percentComplete = ((double)currentFileNum / totalFiles) * 100;
                        logger.info("Overall Progress: {}/{} files processed ({}%), {} chunks successfully embedded, {} chunks failed, {} files skipped", 
                                   currentFileNum, totalFiles, String.format("%.1f", percentComplete), 
                                   totalSuccessfulEmbeddings.get(), totalFailedEmbeddings.get(), totalSkippedFiles.get());
                    }

                } catch (IOException e) {
                    logger.error("Error processing file {}: {}", fullRelativePath, e.getMessage(), e);
                    totalFailedEmbeddings.incrementAndGet();
                }
            }

            long endTime = System.currentTimeMillis();
            double totalTimeInMinutes = (endTime - startTime) / 60000.0;
            logger.info("Embedding generation for namespace '{}' completed in {} minutes.", namespace, String.format("%.2f", totalTimeInMinutes));
            
            // Final progress summary
            logger.info("FINAL SUMMARY: {}/{} files processed (100%), {} chunks successfully embedded, {} chunks failed, {} files skipped", 
                      totalFilesProcessed, totalFiles, 
                      totalSuccessfulEmbeddings.get(), totalFailedEmbeddings.get(), totalSkippedFiles.get());

            // Update summary
            summary.put("status", 1); // Success status
            summary.put("totalFiles", totalFiles);
            summary.put("totalFilesProcessed", totalFilesProcessed);
            summary.put("successfulEmbeddings", totalSuccessfulEmbeddings.get());
            summary.put("failedEmbeddings", totalFailedEmbeddings.get());
            summary.put("skippedFiles", totalSkippedFiles.get());
            
        } catch (Exception e) {
            logger.error("Error generating embeddings: {}", e.getMessage(), e);
            summary.put("status", 0); // Error status
        }
        
        return summary;
    }

    private int[] processFileChunksInBatches(List<CodeChunkingService.CodeChunk> chunks, 
                                      String namespace,
                                            int batchSize, 
                                            Map<CodeChunkingService.CodeChunk, File> chunkToFileMap,
                                            String actualRelativePath) {
        int successfulEmbeddings = 0;
        int failedEmbeddings = 0;
        int totalChunks = chunks.size();
        int processedChunks = 0;

        // If we have a file reference, use its name in logs for better visibility
        String fileDisplayName = null;
        if (!chunkToFileMap.isEmpty()) {
            File fileRef = chunkToFileMap.values().iterator().next();
            if (fileRef != null) {
                fileDisplayName = fileRef.getName();
            }
        }
        if (fileDisplayName == null) {
            fileDisplayName = actualRelativePath.substring(actualRelativePath.lastIndexOf('/') + 1);
        }

        for (int i = 0; i < chunks.size(); i += batchSize) {
            List<CodeChunkingService.CodeChunk> batch = chunks.subList(i, Math.min(i + batchSize, chunks.size()));
            logger.debug("Processing batch of {} chunks for namespace {}", batch.size(), namespace);

            // Prepare metadata and content for batch
            List<VectorStoreService.EmbeddingMetadata> metadataList = new ArrayList<>();
            List<String> contentList = new ArrayList<>();
            
            for (CodeChunkingService.CodeChunk chunk : batch) {
                File originalFile = chunkToFileMap.get(chunk);
                
                VectorStoreService.EmbeddingMetadata metadata = new VectorStoreService.EmbeddingMetadata();
                metadata.setSource(chunk.getId()); // Use chunk ID as source ID
                metadata.setType("code_chunk");
                metadata.setFilePath(actualRelativePath); // Use passed actualRelativePath
                metadata.setStartLine(chunk.getStartLine());
                metadata.setEndLine(chunk.getEndLine());
                metadata.setLanguage(determineLanguage(originalFile != null ? originalFile.getName() : "unknown"));
                metadata.setContent(chunk.getContent()); // Keep content in metadata for now
                metadata.setRepositoryNamespace(namespace); // Set the namespace
                
                // Generate file-level description (if enabled and not already done)
                // This is a simplified placeholder; in a real system, this would be more sophisticated
                // and likely cached per file.
                Map<String, Object> fileDesc = generateFileDescription(
                    actualRelativePath, 
                    chunk.getContent(), // Ideally, this would be the full file content
                    determineCodeType(actualRelativePath)
                );
                metadata.setDescription((String) fileDesc.get("description"));
                metadata.setPurposeSummary((String) fileDesc.get("purposeSummary"));
                metadata.setCapabilities((List<String>) fileDesc.get("capabilities"));
                metadata.setUsageExamples((List<String>) fileDesc.get("usageExamples"));

                metadataList.add(metadata);
                contentList.add(chunk.getContent());
            }

            try {
                // Generate embeddings in batch
                float[][] batchEmbeddings = vectorStoreService.generateEmbeddingsBatch(contentList);

                if (batchEmbeddings != null && batchEmbeddings.length == metadataList.size()) {
                    for (int j = 0; j < batchEmbeddings.length; j++) {
                        processedChunks++;
                        // Log batch progress
                        if (processedChunks % 10 == 0 || processedChunks == totalChunks) {
                            double batchPercentComplete = ((double)processedChunks / totalChunks) * 100;
                            logger.info("Batch progress for file {}: {}/{} chunks processed ({}%)", 
                                      fileDisplayName, processedChunks, totalChunks, String.format("%.1f", batchPercentComplete));
                        }
                        
                        if (batchEmbeddings[j] != null && batchEmbeddings[j].length > 0) {
                            boolean stored = vectorStoreService.storeEmbedding(
                                   metadataList.get(j).getSource(), // Use chunk ID (source)
                                   batchEmbeddings[j], 
                                   metadataList.get(j),
                                   namespace);
                            if (stored) {
                                successfulEmbeddings++;
                                logger.info("Successfully stored embedding for chunk {} of file: {}", metadataList.get(j).getSource(), metadataList.get(j).getFilePath());
                            } else {
                                failedEmbeddings++;
                                String cause = "Failed to store embedding in VectorStoreService";
                                FAILURE_CAUSES.merge(cause, 1, Integer::sum);
                                logger.warn("Failed to store embedding for chunk {} from file: {}. Will retry later.", metadataList.get(j).getSource(), metadataList.get(j).getFilePath());
                            }
                        } else {
                            failedEmbeddings++;
                            String cause = "Null or empty embedding vector received from API";
                            FAILURE_CAUSES.merge(cause, 1, Integer::sum);
                            logger.warn("Null or empty embedding vector for chunk: {} from file: {}", metadataList.get(j).getSource(), metadataList.get(j).getFilePath());
                        }
                    }
                } else {
                    // Mismatch in expected embeddings vs. received
                    logger.error("Batch embedding generation returned unexpected results. Expected: {}, Got: {}", 
                                 metadataList.size(), (batchEmbeddings != null ? batchEmbeddings.length : "null"));
                    failedEmbeddings += batch.size(); // Count all in batch as failed
                    String cause = "Batch embedding result mismatch";
                    FAILURE_CAUSES.merge(cause, 1, Integer::sum);
                }
            } catch (Exception e) {
                logger.error("Error generating or storing batch embeddings: {}", e.getMessage(), e);
                failedEmbeddings += batch.size(); // Count all in batch as failed
                String cause = e.getClass().getSimpleName() + ": " + (e.getMessage() != null ? e.getMessage().substring(0, Math.min(e.getMessage().length(), 50))+"..." : "Unknown error");
                FAILURE_CAUSES.merge(cause, 1, Integer::sum);
            }
        }
        return new int[]{successfulEmbeddings, failedEmbeddings};
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
     * Determines the programming language based on file extension.
     * 
     * @param filePath The file path
     * @return The detected language
     */
    private String determineLanguage(String filePath) {
        String lowerCasePath = filePath.toLowerCase();
        if (lowerCasePath.endsWith(".java")) return "java";
        if (lowerCasePath.endsWith(".js")) return "javascript";
        if (lowerCasePath.endsWith(".py")) return "python";
        if (lowerCasePath.endsWith(".ts")) return "typescript";
        if (lowerCasePath.endsWith(".jsx")) return "javascript";
        if (lowerCasePath.endsWith(".tsx")) return "typescript";
        if (lowerCasePath.endsWith(".c") || lowerCasePath.endsWith(".h")) return "c";
        if (lowerCasePath.endsWith(".cpp")) return "cpp";
        if (lowerCasePath.endsWith(".cs")) return "csharp";
        if (lowerCasePath.endsWith(".go")) return "go";
        if (lowerCasePath.endsWith(".rb")) return "ruby";
        if (lowerCasePath.endsWith(".php")) return "php";
        return "unknown";
    }

    private Map<String, Object> generateFileDescription(String filePath, String content, String codeType) {
        // Create a basic file description map with empty values
        Map<String, Object> description = new HashMap<>();
        description.put("description", "");
        description.put("purposeSummary", "");
        description.put("capabilities", new ArrayList<String>());
        description.put("usageExamples", new ArrayList<String>());
        return description;
    }
    
    // Add this new overload for the determineOverallStatus method
    private String determineOverallStatus(int successfulEmbeddings, int failedEmbeddings, int skippedBoilerplate) {
        if (failedEmbeddings > 0 && successfulEmbeddings == 0) {
            return "error";
        } else if (failedEmbeddings > 0) {
            return "partial_success";
        } else {
            return "success";
        }
    }

    /**
     * Gets a map of failure causes and their counts from the most recent processing run.
     * 
     * @return Map of failure causes and counts
     */
    public Map<String, Integer> getFailureCauses() {
        return new HashMap<>(FAILURE_CAUSES);
    }
    
    /**
     * Clears the failure cause tracking for a new processing run.
     */
    private void clearFailureCauses() {
        FAILURE_CAUSES.clear();
    }

    @Override
    public Map<String, Object> buildKnowledgeGraphOnDemand(String path, boolean recursive) {
        logger.info("Building knowledge graph for path: {}, recursive: {}", path, recursive);
        return knowledgeGraphService.buildKnowledgeGraph(path, recursive);
    }
    
    @Override
    public Map<String, Object> analyzeCodeWorkflowOnDemand(String path, boolean recursive) {
        return analyzeCodeWorkflowOnDemand(path, recursive, false);
    }

    @Override
    public Map<String, Object> analyzeCodeWorkflowOnDemand(String path, boolean recursive, boolean enableCrossRepoAnalysis) {
        logger.info("Analyzing code workflow for path: {}, recursive: {}, cross-repo: {}", path, recursive, enableCrossRepoAnalysis);
        return codeWorkflowService.analyzeCodeWorkflow(path, recursive, enableCrossRepoAnalysis);
    }

    @Override
    public Map<String, Object> generateAllOnDemand(String path, boolean recursive) {
        logger.info("Generating all assets for path: {}, recursive: {}", path, recursive);
        
        // Generate embeddings
        Map<String, Object> embeddingsResult = generateEmbeddingsOnDemand(path, recursive);
        
        // Build knowledge graph
        Map<String, Object> knowledgeGraphResult = buildKnowledgeGraphOnDemand(path, recursive);
        
        // Analyze code workflow
        Map<String, Object> workflowResult = analyzeCodeWorkflowOnDemand(path, recursive);
        
        // Combine results
        Map<String, Object> combinedResults = new HashMap<>();
        combinedResults.put("embeddings_result", embeddingsResult);
        combinedResults.put("knowledge_graph_result", knowledgeGraphResult);
        combinedResults.put("workflow_result", workflowResult);
        
        return combinedResults;
    }
    
    @Override
    public void handleTicket(String ticketId) {
        logger.info("Handling ticket: {}", ticketId);
        // Not implemented in this simplified version
    }
    
    @Override
    public List<VectorStoreService.SimilarityResult> findEmbeddingsByFilePath(String filePath, String repositoryNamespace) {
        return vectorStoreService.findEmbeddingsByFilePath(filePath, repositoryNamespace);
    }
    
    @Override
    public List<KnowledgeGraphService.CodeEntity> findEntitiesByFilePath(String filePath) {
        return knowledgeGraphService.findEntitiesByFilePath(filePath);
    }
    
    @Override
    public List<CodeWorkflowService.WorkflowStep> findWorkflowsByFilePath(String filePath) {
        // Simplified implementation
        return new ArrayList<>();
    }
    
    @Override
    public MCPResponse processMCPQuery(String query) {
        logger.info("Processing MCP query: {}", query);
        return mcpIntegrationService.processQuery(query);
    }
    
    @Override
    public MCPResponse processMCPRequest(MCPRequest request) {
        logger.info("Processing MCP request: {}", request);
        // Create a new enhanced request using the MCPIntegrationService
        MCPRequest enhancedRequest = mcpIntegrationService.enhanceRequest(request);
        // Process the query using the enhanced request
        return mcpIntegrationService.processQuery(enhancedRequest.getQuery());
    }
    
    @Override
    public TicketMessage enhanceTicketWithMCP(String ticketId, TicketMessage message) {
        logger.info("Enhancing ticket message with MCP: {}", ticketId);
        // Simplified implementation
        return message;
    }
} 