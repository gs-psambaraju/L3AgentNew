package com.l3agent.cli;

import com.l3agent.service.KnowledgeGraphService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * Command line runner for rebuilding the knowledge graph.
 * This runner is activated with the --rebuild-knowledge-graph argument.
 */
@Component
public class RebuildKnowledgeGraphRunner implements CommandLineRunner, ExitCodeGenerator {
    
    private static final Logger logger = LoggerFactory.getLogger(RebuildKnowledgeGraphRunner.class);
    
    @Autowired
    private KnowledgeGraphService knowledgeGraphService;
    
    private int exitCode = 0;
    
    @Override
    public void run(String... args) throws Exception {
        // Check if this runner should be activated
        boolean shouldRun = Arrays.stream(args).anyMatch(arg -> arg.equals("--rebuild-knowledge-graph"));
        if (!shouldRun) {
            return;
        }
        
        logger.info("Rebuild Knowledge Graph Runner activated");
        
        // Parse optional arguments
        String repositoryPath = null;
        boolean recursive = true;
        
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals("--repository")) {
                repositoryPath = args[i + 1];
            }
            if (args[i].equals("--recursive")) {
                recursive = Boolean.parseBoolean(args[i + 1]);
            }
        }
        
        try {
            if (repositoryPath != null) {
                logger.info("Rebuilding knowledge graph for repository: {}, recursive: {}", repositoryPath, recursive);
                knowledgeGraphService.buildKnowledgeGraph(repositoryPath, recursive);
            } else {
                logger.info("Rebuilding entire knowledge graph");
                knowledgeGraphService.rebuildEntireKnowledgeGraph();
            }
            
            logger.info("Knowledge graph rebuild completed successfully");
            exitCode = 0;
        } catch (Exception e) {
            logger.error("Error rebuilding knowledge graph", e);
            exitCode = 1;
        }
    }
    
    @Override
    public int getExitCode() {
        return exitCode;
    }
} 