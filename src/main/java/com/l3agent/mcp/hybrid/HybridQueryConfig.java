package com.l3agent.mcp.hybrid;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

/**
 * Configuration for the Hybrid Query Execution Engine.
 * Defines the properties and default values for the engine.
 */
@Configuration
@PropertySource("classpath:hybrid-query.properties")
public class HybridQueryConfig {
    
    // Default properties are defined in hybrid-query.properties
    
    // Enable/disable dynamic tools
    // l3agent.hybrid.enable-dynamic-tools=true
    
    // Maximum execution time in seconds
    // l3agent.hybrid.max-execution-time-seconds=30
    
    // Whether to fallback to static analysis if dynamic analysis fails
    // l3agent.hybrid.fallback-to-static=true
    
    // Hybrid query property prefix
    public static final String PROPERTY_PREFIX = "l3agent.hybrid";
    
    // Tool name properties
    public static final String VECTOR_SEARCH_TOOL = "vector_search";
    public static final String CALL_PATH_ANALYZER_TOOL = "call_path_analyzer";
    public static final String CONFIG_IMPACT_ANALYZER_TOOL = "config_impact_analyzer";
    public static final String ERROR_CHAIN_MAPPER_TOOL = "error_chain_mapper";
    public static final String CROSS_REPO_TRACER_TOOL = "cross_repo_tracer";
    
    // Path types
    public static final String STATIC_PATH_TYPE = "STATIC";
    public static final String DYNAMIC_PATH_TYPE = "DYNAMIC";
    public static final String HYBRID_PATH_TYPE = "HYBRID";
    
    // Query categories
    public static final String CODE_SEARCH_CATEGORY = "CODE_SEARCH";
    public static final String CALL_PATH_CATEGORY = "CALL_PATH";
    public static final String CONFIG_IMPACT_CATEGORY = "CONFIG_IMPACT";
    public static final String ERROR_CHAIN_CATEGORY = "ERROR_CHAIN";
    public static final String CROSS_REPO_CATEGORY = "CROSS_REPO";
    public static final String GENERAL_CATEGORY = "GENERAL";
} 