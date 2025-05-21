package com.l3agent.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.l3agent.model.LogEntry;
import com.l3agent.service.LogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A file-based implementation of the LogService interface.
 * Processes log files stored in a specified directory.
 */
@Service
public class FileBasedLogService implements LogService {
    private static final Logger logger = LoggerFactory.getLogger(FileBasedLogService.class);
    
    private final Path logsDirectory;
    private final ObjectMapper objectMapper;
    
    /**
     * Constructs a new FileBasedLogService.
     * 
     * @param logsDirectory The directory containing the log files
     */
    public FileBasedLogService(@Value("${l3agent.logs.directory:./data/logs}") String logsDirectory) {
        this.logsDirectory = Paths.get(logsDirectory);
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        
        try {
            // Create the logs directory if it doesn't exist
            if (!Files.exists(this.logsDirectory)) {
                Files.createDirectories(this.logsDirectory);
                logger.info("Created logs directory: {}", this.logsDirectory);
            }
        } catch (IOException e) {
            logger.error("Error initializing log service", e);
            throw new RuntimeException("Error initializing log service", e);
        }
    }
    
    @Override
    public List<LogEntry> searchLogs(String query, Instant startTime, Instant endTime, int maxResults) {
        logger.info("Searching logs for: {} (from {} to {})", query, startTime, endTime);
        
        Pattern pattern = Pattern.compile(Pattern.quote(query), Pattern.CASE_INSENSITIVE);
        
        return findLogEntries(
                entry -> (entry.getMessage() != null && pattern.matcher(entry.getMessage()).find()) &&
                         isInTimeRange(entry, startTime, endTime),
                maxResults
        );
    }
    
    @Override
    public List<LogEntry> getServiceLogs(String service, Instant startTime, Instant endTime, int maxResults) {
        logger.info("Getting logs for service: {} (from {} to {})", service, startTime, endTime);
        
        return findLogEntries(
                entry -> service.equalsIgnoreCase(entry.getService()) &&
                         isInTimeRange(entry, startTime, endTime),
                maxResults
        );
    }
    
    @Override
    public List<LogEntry> getLogsByLevel(LogEntry.LogLevel level, Instant startTime, Instant endTime, int maxResults) {
        logger.info("Getting logs with level: {} (from {} to {})", level, startTime, endTime);
        
        return findLogEntries(
                entry -> level == entry.getLevel() &&
                         isInTimeRange(entry, startTime, endTime),
                maxResults
        );
    }
    
    @Override
    public List<ErrorPattern> analyzeErrors(List<LogEntry> logs) {
        logger.info("Analyzing {} log entries for error patterns", logs.size());
        
        // Group errors by common patterns
        Map<String, List<LogEntry>> patternGroups = new HashMap<>();
        
        for (LogEntry entry : logs) {
            // Only process ERROR or FATAL level logs
            if (entry.getLevel() != LogEntry.LogLevel.ERROR && entry.getLevel() != LogEntry.LogLevel.FATAL) {
                continue;
            }
            
            String message = entry.getMessage();
            if (message == null) {
                continue;
            }
            
            // Extract error pattern - simplistic approach for POC
            // In a real implementation, we would use more sophisticated pattern extraction
            String errorPattern = extractErrorPattern(message);
            
            patternGroups.computeIfAbsent(errorPattern, k -> new ArrayList<>()).add(entry);
        }
        
        // Convert to ErrorPattern objects
        List<ErrorPattern> errorPatterns = new ArrayList<>();
        for (Map.Entry<String, List<LogEntry>> entry : patternGroups.entrySet()) {
            if (entry.getValue().size() >= 2) { // Only include patterns with at least 2 occurrences
                errorPatterns.add(new ErrorPattern(
                        entry.getKey(),
                        entry.getValue().size(),
                        new ArrayList<>(entry.getValue().subList(0, Math.min(3, entry.getValue().size())))
                ));
            }
        }
        
        // Sort by frequency (descending)
        errorPatterns.sort((p1, p2) -> Integer.compare(p2.getFrequency(), p1.getFrequency()));
        
        return errorPatterns;
    }
    
    /**
     * Finds log entries matching the given predicate.
     * 
     * @param predicate The predicate to filter log entries
     * @param maxResults The maximum number of results to return
     * @return A list of matching log entries
     */
    private List<LogEntry> findLogEntries(Predicate<LogEntry> predicate, int maxResults) {
        List<LogEntry> results = new ArrayList<>();
        
        try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(logsDirectory, "*.log*")) {
            for (Path logFile : directoryStream) {
                if (results.size() >= maxResults) {
                    break;
                }
                
                processLogFile(logFile, predicate, results, maxResults);
            }
        } catch (IOException e) {
            logger.error("Error reading log files", e);
        }
        
        return results;
    }
    
    /**
     * Processes a log file to find entries matching the given predicate.
     * 
     * @param logFile The log file to process
     * @param predicate The predicate to filter log entries
     * @param results The list to add results to
     * @param maxResults The maximum number of results to collect
     */
    private void processLogFile(Path logFile, Predicate<LogEntry> predicate, List<LogEntry> results, int maxResults) {
        logger.debug("Processing log file: {}", logFile);
        
        try {
            List<String> lines = Files.readAllLines(logFile, StandardCharsets.UTF_8);
            
            for (String line : lines) {
                if (results.size() >= maxResults) {
                    break;
                }
                
                try {
                    LogEntry entry = objectMapper.readValue(line, LogEntry.class);
                    if (predicate.test(entry)) {
                        results.add(entry);
                    }
                } catch (IOException e) {
                    // Skip lines that can't be parsed as JSON
                    logger.trace("Skipping non-JSON line: {}", line);
                }
            }
        } catch (IOException e) {
            logger.error("Error processing log file: {}", logFile, e);
        }
    }
    
    /**
     * Checks if a log entry's timestamp is within the given time range.
     * 
     * @param entry The log entry to check
     * @param startTime The start of the time range (inclusive)
     * @param endTime The end of the time range (inclusive)
     * @return true if the entry is within the time range, false otherwise
     */
    private boolean isInTimeRange(LogEntry entry, Instant startTime, Instant endTime) {
        Instant timestamp = entry.getTimestamp();
        return timestamp != null &&
               (startTime == null || !timestamp.isBefore(startTime)) &&
               (endTime == null || !timestamp.isAfter(endTime));
    }
    
    /**
     * Extracts an error pattern from a log message.
     * This is a simplified implementation for the POC.
     * 
     * @param message The log message to extract the pattern from
     * @return The extracted error pattern
     */
    private String extractErrorPattern(String message) {
        // Replace specific details with placeholders to identify common patterns
        return message
                // Replace numbers with <NUM>
                .replaceAll("\\b\\d+\\b", "<NUM>")
                // Replace UUIDs, hashes, etc. with <ID>
                .replaceAll("\\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\b", "<ID>")
                .replaceAll("\\b[0-9a-f]{32}\\b", "<ID>")
                // Replace file paths with <PATH>
                .replaceAll("/[\\w/.-]+", "<PATH>")
                .replaceAll("\\\\[\\w\\\\.-]+", "<PATH>");
    }
} 