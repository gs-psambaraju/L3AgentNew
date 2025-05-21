package com.l3agent.mcp.tools.config.ast;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.l3agent.mcp.tools.config.model.PropertyReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * AST-based analyzer for finding property references in Java code.
 * Uses JavaParser to parse and analyze Java source files.
 */
@Component
public class ASTPropertyAnalyzer {
    
    private static final Logger logger = LoggerFactory.getLogger(ASTPropertyAnalyzer.class);
    
    @Value("${l3agent.config.scan-paths:src/main/java}")
    private String scanPaths;
    
    // List of critical package identifiers
    private static final List<String> CRITICAL_PACKAGES = List.of(
        ".security.", 
        ".auth.", 
        ".core.",
        ".persistence."
    );
    
    /**
     * Finds references to properties in the codebase using AST parsing.
     * 
     * @param propertyNames The property names to search for
     * @return List of property references
     * @throws IOException If an error occurs reading files
     */
    public List<PropertyReference> findPropertyReferences(Set<String> propertyNames) throws IOException {
        List<PropertyReference> references = new ArrayList<>();
        List<String> propertyNamesList = new ArrayList<>(propertyNames);
        JavaParser javaParser = new JavaParser();
        
        // Scan all Java files in the configured paths
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
                
                logger.info("Found {} Java files to analyze in {}", javaFiles.size(), scanPath);
                
                for (Path javaFile : javaFiles) {
                    try (FileInputStream in = new FileInputStream(javaFile.toFile())) {
                        // Parse the Java file
                        ParseResult<CompilationUnit> parseResult = javaParser.parse(in);
                        
                        if (parseResult.isSuccessful() && parseResult.getResult().isPresent()) {
                            CompilationUnit cu = parseResult.getResult().get();
                            
                            // Determine class properties
                            String content = Files.readString(javaFile);
                            String componentType = determineComponentType(content);
                            boolean isCritical = isCriticalComponent(content);
                            
                            // Create and run the visitor
                            PropertyVisitor visitor = new PropertyVisitor(propertyNamesList, componentType, isCritical);
                            visitor.visit(cu, null);
                            
                            // Add found references
                            references.addAll(visitor.getReferences());
                        } else {
                            logger.warn("Failed to parse Java file: {}", javaFile);
                            if (parseResult.getProblems() != null && !parseResult.getProblems().isEmpty()) {
                                parseResult.getProblems().forEach(problem -> 
                                    logger.warn("  Parser problem: {}", problem.getMessage()));
                            }
                        }
                    } catch (Exception e) {
                        logger.error("Error analyzing file {}: {}", javaFile, e.getMessage(), e);
                    }
                }
            }
        }
        
        return references;
    }
    
    /**
     * Determines the component type based on content.
     */
    private String determineComponentType(String content) {
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
        if (content.contains("class") && content.contains("Controller")) {
            return "Controller";
        } else if (content.contains("class") && content.contains("Service")) {
            return "Service";
        } else if (content.contains("class") && (content.contains("Repository") || content.contains("DAO"))) {
            return "Repository";
        } else if (content.contains("class") && (content.contains("Config") || content.contains("Configuration"))) {
            return "Configuration";
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
     * Determines if a component is critical based on content.
     */
    private boolean isCriticalComponent(String content) {
        // Extract package name
        int packageIndex = content.indexOf("package");
        if (packageIndex >= 0) {
            int semicolonIndex = content.indexOf(";", packageIndex);
            if (semicolonIndex > packageIndex) {
                String packageName = content.substring(packageIndex + 8, semicolonIndex).trim();
                
                for (String criticalPackage : CRITICAL_PACKAGES) {
                    if (packageName.contains(criticalPackage)) {
                        return true;
                    }
                }
            }
        }
        
        // Security and auth components are critical
        return content.contains("Security") || 
               content.contains("Auth") || 
               content.contains("JWT") || 
               content.contains("Login") || 
               content.contains("Permission");
    }
    
    /**
     * Custom visitor for finding property references.
     */
    private class PropertyVisitor extends VoidVisitorAdapter<Void> {
        private final List<String> propertyNames;
        private final List<PropertyReference> references = new ArrayList<>();
        private final String componentType;
        private final boolean isCritical;
        
        private String currentClassName;
        private String packageName;
        
        // Pattern to extract property name from Spring @Value annotation
        private static final java.util.regex.Pattern VALUE_PROPERTY_PATTERN = 
            java.util.regex.Pattern.compile("\\$\\{([^:}]+)(?::([^}]*))?\\}");
        
        public PropertyVisitor(List<String> propertyNames, String componentType, boolean isCritical) {
            this.propertyNames = propertyNames;
            this.componentType = componentType;
            this.isCritical = isCritical;
        }
        
        @Override
        public void visit(CompilationUnit cu, Void arg) {
            try {
                packageName = cu.getPackageDeclaration()
                                .map(pd -> pd.getName().asString())
                                .orElse("");
                super.visit(cu, arg);
            } catch (Exception e) {
                logger.error("Error visiting CompilationUnit: {}", e.getMessage(), e);
            }
        }
        
        @Override
        public void visit(ClassOrInterfaceDeclaration clazz, Void arg) {
            try {
                // Set current class name
                currentClassName = packageName.isEmpty() 
                        ? clazz.getNameAsString() 
                        : packageName + "." + clazz.getNameAsString();
                
                // Continue with normal visit
                super.visit(clazz, arg);
            } catch (Exception e) {
                logger.error("Error visiting class {}: {}", clazz.getNameAsString(), e.getMessage(), e);
            }
        }
        
        @Override
        public void visit(SingleMemberAnnotationExpr annotation, Void arg) {
            try {
                // Handle Spring @Value annotations
                if (annotation.getNameAsString().equals("Value")) {
                    handleValueAnnotation(annotation);
                }
                super.visit(annotation, arg);
            } catch (Exception e) {
                logger.error("Error processing annotation {}: {}", annotation, e.getMessage(), e);
            }
        }
        
        @Override
        public void visit(NormalAnnotationExpr annotation, Void arg) {
            try {
                // Handle @ConfigurationProperties annotations
                if (annotation.getNameAsString().equals("ConfigurationProperties")) {
                    handleConfigPropertiesAnnotation(annotation);
                }
                // Handle @ConditionalOnProperty annotations
                else if (annotation.getNameAsString().contains("ConditionalOn")) {
                    handleConditionalAnnotation(annotation);
                }
                super.visit(annotation, arg);
            } catch (Exception e) {
                logger.error("Error processing annotation {}: {}", annotation, e.getMessage(), e);
            }
        }
        
        @Override
        public void visit(MarkerAnnotationExpr annotation, Void arg) {
            // Process marker annotations if needed
            super.visit(annotation, arg);
        }
        
        @Override
        public void visit(MethodCallExpr methodCall, Void arg) {
            try {
                // Handle environment.getProperty() calls
                if (isEnvironmentGetProperty(methodCall)) {
                    handleEnvironmentGetProperty(methodCall);
                }
                // Handle properties.getProperty() calls
                else if (isPropertiesGetProperty(methodCall)) {
                    handlePropertiesGetProperty(methodCall);
                }
                
                super.visit(methodCall, arg);
            } catch (Exception e) {
                logger.error("Error processing method call {}: {}", methodCall, e.getMessage(), e);
            }
        }
        
        /**
         * Processes @Value annotations to extract property references.
         */
        private void handleValueAnnotation(SingleMemberAnnotationExpr annotation) {
            Expression value = annotation.getMemberValue();
            
            if (value.isStringLiteralExpr()) {
                String stringValue = value.asStringLiteralExpr().getValue();
                java.util.regex.Matcher matcher = VALUE_PROPERTY_PATTERN.matcher(stringValue);
                
                if (matcher.find()) {
                    String propertyName = matcher.group(1);
                    String defaultValue = matcher.group(2);
                    
                    if (isTargetProperty(propertyName)) {
                        // Get the field being annotated
                        com.github.javaparser.ast.Node parent = annotation.getParentNode().orElse(null);
                        String fieldName = extractFieldName(parent);
                        
                        // Create the reference
                        PropertyReference reference = new PropertyReference(currentClassName, componentType)
                                .setCriticalComponent(isCritical)
                                .setReferenceType("@Value")
                                .setFieldName(fieldName)
                                .setPropertyName(propertyName)
                                .setLineNumber(annotation.getBegin().isPresent() ? annotation.getBegin().get().line : null)
                                .setAccessPattern(defaultValue != null ? "fallback" : "direct");
                        
                        if (defaultValue != null) {
                            reference.setNotes("Default value: " + defaultValue);
                        }
                        
                        references.add(reference);
                        logger.debug("Found @Value property reference: {} in {}", propertyName, currentClassName);
                    }
                }
            }
        }
        
        /**
         * Processes @ConfigurationProperties annotations.
         */
        private void handleConfigPropertiesAnnotation(NormalAnnotationExpr annotation) {
            String prefix = null;
            
            for (MemberValuePair pair : annotation.getPairs()) {
                if ((pair.getNameAsString().equals("value") || pair.getNameAsString().equals("prefix")) 
                        && pair.getValue().isStringLiteralExpr()) {
                    prefix = pair.getValue().asStringLiteralExpr().getValue();
                    break;
                }
            }
            
            if (prefix != null) {
                // Check if any target property starts with this prefix
                for (String propertyName : propertyNames) {
                    if (propertyName.startsWith(prefix)) {
                        PropertyReference reference = new PropertyReference(currentClassName, componentType)
                                .setCriticalComponent(isCritical)
                                .setReferenceType("@ConfigurationProperties")
                                .setLineNumber(annotation.getBegin().isPresent() ? annotation.getBegin().get().line : null)
                                .setAccessPattern("binding")
                                .setNotes("Properties with prefix: " + prefix);
                        
                        references.add(reference);
                        break; // Only add one reference per class
                    }
                }
            }
        }
        
        /**
         * Processes @ConditionalOn* annotations.
         */
        private void handleConditionalAnnotation(NormalAnnotationExpr annotation) {
            for (MemberValuePair pair : annotation.getPairs()) {
                if ((pair.getNameAsString().equals("name") || pair.getNameAsString().equals("value")) 
                        && pair.getValue().isStringLiteralExpr()) {
                    String propertyName = pair.getValue().asStringLiteralExpr().getValue();
                    
                    if (isTargetProperty(propertyName)) {
                        PropertyReference reference = new PropertyReference(currentClassName, componentType)
                                .setCriticalComponent(isCritical)
                                .setReferenceType("@" + annotation.getNameAsString())
                                .setLineNumber(annotation.getBegin().isPresent() ? annotation.getBegin().get().line : null)
                                .setAccessPattern("conditional")
                                .setNotes("Used in conditional bean creation");
                        
                        references.add(reference);
                    }
                }
            }
        }
        
        /**
         * Processes environment.getProperty() calls.
         */
        private void handleEnvironmentGetProperty(MethodCallExpr methodCall) {
            if (methodCall.getArguments().size() >= 1) {
                Expression firstArg = methodCall.getArguments().get(0);
                
                if (firstArg.isStringLiteralExpr()) {
                    String propertyName = firstArg.asStringLiteralExpr().getValue();
                    
                    if (isTargetProperty(propertyName)) {
                        // Find the enclosing method
                        String methodName = findEnclosingMethod(methodCall);
                        
                        // Check for default value (second argument)
                        String defaultValue = null;
                        if (methodCall.getArguments().size() >= 2) {
                            Expression secondArg = methodCall.getArguments().get(1);
                            if (secondArg.isStringLiteralExpr()) {
                                defaultValue = secondArg.asStringLiteralExpr().getValue();
                            } else if (secondArg.isLiteralExpr()) {
                                defaultValue = secondArg.toString();
                            }
                        }
                        
                        // Look for conditional usage
                        boolean isConditional = isConditionalUsage(methodCall);
                        
                        // Create reference
                        PropertyReference reference = new PropertyReference(currentClassName, componentType)
                                .setCriticalComponent(isCritical)
                                .setReferenceType("Environment.getProperty")
                                .setMethodName(methodName)
                                .setLineNumber(methodCall.getBegin().isPresent() ? methodCall.getBegin().get().line : null)
                                .setAccessPattern(isConditional ? "conditional" : 
                                                 defaultValue != null ? "fallback" : "direct");
                        
                        if (defaultValue != null) {
                            reference.setNotes("Default value: " + defaultValue);
                        }
                        
                        references.add(reference);
                    }
                }
            }
        }
        
        /**
         * Processes properties.getProperty() calls.
         */
        private void handlePropertiesGetProperty(MethodCallExpr methodCall) {
            if (methodCall.getArguments().size() >= 1) {
                Expression firstArg = methodCall.getArguments().get(0);
                
                if (firstArg.isStringLiteralExpr()) {
                    String propertyName = firstArg.asStringLiteralExpr().getValue();
                    
                    if (isTargetProperty(propertyName)) {
                        // Find the enclosing method
                        String methodName = findEnclosingMethod(methodCall);
                        
                        // Check for default value (second argument)
                        String defaultValue = null;
                        if (methodCall.getArguments().size() >= 2) {
                            Expression secondArg = methodCall.getArguments().get(1);
                            if (secondArg.isStringLiteralExpr()) {
                                defaultValue = secondArg.asStringLiteralExpr().getValue();
                            } else if (secondArg.isLiteralExpr()) {
                                defaultValue = secondArg.toString();
                            }
                        }
                        
                        // Look for conditional usage
                        boolean isConditional = isConditionalUsage(methodCall);
                        
                        // Create reference
                        PropertyReference reference = new PropertyReference(currentClassName, componentType)
                                .setCriticalComponent(isCritical)
                                .setReferenceType("Properties.getProperty")
                                .setMethodName(methodName)
                                .setLineNumber(methodCall.getBegin().isPresent() ? methodCall.getBegin().get().line : null)
                                .setAccessPattern(isConditional ? "conditional" : 
                                                 defaultValue != null ? "fallback" : "direct");
                        
                        if (defaultValue != null) {
                            reference.setNotes("Default value: " + defaultValue);
                        }
                        
                        references.add(reference);
                    }
                }
            }
        }
        
        /**
         * Checks if a method call is environment.getProperty().
         */
        private boolean isEnvironmentGetProperty(MethodCallExpr methodCall) {
            return methodCall.getNameAsString().equals("getProperty") && 
                   methodCall.getScope().isPresent() && 
                   methodCall.getScope().get().toString().contains("environment");
        }
        
        /**
         * Checks if a method call is properties.getProperty().
         */
        private boolean isPropertiesGetProperty(MethodCallExpr methodCall) {
            if (!methodCall.getScope().isPresent()) {
                return false;
            }
            
            String scope = methodCall.getScope().get().toString();
            String name = methodCall.getNameAsString();
            
            return (name.equals("getProperty") || name.equals("get")) && 
                   (scope.contains("properties") || scope.contains("Properties") || 
                    scope.contains("props") || scope.contains("config"));
        }
        
        /**
         * Checks if a property usage is in a conditional context.
         */
        private boolean isConditionalUsage(MethodCallExpr methodCall) {
            // Check if the method call is in an if statement
            Optional<IfStmt> parentIf = 
                methodCall.findAncestor(IfStmt.class);
            if (parentIf.isPresent()) {
                return true;
            }
            
            // Check if the method call is part of a ternary expression
            Optional<ConditionalExpr> parentTernary = 
                methodCall.findAncestor(ConditionalExpr.class);
            if (parentTernary.isPresent()) {
                return true;
            }
            
            // Check if method call is used in a boolean context
            Optional<BinaryExpr> parentBinary = 
                methodCall.findAncestor(BinaryExpr.class);
            if (parentBinary.isPresent() && parentBinary.get().getOperator().name().contains("EQUALS")) {
                return true;
            }
            
            return false;
        }
        
        /**
         * Extracts field name from a node.
         */
        private String extractFieldName(Node node) {
            if (node instanceof FieldDeclaration) {
                FieldDeclaration field = 
                    (FieldDeclaration) node;
                
                NodeList<VariableDeclarator> variables = 
                    field.getVariables();
                
                if (!variables.isEmpty()) {
                    return variables.get(0).getNameAsString();
                }
            }
            return null;
        }
        
        /**
         * Finds the enclosing method for a node.
         */
        private String findEnclosingMethod(Node node) {
            Optional<MethodDeclaration> methodDecl = 
                node.findAncestor(MethodDeclaration.class);
            
            return methodDecl.map(MethodDeclaration::getNameAsString).orElse(null);
        }
        
        /**
         * Checks if a property name is one of the target properties.
         */
        private boolean isTargetProperty(String propertyName) {
            // Check exact matches
            if (propertyNames.contains(propertyName)) {
                return true;
            }
            
            // Check prefix matches for properties ending with *
            for (String pattern : propertyNames) {
                if (pattern.endsWith("*")) {
                    String prefix = pattern.substring(0, pattern.length() - 1);
                    if (propertyName.startsWith(prefix)) {
                        return true;
                    }
                }
            }
            
            return false;
        }
        
        public List<PropertyReference> getReferences() {
            return references;
        }
    }
} 