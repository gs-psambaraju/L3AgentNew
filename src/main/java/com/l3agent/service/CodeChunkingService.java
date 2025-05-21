package com.l3agent.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.l3agent.util.BoilerplateFilter;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for breaking code into appropriate chunks for embedding and semantic search.
 */
@Service
public class CodeChunkingService {
    
    private static final Logger logger = LoggerFactory.getLogger(CodeChunkingService.class);
    
    @Autowired
    private BoilerplateFilter boilerplateFilter;
    
    // Configuration for chunking
    @Value("${l3agent.chunking.max-chunk-size:8000}")
    private int maxChunkSize = 8000;        // Maximum characters per chunk
    
    @Value("${l3agent.chunking.overlap-size:200}")
    private int overlapSize = 200;           // Overlap between chunks for context
    
    @Value("${l3agent.chunking.min-chunk-size:100}")
    private int minChunkSize = 100;         // Minimum characters for a valid chunk
    
    @Value("${l3agent.chunking.context-overlap-percentage:15}")
    private int contextOverlapPercentage = 15;  // Percentage of content to include as context overlap
    
    // Log extraction pattern
    private static final Pattern LOG_PATTERN = Pattern.compile("\\b(log|logger)\\.(debug|info|warn|error|trace)\\s*\\(([^;]+)\\);");
    
    /**
     * Break a code file into appropriate chunks for semantic search.
     * Uses a natural boundary approach where possible.
     * 
     * @param filePath The path of the file being chunked
     * @param content The content of the file to chunk
     * @return List of code chunks suitable for embedding
     */
    public List<CodeChunk> chunkCodeFile(String filePath, String content) {
        return chunkCodeFile(filePath, content, overlapSize);
    }
    
    /**
     * Break a code file into appropriate chunks for semantic search with configurable overlap.
     * Uses a natural boundary approach where possible.
     * 
     * @param filePath The path of the file being chunked
     * @param content The content of the file to chunk
     * @param overlapSize The number of characters to overlap between chunks
     * @return List of code chunks suitable for embedding
     */
    public List<CodeChunk> chunkCodeFile(String filePath, String content, int overlapSize) {
        List<CodeChunk> chunks = new ArrayList<>();
        
        // Extract file extension and language
        String language = extractLanguage(filePath);
        
        // Extract logs for Java files
        List<LogMetadata> logs = new ArrayList<>();
        if (filePath.endsWith(".java")) {
            logs = extractLogs(content);
        }
        
        // If file is small enough, keep it as a single chunk
        if (content.length() <= maxChunkSize) {
            CodeChunk chunk = new CodeChunk();
            chunk.setId(filePath + "#" + 1);
            chunk.setFilePath(filePath);
            chunk.setContent(content);
            chunk.setType("file");
            chunk.setStartLine(1);
            chunk.setEndLine(countLines(content));
            chunk.setLanguage(language);
            chunk.setLogs(logs);
            chunks.add(chunk);
            return chunks;
        }
        
        // For larger files, split into chunks with overlap
        List<String> textChunks = chunkText(content, maxChunkSize, overlapSize);
        
        int startLine = 1;
        for (int i = 0; i < textChunks.size(); i++) {
            String chunkContent = textChunks.get(i);
            int lineCount = countLines(chunkContent);
            
            CodeChunk chunk = new CodeChunk();
            chunk.setId(filePath + "#" + (i + 1));
            chunk.setFilePath(filePath);
            chunk.setContent(chunkContent);
            chunk.setType("chunk");
            chunk.setStartLine(startLine);
            chunk.setEndLine(startLine + lineCount - 1);
            chunk.setLanguage(language);
            
            // Add context overlap from adjacent chunks
            if (i > 0) {
                // Add context before from the previous chunk
                String previousChunk = textChunks.get(i - 1);
                int contextSize = Math.min(previousChunk.length(), 
                        (int)(chunkContent.length() * (contextOverlapPercentage / 100.0)));
                if (contextSize > 0) {
                    chunk.setContextBefore(previousChunk.substring(
                            Math.max(0, previousChunk.length() - contextSize)));
                }
            }
            
            if (i < textChunks.size() - 1) {
                // Add context after from the next chunk
                String nextChunk = textChunks.get(i + 1);
                int contextSize = Math.min(nextChunk.length(), 
                        (int)(chunkContent.length() * (contextOverlapPercentage / 100.0)));
                if (contextSize > 0) {
                    chunk.setContextAfter(nextChunk.substring(0, contextSize));
                }
            }
            
            // Associate logs that fall within this chunk's line range
            List<LogMetadata> chunkLogs = new ArrayList<>();
            for (LogMetadata log : logs) {
                if (log.getLine() >= startLine && log.getLine() <= (startLine + lineCount - 1)) {
                    chunkLogs.add(log);
                }
            }
            chunk.setLogs(chunkLogs);
            
            chunks.add(chunk);
            
            // Update the start line for the next chunk, accounting for overlap
            if (i < textChunks.size() - 1) {
                // Calculate lines to move forward (subtract overlap lines)
                String overlapContent = textChunks.get(i).substring(Math.max(0, textChunks.get(i).length() - overlapSize));
                int overlapLines = countLines(overlapContent);
                startLine += (lineCount - overlapLines);
            }
        }
        
        logger.debug("Split file {} into {} chunks", filePath, chunks.size());
        return chunks;
    }
    
    /**
     * Process chunks in batches for efficient processing.
     * 
     * @param chunks List of code chunks to process
     * @param batchSize Maximum number of chunks per batch
     * @return List of batches, where each batch is a list of chunks
     */
    public List<List<CodeChunk>> batchChunks(List<CodeChunk> chunks, int batchSize) {
        List<List<CodeChunk>> batches = new ArrayList<>();
        if (chunks == null || chunks.isEmpty()) {
            return batches;
        }
        
        int totalChunks = chunks.size();
        for (int i = 0; i < totalChunks; i += batchSize) {
            int end = Math.min(i + batchSize, totalChunks);
            batches.add(new ArrayList<>(chunks.subList(i, end)));
        }
        
        logger.debug("Split {} chunks into {} batches of size up to {}", 
                totalChunks, batches.size(), batchSize);
        return batches;
    }
    
    /**
     * Detect if a code chunk is likely boilerplate code.
     * 
     * @param chunk The code chunk to analyze
     * @return True if the chunk is detected as boilerplate, false otherwise
     */
    public boolean isBoilerplate(CodeChunk chunk) {
        // Delegate to BoilerplateFilter utility
        return boilerplateFilter.isBoilerplateCode(chunk.getContent(), chunk.getLanguage());
    }
    
    /**
     * Splits text into chunks of the specified size with overlap between chunks.
     */
    private List<String> chunkText(String text, int chunkSize, int overlapSize) {
        List<String> chunks = new ArrayList<>();
        int len = text.length();
        
        // Handle empty or very small texts
        if (len <= 0) {
            return chunks;
        }
        
        if (len <= chunkSize) {
            chunks.add(text);
            return chunks;
        }
        
        // Create chunks with overlap
        for (int i = 0; i < len; i += (chunkSize - overlapSize)) {
            int end = Math.min(len, i + chunkSize);
            
            // Ensure minimum chunk size
            if (end - i < minChunkSize && chunks.size() > 0) {
                // If chunk is too small, add it to the previous chunk
                String lastChunk = chunks.get(chunks.size() - 1);
                chunks.set(chunks.size() - 1, lastChunk + text.substring(i, end));
            } else {
                chunks.add(text.substring(i, end));
            }
        }
        
        return chunks;
    }
    
    /**
     * Counts the number of lines in a text.
     */
    private int countLines(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return text.split("\n").length;
    }
    
    /**
     * Extracts the programming language from the file path based on extension.
     */
    private String extractLanguage(String filePath) {
        if (filePath.endsWith(".java")) {
            return "java";
        } else if (filePath.endsWith(".py")) {
            return "python";
        } else if (filePath.endsWith(".js")) {
            return "javascript";
        } else if (filePath.endsWith(".ts")) {
            return "typescript";
        } else if (filePath.endsWith(".html") || filePath.endsWith(".htm")) {
            return "html";
        } else if (filePath.endsWith(".css")) {
            return "css";
        } else if (filePath.endsWith(".xml")) {
            return "xml";
        } else if (filePath.endsWith(".json")) {
            return "json";
        } else if (filePath.endsWith(".yml") || filePath.endsWith(".yaml")) {
            return "yaml";
        } else if (filePath.endsWith(".properties")) {
            return "properties";
        } else {
            return "plaintext";
        }
    }
    
    /**
     * Extracts log statements from code.
     */
    private List<LogMetadata> extractLogs(String content) {
        List<LogMetadata> logs = new ArrayList<>();
        Matcher matcher = LOG_PATTERN.matcher(content);
        
        // Split by new line to have line numbers available
        String[] lines = content.split("\n");
        
        while (matcher.find()) {
            String logType = matcher.group(2);       // debug, info, warn, error, trace
            String logMessage = matcher.group(3);    // Content inside the parentheses
            
            // Find line number by counting newlines before this position
            int position = matcher.start();
            int lineCount = 1;
            for (int i = 0; i < position; i++) {
                if (content.charAt(i) == '\n') {
                    lineCount++;
                }
            }
            
            LogMetadata logMetadata = new LogMetadata();
            logMetadata.setType(logType);
            logMetadata.setMessage(logMessage.trim());
            logMetadata.setLine(lineCount);
            logs.add(logMetadata);
        }
        
        return logs;
    }
    
    /**
     * Represents a chunk of code.
     */
    public static class CodeChunk {
        private String id;
        private String filePath;
        private String content;
        private String type;  // "file", "class", "method", "chunk", etc.
        private int startLine;
        private int endLine;
        private String language;
        private List<LogMetadata> logs = new ArrayList<>();
        private boolean boilerplate = false;
        private String contextBefore = "";
        private String contextAfter = "";
        
        // Getters and setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        
        public String getFilePath() { return filePath; }
        public void setFilePath(String filePath) { this.filePath = filePath; }
        
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        
        public int getStartLine() { return startLine; }
        public void setStartLine(int startLine) { this.startLine = startLine; }
        
        public int getEndLine() { return endLine; }
        public void setEndLine(int endLine) { this.endLine = endLine; }
        
        public String getLanguage() { return language; }
        public void setLanguage(String language) { this.language = language; }
        
        public List<LogMetadata> getLogs() { return logs; }
        public void setLogs(List<LogMetadata> logs) { this.logs = logs; }
        
        public boolean isBoilerplate() { return boilerplate; }
        public void setBoilerplate(boolean boilerplate) { this.boilerplate = boilerplate; }
        
        public String getContextBefore() { return contextBefore; }
        public void setContextBefore(String contextBefore) { this.contextBefore = contextBefore; }
        
        public String getContextAfter() { return contextAfter; }
        public void setContextAfter(String contextAfter) { this.contextAfter = contextAfter; }
        
        /**
         * Gets the content with surrounding context.
         * 
         * @return The combined content with context
         */
        public String getContentWithContext() {
            StringBuilder builder = new StringBuilder();
            if (contextBefore != null && !contextBefore.isEmpty()) {
                builder.append(contextBefore);
            }
            builder.append(content);
            if (contextAfter != null && !contextAfter.isEmpty()) {
                builder.append(contextAfter);
            }
            return builder.toString();
        }
    }
    
    /**
     * Represents a log statement found in code.
     */
    public static class LogMetadata {
        private String type;      // debug, info, warn, error, trace
        private String message;   // Log message content
        private int line;         // Line number
        
        // Getters and setters
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        
        public int getLine() { return line; }
        public void setLine(int line) { this.line = line; }
    }
} 