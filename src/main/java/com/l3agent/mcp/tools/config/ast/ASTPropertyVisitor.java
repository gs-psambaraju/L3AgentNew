package com.l3agent.mcp.tools.config.ast;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.visitor.GenericVisitorAdapter;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.l3agent.mcp.tools.config.model.PropertyReference;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JavaParser visitor that identifies configuration property references in Java code.
 */
public class ASTPropertyVisitor extends VoidVisitorAdapter<Void> {
    
    private static final Logger logger = LoggerFactory.getLogger(ASTPropertyVisitor.class);
    
    private final List<String> propertyNames;
    private final List<PropertyReference> references = new ArrayList<>();
    private String currentClassName;
    private String packageName;
    private String componentType;
    private boolean isCritical;
    
    // Pattern to extract property name from Spring @Value annotation
    private static final Pattern VALUE_PROPERTY_PATTERN = Pattern.compile("\\$\\{([^:}]+)(?::([^}]*))?\\}");
    
    /**
     * Creates a new AST property visitor.
     * 
     * @param propertyNames The property names to search for
     * @param componentType The component type of the class
     * @param isCritical Whether the class is a critical component
     */
    public ASTPropertyVisitor(List<String> propertyNames, String componentType, boolean isCritical) {
        this.propertyNames = propertyNames;
        this.componentType = componentType;
        this.isCritical = isCritical;
    }
    
    @Override
    public void visit(CompilationUnit cu, Void arg) {
        try {
            // Extract the package name
            packageName = cu.getPackageDeclaration()
                            .map(pd -> pd.getName().asString())
                            .orElse("");
            
            // Continue with normal visit
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
            logger.error("Error visiting ClassOrInterfaceDeclaration {}: {}", clazz.getNameAsString(), e.getMessage(), e);
        }
    }
    
    @Override
    public void visit(SingleMemberAnnotationExpr annotation, Void arg) {
        try {
            // Process @Value annotation
            if (annotation.getNameAsString().equals("Value")) {
                handleValueAnnotation(annotation);
            }
            super.visit(annotation, arg);
        } catch (Exception e) {
            logger.error("Error visiting SingleMemberAnnotationExpr {}: {}", annotation.getNameAsString(), e.getMessage(), e);
        }
    }
    
    @Override
    public void visit(NormalAnnotationExpr annotation, Void arg) {
        try {
            // Process @ConfigurationProperties and @ConditionalOn* annotations
            if (annotation.getNameAsString().equals("ConfigurationProperties")) {
                handleConfigPropertiesAnnotation(annotation);
            } else if (annotation.getNameAsString().contains("ConditionalOn")) {
                handleConditionalAnnotation(annotation);
            }
            super.visit(annotation, arg);
        } catch (Exception e) {
            logger.error("Error visiting NormalAnnotationExpr {}: {}", annotation.getNameAsString(), e.getMessage(), e);
        }
    }
    
    @Override
    public void visit(MarkerAnnotationExpr annotation, Void arg) {
        // Handle marker annotations if needed in the future
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
            logger.error("Error visiting MethodCallExpr {}: {}", methodCall.getNameAsString(), e.getMessage(), e);
        }
    }
    
    /**
     * Processes @Value annotations to extract property references.
     */
    private void handleValueAnnotation(SingleMemberAnnotationExpr annotation) {
        Expression value = annotation.getMemberValue();
        
        if (value.isStringLiteralExpr()) {
            String stringValue = value.asStringLiteralExpr().getValue();
            Matcher matcher = VALUE_PROPERTY_PATTERN.matcher(stringValue);
            
            if (matcher.find()) {
                String propertyName = matcher.group(1);
                String defaultValue = matcher.group(2);
                
                if (isTargetProperty(propertyName)) {
                    // Get the field being annotated
                    Node parent = annotation.getParentNode().orElse(null);
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
    private void handleConfigPropertiesAnnotation(AnnotationExpr annotation) {
        String prefix = null;
        
        if (annotation instanceof NormalAnnotationExpr) {
            NormalAnnotationExpr normalAnnotation = (NormalAnnotationExpr) annotation;
            for (MemberValuePair pair : normalAnnotation.getPairs()) {
                if ((pair.getNameAsString().equals("value") || pair.getNameAsString().equals("prefix")) 
                        && pair.getValue().isStringLiteralExpr()) {
                    prefix = pair.getValue().asStringLiteralExpr().getValue();
                    break;
                }
            }
        } else if (annotation instanceof SingleMemberAnnotationExpr) {
            Expression value = ((SingleMemberAnnotationExpr) annotation).getMemberValue();
            if (value.isStringLiteralExpr()) {
                prefix = value.asStringLiteralExpr().getValue();
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
    private void handleConditionalAnnotation(AnnotationExpr annotation) {
        if (annotation instanceof NormalAnnotationExpr) {
            NormalAnnotationExpr normalAnnotation = (NormalAnnotationExpr) annotation;
            
            for (MemberValuePair pair : normalAnnotation.getPairs()) {
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
                    
                    // Create reference
                    PropertyReference reference = new PropertyReference(currentClassName, componentType)
                            .setCriticalComponent(isCritical)
                            .setReferenceType("Environment.getProperty")
                            .setMethodName(methodName)
                            .setLineNumber(methodCall.getBegin().isPresent() ? methodCall.getBegin().get().line : null)
                            .setAccessPattern(defaultValue != null ? "fallback" : "direct");
                    
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
                    
                    // Create reference
                    PropertyReference reference = new PropertyReference(currentClassName, componentType)
                            .setCriticalComponent(isCritical)
                            .setReferenceType("Properties.getProperty")
                            .setMethodName(methodName)
                            .setLineNumber(methodCall.getBegin().isPresent() ? methodCall.getBegin().get().line : null)
                            .setAccessPattern(defaultValue != null ? "fallback" : "direct");
                    
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
     * Extracts field name from a node.
     */
    private String extractFieldName(Node node) {
        if (node instanceof FieldDeclaration) {
            FieldDeclaration field = (FieldDeclaration) node;
            NodeList<VariableDeclarator> variables = field.getVariables();
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
        if (node == null) {
            return null;
        }
        
        Optional<Node> parent = node.getParentNode();
        while (parent.isPresent()) {
            if (parent.get() instanceof MethodDeclaration) {
                return ((MethodDeclaration) parent.get()).getNameAsString();
            }
            parent = parent.get().getParentNode();
        }
        
        return null;
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
    
    /**
     * Gets all property references found during the visit.
     */
    public List<PropertyReference> getReferences() {
        return references;
    }
} 