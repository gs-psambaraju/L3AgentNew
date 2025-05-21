package com.l3agent.cli;

import com.l3agent.service.impl.InMemoryKnowledgeGraphService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Map;

/**
 * Command line runner for analyzing Java files to debug knowledge graph issues.
 * This runner is activated with the --analyze-file argument followed by the file path.
 */
@Component
public class AnalyzeCodeRunner implements CommandLineRunner, ExitCodeGenerator {
    
    private static final Logger logger = LoggerFactory.getLogger(AnalyzeCodeRunner.class);
    
    @Autowired
    private InMemoryKnowledgeGraphService knowledgeGraphService;
    
    @Autowired
    private ApplicationContext context;
    
    private int exitCode = 0;
    
    @Override
    public void run(String... args) throws Exception {
        // Check if this runner should be activated
        boolean shouldRun = Arrays.stream(args).anyMatch(arg -> arg.equals("--analyze-file"));
        if (!shouldRun) {
            return;
        }
        
        logger.info("Analyze Code Runner activated");
        
        // Parse the file path argument
        String filePath = null;
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals("--analyze-file")) {
                filePath = args[i + 1];
                break;
            }
        }
        
        if (filePath == null) {
            logger.error("No file path provided. Usage: --analyze-file <file_path>");
            exitCode = 1;
            return;
        }
        
        // Perform the analysis
        logger.info("Analyzing file: {}", filePath);
        Map<String, Object> results = knowledgeGraphService.analyzeJavaFile(filePath);
        
        // Print the results
        logger.info("Analysis Results:");
        results.forEach((key, value) -> logger.info("  {}: {}", key, value));
        
        exitCode = 0;
    }
    
    @Override
    public int getExitCode() {
        return exitCode;
    }
} 