package com.l3agent.controller;

import com.l3agent.model.Ticket;
import com.l3agent.model.TicketMessage;
import com.l3agent.model.llm.LLMRequest;
import com.l3agent.model.llm.LLMResponse;
import com.l3agent.model.llm.ModelParameters;
import com.l3agent.service.CodeRepositoryService;
import com.l3agent.service.L3AgentService;
import com.l3agent.service.LLMService;
import com.l3agent.service.TicketService;
import com.l3agent.service.KnowledgeBaseService;
import com.l3agent.service.VectorStoreService;
import com.l3agent.service.KnowledgeGraphService;
import com.l3agent.service.CodeWorkflowService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * REST controller for L3Agent operations.
 * Provides endpoints for processing tickets and generating responses.
 */
@RestController
@RequestMapping("/api/l3agent")
public class L3AgentController {
    private static final Logger logger = LoggerFactory.getLogger(L3AgentController.class);
    
    @Autowired
    private L3AgentService l3AgentService;
    
    @Autowired
    private TicketService ticketService;
    
    @Autowired
    private LLMService llmService;
    
    @Autowired
    private VectorStoreService vectorStoreService;
    
    @Autowired
    private KnowledgeGraphService knowledgeGraphService;
    
    @Autowired
    private CodeRepositoryService codeRepositoryService;
    
    @Autowired
    private KnowledgeBaseService knowledgeBaseService;
    
    @Autowired
    private CodeWorkflowService codeWorkflowService;
    
    /**
     * Processes a ticket message and generates a response.
     * 
     * @param ticketId The ID of the ticket to process
     * @param message The message to process
     * @return The generated response
     */
    @PostMapping("/tickets/{ticketId}/process")
    public ResponseEntity<TicketMessage> processTicketMessage(
            @PathVariable String ticketId,
            @RequestBody TicketMessage message) {
        logger.info("Processing ticket message for ticket: {}", ticketId);
        
        return l3AgentService.processTicketMessage(ticketId, message)
                .map(response -> new ResponseEntity<>(response, HttpStatus.OK))
                .orElse(new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR));
    }
    
    /**
     * Analyzes a new ticket and generates initial insights.
     * 
     * @param ticket The ticket to analyze
     * @return The analysis results
     */
    @PostMapping("/tickets/analyze")
    public ResponseEntity<TicketMessage> analyzeTicket(@RequestBody Ticket ticket) {
        logger.info("Analyzing new ticket: {}", ticket.getSubject());
        
        // Create the ticket first
        Ticket createdTicket = ticketService.createTicket(ticket);
        
        // Then analyze it
        return l3AgentService.analyzeTicket(createdTicket)
                .map(analysis -> new ResponseEntity<>(analysis, HttpStatus.OK))
                .orElse(new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR));
    }
    
    /**
     * Gets information about the current L3Agent configuration including LLM provider.
     * 
     * @return Information about the agent configuration
     */
    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> getAgentInfo() {
        logger.info("Getting agent configuration information");
        
        Map<String, Object> info = new HashMap<>();
        info.put("llm_provider", llmService.getProviderName());
        info.put("llm_model", llmService.getDefaultModelName());
        info.put("llm_available", llmService.isAvailable());
        
        return new ResponseEntity<>(info, HttpStatus.OK);
    }
    
    /**
     * Gets information about embedding generation failures.
     * Useful for monitoring and debugging embedding issues.
     * 
     * @return Map of embedding failures
     */
    @GetMapping("/embeddings/failures")
    public ResponseEntity<Map<String, Object>> getEmbeddingFailures() {
        logger.info("Getting embedding failures information");
        
        Map<String, Object> response = new HashMap<>();
        response.put("failures", vectorStoreService.getEmbeddingFailures());
        response.put("count", vectorStoreService.getEmbeddingFailures().size());
        
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
    
    /**
     * Clears all embedding failure records.
     * 
     * @return Success confirmation
     */
    @DeleteMapping("/embeddings/failures")
    public ResponseEntity<Map<String, Object>> clearEmbeddingFailures() {
        logger.info("Clearing embedding failures");
        
        vectorStoreService.clearEmbeddingFailures();
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "All embedding failures cleared");
        
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
    
    /**
     * Generates embeddings on demand for specified code files or directories.
     * This allows explicit control over when and what code gets embedded for semantic search.
     * 
     * @param path Optional path to a directory or file (defaults to all code if not specified)
     * @param recursive Whether to recursively process subdirectories (defaults to false)
     * @return Status information about the embedding generation process
     */
    @PostMapping("/generate-embeddings")
    public ResponseEntity<Map<String, Object>> generateEmbeddings(
            @RequestParam(value = "path", required = false) String path,
            @RequestParam(value = "recursive", defaultValue = "false") boolean recursive) {
        
        logger.info("Received request to generate embeddings for path: {}, recursive: {}", path, recursive);
        
        Map<String, Object> result = l3AgentService.generateEmbeddingsOnDemand(path, recursive);
        
        return new ResponseEntity<>(result, HttpStatus.OK);
    }
    
    /**
     * Builds a knowledge graph for specified code files or directories.
     * This allows explicit control over when and what code gets included in the knowledge graph.
     * 
     * @param path Optional path to a directory or file (defaults to all code if not specified)
     * @param recursive Whether to recursively process subdirectories (defaults to false)
     * @return Status information about the knowledge graph building process
     */
    @PostMapping("/build-knowledge-graph")
    public ResponseEntity<Map<String, Object>> buildKnowledgeGraph(
            @RequestParam(value = "path", required = false) String path,
            @RequestParam(value = "recursive", defaultValue = "false") boolean recursive) {
        
        logger.info("Received request to build knowledge graph for path: {}, recursive: {}", path, recursive);
        
        Map<String, Object> result = l3AgentService.buildKnowledgeGraphOnDemand(path, recursive);
        
        return new ResponseEntity<>(result, HttpStatus.OK);
    }
    
    /**
     * Gets knowledge graph statistics.
     * 
     * @return Statistics about the knowledge graph
     */
    @GetMapping("/knowledge-graph/stats")
    public ResponseEntity<Map<String, Object>> getKnowledgeGraphStats() {
        logger.info("Getting knowledge graph statistics");
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("entity_count", knowledgeGraphService.getEntityCount());
        stats.put("relationship_count", knowledgeGraphService.getRelationshipCount());
        stats.put("available", knowledgeGraphService.isAvailable());
        
        return new ResponseEntity<>(stats, HttpStatus.OK);
    }
    
    /**
     * Searches for entities in the knowledge graph.
     * 
     * @param query The search query
     * @param maxResults Maximum number of results to return
     * @return List of matching entities
     */
    @GetMapping("/knowledge-graph/search")
    public ResponseEntity<Map<String, Object>> searchKnowledgeGraph(
            @RequestParam("query") String query,
            @RequestParam(value = "max_results", defaultValue = "10") int maxResults) {
        
        logger.info("Searching knowledge graph for: {}", query);
        
        List<KnowledgeGraphService.CodeEntity> entities = knowledgeGraphService.searchEntities(query, maxResults);
        
        Map<String, Object> response = new HashMap<>();
        response.put("query", query);
        response.put("results", entities);
        response.put("result_count", entities.size());
        
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
    
    /**
     * Finds related entities in the knowledge graph.
     * 
     * @param entityId The ID of the entity to find relationships for
     * @param depth The maximum traversal depth
     * @return List of related entities with relationship information
     */
    @GetMapping("/knowledge-graph/related")
    public ResponseEntity<Map<String, Object>> findRelatedEntities(
            @RequestParam("entity_id") String entityId,
            @RequestParam(value = "depth", defaultValue = "1") int depth) {
        
        logger.info("Finding entities related to: {}", entityId);
        
        List<KnowledgeGraphService.CodeRelationship> relationships = 
                knowledgeGraphService.findRelatedEntities(entityId, depth);
        
        Map<String, Object> response = new HashMap<>();
        response.put("entity_id", entityId);
        response.put("depth", depth);
        response.put("relationships", relationships);
        response.put("relationship_count", relationships.size());
        
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
    
    /**
     * Returns a visual representation of the knowledge graph for a specific entity and its relationships.
     * This is used for visualization and debugging purposes.
     * 
     * @param entityId The ID of the entity to visualize
     * @param depth The maximum traversal depth
     * @return A visualization-friendly structure of the graph
     */
    @GetMapping("/knowledge-graph/visualize")
    public ResponseEntity<Map<String, Object>> visualizeGraph(
            @RequestParam(value = "entity_id", required = false) String entityId,
            @RequestParam(value = "depth", defaultValue = "2") int depth) {
        
        logger.info("Generating knowledge graph visualization for entity: {}, depth: {}", 
                entityId, depth);
        
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> edges = new ArrayList<>();
        
        // If no specific entity is requested, return top-level entities
        if (entityId == null || entityId.isEmpty()) {
            // Get top-level entities (limit to 50 for visualization performance)
            List<KnowledgeGraphService.CodeEntity> entities = 
                    knowledgeGraphService.searchEntities("", 50);
            
            for (KnowledgeGraphService.CodeEntity entity : entities) {
                Map<String, Object> node = new HashMap<>();
                node.put("id", entity.getId());
                node.put("label", entity.getName());
                node.put("type", entity.getType());
                node.put("file", entity.getFilePath());
                nodes.add(node);
            }
        } else {
            // First, add the central node
            try {
                // Get relationships
                List<KnowledgeGraphService.CodeRelationship> relationships = 
                        knowledgeGraphService.findRelatedEntities(entityId, depth);
                
                // Add the central node
                Map<String, Object> centralNode = new HashMap<>();
                centralNode.put("id", entityId);
                centralNode.put("label", entityId.substring(entityId.lastIndexOf(":") + 1));
                centralNode.put("type", "central");
                nodes.add(centralNode);
                
                // Process relationships
                Set<String> processedNodeIds = new HashSet<>();
                processedNodeIds.add(entityId);
                
                for (KnowledgeGraphService.CodeRelationship relationship : relationships) {
                    // Add source node if not already added
                    if (!processedNodeIds.contains(relationship.getSourceId())) {
                        Map<String, Object> sourceNode = new HashMap<>();
                        sourceNode.put("id", relationship.getSourceId());
                        sourceNode.put("label", relationship.getSourceId().substring(
                                relationship.getSourceId().lastIndexOf(":") + 1));
                        sourceNode.put("type", "related");
                        nodes.add(sourceNode);
                        processedNodeIds.add(relationship.getSourceId());
                    }
                    
                    // Add target node if not already added
                    if (!processedNodeIds.contains(relationship.getTargetId())) {
                        Map<String, Object> targetNode = new HashMap<>();
                        targetNode.put("id", relationship.getTargetId());
                        targetNode.put("label", relationship.getTargetId().substring(
                                relationship.getTargetId().lastIndexOf(":") + 1));
                        targetNode.put("type", "related");
                        nodes.add(targetNode);
                        processedNodeIds.add(relationship.getTargetId());
                    }
                    
                    // Add edge
                    Map<String, Object> edge = new HashMap<>();
                    edge.put("source", relationship.getSourceId());
                    edge.put("target", relationship.getTargetId());
                    edge.put("label", relationship.getType());
                    edge.put("type", relationship.getType().toLowerCase());
                    edges.add(edge);
                }
            } catch (Exception e) {
                logger.error("Error generating graph visualization", e);
                result.put("error", "Error generating visualization: " + e.getMessage());
            }
        }
        
        result.put("nodes", nodes);
        result.put("edges", edges);
        result.put("total_nodes", nodes.size());
        result.put("total_edges", edges.size());
        
        return new ResponseEntity<>(result, HttpStatus.OK);
    }
    
    /**
     * Performs a hybrid search that combines vector-based semantic search with knowledge graph traversal.
     * This provides more comprehensive results by considering both semantic and structural relevance.
     * 
     * @param query The search query
     * @param maxResults Maximum number of code results to return
     * @param semanticWeight Weight for semantic search results (0.0-1.0)
     * @param structuralWeight Weight for structural search results (0.0-1.0)
     * @return Combined search results
     */
    @GetMapping("/hybrid-search")
    public ResponseEntity<Map<String, Object>> hybridSearch(
            @RequestParam("query") String query,
            @RequestParam(value = "max_results", defaultValue = "20") int maxResults,
            @RequestParam(value = "semantic_weight", defaultValue = "0.7") float semanticWeight,
            @RequestParam(value = "structural_weight", defaultValue = "0.3") float structuralWeight) {
        
        logger.info("Performing hybrid search: {} (semantic: {}, structural: {})", 
                query, semanticWeight, structuralWeight);
        
        Map<String, Object> response = new HashMap<>();
        long startTime = System.currentTimeMillis();
        
        try {
            // Step 1: Semantic search via vector embeddings
            List<CodeRepositoryService.CodeSnippet> semanticResults = 
                    codeRepositoryService.searchCode(query, maxResults);
            
            // Step 2: Structural search via knowledge graph
            List<KnowledgeGraphService.CodeEntity> structuralResults = 
                    knowledgeGraphService.searchEntities(query, maxResults);
            
            // Step 3: Combine results
            Map<String, Object> combinedResults = new HashMap<>();
            combinedResults.put("semantic_results", semanticResults);
            combinedResults.put("structural_results", structuralResults);
            combinedResults.put("semantic_count", semanticResults.size());
            combinedResults.put("structural_count", structuralResults.size());
            
            // Add metrics
            long duration = System.currentTimeMillis() - startTime;
            response.put("query", query);
            response.put("results", combinedResults);
            response.put("weights", Map.of(
                "semantic", semanticWeight,
                "structural", structuralWeight
            ));
            response.put("processing_time_ms", duration);
            
        } catch (Exception e) {
            logger.error("Error performing hybrid search", e);
            response.put("error", "Error performing search: " + e.getMessage());
        }
        
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
    
    /**
     * Returns metrics about the current system state.
     * Provides information about embeddings, knowledge graph, and processing capabilities.
     * 
     * @return System-wide metrics
     */
    @GetMapping("/metrics")
    public ResponseEntity<Map<String, Object>> getSystemMetrics() {
        logger.info("Getting system metrics");
        
        Map<String, Object> metrics = new HashMap<>();
        
        // Vector store metrics
        Map<String, Object> vectorMetrics = new HashMap<>();
        vectorMetrics.put("total_embeddings", vectorStoreService.getEmbeddingCount());
        vectorMetrics.put("failure_count", vectorStoreService.getEmbeddingFailures().size());
        vectorMetrics.put("available", vectorStoreService.isAvailable());
        metrics.put("vector_store", vectorMetrics);
        
        // Knowledge graph metrics
        Map<String, Object> graphMetrics = new HashMap<>();
        graphMetrics.put("entity_count", knowledgeGraphService.getEntityCount());
        graphMetrics.put("relationship_count", knowledgeGraphService.getRelationshipCount());
        graphMetrics.put("available", knowledgeGraphService.isAvailable());
        metrics.put("knowledge_graph", graphMetrics);
        
        // LLM metrics
        Map<String, Object> llmMetrics = new HashMap<>();
        llmMetrics.put("provider", llmService.getProviderName());
        llmMetrics.put("model", llmService.getDefaultModelName());
        llmMetrics.put("available", llmService.isAvailable());
        metrics.put("llm", llmMetrics);
        
        return new ResponseEntity<>(metrics, HttpStatus.OK);
    }
    
    /**
     * Responds to a direct chat message without requiring a ticket context.
     * This endpoint is more user-friendly for simple queries and doesn't require
     * creating a ticket first. The response is enriched with:
     * - Semantically relevant code snippets with their natural language descriptions
     * - Knowledge base articles
     * - Code component relationships from the knowledge graph
     * 
     * @param request A map containing the query and optional context parameters
     * @return The generated response from the L3Agent
     */
    @PostMapping("/chat")
    public ResponseEntity<Map<String, Object>> chat(@RequestBody Map<String, Object> request) {
        logger.info("Processing direct chat request");
        
        // Extract the query from the request
        String query = (String) request.getOrDefault("query", "");
        if (query.isEmpty()) {
            return new ResponseEntity<>(
                Map.of("error", "Missing required 'query' field"),
                HttpStatus.BAD_REQUEST
            );
        }
        
        // Extract optional context parameters
        String contextType = (String) request.getOrDefault("contextType", null);
        String contextId = (String) request.getOrDefault("contextId", null);
        
        // New: Check if full file context is explicitly requested
        boolean includeFullFiles = Boolean.parseBoolean((String) request.getOrDefault("includeFullFiles", "false"));
        boolean requestsFullContext = query.toLowerCase().contains("full file") || 
                                    query.toLowerCase().contains("entire file") ||
                                    query.toLowerCase().contains("complete file") ||
                                    query.toLowerCase().contains("full context") ||
                                    query.toLowerCase().contains("full path");
        
        if (requestsFullContext) {
            includeFullFiles = true;
            logger.info("Full file context explicitly requested in query");
        }
        
        // Create a response map
        Map<String, Object> response = new HashMap<>();
        long startTime = System.currentTimeMillis();
        
        try {
            logger.info("Search started for query: '{}' - Looking in knowledge base, code, and knowledge graph", query);
            
            // Step 1: Find relevant knowledge sources based on the query and optional context
            long knowledgeStartTime = System.currentTimeMillis();
            List<KnowledgeBaseService.RelevantArticle> relevantArticles = 
                    knowledgeBaseService.findRelevantArticles(query, 3);
            long knowledgeEndTime = System.currentTimeMillis();
            logger.info("Knowledge Base Search: found {} relevant articles in {}ms", 
                    relevantArticles.size(), knowledgeEndTime - knowledgeStartTime);
            
            // Step 2: Find relevant code snippets, respecting the optional context
            long codeStartTime = System.currentTimeMillis();
            List<CodeRepositoryService.CodeSnippet> codeSnippets;
            if (contextId != null && !contextId.isEmpty()) {
                // If a specific contextId is provided, we still need to search all code
                // but filter results that don't match the repository
                codeSnippets = codeRepositoryService.searchCode(query, 5).stream()
                    .filter(snippet -> snippet.getFilePath().contains("/" + contextId + "/"))
                    .collect(Collectors.toList());
                logger.info("Code Search: filtered to repository: {}, found {} code snippets in {}ms", 
                        contextId, codeSnippets.size(), System.currentTimeMillis() - codeStartTime);
            } else {
                // Otherwise, search across all repositories
                codeSnippets = codeRepositoryService.searchCode(query, 5);
                logger.info("Code Search: found {} code snippets in {}ms", 
                        codeSnippets.size(), System.currentTimeMillis() - codeStartTime);
            }
            
            // Enhance snippets with full file content if requested
            if (includeFullFiles) {
                enhanceSnippetsWithFullFileContent(codeSnippets);
            }
            
            // Log code snippet details
            if (!codeSnippets.isEmpty()) {
                Map<String, Integer> codeTypeCount = new HashMap<>();
                for (CodeRepositoryService.CodeSnippet snippet : codeSnippets) {
                    Map<String, String> metadata = snippet.getMetadata();
                    if (metadata != null && metadata.containsKey("purposeSummary")) {
                        codeTypeCount.put("with_explanation", codeTypeCount.getOrDefault("with_explanation", 0) + 1);
                    } else {
                        codeTypeCount.put("without_explanation", codeTypeCount.getOrDefault("without_explanation", 0) + 1);
                    }
                    
                    // Count full file context
                    if (metadata != null && metadata.containsKey("fullFileContent")) {
                        codeTypeCount.put("with_full_file", codeTypeCount.getOrDefault("with_full_file", 0) + 1);
                    }
                }
                logger.info("Code details: {} snippets with explanations, {} without, {} with full file content", 
                        codeTypeCount.getOrDefault("with_explanation", 0),
                        codeTypeCount.getOrDefault("without_explanation", 0),
                        codeTypeCount.getOrDefault("with_full_file", 0));
            }
            
            // Step 3: Enhance with knowledge graph if appropriate
            long graphStartTime = System.currentTimeMillis();
            List<KnowledgeGraphService.CodeRelationship> relatedEntities = new ArrayList<>();
            if ((contextType == null || contextType.equalsIgnoreCase("graph") || 
                 contextType.equalsIgnoreCase("both")) && knowledgeGraphService.isAvailable()) {
                
                // For each code snippet, find related entities
                for (CodeRepositoryService.CodeSnippet snippet : codeSnippets) {
                    String potentialEntityId = extractEntityIdFromSnippet(snippet);
                    if (potentialEntityId != null) {
                        List<KnowledgeGraphService.CodeRelationship> relationships = 
                                knowledgeGraphService.findRelatedEntities(potentialEntityId, 1);
                        relatedEntities.addAll(relationships);
                    }
                }
                logger.info("Knowledge Graph Search: found {} related entities in {}ms", 
                        relatedEntities.size(), System.currentTimeMillis() - graphStartTime);
            }
            
            // Step 3.5: Add workflow analysis data
            long workflowStartTime = System.currentTimeMillis();
            List<CodeWorkflowService.WorkflowStep> workflowSteps = new ArrayList<>();
            
            if (codeSnippets.size() > 0 && l3AgentService.findWorkflowsByFilePath("") != null) {
                // For each code snippet, find related workflow steps
                Set<String> processedFiles = new HashSet<>();
                for (CodeRepositoryService.CodeSnippet snippet : codeSnippets) {
                    String filePath = snippet.getFilePath();
                    if (!processedFiles.contains(filePath)) {
                        processedFiles.add(filePath);
                        List<CodeWorkflowService.WorkflowStep> steps = 
                                l3AgentService.findWorkflowsByFilePath(filePath);
                        if (steps != null && !steps.isEmpty()) {
                            workflowSteps.addAll(steps);
                        }
                    }
                }
                logger.info("Workflow Analysis: found {} workflow steps in {}ms", 
                        workflowSteps.size(), System.currentTimeMillis() - workflowStartTime);
            }
            
            // Step 4: Generate a prompt with the retrieved context
            String prompt = buildPromptWithContext(query, relevantArticles, codeSnippets, relatedEntities, workflowSteps);
            
            // Step 5: Send the prompt to the LLM and get the response
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("query_type", "direct_chat");
            metadata.put("context_type", contextType);
            metadata.put("context_id", contextId);
            
            ModelParameters parameters = ModelParameters.forAnalyticalTask(llmService.getDefaultModelName())
                    .withMaxTokens(2000);
            
            LLMRequest llmRequest = new LLMRequest(prompt, parameters)
                    .withMetadata(metadata)
                    .withKnowledgeSources(getKnowledgeSources(relevantArticles, codeSnippets));
            
            LLMResponse llmResponse = llmService.processRequest(llmRequest);
            
            // Step 6: Prepare the response
            response.put("answer", llmResponse.getContent());
            Map<String, Integer> sources = new HashMap<>();
            sources.put("articles", relevantArticles.size());
            sources.put("code_snippets", codeSnippets.size());
            sources.put("relationships", relatedEntities.size());
            sources.put("workflow_steps", workflowSteps.size());
            response.put("sources", sources);
            response.put("processing_time_ms", System.currentTimeMillis() - startTime);
            
            logger.info("Chat response generated in {}ms with {} knowledge sources", 
                    System.currentTimeMillis() - startTime,
                    relevantArticles.size() + codeSnippets.size() + relatedEntities.size() + workflowSteps.size());
            
            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (Exception e) {
            logger.error("Error processing chat request", e);
            response.put("error", "Error processing request: " + e.getMessage());
            return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    
    /**
     * Builds a prompt for the LLM with all the relevant context for a direct chat.
     * Enhanced to include comprehensive path-level descriptions, full file context, and log information.
     */
    private String buildPromptWithContext(
            String query,
            List<KnowledgeBaseService.RelevantArticle> relevantArticles,
            List<CodeRepositoryService.CodeSnippet> codeSnippets,
            List<KnowledgeGraphService.CodeRelationship> relatedEntities,
            List<CodeWorkflowService.WorkflowStep> workflowSteps) {
        
        StringBuilder promptBuilder = new StringBuilder();
        
        // Add system instructions with enhanced direction to use descriptions and logs
        promptBuilder.append("You are L3Agent, an AI assistant for developers and engineers. ")
                .append("You help resolve technical questions by providing clear, accurate information ")
                .append("based on the context provided. Answer the user's question using the ")
                .append("information in the provided context. If you cannot find the answer in the ")
                .append("context, admit that you don't know and suggest what additional information ")
                .append("might be needed.\n\n")
                .append("Pay special attention to the following elements:\n")
                .append("1. Path-level descriptions: These provide high-level understanding of what entire files/components do\n")
                .append("2. Code execution flows: These show how methods call each other and how data flows between components\n")
                .append("3. Log statements: These indicate important runtime behaviors and states\n")
                .append("4. Code snippets: These show the actual implementation with full path context\n\n");
        
        // Add knowledge articles with better formatting
        if (!relevantArticles.isEmpty()) {
            promptBuilder.append("KNOWLEDGE BASE ARTICLES:\n\n");
            
            for (KnowledgeBaseService.RelevantArticle article : relevantArticles) {
                promptBuilder.append("ARTICLE: ").append(article.getArticle().getTitle())
                        .append("\n----------------\n")
                        .append(article.getArticle().getContent()).append("\n\n");
            }
        }
        
        // Track files for which we've already included full content to avoid duplication
        Set<String> filesWithFullContent = new HashSet<>();
        
        // Add code snippets and their path-level explanations
        if (!codeSnippets.isEmpty()) {
            promptBuilder.append("CODE CONTEXT:\n\n");
            
            for (CodeRepositoryService.CodeSnippet snippet : codeSnippets) {
                promptBuilder.append("FILE: ").append(snippet.getFilePath())
                        .append(" (Lines ").append(snippet.getStartLine())
                        .append("-").append(snippet.getEndLine()).append(")\n");
                
                // Add metadata if available
                Map<String, String> metadata = snippet.getMetadata();
                if (metadata != null) {
                    // Add architectural explanation first (highest level context)
                    if (metadata.containsKey("architecturalExplanation") && 
                            metadata.get("architecturalExplanation") != null && 
                            !metadata.get("architecturalExplanation").isEmpty()) {
                        promptBuilder.append("ARCHITECTURAL ROLE: ").append(metadata.get("architecturalExplanation")).append("\n");
                    }
                    
                    // Add architectural role if available
                    if (metadata.containsKey("architecturalRole") && 
                            metadata.get("architecturalRole") != null && 
                            !metadata.get("architecturalRole").isEmpty()) {
                        promptBuilder.append("COMPONENT TYPE: ").append(metadata.get("architecturalRole")).append("\n");
                    }
                    
                    // Add path hierarchy if available
                    if (metadata.containsKey("pathHierarchy") && 
                            metadata.get("pathHierarchy") != null && 
                            !metadata.get("pathHierarchy").isEmpty()) {
                        promptBuilder.append("PATH HIERARCHY: ").append(metadata.get("pathHierarchy")).append("\n");
                    } else if (metadata.containsKey("pathComponents") && 
                            metadata.get("pathComponents") != null && 
                            !metadata.get("pathComponents").isEmpty()) {
                        promptBuilder.append("PATH HIERARCHY: ").append(metadata.get("pathComponents")).append("\n");
                    }
                    
                    // Add related files if available
                    if (metadata.containsKey("relatedFiles") && 
                            metadata.get("relatedFiles") != null && 
                            !metadata.get("relatedFiles").isEmpty()) {
                        promptBuilder.append("RELATED FILES: ").append(metadata.get("relatedFiles")).append("\n");
                    }
                    
                    // Add purpose summary
                    if (metadata.containsKey("purposeSummary") && 
                            metadata.get("purposeSummary") != null && 
                            !metadata.get("purposeSummary").isEmpty()) {
                        promptBuilder.append("PURPOSE: ").append(metadata.get("purposeSummary")).append("\n");
                    }
                    
                    // Add full description next
                    if (metadata.containsKey("description") && 
                            metadata.get("description") != null && 
                            !metadata.get("description").isEmpty()) {
                        promptBuilder.append("DESCRIPTION: ").append(metadata.get("description")).append("\n");
                    }
                    
                    // Add capabilities list
                    if (metadata.containsKey("capabilities") && 
                            metadata.get("capabilities") != null && 
                            !metadata.get("capabilities").isEmpty()) {
                        promptBuilder.append("CAPABILITIES: ").append(metadata.get("capabilities")).append("\n");
                    }
                    
                    // Add log statements for better understanding of runtime behavior
                    if (metadata.containsKey("logs") && 
                            metadata.get("logs") != null && 
                            !metadata.get("logs").isEmpty()) {
                        promptBuilder.append("LOG STATEMENTS:\n").append(metadata.get("logs")).append("\n");
                    }
                    
                    // Add path resolution method if applicable
                    if (metadata.containsKey("pathResolutionMethod")) {
                        promptBuilder.append("PATH RESOLUTION: ").append(metadata.get("pathResolutionMethod")).append("\n");
                    }
                }
                
                // Add the snippet content
                promptBuilder.append("CODE SNIPPET:\n```\n")
                        .append(snippet.getSnippet()).append("\n```\n\n");
                
                // Add full file content if available and not already included
                if (metadata != null && 
                    ((metadata.containsKey("fullFileContent") && metadata.get("fullFileContent") != null) ||
                     (metadata.containsKey("hasFullFile") && "true".equals(metadata.get("hasFullFile"))))) {
                    
                    // Only include full file content if we haven't already for this file
                    if (!filesWithFullContent.contains(snippet.getFilePath())) {
                        // Mark this file as having full content included
                        filesWithFullContent.add(snippet.getFilePath());
                        
                        // Add a clear separator for the full file
                        promptBuilder.append("FULL FILE CONTENT (").append(snippet.getFilePath()).append("):\n");
                        
                        // Note if file is truncated
                        if (metadata.containsKey("isTruncatedFile") && 
                            "true".equals(metadata.get("isTruncatedFile"))) {
                            promptBuilder.append("Note: This file is large, showing the most relevant section.\n");
                        }
                        
                        // Add the full file content if present in metadata
                        if (metadata.containsKey("fullFileContent")) {
                            promptBuilder.append("```\n")
                                    .append(metadata.get("fullFileContent"))
                                    .append("\n```\n\n");
                        } else {
                            // Otherwise, fetch it directly if we know it's available
                            try {
                                Optional<String> fullFileContent = codeRepositoryService.getFileContent(snippet.getFilePath());
                                if (fullFileContent.isPresent()) {
                                    String content = fullFileContent.get();
                                    // Check if file is too large, if so, truncate
                                    if (content.length() > 20000) {
                                        content = content.substring(0, 20000) + "\n... (file truncated due to size) ...";
                                        promptBuilder.append("Note: This file is large, showing only the first part.\n");
                                    }
                                    promptBuilder.append("```\n")
                                            .append(content)
                                            .append("\n```\n\n");
                                }
                            } catch (Exception e) {
                                logger.warn("Error retrieving full file content: {}", e.getMessage());
                            }
                        }
                    }
                }
            }
        }
        
        // Add workflow execution paths with more detail
        if (!workflowSteps.isEmpty()) {
            promptBuilder.append("CODE EXECUTION FLOWS:\n");
            
            // Group by source file for better organization
            Map<String, List<CodeWorkflowService.WorkflowStep>> stepsBySource = 
                    workflowSteps.stream()
                        .collect(Collectors.groupingBy(CodeWorkflowService.WorkflowStep::getSourceFile));
            
            for (String sourceFile : stepsBySource.keySet()) {
                promptBuilder.append("FROM FILE: ").append(sourceFile).append("\n");
                
                for (CodeWorkflowService.WorkflowStep step : stepsBySource.get(sourceFile)) {
                    // Add confidence score to help LLM understand reliability
                    double confidence = step.getConfidence();
                    String confidenceLevel = confidence > 0.8 ? "high" : confidence > 0.5 ? "medium" : "low";
                    
                    promptBuilder.append("  → ").append(step.getSourceMethod())
                            .append(" calls ").append(step.getTargetMethod())
                            .append(" in ").append(step.getTargetFile())
                            .append(" (confidence: ").append(confidenceLevel).append(")");
                    
                    if (step.isCrossRepository()) {
                        promptBuilder.append(" [cross-repository call]");
                    }
                    
                    // Add pattern type if available
                    if (step.getPatternType() != null && !step.getPatternType().isEmpty()) {
                        promptBuilder.append(" [").append(step.getPatternType()).append(" pattern]");
                    }
                    
                    promptBuilder.append("\n");
                    
                    if (step.getDataParameters() != null && !step.getDataParameters().isEmpty()) {
                        promptBuilder.append("    DATA PASSED: ").append(String.join(", ", step.getDataParameters())).append("\n");
                    }
                }
                promptBuilder.append("\n");
            }
        }
        
        // Add related entities with more context
        if (!relatedEntities.isEmpty()) {
            promptBuilder.append("CODE RELATIONSHIPS (from knowledge graph):\n");
            
            for (KnowledgeGraphService.CodeRelationship relationship : relatedEntities) {
                promptBuilder.append("SOURCE: ").append(relationship.getSourceId()).append("\n")
                        .append("TARGET: ").append(relationship.getTargetId()).append("\n")
                        .append("RELATIONSHIP TYPE: ").append(relationship.getType()).append("\n\n");
            }
        }
        
        // Add the user's query
        promptBuilder.append("USER QUERY: ").append(query).append("\n\n")
                .append("Please provide a detailed answer to the user's query based on the information above. ")
                .append("Include specific references to relevant code, knowledge articles, code flows, or relationships. ")
                .append("When referencing code, include file paths and line numbers. ")
                .append("Pay attention to the following:\n")
                .append("1. Use path-level explanations to understand the overall purpose of components\n")
                .append("2. Use code snippets to see implementation details\n")
                .append("3. Use log statements to understand runtime behavior\n")
                .append("4. Use workflow execution paths to understand how components interact\n")
                .append("If the information is insufficient, state what additional details are needed.");
        
        return promptBuilder.toString();
    }
    
    /**
     * Gets a list of knowledge sources for a chat request.
     */
    private List<String> getKnowledgeSources(
            List<KnowledgeBaseService.RelevantArticle> relevantArticles,
            List<CodeRepositoryService.CodeSnippet> codeSnippets) {
        
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
        
        return sources;
    }
    
    /**
     * Helper method to extract entity ID from a code snippet.
     */
    private String extractEntityIdFromSnippet(CodeRepositoryService.CodeSnippet snippet) {
        // Extract the file path and convert to a potential entity ID
        String filePath = snippet.getFilePath();
        if (filePath == null || filePath.isEmpty()) {
            return null;
        }
        
        // Handle Java files - extract class name and package
        if (filePath.endsWith(".java")) {
            String className = filePath.substring(filePath.lastIndexOf('/') + 1, filePath.length() - 5);
            // Try to extract package from the snippet content
            String packageName = extractPackageFromContent(snippet.getSnippet());
            if (packageName != null) {
                return "java:" + packageName + "." + className;
            } else {
                return "java:" + className;
            }
        }
        
        // For other file types, just use the file path
        return filePath.replace('/', '.');
    }
    
    /**
     * Helper method to extract package name from Java code content.
     */
    private String extractPackageFromContent(String content) {
        if (content == null || content.isEmpty()) {
            return null;
        }
        
        // Simple regex to extract package name
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("package\\s+([\\w.]+);");
        java.util.regex.Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(1);
        }
        
        return null;
    }
    
    /**
     * Diagnostic endpoint to debug vector search issues.
     * Allows testing the search with different similarity thresholds.
     * 
     * @param query The search query
     * @param threshold Optional custom similarity threshold (0.0-1.0)
     * @return Detailed diagnostic information about the search
     */
    @GetMapping("/diagnostics/search")
    public ResponseEntity<Map<String, Object>> debugSearch(
            @RequestParam("query") String query,
            @RequestParam(value = "threshold", required = false) Float threshold) {
        
        logger.info("Running diagnostic search for query: {} with threshold: {}", query, threshold);
        
        Map<String, Object> response = new HashMap<>();
        response.put("query", query);
        
        try {
            // Get the default threshold from the code repository service
            float defaultThreshold = 0.7f; // Common default value for similarity threshold
            response.put("default_threshold", defaultThreshold);
            
            // Use provided threshold or default
            float searchThreshold = threshold != null ? threshold : defaultThreshold;
            response.put("applied_threshold", searchThreshold);
            
            // Generate embedding for the query
            float[] queryEmbedding = vectorStoreService.generateEmbedding(query);
            
            if (queryEmbedding == null || queryEmbedding.length == 0) {
                response.put("error", "Failed to generate embedding for query");
                return new ResponseEntity<>(response, HttpStatus.OK);
            }
            
            response.put("embedding_dimensions", queryEmbedding.length);
            
            // Get namespaces
            List<String> namespaces = vectorStoreService.getRepositoryNamespaces();
            response.put("available_namespaces", namespaces);
            
            // Search with extremely low threshold to get raw results
            List<VectorStoreService.SimilarityResult> rawResults = 
                    vectorStoreService.findSimilar(queryEmbedding, 20, 0.1f);
            
            // Search with actual threshold
            List<VectorStoreService.SimilarityResult> filteredResults = 
                    vectorStoreService.findSimilar(queryEmbedding, 20, searchThreshold);
            
            List<Map<String, Object>> rawResultsList = new ArrayList<>();
            for (VectorStoreService.SimilarityResult result : rawResults) {
                Map<String, Object> resultMap = new HashMap<>();
                resultMap.put("id", result.getId());
                resultMap.put("score", result.getSimilarityScore());
                
                VectorStoreService.EmbeddingMetadata metadata = result.getMetadata();
                if (metadata != null) {
                    resultMap.put("file_path", metadata.getFilePath());
                    resultMap.put("start_line", metadata.getStartLine());
                    resultMap.put("end_line", metadata.getEndLine());
                    resultMap.put("namespace", metadata.getRepositoryNamespace());
                    // Truncate content for readability
                    if (metadata.getContent() != null) {
                        resultMap.put("snippet", metadata.getContent().length() > 100 
                                ? metadata.getContent().substring(0, 100) + "..." 
                                : metadata.getContent());
                    }
                }
                
                rawResultsList.add(resultMap);
            }
            
            response.put("raw_results_count", rawResults.size());
            response.put("raw_results", rawResultsList);
            response.put("filtered_results_count", filteredResults.size());
            
            // Convert to code snippets (what the application would actually return)
            List<CodeRepositoryService.CodeSnippet> snippets = new ArrayList<>();
            
            for (VectorStoreService.SimilarityResult result : filteredResults) {
                VectorStoreService.EmbeddingMetadata metadata = result.getMetadata();
                
                // Skip if metadata is missing
                if (metadata == null) {
                    continue;
                }
                
                CodeRepositoryService.CodeSnippet snippet = new CodeRepositoryService.CodeSnippet();
                snippet.setFilePath(metadata.getFilePath());
                snippet.setStartLine(metadata.getStartLine());
                snippet.setEndLine(metadata.getEndLine());
                snippet.setSnippet(metadata.getContent());
                snippet.setRelevance(result.getSimilarityScore());
                
                snippets.add(snippet);
            }
            
            response.put("snippets", snippets);
            
        } catch (Exception e) {
            logger.error("Error in diagnostic search", e);
            response.put("error", "Error: " + e.getMessage());
        }
        
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
    
    /**
     * Enhanced diagnostic endpoint for testing the description-aware search functionality.
     * This endpoint allows testing both regular searches and searches with different
     * description weightings and preprocessing options.
     * 
     * @param query The search query
     * @param useDescriptions Whether to include and use descriptions in search results
     * @return Detailed diagnostic information about the search with descriptions
     */
    @GetMapping("/diagnostics/enhanced-search")
    public ResponseEntity<Map<String, Object>> enhancedDebugSearch(
            @RequestParam("query") String query,
            @RequestParam(value = "use_descriptions", defaultValue = "true") boolean useDescriptions) {
        
        logger.info("Running enhanced diagnostic search for query: {} with descriptions: {}", 
                query, useDescriptions);
        
        Map<String, Object> response = new HashMap<>();
        response.put("query", query);
        response.put("use_descriptions", useDescriptions);
        
        try {
            // Search for code snippets (will use code descriptions per current implementation)
            int maxResults = 10;
            List<CodeRepositoryService.CodeSnippet> snippets = codeRepositoryService.searchCode(query, maxResults);
            
            // Extract detailed information from the results
            List<Map<String, Object>> detailedResults = new ArrayList<>();
            
            for (CodeRepositoryService.CodeSnippet snippet : snippets) {
                Map<String, Object> result = new HashMap<>();
                result.put("file_path", snippet.getFilePath());
                result.put("start_line", snippet.getStartLine());
                result.put("end_line", snippet.getEndLine());
                result.put("relevance", snippet.getRelevance());
                
                // Include descriptions if available and requested
                if (useDescriptions && snippet.getMetadata() != null) {
                    Map<String, Object> descriptionInfo = new HashMap<>();
                    
                    if (snippet.getMetadata().containsKey("description")) {
                        descriptionInfo.put("description", snippet.getMetadata().get("description"));
                    }
                    
                    if (snippet.getMetadata().containsKey("purposeSummary")) {
                        descriptionInfo.put("purpose_summary", snippet.getMetadata().get("purposeSummary"));
                    }
                    
                    if (snippet.getMetadata().containsKey("capabilities")) {
                        descriptionInfo.put("capabilities", snippet.getMetadata().get("capabilities"));
                    }
                    
                    result.put("description_info", descriptionInfo);
                }
                
                // Include snippet preview (first 200 characters)
                String snippetText = snippet.getSnippet();
                if (snippetText != null && !snippetText.isEmpty()) {
                    result.put("snippet_preview", snippetText.length() <= 200 ? 
                            snippetText : snippetText.substring(0, 200) + "...");
                }
                
                detailedResults.add(result);
            }
            
            response.put("result_count", snippets.size());
            response.put("results", detailedResults);
            
        } catch (Exception e) {
            logger.error("Error in enhanced diagnostic search", e);
            response.put("error", "Error: " + e.getMessage());
        }
        
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
    
    /**
     * Diagnostic endpoint to test prompt generation with code descriptions.
     * This allows verifying that descriptions are properly included in the LLM prompt.
     * 
     * @param query The test query
     * @return The generated prompt that would be sent to the LLM
     */
    @GetMapping("/diagnostics/test-prompt")
    public ResponseEntity<Map<String, Object>> testPromptGeneration(
            @RequestParam("query") String query) {
        
        logger.info("Testing prompt generation for query: {}", query);
        
        Map<String, Object> response = new HashMap<>();
        response.put("query", query);
        
        try {
            // Get sample data to build a prompt
            List<CodeRepositoryService.CodeSnippet> codeSnippets = 
                    codeRepositoryService.searchCode(query, 3);
            
            List<KnowledgeBaseService.RelevantArticle> relevantArticles = 
                    knowledgeBaseService.findRelevantArticles(query, 2);
            
            List<KnowledgeGraphService.CodeRelationship> relationships = new ArrayList<>();
            if (!codeSnippets.isEmpty() && codeSnippets.get(0).getFilePath() != null) {
                String entityId = extractEntityIdFromSnippet(codeSnippets.get(0));
                if (entityId != null) {
                    relationships = knowledgeGraphService.findRelatedEntities(entityId, 1);
                }
            }
            
            // Find workflow steps based on code snippets
            List<CodeWorkflowService.WorkflowStep> workflowSteps = new ArrayList<>();
            if (!codeSnippets.isEmpty()) {
                Set<String> processedFiles = new HashSet<>();
                for (CodeRepositoryService.CodeSnippet snippet : codeSnippets) {
                    String filePath = snippet.getFilePath();
                    if (!processedFiles.contains(filePath)) {
                        processedFiles.add(filePath);
                        List<CodeWorkflowService.WorkflowStep> steps = 
                                l3AgentService.findWorkflowsByFilePath(filePath);
                        if (steps != null && !steps.isEmpty()) {
                            workflowSteps.addAll(steps);
                        }
                    }
                }
            }
            
            // Generate the prompt
            String generatedPrompt = buildPromptWithContext(
                    query, relevantArticles, codeSnippets, relationships, workflowSteps);
            
            // Add details to the response
            response.put("generated_prompt", generatedPrompt);
            response.put("code_snippets_count", codeSnippets.size());
            response.put("articles_count", relevantArticles.size());
            response.put("relationships_count", relationships.size());
            response.put("workflow_steps_count", workflowSteps.size());
            
            // Check if descriptions are included
            boolean hasDescriptions = false;
            for (CodeRepositoryService.CodeSnippet snippet : codeSnippets) {
                if (snippet.getMetadata() != null && 
                    (snippet.getMetadata().containsKey("description") || 
                     snippet.getMetadata().containsKey("purposeSummary"))) {
                    hasDescriptions = true;
                    break;
                }
            }
            response.put("includes_descriptions", hasDescriptions);
            
        } catch (Exception e) {
            logger.error("Error testing prompt generation", e);
            response.put("error", "Error: " + e.getMessage());
        }
        
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
    
    /**
     * Enhances code snippets with full file content where appropriate.
     * 
     * @param snippets The code snippets to enhance
     */
    private void enhanceSnippetsWithFullFileContent(List<CodeRepositoryService.CodeSnippet> snippets) {
        logger.info("Enhancing snippets with full file content for {} snippets", snippets.size());
        
        // Track processed files to avoid duplicates
        Set<String> processedFiles = new HashSet<>();
        
        for (CodeRepositoryService.CodeSnippet snippet : snippets) {
            // Skip if no file path or already processed this file
            if (snippet.getFilePath() == null || processedFiles.contains(snippet.getFilePath())) {
                continue;
            }
            
            // Get metadata or create if doesn't exist
            Map<String, String> metadata = snippet.getMetadata();
            if (metadata == null) {
                metadata = new HashMap<>();
                snippet.setMetadata(metadata);
            }
            
            try {
                // Get full file content
                Optional<String> fullFileContent = codeRepositoryService.getFileContent(snippet.getFilePath());
                if (fullFileContent.isPresent()) {
                    String content = fullFileContent.get();
                    
                    // Check if file is too large - if so, just include a reasonable portion
                    if (content.length() > 20000) {
                        // Try to find the relevant section of the file
                        int startLinePos = findLinePosition(content, snippet.getStartLine());
                        int endLinePos = findLinePosition(content, snippet.getEndLine() + 10); // Include some context after
                        
                        if (startLinePos >= 0 && endLinePos > startLinePos) {
                            // Get section around the snippet with context
                            int contextStartPos = Math.max(0, startLinePos - 5000);
                            int contextEndPos = Math.min(content.length(), endLinePos + 5000);
                            
                            String trimmedContent = content.substring(contextStartPos, contextEndPos);
                            metadata.put("fullFileContent", trimmedContent);
                            metadata.put("isTruncatedFile", "true");
                        } else {
                            // If can't find position, include the first part of the file
                            metadata.put("fullFileContent", content.substring(0, 15000) + "\n... (file truncated due to size) ...");
                            metadata.put("isTruncatedFile", "true");
                        }
                    } else {
                        // Include the full file content
                        metadata.put("fullFileContent", content);
                    }
                    
                    metadata.put("fullPathContext", "Full path: " + snippet.getFilePath());
                    processedFiles.add(snippet.getFilePath());
                    logger.debug("Added full file content for {}", snippet.getFilePath());
                }
            } catch (Exception e) {
                logger.warn("Error enhancing snippet with full file: {}", e.getMessage());
            }
        }
    }
    
    /**
     * Finds the position of a specific line in a text string.
     * 
     * @param content The text content
     * @param line The line number to find (1-based)
     * @return The character position of the start of the line, or -1 if not found
     */
    private int findLinePosition(String content, int line) {
        if (line <= 0) {
            return 0;
        }
        
        int currentLine = 1;
        int pos = 0;
        
        while (currentLine < line && pos < content.length()) {
            if (content.charAt(pos) == '\n') {
                currentLine++;
            }
            pos++;
        }
        
        return currentLine == line ? pos : -1;
    }
} 