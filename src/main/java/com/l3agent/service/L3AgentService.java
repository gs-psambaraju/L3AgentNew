package com.l3agent.service;

import com.l3agent.mcp.model.MCPRequest;
import com.l3agent.mcp.model.MCPResponse;
import com.l3agent.model.Ticket;
import com.l3agent.model.TicketMessage;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service interface for L3Agent operations.
 */
public interface L3AgentService {
    
    /**
     * Processes a ticket message and generates a response.
     * 
     * @param ticketId The ID of the ticket to process
     * @param message The message to process
     * @return An Optional containing the generated response if successful, empty otherwise
     */
    Optional<TicketMessage> processTicketMessage(String ticketId, TicketMessage message);
    
    /**
     * Analyzes a ticket and generates initial insights.
     * 
     * @param ticket The ticket to analyze
     * @return An Optional containing the analysis results if successful, empty otherwise
     */
    Optional<TicketMessage> analyzeTicket(Ticket ticket);
    
    /**
     * Generates embeddings on demand for specified code files or directories.
     * This is an explicit, manual process that allows control over when and 
     * what code gets embedded for semantic search.
     * 
     * @param path The file or directory path to generate embeddings for, null for all code
     * @param recursive Whether to recursively process directories
     * @return A map containing summary information about the embedding generation process
     */
    Map<String, Object> generateEmbeddingsOnDemand(String path, boolean recursive);
    
    /**
     * Generates embeddings on demand with additional configuration options.
     * 
     * @param options Map of configuration options including:
     *        - repository_path: The path to process
     *        - repository_namespace: The namespace to use for storing embeddings
     *        - recursive: Whether to process recursively
     *        - force_regenerate: Whether to force regeneration of existing embeddings
     * @return A map containing summary information about the embedding generation process
     */
    Map<String, Object> generateEmbeddingsOnDemand(Map<String, Object> options);
    
    /**
     * Retries failed embeddings that were previously recorded.
     * 
     * @param options Map of configuration options including:
     *        - repository_namespace: Optional namespace to filter failures
     * @return A map containing summary information about the retry process
     */
    Map<String, Object> retryFailedEmbeddings(Map<String, Object> options);
    
    /**
     * Builds a knowledge graph for specified code files or directories.
     * This allows explicit control over what gets represented in the knowledge graph.
     * 
     * @param path The file or directory path to build a knowledge graph for, null for all code
     * @param recursive Whether to recursively process directories
     * @return A map containing summary information about the knowledge graph building process
     */
    Map<String, Object> buildKnowledgeGraphOnDemand(String path, boolean recursive);
    
    /**
     * Analyzes code workflow on demand for specified code files or directories.
     * This analyzes execution flows, method calls, and data transfers within the codebase.
     * 
     * @param path The file or directory path to analyze, null for all code
     * @param recursive Whether to recursively process directories
     * @return A map containing summary information about the workflow analysis process
     */
    Map<String, Object> analyzeCodeWorkflowOnDemand(String path, boolean recursive);
    
    /**
     * Analyzes code workflow with option for cross-repository analysis.
     * 
     * @param path The file or directory path to analyze, null for all code
     * @param recursive Whether to recursively process directories
     * @param enableCrossRepoAnalysis Whether to analyze workflows across repository boundaries
     * @return A map containing summary information about the workflow analysis process
     */
    Map<String, Object> analyzeCodeWorkflowOnDemand(String path, boolean recursive, boolean enableCrossRepoAnalysis);
    
    /**
     * Generates all assets (embeddings, knowledge graph, and code workflow) in a single operation.
     * This is a convenience method that combines multiple operations to prepare all contextual
     * understanding components for L3Agent in one step.
     * 
     * @param path The file or directory path to process, null for all code
     * @param recursive Whether to recursively process directories
     * @return A map containing summary information about all generation processes
     */
    Map<String, Object> generateAllOnDemand(String path, boolean recursive);
    
    /**
     * Handles a support ticket, analyzing the issue and providing a response.
     * 
     * @param ticketId ID of the ticket to handle
     */
    void handleTicket(String ticketId);
    
    /**
     * Finds embeddings for a specific file path.
     * 
     * @param filePath The file path to search for
     * @param repositoryNamespace Optional repository namespace to limit the search
     * @return A list of embedding results matching the file path
     */
    List<VectorStoreService.SimilarityResult> findEmbeddingsByFilePath(String filePath, String repositoryNamespace);
    
    /**
     * Finds knowledge graph entities for a specific file path.
     * 
     * @param filePath The file path to search for
     * @return A list of entities associated with the file path
     */
    List<KnowledgeGraphService.CodeEntity> findEntitiesByFilePath(String filePath);
    
    /**
     * Finds workflow steps for a specific file path.
     * 
     * @param filePath The file path to search for
     * @return A list of workflow steps involving the file
     */
    List<CodeWorkflowService.WorkflowStep> findWorkflowsByFilePath(String filePath);
    
    /**
     * Processes a query using the MCP framework.
     * This method leverages the Model Control Plane for dynamic tool execution
     * to provide enhanced code analysis and question answering.
     * 
     * @param query The query to process
     * @return The MCPResponse containing the results of processing
     */
    MCPResponse processMCPQuery(String query);
    
    /**
     * Processes a custom MCP request with a specific execution plan.
     * This allows for more fine-grained control over tool execution.
     * 
     * @param request The custom MCP request to process
     * @return The MCPResponse containing the results of processing
     */
    MCPResponse processMCPRequest(MCPRequest request);
    
    /**
     * Enhances a ticket message by adding MCP analysis results.
     * 
     * @param ticketId The ID of the ticket to enhance
     * @param message The message to enhance
     * @return The enhanced ticket message containing MCP analysis
     */
    TicketMessage enhanceTicketWithMCP(String ticketId, TicketMessage message);
} 