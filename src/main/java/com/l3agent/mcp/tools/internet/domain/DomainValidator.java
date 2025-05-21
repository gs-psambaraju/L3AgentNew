package com.l3agent.mcp.tools.internet.domain;

import com.l3agent.mcp.tools.internet.config.InternetDataConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Validates that domains are allowed for access.
 */
@Component
public class DomainValidator {
    
    private static final Logger logger = LoggerFactory.getLogger(DomainValidator.class);
    
    private final InternetDataConfig config;
    
    /**
     * Creates a new DomainValidator with the specified configuration.
     * 
     * @param config The configuration
     */
    @Autowired
    public DomainValidator(InternetDataConfig config) {
        this.config = config;
        logger.info("Initialized domain validator with {} trusted domains", 
                config.getTrustedDomains().size());
    }
    
    /**
     * Checks if a URL is allowed based on its domain.
     * 
     * @param url The URL to check
     * @return true if allowed, false otherwise
     */
    public boolean isAllowed(String url) {
        if (url == null || url.trim().isEmpty()) {
            return false;
        }
        
        // Get the domain from the URL
        String domain = extractDomain(url);
        if (domain.isEmpty()) {
            return false;
        }
        
        // Check if the domain is in the trusted domains list
        Set<String> trustedDomains = config.getTrustedDomains();
        boolean allowed = trustedDomains.contains(domain);
        
        if (!allowed) {
            // Check if the domain is a subdomain of a trusted domain
            for (String trustedDomain : trustedDomains) {
                if (domain.endsWith("." + trustedDomain)) {
                    allowed = true;
                    break;
                }
            }
        }
        
        if (allowed) {
            logger.debug("Domain '{}' is allowed", domain);
        } else {
            logger.warn("Domain '{}' is not in trusted domains list", domain);
        }
        
        return allowed;
    }
    
    /**
     * Extracts the domain from a URL.
     * 
     * @param url The URL to extract the domain from
     * @return The domain
     */
    private String extractDomain(String url) {
        if (url == null || url.isEmpty()) {
            return "";
        }
        
        // Remove protocol if present
        String domain = url.toLowerCase();
        if (domain.startsWith("http://")) {
            domain = domain.substring(7);
        } else if (domain.startsWith("https://")) {
            domain = domain.substring(8);
        }
        
        // Remove path and query parameters
        int pathStart = domain.indexOf('/');
        if (pathStart > 0) {
            domain = domain.substring(0, pathStart);
        }
        
        return domain;
    }
} 