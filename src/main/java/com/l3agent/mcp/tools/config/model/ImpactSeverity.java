package com.l3agent.mcp.tools.config.model;

/**
 * Represents the severity of a configuration property's impact.
 */
public enum ImpactSeverity {
    /**
     * High severity impact - affects core functionality, security, or critical components.
     */
    HIGH,
    
    /**
     * Medium severity impact - affects important but non-critical functionality.
     */
    MEDIUM,
    
    /**
     * Low severity impact - affects minor functionality or has limited scope.
     */
    LOW,
    
    /**
     * Unknown severity - not enough information to determine impact.
     */
    UNKNOWN
} 