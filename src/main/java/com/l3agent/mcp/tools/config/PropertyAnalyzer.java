package com.l3agent.mcp.tools.config;

import com.l3agent.mcp.config.L3AgentPathConfig;
import com.l3agent.mcp.tools.config.ast.ASTPropertyAnalyzer;
import com.l3agent.mcp.tools.config.context.PropertyContextAnalyzer;
import com.l3agent.mcp.tools.config.db.DatabaseConfigDetector;
import com.l3agent.mcp.tools.config.indirect.IndirectReferenceDetector;
import com.l3agent.mcp.tools.config.model.ConfigImpactResult;
import com.l3agent.mcp.tools.config.model.DatabaseConfigReference;
import com.l3agent.mcp.tools.config.model.PropertyReference;
import com.l3agent.mcp.tools.config.model.PropertySource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
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
 * Analyzes the impact of configuration properties throughout the codebase.
 */
@Component
public class PropertyAnalyzer {
    
    private static final Logger logger = LoggerFactory.getLogger(PropertyAnalyzer.class);
    
    // Regular expressions for finding property references
    private static final String VALUE_ANNOTATION_PATTERN = "@Value\\s*\\(\\s*\"\\$\\{([^:}]+)(?::([^}]*))?\\}\"\\s*\\)";
    private static final String ENVIRONMENT_GET_PATTERN = "environment\\.getProperty\\s*\\(\\s*\"([^\"]+)\"";
    private static final String PROPERTIES_GET_PATTERN = "properties\\.get(?:Property)?\\s*\\(\\s*\"([^\"]+)\"";
    private static final String CONFIG_PROPERTIES_PATTERN = "@ConfigurationProperties\\s*\\(\\s*\"([^\"]+)\"\\s*\\)";
    private static final String CONDITIONAL_PROPERTY_PATTERN = "@ConditionalOn(?:Property|Bean)\\s*\\(\\s*(?:name|value)\\s*=\\s*\"([^\"]+)\"";
    private static final String REPOSITORY_PATTERN = "(?:extends|implements)\\s+.*Repository";
    private static final String DB_CONFIG_PATTERN = "(?:SettingsRepository|ConfigurationRepository|OptionsRepository)";
    
    // Cache for faster repeated analysis
    private Map<String, PropertyReference> referenceCache = new ConcurrentHashMap<>();
    private Set<String> analyzedFiles = ConcurrentHashMap.newKeySet();
    
    // Package naming patterns that indicate different component types
    private static final Map<String, String> PACKAGE_COMPONENT_TYPES = new HashMap<>();
    static {
        PACKAGE_COMPONENT_TYPES.put(".repository.", "Repository");
        PACKAGE_COMPONENT_TYPES.put(".repositories.", "Repository");
        PACKAGE_COMPONENT_TYPES.put(".dao.", "Repository");
        PACKAGE_COMPONENT_TYPES.put(".service.", "Service");
        PACKAGE_COMPONENT_TYPES.put(".services.", "Service");
        PACKAGE_COMPONENT_TYPES.put(".controller.", "Controller");
        PACKAGE_COMPONENT_TYPES.put(".controllers.", "Controller");
        PACKAGE_COMPONENT_TYPES.put(".rest.", "Controller");
        PACKAGE_COMPONENT_TYPES.put(".config.", "Configuration");
        PACKAGE_COMPONENT_TYPES.put(".configuration.", "Configuration");
        PACKAGE_COMPONENT_TYPES.put(".security.", "Security");
        PACKAGE_COMPONENT_TYPES.put(".auth.", "Security");
    }
    
    // List of critical package identifiers
    private static final List<String> CRITICAL_PACKAGES = List.of(
        ".security.", 
        ".auth.", 
        ".core.",
        ".persistence."
    );
    
    @Autowired
    private ASTPropertyAnalyzer astPropertyAnalyzer;
    
    @Autowired
    private L3AgentPathConfig pathConfig;
    
    // Paths to scan for Java files
    @Value("${l3agent.config.scan-paths:${l3agent.paths.code-base:src/main/java}}")
    private String scanPaths;
    
    // Paths to scan for property files
    @Value("${l3agent.config.property-paths:${l3agent.paths.resources:src/main/resources}}")
    private String propertyPaths;
    
    @PostConstruct
    public void init() {
        // Use common configuration
        scanPaths = pathConfig.getCodeBasePaths();
        propertyPaths = pathConfig.getResourcePaths();
        
        logger.info("PropertyAnalyzer initialized with scan paths: {}, property paths: {}", 
                scanPaths, propertyPaths);
    }
    
    /**
     * Analyzes the impact of properties on the system.
     * 
     * @param propertyNames List of property names to analyze
     * @return Result of the impact analysis
     */
    public ConfigImpactResult analyzeImpact(List<String> propertyNames) {
        try {
            ConfigImpactResult result = new ConfigImpactResult();
            
            // Find property sources
            Map<String, List<PropertySource>> propertySources = findPropertySources(propertyNames);
            result.setPropertiesBySource(propertySources);
            
            // Find property references
            List<PropertyReference> references = new ArrayList<>();
            for (String propertyName : propertyNames) {
                List<PropertyReference> propertyRefs = findPropertyReferences(propertyName, true);
                references.addAll(propertyRefs);
            }
            
            result.setReferences(references);
            
            // Analyze severity and recommendations
            analyzeImpactSeverity(result);
            generateRecommendations(result);
            
            return result;
        } catch (Exception e) {
            logger.error("Error analyzing property impact", e);
            throw new RuntimeException("Failed to analyze property impact: " + e.getMessage(), e);
        }
    }
    
    /**
     * Finds sources of a set of properties.
     * 
     * @param propertyNames List of property names
     * @return Map of property sources to their properties
     */
    private Map<String, List<PropertySource>> findPropertySources(List<String> propertyNames) {
        Map<String, List<PropertySource>> result = new HashMap<>();
        
        // For each property path, find property files
        for (String path : propertyPaths.split(",")) {
            Path basePath = Paths.get(path);
            if (!Files.exists(basePath)) {
                continue;
            }
            
            try (Stream<Path> pathStream = Files.walk(basePath)) {
                List<Path> propertyFiles = pathStream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".properties") || 
                               p.toString().endsWith(".yml") || 
                               p.toString().endsWith(".yaml"))
                    .collect(Collectors.toList());
                
                for (Path propertyFile : propertyFiles) {
                    List<PropertySource> sources = extractPropertySources(propertyFile, propertyNames);
                    if (!sources.isEmpty()) {
                        result.put(propertyFile.toString(), sources);
                    }
                }
            } catch (IOException e) {
                logger.warn("Error scanning property path: {}", path, e);
            }
        }
        
        return result;
    }
    
    /**
     * Extracts property sources from a property file.
     * 
     * @param propertyFile The property file to scan
     * @param propertyNames List of property names to find
     * @return List of property sources found in the file
     */
    private List<PropertySource> extractPropertySources(Path propertyFile, List<String> propertyNames) {
        List<PropertySource> sources = new ArrayList<>();
        
        try {
            String content = Files.readString(propertyFile);
            
            // For each property, check if it's present in this file
            for (String propertyName : propertyNames) {
                // Handle wildcard patterns
                if (propertyName.endsWith("*")) {
                    String prefix = propertyName.substring(0, propertyName.length() - 1);
                    Pattern pattern = Pattern.compile("(?m)^\\s*" + Pattern.quote(prefix) + "[^\\s=:]+\\s*[=:]");
                    Matcher matcher = pattern.matcher(content);
                    
                    while (matcher.find()) {
                        String line = content.substring(matcher.start(), content.indexOf('\n', matcher.start()));
                        String actualPropertyName = extractActualPropertyName(line, prefix);
                        String value = extractPropertyValue(line);
                        
                        PropertySource source = new PropertySource(
                            actualPropertyName,
                            value,
                            propertyFile.getFileName().toString(),
                            matcher.start()
                        );
                        sources.add(source);
                    }
                } else {
                    // Exact property name match
                    Pattern pattern = Pattern.compile("(?m)^\\s*" + Pattern.quote(propertyName) + "\\s*[=:]");
                    Matcher matcher = pattern.matcher(content);
                    
                    if (matcher.find()) {
                        String line = content.substring(matcher.start(), content.indexOf('\n', matcher.start()));
                        String value = extractPropertyValue(line);
                        
                        PropertySource source = new PropertySource(
                            propertyName,
                            value,
                            propertyFile.getFileName().toString(),
                            matcher.start()
                        );
                        sources.add(source);
                    }
                }
            }
        } catch (IOException e) {
            logger.warn("Error reading property file: {}", propertyFile, e);
        }
        
        return sources;
    }
    
    /**
     * Extracts the actual property name from a line.
     */
    private String extractActualPropertyName(String line, String prefix) {
        int equalsIndex = line.indexOf('=');
        if (equalsIndex == -1) {
            equalsIndex = line.indexOf(':');
        }
        
        if (equalsIndex != -1) {
            return line.substring(0, equalsIndex).trim();
        }
        
        return prefix;
    }
    
    /**
     * Extracts the property value from a line.
     */
    private String extractPropertyValue(String line) {
        int equalsIndex = line.indexOf('=');
        if (equalsIndex == -1) {
            equalsIndex = line.indexOf(':');
        }
        
        if (equalsIndex != -1 && equalsIndex < line.length() - 1) {
            return line.substring(equalsIndex + 1).trim();
        }
        
        return "";
    }
    
    /**
     * Analyzes the impact of a configuration property.
     * 
     * @param propertyName The name of the property to analyze
     * @param includeSimilar Whether to include similar property names
     * @param includeIndirect Whether to include indirect impacts
     * @param includeDbConfig Whether to check for database-stored configuration
     * @return The analysis result
     * @throws IOException If an error occurs during file processing
     */
    public ConfigImpactResult analyzePropertyImpact(
            String propertyName, 
            boolean includeSimilar,
            boolean includeIndirect,
            boolean includeDbConfig) throws IOException {
        
        logger.info("Starting analysis of property: {}", propertyName);
        ConfigImpactResult result = new ConfigImpactResult(propertyName);
        
        // Find direct references to the property
        List<PropertyReference> directReferences = findPropertyReferences(propertyName, includeSimilar);
        
        logger.info("Found {} direct references to property {}", directReferences.size(), propertyName);
        
        // Add direct references to the result
        for (PropertyReference reference : directReferences) {
            result.addReference(reference);
        }
        
        // Find indirect references if requested
        if (includeIndirect && !directReferences.isEmpty()) {
            List<PropertyReference> indirectReferences = findIndirectReferences(directReferences);
            
            logger.info("Found {} indirect references related to property {}", 
                    indirectReferences.size(), propertyName);
            
            // Add indirect references to the result
            for (PropertyReference reference : indirectReferences) {
                // Mark as indirect reference
                reference.setReferenceType("Indirect Reference");
                result.addReference(reference);
            }
        }
        
        // Look for default values in property files
        String defaultValue = findDefaultValue(propertyName);
        if (defaultValue != null) {
            result.setDefaultValue(defaultValue);
            result.addAnalysisNote("Default value found in property files: " + defaultValue);
        } else {
            // Look for default values in @Value annotations
            defaultValue = findDefaultValueInCode(propertyName);
            if (defaultValue != null) {
                result.setDefaultValue(defaultValue);
                result.addAnalysisNote("Default value found in code: " + defaultValue);
            }
        }
        
        // Check for database-stored configuration if requested
        if (includeDbConfig) {
            checkForDatabaseConfiguration(result);
        }
        
        // Add additional analysis notes
        addAnalysisNotes(result);
        
        return result;
    }
    
    /**
     * Finds all references to a property in the codebase.
     * 
     * @param propertyName The property name to search for
     * @param includeSimilar Whether to include similar property names
     * @return List of property references
     * @throws IOException If an error occurs during file processing
     */
    private List<PropertyReference> findPropertyReferences(String propertyName, boolean includeSimilar) 
            throws IOException {
        List<PropertyReference> references = new ArrayList<>();
        Set<String> propertiesToSearch = new HashSet<>();
        
        // Add the exact property name
        propertiesToSearch.add(propertyName);
        
        // Add similar property names if requested
        if (includeSimilar) {
            // Add parent properties (e.g., for spring.datasource.url, add spring.datasource)
            String parentProperty = getParentProperty(propertyName);
            if (parentProperty != null) {
                propertiesToSearch.add(parentProperty);
            }
            
            // Add properties with the same prefix
            if (propertyName.contains(".")) {
                String prefix = propertyName.substring(0, propertyName.lastIndexOf(".") + 1);
                // We'll handle these when scanning files to avoid creating too many patterns
                propertiesToSearch.add(prefix + "*");
            }
        }
        
        // Create patterns for finding property references
        List<Pattern> patterns = createSearchPatterns(propertiesToSearch);
        
        // Scan all Java files in the configured scan paths
        for (String scanPath : scanPaths.split(",")) {
            Path path = Paths.get(scanPath.trim());
            if (Files.exists(path)) {
                try (Stream<Path> paths = Files.walk(path)) {
                    List<Path> javaFiles = paths
                            .filter(Files::isRegularFile)
                            .filter(p -> p.toString().endsWith(".java"))
                            .collect(Collectors.toList());
                    
                    for (Path file : javaFiles) {
                        // Skip already analyzed files for performance
                        if (analyzedFiles.contains(file.toString())) {
                            continue;
                        }
                        
                        analyzedFiles.add(file.toString());
                        
                        // Process the file and find references
                        List<PropertyReference> fileReferences = 
                                findReferencesInFile(file, patterns, propertiesToSearch);
                        
                        references.addAll(fileReferences);
                    }
                }
            }
        }
        
        return references;
    }
    
    /**
     * Creates regex patterns for searching property references.
     * 
     * @param properties The properties to create patterns for
     * @return List of compiled regex patterns
     */
    private List<Pattern> createSearchPatterns(Set<String> properties) {
        List<Pattern> patterns = new ArrayList<>();
        
        for (String property : properties) {
            // Skip wildcard properties, we'll handle them separately
            if (property.endsWith("*")) {
                continue;
            }
            
            // Create pattern for @Value annotation
            String valuePattern = VALUE_ANNOTATION_PATTERN.replace("[^:}]+", Pattern.quote(property));
            patterns.add(Pattern.compile(valuePattern));
            
            // Create pattern for Environment.getProperty
            String envPattern = ENVIRONMENT_GET_PATTERN.replace("[^\"]+", Pattern.quote(property));
            patterns.add(Pattern.compile(envPattern));
            
            // Create pattern for Properties.getProperty
            String propPattern = PROPERTIES_GET_PATTERN.replace("[^\"]+", Pattern.quote(property));
            patterns.add(Pattern.compile(propPattern));
            
            // For ConfigurationProperties, we need the prefix
            if (property.contains(".")) {
                String prefix = property.substring(0, property.lastIndexOf("."));
                String configPattern = CONFIG_PROPERTIES_PATTERN.replace("[^\"]+", Pattern.quote(prefix));
                patterns.add(Pattern.compile(configPattern));
            }
            
            // For ConditionalOnProperty
            String conditionalPattern = CONDITIONAL_PROPERTY_PATTERN.replace("[^\"]+", Pattern.quote(property));
            patterns.add(Pattern.compile(conditionalPattern));
        }
        
        return patterns;
    }
    
    /**
     * Finds references to properties in a single file.
     * 
     * @param file The file to scan
     * @param patterns The regex patterns to search for
     * @param properties The properties being searched for
     * @return List of property references found in the file
     * @throws IOException If an error occurs reading the file
     */
    private List<PropertyReference> findReferencesInFile(
            Path file, 
            List<Pattern> patterns,
            Set<String> properties) throws IOException {
        
        List<PropertyReference> references = new ArrayList<>();
        List<String> lines = Files.readAllLines(file);
        String content = String.join("\n", lines);
        
        // Get class info from the file content
        String className = extractClassName(content);
        if (className == null) {
            return references;
        }
        
        String componentType = determineComponentType(className, content);
        boolean isCritical = isCriticalComponent(className);
        
        // Check each pattern
        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(content);
            while (matcher.find()) {
                // Create a reference and add it to the list
                String patternStr = pattern.pattern();
                PropertyReference reference = createReference(
                        file, 
                        content, 
                        lines, 
                        matcher, 
                        className, 
                        componentType, 
                        isCritical, 
                        patternStr);
                
                if (reference != null) {
                    references.add(reference);
                    
                    // Cache the reference for future use
                    referenceCache.put(className + ":" + reference.getReferenceType(), reference);
                }
            }
        }
        
        // Check for wildcard property prefixes
        for (String property : properties) {
            if (property.endsWith("*")) {
                String prefix = property.substring(0, property.length() - 1);
                references.addAll(findWildcardReferences(file, content, lines, className, componentType, isCritical, prefix));
            }
        }
        
        return references;
    }
    
    /**
     * Finds references matching a wildcard property prefix.
     */
    private List<PropertyReference> findWildcardReferences(
            Path file, 
            String content, 
            List<String> lines, 
            String className, 
            String componentType, 
            boolean isCritical, 
            String prefix) {
        
        List<PropertyReference> references = new ArrayList<>();
        
        // Look for @Value annotations with the prefix
        Pattern valuePattern = Pattern.compile("@Value\\s*\\(\\s*\"\\$\\{(" + Pattern.quote(prefix) + "[^:}]+)(?::([^}]*))?\\}\"\\s*\\)");
        Matcher valueMatcher = valuePattern.matcher(content);
        
        while (valueMatcher.find()) {
            PropertyReference reference = createReference(
                    file,
                    content,
                    lines,
                    valueMatcher,
                    className,
                    componentType,
                    isCritical,
                    VALUE_ANNOTATION_PATTERN);
            
            if (reference != null) {
                references.add(reference);
            }
        }
        
        // Look for Environment.getProperty with the prefix
        Pattern envPattern = Pattern.compile("environment\\.getProperty\\s*\\(\\s*\"(" + Pattern.quote(prefix) + "[^\"]+)\"");
        Matcher envMatcher = envPattern.matcher(content);
        
        while (envMatcher.find()) {
            PropertyReference reference = createReference(
                    file,
                    content,
                    lines,
                    envMatcher,
                    className,
                    componentType,
                    isCritical,
                    ENVIRONMENT_GET_PATTERN);
            
            if (reference != null) {
                references.add(reference);
            }
        }
        
        return references;
    }
    
    /**
     * Creates a property reference from a regex match.
     */
    private PropertyReference createReference(
            Path file,
            String content, 
            List<String> lines, 
            Matcher matcher, 
            String className, 
            String componentType, 
            boolean isCritical, 
            String patternType) {
        
        try {
            // Extract the property name from the match
            String matchedProperty = matcher.group(1);
            
            // Determine reference type based on the pattern
            String referenceType;
            if (patternType.equals(VALUE_ANNOTATION_PATTERN)) {
                referenceType = "@Value";
            } else if (patternType.equals(ENVIRONMENT_GET_PATTERN)) {
                referenceType = "Environment.getProperty";
            } else if (patternType.equals(PROPERTIES_GET_PATTERN)) {
                referenceType = "Properties.get";
            } else if (patternType.equals(CONFIG_PROPERTIES_PATTERN)) {
                referenceType = "@ConfigurationProperties";
            } else if (patternType.equals(CONDITIONAL_PROPERTY_PATTERN)) {
                referenceType = "@ConditionalOnProperty";
            } else {
                referenceType = "Unknown";
            }
            
            // Create the reference
            PropertyReference reference = new PropertyReference(className, componentType)
                    .setCriticalComponent(isCritical)
                    .setReferenceType(referenceType);
            
            // Find line number
            int position = matcher.start();
            int lineNumber = countLines(content.substring(0, position)) + 1;
            reference.setLineNumber(lineNumber);
            
            // Determine field and method names
            if (referenceType.equals("@Value")) {
                // Find the field name from context after the @Value annotation
                String fieldDeclaration = findFieldDeclaration(lines, lineNumber);
                if (fieldDeclaration != null) {
                    reference.setFieldName(extractFieldName(fieldDeclaration));
                }
            } else if (referenceType.equals("Environment.getProperty") || 
                       referenceType.equals("Properties.get")) {
                // Find the method name from the surrounding context
                String methodName = findMethodName(content, matcher.start());
                if (methodName != null) {
                    reference.setMethodName(methodName);
                }
            }
            
            // Determine access pattern
            if (patternType.equals(VALUE_ANNOTATION_PATTERN) && matcher.groupCount() >= 2 && matcher.group(2) != null) {
                // Has default value
                reference.setAccessPattern("fallback");
                reference.setNotes("Default value: " + matcher.group(2));
            } else if (content.substring(Math.max(0, matcher.start() - 50), 
                                       Math.min(content.length(), matcher.start())).contains("if") ||
                      content.substring(matcher.end(), 
                                       Math.min(content.length(), matcher.end() + 50)).contains("?")) {
                // Conditional usage
                reference.setAccessPattern("conditional");
            } else {
                reference.setAccessPattern("direct");
            }
            
            return reference;
        } catch (Exception e) {
            logger.warn("Error creating property reference for {} in {}: {}", 
                    matcher.group(), file, e.getMessage());
            return null;
        }
    }
    
    /**
     * Finds indirect references to a property through the classes that directly reference it.
     * 
     * @param directReferences The direct references to the property
     * @return List of indirect property references
     * @throws IOException If an error occurs during file processing
     */
    private List<PropertyReference> findIndirectReferences(List<PropertyReference> directReferences) 
            throws IOException {
        List<PropertyReference> indirectReferences = new ArrayList<>();
        Set<String> processedClasses = new HashSet<>();
        
        // For each direct reference, find classes that reference it
        for (PropertyReference direct : directReferences) {
            String className = direct.getClassName();
            
            // Skip if already processed to avoid cycles
            if (processedClasses.contains(className)) {
                continue;
            }
            
            processedClasses.add(className);
            
            // Find classes that reference this class
            List<PropertyReference> referencingClasses = findClassReferences(className);
            
            // Filter out duplicates and direct references
            for (PropertyReference ref : referencingClasses) {
                if (!processedClasses.contains(ref.getClassName()) && 
                    directReferences.stream().noneMatch(d -> d.getClassName().equals(ref.getClassName()))) {
                    indirectReferences.add(ref);
                }
            }
        }
        
        return indirectReferences;
    }
    
    /**
     * Finds classes that reference a given class.
     * 
     * @param targetClassName The class name to find references to
     * @return List of property references to classes that reference the target class
     * @throws IOException If an error occurs during file processing
     */
    private List<PropertyReference> findClassReferences(String targetClassName) throws IOException {
        List<PropertyReference> references = new ArrayList<>();
        String simpleClassName = getSimpleClassName(targetClassName);
        
        // Pattern for finding references to the class
        Pattern classPattern = Pattern.compile("\\b" + Pattern.quote(simpleClassName) + "\\b");
        
        // Scan all Java files
        for (String scanPath : scanPaths.split(",")) {
            Path path = Paths.get(scanPath.trim());
            if (Files.exists(path)) {
                try (Stream<Path> paths = Files.walk(path)) {
                    List<Path> javaFiles = paths
                            .filter(Files::isRegularFile)
                            .filter(p -> p.toString().endsWith(".java"))
                            .collect(Collectors.toList());
                    
                    for (Path file : javaFiles) {
                        String content = Files.readString(file);
                        
                        // Skip files that don't mention the class at all
                        if (!content.contains(simpleClassName)) {
                            continue;
                        }
                        
                        // Extract class information
                        String className = extractClassName(content);
                        if (className == null || className.equals(targetClassName)) {
                            continue;
                        }
                        
                        // Check if this class references the target class
                        Matcher matcher = classPattern.matcher(content);
                        if (matcher.find()) {
                            String componentType = determineComponentType(className, content);
                            boolean isCritical = isCriticalComponent(className);
                            
                            // Create a reference
                            PropertyReference reference = new PropertyReference(className, componentType)
                                    .setCriticalComponent(isCritical)
                                    .setReferenceType("ClassDependency")
                                    .setNotes("References " + targetClassName);
                            
                            references.add(reference);
                        }
                    }
                }
            }
        }
        
        return references;
    }
    
    /**
     * Finds the default value for a property in property files.
     * 
     * @param propertyName The property name
     * @return The default value or null if not found
     * @throws IOException If an error occurs during file processing
     */
    private String findDefaultValue(String propertyName) throws IOException {
        // Pattern for finding the property in .properties files
        Pattern propertiesPattern = Pattern.compile("^\\s*" + Pattern.quote(propertyName) + "\\s*=\\s*(.*)$", 
                Pattern.MULTILINE);
        
        // Pattern for finding the property in .yml files
        String ymlKey = propertyName.substring(propertyName.lastIndexOf('.') + 1);
        Pattern ymlPattern = Pattern.compile("\\s+" + Pattern.quote(ymlKey) + ":\\s+(.*)$", 
                Pattern.MULTILINE);
        
        // Scan all property files
        for (String propPath : propertyPaths.split(",")) {
            Path path = Paths.get(propPath.trim());
            if (Files.exists(path)) {
                try (Stream<Path> paths = Files.walk(path)) {
                    List<Path> propertyFiles = paths
                            .filter(Files::isRegularFile)
                            .filter(p -> p.toString().endsWith(".properties") || 
                                         p.toString().endsWith(".yml") || 
                                         p.toString().endsWith(".yaml"))
                            .collect(Collectors.toList());
                    
                    for (Path file : propertyFiles) {
                        String content = Files.readString(file);
                        
                        // Use appropriate pattern based on file extension
                        Pattern pattern = file.toString().endsWith(".properties") ? 
                                propertiesPattern : ymlPattern;
                        
                        Matcher matcher = pattern.matcher(content);
                        if (matcher.find()) {
                            return matcher.group(1).trim();
                        }
                    }
                }
            }
        }
        
        return null;
    }
    
    /**
     * Finds default values specified in @Value annotations.
     * 
     * @param propertyName The property name
     * @return The default value or null if not found
     */
    private String findDefaultValueInCode(String propertyName) {
        // Pattern to find @Value annotations with default values
        Pattern pattern = Pattern.compile("@Value\\s*\\(\\s*\"\\$\\{" + 
                Pattern.quote(propertyName) + ":([^}]*)\\}\"\\s*\\)");
        
        for (PropertyReference reference : referenceCache.values()) {
            if (reference.getReferenceType().equals("@Value") && 
                reference.getNotes() != null && 
                reference.getNotes().startsWith("Default value:")) {
                
                return reference.getNotes().substring("Default value:".length()).trim();
            }
        }
        
        return null;
    }
    
    /**
     * Checks for potential database-stored configuration related to the property.
     * 
     * @param result The configuration impact result to update
     */
    private void checkForDatabaseConfiguration(ConfigImpactResult result) throws IOException {
        String propertyName = result.getPropertyName();
        String simplePropertyName = getSimpleName(propertyName);
        
        // Look for repository classes that might store configuration
        Pattern dbConfigPattern = Pattern.compile(DB_CONFIG_PATTERN);
        
        for (String scanPath : scanPaths.split(",")) {
            Path path = Paths.get(scanPath.trim());
            if (Files.exists(path)) {
                try (Stream<Path> paths = Files.walk(path)) {
                    List<Path> javaFiles = paths
                            .filter(Files::isRegularFile)
                            .filter(p -> p.toString().endsWith(".java"))
                            .filter(p -> p.toString().contains("Repository") || 
                                         p.toString().contains("Config") || 
                                         p.toString().contains("Setting"))
                            .collect(Collectors.toList());
                    
                    for (Path file : javaFiles) {
                        String content = Files.readString(file);
                        
                        // Check if this is a repository interface
                        if (content.contains("interface") && content.contains(REPOSITORY_PATTERN)) {
                            // Extract class name
                            String className = extractClassName(content);
                            if (className == null) {
                                continue;
                            }
                            
                            // Check if it's a configuration repository
                            Matcher matcher = dbConfigPattern.matcher(className);
                            if (matcher.find() || 
                                className.contains("Config") || 
                                className.contains("Setting") || 
                                className.contains("Option")) {
                                
                                // Check for methods that might retrieve configuration
                                if ((content.contains("findBy") && (
                                     content.contains("Name") || 
                                     content.contains("Key") || 
                                     content.contains(simplePropertyName))) ||
                                    content.contains("getConfiguration") || 
                                    content.contains("getSettings") || 
                                    content.contains("getOptions")) {
                                    
                                    // Found potential database configuration
                                    result.addDatabaseConfigSource(
                                            className, 
                                            "What is the current value of " + propertyName + 
                                            " stored in the database? Check the settings/configuration table.");
                                    
                                    // Add a note about this finding
                                    result.addAnalysisNote(
                                            "Potential database-stored configuration detected in " + 
                                            className + ". The property may be overridden by database values.");
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Adds additional analysis notes based on the findings.
     * 
     * @param result The configuration impact result to update
     */
    private void addAnalysisNotes(ConfigImpactResult result) {
        // Add notes about impact severity
        switch (result.getImpactSeverity()) {
            case HIGH:
                result.addAnalysisNote(
                        "This property has HIGH impact severity because it affects critical components " +
                        "or security-related functionality. Changes should be carefully tested.");
                break;
            case MEDIUM:
                result.addAnalysisNote(
                        "This property has MEDIUM impact severity because it affects multiple components. " +
                        "Changes may require testing across these components.");
                break;
            case LOW:
                result.addAnalysisNote(
                        "This property has LOW impact severity with limited scope. " + 
                        "Changes are likely to have minimal side effects.");
                break;
            default:
                result.addAnalysisNote(
                        "Unable to determine impact severity due to insufficient references.");
                break;
        }
        
        // Add notes about property type based on name
        if (result.getPropertyName().contains("url") || 
            result.getPropertyName().contains("uri") || 
            result.getPropertyName().contains("endpoint")) {
            result.addAnalysisNote(
                    "This appears to be a connection URL/endpoint. " +
                    "Changes may affect connectivity to external systems.");
        } else if (result.getPropertyName().contains("timeout")) {
            result.addAnalysisNote(
                    "This appears to be a timeout setting. " +
                    "Decreasing it may lead to premature timeouts, increasing it may delay error detection.");
        } else if (result.getPropertyName().contains("enable") || 
                   result.getPropertyName().contains("disable")) {
            result.addAnalysisNote(
                    "This appears to be a feature toggle. " +
                    "Changing it will enable/disable functionality.");
        } else if (result.getPropertyName().contains("password") || 
                   result.getPropertyName().contains("secret") || 
                   result.getPropertyName().contains("key")) {
            result.addAnalysisNote(
                    "This appears to be a sensitive credential. " +
                    "Handle with care and ensure it's stored securely.");
        }
    }
    
    /**
     * Returns the parent property of a property.
     * For example, for "spring.datasource.url", returns "spring.datasource".
     * 
     * @param property The property name
     * @return The parent property or null if there's no parent
     */
    private String getParentProperty(String property) {
        if (property.contains(".")) {
            return property.substring(0, property.lastIndexOf('.'));
        }
        return null;
    }
    
    /**
     * Extracts the fully qualified class name from Java file content.
     * 
     * @param content The file content
     * @return The fully qualified class name or null if not found
     */
    private String extractClassName(String content) {
        // Extract package name
        Pattern packagePattern = Pattern.compile("package\\s+([^;]+);");
        Matcher packageMatcher = packagePattern.matcher(content);
        String packageName = packageMatcher.find() ? packageMatcher.group(1).trim() : "";
        
        // Extract class/interface name
        Pattern classPattern = Pattern.compile("(?:public|private|protected)?\\s+(?:class|interface|enum)\\s+([^\\s<{]+)");
        Matcher classMatcher = classPattern.matcher(content);
        if (classMatcher.find()) {
            String className = classMatcher.group(1).trim();
            return packageName.isEmpty() ? className : packageName + "." + className;
        }
        
        return null;
    }
    
    /**
     * Determines the component type based on class name and content.
     * 
     * @param className The class name
     * @param content The file content
     * @return The component type
     */
    private String determineComponentType(String className, String content) {
        // Check annotations first
        if (content.contains("@Controller") || content.contains("@RestController")) {
            return "Controller";
        } else if (content.contains("@Service")) {
            return "Service";
        } else if (content.contains("@Repository")) {
            return "Repository";
        } else if (content.contains("@Component")) {
            return "Component";
        } else if (content.contains("@Configuration")) {
            return "Configuration";
        }
        
        // Check class name
        if (className.endsWith("Controller")) {
            return "Controller";
        } else if (className.endsWith("Service")) {
            return "Service";
        } else if (className.endsWith("Repository") || className.endsWith("DAO")) {
            return "Repository";
        } else if (className.endsWith("Config") || className.endsWith("Configuration")) {
            return "Configuration";
        }
        
        // Check package name
        for (Map.Entry<String, String> entry : PACKAGE_COMPONENT_TYPES.entrySet()) {
            if (className.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        
        // Check implements/extends
        if (content.contains("implements") && content.contains("Repository")) {
            return "Repository";
        } else if (content.contains("implements") && content.contains("Service")) {
            return "Service";
        }
        
        return "Other";
    }
    
    /**
     * Determines if a component is critical based on its package name.
     * 
     * @param className The fully qualified class name
     * @return true if it's a critical component
     */
    private boolean isCriticalComponent(String className) {
        for (String criticalPackage : CRITICAL_PACKAGES) {
            if (className.contains(criticalPackage)) {
                return true;
            }
        }
        
        // Security and auth components are critical
        return className.contains("Security") || 
               className.contains("Auth") || 
               className.contains("JWT") || 
               className.contains("Login") || 
               className.contains("Permission");
    }
    
    /**
     * Gets the simple class name from a fully qualified class name.
     * 
     * @param className The fully qualified class name
     * @return The simple class name
     */
    private String getSimpleClassName(String className) {
        if (className.contains(".")) {
            return className.substring(className.lastIndexOf('.') + 1);
        }
        return className;
    }
    
    /**
     * Gets a simpler name from a property name.
     * For example, converts "spring.datasource.url" to "url".
     * 
     * @param propertyName The property name
     * @return The simple name
     */
    private String getSimpleName(String propertyName) {
        if (propertyName.contains(".")) {
            return propertyName.substring(propertyName.lastIndexOf('.') + 1);
        }
        return propertyName;
    }
    
    /**
     * Counts the number of lines in a string.
     * 
     * @param text The text to count lines in
     * @return The number of lines
     */
    private int countLines(String text) {
        return (int) text.chars().filter(ch -> ch == '\n').count();
    }
    
    /**
     * Finds the field declaration following an annotation.
     * 
     * @param lines The file lines
     * @param annotationLineNumber The line number of the annotation
     * @return The field declaration or null if not found
     */
    private String findFieldDeclaration(List<String> lines, int annotationLineNumber) {
        if (annotationLineNumber >= lines.size()) {
            return null;
        }
        
        // Check the next few lines for the field declaration
        for (int i = annotationLineNumber; i < Math.min(lines.size(), annotationLineNumber + 5); i++) {
            String line = lines.get(i);
            if (line.contains(";") && 
                !line.trim().startsWith("@") && 
                (line.contains("private") || line.contains("protected") || line.contains("public"))) {
                return line;
            }
        }
        
        return null;
    }
    
    /**
     * Extracts the field name from a field declaration.
     * 
     * @param declaration The field declaration
     * @return The field name
     */
    private String extractFieldName(String declaration) {
        // Simple regex to extract the field name
        Pattern pattern = Pattern.compile("(?:private|protected|public)\\s+\\S+\\s+(\\w+)\\s*;");
        Matcher matcher = pattern.matcher(declaration);
        if (matcher.find()) {
            return matcher.group(1);
        }
        
        // Fallback - look for any identifier before semicolon
        pattern = Pattern.compile("\\s+(\\w+)\\s*;");
        matcher = pattern.matcher(declaration);
        if (matcher.find()) {
            return matcher.group(1);
        }
        
        return null;
    }
    
    /**
     * Finds the method name containing a certain position in the file.
     * 
     * @param content The file content
     * @param position The position to find the method for
     * @return The method name or null if not found
     */
    private String findMethodName(String content, int position) {
        // Find the last method declaration before the position
        Pattern methodPattern = Pattern.compile("(?:public|private|protected)\\s+[\\w<>\\[\\],\\s]+\\s+(\\w+)\\s*\\([^)]*\\)\\s*\\{");
        Matcher matcher = methodPattern.matcher(content.substring(0, position));
        
        String methodName = null;
        while (matcher.find()) {
            methodName = matcher.group(1);
        }
        
        return methodName;
    }
    
    /**
     * Analyzes the impact severity of configuration changes.
     * 
     * @param result The configuration impact result to analyze
     */
    private void analyzeImpactSeverity(ConfigImpactResult result) {
        // Impact severity is already calculated by ConfigImpactResult.updateSeverity()
        // based on various factors like critical components and usage patterns
        
        // Here we can add additional analysis based on property context
        List<PropertyReference> references = result.getReferences();
        
        // Check for properties used in critical contexts
        boolean usedInConditionals = references.stream()
            .anyMatch(ref -> ref.getContext() != null && ref.getContext().getConditionalUses() > 0);
        
        boolean usedInLoops = references.stream()
            .anyMatch(ref -> ref.getContext() != null && ref.getContext().getUsageInLoops() > 0);
        
        boolean usedInCriticalComponent = references.stream()
            .anyMatch(PropertyReference::isCriticalComponent);
        
        if (usedInConditionals || usedInLoops || usedInCriticalComponent) {
            result.addAnalysisNote("This property is used in critical contexts (conditionals, loops, or critical components)");
        }
        
        // Check for property scope
        long classCount = references.stream()
            .map(PropertyReference::getClassName)
            .distinct()
            .count();
        
        if (classCount > 5) {
            result.addAnalysisNote("This property has a wide impact scope, affecting " + classCount + " different classes");
        }
    }
    
    /**
     * Generates recommendations based on the analysis.
     * 
     * @param result The configuration impact result
     */
    private void generateRecommendations(ConfigImpactResult result) {
        List<PropertyReference> references = result.getReferences();
        
        // Check for properties used in conditionals
        boolean usedInConditionals = references.stream()
            .anyMatch(ref -> ref.getContext() != null && ref.getContext().getConditionalUses() > 0);
        
        if (usedInConditionals) {
            result.addRecommendation(
                "Add Default Value", 
                "This property is used in conditional logic. Consider adding a default value to prevent runtime issues if the property is missing."
            );
        }
        
        // Check for properties that are modified after injection
        boolean isModifiedAfterInjection = references.stream()
            .anyMatch(ref -> ref.getContext() != null && ref.getContext().getMutatingOperations() > 0);
        
        if (isModifiedAfterInjection) {
            result.addRecommendation(
                "Consider Immutability", 
                "This property is modified after injection. Consider making it immutable or applying post-processing in a configuration class."
            );
        }
        
        // Check for database properties
        boolean containsDatabaseProperty = references.stream()
            .anyMatch(ref -> ref instanceof DatabaseConfigReference);
        
        if (containsDatabaseProperty) {
            result.addRecommendation(
                "Secure Database Configuration", 
                "This appears to be a database configuration property. Consider using environment variables or encrypted configuration for sensitive values."
            );
        }
        
        // General best practices
        result.addRecommendation(
            "Configuration Documentation", 
            "Document this property's purpose, accepted values, and impact of changes to improve maintainability."
        );
        
        // If it's a widely used property
        long classCount = references.stream()
            .map(PropertyReference::getClassName)
            .distinct()
            .count();
        
        if (classCount > 3) {
            result.addRecommendation(
                "Configuration Centralization", 
                "This property is used in " + classCount + " classes. Consider centralizing access through a configuration service class."
            );
        }
    }
} 