package com.l3agent.mcp.tools;

import com.l3agent.mcp.MCPToolInterface;
import com.l3agent.mcp.model.ToolParameter;
import com.l3agent.mcp.model.ToolResponse;
import com.l3agent.mcp.tools.config.PropertyAnalyzer;
import com.l3agent.mcp.tools.config.ast.ASTPropertyAnalyzer;
import com.l3agent.mcp.tools.config.context.PropertyContextAnalyzer;
import com.l3agent.mcp.tools.config.db.DatabaseConfigDetector;
import com.l3agent.mcp.tools.config.indirect.IndirectReferenceDetector;
import com.l3agent.mcp.tools.config.model.ConfigImpactResult;
import com.l3agent.mcp.tools.config.model.ImpactSeverity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP tool for analyzing the impact of configuration properties.
 * Identifies classes and methods affected by specific configuration properties.
 */
@Component
public class ConfigImpactAnalyzerTool implements MCPToolInterface {
    
    private static final Logger logger = LoggerFactory.getLogger(ConfigImpactAnalyzerTool.class);
    
    private static final String TOOL_NAME = "config_impact_analyzer";
    
    private final PropertyAnalyzer propertyAnalyzer;
    
    @Autowired
    private ASTPropertyAnalyzer astPropertyAnalyzer;
    
    @Autowired
    private DatabaseConfigDetector databaseConfigDetector;
    
    @Autowired
    private IndirectReferenceDetector indirectReferenceDetector;
    
    @Autowired
    private PropertyContextAnalyzer contextAnalyzer;
    
    @Value("${l3agent.config.timeout-seconds:3}")
    private int timeoutSeconds;
    
    @Value("${l3agent.config.enable-ast-analysis:true}")
    private boolean enableAstAnalysis;
    
    @Value("${l3agent.config.enable-db-detection:true}")
    private boolean enableDbDetection;
    
    @Value("${l3agent.config.enable-indirect-detection:true}")
    private boolean enableIndirectDetection;
    
    @Value("${l3agent.config.enable-context-analysis:true}")
    private boolean enableContextAnalysis;
    
    public ConfigImpactAnalyzerTool(PropertyAnalyzer propertyAnalyzer) {
        this.propertyAnalyzer = propertyAnalyzer;
    }
    
    @Override
    public String getName() {
        return TOOL_NAME;
    }
    
    @Override
    public String getDescription() {
        return "Analyzes how configuration properties affect system behavior";
    }
    
    @Override
    public List<ToolParameter> getParameters() {
        List<ToolParameter> params = new ArrayList<>();
        
        // Required parameter: property name to analyze
        ToolParameter propertyNameParam = new ToolParameter(
            "propertyName",
            "Name of the configuration property to analyze (e.g., spring.datasource.url)",
            "string",
            true,
            null
        );
        params.add(propertyNameParam);
        
        // Optional parameter: whether to analyze properties with similar names
        ToolParameter includeSimilarParam = new ToolParameter(
            "includeSimilar",
            "Whether to include parent properties and properties with the same prefix in the analysis (e.g., for 'spring.datasource.url' also analyze 'spring.datasource') (default: true)",
            "boolean",
            false,
            true
        );
        params.add(includeSimilarParam);
        
        // Optional parameter: whether to include indirect impacts
        ToolParameter includeIndirectParam = new ToolParameter(
            "includeIndirect",
            "Whether to include classes that depend on classes directly using the property, showing propagation of configuration changes through component dependencies (default: true)",
            "boolean",
            false,
            true
        );
        params.add(includeIndirectParam);
        
        // Optional parameter: whether to check for database-stored configuration
        ToolParameter includeDbConfigParam = new ToolParameter(
            "includeDbConfig",
            "Whether to check for database-stored configuration that might override the property and generate interactive prompts for the user to investigate database values (default: true)",
            "boolean",
            false,
            true
        );
        params.add(includeDbConfigParam);
        
        return params;
    }
    
    @Override
    public ToolResponse execute(Map<String, Object> parameters) {
        // Validate required parameters
        String propertyName = (String) parameters.get("propertyName");
        if (propertyName == null || propertyName.trim().isEmpty()) {
            logger.warn("Missing required parameter: propertyName");
            return new ToolResponse(false, "Missing required parameter: propertyName", null);
        }
        
        try {
            // Get optional parameters with defaults
            boolean includeSimilar = getBooleanParameter(parameters, "includeSimilar", true);
            boolean includeIndirect = getBooleanParameter(parameters, "includeIndirect", true);
            boolean includeDbConfig = getBooleanParameter(parameters, "includeDbConfig", true);
            
            // Execute the analysis
            logger.info("Analyzing configuration impact for property: {}", propertyName);
            long startTime = System.currentTimeMillis();
            
            // Create a list of properties - we'll expand this for similar property detection if needed
            List<String> propertyNames = new ArrayList<>();
            propertyNames.add(propertyName);
            
            if (includeSimilar) {
                // Add similar properties based on naming patterns
                // For example, if spring.datasource.url is provided, also include other spring.datasource.* properties
                String propPrefix = getPropertyPrefix(propertyName);
                if (propPrefix != null) {
                    propertyNames.add(propPrefix + "*"); // Add wildcard for prefix match
                }
            }
            
            // Configure feature flags based on input parameters
            this.enableIndirectDetection = includeIndirect;
            this.enableDbDetection = includeDbConfig;
            
            ConfigImpactResult result = analyzeConfigImpact(propertyNames);
            result.setPropertyName(propertyName);
            
            long duration = System.currentTimeMillis() - startTime;
            logger.info("Configuration impact analysis completed in {} ms", duration);
            
            // Build the response
            Map<String, Object> resultData = new HashMap<>();
            
            // Add the impact analysis results
            resultData.put("propertyName", propertyName);
            resultData.put("directReferences", result.getDirectReferences());
            resultData.put("indirectReferences", result.getIndirectReferences());
            resultData.put("defaultValue", result.getDefaultValue());
            resultData.put("impactSeverity", result.getImpactSeverity().toString());
            resultData.put("analysisNotes", result.getAnalysisNotes());
            resultData.put("databaseConfigDetected", result.isDatabaseConfigDetected());
            
            // If database configuration is detected, include prompts for the user
            if (result.isDatabaseConfigDetected()) {
                resultData.put("databaseConfigSources", result.getDatabaseConfigSources());
                resultData.put("userPrompts", result.getUserPrompts());
            }
            
            // Add enhanced analysis information
            resultData.put("summaryItems", result.getSummaryItems());
            resultData.put("recommendations", result.getRecommendations());
            resultData.put("analysisDurationMs", duration);
            
            return new ToolResponse(true, "Configuration impact analysis completed successfully", resultData);
        } catch (Exception e) {
            logger.error("Error analyzing configuration impact", e);
            ToolResponse response = new ToolResponse(false, "Error analyzing configuration impact: " + e.getMessage(), null);
            response.addError(e.toString());
            return response;
        }
    }
    
    /**
     * Gets a property prefix for similar property detection.
     */
    private String getPropertyPrefix(String propertyName) {
        int lastDotIndex = propertyName.lastIndexOf('.');
        if (lastDotIndex > 0) {
            return propertyName.substring(0, lastDotIndex);
        }
        return null;
    }
    
    /**
     * Analyzes the impact of configuration properties.
     * 
     * @param propertyNames List of property names to analyze
     * @return Impact analysis result
     */
    private ConfigImpactResult analyzeConfigImpact(List<String> propertyNames) {
        ConfigImpactResult result = propertyAnalyzer.analyzeImpact(propertyNames);
        
        try {
            // Apply database configuration detection if enabled
            if (enableDbDetection) {
                logger.info("Enhancing property references with database configuration detection");
                result.setReferences(databaseConfigDetector.detectDatabaseConfig(result.getReferences()));
            }
            
            // Apply indirect reference detection if enabled
            if (enableIndirectDetection) {
                logger.info("Detecting indirect property references");
                result.setReferences(indirectReferenceDetector.detectIndirectReferences(result.getReferences()));
            }
            
            // Apply context analysis if enabled
            if (enableContextAnalysis) {
                logger.info("Analyzing property usage context");
                result.setReferences(contextAnalyzer.analyzePropertyContext(result.getReferences()));
            }
            
            // Add comprehensive analysis overview
            result.addSummaryItem("Direct References", 
                String.valueOf(result.getReferences().stream()
                    .filter(ref -> !"Indirect Reference".equals(ref.getReferenceType()))
                    .count()));
                    
            result.addSummaryItem("Indirect References", 
                String.valueOf(result.getReferences().stream()
                    .filter(ref -> "Indirect Reference".equals(ref.getReferenceType()))
                    .count()));
                    
            result.addSummaryItem("Database Properties", 
                String.valueOf(result.getReferences().stream()
                    .filter(ref -> ref instanceof com.l3agent.mcp.tools.config.model.DatabaseConfigReference)
                    .count()));
                    
            // Count properties used in conditional logic
            long conditionalUses = result.getReferences().stream()
                .filter(ref -> ref.getContext() != null && ref.getContext().getConditionalUses() > 0)
                .count();
                
            result.addSummaryItem("Used in Conditional Logic", String.valueOf(conditionalUses));
            
            // Count properties modified after injection
            long modifiedProps = result.getReferences().stream()
                .filter(ref -> ref.getContext() != null && ref.getContext().getMutatingOperations() > 0)
                .count();
                
            result.addSummaryItem("Modified After Injection", String.valueOf(modifiedProps));
            
            // Add detailed analysis recommendations
            result.addRecommendation("Configuration Best Practices", 
                "Consider centralizing configuration in a dedicated ConfigurationProperties class for better management.");
                
            if (conditionalUses > 0) {
                result.addRecommendation("Conditional Configuration", 
                    "Properties used in conditional logic should have defaults and validation to avoid runtime errors.");
            }
            
            boolean hasDatabaseProps = result.getReferences().stream()
                .anyMatch(ref -> ref instanceof com.l3agent.mcp.tools.config.model.DatabaseConfigReference);
                
            if (hasDatabaseProps) {
                result.addRecommendation("Database Configuration", 
                    "Consider using environment variables or a secure vault for sensitive database credentials.");
            }
        } catch (Exception e) {
            logger.warn("Error during enhanced property analysis: {}", e.getMessage());
            // Continue with the basic result if advanced analysis fails
        }
        
        return result;
    }
    
    /**
     * Gets a boolean parameter with a default value.
     */
    private boolean getBooleanParameter(Map<String, Object> parameters, String name, boolean defaultValue) {
        Object value = parameters.get(name);
        if (value instanceof Boolean) {
            return (Boolean) value;
        } else if (value instanceof String) {
            return Boolean.parseBoolean((String) value);
        }
        return defaultValue;
    }
} 