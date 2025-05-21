package com.l3agent.mcp.tools.crossrepo;

import com.l3agent.mcp.config.L3AgentPathConfig;
import com.l3agent.mcp.tools.crossrepo.model.RepositoryInfo;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Scans for repositories in the configured directory.
 * Provides information about available repositories for the cross-repository tracer tool.
 */
@Component
public class RepositoryScanner {
    
    private static final Logger logger = LoggerFactory.getLogger(RepositoryScanner.class);
    
    @Value("${l3agent.crossrepo.repos-dir:${l3agent.paths.external-repos:./data/code}}")
    private String repositoriesDirectory;
    
    @Value("${l3agent.crossrepo.scan-on-startup:true}")
    private boolean scanOnStartup;
    
    @Autowired
    private L3AgentPathConfig pathConfig;
    
    // Cache of repository information
    private final ConcurrentMap<String, RepositoryInfo> repositoryCache = new ConcurrentHashMap<>();
    
    /**
     * Initializes the repository scanner.
     * If configured, scans repositories on startup.
     */
    @PostConstruct
    public void init() {
        // Update path from common config
        repositoriesDirectory = pathConfig.getExternalRepoPath();
        
        if (scanOnStartup) {
            logger.info("Scanning repositories on startup from: {}", repositoriesDirectory);
            scanRepositories();
        }
    }
    
    /**
     * Scans the repositories directory to find all available repositories.
     * Updates the internal cache with repository information.
     * 
     * @return The number of repositories found
     */
    public int scanRepositories() {
        repositoryCache.clear();
        
        File reposDir = new File(repositoriesDirectory);
        if (!reposDir.exists() || !reposDir.isDirectory()) {
            logger.warn("Repositories directory does not exist or is not a directory: {}", repositoriesDirectory);
            return 0;
        }
        
        File[] repoDirs = reposDir.listFiles(File::isDirectory);
        if (repoDirs == null) {
            logger.warn("Error listing directories in: {}", repositoriesDirectory);
            return 0;
        }
        
        int count = 0;
        for (File repoDir : repoDirs) {
            if (isValidRepository(repoDir)) {
                RepositoryInfo repo = new RepositoryInfo(
                    repoDir.getName(),
                    repoDir.getAbsolutePath(),
                    detectRepositoryDescription(repoDir)
                );
                repositoryCache.put(repo.getName(), repo);
                count++;
            }
        }
        
        logger.info("Found {} repositories in {}", count, repositoriesDirectory);
        return count;
    }
    
    /**
     * Gets all available repositories that have been scanned.
     * 
     * @return List of repository information
     */
    public List<RepositoryInfo> getRepositories() {
        if (repositoryCache.isEmpty()) {
            scanRepositories();
        }
        return new ArrayList<>(repositoryCache.values());
    }
    
    /**
     * Gets information about a specific repository by name.
     * 
     * @param name The repository name
     * @return The repository information or null if not found
     */
    public RepositoryInfo getRepository(String name) {
        if (repositoryCache.isEmpty()) {
            scanRepositories();
        }
        return repositoryCache.get(name);
    }
    
    /**
     * Gets all code files in a repository, filtered by extensions.
     * 
     * @param repositoryName The repository name
     * @param extensions The file extensions to include (e.g., ".java", ".xml")
     * @return List of code files
     */
    public List<File> getCodeFiles(String repositoryName, String... extensions) {
        RepositoryInfo repo = getRepository(repositoryName);
        if (repo == null) {
            logger.warn("Repository not found: {}", repositoryName);
            return Collections.emptyList();
        }
        
        List<File> codeFiles = new ArrayList<>();
        File repoDir = new File(repo.getPath());
        
        try {
            Files.walk(repoDir.toPath())
                .filter(Files::isRegularFile)
                .filter(path -> hasValidExtension(path, extensions))
                .forEach(path -> codeFiles.add(path.toFile()));
        } catch (Exception e) {
            logger.error("Error scanning code files in repository {}: {}", repositoryName, e.getMessage(), e);
        }
        
        return codeFiles;
    }
    
    /**
     * Checks if a directory is a valid repository.
     * A valid repository should contain code files or a .git directory.
     * 
     * @param dir The directory to check
     * @return true if the directory is a valid repository
     */
    private boolean isValidRepository(File dir) {
        // Check for .git directory (common in git repositories)
        File gitDir = new File(dir, ".git");
        if (gitDir.exists() && gitDir.isDirectory()) {
            return true;
        }
        
        // Check for src directory (common in Java/Maven projects)
        File srcDir = new File(dir, "src");
        if (srcDir.exists() && srcDir.isDirectory()) {
            return true;
        }
        
        // Check for common code files
        try {
            long codeFileCount = Files.walk(dir.toPath())
                .filter(Files::isRegularFile)
                .filter(path -> {
                    String filename = path.toString().toLowerCase();
                    return filename.endsWith(".java") || filename.endsWith(".kt") || 
                           filename.endsWith(".xml") || filename.endsWith(".gradle") ||
                           filename.endsWith(".properties");
                })
                .limit(5) // Stop after finding 5 files
                .count();
            
            return codeFileCount > 0;
        } catch (Exception e) {
            logger.warn("Error checking for code files in {}: {}", dir, e.getMessage());
            return false;
        }
    }
    
    /**
     * Attempts to detect a description for the repository by checking common files.
     * 
     * @param repoDir The repository directory
     * @return A description or null if none found
     */
    private String detectRepositoryDescription(File repoDir) {
        // Try to get description from README
        for (String readmeFile : new String[]{"README.md", "README.txt", "README"}) {
            File readme = new File(repoDir, readmeFile);
            if (readme.exists() && readme.isFile()) {
                try {
                    List<String> lines = Files.readAllLines(readme.toPath());
                    for (String line : lines) {
                        line = line.trim();
                        if (!line.isEmpty() && !line.startsWith("#") && line.length() > 10) {
                            // Return first meaningful line from README
                            return line;
                        }
                    }
                } catch (Exception e) {
                    logger.debug("Error reading README in {}: {}", repoDir.getName(), e.getMessage());
                }
            }
        }
        
        // Try to get description from pom.xml
        File pom = new File(repoDir, "pom.xml");
        if (pom.exists() && pom.isFile()) {
            try {
                String pomContent = Files.readString(pom.toPath());
                int descStart = pomContent.indexOf("<description>");
                int descEnd = pomContent.indexOf("</description>");
                if (descStart >= 0 && descEnd > descStart) {
                    return pomContent.substring(descStart + 13, descEnd).trim();
                }
            } catch (Exception e) {
                logger.debug("Error reading pom.xml in {}: {}", repoDir.getName(), e.getMessage());
            }
        }
        
        // No description found
        return null;
    }
    
    /**
     * Checks if a file has one of the specified extensions.
     * 
     * @param path The file path
     * @param extensions The extensions to check for
     * @return true if the file has one of the specified extensions
     */
    private boolean hasValidExtension(Path path, String... extensions) {
        if (extensions == null || extensions.length == 0) {
            // Default set of code file extensions if none specified
            extensions = new String[]{".java", ".kt", ".groovy", ".scala", ".xml", ".gradle", ".properties"};
        }
        
        String filename = path.toString().toLowerCase();
        for (String ext : extensions) {
            if (filename.endsWith(ext.toLowerCase())) {
                return true;
            }
        }
        
        return false;
    }
} 