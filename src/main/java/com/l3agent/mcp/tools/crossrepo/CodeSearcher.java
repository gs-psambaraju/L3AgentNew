package com.l3agent.mcp.tools.crossrepo;

import com.l3agent.mcp.tools.crossrepo.model.CodeReference;
import com.l3agent.mcp.tools.crossrepo.model.CrossRepoResult;
import com.l3agent.mcp.tools.crossrepo.model.RepositoryInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Provides code search capabilities across multiple repositories.
 * This class implements the actual search functionality used by the cross-repository tracer tool.
 */
@Component
public class CodeSearcher {
    
    private static final Logger logger = LoggerFactory.getLogger(CodeSearcher.class);
    
    @Value("${l3agent.crossrepo.context-lines:3}")
    private int contextLines;
    
    @Value("${l3agent.crossrepo.max-references-per-repo:1000}")
    private int maxReferencesPerRepo;
    
    @Value("${l3agent.crossrepo.thread-pool-size:4}")
    private int threadPoolSize;
    
    @Value("${l3agent.crossrepo.search-timeout-seconds:60}")
    private int searchTimeoutSeconds;
    
    private final RepositoryScanner repositoryScanner;
    private ExecutorService executorService;
    
    /**
     * Creates a new code searcher.
     * 
     * @param repositoryScanner The repository scanner to use
     */
    @Autowired
    public CodeSearcher(RepositoryScanner repositoryScanner) {
        this.repositoryScanner = repositoryScanner;
    }
    
    /**
     * Initializes the thread pool if it hasn't been already.
     */
    private void initThreadPool() {
        if (executorService == null || executorService.isShutdown()) {
            executorService = Executors.newFixedThreadPool(threadPoolSize);
        }
    }
    
    /**
     * Searches for code across all available repositories.
     * 
     * @param searchTerm The term to search for
     * @param useRegex Whether to treat the search term as a regular expression
     * @param caseSensitive Whether the search should be case-sensitive
     * @param extensions File extensions to include in the search (e.g., "java", "xml")
     * @return The search results
     */
    public CrossRepoResult search(String searchTerm, boolean useRegex, boolean caseSensitive, String... extensions) {
        initThreadPool();
        
        long startTime = System.currentTimeMillis();
        CrossRepoResult result = new CrossRepoResult(searchTerm);
        result.setUseRegex(useRegex);
        result.setCaseSensitive(caseSensitive);
        
        if (extensions != null) {
            for (String ext : extensions) {
                result.addIncludedExtension(ext);
            }
        }
        
        // Convert extensions to the format needed for file filtering
        String[] extensionsArray = result.getIncludedExtensions().toArray(new String[0]);
        
        // Get all repositories
        List<RepositoryInfo> repositories = repositoryScanner.getRepositories();
        if (repositories.isEmpty()) {
            logger.warn("No repositories found for search");
            result.setSearchTimeMs(System.currentTimeMillis() - startTime);
            return result;
        }
        
        // Compile the search pattern
        Pattern pattern;
        try {
            if (useRegex) {
                pattern = caseSensitive 
                    ? Pattern.compile(searchTerm) 
                    : Pattern.compile(searchTerm, Pattern.CASE_INSENSITIVE);
            } else {
                String escapedTerm = Pattern.quote(searchTerm);
                pattern = caseSensitive 
                    ? Pattern.compile(escapedTerm) 
                    : Pattern.compile(escapedTerm, Pattern.CASE_INSENSITIVE);
            }
        } catch (Exception e) {
            logger.error("Invalid search pattern: {}", searchTerm, e);
            result.setSearchTimeMs(System.currentTimeMillis() - startTime);
            return result;
        }
        
        // Create search tasks
        List<Callable<List<CodeReference>>> tasks = new ArrayList<>();
        for (RepositoryInfo repo : repositories) {
            tasks.add(() -> searchRepository(repo, pattern, extensionsArray));
            result.addSearchedRepository(repo.getName());
        }
        
        try {
            // Execute searches in parallel
            List<Future<List<CodeReference>>> futures = executorService.invokeAll(
                tasks, searchTimeoutSeconds, TimeUnit.SECONDS);
            
            // Collect results
            for (Future<List<CodeReference>> future : futures) {
                try {
                    List<CodeReference> references = future.get();
                    for (CodeReference ref : references) {
                        result.addReference(ref);
                    }
                } catch (ExecutionException e) {
                    logger.error("Error searching repository: {}", e.getMessage(), e);
                } catch (CancellationException e) {
                    logger.warn("Repository search timed out after {} seconds", searchTimeoutSeconds);
                }
            }
        } catch (InterruptedException e) {
            logger.error("Search interrupted: {}", e.getMessage(), e);
            Thread.currentThread().interrupt();
        }
        
        // Sort results by repository and file path
        result.setReferences(result.getReferences().stream()
            .sorted(Comparator
                .comparing(CodeReference::getRepository)
                .thenComparing(CodeReference::getFilePath)
                .thenComparingInt(CodeReference::getLineNumber))
            .collect(Collectors.toList()));
        
        result.setSearchTimeMs(System.currentTimeMillis() - startTime);
        logger.info("Found {} references in {} repositories ({}ms)", 
            result.getReferenceCount(), result.getMatchedRepositoryCount(), result.getSearchTimeMs());
        
        return result;
    }
    
    /**
     * Searches a single repository for matches.
     * 
     * @param repo The repository to search
     * @param pattern The compiled search pattern
     * @param extensions The file extensions to include
     * @return The code references found in the repository
     */
    private List<CodeReference> searchRepository(RepositoryInfo repo, Pattern pattern, String[] extensions) {
        List<CodeReference> references = new ArrayList<>();
        
        try {
            List<File> codeFiles = repositoryScanner.getCodeFiles(repo.getName(), extensions);
            logger.debug("Searching {} files in repository: {}", codeFiles.size(), repo.getName());
            
            for (File file : codeFiles) {
                try {
                    searchFile(repo.getName(), file, pattern, references);
                    
                    // Stop if we've reached the maximum number of references for this repo
                    if (references.size() >= maxReferencesPerRepo) {
                        logger.warn("Reached maximum number of references ({}) for repository {}", 
                            maxReferencesPerRepo, repo.getName());
                        break;
                    }
                } catch (Exception e) {
                    logger.warn("Error searching file {}: {}", file.getPath(), e.getMessage());
                }
            }
        } catch (Exception e) {
            logger.error("Error searching repository {}: {}", repo.getName(), e.getMessage(), e);
        }
        
        return references;
    }
    
    /**
     * Searches a single file for matches.
     * 
     * @param repoName The repository name
     * @param file The file to search
     * @param pattern The compiled search pattern
     * @param references The list to add found references to
     */
    private void searchFile(String repoName, File file, Pattern pattern, List<CodeReference> references) {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            // Read the entire file
            List<String> lines = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
            
            // Search for matches
            for (int i = 0; i < lines.size(); i++) {
                line = lines.get(i);
                Matcher matcher = pattern.matcher(line);
                
                if (matcher.find()) {
                    // Get context lines
                    List<String> context = getContextLines(lines, i, contextLines);
                    
                    // Create a relative file path from the repo root
                    String relativePath = getRelativePathFromRepo(repoName, file);
                    
                    // Create a reference
                    CodeReference reference = new CodeReference(
                        repoName,
                        relativePath,
                        i + 1,  // Convert to 1-based line number
                        line,
                        context
                    );
                    
                    references.add(reference);
                    
                    // Stop if we've reached the maximum number of references
                    if (references.size() >= maxReferencesPerRepo) {
                        break;
                    }
                }
            }
        } catch (IOException e) {
            logger.warn("Error reading file {}: {}", file.getPath(), e.getMessage());
        }
    }
    
    /**
     * Gets context lines around a match.
     * 
     * @param lines All lines in the file
     * @param matchLine The line number of the match (0-based)
     * @param contextLineCount The number of context lines to include before and after
     * @return The context lines
     */
    private List<String> getContextLines(List<String> lines, int matchLine, int contextLineCount) {
        List<String> context = new ArrayList<>();
        
        // Add lines before the match
        int start = Math.max(0, matchLine - contextLineCount);
        for (int i = start; i < matchLine; i++) {
            context.add(lines.get(i));
        }
        
        // The match line itself is not included here (it's stored separately)
        
        // Add lines after the match
        int end = Math.min(lines.size() - 1, matchLine + contextLineCount);
        for (int i = matchLine + 1; i <= end; i++) {
            context.add(lines.get(i));
        }
        
        return context;
    }
    
    /**
     * Gets a file path relative to its repository root.
     * 
     * @param repoName The repository name
     * @param file The file
     * @return The relative path
     */
    private String getRelativePathFromRepo(String repoName, File file) {
        RepositoryInfo repo = repositoryScanner.getRepository(repoName);
        if (repo == null) {
            // Fallback to absolute path if repo not found
            return file.getAbsolutePath();
        }
        
        Path repoPath = Paths.get(repo.getPath());
        Path filePath = file.toPath();
        
        if (filePath.startsWith(repoPath)) {
            return repoPath.relativize(filePath).toString();
        } else {
            return file.getAbsolutePath();
        }
    }
    
    /**
     * Shuts down the thread pool.
     */
    public void shutdown() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
} 