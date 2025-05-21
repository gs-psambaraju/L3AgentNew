package com.l3agent.mcp.tools.errorchain.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Contains the results of analyzing an exception chain.
 * Includes information about propagation paths, wrapping patterns, 
 * logging behavior, and potential error handling anti-patterns.
 */
public class ErrorChainResult {
    
    private String exceptionClass;
    private List<ExceptionNode> exceptionHierarchy;
    private List<PropagationChain> propagationChains;
    private Map<String, List<String>> wrappingPatterns;
    private List<LoggingPattern> loggingPatterns;
    private Map<String, Map<String, List<String>>> detectedAntiPatterns;
    private Map<String, Integer> commonErrorMessages;
    private List<HandlingStrategy> handlingStrategies;
    private Map<String, String> rethrowLocations;
    private List<String> analysisNotes;
    private Set<String> throwLocations;
    private Set<String> catchLocations;
    private Map<String, String> recommendations;
    
    /**
     * Creates a new empty result for the specified exception class.
     * 
     * @param exceptionClass The fully qualified name of the exception
     */
    public ErrorChainResult(String exceptionClass) {
        this.exceptionClass = exceptionClass;
        this.exceptionHierarchy = new ArrayList<>();
        this.propagationChains = new ArrayList<>();
        this.wrappingPatterns = new HashMap<>();
        this.loggingPatterns = new ArrayList<>();
        this.detectedAntiPatterns = new HashMap<>();
        this.commonErrorMessages = new HashMap<>();
        this.handlingStrategies = new ArrayList<>();
        this.rethrowLocations = new HashMap<>();
        this.analysisNotes = new ArrayList<>();
        this.recommendations = new HashMap<>();
    }
    
    /**
     * Adds an exception node to the hierarchy.
     * 
     * @param node The exception node to add
     */
    public void addExceptionHierarchy(ExceptionNode node) {
        this.exceptionHierarchy.add(node);
    }
    
    /**
     * Adds a propagation chain.
     * 
     * @param chain The propagation chain to add
     */
    public void addPropagationChain(PropagationChain chain) {
        this.propagationChains.add(chain);
    }
    
    /**
     * Adds a wrapping pattern.
     * 
     * @param wrapperException The exception that wraps
     * @param wrappedException The exception being wrapped
     * @param occurrences Number of times this pattern was found
     */
    public void addWrappingPattern(String wrapperException, String wrappedException, Integer occurrences) {
        String key = wrapperException + " -> " + wrappedException;
        List<String> details = wrappingPatterns.computeIfAbsent(key, k -> new ArrayList<>());
        details.add(String.format("Found %d occurrences of %s wrapped by %s", 
                occurrences, wrappedException, wrapperException));
    }
    
    /**
     * Adds a logging pattern.
     * 
     * @param pattern The logging pattern to add
     */
    public void addLoggingPattern(LoggingPattern pattern) {
        this.loggingPatterns.add(pattern);
    }
    
    /**
     * Adds a detected anti-pattern.
     * 
     * @param antiPatternType The type of anti-pattern
     * @param description Description of the anti-pattern
     * @param locations Code locations where the anti-pattern was found
     */
    public void addAntiPattern(String antiPatternType, String description, List<String> locations) {
        Map<String, List<String>> antiPattern = detectedAntiPatterns.computeIfAbsent(antiPatternType, k -> new HashMap<>());
        antiPattern.put(description, locations);
    }
    
    /**
     * Adds a common error message.
     * 
     * @param message The error message
     * @param occurrences Number of times this message was found
     */
    public void addCommonErrorMessage(String message, Integer occurrences) {
        this.commonErrorMessages.put(message, occurrences);
    }
    
    /**
     * Adds a handling strategy.
     * 
     * @param strategy The handling strategy to add
     */
    public void addHandlingStrategy(HandlingStrategy strategy) {
        this.handlingStrategies.add(strategy);
    }
    
    /**
     * Adds a rethrow location.
     * 
     * @param location The code location
     * @param description Description of how the exception is rethrown
     */
    public void addRethrowLocation(String location, String description) {
        this.rethrowLocations.put(location, description);
    }
    
    /**
     * Adds an analysis note.
     * 
     * @param note The note to add
     */
    public void addAnalysisNote(String note) {
        this.analysisNotes.add(note);
    }
    
    /**
     * Sets the throw locations.
     * 
     * @param throwLocations Set of code locations where the exception is thrown
     */
    public void setThrowLocations(Set<String> throwLocations) {
        this.throwLocations = throwLocations;
    }
    
    /**
     * Sets the catch locations.
     * 
     * @param catchLocations Set of code locations where the exception is caught
     */
    public void setCatchLocations(Set<String> catchLocations) {
        this.catchLocations = catchLocations;
    }
    
    /**
     * Adds an enhanced anti-pattern entry with detailed information.
     * 
     * @param name The name of the anti-pattern
     * @param details A map containing details about the anti-pattern
     */
    public void addEnhancedAntiPattern(String name, Map<String, List<String>> details) {
        detectedAntiPatterns.put(name, details);
    }
    
    /**
     * Adds a recommendation for handling this exception type.
     * 
     * @param title The title of the recommendation
     * @param description The description of the recommendation
     */
    public void addRecommendation(String title, String description) {
        recommendations.put(title, description);
    }
    
    /**
     * Gets the recommendations for handling this exception type.
     * 
     * @return The recommendations map
     */
    public Map<String, String> getRecommendations() {
        return recommendations;
    }
    
    // Getters
    
    public String getExceptionClass() {
        return exceptionClass;
    }
    
    public List<ExceptionNode> getExceptionHierarchy() {
        return exceptionHierarchy;
    }
    
    public List<PropagationChain> getPropagationChains() {
        return propagationChains;
    }
    
    public Map<String, List<String>> getWrappingPatterns() {
        return wrappingPatterns;
    }
    
    public List<LoggingPattern> getLoggingPatterns() {
        return loggingPatterns;
    }
    
    public Map<String, Map<String, List<String>>> getDetectedAntiPatterns() {
        return detectedAntiPatterns;
    }
    
    public Map<String, Integer> getCommonErrorMessages() {
        return commonErrorMessages;
    }
    
    public List<HandlingStrategy> getHandlingStrategies() {
        return handlingStrategies;
    }
    
    public Map<String, String> getRethrowLocations() {
        return rethrowLocations;
    }
    
    public List<String> getAnalysisNotes() {
        return analysisNotes;
    }
    
    public Set<String> getThrowLocations() {
        return throwLocations;
    }
    
    public Set<String> getCatchLocations() {
        return catchLocations;
    }
} 