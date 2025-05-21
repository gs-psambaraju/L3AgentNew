package com.l3agent.service.impl;

import com.l3agent.service.CodeChunkingService;
import com.l3agent.service.util.BoilerplateFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Implementation of CodeChunkingService with specialized handling for Java code.
 * Segments Java code into logical chunks based on class and method boundaries.
 */
@Service
public class JavaAwareCodeChunkingService extends CodeChunkingService {
    
    private static final Logger logger = LoggerFactory.getLogger(JavaAwareCodeChunkingService.class);
    
    // Regular expressions for Java code structure
    private static final Pattern CLASS_PATTERN = Pattern.compile(
            "(?:public|private|protected|static|\\s)*\\s*(class|interface|enum)\\s+([\\w<>]+)");
    
    private static final Pattern METHOD_PATTERN = Pattern.compile(
            "(?:public|private|protected|static|final|\\s)*" + 
            "(?:[\\w<>\\[\\]]+)\\s+" +         // Return type
            "(\\w+)\\s*\\([^\\)]*\\)\\s*" +    // Method name and parameters
            "(?:throws\\s+[\\w,\\s]+)?\\s*\\{" // Optional throws clause
    );
    
    // Python patterns
    private static final Pattern PYTHON_CLASS_PATTERN = Pattern.compile(
            "^\\s*class\\s+(\\w+)\\s*(?:\\([^)]*\\))?\\s*:");
    
    private static final Pattern PYTHON_METHOD_PATTERN = Pattern.compile(
            "^\\s*def\\s+(\\w+)\\s*\\([^)]*\\)\\s*(?:->\\s*[^:]*)?\\s*:");
    
    // JavaScript/TypeScript patterns
    private static final Pattern JS_CLASS_PATTERN = Pattern.compile(
            "(?:export\\s+)?(?:default\\s+)?(?:abstract\\s+)?class\\s+(\\w+)");
    
    private static final Pattern JS_METHOD_PATTERN = Pattern.compile(
            "(?:public|private|protected|static|async|\\s)*\\s*" +
            "(?:get|set)?\\s*" +             // Getters and setters
            "(\\w+)\\s*\\([^\\)]*\\)\\s*" +  // Method name and parameters
            "(?:\\s*:\\s*[\\w<>\\[\\]]+)?\\s*\\{" // Optional TypeScript return type
    );
    
    private static final Pattern JS_FUNCTION_PATTERN = Pattern.compile(
            "(?:export\\s+)?(?:default\\s+)?(?:async\\s+)?function\\s+(\\w+)\\s*\\([^\\)]*\\)\\s*" +
            "(?:\\s*:\\s*[\\w<>\\[\\]]+)?\\s*\\{"  // Optional TypeScript return type
    );
    
    // Golang patterns
    private static final Pattern GO_STRUCT_PATTERN = Pattern.compile(
            "^\\s*type\\s+(\\w+)\\s+struct\\s*\\{");
    
    private static final Pattern GO_INTERFACE_PATTERN = Pattern.compile(
            "^\\s*type\\s+(\\w+)\\s+interface\\s*\\{");
    
    private static final Pattern GO_FUNCTION_PATTERN = Pattern.compile(
            "^\\s*func\\s+(?:\\([^)]*\\)\\s+)?(\\w+)\\s*\\([^\\)]*\\)\\s*(?:[^{]*)?\\s*\\{");
    
    private static final Map<String, String> LANGUAGE_EXTENSIONS = new HashMap<>();
    
    @Autowired(required = true)
    @org.springframework.beans.factory.annotation.Qualifier("serviceBoilerplateFilter")
    private BoilerplateFilter boilerplateFilter;
    
    static {
        LANGUAGE_EXTENSIONS.put("java", "java");
        LANGUAGE_EXTENSIONS.put("py", "python");
        LANGUAGE_EXTENSIONS.put("js", "javascript");
        LANGUAGE_EXTENSIONS.put("ts", "typescript");
        LANGUAGE_EXTENSIONS.put("rb", "ruby");
        LANGUAGE_EXTENSIONS.put("go", "golang");
        LANGUAGE_EXTENSIONS.put("cs", "csharp");
        LANGUAGE_EXTENSIONS.put("php", "php");
        LANGUAGE_EXTENSIONS.put("kt", "kotlin");
        LANGUAGE_EXTENSIONS.put("scala", "scala");
        LANGUAGE_EXTENSIONS.put("swift", "swift");
        LANGUAGE_EXTENSIONS.put("c", "c");
        LANGUAGE_EXTENSIONS.put("cpp", "cpp");
        LANGUAGE_EXTENSIONS.put("h", "c-header");
        LANGUAGE_EXTENSIONS.put("hpp", "cpp-header");
        LANGUAGE_EXTENSIONS.put("sh", "shell");
        LANGUAGE_EXTENSIONS.put("sql", "sql");
        LANGUAGE_EXTENSIONS.put("xml", "xml");
        LANGUAGE_EXTENSIONS.put("json", "json");
        LANGUAGE_EXTENSIONS.put("yml", "yaml");
        LANGUAGE_EXTENSIONS.put("yaml", "yaml");
        LANGUAGE_EXTENSIONS.put("md", "markdown");
    }
    
    @Value("${l3agent.chunking.min-chunk-size:50}")
    private int minChunkSize;
    
    @Value("${l3agent.chunking.max-chunk-size:1500}")
    private int maxChunkSize;
    
    @Value("${l3agent.chunking.overlap:20}")
    private int overlapLines;
    
    @Value("${l3agent.chunking.filter-boilerplate:true}")
    private boolean filterBoilerplate;
    
    @Override
    public List<CodeChunk> chunkCodeFile(String filePath, String content) {
        List<CodeChunk> chunks = new ArrayList<>();
        
        String language = detectLanguage(filePath, content);
        
        // Use language-specific chunking when available
        if ("java".equals(language)) {
            chunks.addAll(chunkJavaCode(filePath, content));
        } else if ("python".equals(language)) {
            chunks.addAll(chunkPythonCode(filePath, content));
        } else if ("javascript".equals(language) || "typescript".equals(language)) {
            chunks.addAll(chunkJavaScriptCode(filePath, content, language));
        } else if ("golang".equals(language)) {
            chunks.addAll(chunkGoCode(filePath, content));
        } else {
            // For other files, use simple line-based chunking
            chunks.addAll(chunkGenericCode(filePath, content, language));
        }
        
        // If no chunks were created (e.g., empty file), create a single chunk for the entire file
        if (chunks.isEmpty() && content != null && !content.trim().isEmpty()) {
            chunks.add(createChunk(filePath, content, 1, countLines(content)));
        }
        
        // Add context overlap between chunks if there are multiple chunks
        if (chunks.size() > 1 && overlapLines > 0) {
            addContextOverlap(chunks, content);
        }
        
        // Mark boilerplate if filtering is enabled
        if (filterBoilerplate) {
            markBoilerplateChunks(chunks);
        }
        
        return chunks;
    }
    
    /**
     * Marks chunks that contain boilerplate code.
     */
    private void markBoilerplateChunks(List<CodeChunk> chunks) {
        for (CodeChunk chunk : chunks) {
            // Skip file-level chunks
            if ("file".equals(chunk.getType())) {
                continue;
            }
            
            // Check if this chunk is boilerplate
            if (boilerplateFilter.isBoilerplate(chunk.getContent())) {
                chunk.setBoilerplate(true);
                logger.debug("Marked chunk {} as boilerplate", chunk.getId());
            }
        }
    }
    
    /**
     * Adds context overlap between consecutive chunks.
     */
    private void addContextOverlap(List<CodeChunk> chunks, String fullContent) {
        String[] lines = fullContent.split("\\n");
        
        for (int i = 0; i < chunks.size(); i++) {
            CodeChunk chunk = chunks.get(i);
            
            // Add context before (from previous chunk)
            if (i > 0) {
                int prevEndLine = chunks.get(i-1).getEndLine();
                int contextStartLine = Math.max(chunk.getStartLine() - overlapLines, prevEndLine - overlapLines + 1);
                if (contextStartLine < chunk.getStartLine()) {
                    StringBuilder contextBefore = new StringBuilder();
                    for (int line = contextStartLine - 1; line < chunk.getStartLine() - 1; line++) {
                        if (line >= 0 && line < lines.length) {
                            contextBefore.append(lines[line]).append("\n");
                        }
                    }
                    chunk.setContextBefore(contextBefore.toString());
                }
            }
            
            // Add context after (from next chunk)
            if (i < chunks.size() - 1) {
                int nextStartLine = chunks.get(i+1).getStartLine();
                int contextEndLine = Math.min(chunk.getEndLine() + overlapLines, nextStartLine + overlapLines - 1);
                if (contextEndLine > chunk.getEndLine()) {
                    StringBuilder contextAfter = new StringBuilder();
                    for (int line = chunk.getEndLine(); line < contextEndLine; line++) {
                        if (line >= 0 && line < lines.length) {
                            contextAfter.append(lines[line]).append("\n");
                        }
                    }
                    chunk.setContextAfter(contextAfter.toString());
                }
            }
        }
    }
    
    /**
     * Chunks Java code based on class and method boundaries.
     */
    private List<CodeChunk> chunkJavaCode(String filePath, String content) {
        List<CodeChunk> chunks = new ArrayList<>();
        
        // First, create a chunk for the entire file for context
        CodeChunk fileChunk = createChunk(filePath, content, 1, countLines(content));
        fileChunk.setType("file");
        chunks.add(fileChunk);
        
        String[] lines = content.split("\\n");
        
        // Track class and method boundaries
        List<Boundary> boundaries = new ArrayList<>();
        
        int currentBracketDepth = 0;
        Boundary currentClass = null;
        Boundary currentMethod = null;
        
        // Parse the file to identify class and method boundaries
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            
            // Count opening and closing brackets to track scope
            currentBracketDepth += countChar(line, '{');
            
            // Check for class declarations
            Matcher classMatcher = CLASS_PATTERN.matcher(line);
            if (classMatcher.find()) {
                String classType = classMatcher.group(1); // class, interface, or enum
                String className = classMatcher.group(2);
                
                // Close any current classes if we're at root level
                if (currentBracketDepth == 1 && currentClass != null) {
                    currentClass.end = i;
                    boundaries.add(currentClass);
                    currentClass = null;
                }
                
                // Create a new class boundary
                currentClass = new Boundary();
                currentClass.start = i;
                currentClass.name = className;
                currentClass.type = classType;
            }
            
            // Check for method declarations
            if (currentClass != null && currentMethod == null) {
                Matcher methodMatcher = METHOD_PATTERN.matcher(line);
                if (methodMatcher.find()) {
                    String methodName = methodMatcher.group(1);
                    
                    // Create a new method boundary
                    currentMethod = new Boundary();
                    currentMethod.start = i;
                    currentMethod.name = methodName;
                    currentMethod.type = "method";
                    currentMethod.parentName = currentClass.name;
                }
            }
            
            // Track closing brackets to identify end of methods and classes
            int closingBrackets = countChar(line, '}');
            if (closingBrackets > 0) {
                currentBracketDepth -= closingBrackets;
                
                // Check if a method has ended
                if (currentMethod != null && currentBracketDepth < 2) {
                    currentMethod.end = i;
                    boundaries.add(currentMethod);
                    currentMethod = null;
                }
                
                // Check if a class has ended
                if (currentClass != null && currentBracketDepth == 0) {
                    currentClass.end = i;
                    boundaries.add(currentClass);
                    currentClass = null;
                }
            }
        }
        
        // Close any unclosed boundaries at the end of the file
        if (currentMethod != null) {
            currentMethod.end = lines.length - 1;
            boundaries.add(currentMethod);
        }
        
        if (currentClass != null) {
            currentClass.end = lines.length - 1;
            boundaries.add(currentClass);
        }
        
        // Create chunks for each boundary
        for (Boundary boundary : boundaries) {
            StringBuilder chunkContent = new StringBuilder();
            for (int i = boundary.start; i <= boundary.end; i++) {
                chunkContent.append(lines[i]).append("\n");
            }
            
            CodeChunk chunk = createChunk(
                    filePath,
                    chunkContent.toString(),
                    boundary.start + 1, // Convert to 1-based line numbers
                    boundary.end + 1
            );
            
            chunk.setType(boundary.type);
            
            // Add more context to the chunk ID
            if ("method".equals(boundary.type)) {
                chunk.setId(filePath + "#" + boundary.parentName + "." + boundary.name);
            } else {
                chunk.setId(filePath + "#" + boundary.name);
            }
            
            chunks.add(chunk);
        }
        
        return chunks;
    }
    
    /**
     * Chunks Python code based on class and function boundaries.
     */
    private List<CodeChunk> chunkPythonCode(String filePath, String content) {
        List<CodeChunk> chunks = new ArrayList<>();
        
        // First, create a chunk for the entire file for context
        CodeChunk fileChunk = createChunk(filePath, content, 1, countLines(content));
        fileChunk.setType("file");
        chunks.add(fileChunk);
        
        String[] lines = content.split("\\n");
        
        // Track class and method boundaries
        List<Boundary> boundaries = new ArrayList<>();
        
        int indentLevel = 0;
        Boundary currentClass = null;
        Boundary currentMethod = null;
        int lastLineWithContent = 0;
        
        // Parse the file to identify class and method boundaries
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;  // Skip empty lines and comments
            }
            
            // Calculate current indent level
            int currentIndent = countLeadingSpaces(lines[i]) / 4;  // Assuming 4 spaces per indent
            
            // If we went back in indent level, close any open boundaries
            if (currentIndent < indentLevel) {
                if (currentMethod != null && currentIndent <= 1) {
                    currentMethod.end = lastLineWithContent;
                    boundaries.add(currentMethod);
                    currentMethod = null;
                }
                
                if (currentClass != null && currentIndent == 0) {
                    currentClass.end = lastLineWithContent;
                    boundaries.add(currentClass);
                    currentClass = null;
                }
            }
            
            // Check for class declarations
            Matcher classMatcher = PYTHON_CLASS_PATTERN.matcher(lines[i]);
            if (classMatcher.find()) {
                String className = classMatcher.group(1);
                
                // Close any current classes if we're starting a new one
                if (currentClass != null) {
                    currentClass.end = lastLineWithContent;
                    boundaries.add(currentClass);
                }
                
                // Create a new class boundary
                currentClass = new Boundary();
                currentClass.start = i;
                currentClass.name = className;
                currentClass.type = "class";
                indentLevel = currentIndent + 1;
            }
            
            // Check for method declarations
            Matcher methodMatcher = PYTHON_METHOD_PATTERN.matcher(lines[i]);
            if (methodMatcher.find()) {
                String methodName = methodMatcher.group(1);
                
                // Close any current method if we're starting a new one
                if (currentMethod != null) {
                    currentMethod.end = lastLineWithContent;
                    boundaries.add(currentMethod);
                }
                
                // Create a new method boundary
                currentMethod = new Boundary();
                currentMethod.start = i;
                currentMethod.name = methodName;
                currentMethod.type = "method";
                currentMethod.parentName = currentClass != null ? currentClass.name : null;
                indentLevel = currentIndent + 1;
            }
            
            lastLineWithContent = i;
        }
        
        // Close any open boundaries at end of file
        if (currentMethod != null) {
            currentMethod.end = lastLineWithContent;
            boundaries.add(currentMethod);
        }
        
        if (currentClass != null) {
            currentClass.end = lastLineWithContent;
            boundaries.add(currentClass);
        }
        
        // Create chunks from boundaries
        for (Boundary boundary : boundaries) {
            // Skip if the boundary is invalid
            if (boundary.start > boundary.end || boundary.end >= lines.length) {
                continue;
            }
            
            // Calculate chunk content
            StringBuilder chunkContent = new StringBuilder();
            for (int i = boundary.start; i <= boundary.end; i++) {
                chunkContent.append(lines[i]).append("\n");
            }
            
            // Create chunk
            CodeChunk chunk = createChunk(filePath, chunkContent.toString(), 
                    boundary.start + 1, boundary.end + 1);
            
            // Set type and additional metadata
            chunk.setType(boundary.type);
            chunk.setId(filePath + "#" + boundary.type + ":" + boundary.name);
            
            chunks.add(chunk);
        }
        
        return chunks;
    }
    
    /**
     * Chunks JavaScript/TypeScript code based on class, method and function boundaries.
     */
    private List<CodeChunk> chunkJavaScriptCode(String filePath, String content, String language) {
        List<CodeChunk> chunks = new ArrayList<>();
        
        // First, create a chunk for the entire file for context
        CodeChunk fileChunk = createChunk(filePath, content, 1, countLines(content));
        fileChunk.setType("file");
        chunks.add(fileChunk);
        
        String[] lines = content.split("\\n");
        
        // Track class and method boundaries
        List<Boundary> boundaries = new ArrayList<>();
        
        int currentBracketDepth = 0;
        Boundary currentClass = null;
        Boundary currentMethod = null;
        Boundary currentFunction = null;
        
        // Parse the file to identify class, method and function boundaries
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            
            // Count opening and closing brackets to track scope
            currentBracketDepth += countChar(line, '{');
            
            // Check for class declarations
            Matcher classMatcher = JS_CLASS_PATTERN.matcher(line);
            if (classMatcher.find()) {
                String className = classMatcher.group(1);
                
                // Close any current classes if we're at root level
                if (currentBracketDepth == 1 && currentClass != null) {
                    currentClass.end = i;
                    boundaries.add(currentClass);
                    currentClass = null;
                }
                
                // Create a new class boundary
                currentClass = new Boundary();
                currentClass.start = i;
                currentClass.name = className;
                currentClass.type = "class";
            }
            
            // Check for method declarations within classes
            if (currentClass != null && currentMethod == null) {
                Matcher methodMatcher = JS_METHOD_PATTERN.matcher(line);
                if (methodMatcher.find()) {
                    String methodName = methodMatcher.group(1);
                    
                    // Create a new method boundary
                    currentMethod = new Boundary();
                    currentMethod.start = i;
                    currentMethod.name = methodName;
                    currentMethod.type = "method";
                    currentMethod.parentName = currentClass.name;
                }
            }
            
            // Check for standalone function declarations
            if (currentClass == null && currentFunction == null) {
                Matcher functionMatcher = JS_FUNCTION_PATTERN.matcher(line);
                if (functionMatcher.find()) {
                    String functionName = functionMatcher.group(1);
                    
                    // Create a new function boundary
                    currentFunction = new Boundary();
                    currentFunction.start = i;
                    currentFunction.name = functionName;
                    currentFunction.type = "function";
                }
            }
            
            // Track closing brackets to identify end of methods, functions and classes
            int closingBrackets = countChar(line, '}');
            if (closingBrackets > 0) {
                currentBracketDepth -= closingBrackets;
                
                // Check if a method has ended
                if (currentMethod != null && 
                        (currentBracketDepth < 2 || (i < lines.length - 1 && lines[i + 1].contains("}")))) {
                    currentMethod.end = i;
                    boundaries.add(currentMethod);
                    currentMethod = null;
                }
                
                // Check if a function has ended
                if (currentFunction != null && currentBracketDepth == 0) {
                    currentFunction.end = i;
                    boundaries.add(currentFunction);
                    currentFunction = null;
                }
                
                // Check if a class has ended
                if (currentClass != null && currentBracketDepth == 0) {
                    currentClass.end = i;
                    boundaries.add(currentClass);
                    currentClass = null;
                }
            }
        }
        
        // Close any open boundaries at end of file
        if (currentMethod != null) {
            currentMethod.end = lines.length - 1;
            boundaries.add(currentMethod);
        }
        
        if (currentFunction != null) {
            currentFunction.end = lines.length - 1;
            boundaries.add(currentFunction);
        }
        
        if (currentClass != null) {
            currentClass.end = lines.length - 1;
            boundaries.add(currentClass);
        }
        
        // Create chunks from boundaries
        for (Boundary boundary : boundaries) {
            // Skip if the boundary is invalid
            if (boundary.start > boundary.end || boundary.end >= lines.length) {
                continue;
            }
            
            // Calculate chunk content
            StringBuilder chunkContent = new StringBuilder();
            for (int i = boundary.start; i <= boundary.end; i++) {
                chunkContent.append(lines[i]).append("\n");
            }
            
            // Create chunk
            CodeChunk chunk = createChunk(filePath, chunkContent.toString(), 
                    boundary.start + 1, boundary.end + 1);
            
            // Set type and additional metadata
            chunk.setType(boundary.type);
            chunk.setId(filePath + "#" + boundary.type + ":" + boundary.name);
            
            chunks.add(chunk);
        }
        
        return chunks;
    }
    
    /**
     * Chunks Go code based on struct, interface and function boundaries.
     */
    private List<CodeChunk> chunkGoCode(String filePath, String content) {
        List<CodeChunk> chunks = new ArrayList<>();
        
        // First, create a chunk for the entire file for context
        CodeChunk fileChunk = createChunk(filePath, content, 1, countLines(content));
        fileChunk.setType("file");
        chunks.add(fileChunk);
        
        String[] lines = content.split("\\n");
        
        // Track struct, interface and function boundaries
        List<Boundary> boundaries = new ArrayList<>();
        
        int currentBracketDepth = 0;
        Boundary currentStruct = null;
        Boundary currentInterface = null;
        Boundary currentFunction = null;
        
        // Parse the file to identify struct, interface and function boundaries
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            
            // Count opening and closing brackets to track scope
            currentBracketDepth += countChar(line, '{');
            
            // Check for struct declarations
            Matcher structMatcher = GO_STRUCT_PATTERN.matcher(line);
            if (structMatcher.find()) {
                String structName = structMatcher.group(1);
                
                // Create a new struct boundary
                currentStruct = new Boundary();
                currentStruct.start = i;
                currentStruct.name = structName;
                currentStruct.type = "struct";
            }
            
            // Check for interface declarations
            Matcher interfaceMatcher = GO_INTERFACE_PATTERN.matcher(line);
            if (interfaceMatcher.find()) {
                String interfaceName = interfaceMatcher.group(1);
                
                // Create a new interface boundary
                currentInterface = new Boundary();
                currentInterface.start = i;
                currentInterface.name = interfaceName;
                currentInterface.type = "interface";
            }
            
            // Check for function declarations
            Matcher functionMatcher = GO_FUNCTION_PATTERN.matcher(line);
            if (functionMatcher.find()) {
                String functionName = functionMatcher.group(1);
                
                // Create a new function boundary
                currentFunction = new Boundary();
                currentFunction.start = i;
                currentFunction.name = functionName;
                currentFunction.type = "function";
            }
            
            // Track closing brackets to identify end of structures
            int closingBrackets = countChar(line, '}');
            if (closingBrackets > 0) {
                currentBracketDepth -= closingBrackets;
                
                // Check if a struct has ended
                if (currentStruct != null && currentBracketDepth == 0) {
                    currentStruct.end = i;
                    boundaries.add(currentStruct);
                    currentStruct = null;
                }
                
                // Check if an interface has ended
                if (currentInterface != null && currentBracketDepth == 0) {
                    currentInterface.end = i;
                    boundaries.add(currentInterface);
                    currentInterface = null;
                }
                
                // Check if a function has ended
                if (currentFunction != null && currentBracketDepth == 0) {
                    currentFunction.end = i;
                    boundaries.add(currentFunction);
                    currentFunction = null;
                }
            }
        }
        
        // Close any open boundaries at end of file
        if (currentStruct != null) {
            currentStruct.end = lines.length - 1;
            boundaries.add(currentStruct);
        }
        
        if (currentInterface != null) {
            currentInterface.end = lines.length - 1;
            boundaries.add(currentInterface);
        }
        
        if (currentFunction != null) {
            currentFunction.end = lines.length - 1;
            boundaries.add(currentFunction);
        }
        
        // Create chunks from boundaries
        for (Boundary boundary : boundaries) {
            // Skip if the boundary is invalid
            if (boundary.start > boundary.end || boundary.end >= lines.length) {
                continue;
            }
            
            // Calculate chunk content
            StringBuilder chunkContent = new StringBuilder();
            for (int i = boundary.start; i <= boundary.end; i++) {
                chunkContent.append(lines[i]).append("\n");
            }
            
            // Create chunk
            CodeChunk chunk = createChunk(filePath, chunkContent.toString(), 
                    boundary.start + 1, boundary.end + 1);
            
            // Set type and additional metadata
            chunk.setType(boundary.type);
            chunk.setId(filePath + "#" + boundary.type + ":" + boundary.name);
            
            chunks.add(chunk);
        }
        
        return chunks;
    }
    
    /**
     * Counts the number of leading whitespace characters in a string.
     */
    private int countLeadingSpaces(String str) {
        int count = 0;
        for (int i = 0; i < str.length(); i++) {
            if (str.charAt(i) == ' ') {
                count++;
            } else {
                break;
            }
        }
        return count;
    }
    
    /**
     * Creates a code chunk with the specified properties.
     */
    public CodeChunk createChunk(String filePath, String content, int startLine, int endLine) {
        CodeChunk chunk = new CodeChunk();
        chunk.setId(UUID.randomUUID().toString());
        chunk.setFilePath(filePath);
        chunk.setContent(content);
        chunk.setStartLine(startLine);
        chunk.setEndLine(endLine);
        chunk.setLanguage(detectLanguage(filePath, content));
        return chunk;
    }
    
    /**
     * Detects the programming language of a file based on its extension and content.
     */
    public String detectLanguage(String filePath, String content) {
        if (filePath == null) {
            return "plaintext";
        }
        
        String extension = getFileExtension(filePath).toLowerCase();
        return LANGUAGE_EXTENSIONS.getOrDefault(extension, "plaintext");
    }
    
    /**
     * Returns the file extension (without the dot) or an empty string if none exists.
     */
    private String getFileExtension(String filePath) {
        if (filePath == null) {
            return "";
        }
        
        int lastDot = filePath.lastIndexOf('.');
        if (lastDot >= 0 && lastDot < filePath.length() - 1) {
            return filePath.substring(lastDot + 1);
        }
        return "";
    }
    
    /**
     * Counts occurrences of a character in a string.
     */
    private int countChar(String str, char target) {
        if (str == null) {
            return 0;
        }
        
        int count = 0;
        for (char c : str.toCharArray()) {
            if (c == target) count++;
        }
        return count;
    }
    
    /**
     * Counts the number of lines in a text.
     */
    private int countLines(String str) {
        if (str == null || str.isEmpty()) {
            return 0;
        }
        return str.split("\n").length + 1;
    }
    
    /**
     * Sets whether to filter out boilerplate code.
     */
    public void setFilterBoilerplate(boolean filterBoilerplate) {
        this.filterBoilerplate = filterBoilerplate;
    }
    
    /**
     * Gets whether boilerplate code filtering is enabled.
     */
    public boolean isFilterBoilerplate() {
        return filterBoilerplate;
    }
    
    /**
     * Sets the number of overlap lines between chunks.
     */
    public void setOverlapLines(int overlapLines) {
        this.overlapLines = overlapLines;
    }
    
    /**
     * Gets the number of overlap lines between chunks.
     */
    public int getOverlapLines() {
        return overlapLines;
    }
    
    /**
     * Sets the maximum chunk size in lines.
     */
    public void setMaxChunkSize(int maxChunkSize) {
        this.maxChunkSize = maxChunkSize;
    }
    
    /**
     * Gets the maximum chunk size in lines.
     */
    public int getMaxChunkSize() {
        return maxChunkSize;
    }
    
    /**
     * Generic chunking method for languages without specific parsing rules.
     * Creates simple line-based chunks for arbitrary code files.
     *
     * @param filePath Path to the file being chunked
     * @param content The content to chunk
     * @param language The detected language of the content
     * @return List of code chunks
     */
    private List<CodeChunk> chunkGenericCode(String filePath, String content, String language) {
        List<CodeChunk> chunks = new ArrayList<>();
        
        // Create a chunk for the entire file for context
        CodeChunk fileChunk = createChunk(filePath, content, 1, countLines(content));
        fileChunk.setType("file");
        chunks.add(fileChunk);
        
        // If the file is small enough, we're done
        if (countLines(content) <= maxChunkSize) {
            return chunks;
        }
        
        // For larger files, split into chunks based on maxChunkSize
        String[] lines = content.split("\\n");
        int totalLines = lines.length;
        
        for (int i = 0; i < totalLines; i += maxChunkSize) {
            int endLine = Math.min(i + maxChunkSize, totalLines);
            
            // Skip if chunk would be too small
            if (endLine - i < minChunkSize) {
                continue;
            }
            
            // Build chunk content
            StringBuilder chunkContent = new StringBuilder();
            for (int j = i; j < endLine; j++) {
                if (j < lines.length) {
                    chunkContent.append(lines[j]).append("\n");
                }
            }
            
            // Create and configure the chunk
            CodeChunk chunk = createChunk(filePath, chunkContent.toString(), i + 1, endLine);
            chunk.setType("chunk");
            chunk.setId(filePath + "#chunk-" + (i + 1) + "-" + endLine);
            
            chunks.add(chunk);
        }
        
        return chunks;
    }
    
    private static class Boundary {
        int start;
        int end;
        String name;
        String type; // "class", "interface", "enum", "method"
        String parentName; // For methods, the name of the containing class
    }
} 