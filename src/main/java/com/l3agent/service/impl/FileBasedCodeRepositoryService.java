package com.l3agent.service.impl;

import com.l3agent.service.CodeRepositoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A file-based implementation of the CodeRepositoryService interface.
 * Provides access to code repositories stored in a local directory.
 */
@Service
public class FileBasedCodeRepositoryService implements CodeRepositoryService {
    private static final Logger logger = LoggerFactory.getLogger(FileBasedCodeRepositoryService.class);
    
    private final Path codeDirectory;
    
    // File extensions to consider for code searches
    private static final Set<String> CODE_EXTENSIONS = new HashSet<>(Arrays.asList(
            ".java", ".js", ".ts", ".jsx", ".tsx", ".py", ".xml", ".json", ".yml", ".yaml", 
            ".properties", ".sh", ".md", ".html", ".css", ".scss"
    ));
    
    // Files and directories to exclude from searches
    private static final Set<String> EXCLUDED_DIRS = new HashSet<>(Arrays.asList(
            "node_modules", "target", "build", "dist", ".git", ".idea", ".vscode"
    ));
    
    /**
     * Constructs a new FileBasedCodeRepositoryService.
     * 
     * @param codeDirectory The directory containing the code repositories
     */
    public FileBasedCodeRepositoryService(@Value("${l3agent.code.directory:./data/code}") String codeDirectory) {
        this.codeDirectory = Paths.get(codeDirectory);
        
        if (!Files.exists(this.codeDirectory)) {
            try {
                Files.createDirectories(this.codeDirectory);
                logger.info("Created code directory: {}", this.codeDirectory);
            } catch (IOException e) {
                logger.error("Error creating code directory", e);
                throw new RuntimeException("Error creating code directory", e);
            }
        }
        
        logger.info("Code repository service initialized with directory: {}", this.codeDirectory);
        
        // Log the available repositories
        try {
            Files.list(this.codeDirectory)
                    .filter(Files::isDirectory)
                    .forEach(repo -> logger.info("Found repository: {}", repo.getFileName()));
        } catch (IOException e) {
            logger.error("Error listing repositories", e);
        }
    }
    
    @Override
    public List<CodeSnippet> searchCode(String query, int maxResults) {
        logger.info("Searching code for: {}", query);
        
        List<CodeSnippet> results = new ArrayList<>();
        try {
            Pattern pattern = Pattern.compile(Pattern.quote(query), Pattern.CASE_INSENSITIVE);
            
            // Visit all files in all repositories
            Files.walkFileTree(codeDirectory, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    String dirName = dir.getFileName().toString();
                    if (EXCLUDED_DIRS.contains(dirName)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }
                
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (results.size() >= maxResults) {
                        return FileVisitResult.TERMINATE;
                    }
                    
                    String fileName = file.getFileName().toString();
                    if (isCodeFile(fileName)) {
                        processFile(file, pattern, results, maxResults);
                    }
                    
                    return FileVisitResult.CONTINUE;
                }
            });
            
        } catch (IOException e) {
            logger.error("Error searching code", e);
        }
        
        return results;
    }
    
    @Override
    public Optional<String> getFileContent(String filePath) {
        Path fullPath = codeDirectory.resolve(filePath);
        
        try {
            if (Files.exists(fullPath) && Files.isRegularFile(fullPath)) {
                String content = Files.readString(fullPath, StandardCharsets.UTF_8);
                return Optional.of(content);
            }
        } catch (IOException e) {
            logger.error("Error reading file: {}", filePath, e);
        }
        
        return Optional.empty();
    }
    
    /**
     * Processes a file to find matches for the given pattern.
     * 
     * @param file The file to process
     * @param pattern The pattern to search for
     * @param results The list to add results to
     * @param maxResults The maximum number of results to collect
     */
    private void processFile(Path file, Pattern pattern, List<CodeSnippet> results, int maxResults) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        
        for (int i = 0; i < lines.size(); i++) {
            if (results.size() >= maxResults) {
                break;
            }
            
            String line = lines.get(i);
            if (pattern.matcher(line).find()) {
                // Found a match, extract a snippet with context
                int startLine = Math.max(0, i - 2);
                int endLine = Math.min(lines.size() - 1, i + 2);
                
                StringBuilder snippet = new StringBuilder();
                for (int j = startLine; j <= endLine; j++) {
                    snippet.append(lines.get(j)).append("\n");
                }
                
                // Create a relative path from the codeDirectory
                String relativePath = codeDirectory.relativize(file).toString();
                
                CodeSnippet codeSnippet = new CodeSnippet(
                        relativePath,
                        snippet.toString(),
                        startLine + 1, // Convert to 1-based line numbering
                        endLine + 1    // Convert to 1-based line numbering
                );
                
                Map<String, String> metadata = new HashMap<>();
                metadata.put("matchedLine", Integer.toString(i + 1)); // Convert to 1-based line numbering
                metadata.put("repository", getRepositoryName(file));
                codeSnippet.setMetadata(metadata);
                
                results.add(codeSnippet);
            }
        }
    }
    
    /**
     * Checks if a file is a code file based on its extension.
     * 
     * @param fileName The name of the file to check
     * @return true if the file is a code file, false otherwise
     */
    private boolean isCodeFile(String fileName) {
        return CODE_EXTENSIONS.stream()
                .anyMatch(ext -> fileName.toLowerCase().endsWith(ext));
    }
    
    /**
     * Gets the name of the repository containing the given file.
     * 
     * @param file The file to get the repository name for
     * @return The name of the repository
     */
    private String getRepositoryName(Path file) {
        Path relative = codeDirectory.relativize(file);
        if (relative.getNameCount() > 0) {
            return relative.getName(0).toString();
        }
        return "unknown";
    }
} 