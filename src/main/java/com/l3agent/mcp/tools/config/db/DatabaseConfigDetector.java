package com.l3agent.mcp.tools.config.db;

import com.l3agent.mcp.tools.config.model.DatabaseConfigReference;
import com.l3agent.mcp.tools.config.model.PropertyReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Specialized detector for database configuration properties.
 * Identifies database connection and configuration properties with additional context.
 * 
 * This detector enhances property references by converting them to DatabaseConfigReference
 * instances with additional metadata about database properties, and also identifies
 * database configuration stored in property files.
 */
@Component
public class DatabaseConfigDetector {
    
    private static final Logger logger = LoggerFactory.getLogger(DatabaseConfigDetector.class);
    
    // Database property prefixes to check
    private static final List<String> DB_PROPERTY_PREFIXES = List.of(
        "spring.datasource",
        "spring.jpa",
        "spring.data.jdbc",
        "spring.data.mongodb",
        "spring.data.redis",
        "spring.data.cassandra",
        "spring.data.elasticsearch",
        "hibernate",
        "jdbc",
        "database",
        "db"
    );
    
    // Database technologies by connection string patterns
    private static final Map<String, String> DB_TECH_PATTERNS = new HashMap<>();
    static {
        DB_TECH_PATTERNS.put("jdbc:mysql", "MySQL");
        DB_TECH_PATTERNS.put("jdbc:postgresql", "PostgreSQL");
        DB_TECH_PATTERNS.put("jdbc:oracle", "Oracle");
        DB_TECH_PATTERNS.put("jdbc:sqlserver", "SQL Server");
        DB_TECH_PATTERNS.put("jdbc:h2", "H2");
        DB_TECH_PATTERNS.put("jdbc:db2", "DB2");
        DB_TECH_PATTERNS.put("jdbc:sqlite", "SQLite");
        DB_TECH_PATTERNS.put("mongodb://", "MongoDB");
        DB_TECH_PATTERNS.put("redis://", "Redis");
    }
    
    // Patterns for different types of database properties
    private static final Pattern CONNECTION_URL_PATTERN = 
        Pattern.compile("(?:url|jdbc-url|connection-url|connection-string)\\s*[=:]\\s*\"?([^\"\\s]+)\"?");
    
    private static final Pattern USERNAME_PATTERN = 
        Pattern.compile("(?:username|user|db-user)\\s*[=:]\\s*\"?([^\"\\s]+)\"?");
    
    private static final Pattern PASSWORD_PATTERN = 
        Pattern.compile("(?:password|db-password)\\s*[=:]\\s*\"?([^\"\\s]+)\"?");
    
    private static final Pattern DRIVER_PATTERN = 
        Pattern.compile("(?:driver-class-name|driver|driverClassName)\\s*[=:]\\s*\"?([^\"\\s]+)\"?");
    
    @Value("${l3agent.config.property-paths:src/main/resources}")
    private String propertyPaths;
    
    /**
     * Detects database configuration properties and returns enhanced references.
     * 
     * @param propertyReferences List of property references found by general analysis
     * @return Enhanced list with database-specific details
     */
    public List<PropertyReference> detectDatabaseConfig(List<PropertyReference> propertyReferences) {
        try {
            // First find any database configuration files
            List<Path> dbConfigFiles = findDatabaseConfigFiles();
            
            // Create a new list for enhanced references
            List<PropertyReference> enhancedReferences = new ArrayList<>(propertyReferences.size());
            
            // Enhance existing DB-related property references
            for (PropertyReference ref : propertyReferences) {
                if (isDatabaseProperty(ref)) {
                    enhancedReferences.add(enhanceDatabaseReference(ref));
                } else {
                    enhancedReferences.add(ref);
                }
            }
            
            // Add additional database references found in config files
            List<PropertyReference> additionalRefs = extractReferencesFromDbConfigFiles(dbConfigFiles);
            enhancedReferences.addAll(additionalRefs);
            
            return enhancedReferences;
        } catch (Exception e) {
            logger.error("Error detecting database configuration", e);
            return propertyReferences; // Return original list if detection fails
        }
    }
    
    /**
     * Checks if a property is related to database configuration.
     */
    private boolean isDatabaseProperty(PropertyReference ref) {
        String propertyName = ref.getPropertyName();
        if (propertyName == null) {
            return false;
        }
        
        // Check if the property starts with any of the database prefixes
        for (String prefix : DB_PROPERTY_PREFIXES) {
            if (propertyName.startsWith(prefix)) {
                return true;
            }
        }
        
        // Look for database keywords
        return propertyName.contains("database") || 
               propertyName.contains("datasource") || 
               propertyName.contains("db") || 
               propertyName.contains("jdbc") || 
               propertyName.contains("jpa") || 
               propertyName.contains("hibernate");
    }
    
    /**
     * Enhances a database property reference with additional context.
     * 
     * @param ref The property reference to enhance
     * @return Enhanced database config reference
     */
    private PropertyReference enhanceDatabaseReference(PropertyReference ref) {
        if (!(ref instanceof DatabaseConfigReference)) {
            // Convert to DatabaseConfigReference
            DatabaseConfigReference dbRef = new DatabaseConfigReference(ref);
            
            // Detect database type
            String propertyName = ref.getPropertyName();
            if (propertyName != null) {
                if (propertyName.contains("url") || propertyName.contains("jdbc")) {
                    dbRef.setPropertyType("connection");
                    // Try to extract DB technology from value
                    if (ref.getValue() != null) {
                        String dbTech = identifyDatabaseTech(ref.getValue());
                        if (dbTech != null) {
                            dbRef.setDatabaseType(dbTech);
                        }
                    }
                } else if (propertyName.contains("username") || propertyName.contains("user")) {
                    dbRef.setPropertyType("authentication");
                } else if (propertyName.contains("password")) {
                    dbRef.setPropertyType("authentication");
                    dbRef.setIsSensitive(true);
                } else if (propertyName.contains("driver")) {
                    dbRef.setPropertyType("driver");
                } else if (propertyName.contains("pool") || propertyName.contains("connection")) {
                    dbRef.setPropertyType("pool-config");
                } else if (propertyName.contains("dialect")) {
                    dbRef.setPropertyType("dialect");
                } else if (propertyName.contains("jpa") || propertyName.contains("hibernate")) {
                    dbRef.setPropertyType("orm-config");
                }
            }
            
            // Return the enhanced reference
            return dbRef;
        }
        
        // If already a DatabaseConfigReference, return as is
        return ref;
    }
    
    /**
     * Finds database configuration files in the project.
     */
    private List<Path> findDatabaseConfigFiles() throws IOException {
        List<Path> dbConfigFiles = new ArrayList<>();
        
        for (String pathStr : propertyPaths.split(",")) {
            Path basePath = Paths.get(pathStr.trim());
            if (!Files.exists(basePath)) {
                continue;
            }
            
            try (Stream<Path> paths = Files.walk(basePath)) {
                List<Path> configFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        String fileName = path.getFileName().toString().toLowerCase();
                        return (fileName.endsWith(".properties") || 
                                fileName.endsWith(".yml") || 
                                fileName.endsWith(".yaml")) &&
                               (fileName.contains("database") || 
                                fileName.contains("datasource") || 
                                fileName.contains("persistence") || 
                                fileName.contains("jdbc") || 
                                fileName.contains("jpa"));
                    })
                    .collect(Collectors.toList());
                
                dbConfigFiles.addAll(configFiles);
            }
        }
        
        return dbConfigFiles;
    }
    
    /**
     * Extracts database property references from configuration files.
     */
    private List<PropertyReference> extractReferencesFromDbConfigFiles(List<Path> dbConfigFiles) {
        List<PropertyReference> references = new ArrayList<>();
        
        for (Path file : dbConfigFiles) {
            try {
                String content = Files.readString(file);
                String fileName = file.getFileName().toString();
                
                // Extract database connection details
                Matcher urlMatcher = CONNECTION_URL_PATTERN.matcher(content);
                Matcher userMatcher = USERNAME_PATTERN.matcher(content);
                Matcher passwordMatcher = PASSWORD_PATTERN.matcher(content);
                Matcher driverMatcher = DRIVER_PATTERN.matcher(content);
                
                // Extract connection URL
                if (urlMatcher.find()) {
                    String url = urlMatcher.group(1);
                    String dbType = identifyDatabaseTech(url);
                    
                    DatabaseConfigReference ref = new DatabaseConfigReference(
                        "Config File: " + fileName, 
                        "Configuration"
                    );
                    ref.setPropertyName("connection.url");
                    ref.setPropertyType("connection");
                    ref.setValue(url);
                    ref.setDatabaseType(dbType != null ? dbType : "Unknown");
                    ref.setReferenceType("Property File");
                    ref.setIsCritical(true);
                    
                    references.add(ref);
                }
                
                // Extract username
                if (userMatcher.find()) {
                    String username = userMatcher.group(1);
                    
                    DatabaseConfigReference ref = new DatabaseConfigReference(
                        "Config File: " + fileName, 
                        "Configuration"
                    );
                    ref.setPropertyName("connection.username");
                    ref.setPropertyType("authentication");
                    ref.setValue(username);
                    ref.setReferenceType("Property File");
                    
                    references.add(ref);
                }
                
                // Extract password
                if (passwordMatcher.find()) {
                    DatabaseConfigReference ref = new DatabaseConfigReference(
                        "Config File: " + fileName, 
                        "Configuration"
                    );
                    ref.setPropertyName("connection.password");
                    ref.setPropertyType("authentication");
                    ref.setReferenceType("Property File");
                    ref.setIsSensitive(true);
                    ref.setIsCritical(true);
                    
                    references.add(ref);
                }
                
                // Extract driver
                if (driverMatcher.find()) {
                    String driver = driverMatcher.group(1);
                    
                    DatabaseConfigReference ref = new DatabaseConfigReference(
                        "Config File: " + fileName, 
                        "Configuration"
                    );
                    ref.setPropertyName("connection.driver");
                    ref.setPropertyType("driver");
                    ref.setValue(driver);
                    ref.setReferenceType("Property File");
                    
                    references.add(ref);
                }
            } catch (Exception e) {
                logger.warn("Error processing database config file {}: {}", file, e.getMessage());
            }
        }
        
        return references;
    }
    
    /**
     * Identifies database technology from a connection string.
     */
    private String identifyDatabaseTech(String connectionString) {
        if (connectionString == null) {
            return null;
        }
        
        for (Map.Entry<String, String> entry : DB_TECH_PATTERNS.entrySet()) {
            if (connectionString.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        
        return null;
    }
} 