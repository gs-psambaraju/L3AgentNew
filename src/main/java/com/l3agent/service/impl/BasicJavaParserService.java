package com.l3agent.service.impl;

import com.l3agent.service.JavaParserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A basic implementation of JavaParserService using regex patterns.
 * Note: In a production system, this would be replaced with a proper Java parser library.
 */
@Service
public class BasicJavaParserService implements JavaParserService {
    
    private static final Logger logger = LoggerFactory.getLogger(BasicJavaParserService.class);
    
    // Regex patterns for Java parsing
    private static final Pattern PACKAGE_PATTERN = Pattern.compile("package\\s+([\\w\\.]+);");
    private static final Pattern CLASS_PATTERN = Pattern.compile("(public|private|protected)?\\s*class\\s+(\\w+)");
    private static final Pattern EXTENDS_PATTERN = Pattern.compile("class\\s+(\\w+)\\s+extends\\s+(\\w+)");
    private static final Pattern IMPLEMENTS_PATTERN = Pattern.compile("class\\s+(\\w+)\\s+implements\\s+([\\w\\s,]+)");
    private static final Pattern IMPORT_PATTERN = Pattern.compile("import\\s+([\\w\\.\\*]+);");
    private static final Pattern METHOD_PATTERN = Pattern.compile("(public|private|protected)?\\s*(?:static)?\\s*(\\w+)\\s+(\\w+)\\s*\\(([^\\)]*)\\)");
    
    // Improved method call pattern to catch various forms of method calls
    private static final Pattern METHOD_CALL_PATTERN = Pattern.compile(
        "(?:^|\\s|;|\\(|\\)|\\{|\\}|,)(?:(\\w+)\\.)?(\\w+)\\s*\\(([^\\(\\)]*(?:\\([^\\(\\)]*\\)[^\\(\\)]*)*)\\)"
    );
    
    @Override
    public Map<String, Object> parseJavaFile(String filePath) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            String content = new String(Files.readAllBytes(Paths.get(filePath)));
            
            // Extract package
            Matcher packageMatcher = PACKAGE_PATTERN.matcher(content);
            if (packageMatcher.find()) {
                result.put("package", packageMatcher.group(1));
            }
            
            // Extract class name
            Matcher classMatcher = CLASS_PATTERN.matcher(content);
            if (classMatcher.find()) {
                result.put("className", classMatcher.group(2));
            }
            
            // Extract imports
            List<String> imports = extractImports(filePath);
            result.put("imports", imports);
            
            // Extract methods
            List<Map<String, Object>> methods = new ArrayList<>();
            Matcher methodMatcher = METHOD_PATTERN.matcher(content);
            while (methodMatcher.find()) {
                Map<String, Object> method = new HashMap<>();
                method.put("visibility", methodMatcher.group(1) != null ? methodMatcher.group(1) : "default");
                method.put("returnType", methodMatcher.group(2));
                method.put("name", methodMatcher.group(3));
                method.put("parameters", methodMatcher.group(4));
                methods.add(method);
            }
            result.put("methods", methods);
            
            // Extract method calls and attach them to the methods
            List<Map<String, Object>> methodCalls = extractMethodCalls(filePath);
            logger.debug("Extracted {} method calls from {}", methodCalls.size(), filePath);
            
            // Group calls by source method
            Map<String, List<Map<String, Object>>> callsByMethod = new HashMap<>();
            for (Map<String, Object> call : methodCalls) {
                String sourceMethod = (String) call.get("sourceMethod");
                if (sourceMethod != null) {
                    callsByMethod.computeIfAbsent(sourceMethod, k -> new ArrayList<>()).add(call);
                }
            }
            
            // Attach calls to their methods
            int totalAttachedCalls = 0;
            for (Map<String, Object> method : methods) {
                String methodName = (String) method.get("name");
                if (callsByMethod.containsKey(methodName)) {
                    List<Map<String, Object>> calls = callsByMethod.get(methodName);
                    method.put("methodCalls", calls);
                    totalAttachedCalls += calls.size();
                    logger.debug("Attached {} calls to method {}", calls.size(), methodName);
                } else {
                    // Add empty calls list to ensure the key exists
                    method.put("methodCalls", new ArrayList<>());
                }
            }
            logger.debug("Attached {} out of {} method calls to methods in {}", 
                      totalAttachedCalls, methodCalls.size(), filePath);
            
            // Extract inheritance
            Map<String, Object> hierarchy = extractClassHierarchy(filePath);
            result.put("hierarchy", hierarchy);
            
        } catch (Exception e) {
            logger.error("Error parsing Java file {}: {}", filePath, e.getMessage(), e);
        }
        
        return result;
    }
    
    @Override
    public List<Map<String, Object>> extractMethodCalls(String filePath) {
        List<Map<String, Object>> methodCalls = new ArrayList<>();
        
        try {
            String content = new String(Files.readAllBytes(Paths.get(filePath)));
            
            // Extract package and class name
            String packageName = null;
            Matcher packageMatcher = PACKAGE_PATTERN.matcher(content);
            if (packageMatcher.find()) {
                packageName = packageMatcher.group(1);
            }
            
            String className = null;
            Matcher classMatcher = CLASS_PATTERN.matcher(content);
            if (classMatcher.find()) {
                className = classMatcher.group(2);
            }
            
            // Track current method context with line ranges
            String currentMethod = null;
            int methodStartLine = -1;
            int braceCount = 0;
            boolean inMethod = false;
            
            String[] lines = content.split("\\r?\\n");
            
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                
                // Count braces to track method bodies
                for (char c : line.toCharArray()) {
                    if (c == '{') braceCount++;
                    if (c == '}') braceCount--;
                }
                
                // Check if we're entering a new method
                Matcher methodDeclarationMatcher = METHOD_PATTERN.matcher(line);
                if (methodDeclarationMatcher.find() && line.contains("{")) {
                    currentMethod = methodDeclarationMatcher.group(3);
                    methodStartLine = i + 1;
                    inMethod = true;
                    braceCount = 1; // Reset brace count for new method
                }
                
                // Check if we're exiting a method
                if (inMethod && braceCount <= 0) {
                    inMethod = false;
                    currentMethod = null;
                }
                
                // Only look for method calls when we're inside a method
                if (inMethod && currentMethod != null) {
                    Matcher methodCallMatcher = METHOD_CALL_PATTERN.matcher(line);
                    while (methodCallMatcher.find()) {
                        Map<String, Object> call = new HashMap<>();
                        call.put("sourceClass", className);
                        call.put("sourceMethod", currentMethod);
                        call.put("sourceLine", i + 1);
                        
                        // Object might be null for simple method calls without object reference
                        String targetObject = methodCallMatcher.group(1);
                        call.put("targetObject", targetObject);
                        
                        String targetMethod = methodCallMatcher.group(2);
                        // Skip if it's a constructor call with the same name as the current class
                        if (targetMethod.equals(className)) {
                            continue;
                        }
                        
                        call.put("targetMethod", targetMethod);
                        
                        // Set a default target class if not specified (using the object reference if available)
                        if (targetObject != null) {
                            call.put("targetClass", targetObject);
                        } else {
                            // Default to current class if it's a local method call
                            call.put("targetClass", className);
                        }
                        
                        // Extract parameters
                        String paramsString = methodCallMatcher.group(3);
                        call.put("parameters", paramsString);
                        
                        // Extract individual parameters as a list if needed
                        if (paramsString != null && !paramsString.trim().isEmpty()) {
                            List<String> params = new ArrayList<>();
                            for (String param : paramsString.split(",")) {
                                params.add(param.trim());
                            }
                            if (!params.isEmpty()) {
                                call.put("parameters", params);
                            }
                        }
                        
                        methodCalls.add(call);
                    }
                }
            }
            
            logger.debug("Found {} method calls in {}", methodCalls.size(), filePath);
            
        } catch (Exception e) {
            logger.error("Error extracting method calls from {}: {}", filePath, e.getMessage(), e);
        }
        
        return methodCalls;
    }
    
    @Override
    public List<String> extractImports(String filePath) {
        List<String> imports = new ArrayList<>();
        
        try {
            String content = new String(Files.readAllBytes(Paths.get(filePath)));
            Matcher importMatcher = IMPORT_PATTERN.matcher(content);
            
            while (importMatcher.find()) {
                imports.add(importMatcher.group(1));
            }
        } catch (Exception e) {
            logger.error("Error extracting imports from {}: {}", filePath, e.getMessage(), e);
        }
        
        return imports;
    }
    
    @Override
    public Map<String, Object> extractClassHierarchy(String filePath) {
        Map<String, Object> hierarchy = new HashMap<>();
        
        try {
            String content = new String(Files.readAllBytes(Paths.get(filePath)));
            
            // Extract class name
            String className = null;
            Matcher classMatcher = CLASS_PATTERN.matcher(content);
            if (classMatcher.find()) {
                className = classMatcher.group(2);
                hierarchy.put("className", className);
            }
            
            // Extract superclass
            Matcher extendsMatcher = EXTENDS_PATTERN.matcher(content);
            if (extendsMatcher.find()) {
                hierarchy.put("extends", extendsMatcher.group(2));
            }
            
            // Extract interfaces
            Matcher implementsMatcher = IMPLEMENTS_PATTERN.matcher(content);
            if (implementsMatcher.find()) {
                String interfacesStr = implementsMatcher.group(2);
                String[] interfaces = interfacesStr.split(",");
                List<String> interfaceList = new ArrayList<>();
                for (String iface : interfaces) {
                    interfaceList.add(iface.trim());
                }
                hierarchy.put("implements", interfaceList);
            }
        } catch (Exception e) {
            logger.error("Error extracting class hierarchy from {}: {}", filePath, e.getMessage(), e);
        }
        
        return hierarchy;
    }
    
    @Override
    public String resolveClassToSourceFile(String className, List<String> searchPaths) {
        // Convert package dots to path separators
        String classPath = className.replace('.', '/') + ".java";
        
        for (String basePath : searchPaths) {
            Path potentialFile = Paths.get(basePath, classPath);
            if (Files.exists(potentialFile)) {
                return potentialFile.toString();
            }
            
            // Also try finding by simple class name in all directories
            String simpleClassName = className;
            if (className.contains(".")) {
                simpleClassName = className.substring(className.lastIndexOf('.') + 1);
            }
            
            try {
                // Search recursively in the base path
                File baseDir = new File(basePath);
                if (baseDir.exists() && baseDir.isDirectory()) {
                    String result = findClassFile(baseDir, simpleClassName);
                    if (result != null) {
                        return result;
                    }
                }
            } catch (Exception e) {
                logger.warn("Error searching for class {}: {}", className, e.getMessage());
            }
        }
        
        return null;
    }
    
    /**
     * Recursively searches for a class file.
     * 
     * @param directory Directory to search in
     * @param className Class name to find
     * @return Path to the file if found, null otherwise
     */
    private String findClassFile(File directory, String className) {
        File[] files = directory.listFiles();
        if (files == null) {
            return null;
        }
        
        for (File file : files) {
            if (file.isDirectory()) {
                String result = findClassFile(file, className);
                if (result != null) {
                    return result;
                }
            } else if (file.getName().equals(className + ".java")) {
                return file.getAbsolutePath();
            }
        }
        
        return null;
    }
} 