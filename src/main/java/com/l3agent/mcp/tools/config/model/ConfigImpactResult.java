package com.l3agent.mcp.tools.config.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents the result of a configuration property impact analysis.
 */
public class ConfigImpactResult {
    
    private String propertyName;
    private List<PropertyReference> references;
    private Map<String, List<PropertySource>> propertiesBySource;
    private String defaultValue;
    private ImpactSeverity impactSeverity;
    private List<String> analysisNotes;
    private boolean databaseConfigDetected;
    private List<String> databaseConfigSources;
    private List<String> userPrompts;
    private Map<String, String> summaryItems;
    private List<Map<String, String>> recommendations;
    
    /**
     * Creates a new configuration impact result.
     */
    public ConfigImpactResult() {
        this.references = new ArrayList<>();
        this.propertiesBySource = new HashMap<>();
        this.analysisNotes = new ArrayList<>();
        this.databaseConfigSources = new ArrayList<>();
        this.userPrompts = new ArrayList<>();
        this.summaryItems = new HashMap<>();
        this.recommendations = new ArrayList<>();
        this.impactSeverity = ImpactSeverity.UNKNOWN;
        this.databaseConfigDetected = false;
    }
    
    /**
     * Creates a new configuration impact result.
     * 
     * @param propertyName The name of the analyzed property
     */
    public ConfigImpactResult(String propertyName) {
        this();
        this.propertyName = propertyName;
    }
    
    /**
     * Adds a property reference.
     * 
     * @param reference The property reference to add
     */
    public void addReference(PropertyReference reference) {
        references.add(reference);
        
        // Update severity based on the references
        updateSeverity();
    }
    
    /**
     * Sets the list of property references.
     * 
     * @param references The list of references
     */
    public void setReferences(List<PropertyReference> references) {
        this.references = references;
        
        // Update severity based on the references
        updateSeverity();
    }
    
    /**
     * Gets all property references.
     * 
     * @return The list of property references
     */
    public List<PropertyReference> getReferences() {
        return references;
    }
    
    /**
     * Sets the map of properties by source.
     * 
     * @param propertiesBySource Map of property sources to properties
     */
    public void setPropertiesBySource(Map<String, List<PropertySource>> propertiesBySource) {
        this.propertiesBySource = propertiesBySource;
    }
    
    /**
     * Gets the map of properties by source.
     * 
     * @return Map of property sources to properties
     */
    public Map<String, List<PropertySource>> getPropertiesBySource() {
        return propertiesBySource;
    }
    
    /**
     * Adds a summary item to the analysis result.
     * 
     * @param key The summary item key
     * @param value The summary item value
     */
    public void addSummaryItem(String key, String value) {
        summaryItems.put(key, value);
    }
    
    /**
     * Gets the summary items.
     * 
     * @return Map of summary items
     */
    public Map<String, String> getSummaryItems() {
        return summaryItems;
    }
    
    /**
     * Adds a recommendation to the analysis result.
     * 
     * @param title The recommendation title
     * @param description The recommendation description
     */
    public void addRecommendation(String title, String description) {
        Map<String, String> recommendation = new HashMap<>();
        recommendation.put("title", title);
        recommendation.put("description", description);
        recommendations.add(recommendation);
    }
    
    /**
     * Gets the recommendations.
     * 
     * @return List of recommendations
     */
    public List<Map<String, String>> getRecommendations() {
        return recommendations;
    }
    
    /**
     * Updates the impact severity based on the references.
     */
    private void updateSeverity() {
        // Count critical components that reference this property
        long criticalCount = references.stream()
                .filter(PropertyReference::isCriticalComponent)
                .count();
        
        // Count security-related components
        long securityCount = references.stream()
                .filter(r -> r.getComponentType() != null && 
                           (r.getComponentType().contains("Security") || 
                            r.getComponentType().contains("Auth")))
                .count();
        
        // Look for conditional usage, which increases importance
        long conditionalUseCount = references.stream()
                .filter(r -> r.getContext() != null && r.getContext().getConditionalUses() > 0)
                .count();
        
        // Determine severity based on references
        if (criticalCount > 0 || securityCount > 0 || conditionalUseCount > 1) {
            impactSeverity = ImpactSeverity.HIGH;
        } else if (references.size() > 5 || conditionalUseCount > 0) {
            impactSeverity = ImpactSeverity.MEDIUM;
        } else if (references.size() > 0) {
            impactSeverity = ImpactSeverity.LOW;
        } else {
            impactSeverity = ImpactSeverity.UNKNOWN;
        }
    }
    
    /**
     * Adds a database configuration source.
     * 
     * @param source Description of the database configuration source
     * @param prompt User prompt related to this database configuration
     */
    public void addDatabaseConfigSource(String source, String prompt) {
        this.databaseConfigDetected = true;
        this.databaseConfigSources.add(source);
        this.userPrompts.add(prompt);
    }
    
    /**
     * Adds an analysis note.
     * 
     * @param note The analysis note to add
     */
    public void addAnalysisNote(String note) {
        analysisNotes.add(note);
    }

    /**
     * Gets the property name that was analyzed.
     * 
     * @return The property name
     */
    public String getPropertyName() {
        return propertyName;
    }
    
    /**
     * Sets the property name that was analyzed.
     * 
     * @param propertyName The property name
     */
    public void setPropertyName(String propertyName) {
        this.propertyName = propertyName;
    }

    /**
     * Gets the direct references to the property.
     * 
     * @return List of direct property references
     */
    public List<PropertyReference> getDirectReferences() {
        return references.stream()
                .filter(r -> !"Indirect Reference".equals(r.getReferenceType()))
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Gets the indirect references to the property.
     * 
     * @return List of indirect property references
     */
    public List<PropertyReference> getIndirectReferences() {
        return references.stream()
                .filter(r -> "Indirect Reference".equals(r.getReferenceType()))
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Gets the default value of the property.
     * 
     * @return The default value or null if not found
     */
    public String getDefaultValue() {
        return defaultValue;
    }

    /**
     * Sets the default value of the property.
     * 
     * @param defaultValue The default value to set
     */
    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    /**
     * Gets the impact severity level.
     * 
     * @return The impact severity
     */
    public ImpactSeverity getImpactSeverity() {
        return impactSeverity;
    }

    /**
     * Gets the analysis notes.
     * 
     * @return List of analysis notes
     */
    public List<String> getAnalysisNotes() {
        return analysisNotes;
    }

    /**
     * Checks if database configuration was detected.
     * 
     * @return true if database configuration was detected
     */
    public boolean isDatabaseConfigDetected() {
        return databaseConfigDetected;
    }

    /**
     * Gets the database configuration sources.
     * 
     * @return List of database configuration source descriptions
     */
    public List<String> getDatabaseConfigSources() {
        return databaseConfigSources;
    }

    /**
     * Gets the user prompts related to database configuration.
     * 
     * @return List of user prompts
     */
    public List<String> getUserPrompts() {
        return userPrompts;
    }
} 