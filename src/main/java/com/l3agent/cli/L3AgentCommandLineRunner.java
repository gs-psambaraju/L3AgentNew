package com.l3agent.cli;

import com.l3agent.service.L3AgentService;
import com.l3agent.service.KnowledgeGraphService;
import com.l3agent.service.VectorStoreService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

/**
 * Command-line interface for L3Agent operations.
 * Provides a way to run operations like embedding generation and knowledge graph building from the command line.
 */
@Component
@Order(1)
public class L3AgentCommandLineRunner implements CommandLineRunner {
    
    @Autowired
    private L3AgentService l3AgentService;
    
    @Autowired
    private ApplicationContext context;
    
    @Override
    public void run(String... args) throws Exception {
        if (args.length > 0) {
            switch (args[0]) {
                case "generate-embeddings":
                    handleGenerateEmbeddings(args);
                    break;
                case "build-knowledge-graph":
                    handleBuildKnowledgeGraph(args);
                    break;
                case "generate-all":
                    handleGenerateAll(args);
                    break;
                case "analyze-workflow":
                    handleAnalyzeWorkflow(args);
                    break;
                case "help":
                    printHelp();
                    break;
                case "inspect":
                    handleInspectCommand(args);
                    break;
                default:
                    // If no recognized command, ignore - this allows normal Spring Boot startup
                    return;
            }
            
            // Exit after command is complete if running from command line
            if (Arrays.asList(args).contains("--exit")) {
                System.exit(0);
            }
        }
    }
    
    private void handleGenerateEmbeddings(String[] args) {
        // Parse command line arguments for path and recursive flag
        String path = getArgValue(args, "--path", null);
        boolean recursive = hasArg(args, "--recursive");
        boolean verbose = hasArg(args, "--verbose");
        
        System.out.println("Starting embedding generation...");
        System.out.println("Path: " + (path != null ? path : "default (./data/code)"));
        System.out.println("Recursive: " + recursive);
        System.out.println("Verbose output: " + verbose);
        
        if (verbose) {
            System.out.println("This process might take several minutes for large codebases.");
            System.out.println("Processing code files and generating vector embeddings...");
        }
        
        Map<String, Object> result = l3AgentService.generateEmbeddingsOnDemand(path, recursive);
        
        System.out.println("\nEmbedding generation completed:");
        System.out.println("Status: " + result.get("status"));
        System.out.println("Files processed: " + result.get("files_processed"));
        System.out.println("Total chunks: " + result.get("total_chunks"));
        System.out.println("Successful embeddings: " + result.get("successful_embeddings"));
        System.out.println("Failed embeddings: " + result.get("failed_embeddings"));
        System.out.println("Skipped boilerplate: " + result.get("skipped_boilerplate"));
        System.out.println("Processing time: " + result.get("duration_ms") + " ms");
        
        if (!result.get("status").equals("success")) {
            System.out.println("Error message: " + result.get("message"));
        }
        
        // If there were failures, provide more details
        if (result.containsKey("failed_embeddings") && (Integer)result.get("failed_embeddings") > 0) {
            System.out.println("\nWarning: Some embeddings failed to generate.");
            System.out.println("Common causes: API rate limits, network errors, or invalid code chunks.");
            System.out.println("Check the logs for detailed error messages.");
        }
    }
    
    private void handleBuildKnowledgeGraph(String[] args) {
        // Parse command line arguments for path and recursive flag
        String path = getArgValue(args, "--path", null);
        boolean recursive = hasArg(args, "--recursive");
        boolean verbose = hasArg(args, "--verbose");
        
        System.out.println("Starting knowledge graph construction...");
        System.out.println("Path: " + (path != null ? path : "default (./data/code)"));
        System.out.println("Recursive: " + recursive);
        System.out.println("Verbose output: " + verbose);
        
        if (verbose) {
            System.out.println("This process might take several minutes for large codebases.");
            System.out.println("Parsing code files and extracting relationships...");
        }
        
        Map<String, Object> result = l3AgentService.buildKnowledgeGraphOnDemand(path, recursive);
        
        System.out.println("\nKnowledge graph construction completed:");
        System.out.println("Status: " + result.get("status"));
        System.out.println("Files processed: " + result.get("files_processed"));
        System.out.println("Entities created: " + result.get("entities_created"));
        System.out.println("Relationships created: " + result.get("relationships_created"));
        System.out.println("Processing time: " + result.get("duration_ms") + " ms");
        
        if (!result.get("status").equals("success")) {
            System.out.println("Error message: " + result.get("message"));
        }
    }
    
    private void handleGenerateAll(String[] args) {
        // Parse command line arguments for path and recursive flag
        String path = getArgValue(args, "--path", null);
        boolean recursive = hasArg(args, "--recursive");
        boolean verbose = hasArg(args, "--verbose");
        
        System.out.println("Starting combined generation (embeddings, knowledge graph, and workflow)...");
        System.out.println("Path: " + (path != null ? path : "default (./data/code)"));
        System.out.println("Recursive: " + recursive);
        System.out.println("Verbose output: " + verbose);
        
        if (verbose) {
            System.out.println("This process might take several minutes for large codebases.");
            System.out.println("Processing will include embedding generation, knowledge graph building, and workflow analysis.");
        }
        
        Map<String, Object> result = l3AgentService.generateAllOnDemand(path, recursive);
        
        // Overall summary
        System.out.println("\nCombined generation process completed:");
        System.out.println("Overall status: " + result.get("status"));
        System.out.println("Total processing time: " + result.get("total_duration_ms") + " ms");
        
        // Embedding results
        Map<String, Object> embeddingResults = (Map<String, Object>) result.get("embeddings");
        System.out.println("\nEmbedding generation results:");
        System.out.println("Status: " + embeddingResults.get("status"));
        System.out.println("Files processed: " + embeddingResults.get("files_processed"));
        System.out.println("Total chunks: " + embeddingResults.get("total_chunks"));
        System.out.println("Successful embeddings: " + embeddingResults.get("successful_embeddings"));
        System.out.println("Failed embeddings: " + embeddingResults.get("failed_embeddings"));
        System.out.println("Skipped boilerplate: " + embeddingResults.get("skipped_boilerplate"));
        System.out.println("Processing time: " + embeddingResults.get("duration_ms") + " ms");
        
        // If there were failures, provide more details
        if (embeddingResults.containsKey("failed_embeddings") && (Integer)embeddingResults.get("failed_embeddings") > 0) {
            System.out.println("Warning: Some embeddings failed to generate.");
            System.out.println("Common causes: API rate limits, network errors, or invalid code chunks.");
            System.out.println("Check the logs for detailed error messages.");
        }
        
        // Knowledge graph results
        Map<String, Object> kgResults = (Map<String, Object>) result.get("knowledge_graph");
        System.out.println("\nKnowledge graph results:");
        System.out.println("Status: " + kgResults.get("status"));
        System.out.println("Files processed: " + kgResults.get("files_processed"));
        System.out.println("Entities created: " + kgResults.get("entities_created"));
        System.out.println("Relationships created: " + kgResults.get("relationships_created"));
        System.out.println("Processing time: " + kgResults.get("duration_ms") + " ms");
        
        // Workflow results
        Map<String, Object> workflowResults = (Map<String, Object>) result.get("workflow");
        System.out.println("\nWorkflow analysis results:");
        System.out.println("Status: " + workflowResults.get("status"));
        if (workflowResults.containsKey("message")) {
            System.out.println("Message: " + workflowResults.get("message"));
        }
    }
    
    private String getArgValue(String[] args, String argName, String defaultValue) {
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals(argName)) {
                return args[i + 1];
            }
        }
        return defaultValue;
    }
    
    private boolean hasArg(String[] args, String argName) {
        return Arrays.asList(args).contains(argName);
    }
    
    private void printHelp() {
        System.out.println("L3Agent CLI Commands:");
        System.out.println("======================");
        System.out.println("  generate-embeddings [--path <path>] [--recursive] [--verbose] [--exit]");
        System.out.println("      Generates vector embeddings for code files");
        System.out.println("      --path: Path to directory or file (default: ./data/code)");
        System.out.println("      --recursive: Process subdirectories recursively");
        System.out.println("      --verbose: Show detailed progress");
        System.out.println("      --exit: Exit application after command completes");
        System.out.println();
        System.out.println("  build-knowledge-graph [--path <path>] [--recursive] [--verbose] [--exit]");
        System.out.println("      Builds knowledge graph of code relationships");
        System.out.println("      --path: Path to directory or file (default: ./data/code)");
        System.out.println("      --recursive: Process subdirectories recursively");
        System.out.println("      --verbose: Show detailed progress");
        System.out.println("      --exit: Exit application after command completes");
        System.out.println();
        System.out.println("  analyze-workflow [--path <path>] [--recursive] [--verbose] [--cross-repo] [--exit]");
        System.out.println("      Analyzes code workflow and execution paths");
        System.out.println("      --path: Path to directory or file (default: ./data/code)");
        System.out.println("      --recursive: Process subdirectories recursively");
        System.out.println("      --cross-repo: Enable cross-repository analysis");
        System.out.println("      --verbose: Show detailed progress");
        System.out.println("      --exit: Exit application after command completes");
        System.out.println();
        System.out.println("  generate-all [--path <path>] [--recursive] [--verbose] [--exit]");
        System.out.println("      Performs all generation tasks in one operation (embeddings, knowledge graph, workflow)");
        System.out.println("      --path: Path to directory or file (default: ./data/code)");
        System.out.println("      --recursive: Process subdirectories recursively");
        System.out.println("      --verbose: Show detailed progress");
        System.out.println("      --exit: Exit application after command completes");
        System.out.println();
        System.out.println("  help");
        System.out.println("      Shows this help message");
        System.out.println("  inspect <filepath> [--embeddings] [--entities]");
        System.out.println("      Displays embedding and knowledge graph information for a specific file");
        System.out.println("      --embeddings   Display embedding information (default: true)");
        System.out.println("      --entities     Display knowledge graph entity information (default: true)");
    }
    
    /**
     * Handles the 'inspect' command to examine embeddings and knowledge graph entries for a specific file.
     * 
     * @param args Command arguments
     */
    private void handleInspectCommand(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: inspect <filepath> [--embeddings] [--entities]");
            System.out.println("  --embeddings   Display embedding information (default: true)");
            System.out.println("  --entities     Display knowledge graph entity information (default: true)");
            return;
        }
        
        String filePath = args[1];
        boolean showEmbeddings = true;
        boolean showEntities = true;
        
        // Parse optional flags
        for (int i = 2; i < args.length; i++) {
            String arg = args[i].toLowerCase();
            if (arg.equals("--embeddings=false")) {
                showEmbeddings = false;
            } else if (arg.equals("--entities=false")) {
                showEntities = false;
            }
        }
        
        System.out.println("Inspecting file: " + filePath);
        
        if (showEmbeddings) {
            // Display embeddings for the file
            List<VectorStoreService.SimilarityResult> embeddings = 
                l3AgentService.findEmbeddingsByFilePath(filePath, null);
            
            System.out.println("=== EMBEDDINGS ===");
            System.out.println("Found " + embeddings.size() + " embeddings for file " + filePath);
            
            for (int i = 0; i < embeddings.size(); i++) {
                VectorStoreService.SimilarityResult result = embeddings.get(i);
                VectorStoreService.EmbeddingMetadata metadata = result.getMetadata();
                
                System.out.println("Embedding #" + (i + 1) + ":");
                System.out.println("  ID: " + result.getId());
                System.out.println("  Type: " + metadata.getType());
                System.out.println("  File: " + metadata.getFilePath());
                System.out.println("  Lines: " + metadata.getStartLine() + "-" + metadata.getEndLine());
                
                // Only log the first 100 chars of content to avoid overwhelming the console
                String contentPreview = metadata.getContent();
                if (contentPreview != null && contentPreview.length() > 100) {
                    contentPreview = contentPreview.substring(0, 100) + "...";
                }
                System.out.println("  Content preview: " + contentPreview);
                
                // Check if it has a description
                if (metadata.getDescription() != null && !metadata.getDescription().isEmpty()) {
                    String descriptionPreview = metadata.getDescription();
                    if (descriptionPreview.length() > 100) {
                        descriptionPreview = descriptionPreview.substring(0, 100) + "...";
                    }
                    System.out.println("  Description: " + descriptionPreview);
                } else {
                    System.out.println("  Description: <none>");
                }
                
                System.out.println(""); // Empty line for readability
            }
        }
        
        if (showEntities) {
            // Display knowledge graph entities for the file
            List<KnowledgeGraphService.CodeEntity> entities = 
                l3AgentService.findEntitiesByFilePath(filePath);
            
            System.out.println("=== KNOWLEDGE GRAPH ENTITIES ===");
            System.out.println("Found " + entities.size() + " entities for file " + filePath);
            
            for (int i = 0; i < entities.size(); i++) {
                KnowledgeGraphService.CodeEntity entity = entities.get(i);
                
                System.out.println("Entity #" + (i + 1) + ":");
                System.out.println("  ID: " + entity.getId());
                System.out.println("  Type: " + entity.getType());
                System.out.println("  Name: " + entity.getName());
                System.out.println("  Fully Qualified Name: " + entity.getFullyQualifiedName());
                System.out.println("  File: " + entity.getFilePath());
                System.out.println("  Lines: " + entity.getStartLine() + "-" + entity.getEndLine());
                
                // Print additional properties if available
                if (entity.getProperties() != null && !entity.getProperties().isEmpty()) {
                    System.out.println("  Additional Properties:");
                    for (Map.Entry<String, String> entry : entity.getProperties().entrySet()) {
                        System.out.println("    " + entry.getKey() + ": " + entry.getValue());
                    }
                }
                
                System.out.println(""); // Empty line for readability
            }
        }
    }
    
    /**
     * Handles the 'analyze-workflow' command to analyze code workflow.
     * 
     * @param args Command arguments
     */
    private void handleAnalyzeWorkflow(String[] args) {
        // Parse command line arguments
        String path = getArgValue(args, "--path", null);
        boolean recursive = hasArg(args, "--recursive");
        boolean verbose = hasArg(args, "--verbose");
        boolean crossRepo = hasArg(args, "--cross-repo");
        
        System.out.println("Starting code workflow analysis...");
        System.out.println("Path: " + (path != null ? path : "default (./data/code)"));
        System.out.println("Recursive: " + recursive);
        System.out.println("Cross-repository analysis: " + crossRepo);
        System.out.println("Verbose output: " + verbose);
        
        if (verbose) {
            System.out.println("This process might take several minutes for complex codebases.");
            System.out.println("Analyzing code execution paths and method calls...");
        }
        
        Map<String, Object> result = l3AgentService.analyzeCodeWorkflowOnDemand(path, recursive, crossRepo);
        
        System.out.println("\nWorkflow analysis completed:");
        System.out.println("Status: " + result.get("status"));
        
        // Display detailed results
        if (result.containsKey("files_processed")) {
            System.out.println("Files processed: " + result.get("files_processed"));
        }
        if (result.containsKey("workflow_steps")) {
            System.out.println("Workflow steps extracted: " + result.get("workflow_steps"));
        }
        if (result.containsKey("cross_repository_steps")) {
            System.out.println("Cross-repository steps: " + result.get("cross_repository_steps"));
        }
        if (result.containsKey("entry_points")) {
            System.out.println("Entry points identified: " + result.get("entry_points"));
        }
        if (result.containsKey("repositories")) {
            System.out.println("Repositories analyzed: " + result.get("repositories"));
        }
        System.out.println("Processing time: " + result.get("duration_ms") + " ms");
        
        if (!result.get("status").equals("success")) {
            System.out.println("Error message: " + result.get("message"));
        }
    }
} 