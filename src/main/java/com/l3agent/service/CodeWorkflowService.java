package com.l3agent.service;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * Service interface for analyzing code workflows.
 * Provides methods to analyze code execution paths, method calls, and data flows.
 */
public interface CodeWorkflowService {
    
    /**
     * Analyzes code for workflow information.
     * 
     * @param path Path to code files or directory
     * @param recursive Whether to recursively process subdirectories
     * @return Result map containing workflow analysis metadata
     */
    Map<String, Object> analyzeCodeWorkflow(String path, boolean recursive);
    
    /**
     * Analyzes code for workflow information with optional cross-repository analysis.
     * 
     * @param path Path to code files or directory
     * @param recursive Whether to recursively process subdirectories
     * @param enableCrossRepoAnalysis Whether to analyze workflows across repository boundaries
     * @return Result map containing workflow analysis metadata
     */
    Map<String, Object> analyzeCodeWorkflow(String path, boolean recursive, boolean enableCrossRepoAnalysis);
    
    /**
     * Finds workflow steps involving a specific file path.
     * 
     * @param filePath The file path to search for
     * @return List of workflow steps where this file is either the source or target
     */
    List<WorkflowStep> findWorkflowsByFilePath(String filePath);
    
    /**
     * Finds complete execution paths that include a specific file.
     * 
     * @param filePath The file path to search for
     * @return A list of execution paths (each a sequence of workflow steps) involving the file
     */
    List<List<WorkflowStep>> findExecutionPathsByFilePath(String filePath);
    
    /**
     * Checks if the workflow service is available and ready to use.
     * 
     * @return true if the service is available, false otherwise
     */
    boolean isAvailable();
    
    /**
     * Represents a step in a code workflow, typically a method call from one file to another.
     */
    class WorkflowStep implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private String sourceRepository;
        private String sourceFile;
        private String sourceMethod;
        private String targetRepository;
        private String targetFile;
        private String targetMethod;
        private boolean crossRepository;
        private List<String> dataParameters;
        private double confidence = 1.0; // Default to high confidence
        private String patternType; // E.g., "factory", "builder", "dependency-injection"

        // Getters and setters
        
        public String getSourceRepository() {
            return sourceRepository;
        }
        
        public void setSourceRepository(String sourceRepository) {
            this.sourceRepository = sourceRepository;
        }
        
        public String getSourceFile() {
            return sourceFile;
        }
        
        public void setSourceFile(String sourceFile) {
            this.sourceFile = sourceFile;
        }
        
        public String getSourceMethod() {
            return sourceMethod;
        }
        
        public void setSourceMethod(String sourceMethod) {
            this.sourceMethod = sourceMethod;
        }
        
        public String getTargetRepository() {
            return targetRepository;
        }
        
        public void setTargetRepository(String targetRepository) {
            this.targetRepository = targetRepository;
        }
        
        public String getTargetFile() {
            return targetFile;
        }
        
        public void setTargetFile(String targetFile) {
            this.targetFile = targetFile;
        }
        
        public String getTargetMethod() {
            return targetMethod;
        }
        
        public void setTargetMethod(String targetMethod) {
            this.targetMethod = targetMethod;
        }
        
        public boolean isCrossRepository() {
            return crossRepository;
        }
        
        public void setCrossRepository(boolean crossRepository) {
            this.crossRepository = crossRepository;
        }
        
        public List<String> getDataParameters() {
            return dataParameters;
        }
        
        public void setDataParameters(List<String> dataParameters) {
            this.dataParameters = dataParameters;
        }
        
        /**
         * Gets the confidence score for this workflow step.
         * Values range from 0.0 (lowest confidence) to 1.0 (highest confidence).
         * 
         * @return confidence score between 0.0 and 1.0
         */
        public double getConfidence() {
            return confidence;
        }
        
        /**
         * Sets the confidence score for this workflow step.
         * 
         * @param confidence value between 0.0 and 1.0
         */
        public void setConfidence(double confidence) {
            this.confidence = confidence;
        }
        
        /**
         * Gets the design pattern type associated with this workflow step.
         * 
         * @return the pattern type (e.g., "factory", "builder", "dependency-injection")
         */
        public String getPatternType() {
            return patternType;
        }
        
        /**
         * Sets the design pattern type associated with this workflow step.
         * 
         * @param patternType the pattern type
         */
        public void setPatternType(String patternType) {
            this.patternType = patternType;
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            
            WorkflowStep that = (WorkflowStep) o;
            
            if (!sourceFile.equals(that.sourceFile)) return false;
            if (!sourceMethod.equals(that.sourceMethod)) return false;
            if (!targetMethod.equals(that.targetMethod)) return false;
            return targetFile != null ? targetFile.equals(that.targetFile) : that.targetFile == null;
        }
        
        @Override
        public int hashCode() {
            int result = sourceFile.hashCode();
            result = 31 * result + sourceMethod.hashCode();
            result = 31 * result + targetMethod.hashCode();
            return result;
        }
    }
} 