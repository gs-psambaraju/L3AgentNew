package com.l3agent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l3agent.service.LLMService;
import com.l3agent.service.impl.GainsightLLMService;

/**
 * Configuration class for LLM services.
 * Configures Gainsight as the LLM service.
 */
@Configuration
public class LLMConfiguration {
    
    private static final Logger logger = LoggerFactory.getLogger(LLMConfiguration.class);
    
    @Autowired
    private GainsightLLMService gainsightLLMService;
    
    /**
     * Creates and configures the primary LLM service as Gainsight.
     * 
     * @return The configured LLM service
     */
    @Bean
    @Primary
    public LLMService llmService() {
        logger.info("Using Gainsight LLM service");
        return gainsightLLMService;
    }
} 