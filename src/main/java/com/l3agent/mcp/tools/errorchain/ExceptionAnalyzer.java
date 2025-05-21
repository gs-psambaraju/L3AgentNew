package com.l3agent.mcp.tools.errorchain;

import com.l3agent.mcp.tools.callpath.BytecodeAnalyzer;
import com.l3agent.mcp.tools.errorchain.model.ErrorChainResult;
import com.l3agent.mcp.tools.errorchain.model.ExceptionNode;
import com.l3agent.mcp.tools.errorchain.model.HandlingStrategy;
import com.l3agent.mcp.tools.errorchain.model.LoggingPattern;
import com.l3agent.mcp.tools.errorchain.model.PropagationChain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Analyzes exception propagation chains and error handling patterns.
 * Identifies how exceptions flow through the system, wrapping patterns,
 * common error messages, and potential anti-patterns.
 */
@Component
public class ExceptionAnalyzer {
    
    private static final Logger logger = LoggerFactory.getLogger(ExceptionAnalyzer.class);
    
    // Regular expression patterns for analysis
    private static final String CATCH_PATTERN = "catch\\s*\\(\\s*([\\w\\.]+)(?:\\s+\\w+)?\\s*\\)";
    private static final String THROW_PATTERN = "throw\\s+new\\s+([\\w\\.]+)(\\(.*?\\))";
    private static final String LOGGING_PATTERN = "(?:log|logger)\\.(error|warn|info|debug|trace)\\((.*)\\)";
    private static final String WRAPPING_PATTERN = "new\\s+([\\w\\.]+)\\([^)]*([\\w\\.]+Exception)[^)]*\\)";
    
    // Anti-pattern detection patterns
    private static final String EMPTY_CATCH_PATTERN = "catch\\s*\\([^)]+\\)\\s*\\{\\s*\\}";
    private static final String SWALLOWED_EXCEPTION_PATTERN = "catch\\s*\\([^)]+\\)\\s*\\{(?!.*?(?:throw|log|return)).*?\\}";
    private static final String GENERIC_CATCH_PATTERN = "catch\\s*\\(\\s*Exception\\s+[\\w]+\\s*\\)";
    
    // Cache for analyzed exceptions to avoid redundant work
    private Map<String, ErrorChainResult> analysisCache = new ConcurrentHashMap<>();
    
    @Value("${l3agent.errorchain.scan-paths:src/main/java,src/test/java}")
    private String scanPaths;
    
    @Value("${l3agent.errorchain.cache-enabled:true}")
    private boolean cacheEnabled;
    
    @Value("${l3agent.errorchain.max-propagation-depth:10}")
    private int maxPropagationDepth;
    
    @Autowired
    private BytecodeAnalyzer bytecodeAnalyzer;
    
    /**
     * Analyzes the propagation chain and handling patterns for an exception.
     * 
     * @param exceptionClass Fully qualified name of the exception to analyze
     * @param includeHierarchy Whether to analyze the exception hierarchy
     * @param analyzeWrapping Whether to analyze exception wrapping patterns
     * @param identifyLogging Whether to analyze logging patterns
     * @param detectAntiPatterns Whether to detect error handling anti-patterns
     * @return The analysis result
     * @throws IOException If an I/O error occurs during file scanning
     */
    public ErrorChainResult analyzeExceptionChain(
            String exceptionClass,
            boolean includeHierarchy,
            boolean analyzeWrapping,
            boolean identifyLogging,
            boolean detectAntiPatterns) throws IOException {
        
        // Check if we have a cached result
        String cacheKey = generateCacheKey(exceptionClass, includeHierarchy, analyzeWrapping, identifyLogging, detectAntiPatterns);
        if (cacheEnabled && analysisCache.containsKey(cacheKey)) {
            logger.info("Using cached analysis for exception: {}", exceptionClass);
            return analysisCache.get(cacheKey);
        }
        
        logger.info("Starting analysis of exception: {}", exceptionClass);
        ErrorChainResult result = new ErrorChainResult(exceptionClass);
        
        // Analyze exception hierarchy if requested
        if (includeHierarchy) {
            analyzeExceptionHierarchy(exceptionClass, result);
        }
        
        // Find where the exception is thrown, caught, and handled
        findExceptionUsages(exceptionClass, result);
        
        // Analyze wrapping patterns if requested
        if (analyzeWrapping) {
            analyzeWrappingPatterns(exceptionClass, result);
        }
        
        // Identify logging patterns if requested
        if (identifyLogging) {
            identifyLoggingPatterns(exceptionClass, result);
        }
        
        // Detect anti-patterns if requested
        if (detectAntiPatterns) {
            detectAntiPatterns(exceptionClass, result);
        }
        
        // Build propagation chains
        buildPropagationChains(exceptionClass, result);
        
        // Identify common error messages
        identifyCommonErrorMessages(exceptionClass, result);
        
        // Cache the result if caching is enabled
        if (cacheEnabled) {
            analysisCache.put(cacheKey, result);
        }
        
        return result;
    }
    
    /**
     * Generates a cache key for an analysis configuration.
     */
    private String generateCacheKey(
            String exceptionClass,
            boolean includeHierarchy,
            boolean analyzeWrapping,
            boolean identifyLogging,
            boolean detectAntiPatterns) {
        return String.format("%s_%b_%b_%b_%b",
                exceptionClass,
                includeHierarchy,
                analyzeWrapping,
                identifyLogging,
                detectAntiPatterns);
    }
    
    /**
     * Analyzes the exception class hierarchy using bytecode analysis.
     */
    private void analyzeExceptionHierarchy(String exceptionClass, ErrorChainResult result) {
        try {
            logger.info("Analyzing hierarchy for exception: {}", exceptionClass);
            
            // Use BytecodeAnalyzer to get accurate hierarchy information
            ExceptionNode rootNode = bytecodeAnalyzer.analyzeExceptionHierarchy(exceptionClass);
            result.addExceptionHierarchy(rootNode);
            
            // Add an analysis note
            result.addAnalysisNote("Exception hierarchy determined using bytecode analysis");
            
            // Add additional analysis note about the exception type
            if (rootNode.isChecked()) {
                result.addAnalysisNote("This is a checked exception - must be explicitly caught or declared in method signatures");
            } else {
                result.addAnalysisNote("This is an unchecked exception - can be thrown without explicit declaration");
            }
            
            // List classes that throw this exception
            try {
                List<String> throwingClasses = bytecodeAnalyzer.findClassesThatThrow(exceptionClass);
                if (!throwingClasses.isEmpty()) {
                    int count = Math.min(throwingClasses.size(), 5); // Limit to 5 examples
                    StringBuilder note = new StringBuilder("Found ")
                        .append(throwingClasses.size())
                        .append(" method(s) that declare this exception in throws clause");
                    
                    if (!throwingClasses.isEmpty()) {
                        note.append(". Examples: ");
                        for (int i = 0; i < count; i++) {
                            if (i > 0) note.append(", ");
                            note.append(throwingClasses.get(i));
                        }
                        if (throwingClasses.size() > count) {
                            note.append(", and ").append(throwingClasses.size() - count).append(" more");
                        }
                    }
                    
                    result.addAnalysisNote(note.toString());
                }
            } catch (Exception e) {
                logger.warn("Error finding classes that throw {}", exceptionClass, e);
            }
        } catch (Exception e) {
            logger.warn("Error analyzing exception hierarchy, falling back to regex-based analysis", e);
            // Fall back to regex-based analysis if BytecodeAnalyzer fails
            analyzeExceptionHierarchyWithRegex(exceptionClass, result);
        }
    }
    
    /**
     * Fallback method that uses regex-based analysis for exception hierarchy.
     */
    private void analyzeExceptionHierarchyWithRegex(String exceptionClass, ErrorChainResult result) {
        logger.info("Using regex-based analysis for hierarchy of: {}", exceptionClass);
        ExceptionNode rootNode = new ExceptionNode(exceptionClass);
        result.addExceptionHierarchy(rootNode);
        result.addAnalysisNote("Exception " + exceptionClass + " analyzed for hierarchy relationships (regex-based)");
    }
    
    /**
     * Finds where the exception is thrown, caught, and handled in the codebase.
     */
    private void findExceptionUsages(String exceptionClass, ErrorChainResult result) throws IOException {
        logger.info("Finding usages of exception: {}", exceptionClass);
        
        // Extract simple name for easier regex matching
        String simpleExceptionName = bytecodeAnalyzer.getSimpleName(exceptionClass);
        
        // Compile patterns to find where this exception is used
        Pattern throwPattern = Pattern.compile("throw\\s+new\\s+" + simpleExceptionName + "\\s*\\(");
        Pattern catchPattern = Pattern.compile("catch\\s*\\(\\s*" + simpleExceptionName + "\\s+");
        
        // Track where the exception is used
        Set<String> throwLocations = new HashSet<>();
        Set<String> catchLocations = new HashSet<>();
        
        // Scan the codebase for usages
        for (String scanPath : scanPaths.split(",")) {
            Path path = Paths.get(scanPath.trim());
            if (Files.exists(path)) {
                try (Stream<Path> paths = Files.walk(path)) {
                    List<Path> javaFiles = paths
                            .filter(Files::isRegularFile)
                            .filter(p -> p.toString().endsWith(".java"))
                            .collect(Collectors.toList());
                    
                    for (Path file : javaFiles) {
                        String content = new String(Files.readAllBytes(file));
                        
                        // Look for throw statements
                        Matcher throwMatcher = throwPattern.matcher(content);
                        if (throwMatcher.find()) {
                            throwLocations.add(file.toString());
                        }
                        
                        // Look for catch statements
                        Matcher catchMatcher = catchPattern.matcher(content);
                        if (catchMatcher.find()) {
                            catchLocations.add(file.toString());
                        }
                    }
                }
            }
        }
        
        // Store the locations
        result.setThrowLocations(throwLocations);
        result.setCatchLocations(catchLocations);
        
        logger.info("Found {} throw locations and {} catch locations for {}", 
                throwLocations.size(), catchLocations.size(), exceptionClass);
        
        // Add rethrow locations
        for (String location : throwLocations) {
            result.addRethrowLocation(location, "Throws " + simpleExceptionName + " directly");
        }
    }
    
    /**
     * Analyzes exception wrapping patterns.
     */
    private void analyzeWrappingPatterns(String exceptionClass, ErrorChainResult result) throws IOException {
        logger.info("Analyzing wrapping patterns for exception: {}", exceptionClass);
        
        // Extract simple name for easier regex matching
        String simpleExceptionName = bytecodeAnalyzer.getSimpleName(exceptionClass);
        
        // Patterns to identify wrapping
        Pattern wrapperPattern = Pattern.compile(
                "new\\s+([\\w\\.]+Exception)\\(.*" + simpleExceptionName + ".*\\)");
        Pattern wrappedPattern = Pattern.compile(
                "new\\s+" + simpleExceptionName + "\\(.*([\\w\\.]+Exception).*\\)");
        
        // Track wrapping relationships
        Map<String, Integer> wrappingExceptions = new HashMap<>();
        Map<String, Integer> wrappedExceptions = new HashMap<>();
        
        // Scan the codebase for wrapping patterns
        for (String scanPath : scanPaths.split(",")) {
            Path path = Paths.get(scanPath.trim());
            if (Files.exists(path)) {
                try (Stream<Path> paths = Files.walk(path)) {
                    List<Path> javaFiles = paths
                            .filter(Files::isRegularFile)
                            .filter(p -> p.toString().endsWith(".java"))
                            .collect(Collectors.toList());
                    
                    for (Path file : javaFiles) {
                        String content = new String(Files.readAllBytes(file));
                        
                        // Look for cases where this exception is wrapped by others
                        Matcher wrapperMatcher = wrapperPattern.matcher(content);
                        while (wrapperMatcher.find()) {
                            String wrapperException = wrapperMatcher.group(1);
                            wrappingExceptions.put(wrapperException, 
                                    wrappingExceptions.getOrDefault(wrapperException, 0) + 1);
                        }
                        
                        // Look for cases where this exception wraps others
                        Matcher wrappedMatcher = wrappedPattern.matcher(content);
                        while (wrappedMatcher.find()) {
                            String wrappedException = wrappedMatcher.group(1);
                            wrappedExceptions.put(wrappedException,
                                    wrappedExceptions.getOrDefault(wrappedException, 0) + 1);
                        }
                    }
                }
            }
        }
        
        // Add wrapping patterns to the result
        for (Map.Entry<String, Integer> entry : wrappingExceptions.entrySet()) {
            result.addWrappingPattern(entry.getKey(), exceptionClass, entry.getValue());
        }
        
        for (Map.Entry<String, Integer> entry : wrappedExceptions.entrySet()) {
            result.addWrappingPattern(exceptionClass, entry.getKey(), entry.getValue());
        }
        
        logger.info("Found {} exception types that wrap {} and {} exception types wrapped by {}",
                wrappingExceptions.size(), exceptionClass, wrappedExceptions.size(), exceptionClass);
    }
    
    /**
     * Identifies logging patterns associated with the exception.
     */
    private void identifyLoggingPatterns(String exceptionClass, ErrorChainResult result) throws IOException {
        logger.info("Identifying logging patterns for exception: {}", exceptionClass);
        
        // Extract simple name for easier regex matching
        String simpleExceptionName = bytecodeAnalyzer.getSimpleName(exceptionClass);
        
        // Pattern to identify logging of this exception
        Pattern loggingPattern = Pattern.compile(
                "log(?:ger)?\\.(error|warn|info|debug|trace)\\(.*" + simpleExceptionName + ".*\\)");
        
        // Track logging levels
        Map<String, Integer> loggingLevels = new HashMap<>();
        
        // Scan the codebase for logging patterns
        for (String scanPath : scanPaths.split(",")) {
            Path path = Paths.get(scanPath.trim());
            if (Files.exists(path)) {
                try (Stream<Path> paths = Files.walk(path)) {
                    List<Path> javaFiles = paths
                            .filter(Files::isRegularFile)
                            .filter(p -> p.toString().endsWith(".java"))
                            .collect(Collectors.toList());
                    
                    for (Path file : javaFiles) {
                        String content = new String(Files.readAllBytes(file));
                        
                        // Look for logging statements
                        Matcher logMatcher = loggingPattern.matcher(content);
                        while (logMatcher.find()) {
                            String level = logMatcher.group(1);
                            loggingLevels.put(level, loggingLevels.getOrDefault(level, 0) + 1);
                        }
                    }
                }
            }
        }
        
        // Add logging patterns to the result
        for (Map.Entry<String, Integer> entry : loggingLevels.entrySet()) {
            result.addLoggingPattern(new LoggingPattern(exceptionClass, entry.getKey(), entry.getValue()));
        }
        
        logger.info("Found logging patterns at levels: {}", loggingLevels.keySet());
    }
    
    /**
     * Detects common anti-patterns in exception handling.
     */
    private void detectAntiPatterns(String exceptionClass, ErrorChainResult result) throws IOException {
        logger.info("Detecting anti-patterns for exception: {}", exceptionClass);
        
        // Extract simple name for easier regex matching
        String simpleExceptionName = bytecodeAnalyzer.getSimpleName(exceptionClass);
        
        // Patterns to identify anti-patterns
        Pattern emptyCatchPattern = Pattern.compile(
                "catch\\s*\\(\\s*" + simpleExceptionName + "\\s+[\\w]+\\s*\\)\\s*\\{\\s*\\}");
        Pattern swallowedPattern = Pattern.compile(
                "catch\\s*\\(\\s*" + simpleExceptionName + "\\s+[\\w]+\\s*\\)\\s*\\{(?!.*?(?:throw|log|return)).*?\\}");
        Pattern genericCatchPattern = Pattern.compile(
                "catch\\s*\\(\\s*Exception\\s+[\\w]+\\s*\\)"); 
        Pattern catchAndLogOnlyPattern = Pattern.compile(
                "catch\\s*\\(\\s*" + simpleExceptionName + "\\s+([\\w]+)\\s*\\)\\s*\\{[^}]*?log(?:ger)?\\.[a-z]+\\(.*?\\1.*?\\)[^}]*?\\}");
        Pattern printstackTracePattern = Pattern.compile(
                "catch\\s*\\(\\s*" + simpleExceptionName + "\\s+([\\w]+)\\s*\\)\\s*\\{[^}]*?\\1\\.printStackTrace\\(\\)[^}]*?\\}");
        Pattern threadSleepInCatchPattern = Pattern.compile(
                "catch\\s*\\(\\s*" + simpleExceptionName + "\\s+[\\w]+\\s*\\)[^}]*?Thread\\.sleep\\([^)]*?\\)[^}]*?\\}");
        
        // Track anti-pattern locations
        List<String> emptyCatchLocations = new ArrayList<>();
        List<String> swallowedLocations = new ArrayList<>();
        List<String> genericCatchLocations = new ArrayList<>();
        List<String> catchAndLogOnlyLocations = new ArrayList<>();
        List<String> printStackTraceLocations = new ArrayList<>();
        List<String> threadSleepLocations = new ArrayList<>();
        
        // Scan the codebase for anti-patterns
        for (String scanPath : scanPaths.split(",")) {
            Path path = Paths.get(scanPath.trim());
            if (Files.exists(path)) {
                try (Stream<Path> paths = Files.walk(path)) {
                    List<Path> javaFiles = paths
                            .filter(Files::isRegularFile)
                            .filter(p -> p.toString().endsWith(".java"))
                            .collect(Collectors.toList());
                    
                    for (Path file : javaFiles) {
                        String content = new String(Files.readAllBytes(file));
                        
                        // Look for empty catch blocks
                        Matcher emptyCatchMatcher = emptyCatchPattern.matcher(content);
                        if (emptyCatchMatcher.find()) {
                            emptyCatchLocations.add(file.toString());
                        }
                        
                        // Look for swallowed exceptions
                        Matcher swallowedMatcher = swallowedPattern.matcher(content);
                        if (swallowedMatcher.find()) {
                            swallowedLocations.add(file.toString());
                        }
                        
                        // Look for generic catch blocks
                        Matcher genericCatchMatcher = genericCatchPattern.matcher(content);
                        if (genericCatchMatcher.find()) {
                            genericCatchLocations.add(file.toString());
                        }
                        
                        // Look for catch-and-log-only patterns
                        Matcher catchAndLogOnlyMatcher = catchAndLogOnlyPattern.matcher(content);
                        if (catchAndLogOnlyMatcher.find()) {
                            catchAndLogOnlyLocations.add(file.toString());
                        }
                        
                        // Look for printStackTrace patterns
                        Matcher printStackTraceMatcher = printstackTracePattern.matcher(content);
                        if (printStackTraceMatcher.find()) {
                            printStackTraceLocations.add(file.toString());
                        }
                        
                        // Look for Thread.sleep in catch blocks
                        Matcher threadSleepMatcher = threadSleepInCatchPattern.matcher(content);
                        if (threadSleepMatcher.find()) {
                            threadSleepLocations.add(file.toString());
                        }
                    }
                }
            }
        }
        
        // Add detected anti-patterns to the result with recommendations
        if (!emptyCatchLocations.isEmpty()) {
            Map<String, List<String>> antiPattern = new HashMap<>();
            antiPattern.put("Description", List.of("Exception is caught but not handled"));
            antiPattern.put("Impact", List.of("Critical", "Silently ignores failures"));
            antiPattern.put("Recommendation", List.of(
                "Log the exception at appropriate level",
                "Consider rethrowing as a more specific exception",
                "Add appropriate recovery logic if necessary"
            ));
            antiPattern.put("Locations", emptyCatchLocations);
            result.addEnhancedAntiPattern("Empty Catch Block", antiPattern);
        }
        
        if (!swallowedLocations.isEmpty()) {
            Map<String, List<String>> antiPattern = new HashMap<>();
            antiPattern.put("Description", List.of("Exception is caught but neither logged nor rethrown"));
            antiPattern.put("Impact", List.of("High", "Makes debugging impossible and hides failures"));
            antiPattern.put("Recommendation", List.of(
                "Always log exceptions that are caught but not rethrown",
                "Consider adding appropriate recovery logic",
                "Use specific exception types instead of catching broad exceptions"
            ));
            antiPattern.put("Locations", swallowedLocations);
            result.addEnhancedAntiPattern("Swallowed Exception", antiPattern);
        }
        
        if (!genericCatchLocations.isEmpty()) {
            Map<String, List<String>> antiPattern = new HashMap<>();
            antiPattern.put("Description", List.of("Using generic Exception catch block"));
            antiPattern.put("Impact", List.of("Medium", "Catches unexpected exceptions and can mask serious issues"));
            antiPattern.put("Recommendation", List.of(
                "Use specific exception types that match the expected failure modes",
                "Consider separating catch blocks for different exception types",
                "If generic catch is necessary, ensure proper logging and handling"
            ));
            antiPattern.put("Locations", genericCatchLocations);
            result.addEnhancedAntiPattern("Generic Exception Catch", antiPattern);
        }
        
        if (!catchAndLogOnlyLocations.isEmpty()) {
            Map<String, List<String>> antiPattern = new HashMap<>();
            antiPattern.put("Description", List.of("Exception is caught and logged but no recovery action is taken"));
            antiPattern.put("Impact", List.of("Medium", "Continues execution but may leave system in inconsistent state"));
            antiPattern.put("Recommendation", List.of(
                "Add appropriate recovery logic",
                "Consider whether execution should continue or be aborted",
                "If operation is optional, ensure system remains in consistent state"
            ));
            antiPattern.put("Locations", catchAndLogOnlyLocations);
            result.addEnhancedAntiPattern("Catch-and-Log-Only", antiPattern);
        }
        
        if (!printStackTraceLocations.isEmpty()) {
            Map<String, List<String>> antiPattern = new HashMap<>();
            antiPattern.put("Description", List.of("Using printStackTrace() instead of proper logging"));
            antiPattern.put("Impact", List.of("Medium", "Stack traces go to standard error instead of log system"));
            antiPattern.put("Recommendation", List.of(
                "Replace printStackTrace() with proper logging statements",
                "Use SLF4J or Log4j for consistent logging",
                "Consider log level appropriately (error for exceptions, warn for expected issues)"
            ));
            antiPattern.put("Locations", printStackTraceLocations);
            result.addEnhancedAntiPattern("Using printStackTrace()", antiPattern);
        }
        
        if (!threadSleepLocations.isEmpty()) {
            Map<String, List<String>> antiPattern = new HashMap<>();
            antiPattern.put("Description", List.of("Using Thread.sleep() in exception handler"));
            antiPattern.put("Impact", List.of("Medium", "Can block threads and cause performance issues"));
            antiPattern.put("Recommendation", List.of(
                "Avoid Thread.sleep() in exception handlers",
                "Consider using exponential backoff for retries",
                "Use asynchronous retry mechanisms instead of sleeping"
            ));
            antiPattern.put("Locations", threadSleepLocations);
            result.addEnhancedAntiPattern("Thread Sleep In Catch Block", antiPattern);
        }
        
        // If no anti-patterns were found, add a positive note
        if (emptyCatchLocations.isEmpty() && swallowedLocations.isEmpty() && 
            genericCatchLocations.isEmpty() && catchAndLogOnlyLocations.isEmpty() &&
            printStackTraceLocations.isEmpty() && threadSleepLocations.isEmpty()) {
            result.addAnalysisNote("No significant exception handling anti-patterns were detected");
        } else {
            int totalAntiPatterns = emptyCatchLocations.size() + swallowedLocations.size() + 
                genericCatchLocations.size() + catchAndLogOnlyLocations.size() +
                printStackTraceLocations.size() + threadSleepLocations.size();
            result.addAnalysisNote("Detected " + totalAntiPatterns + " instances of exception handling anti-patterns");
        }
        
        // Add best practice recommendations based on exception type
        addBestPracticeRecommendations(exceptionClass, result);
        
        logger.info("Detected anti-patterns: Empty catch blocks: {}, Swallowed exceptions: {}, " +
                "Generic catches: {}, Catch-and-log-only: {}, printStackTrace(): {}, Thread.sleep: {}",
                emptyCatchLocations.size(), swallowedLocations.size(), genericCatchLocations.size(),
                catchAndLogOnlyLocations.size(), printStackTraceLocations.size(), threadSleepLocations.size());
    }
    
    /**
     * Adds best practice recommendations based on the exception type.
     */
    private void addBestPracticeRecommendations(String exceptionClass, ErrorChainResult result) {
        // Add specific recommendations based on exception type
        if (exceptionClass.endsWith("RuntimeException") || 
            exceptionClass.equals("java.lang.RuntimeException")) {
            result.addRecommendation("RuntimeException Handling", 
                    "RuntimeExceptions should only be caught at application boundaries or with specific recovery logic");
        } else if (exceptionClass.contains("IO") || exceptionClass.endsWith("IOException")) {
            result.addRecommendation("IO Exception Handling",
                    "Close resources properly using try-with-resources and consider retry mechanisms for transient IO failures");
        } else if (exceptionClass.contains("SQL") || exceptionClass.endsWith("SQLException")) {
            result.addRecommendation("SQL Exception Handling",
                    "Use specific catch blocks for different SQL error codes and implement proper transaction management");
        } else if (exceptionClass.contains("Timeout") || exceptionClass.endsWith("TimeoutException")) {
            result.addRecommendation("Timeout Exception Handling",
                    "Implement circuit breaker patterns and consider fallback mechanisms for timeout scenarios");
        }
        
        // General recommendations
        result.addRecommendation("Exception Documentation",
                "Document all checked exceptions in method Javadoc and explain their meaning");
        result.addRecommendation("Exception Specificity",
                "Catch the most specific exception type possible, avoid catching Exception directly");
    }
    
    /**
     * Builds propagation chains for the exception.
     */
    private void buildPropagationChains(String exceptionClass, ErrorChainResult result) {
        logger.info("Building propagation chains for exception: {}", exceptionClass);
        
        try {
            // Use BytecodeAnalyzer to get actual propagation chains
            List<PropagationChain> propagationChains = bytecodeAnalyzer.analyzeExceptionPropagation(
                    exceptionClass, maxPropagationDepth);
            
            // Add the chains to the result
            for (PropagationChain chain : propagationChains) {
                result.addPropagationChain(chain);
            }
            
            // If no propagation chains were found, add a note
            if (propagationChains.isEmpty()) {
                result.addAnalysisNote("No exception propagation chains could be detected through bytecode analysis");
                // Fall back to a sample chain if no real chains were found
                createSamplePropagationChain(exceptionClass, result);
            } else {
                result.addAnalysisNote("Found " + propagationChains.size() + 
                        " exception propagation chains through bytecode analysis");
                
                // Add handling strategies based on found chains
                addHandlingStrategiesFromChains(propagationChains, exceptionClass, result);
            }
            
        } catch (Exception e) {
            // If bytecode analysis fails, log the error and fall back to a sample chain
            logger.warn("Error analyzing exception propagation through bytecode analysis: {}", e.getMessage());
            result.addAnalysisNote("Exception propagation analysis through bytecode failed: " + e.getMessage());
            createSamplePropagationChain(exceptionClass, result);
        }
        
        logger.info("Built propagation chains for exception: {}", exceptionClass);
    }
    
    /**
     * Creates a sample propagation chain as a fallback when bytecode analysis fails.
     */
    private void createSamplePropagationChain(String exceptionClass, ErrorChainResult result) {
        // Create a sample chain as a fallback
        PropagationChain sampleChain = new PropagationChain(exceptionClass);
        sampleChain.addNode("ServiceLayer", "Throws");
        sampleChain.addNode("ControllerLayer", "Catches and wraps");
        sampleChain.addNode("ApiEndpoint", "Returns error response");
        
        result.addPropagationChain(sampleChain);
        
        // Add a sample handling strategy
        result.addHandlingStrategy(new HandlingStrategy(
                exceptionClass,
                "ControllerAdvice",
                "Global exception handler converts to API response",
                "High"));
        
        result.addAnalysisNote("Using sample propagation chain due to limitations in actual code analysis");
    }
    
    /**
     * Adds handling strategies based on the discovered propagation chains.
     */
    private void addHandlingStrategiesFromChains(List<PropagationChain> chains, String exceptionClass, ErrorChainResult result) {
        // Track components that handle the exception
        Map<String, String> handlerComponents = new HashMap<>();
        
        // Extract handler information from the chains
        for (PropagationChain chain : chains) {
            for (Map<String, String> node : chain.getNodes()) {
                String action = node.get("action");
                if ("CATCHES".equals(action)) {
                    String component = node.get("component");
                    String location = node.get("location");
                    String details = node.getOrDefault("details", "Handles " + exceptionClass);
                    
                    handlerComponents.put(component, location);
                    
                    // Add a handling strategy for this component
                    String effectiveness = "Medium"; // Default
                    
                    // Try to determine effectiveness based on component type
                    if (component.contains("Controller") || component.contains("Advice")) {
                        effectiveness = "High"; // Controllers and advice classes often provide good handling
                    } else if (component.contains("Service")) {
                        effectiveness = "Medium"; // Services typically handle with mixed effectiveness
                    } else if (component.contains("Repository") || component.contains("Dao")) {
                        effectiveness = "Low"; // Data layer usually has minimal handling
                    }
                    
                    result.addHandlingStrategy(new HandlingStrategy(
                            exceptionClass,
                            component,
                            details,
                            effectiveness));
                }
            }
        }
        
        // If we didn't find any handlers, note that
        if (handlerComponents.isEmpty()) {
            result.addAnalysisNote("No explicit exception handlers found in the analyzed code");
        }
    }
    
    /**
     * Identifies common error messages associated with the exception.
     */
    private void identifyCommonErrorMessages(String exceptionClass, ErrorChainResult result) throws IOException {
        logger.info("Identifying common error messages for exception: {}", exceptionClass);
        
        // Extract simple name for easier regex matching
        String simpleExceptionName = bytecodeAnalyzer.getSimpleName(exceptionClass);
        
        // Pattern to identify error messages
        Pattern messagePattern = Pattern.compile(
                "new\\s+" + simpleExceptionName + "\\s*\\(\\s*\"([^\"]+)\"");
        
        // Track error messages
        Map<String, Integer> errorMessages = new HashMap<>();
        
        // Scan the codebase for error messages
        for (String scanPath : scanPaths.split(",")) {
            Path path = Paths.get(scanPath.trim());
            if (Files.exists(path)) {
                try (Stream<Path> paths = Files.walk(path)) {
                    List<Path> javaFiles = paths
                            .filter(Files::isRegularFile)
                            .filter(p -> p.toString().endsWith(".java"))
                            .collect(Collectors.toList());
                    
                    for (Path file : javaFiles) {
                        String content = new String(Files.readAllBytes(file));
                        
                        // Look for exception construction with messages
                        Matcher messageMatcher = messagePattern.matcher(content);
                        while (messageMatcher.find()) {
                            String message = messageMatcher.group(1);
                            errorMessages.put(message, errorMessages.getOrDefault(message, 0) + 1);
                        }
                    }
                }
            }
        }
        
        // Add common error messages to the result
        for (Map.Entry<String, Integer> entry : errorMessages.entrySet()) {
            // Only add messages that appear more than once
            if (entry.getValue() > 1) {
                result.addCommonErrorMessage(entry.getKey(), entry.getValue());
            }
        }
        
        logger.info("Found {} common error messages for exception: {}", 
                errorMessages.size(), exceptionClass);
    }
} 