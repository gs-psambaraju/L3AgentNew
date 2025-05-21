package com.l3agent.service.impl;

import com.l3agent.service.CodeWorkflowService;
import com.l3agent.service.JavaParserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.beans.factory.DisposableBean;

import java.io.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Implementation of CodeWorkflowService for JVM-based languages.
 * Focuses on analyzing Java code for method calls, inheritance, and data flows.
 * Supports cross-repository analysis for more comprehensive workflow understanding.
 */
@Service
public class JvmCodeWorkflowService implements CodeWorkflowService, DisposableBean {
    
    private static final Logger logger = LoggerFactory.getLogger(JvmCodeWorkflowService.class);
    
    @Autowired
    private JavaParserService javaParserService;
    
    // Cache for workflow data - modified to store by repository
    private Map<String, Map<String, List<WorkflowStep>>> workflowByFileByRepoCache = new ConcurrentHashMap<>();
    private Map<String, Set<String>> classHierarchyCache = new ConcurrentHashMap<>();
    private Map<String, String> classToFileCache = new ConcurrentHashMap<>();
    private Map<String, String> classToRepoCache = new ConcurrentHashMap<>();
    
    // New caches for advanced analysis
    private Map<String, Set<String>> interfaceImplementations = new ConcurrentHashMap<>();
    private Map<String, String> classTypes = new ConcurrentHashMap<>();
    private Map<String, String> patternDetectionCache = new ConcurrentHashMap<>();
    
    // Additional caches for execution paths and method chains
    private Map<String, List<List<WorkflowStep>>> executionPathCache = new ConcurrentHashMap<>();
    private Map<String, Map<String, List<WorkflowStep>>> methodChainCache = new ConcurrentHashMap<>();
    private Map<String, Map<String, Integer>> workflowIndexCache = new ConcurrentHashMap<>();
    private Map<String, Map<String, Double>> methodChainConfidence = new ConcurrentHashMap<>();
    private AtomicLong memoryUsage = new AtomicLong(0);
    
    // Error tracking
    private Map<String, List<AnalysisError>> analysisErrors = new ConcurrentHashMap<>();
    
    // Cross-repository settings
    private Set<String> processedRepositories = new HashSet<>();
    private boolean enableCrossRepoAnalysis = false;
    
    // Configuration properties
    @Value("${l3agent.workflow.data-dir:./data/workflow}")
    private String workflowDataDir;
    
    @Value("${l3agent.workflow.persistence-enabled:true}")
    private boolean persistenceEnabled;
    
    @Value("${l3agent.workflow.thread-count:4}")
    private int threadCount;
    
    @Value("${l3agent.workflow.batch-size:10}")
    private int batchSize;
    
    @Value("${l3agent.workflow.parallel-processing:true}")
    private boolean parallelProcessing;
    
    @Value("${l3agent.workflow.detect-patterns:true}")
    private boolean detectPatterns;
    
    @Value("${l3agent.workflow.min-confidence-threshold:0.5}")
    private double minConfidenceThreshold;
    
    @Value("${l3agent.workflow.track-interface-implementations:true}")
    private boolean trackInterfaceImplementations;
    
    @Value("${l3agent.workflow.max-path-depth:10}")
    private int workflowMaxPathDepth;
    
    @Value("${l3agent.workflow.max-memory-mb:100}")
    private int maxMemoryMb;
    
    @Value("${l3agent.workflow.max-child-depth:15}")
    private int maxChildDepth;
    
    @Value("${l3agent.workflow.chain-min-frequency:2}")
    private int chainMinFrequency;
    
    /**
     * Initialize the service and load existing workflow data if available.
     */
    @EventListener
    public void onApplicationEvent(ContextRefreshedEvent event) {
        logger.info("Initializing JvmCodeWorkflowService...");
        if (persistenceEnabled) {
            loadWorkflowData();
        }
        logger.info("JvmCodeWorkflowService initialized. {} repositories loaded.", processedRepositories.size());
    }
    
    @Override
    public Map<String, Object> analyzeCodeWorkflow(String path, boolean recursive) {
        return analyzeCodeWorkflow(path, recursive, false);
    }
    
    @Override
    public Map<String, Object> analyzeCodeWorkflow(String path, boolean recursive, boolean enableCrossRepoAnalysis) {
        logger.info("Analyzing code workflow for path: {}, recursive: {}, cross-repo: {}", 
                  path, recursive, enableCrossRepoAnalysis);
        
        this.enableCrossRepoAnalysis = enableCrossRepoAnalysis;
        Map<String, Object> result = new HashMap<>();
        long startTime = System.currentTimeMillis();
        
        try {
            // Extract the repository we're processing
            String repoName = extractRepositoryName(path);
            if (repoName != null) {
                // Clear previous workflow data only for this repository
                clearCaches(repoName);
                processedRepositories.add(repoName);
                logger.info("Analyzing repository: {}", repoName);
            } else {
                // If repo name can't be determined, use a default
                repoName = "default";
                clearCaches(repoName);
                processedRepositories.add(repoName);
            }
            
            // Process the primary repository
            List<File> filesToProcess = collectFilesForAnalysis(path, recursive);
            logger.info("Found {} Java files to analyze in primary path", filesToProcess.size());
            
            // Process files in the primary repository
            processRepository(filesToProcess, repoName);
            
            // Process additional repositories if cross-repo analysis is enabled
            if (enableCrossRepoAnalysis) {
                List<String> additionalRepos = findOtherRepositories();
                int crossRepoTotal = 0;
                
                for (String repo : additionalRepos) {
                    // Skip if already processed or if it's the data folder
                    if (processedRepositories.contains(repo) || repo.equals("data")) {
                        continue;
                    }
                    
                    // Process the additional repository
                    String repoPath = "./data/code/" + repo;
                    List<File> repoFiles = collectFilesForAnalysis(repoPath, true);
                    logger.info("Found {} Java files to analyze in repository {}", repoFiles.size(), repo);
                    
                    processRepository(repoFiles, repo);
                    processedRepositories.add(repo);
                    crossRepoTotal += repoFiles.size();
                }
                
                // Resolve cross-repository references
                resolveCrossRepositoryReferences();
                
                // Perform enhanced cross-repository resolution
                enhancedCrossRepositoryResolution();
                
                result.put("cross_repo_analysis", true);
                result.put("repositories_analyzed", processedRepositories.size());
                result.put("additional_files_analyzed", crossRepoTotal);
            } else {
                result.put("cross_repo_analysis", false);
            }
            
            // Analyze entry points (potential workflow starting points)
            List<String> entryPoints = findEntryPoints();
            
            // Calculate metrics
            int totalWorkflowSteps = 0;
            int crossRepositorySteps = 0;
            
            for (Map<String, List<WorkflowStep>> repoWorkflows : workflowByFileByRepoCache.values()) {
                for (List<WorkflowStep> steps : repoWorkflows.values()) {
                    totalWorkflowSteps += steps.size();
                    for (WorkflowStep step : steps) {
                        if (step.isCrossRepository()) {
                            crossRepositorySteps++;
                        }
                    }
                }
            }
            
            // Build result object
            result.put("status", "success");
            result.put("files_processed", filesToProcess.size());
            result.put("workflow_steps", totalWorkflowSteps);
            result.put("cross_repository_steps", crossRepositorySteps);
            result.put("entry_points", entryPoints.size());
            result.put("repositories", new ArrayList<>(processedRepositories));
            
            result.put("message", String.format("Analyzed %d files and extracted %d workflow steps", 
                    filesToProcess.size(), totalWorkflowSteps));
            
            long duration = System.currentTimeMillis() - startTime;
            result.put("duration_ms", duration);
            
            logger.info("Workflow analysis completed in {} ms, found {} workflow steps", 
                    duration, totalWorkflowSteps);
            
        } catch (Exception e) {
            logger.error("Error analyzing code workflow: {}", e.getMessage(), e);
            result.put("status", "error");
            result.put("message", "Error: " + e.getMessage());
        }
        
        return result;
    }
    
    @Override
    public List<WorkflowStep> findWorkflowsByFilePath(String filePath) {
        List<WorkflowStep> result = new ArrayList<>();
        
        // Search across all repositories
        for (Map<String, List<WorkflowStep>> repoWorkflows : workflowByFileByRepoCache.values()) {
            // Find workflows where this file is the source
            if (repoWorkflows.containsKey(filePath)) {
                result.addAll(repoWorkflows.get(filePath));
            }
            
            // Find workflows where this file is the target
            for (List<WorkflowStep> steps : repoWorkflows.values()) {
                for (WorkflowStep step : steps) {
                    if (filePath.equals(step.getTargetFile()) && !result.contains(step)) {
                        result.add(step);
                    }
                }
            }
        }
        
        return result;
    }
    
    @Override
    public boolean isAvailable() {
        return true;
    }
    
    /**
     * Processes all files in a repository to extract workflow data.
     * 
     * @param files List of files to process
     * @param repositoryName Name of the repository
     */
    private void processRepository(List<File> files, String repositoryName) {
        // First pass: build class hierarchy and cache class locations
        for (File file : files) {
            try {
                String filePath = file.getAbsolutePath();
                
                // Parse the file to extract basic structure
                Map<String, Object> structure = javaParserService.parseJavaFile(filePath);
                
                if (structure.containsKey("package") && structure.containsKey("className")) {
                    String packageName = (String) structure.get("package");
                    String className = (String) structure.get("className");
                    String fullyQualifiedName = packageName + "." + className;
                    
                    // Cache class to file mapping
                    classToFileCache.put(fullyQualifiedName, filePath);
                    
                    // Cache class to repository mapping
                    classToRepoCache.put(fullyQualifiedName, repositoryName);
                    
                    // Cache class hierarchy information
                    if (structure.containsKey("hierarchy")) {
                        Set<String> hierarchy = new HashSet<>((List<String>) structure.get("hierarchy"));
                        classHierarchyCache.put(fullyQualifiedName, hierarchy);
                    }
                    
                    // Determine class type (class, interface, abstract) if available
                    if (structure.containsKey("type")) {
                        String classType = (String) structure.get("type");
                        classTypes.put(fullyQualifiedName, classType);
                    }
                }
            } catch (Exception e) {
                logger.debug("Error parsing file structure in first pass: {}", e.getMessage());
            }
        }
        
        // Process files in batches for better performance if parallel processing is enabled
        if (parallelProcessing && files.size() > batchSize) {
            processBatchedFiles(files, repositoryName);
        } else {
            // Process files sequentially
            processFilesSequentially(files, repositoryName);
        }
        
        // Track interface implementations if enabled
        if (trackInterfaceImplementations) {
            trackInterfaceImplementations(repositoryName);
        }
        
        // Detect design patterns if enabled
        if (detectPatterns) {
            detectDesignPatterns(repositoryName);
        }
        
        // Detect method chains to identify common sequences
        detectMethodChains();
        
        // Save workflow data if persistence is enabled
        if (persistenceEnabled) {
            saveWorkflowData();
        }
    }
    
    /**
     * Processes files in batches for better performance.
     * 
     * @param files List of files to process
     * @param repositoryName Repository name
     */
    private void processBatchedFiles(List<File> files, String repositoryName) {
        logger.info("Processing {} files in {} batches of size {}", 
                  files.size(), (files.size() + batchSize - 1) / batchSize, batchSize);
        
        List<List<File>> batches = new ArrayList<>();
        for (int i = 0; i < files.size(); i += batchSize) {
            batches.add(files.subList(i, Math.min(i + batchSize, files.size())));
        }
        
        // Process each batch in parallel if threads > 1
        if (threadCount > 1) {
            batches.parallelStream().forEach(batch -> {
                processFilesSequentially(batch, repositoryName);
                logger.debug("Processed batch of {} files in parallel", batch.size());
            });
        } else {
            // Sequential processing when threads == 1
            batches.forEach(batch -> {
                processFilesSequentially(batch, repositoryName);
                logger.debug("Processed batch of {} files sequentially", batch.size());
            });
        }
    }
    
    /**
     * Processes files sequentially.
     * 
     * @param files List of files to process
     * @param repositoryName Repository name
     */
    private void processFilesSequentially(List<File> files, String repositoryName) {
        for (File file : files) {
            try {
                // Only process Java files
                if (!file.getName().endsWith(".java")) {
                    continue;
                }
                
                String sourceFilePath = file.getAbsolutePath();
                
                // Parse the file to extract method calls
                Map<String, Object> parsedData = javaParserService.parseJavaFile(sourceFilePath);
                if (parsedData == null) {
                    continue;
                }
                
                // Process class hierarchy if present
                String className = (String) parsedData.get("className");
                String packageName = (String) parsedData.get("package");
                if (className != null) {
                    String fullClassName = packageName != null ? packageName + "." + className : className;
                    processClassHierarchy(parsedData, fullClassName, packageName, repositoryName);
                    
                    // Store class in repo mapping for cross-repo lookups
                    classToRepoCache.put(fullClassName, repositoryName);
                    classToFileCache.put(fullClassName, sourceFilePath);
                }
                
                // Process method calls into workflow steps
                processMethodCallsToWorkflowSteps(parsedData, sourceFilePath, repositoryName);
                
            } catch (Exception e) {
                logger.error("Error analyzing method calls in file {}: {}", file.getAbsolutePath(), e.getMessage());
                // Add error tracking
                analysisErrors.computeIfAbsent(repositoryName, k -> new ArrayList<>())
                    .add(new AnalysisError(file.getAbsolutePath(), e.getMessage()));
            }
        }
    }
    
    /**
     * Processes class hierarchy information from parsed file data.
     * 
     * @param parsedData The parsed file data
     * @param fullClassName The fully qualified class name
     * @param packageName The package name
     * @param repositoryName The repository name
     */
    private void processClassHierarchy(Map<String, Object> parsedData, String fullClassName, 
                                     String packageName, String repositoryName) {
        // Extract class hierarchy for inheritance-based calls
        if (parsedData.containsKey("hierarchy")) {
            Object hierarchyObj = parsedData.get("hierarchy");
            Set<String> hierarchySet = new HashSet<>();
            hierarchySet.add(fullClassName); // Add self
            
            // Handle hierarchy as Map
            if (hierarchyObj instanceof Map) {
                Map<String, Object> hierarchy = (Map<String, Object>) hierarchyObj;
                
                if (hierarchy.containsKey("extends")) {
                    String parentClass = (String) hierarchy.get("extends");
                    String resolvedParent = resolveClassName(parentClass, 
                        (List<String>) parsedData.get("imports"), packageName);
                    if (resolvedParent != null) {
                        hierarchySet.add(resolvedParent);
                    }
                }
                
                if (hierarchy.containsKey("implements")) {
                    Object implementsObj = hierarchy.get("implements");
                    List<String> interfaces = new ArrayList<>();
                    
                    // Handle implements as List or single String
                    if (implementsObj instanceof List) {
                        interfaces = (List<String>) implementsObj;
                    } else if (implementsObj instanceof String) {
                        interfaces.add((String) implementsObj);
                    }
                    
                    for (String iface : interfaces) {
                        String resolvedInterface = resolveClassName(iface, 
                            (List<String>) parsedData.get("imports"), packageName);
                        if (resolvedInterface != null) {
                            hierarchySet.add(resolvedInterface);
                            
                            // Register that this interface is implemented by this class
                            interfaceImplementations
                                .computeIfAbsent(resolvedInterface, k -> new HashSet<>())
                                .add(fullClassName);
                        }
                    }
                }
            }
            
            classHierarchyCache.put(fullClassName, hierarchySet);
            
            // Track class/interface type
            String classType = "class"; // Default
            if (parsedData.containsKey("type")) {
                classType = (String) parsedData.get("type");
            }
            classTypes.put(fullClassName, classType);
            
            // Maintain bidirectional relationship between interfaces and implementations
            if ("interface".equals(classType)) {
                // This is an interface - add it to the map
                interfaceImplementations.computeIfAbsent(fullClassName, k -> new HashSet<>());
            } else {
                // This is a class - check if it implements any interfaces
                for (String parent : hierarchySet) {
                    // Use containsKey/get pattern to avoid type mismatch
                    String parentType = classTypes.containsKey(parent) ? 
                        classTypes.get(parent) : "class";
                    
                    if ("interface".equals(parentType)) {
                        interfaceImplementations
                            .computeIfAbsent(parent, k -> new HashSet<>())
                            .add(fullClassName);
                        
                        // Track this as a framework-based component if it matches known patterns
                        if (parent.contains("Repository") || parent.contains("DAO") || 
                            parent.contains("Service") || parent.contains("Controller")) {
                            patternDetectionCache.put(fullClassName, "framework-component:" + parent);
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Resolves cross-repository references by reprocessing workflow steps.
     */
    private void resolveCrossRepositoryReferences() {
        logger.debug("Resolving cross-repository references");
        
        // Iterate through all workflow steps
        for (Map<String, List<WorkflowStep>> repoWorkflows : workflowByFileByRepoCache.values()) {
            for (List<WorkflowStep> steps : repoWorkflows.values()) {
                for (WorkflowStep step : steps) {
                    // Skip steps that already have resolved target files
                    if (step.getTargetFile() != null) {
                        continue;
                    }
                    
                    // Look for matching class in other repositories
                    String targetMethod = step.getTargetMethod();
                    if (targetMethod != null && targetMethod.contains(".")) {
                        try {
                            // Extract potential class name from method reference
                            String className = targetMethod.substring(0, targetMethod.lastIndexOf("."));
                            String methodName = targetMethod.substring(targetMethod.lastIndexOf(".") + 1);
                            
                            // Search in classToFileCache for this class
                            for (Map.Entry<String, String> entry : classToFileCache.entrySet()) {
                                String fullyQualifiedName = entry.getKey();
                                
                                if (fullyQualifiedName.endsWith("." + className)) {
                                    // Found a potential match
                                    String targetFile = entry.getValue();
                                    String targetRepo = classToRepoCache.get(fullyQualifiedName);
                                    
                                    step.setTargetFile(targetFile);
                                    step.setTargetMethod(methodName);
                                    step.setTargetRepository(targetRepo);
                                    step.setCrossRepository(!step.getSourceRepository().equals(targetRepo));
                                    
                                    logger.debug("Resolved cross-repo reference: {} -> {}.{}", 
                                            className, targetRepo, methodName);
                                    break;
                                }
                            }
                        } catch (Exception e) {
                            // Ignore errors in resolution phase
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Resolves a class name from an object reference using imports and package context.
     * 
     * @param objectReference The object reference to resolve
     * @param imports List of imports from the file
     * @param currentPackage Current package of the file
     * @return Fully qualified class name or null if not resolved
     */
    private String resolveClassName(String objectReference, List<String> imports, String currentPackage) {
        if (objectReference == null) return null;
        
        // Check if it's already a fully qualified name
        if (objectReference.contains(".") && classToFileCache.containsKey(objectReference)) {
            return objectReference;
        }
        
        // Try direct import match
        for (String importPath : imports) {
            if (importPath.endsWith("." + objectReference)) {
                return importPath;
            }
        }
        
        // Try wildcard imports if there are any
        for (String importPath : imports) {
            if (importPath.endsWith(".*")) {
                String basePackage = importPath.substring(0, importPath.length() - 2);
                String fullyQualified = basePackage + "." + objectReference;
                if (classToFileCache.containsKey(fullyQualified)) {
                    return fullyQualified;
                }
            }
        }
        
        // Try same package
        String samePackage = currentPackage + "." + objectReference;
        if (classToFileCache.containsKey(samePackage)) {
            return samePackage;
        }
        
        // Try java.lang package for common classes
        String javaLang = "java.lang." + objectReference;
        if (classToFileCache.containsKey(javaLang)) {
            return javaLang;
        }
        
        // Not resolved, assume it's in the same package
        return currentPackage + "." + objectReference;
    }
    
    /**
     * Enhanced class name resolution that handles more complex cases.
     */
    private String enhancedResolveClassName(String objectReference, List<String> imports, 
                                          String currentPackage, Map<String, Object> context) {
        // Try existing method first
        String resolved = resolveClassName(objectReference, imports, currentPackage);
        if (resolved != null && classToFileCache.containsKey(resolved)) {
            return resolved;
        }
        
        // Handle special cases
        if (objectReference == null) return null;
        
        // Check for generic type parameters
        if (objectReference.contains("<")) {
            String baseType = objectReference.substring(0, objectReference.indexOf("<"));
            return enhancedResolveClassName(baseType, imports, currentPackage, context);
        }
        
        // Handle variable references by checking context
        if (Character.isLowerCase(objectReference.charAt(0)) && context != null) {
            // Check if we have type info for this variable
            if (context.containsKey("variables") && 
                ((Map<String, String>)context.get("variables")).containsKey(objectReference)) {
                String variableType = ((Map<String, String>)context.get("variables")).get(objectReference);
                return resolveClassName(variableType, imports, currentPackage);
            }
            
            // Check if it's a field reference
            if (context.containsKey("fields") && 
                ((Map<String, String>)context.get("fields")).containsKey(objectReference)) {
                String fieldType = ((Map<String, String>)context.get("fields")).get(objectReference);
                return resolveClassName(fieldType, imports, currentPackage);
            }
        }
        
        // Handle array types
        if (objectReference.endsWith("[]")) {
            String componentType = objectReference.substring(0, objectReference.length() - 2);
            return enhancedResolveClassName(componentType, imports, currentPackage, context);
        }
        
        // Try interfaces and base implementation search as a last resort
        for (String iface : interfaceImplementations.keySet()) {
            String ifaceSimpleName = iface.substring(iface.lastIndexOf(".") + 1);
            if (ifaceSimpleName.equals(objectReference)) {
                // Check if there's exactly one implementation we can use
                Set<String> impls = interfaceImplementations.get(iface);
                if (impls.size() == 1) {
                    return impls.iterator().next();
                }
            }
        }
        
        return resolved;
    }
    
    /**
     * Improved cross-repository reference resolution.
     * This handles more complex cases of references between repositories.
     */
    private void enhancedCrossRepositoryResolution() {
        logger.info("Performing enhanced cross-repository resolution");
        
        // Try to resolve unresolved target files
        for (Map<String, List<WorkflowStep>> repoWorkflows : workflowByFileByRepoCache.values()) {
            for (List<WorkflowStep> steps : repoWorkflows.values()) {
                for (WorkflowStep step : steps) {
                    // Skip steps that already have resolved target files
                    if (step.getTargetFile() != null) {
                        continue;
                    }
                    
                    // Try interface-based resolution
                    resolveStepViaInterface(step);
                    
                    // Try to resolve via method signature
                    if (step.getTargetFile() == null) {
                        resolveStepViaMethodSignature(step);
                    }
                    
                    // Try framework component resolution
                    if (step.getTargetFile() == null) {
                        resolveStepViaFrameworkComponents(step);
                    }
                    
                    // Set confidence based on resolution method
                    if (step.getTargetFile() != null) {
                        if (step.getConfidence() == 1.0) {
                            // Already has high confidence from a direct match
                            continue;
                        }
                        
                        // Set confidence based on resolution method
                        if (step.getPatternType() != null) {
                            step.setConfidence(0.8); // Pattern-based resolution
                        } else {
                            step.setConfidence(0.6); // Indirect resolution
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Resolves a step using interface hierarchy.
     */
    private void resolveStepViaInterface(WorkflowStep step) {
        String targetMethod = step.getTargetMethod();
        
        // Only process if we have interface implementations data
        if (interfaceImplementations.isEmpty()) {
            return;
        }
        
        // Check if the target is an interface method
        for (Map.Entry<String, Set<String>> entry : interfaceImplementations.entrySet()) {
            String interfaceName = entry.getKey();
            String simpleInterfaceName = interfaceName.substring(interfaceName.lastIndexOf(".") + 1);
            
            // Check if method is targeting this interface
            if (targetMethod.contains(simpleInterfaceName + ".")) {
                String methodName = targetMethod.substring(targetMethod.lastIndexOf(".") + 1);
                
                // Try each implementation to find the method
                for (String implClass : entry.getValue()) {
                    String implFile = classToFileCache.get(implClass);
                    if (implFile != null) {
                        // Found an implementation, check if it has the method
                        try {
                            Map<String, Object> parsed = javaParserService.parseJavaFile(implFile);
                            if (parsed.containsKey("methods")) {
                                List<Map<String, Object>> methods = (List<Map<String, Object>>) parsed.get("methods");
                                for (Map<String, Object> method : methods) {
                                    if (methodName.equals(method.get("name"))) {
                                        // Found matching method in implementation
                                        step.setTargetFile(implFile);
                                        step.setTargetMethod(methodName);
                                        step.setTargetRepository(classToRepoCache.get(implClass));
                                        step.setCrossRepository(!step.getSourceRepository().equals(step.getTargetRepository()));
                                        step.setPatternType("interface-implementation");
                                        return;
                                    }
                                }
                            }
                        } catch (Exception e) {
                            logger.debug("Error checking implementation method: {}", e.getMessage());
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Resolves a step using method signature matching.
     */
    private void resolveStepViaMethodSignature(WorkflowStep step) {
        String targetMethod = step.getTargetMethod();
        
        // Only process if we have a sufficiently specific target method
        if (targetMethod == null || !targetMethod.contains(".")) {
            return;
        }
        
        String methodName = targetMethod.substring(targetMethod.lastIndexOf(".") + 1);
        
        // Try to find matching method across all files
        for (String className : classToFileCache.keySet()) {
            String filePath = classToFileCache.get(className);
            
            try {
                Map<String, Object> parsed = javaParserService.parseJavaFile(filePath);
                if (parsed.containsKey("methods")) {
                    List<Map<String, Object>> methods = (List<Map<String, Object>>) parsed.get("methods");
                    for (Map<String, Object> method : methods) {
                        if (methodName.equals(method.get("name"))) {
                            // Found a matching method name
                            
                            // If we have parameters, check if they match too
                            if (step.getDataParameters() != null && method.containsKey("parameters")) {
                                List<String> methodParams = (List<String>) method.get("parameters");
                                if (methodParams.size() == step.getDataParameters().size()) {
                                    // Sufficient match based on method name and parameter count
                                    step.setTargetFile(filePath);
                                    step.setTargetMethod(methodName);
                                    step.setTargetRepository(classToRepoCache.get(className));
                                    step.setCrossRepository(!step.getSourceRepository().equals(step.getTargetRepository()));
                                    step.setPatternType("signature-match");
                                    return;
                                }
                            } else {
                                // Match based on method name only (lower confidence)
                                if (step.getTargetFile() == null) {
                                    // Only set if we haven't found a better match
                                    step.setTargetFile(filePath);
                                    step.setTargetMethod(methodName);
                                    step.setTargetRepository(classToRepoCache.get(className));
                                    step.setCrossRepository(!step.getSourceRepository().equals(step.getTargetRepository()));
                                    step.setConfidence(0.5); // Lower confidence for name-only match
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                logger.debug("Error checking method signature: {}", e.getMessage());
            }
        }
    }
    
    /**
     * Resolves a step using framework component detection.
     */
    private void resolveStepViaFrameworkComponents(WorkflowStep step) {
        String targetMethod = step.getTargetMethod();
        
        // Only process if we have potentially a framework component reference
        if (targetMethod == null) {
            return;
        }
        
        // Look for common Spring component patterns
        if (targetMethod.endsWith("Service.") || targetMethod.endsWith("Repository.") || 
            targetMethod.endsWith("Controller.") || targetMethod.endsWith("Component.")) {
            
            // Try to find a component with this name
            String componentName = targetMethod.substring(0, targetMethod.length() - 1);
            
            for (String className : classToFileCache.keySet()) {
                if (className.endsWith(componentName)) {
                    String filePath = classToFileCache.get(className);
                    
                    try {
                        Map<String, Object> parsed = javaParserService.parseJavaFile(filePath);
                        
                        // Check for Spring component annotations
                        boolean isSpringComponent = false;
                        if (parsed.containsKey("annotations")) {
                            List<String> annotations = (List<String>) parsed.get("annotations");
                            for (String annotation : annotations) {
                                if (annotation.contains("Service") || annotation.contains("Repository") || 
                                    annotation.contains("Controller") || annotation.contains("Component")) {
                                    isSpringComponent = true;
                                    break;
                                }
                            }
                        }
                        
                        if (isSpringComponent) {
                            // Found a matching Spring component
                            step.setTargetFile(filePath);
                            // Use a generic method name since the actual method wasn't specified
                            step.setTargetMethod("getInstance");
                            step.setTargetRepository(classToRepoCache.get(className));
                            step.setCrossRepository(!step.getSourceRepository().equals(step.getTargetRepository()));
                            step.setPatternType("spring-component");
                            step.setConfidence(0.7); // Medium confidence for framework component match
                            return;
                        }
                    } catch (Exception e) {
                        logger.debug("Error checking for Spring component: {}", e.getMessage());
                    }
                }
            }
        }
    }
    
    /**
     * Finds potential entry points in the codebase.
     * 
     * @return List of methods that could be entry points
     */
    private List<String> findEntryPoints() {
        List<String> entryPoints = new ArrayList<>();
        
        for (Map<String, List<WorkflowStep>> repoWorkflows : workflowByFileByRepoCache.values()) {
            for (String filePath : repoWorkflows.keySet()) {
                try {
                    String content = new String(Files.readAllBytes(Paths.get(filePath)));
                    
                    // Check for entry point patterns
                    if (content.contains("public static void main(") ||
                        content.contains("@RestController") ||
                        content.contains("@Controller") ||
                        content.contains("@RequestMapping") ||
                        content.contains("@GetMapping") ||
                        content.contains("@PostMapping")) {
                        
                        Map<String, Object> structure = javaParserService.parseJavaFile(filePath);
                        String packageName = (String) structure.getOrDefault("package", "");
                        String className = (String) structure.getOrDefault("className", "");
                        
                        // Find entry point methods
                        if (structure.containsKey("methods")) {
                            List<Map<String, Object>> methods = (List<Map<String, Object>>) structure.get("methods");
                            
                            for (Map<String, Object> method : methods) {
                                String methodName = (String) method.get("name");
                                String visibility = (String) method.get("visibility");
                                
                                if ("public".equals(visibility)) {
                                    if ("main".equals(methodName) || 
                                        content.contains("@RequestMapping") ||
                                        content.contains("@GetMapping") ||
                                        content.contains("@PostMapping")) {
                                        
                                        String entryPoint = packageName + "." + className + "." + methodName;
                                        entryPoints.add(entryPoint);
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    logger.warn("Error finding entry points in {}: {}", filePath, e.getMessage());
                }
            }
        }
        
        return entryPoints;
    }
    
    /**
     * Extracts repository name from a file path.
     * 
     * @param path Path to analyze
     * @return Repository name or null if not determinable
     */
    private String extractRepositoryName(String path) {
        if (path == null) {
            return null;
        }
        
        // Handle paths like "./data/code/repoName" or "/full/path/data/code/repoName"
        if (path.contains("/data/code/")) {
            String[] parts = path.split("/data/code/");
            if (parts.length > 1) {
                String remainder = parts[1];
                if (remainder.contains("/")) {
                    return remainder.substring(0, remainder.indexOf("/"));
                }
                return remainder;
            }
        }
        
        // Handle direct repository name
        File direct = new File("./data/code/" + path);
        if (direct.exists() && direct.isDirectory()) {
            return path;
        }
        
        return null;
    }
    
    /**
     * Collects Java files for analysis.
     * 
     * @param path Path to search
     * @param recursive Whether to search recursively
     * @return List of Java files
     */
    private List<File> collectFilesForAnalysis(String path, boolean recursive) {
        List<File> files = new ArrayList<>();
        
        // Handle direct path
        File rootDir = new File(path);
        if (rootDir.exists()) {
            collectJavaFiles(rootDir, files, recursive);
        } else {
            // Try as repository name
            File repoDir = new File("./data/code/" + path);
            if (repoDir.exists()) {
                collectJavaFiles(repoDir, files, recursive);
            }
        }
        
        return files;
    }
    
    /**
     * Recursively collects Java files.
     * 
     * @param directory Directory to search
     * @param files List to collect files in
     * @param recursive Whether to search recursively
     */
    private void collectJavaFiles(File directory, List<File> files, boolean recursive) {
        File[] fileList = directory.listFiles();
        if (fileList == null) {
            return;
        }
        
        for (File file : fileList) {
            if (file.isDirectory()) {
                if (recursive) {
                    collectJavaFiles(file, files, true);
                }
            } else if (file.getName().endsWith(".java")) {
                files.add(file);
            }
        }
    }
    
    /**
     * Finds other repositories in the data/code directory.
     * 
     * @return List of repository names
     */
    private List<String> findOtherRepositories() {
        List<String> repos = new ArrayList<>();
        File codeDir = new File("./data/code");
        
        if (codeDir.exists() && codeDir.isDirectory()) {
            File[] directories = codeDir.listFiles(File::isDirectory);
            if (directories != null) {
                for (File dir : directories) {
                    String name = dir.getName();
                    // Skip the data folder as specified
                    if (!name.equals("data")) {
                        repos.add(name);
                    }
                }
            }
        }
        
        return repos;
    }
    
    /**
     * Clears caches for a specific repository only.
     * 
     * @param repositoryName The repository to clear caches for
     */
    private void clearCaches(String repositoryName) {
        // Remove the specific repository's workflow data
        workflowByFileByRepoCache.remove(repositoryName);
        
        // Only clear out entries from other caches related to this repository
        // Create a list of class names to remove
        List<String> classesToRemove = new ArrayList<>();
        for (Map.Entry<String, String> entry : classToRepoCache.entrySet()) {
            if (entry.getValue().equals(repositoryName)) {
                classesToRemove.add(entry.getKey());
            }
        }
        
        // Remove the entries
        for (String className : classesToRemove) {
            classHierarchyCache.remove(className);
            String filePath = classToFileCache.remove(className);
            classToRepoCache.remove(className);
        }
        
        // Remove the repository from processed repositories
        processedRepositories.remove(repositoryName);
    }
    
    /**
     * Clear all caches across all repositories.
     * This is maintained for compatibility but generally shouldn't be used.
     */
    private void clearCaches() {
        workflowByFileByRepoCache.clear();
        classHierarchyCache.clear();
        classToFileCache.clear();
        classToRepoCache.clear();
        processedRepositories.clear();
    }
    
    /**
     * Save workflow data to disk for persistence.
     * Stores each repository's workflows separately.
     */
    private void saveWorkflowData() {
        if (!persistenceEnabled) {
            logger.debug("Workflow persistence is disabled, skipping save");
            return;
        }
        
        try {
            // Create workflow data directory if it doesn't exist
            File workflowDir = new File(workflowDataDir);
            if (!workflowDir.exists()) {
                if (workflowDir.mkdirs()) {
                    logger.info("Created workflow data directory: {}", workflowDataDir);
                } else {
                    logger.error("Failed to create workflow data directory: {}", workflowDataDir);
                    return;
                }
            }
            
            // Save repository metadata
            Properties repoMetadata = new Properties();
            repoMetadata.setProperty("processed.count", String.valueOf(processedRepositories.size()));
            int repoIndex = 0;
            for (String repo : processedRepositories) {
                repoMetadata.setProperty("processed." + repoIndex, repo);
                repoIndex++;
            }
            
            // Add version information for backward compatibility
            repoMetadata.setProperty("version", "1.0");
            repoMetadata.setProperty("timestamp", String.valueOf(System.currentTimeMillis()));
            repoMetadata.setProperty("java.version", System.getProperty("java.version"));
            
            try (FileOutputStream fos = new FileOutputStream(new File(workflowDir, "repositories.properties"))) {
                repoMetadata.store(fos, "Repository metadata for workflow analysis");
            }
            
            // Save each repository's workflow data separately
            for (String repo : workflowByFileByRepoCache.keySet()) {
                File repoDir = new File(workflowDir, repo);
                if (!repoDir.exists()) {
                    repoDir.mkdirs();
                }
                
                Map<String, List<WorkflowStep>> repoWorkflows = workflowByFileByRepoCache.get(repo);
                
                // Save workflow steps
                try (ObjectOutputStream oos = new ObjectOutputStream(
                        new FileOutputStream(new File(repoDir, "workflow_steps.dat")))) {
                    oos.writeObject(repoWorkflows);
                }
                
                // Save interface implementations if enabled
                if (trackInterfaceImplementations) {
                    Properties interfaceProps = new Properties();
                    for (Map.Entry<String, Set<String>> entry : interfaceImplementations.entrySet()) {
                        String interfaceName = entry.getKey();
                        Set<String> implementations = entry.getValue();
                        
                        int implIndex = 0;
                        interfaceProps.setProperty(interfaceName + ".count", String.valueOf(implementations.size()));
                        for (String impl : implementations) {
                            interfaceProps.setProperty(interfaceName + "." + implIndex, impl);
                            implIndex++;
                        }
                    }
                    
                    try (FileOutputStream fos = new FileOutputStream(new File(repoDir, "interfaces.properties"))) {
                        interfaceProps.store(fos, "Interface implementation mappings");
                    }
                }
                
                // Save design pattern detections if enabled
                if (detectPatterns) {
                    Properties patternProps = new Properties();
                    int patternCount = 0;
                    for (Map.Entry<String, String> entry : patternDetectionCache.entrySet()) {
                        if (entry.getKey().startsWith(repo + ":")) {
                            String key = entry.getKey().substring(repo.length() + 1);
                            patternProps.setProperty(key, entry.getValue());
                            patternCount++;
                        }
                    }
                    
                    if (patternCount > 0) {
                        try (FileOutputStream fos = new FileOutputStream(new File(repoDir, "patterns.properties"))) {
                            patternProps.store(fos, "Design pattern detections");
                        }
                    }
                }
            }
            
            logger.info("Saved workflow data for {} repositories to {}", 
                      workflowByFileByRepoCache.size(), workflowDataDir);
            
        } catch (Exception e) {
            logger.error("Error saving workflow data: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Load workflow data from disk.
     * Loads each repository's workflows separately.
     */
    private void loadWorkflowData() {
        if (!persistenceEnabled) {
            logger.debug("Workflow persistence is disabled, skipping load");
            return;
        }
        
        try {
            File workflowDir = new File(workflowDataDir);
            if (!workflowDir.exists() || !workflowDir.isDirectory()) {
                logger.info("Workflow data directory does not exist: {}", workflowDataDir);
                return;
            }
            
            // Load repository metadata
            File repoMetadataFile = new File(workflowDir, "repositories.properties");
            if (repoMetadataFile.exists()) {
                Properties repoMetadata = new Properties();
                try (FileInputStream fis = new FileInputStream(repoMetadataFile)) {
                    repoMetadata.load(fis);
                }
                
                String countStr = repoMetadata.getProperty("processed.count", "0");
                int repoCount = Integer.parseInt(countStr);
                
                for (int i = 0; i < repoCount; i++) {
                    String repo = repoMetadata.getProperty("processed." + i);
                    if (repo != null && !repo.isEmpty()) {
                        processedRepositories.add(repo);
                        
                        // Load this repository's workflow data
                        File repoDir = new File(workflowDir, repo);
                        if (repoDir.exists() && repoDir.isDirectory()) {
                            loadRepositoryWorkflowData(repo, repoDir);
                        }
                    }
                }
                
                logger.info("Loaded workflow data for {} repositories from {}", 
                          processedRepositories.size(), workflowDataDir);
            }
        } catch (Exception e) {
            logger.error("Error loading workflow data: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Load workflow data for a specific repository.
     * 
     * @param repo Repository name
     * @param repoDir Directory containing repository workflow data
     */
    @SuppressWarnings("unchecked")
    private void loadRepositoryWorkflowData(String repo, File repoDir) {
        try {
            // Load workflow steps
            File workflowStepsFile = new File(repoDir, "workflow_steps.dat");
            if (workflowStepsFile.exists()) {
                try (ObjectInputStream ois = new ObjectInputStream(
                        new FileInputStream(workflowStepsFile))) {
                    Map<String, List<WorkflowStep>> repoWorkflows = 
                            (Map<String, List<WorkflowStep>>) ois.readObject();
                    workflowByFileByRepoCache.put(repo, repoWorkflows);
                }
            }
            
            // Load interface implementations if tracking is enabled
            if (trackInterfaceImplementations) {
                File interfacesFile = new File(repoDir, "interfaces.properties");
                if (interfacesFile.exists()) {
                    Properties interfaceProps = new Properties();
                    try (FileInputStream fis = new FileInputStream(interfacesFile)) {
                        interfaceProps.load(fis);
                    }
                    
                    for (String key : interfaceProps.stringPropertyNames()) {
                        if (key.endsWith(".count")) {
                            String interfaceName = key.substring(0, key.length() - 6);
                            int implCount = Integer.parseInt(interfaceProps.getProperty(key, "0"));
                            
                            Set<String> implementations = new HashSet<>();
                            for (int i = 0; i < implCount; i++) {
                                String impl = interfaceProps.getProperty(interfaceName + "." + i);
                                if (impl != null) {
                                    implementations.add(impl);
                                }
                            }
                            
                            if (!implementations.isEmpty()) {
                                interfaceImplementations.put(interfaceName, implementations);
                            }
                        }
                    }
                }
            }
            
            // Load design pattern detections if enabled
            if (detectPatterns) {
                File patternsFile = new File(repoDir, "patterns.properties");
                if (patternsFile.exists()) {
                    Properties patternProps = new Properties();
                    try (FileInputStream fis = new FileInputStream(patternsFile)) {
                        patternProps.load(fis);
                    }
                    
                    for (String key : patternProps.stringPropertyNames()) {
                        String value = patternProps.getProperty(key);
                        patternDetectionCache.put(repo + ":" + key, value);
                    }
                }
            }
            
            logger.info("Loaded workflow data for repository: {}", repo);
            
        } catch (Exception e) {
            logger.error("Error loading workflow data for repository {}: {}", 
                       repo, e.getMessage(), e);
        }
    }
    
    /**
     * Detects design patterns in the codebase.
     * Currently detects Factory, Builder, and Dependency Injection patterns.
     * 
     * @param repositoryName The repository being analyzed
     */
    private void detectDesignPatterns(String repositoryName) {
        if (!detectPatterns) {
            return;
        }
        
        logger.info("Detecting design patterns in repository: {}", repositoryName);
        
        // Factory pattern detection
        detectFactoryPattern(repositoryName);
        
        // Builder pattern detection
        detectBuilderPattern(repositoryName);
        
        // Dependency Injection pattern detection
        detectDependencyInjectionPattern(repositoryName);
    }
    
    /**
     * Detects Factory pattern implementations.
     * Looks for classes with "Factory" in the name that create other objects.
     * 
     * @param repositoryName The repository being analyzed
     */
    private void detectFactoryPattern(String repositoryName) {
        // Find potential factory classes
        Set<String> factoryClasses = new HashSet<>();
        
        for (String className : classToFileCache.keySet()) {
            if (classToRepoCache.getOrDefault(className, "").equals(repositoryName)) {
                if (className.contains("Factory")) {
                    factoryClasses.add(className);
                }
            }
        }
        
        if (factoryClasses.isEmpty()) {
            return;
        }
        
        // Analyze each factory class
        for (String factoryClass : factoryClasses) {
            String filePath = classToFileCache.get(factoryClass);
            
            try {
                Map<String, Object> structure = javaParserService.parseJavaFile(filePath);
                
                if (structure.containsKey("methods")) {
                    List<Map<String, Object>> methods = (List<Map<String, Object>>) structure.get("methods");
                    
                    for (Map<String, Object> method : methods) {
                        String methodName = (String) method.get("name");
                        String returnType = (String) method.getOrDefault("returnType", "void");
                        
                        // Factory methods typically start with create/new/get and return an object
                        if ((methodName.startsWith("create") || 
                             methodName.startsWith("new") || 
                             methodName.startsWith("get")) && 
                            !returnType.equals("void") && 
                            !isPrimitiveType(returnType)) {
                            
                            // Record this as a factory pattern
                            String patternKey = repositoryName + ":" + factoryClass + "." + methodName;
                            String patternValue = "factory:" + returnType;
                            patternDetectionCache.put(patternKey, patternValue);
                            
                            logger.debug("Detected Factory pattern: {} creates {}", 
                                      factoryClass + "." + methodName, returnType);
                            
                            // Create implicit workflow steps for this factory pattern
                            if (workflowByFileByRepoCache.containsKey(repositoryName)) {
                                List<String> clients = findFactoryClients(factoryClass, methodName, repositoryName);
                                for (String client : clients) {
                                    createImplicitWorkflowStep(client, factoryClass, methodName, returnType, 
                                                            repositoryName, "factory");
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                logger.debug("Error analyzing factory pattern in {}: {}", filePath, e.getMessage());
            }
        }
    }
    
    /**
     * Detects Builder pattern implementations.
     * Looks for classes with "Builder" in the name or that have fluent setter methods.
     * 
     * @param repositoryName The repository being analyzed
     */
    private void detectBuilderPattern(String repositoryName) {
        // Find potential builder classes
        Set<String> builderClasses = new HashSet<>();
        
        for (String className : classToFileCache.keySet()) {
            if (classToRepoCache.getOrDefault(className, "").equals(repositoryName)) {
                if (className.contains("Builder")) {
                    builderClasses.add(className);
                }
            }
        }
        
        // Analyze each builder class
        for (String builderClass : builderClasses) {
            String filePath = classToFileCache.get(builderClass);
            
            try {
                Map<String, Object> structure = javaParserService.parseJavaFile(filePath);
                
                if (structure.containsKey("methods")) {
                    List<Map<String, Object>> methods = (List<Map<String, Object>>) structure.get("methods");
                    
                    boolean hasFluentSetters = false;
                    boolean hasBuildMethod = false;
                    String builtType = null;
                    
                    for (Map<String, Object> method : methods) {
                        String methodName = (String) method.get("name");
                        String returnType = (String) method.getOrDefault("returnType", "void");
                        
                        // Builder methods typically return the builder itself (fluent interface)
                        if (!methodName.equals("build") && 
                            returnType.contains(builderClass.substring(builderClass.lastIndexOf(".") + 1))) {
                            hasFluentSetters = true;
                        }
                        
                        // Find the build method
                        if (methodName.equals("build") && !returnType.equals("void")) {
                            hasBuildMethod = true;
                            builtType = returnType;
                        }
                    }
                    
                    if (hasFluentSetters && hasBuildMethod && builtType != null) {
                        // Record this as a builder pattern
                        String patternKey = repositoryName + ":" + builderClass;
                        String patternValue = "builder:" + builtType;
                        patternDetectionCache.put(patternKey, patternValue);
                        
                        logger.debug("Detected Builder pattern: {} builds {}", builderClass, builtType);
                    }
                }
            } catch (Exception e) {
                logger.debug("Error analyzing builder pattern in {}: {}", filePath, e.getMessage());
            }
        }
    }
    
    /**
     * Detects Dependency Injection pattern implementations.
     * Looks for Spring annotations like @Autowired, @Inject.
     * 
     * @param repositoryName The repository being analyzed
     */
    private void detectDependencyInjectionPattern(String repositoryName) {
        for (String className : classToFileCache.keySet()) {
            if (classToRepoCache.getOrDefault(className, "").equals(repositoryName)) {
                String filePath = classToFileCache.get(className);
                
                try {
                    String content = new String(Files.readAllBytes(Paths.get(filePath)));
                    
                    // Look for DI annotations (Spring, Jakarta EE, CDI)
                    if (content.contains("@Autowired") || content.contains("@Inject") || 
                        content.contains("@Resource") || content.contains("@EJB") ||
                        content.contains("@PersistenceContext") || content.contains("@PersistenceUnit") ||
                        content.contains("@ManagedProperty") || content.contains("@Value")) {
                        
                        Map<String, Object> structure = javaParserService.parseJavaFile(filePath);
                        
                        // Extract injectable fields
                        if (structure.containsKey("fields")) {
                            List<Map<String, Object>> fields = (List<Map<String, Object>>) structure.get("fields");
                            
                            for (Map<String, Object> field : fields) {
                                if (field.containsKey("annotations")) {
                                    List<String> annotations = (List<String>) field.get("annotations");
                                    
                                    if (annotations.contains("Autowired") || 
                                        annotations.contains("Inject") || 
                                        annotations.contains("Resource")) {
                                        
                                        String fieldType = (String) field.get("type");
                                        String fieldName = (String) field.get("name");
                                        
                                        // Record this as a DI pattern
                                        String patternKey = repositoryName + ":" + className + "." + fieldName;
                                        String patternValue = "di:" + fieldType;
                                        patternDetectionCache.put(patternKey, patternValue);
                                        
                                        logger.debug("Detected Dependency Injection: {} injects {}", 
                                                  className, fieldType);
                                        
                                        // Create implicit workflow for DI relationships
                                        for (String targetClass : classToFileCache.keySet()) {
                                            if (targetClass.endsWith("." + fieldType) || targetClass.equals(fieldType)) {
                                                // Create implicit workflow step
                                                createImplicitWorkflowStep(className, targetClass, "getBean", 
                                                                        fieldName, repositoryName, "di");
                                                break;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    logger.debug("Error analyzing DI pattern in {}: {}", filePath, e.getMessage());
                }
            }
        }
    }
    
    /**
     * Creates an implicit workflow step for design patterns.
     * 
     * @param sourceClass Source class name
     * @param targetClass Target class name
     * @param targetMethod Target method name
     * @param returnType Return type of the target method
     * @param repositoryName Repository name
     * @param patternType Type of pattern (e.g., "factory", "builder", "di")
     */
    private void createImplicitWorkflowStep(String sourceClass, String targetClass, String targetMethod, 
                                          String returnType, String repositoryName, String patternType) {
        try {
            String sourceFile = classToFileCache.getOrDefault(sourceClass, null);
            String targetFile = classToFileCache.getOrDefault(targetClass, null);
            
            if (sourceFile != null && targetFile != null) {
                WorkflowStep step = new WorkflowStep();
                step.setSourceFile(sourceFile);
                step.setSourceMethod("implicit");
                step.setSourceRepository(repositoryName);
                step.setTargetFile(targetFile);
                step.setTargetMethod(targetMethod);
                step.setTargetRepository(repositoryName);
                step.setCrossRepository(false);
                step.setPatternType(patternType);
                
                // Set confidence based on pattern type
                if ("di".equals(patternType)) {
                    step.setConfidence(0.9); // High confidence for DI
                } else if ("factory".equals(patternType)) {
                    step.setConfidence(0.8); // Good confidence for factory pattern
                } else {
                    step.setConfidence(0.7); // Moderate confidence for other patterns
                }
                
                // Add data parameters 
                if ("factory".equals(patternType) || "builder".equals(patternType)) {
                    step.setDataParameters(Collections.singletonList(returnType));
                }
                
                // Add to workflow cache
                workflowByFileByRepoCache
                    .computeIfAbsent(repositoryName, k -> new ConcurrentHashMap<>())
                    .computeIfAbsent(sourceFile, k -> new ArrayList<>())
                    .add(step);
                
                logger.debug("Created implicit workflow step for pattern: {} -> {}.{}", 
                           sourceClass, targetClass, targetMethod);
            }
        } catch (Exception e) {
            logger.debug("Error creating implicit workflow step: {}", e.getMessage());
        }
    }
    
    /**
     * Finds clients of a factory class.
     * 
     * @param factoryClass Factory class name
     * @param factoryMethod Factory method name
     * @param repositoryName Repository name
     * @return List of client class names
     */
    private List<String> findFactoryClients(String factoryClass, String factoryMethod, String repositoryName) {
        List<String> clients = new ArrayList<>();
        
        // Search for files that reference this factory
        for (String className : classToFileCache.keySet()) {
            if (classToRepoCache.getOrDefault(className, "").equals(repositoryName) && 
                !className.equals(factoryClass)) {
                
                String filePath = classToFileCache.get(className);
                
                try {
                    String content = new String(Files.readAllBytes(Paths.get(filePath)));
                    String simpleFactoryName = factoryClass.substring(factoryClass.lastIndexOf(".") + 1);
                    
                    // Look for references to the factory class and method
                    if (content.contains(simpleFactoryName) && content.contains(factoryMethod)) {
                        clients.add(className);
                    }
                } catch (Exception e) {
                    // Ignore errors in searching
                }
            }
        }
        
        return clients;
    }
    
    /**
     * Checks if a type is a primitive or simple type.
     * 
     * @param type Type name
     * @return True if primitive or simple type, false otherwise
     */
    private boolean isPrimitiveType(String type) {
        return type.equals("void") || 
               type.equals("boolean") || type.equals("Boolean") ||
               type.equals("byte") || type.equals("Byte") ||
               type.equals("char") || type.equals("Character") ||
               type.equals("short") || type.equals("Short") ||
               type.equals("int") || type.equals("Integer") ||
               type.equals("long") || type.equals("Long") ||
               type.equals("float") || type.equals("Float") ||
               type.equals("double") || type.equals("Double") ||
               type.equals("String") ||
               type.equals("BigDecimal") || 
               type.equals("BigInteger") ||
               type.equals("Date") ||
               type.equals("LocalDate") ||
               type.equals("LocalDateTime") ||
               type.equals("ZonedDateTime") ||
               type.equals("Instant");
    }
    
    /**
     * Tracks implementations of interfaces across repositories.
     * Builds a map of interfaces to their implementing classes.
     * 
     * @param repositoryName The repository being analyzed
     */
    private void trackInterfaceImplementations(String repositoryName) {
        if (!trackInterfaceImplementations) {
            return;
        }
        
        logger.info("Tracking interface implementations in repository: {}", repositoryName);
        
        for (Map.Entry<String, Set<String>> entry : classHierarchyCache.entrySet()) {
            String className = entry.getKey();
            Set<String> hierarchy = entry.getValue();
            
            if (classToRepoCache.getOrDefault(className, "").equals(repositoryName)) {
                // Check if this class has "interface" or "abstract" in its type
                // Use a default value that's of the right type (empty string)
                String classType = classTypes.containsKey(className) ? 
                    classTypes.get(className) : "class";
                
                if ("interface".equals(classType)) {
                    // This is an interface - add it to the map
                    interfaceImplementations.computeIfAbsent(className, k -> ConcurrentHashMap.newKeySet());
                } else {
                    // This is a class - check if it implements any interfaces
                    for (String parent : hierarchy) {
                        // Use containsKey/get pattern to avoid type mismatch
                        String parentType = classTypes.containsKey(parent) ? 
                            classTypes.get(parent) : "class";
                        
                        if ("interface".equals(parentType)) {
                            // Thread-safe update of the implementation set
                            Set<String> implementations = interfaceImplementations
                                .computeIfAbsent(parent, k -> ConcurrentHashMap.newKeySet());
                            implementations.add(className);
                            
                            logger.debug("Found interface implementation: {} implements {}", 
                                       className, parent);
                        } else if ("abstract".equals(parentType)) {
                            // Thread-safe update for abstract classes
                            Set<String> implementations = interfaceImplementations
                                .computeIfAbsent("abstract:" + parent, k -> ConcurrentHashMap.newKeySet());
                            implementations.add(className);
                            
                            logger.debug("Found abstract class implementation: {} extends {}", 
                                       className, parent);
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Cleanup resources when the bean is destroyed.
     */
    @Override
    public void destroy() {
        clearMemoryEfficientlyIfNeeded(true);
    }
    
    @Override
    public List<List<WorkflowStep>> findExecutionPathsByFilePath(String filePath) {
        logger.info("Finding execution paths for file path: {}", filePath);
        return findExecutionPathsByFile(filePath);
    }
    
    /**
     * Finds execution paths containing a specific file.
     * 
     * @param filePath The file path to search for
     * @return A list of execution paths (each a sequence of workflow steps) involving the file
     */
    public List<List<WorkflowStep>> findExecutionPathsByFile(String filePath) {
        List<List<WorkflowStep>> result = new ArrayList<>();
        
        // Check if execution paths have been built
        if (executionPathCache.isEmpty()) {
            // Build execution paths on-demand if they haven't been built yet
            buildExecutionPaths();
        }
        
        // Check index first for faster retrieval
        if (workflowIndexCache.containsKey(filePath)) {
            Map<String, Integer> pathIndices = workflowIndexCache.get(filePath);
            for (Map.Entry<String, Integer> entry : pathIndices.entrySet()) {
                String key = entry.getKey();
                int pathIndex = entry.getValue();
                
                if (executionPathCache.containsKey(key)) {
                    List<List<WorkflowStep>> paths = executionPathCache.get(key);
                    if (pathIndex < paths.size()) {
                        result.add(paths.get(pathIndex));
                    }
                }
            }
            return result;
        }
        
        // Fallback to linear search if not indexed
        for (Map.Entry<String, List<List<WorkflowStep>>> entry : executionPathCache.entrySet()) {
            for (List<WorkflowStep> path : entry.getValue()) {
                for (WorkflowStep step : path) {
                    if (filePath.equals(step.getSourceFile()) || 
                        filePath.equals(step.getTargetFile())) {
                        result.add(path);
                        break;
                    }
                }
            }
        }
        
        return result;
    }
    
    /**
     * Builds complete execution paths starting from entry points.
     * This method leverages existing workflow data to construct full paths.
     */
    private void buildExecutionPaths() {
        logger.info("Building complete execution paths from entry points");
        
        // Find entry points if not already done
        List<String> entryPoints = findEntryPoints();
        
        for (String entryPoint : entryPoints) {
            String[] parts = entryPoint.split("\\.");
            if (parts.length < 3) continue;
            
            String className = parts[parts.length - 2];
            String methodName = parts[parts.length - 1];
            
            // Find the file for this class
            String sourceFile = null;
            for (String fqn : classToFileCache.keySet()) {
                if (fqn.endsWith("." + className)) {
                    sourceFile = classToFileCache.get(fqn);
                    break;
                }
            }
            
            if (sourceFile != null) {
                // Start path exploration from this entry point
                List<WorkflowStep> initialPath = new ArrayList<>();
                for (Map<String, List<WorkflowStep>> repoWorkflows : workflowByFileByRepoCache.values()) {
                    if (repoWorkflows.containsKey(sourceFile)) {
                        for (WorkflowStep step : repoWorkflows.get(sourceFile)) {
                            if (step.getSourceMethod().equals(methodName)) {
                                initialPath.add(step);
                            }
                        }
                    }
                }
                
                for (WorkflowStep initialStep : initialPath) {
                    List<List<WorkflowStep>> paths = new ArrayList<>();
                    List<WorkflowStep> currentPath = new ArrayList<>();
                    currentPath.add(initialStep);
                    
                    // Recursively explore paths (with cycle detection)
                    exploreExecutionPath(initialStep, currentPath, paths, new HashSet<>(), 0);
                    
                    // Store paths for this entry point
                    String key = className + "." + methodName;
                    executionPathCache.put(key, paths);
                    
                    // Index these paths for faster retrieval
                    indexExecutionPaths(key, paths);
                }
            }
        }
        
        logger.info("Built {} complete execution paths from entry points", executionPathCache.size());
    }
    
    /**
     * Recursively explores execution paths.
     * Uses depth-limited search to prevent excessive exploration.
     */
    private void exploreExecutionPath(WorkflowStep currentStep, List<WorkflowStep> currentPath, 
                                     List<List<WorkflowStep>> allPaths, Set<String> visited,
                                     int depth) {
        // Prevent excessive depth and cycles
        int maxPathDepth = Math.min(maxChildDepth, workflowMaxPathDepth);
        if (depth > maxPathDepth) return;
        
        // Create a unique key for this step to prevent cycles
        String stepKey = null;
        if (currentStep != null && currentStep.getTargetMethod() != null) {
            String targetFile = currentStep.getTargetFile();
            String targetMethod = currentStep.getTargetMethod();
            stepKey = (targetFile != null ? targetFile : "") + "#" + targetMethod;
            
            // Check for cycles
            if (visited.contains(stepKey)) {
                return;
            }
            visited.add(stepKey);
        } else {
            // Skip if we have an invalid step
            return;
        }
        
        // Add the current step to the path
        currentPath.add(currentStep);
        
        // Find all method calls from this target method
        boolean foundChildren = false;
        for (Map.Entry<String, Map<String, List<WorkflowStep>>> repoEntry : workflowByFileByRepoCache.entrySet()) {
            for (Map.Entry<String, List<WorkflowStep>> fileEntry : repoEntry.getValue().entrySet()) {
                for (WorkflowStep step : fileEntry.getValue()) {
                    if (step.getSourceMethod() != null && step.getSourceFile() != null && 
                        currentStep.getTargetMethod() != null) {
                        
                        // Check if this step's source matches our current step's target
                        String sourceMethod = step.getSourceMethod();
                        String sourceFile = step.getSourceFile();
                        String sourceKey = sourceFile + "#" + sourceMethod;
                        
                        if (sourceKey.equals(stepKey)) {
                            // We found a step that continues this path
                            foundChildren = true;
                            
                            // Create a copy of the current path and visited set for this branch
                            List<WorkflowStep> newPath = new ArrayList<>(currentPath);
                            Set<String> newVisited = new HashSet<>(visited);
                            
                            // Recursively explore this child path
                            exploreExecutionPath(step, newPath, allPaths, newVisited, depth + 1);
                        }
                    }
                }
            }
        }
        
        // If this is a leaf node (no children found), add the completed path
        if (!foundChildren) {
            allPaths.add(new ArrayList<>(currentPath));
        }
        
        // Check memory usage and clear if needed
        trackMemoryUsage();
    }
    
    /**
     * Indexes execution paths by file for faster retrieval.
     */
    private void indexExecutionPaths(String entryPointKey, List<List<WorkflowStep>> paths) {
        for (int pathIndex = 0; pathIndex < paths.size(); pathIndex++) {
            List<WorkflowStep> path = paths.get(pathIndex);
            Set<String> indexedFiles = new HashSet<>();
            
            for (WorkflowStep step : path) {
                // Index by source file
                String sourceFile = step.getSourceFile();
                if (!indexedFiles.contains(sourceFile)) {
                    workflowIndexCache
                        .computeIfAbsent(sourceFile, k -> new HashMap<>())
                        .put(entryPointKey, pathIndex);
                    indexedFiles.add(sourceFile);
                }
                
                // Index by target file
                String targetFile = step.getTargetFile();
                if (targetFile != null && !indexedFiles.contains(targetFile)) {
                    workflowIndexCache
                        .computeIfAbsent(targetFile, k -> new HashMap<>())
                        .put(entryPointKey, pathIndex);
                    indexedFiles.add(targetFile);
                }
            }
        }
    }
    
    /**
     * Tracks memory usage and clears caches if needed.
     */
    private void trackMemoryUsage() {
        // Roughly estimate memory usage of caches
        long currentEstimation = 0;
        
        // Count workflow steps with more accurate estimation
        for (Map<String, List<WorkflowStep>> repoMap : workflowByFileByRepoCache.values()) {
            for (List<WorkflowStep> steps : repoMap.values()) {
                for (WorkflowStep step : steps) {
                    currentEstimation += estimateObjectSize(step);
                }
            }
        }
        
        // Count execution paths
        for (List<List<WorkflowStep>> pathSets : executionPathCache.values()) {
            for (List<WorkflowStep> path : pathSets) {
                for (WorkflowStep step : path) {
                    currentEstimation += estimateObjectSize(step);
                }
            }
        }
        
        // Add other caches
        currentEstimation += classHierarchyCache.size() * 512;
        currentEstimation += classToFileCache.size() * 256;
        currentEstimation += classToRepoCache.size() * 256;
        currentEstimation += interfaceImplementations.size() * 512;
        
        // Update tracked usage
        memoryUsage.set(currentEstimation);
        
        // Check if we need to clear some caches
        clearMemoryEfficientlyIfNeeded(false);
    }
    
    /**
     * Estimates the memory size of a workflow step.
     * 
     * @param step The workflow step to estimate
     * @return Estimated size in bytes
     */
    private long estimateObjectSize(WorkflowStep step) {
        long size = 250; // base size
        size += (step.getSourceFile() != null) ? step.getSourceFile().length() * 2 : 0;
        size += (step.getTargetFile() != null) ? step.getTargetFile().length() * 2 : 0;
        size += (step.getSourceMethod() != null) ? step.getSourceMethod().length() * 2 : 0;
        size += (step.getTargetMethod() != null) ? step.getTargetMethod().length() * 2 : 0;
        size += (step.getSourceRepository() != null) ? step.getSourceRepository().length() * 2 : 0;
        size += (step.getTargetRepository() != null) ? step.getTargetRepository().length() * 2 : 0;
        if (step.getDataParameters() != null) {
            for (String param : step.getDataParameters()) {
                size += (param != null) ? param.length() * 2 : 0;
            }
        }
        return size;
    }
    
    /**
     * Clears caches efficiently if memory threshold is exceeded.
     * This tries to preserve the most important data while freeing memory.
     */
    private void clearMemoryEfficientlyIfNeeded(boolean forceClean) {
        long threshold = maxMemoryMb * 1024 * 1024;
        
        if (forceClean || memoryUsage.get() > threshold) {
            logger.info("Memory threshold reached ({}MB), clearing non-essential caches", 
                      memoryUsage.get() / (1024 * 1024));
            
            // Clear in order of least important first
            methodChainCache.clear();
            executionPathCache.clear();
            workflowIndexCache.clear();
            
            if (forceClean || memoryUsage.get() > threshold * 0.8) {
                // Still too high, clear more aggressively but preserve essential data
                
                // Preserve only repositories not currently being analyzed
                for (String repo : processedRepositories) {
                    // Make sure we keep workflow data for active repositories
                    if (!persisted(repo)) {
                        // Save data before clearing, if not already saved
                        try {
                            saveRepositoryWorkflowData(repo);
                        } catch (Exception e) {
                            logger.warn("Failed to save workflow data for repository {} before clearing: {}", 
                                       repo, e.getMessage());
                        }
                    }
                }
            }
            
            System.gc(); // Request garbage collection
            memoryUsage.set(0); // Reset counter to be recalculated on next use
        }
    }
    
    /**
     * Checks if a repository's data has been persisted to disk.
     */
    private boolean persisted(String repositoryName) {
        if (!persistenceEnabled) return false;
        
        File repoDir = new File(new File(workflowDataDir), repositoryName);
        File workflowFile = new File(repoDir, "workflow_steps.dat");
        
        return workflowFile.exists();
    }
    
    /**
     * Saves workflow data for a specific repository.
     */
    private void saveRepositoryWorkflowData(String repositoryName) throws IOException {
        if (!persistenceEnabled) {
            return;
        }
        
        // Repository directory
        File workflowDir = new File(workflowDataDir);
        File repoDir = new File(workflowDir, repositoryName);
        if (!repoDir.exists()) {
            repoDir.mkdirs();
        }
        
        // Save workflow steps
        Map<String, List<WorkflowStep>> repoWorkflows = workflowByFileByRepoCache.get(repositoryName);
        if (repoWorkflows != null) {
            try (ObjectOutputStream oos = new ObjectOutputStream(
                    new FileOutputStream(new File(repoDir, "workflow_steps.dat")))) {
                oos.writeObject(repoWorkflows);
            }
        }
        
        // Also update metadata to include this repository
        Properties repoMetadata = new Properties();
        File metadataFile = new File(workflowDir, "repositories.properties");
        if (metadataFile.exists()) {
            try (FileInputStream fis = new FileInputStream(metadataFile)) {
                repoMetadata.load(fis);
            }
        }
        
        // Update repository metadata
        int count = Integer.parseInt(repoMetadata.getProperty("processed.count", "0"));
        boolean found = false;
        for (int i = 0; i < count; i++) {
            if (repositoryName.equals(repoMetadata.getProperty("processed." + i))) {
                found = true;
                break;
            }
        }
        
        if (!found) {
            repoMetadata.setProperty("processed." + count, repositoryName);
            repoMetadata.setProperty("processed.count", String.valueOf(count + 1));
            
            try (FileOutputStream fos = new FileOutputStream(metadataFile)) {
                repoMetadata.store(fos, "Repository metadata for workflow analysis");
            }
        }
    }
    
    /**
     * Detects common method call chains in the codebase.
     * This identifies frequently occurring sequences of method calls.
     */
    private void detectMethodChains() {
        logger.info("Detecting method call chains");
        
        // Collect all execution paths first
        if (executionPathCache.isEmpty()) {
            buildExecutionPaths();
        }
        
        // Identify common sequences
        Map<String, Integer> chainFrequency = new HashMap<>();
        
        for (List<List<WorkflowStep>> pathSets : executionPathCache.values()) {
            for (List<WorkflowStep> path : pathSets) {
                // For paths of length 2 or more, extract chains of different lengths
                if (path.size() >= 2) {
                    // Use sliding window to find chains of length 2-4
                    for (int chainLength = 2; chainLength <= Math.min(4, path.size()); chainLength++) {
                        for (int i = 0; i <= path.size() - chainLength; i++) {
                            StringBuilder chainKey = new StringBuilder();
                            for (int j = i; j < i + chainLength; j++) {
                                WorkflowStep step = path.get(j);
                                chainKey.append(step.getTargetMethod()).append(":");
                            }
                            
                            String key = chainKey.toString();
                            chainFrequency.put(key, chainFrequency.getOrDefault(key, 0) + 1);
                        }
                    }
                }
            }
        }
        
        // Find chains with frequency > 1 (appearing multiple times)
        for (Map.Entry<String, Integer> entry : chainFrequency.entrySet()) {
            if (entry.getValue() > 1) {
                String chainKey = entry.getKey();
                String[] methodNames = chainKey.split(":");
                
                // Find actual steps for these method names to create chain
                for (Map.Entry<String, List<List<WorkflowStep>>> pathEntry : executionPathCache.entrySet()) {
                    for (List<WorkflowStep> path : pathEntry.getValue()) {
                        findChainInPath(path, methodNames, pathEntry.getKey());
                    }
                }
            }
        }
        
        logger.info("Detected {} method chains across repositories", methodChainCache.size());
    }
    
    /**
     * Finds a specific method chain in a path.
     */
    private void findChainInPath(List<WorkflowStep> path, String[] methodNames, String entryPoint) {
        // Only process if we have enough steps
        if (path.size() < methodNames.length) return;
        
        for (int i = 0; i <= path.size() - methodNames.length; i++) {
            boolean isMatch = true;
            for (int j = 0; j < methodNames.length; j++) {
                if (!path.get(i + j).getTargetMethod().equals(methodNames[j])) {
                    isMatch = false;
                    break;
                }
            }
            
            if (isMatch) {
                // Found a matching chain, extract the steps
                List<WorkflowStep> chainSteps = new ArrayList<>();
                for (int j = 0; j < methodNames.length; j++) {
                    chainSteps.add(path.get(i + j));
                }
                
                // Calculate chain confidence (average of component steps)
                double chainConfidence = 0.0;
                for (WorkflowStep step : chainSteps) {
                    chainConfidence += step.getConfidence();
                }
                chainConfidence /= chainSteps.size();
                
                // Store the chain
                String chainKey = String.join("→", methodNames);
                methodChainCache
                    .computeIfAbsent(entryPoint, k -> new HashMap<>())
                    .computeIfAbsent(chainKey, k -> new ArrayList<>())
                    .addAll(chainSteps);
                
                // Store the confidence score
                methodChainConfidence
                    .computeIfAbsent(entryPoint, k -> new HashMap<>())
                    .put(chainKey, chainConfidence);
            }
        }
    }
    
    /**
     * Represents an error that occurred during workflow analysis.
     */
    private static class AnalysisError implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private final String filePath;
        private final String errorMessage;
        private final long timestamp;
        
        public AnalysisError(String filePath, String errorMessage) {
            this.filePath = filePath;
            this.errorMessage = errorMessage;
            this.timestamp = System.currentTimeMillis();
        }
        
        public String getFilePath() {
            return filePath;
        }
        
        public String getErrorMessage() {
            return errorMessage;
        }
        
        public long getTimestamp() {
            return timestamp;
        }
    }
    
    /**
     * Process method calls extracted by the parser and convert them to workflow steps.
     * 
     * @param parsedData The data parsed from a Java file
     * @param filePath The path to the file being analyzed
     * @param repositoryName The name of the repository containing the file
     */
    @SuppressWarnings("unchecked")
    private void processMethodCallsToWorkflowSteps(Map<String, Object> parsedData, String filePath, String repositoryName) {
        if (parsedData == null) {
            return;
        }
        
        try {
            String className = (String) parsedData.get("className");
            String packageName = (String) parsedData.get("package");
            String fullClassName = packageName != null ? packageName + "." + className : className;
            
            if (fullClassName == null) {
                return;
            }
            
            // Extract methods - handle as Map<String, Object> or List<Map<String, Object>>
            List<Map<String, Object>> methods = new ArrayList<>();
            Object methodsObj = parsedData.get("methods");
            
            if (methodsObj == null) {
                logger.debug("No methods found in {}", filePath);
                return;
            }
            
            logger.debug("Methods data structure type: {} in {}", methodsObj.getClass().getName(), filePath);
            
            if (methodsObj instanceof List) {
                methods = (List<Map<String, Object>>) methodsObj;
            } else if (methodsObj instanceof Map) {
                // Handle methods as a Map where keys are method names
                Map<String, Object> methodsMap = (Map<String, Object>) methodsObj;
                for (Map.Entry<String, Object> entry : methodsMap.entrySet()) {
                    if (entry.getValue() instanceof Map) {
                        Map<String, Object> methodData = (Map<String, Object>) entry.getValue();
                        methodData.put("name", entry.getKey()); // Ensure name is included
                        methods.add(methodData);
                    }
                }
            }
            
            logger.debug("Processing {} methods in {}", methods.size(), filePath);
            
            int stepCount = 0;
            for (Map<String, Object> method : methods) {
                String methodName = (String) method.get("name");
                if (methodName == null) continue;
                
                // Get methodCalls for this method - handle all possible formats
                List<Map<String, Object>> methodCalls = new ArrayList<>();
                Object callsObj = method.get("methodCalls");
                
                if (callsObj == null) {
                    logger.debug("No method calls found for method {} in {}", methodName, filePath);
                    continue;
                }
                
                logger.debug("Method calls data structure type for {}: {} in {}", 
                           methodName, callsObj.getClass().getName(), filePath);
                
                if (callsObj instanceof List) {
                    methodCalls = (List<Map<String, Object>>) callsObj;
                } else if (callsObj instanceof Map) {
                    // Handle as Map of calls
                    Map<String, Object> callsMap = (Map<String, Object>) callsObj;
                    for (Object callValue : callsMap.values()) {
                        if (callValue instanceof Map) {
                            methodCalls.add((Map<String, Object>) callValue);
                        }
                    }
                }
                
                logger.debug("Found {} method calls for method {} in {}", 
                           methodCalls.size(), methodName, filePath);
                
                // Process each method call
                for (Map<String, Object> call : methodCalls) {
                    String targetClass = (String) call.get("targetClass");
                    String targetMethod = (String) call.get("targetMethod");
                    
                    if (targetClass == null || targetMethod == null) {
                        logger.debug("Skipping call with missing class or method: {} -> {}" + 
                                   ", found keys: {}", targetClass, targetMethod, call.keySet());
                        continue;
                    }
                    
                    // Create workflow step
                    WorkflowStep step = new WorkflowStep();
                    step.setSourceFile(filePath);
                    step.setSourceMethod(methodName);
                    step.setSourceRepository(repositoryName);
                    step.setTargetMethod(targetMethod);
                    step.setTargetFile(null); // We don't know the target file yet
                    step.setTargetRepository(repositoryName); // Default to same repository
                    step.setConfidence(1.0); // Direct method call has high confidence
                    
                    // Add to repository cache
                    Map<String, List<WorkflowStep>> repoWorkflow = workflowByFileByRepoCache
                        .computeIfAbsent(repositoryName, k -> new ConcurrentHashMap<>());
                    
                    List<WorkflowStep> fileSteps = repoWorkflow
                        .computeIfAbsent(filePath, k -> new ArrayList<>());
                    
                    fileSteps.add(step);
                    stepCount++;
                }
            }
            
            logger.debug("Created {} workflow steps for {}", stepCount, filePath);
            
        } catch (Exception e) {
            logger.error("Error processing method calls in {}: {}", filePath, e.getMessage());
            logger.debug("Exception details:", e);
        }
    }
} 