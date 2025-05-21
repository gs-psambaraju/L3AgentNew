package com.l3agent.mcp.tools.config.context;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.l3agent.mcp.tools.config.model.PropertyReference;
import com.l3agent.mcp.tools.config.model.PropertyUsageContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Analyzes the context in which properties are used to provide deeper understanding
 * of how configuration impacts the system.
 */
@Component
public class PropertyContextAnalyzer {
    
    private static final Logger logger = LoggerFactory.getLogger(PropertyContextAnalyzer.class);
    
    @Value("${l3agent.config.scan-paths:src/main/java}")
    private String scanPaths;
    
    // Pattern to extract property name from Spring @Value annotation
    private static final Pattern VALUE_PATTERN = Pattern.compile("\\$\\{([^:}]+)(?::([^}]*))?\\}");
    
    /**
     * Analyzes the context of property usages to enhance property references.
     * 
     * @param propertyReferences List of property references to enhance
     * @return Enhanced property references with context information
     */
    public List<PropertyReference> analyzePropertyContext(List<PropertyReference> propertyReferences) {
        try {
            // Map to store property names and their usage locations
            Map<String, List<PropertyReference>> propertyMap = new HashMap<>();
            
            // Group references by property name
            for (PropertyReference ref : propertyReferences) {
                if (ref.getPropertyName() != null) {
                    propertyMap.computeIfAbsent(ref.getPropertyName(), k -> new ArrayList<>()).add(ref);
                }
            }
            
            // Scan codebase to find property usages and analyze context
            for (String scanPath : scanPaths.split(",")) {
                Path path = Paths.get(scanPath.trim());
                if (!Files.exists(path)) {
                    continue;
                }
                
                try (Stream<Path> pathStream = Files.walk(path)) {
                    List<Path> javaFiles = pathStream
                        .filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(".java"))
                        .collect(Collectors.toList());
                    
                    for (Path javaFile : javaFiles) {
                        analyzeFile(javaFile, propertyMap);
                    }
                }
            }
            
            return propertyReferences;
        } catch (Exception e) {
            logger.error("Error analyzing property usage context", e);
            return propertyReferences; // Return original references if analysis fails
        }
    }
    
    /**
     * Analyzes a Java file for property usages and their context.
     */
    private void analyzeFile(Path javaFile, Map<String, List<PropertyReference>> propertyMap) {
        try (FileInputStream in = new FileInputStream(javaFile.toFile())) {
            JavaParser javaParser = new JavaParser();
            ParseResult<CompilationUnit> parseResult = javaParser.parse(in);
            
            if (parseResult.isSuccessful() && parseResult.getResult().isPresent()) {
                CompilationUnit cu = parseResult.getResult().get();
                String className = extractClassName(cu);
                
                if (className == null) {
                    return;
                }
                
                // For each property, check if it's used in this file
                for (Map.Entry<String, List<PropertyReference>> entry : propertyMap.entrySet()) {
                    String propertyName = entry.getKey();
                    List<PropertyReference> refs = entry.getValue();
                    
                    // Find refs for this class
                    List<PropertyReference> classRefs = refs.stream()
                        .filter(ref -> className.equals(ref.getClassName()))
                        .collect(Collectors.toList());
                    
                    if (!classRefs.isEmpty()) {
                        // Analyze the context for these references
                        ContextVisitor visitor = new ContextVisitor(propertyName, classRefs);
                        visitor.visit(cu, null);
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Error analyzing property context in file {}: {}", javaFile, e.getMessage());
        }
    }
    
    /**
     * Extracts the fully qualified class name from a CompilationUnit.
     */
    private String extractClassName(CompilationUnit cu) {
        if (cu.getTypes().isEmpty()) {
            return null;
        }
        
        String packageName = cu.getPackageDeclaration()
            .map(pd -> pd.getName().asString())
            .orElse("");
            
        String className = cu.getTypes().get(0).getNameAsString();
        
        return packageName.isEmpty() ? className : packageName + "." + className;
    }
    
    /**
     * Visitor for analyzing the context of property usages.
     */
    private class ContextVisitor extends VoidVisitorAdapter<Void> {
        private final String propertyName;
        private final List<PropertyReference> references;
        private final Map<Integer, PropertyReference> lineToRefMap = new HashMap<>();
        
        public ContextVisitor(String propertyName, List<PropertyReference> references) {
            this.propertyName = propertyName;
            this.references = references;
            
            // Build a map of line numbers to references for quick lookup
            for (PropertyReference ref : references) {
                if (ref.getLineNumber() != null) {
                    lineToRefMap.put(ref.getLineNumber(), ref);
                }
            }
        }
        
        @Override
        public void visit(SingleMemberAnnotationExpr annotation, Void arg) {
            try {
                // Check for @Value annotations with our property
                if (annotation.getNameAsString().equals("Value")) {
                    Node parent = annotation.getParentNode().orElse(null);
                    
                    if (parent != null && annotation.getBegin().isPresent()) {
                        int lineNumber = annotation.getBegin().get().line;
                        PropertyReference ref = lineToRefMap.get(lineNumber);
                        
                        if (ref != null) {
                            // Analyze field usage by looking at containing class
                            analyzeFieldUsage(ref, parent);
                        }
                    }
                }
                super.visit(annotation, arg);
            } catch (Exception e) {
                logger.error("Error processing annotation {}: {}", annotation, e.getMessage(), e);
            }
        }
        
        @Override
        public void visit(NormalAnnotationExpr annotation, Void arg) {
            try {
                // For other annotation types
                if (annotation.getNameAsString().equals("Value")) {
                    Node parent = annotation.getParentNode().orElse(null);
                    
                    if (parent != null && annotation.getBegin().isPresent()) {
                        int lineNumber = annotation.getBegin().get().line;
                        PropertyReference ref = lineToRefMap.get(lineNumber);
                        
                        if (ref != null) {
                            // Analyze field usage by looking at containing class
                            analyzeFieldUsage(ref, parent);
                        }
                    }
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
                // Check for environment.getProperty calls with our property
                if (isPropertyAccessMethod(methodCall) && methodCall.getBegin().isPresent()) {
                    int lineNumber = methodCall.getBegin().get().line;
                    PropertyReference ref = lineToRefMap.get(lineNumber);
                    
                    if (ref != null) {
                        // Analyze how the result of getProperty is used
                        analyzeMethodCallUsage(ref, methodCall);
                    }
                }
                
                super.visit(methodCall, arg);
            } catch (Exception e) {
                logger.error("Error processing method call {}: {}", methodCall, e.getMessage(), e);
            }
        }
        
        /**
         * Checks if a method call is a property access method.
         */
        private boolean isPropertyAccessMethod(MethodCallExpr methodCall) {
            return (methodCall.getNameAsString().equals("getProperty") || 
                    methodCall.getNameAsString().equals("get")) &&
                   methodCall.getArguments().size() >= 1 &&
                   methodCall.getArguments().get(0).isStringLiteralExpr() &&
                   methodCall.getArguments().get(0).asStringLiteralExpr().getValue().equals(propertyName);
        }
        
        /**
         * Analyzes how a field with an injected property is used.
         */
        private void analyzeFieldUsage(PropertyReference ref, Node parent) {
            // Extract field name
            String fieldName = null;
            if (parent instanceof FieldDeclaration) {
                FieldDeclaration field = (FieldDeclaration) parent;
                if (!field.getVariables().isEmpty()) {
                    fieldName = field.getVariables().get(0).getNameAsString();
                }
            }
            
            if (fieldName == null) {
                return;
            }
            
            PropertyUsageContext context = new PropertyUsageContext();
            context.setInjectionType("field");
            
            // Scan to find field usages
            ScanFieldUsageVisitor fieldVisitor = new ScanFieldUsageVisitor(fieldName);
            parent.getParentNode().ifPresent(classNode -> {
                // Instead of trying to cast directly, we'll visit the nodes in the proper AST structure
                // This allows the visitor's visit methods to be called appropriately based on node type
                classNode.accept(fieldVisitor, null);
            });
            
            // Set usage information
            context.setReadCount(fieldVisitor.getReadCount());
            context.setWriteCount(fieldVisitor.getWriteCount());
            context.setConditionalUses(fieldVisitor.getConditionalUses());
            context.setUsageInLoops(fieldVisitor.getLoopUses());
            context.setMutatingOperations(fieldVisitor.getMutatingOperations());
            
            // Set the context on the reference
            ref.setContext(context);
        }
        
        /**
         * Analyzes how a property retrieved via method call is used.
         */
        private void analyzeMethodCallUsage(PropertyReference ref, MethodCallExpr methodCall) {
            PropertyUsageContext context = new PropertyUsageContext();
            context.setInjectionType("method");
            
            // Find the parent statement/expression to see how the result is used
            Optional<Statement> parentStmt = methodCall.findAncestor(Statement.class);
            if (parentStmt.isPresent()) {
                Statement stmt = parentStmt.get();
                
                // Check if used in conditional
                Optional<IfStmt> parentIf = stmt.findAncestor(IfStmt.class);
                if (parentIf.isPresent()) {
                    context.setConditionalUses(context.getConditionalUses() + 1);
                }
                
                // Check if used in loop
                if (stmt.findAncestor(ForStmt.class).isPresent() ||
                    stmt.findAncestor(ForEachStmt.class).isPresent() ||
                    stmt.findAncestor(WhileStmt.class).isPresent()) {
                    context.setUsageInLoops(context.getUsageInLoops() + 1);
                }
                
                // Check if value is modified
                if (stmt instanceof ExpressionStmt) {
                    Expression expr = ((ExpressionStmt) stmt).getExpression();
                    if (expr instanceof AssignExpr || 
                        expr instanceof UnaryExpr ||
                        (expr instanceof MethodCallExpr && isMutatingMethod(((MethodCallExpr) expr).getNameAsString()))) {
                        context.setMutatingOperations(context.getMutatingOperations() + 1);
                    }
                }
            }
            
            // Set the context on the reference
            ref.setContext(context);
        }
        
        /**
         * Checks if a method name suggests it modifies state.
         */
        private boolean isMutatingMethod(String methodName) {
            return methodName.startsWith("set") || 
                   methodName.startsWith("add") || 
                   methodName.startsWith("remove") || 
                   methodName.startsWith("delete") || 
                   methodName.startsWith("update") || 
                   methodName.startsWith("modify");
        }
    }
    
    /**
     * Visitor that scans for field usages to understand how a property is used.
     */
    private static class ScanFieldUsageVisitor extends VoidVisitorAdapter<Void> {
        private final String fieldName;
        private int readCount = 0;
        private int writeCount = 0;
        private int conditionalUses = 0;
        private int loopUses = 0;
        private int mutatingOperations = 0;
        
        public ScanFieldUsageVisitor(String fieldName) {
            this.fieldName = fieldName;
        }
        
        @Override
        public void visit(NameExpr nameExpr, Void arg) {
            if (nameExpr.getNameAsString().equals(fieldName)) {
                // Found a reference to our field
                readCount++;
                
                // Check the context
                checkUsageContext(nameExpr);
            }
            super.visit(nameExpr, arg);
        }
        
        @Override
        public void visit(AssignExpr assignExpr, Void arg) {
            // Check if our field is being assigned to
            if (assignExpr.getTarget().isNameExpr() && 
                assignExpr.getTarget().asNameExpr().getNameAsString().equals(fieldName)) {
                writeCount++;
                mutatingOperations++;
            }
            super.visit(assignExpr, arg);
        }
        
        /**
         * Analyzes the context in which a field is used.
         */
        private void checkUsageContext(NameExpr nameExpr) {
            // Check for conditional usage
            Optional<IfStmt> parentIf = nameExpr.findAncestor(IfStmt.class);
            if (parentIf.isPresent() && isInCondition(nameExpr, parentIf.get().getCondition())) {
                conditionalUses++;
            }
            
            // Check for usage in conditional expression
            Optional<ConditionalExpr> parentTernary = nameExpr.findAncestor(ConditionalExpr.class);
            if (parentTernary.isPresent() && isInCondition(nameExpr, parentTernary.get().getCondition())) {
                conditionalUses++;
            }
            
            // Check for loop usage
            if (nameExpr.findAncestor(ForStmt.class).isPresent() ||
                nameExpr.findAncestor(ForEachStmt.class).isPresent() ||
                nameExpr.findAncestor(WhileStmt.class).isPresent()) {
                loopUses++;
            }
            
            // Check for usage in binary expressions (could indicate comparison)
            Optional<BinaryExpr> parentBinary = nameExpr.findAncestor(BinaryExpr.class);
            if (parentBinary.isPresent() && parentBinary.get().getOperator().name().contains("EQUALS")) {
                conditionalUses++;
            }
        }
        
        /**
         * Checks if an expression is part of a condition.
         */
        private boolean isInCondition(Node node, Expression condition) {
            // Simple check to see if the node is within the condition
            return condition.containsWithin(node);
        }
        
        public int getReadCount() {
            return readCount;
        }
        
        public int getWriteCount() {
            return writeCount;
        }
        
        public int getConditionalUses() {
            return conditionalUses;
        }
        
        public int getLoopUses() {
            return loopUses;
        }
        
        public int getMutatingOperations() {
            return mutatingOperations;
        }
    }
} 