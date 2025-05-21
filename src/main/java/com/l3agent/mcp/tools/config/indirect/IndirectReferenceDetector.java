package com.l3agent.mcp.tools.config.indirect;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.l3agent.mcp.tools.config.model.PropertyReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Detects indirect references to configuration properties through dependency analysis.
 * Tracks how configuration values propagate through the codebase via method calls
 * and dependency injection.
 */
@Component
public class IndirectReferenceDetector {
    
    private static final Logger logger = LoggerFactory.getLogger(IndirectReferenceDetector.class);
    
    @Value("${l3agent.config.scan-paths:src/main/java}")
    private String scanPaths;
    
    // Maps class names to the classes they depend on
    private final Map<String, Set<String>> classDependencyMap = new HashMap<>();
    
    // Maps method signatures to their called methods
    private final Map<String, Set<String>> methodCallGraph = new HashMap<>();
    
    // Maps field names to their source classes
    private final Map<String, String> fieldToClassMap = new HashMap<>();
    
    // Maps property references to indirectly dependent classes
    private final Map<PropertyReference, Set<String>> propertyDependentsMap = new HashMap<>();
    
    /**
     * Analyzes the codebase to detect indirect property references.
     * 
     * @param directReferences List of direct property references found by the primary analysis
     * @return Enhanced list including indirect references
     */
    public List<PropertyReference> detectIndirectReferences(List<PropertyReference> directReferences) {
        try {
            // Build the dependency graphs
            buildDependencyGraphs();
            
            // Find classes that directly use properties
            Set<String> directUsageClasses = directReferences.stream()
                .map(PropertyReference::getClassName)
                .collect(Collectors.toSet());
            
            List<PropertyReference> allReferences = new ArrayList<>(directReferences);
            
            // For each direct reference, find dependent classes
            for (PropertyReference directRef : directReferences) {
                Set<String> indirectClasses = findDependentClasses(directRef.getClassName());
                // Remove direct classes
                indirectClasses.removeAll(directUsageClasses);
                
                // Store the mapping
                propertyDependentsMap.put(directRef, indirectClasses);
                
                // Create indirect references
                for (String indirectClass : indirectClasses) {
                    PropertyReference indirectRef = new PropertyReference(indirectClass, determineComponentType(indirectClass));
                    indirectRef.setPropertyName(directRef.getPropertyName());
                    indirectRef.setReferenceType("Indirect Reference");
                    indirectRef.setNotes("Indirectly depends on " + directRef.getClassName());
                    indirectRef.setAccessPattern("indirect");
                    
                    allReferences.add(indirectRef);
                }
            }
            
            return allReferences;
        } catch (Exception e) {
            logger.error("Error detecting indirect references", e);
            return directReferences; // Return original references if error occurs
        }
    }
    
    /**
     * Builds dependency graphs by analyzing the codebase.
     */
    private void buildDependencyGraphs() {
        JavaParser javaParser = new JavaParser();
        
        try {
            for (String scanPath : scanPaths.split(",")) {
                Path path = Paths.get(scanPath.trim());
                if (!Files.exists(path)) {
                    logger.warn("Scan path does not exist: {}", path);
                    continue;
                }
                
                try (Stream<Path> pathStream = Files.walk(path)) {
                    List<Path> javaFiles = pathStream
                        .filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(".java"))
                        .collect(Collectors.toList());
                    
                    logger.info("Found {} Java files for dependency analysis", javaFiles.size());
                    
                    for (Path javaFile : javaFiles) {
                        try (FileInputStream in = new FileInputStream(javaFile.toFile())) {
                            // Parse the Java file
                            ParseResult<CompilationUnit> parseResult = javaParser.parse(in);
                            
                            if (parseResult.isSuccessful() && parseResult.getResult().isPresent()) {
                                CompilationUnit cu = parseResult.getResult().get();
                                
                                // Visit class to extract dependencies
                                DependencyVisitor visitor = new DependencyVisitor();
                                visitor.visit(cu, null);
                                
                                String packageName = cu.getPackageDeclaration()
                                    .map(pd -> pd.getName().asString())
                                    .orElse("");
                                    
                                for (TypeDeclaration<?> type : cu.getTypes()) {
                                    String className = packageName.isEmpty() 
                                        ? type.getNameAsString() 
                                        : packageName + "." + type.getNameAsString();
                                        
                                    // Store class dependencies
                                    classDependencyMap.put(className, visitor.getClassDependencies());
                                    
                                    // Store method call graph
                                    for (Map.Entry<String, Set<String>> entry : visitor.getMethodCalls().entrySet()) {
                                        String methodSignature = className + "." + entry.getKey();
                                        methodCallGraph.put(methodSignature, entry.getValue());
                                    }
                                    
                                    // Store field information
                                    for (String fieldName : visitor.getFields()) {
                                        fieldToClassMap.put(className + "." + fieldName, className);
                                    }
                                }
                            }
                        } catch (Exception e) {
                            logger.warn("Error analyzing file for dependencies {}: {}", javaFile, e.getMessage());
                        }
                    }
                }
            }
            
            logger.info("Built dependency graph with {} classes", classDependencyMap.size());
        } catch (Exception e) {
            logger.error("Error building dependency graphs", e);
        }
    }
    
    /**
     * Finds classes that depend on a given class.
     * 
     * @param className The class name to find dependents for
     * @return Set of dependent class names
     */
    private Set<String> findDependentClasses(String className) {
        Set<String> dependents = new HashSet<>();
        Queue<String> toProcess = new LinkedList<>();
        toProcess.add(className);
        
        // Simple breadth-first traversal to find all dependents
        while (!toProcess.isEmpty()) {
            String current = toProcess.poll();
            
            // Find direct dependents of this class
            for (Map.Entry<String, Set<String>> entry : classDependencyMap.entrySet()) {
                if (entry.getValue().contains(current) && !dependents.contains(entry.getKey())) {
                    dependents.add(entry.getKey());
                    toProcess.add(entry.getKey());
                }
            }
            
            // Check method calls
            for (Map.Entry<String, Set<String>> entry : methodCallGraph.entrySet()) {
                String callerClass = entry.getKey().substring(0, entry.getKey().lastIndexOf('.'));
                
                for (String called : entry.getValue()) {
                    if (called.startsWith(current + ".") && !dependents.contains(callerClass)) {
                        dependents.add(callerClass);
                        toProcess.add(callerClass);
                    }
                }
            }
        }
        
        return dependents;
    }
    
    /**
     * Determines the component type from a class name using simple heuristics.
     */
    private String determineComponentType(String className) {
        if (className.contains("Controller")) {
            return "Controller";
        } else if (className.contains("Service")) {
            return "Service";
        } else if (className.contains("Repository") || className.contains("DAO")) {
            return "Repository";
        } else if (className.contains("Config")) {
            return "Configuration";
        } else {
            return "Other";
        }
    }
    
    /**
     * Visitor that collects dependency information from Java classes.
     */
    private static class DependencyVisitor extends VoidVisitorAdapter<Void> {
        private final Set<String> classDependencies = new HashSet<>();
        private final Map<String, Set<String>> methodCalls = new HashMap<>();
        private final Set<String> fields = new HashSet<>();
        private String currentMethod = null;
        
        @Override
        public void visit(FieldDeclaration field, Void arg) {
            field.getVariables().forEach(v -> fields.add(v.getNameAsString()));
            
            // Record the field type as a dependency
            field.getElementType().ifClassOrInterfaceType(classType -> 
                classDependencies.add(classType.getNameAsString()));
                
            super.visit(field, arg);
        }
        
        @Override
        public void visit(MethodDeclaration method, Void arg) {
            currentMethod = method.getNameAsString();
            methodCalls.put(currentMethod, new HashSet<>());
            
            // Record parameter types as dependencies
            method.getParameters().forEach(param -> 
                param.getType().ifClassOrInterfaceType(classType -> 
                    classDependencies.add(classType.getNameAsString())));
                    
            super.visit(method, arg);
            currentMethod = null;
        }
        
        @Override
        public void visit(MethodCallExpr methodCall, Void arg) {
            if (currentMethod != null && methodCall.getScope().isPresent()) {
                String scope = methodCall.getScope().get().toString();
                String calledMethod = methodCall.getNameAsString();
                
                // Record the method call
                methodCalls.get(currentMethod).add(scope + "." + calledMethod);
                
                // The scope might be a class dependency
                classDependencies.add(scope);
            }
            
            super.visit(methodCall, arg);
        }
        
        public Set<String> getClassDependencies() {
            return classDependencies;
        }
        
        public Map<String, Set<String>> getMethodCalls() {
            return methodCalls;
        }
        
        public Set<String> getFields() {
            return fields;
        }
    }
} 