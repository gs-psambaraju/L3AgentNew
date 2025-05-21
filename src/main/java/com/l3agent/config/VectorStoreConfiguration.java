package com.l3agent.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Configuration for vector store services.
 * Ensures embedding endpoints and other properties are properly shared.
 */
@Configuration
public class VectorStoreConfiguration {

    /**
     * Provides the embedding URL from application properties to any bean that needs it.
     * This helps ensure consistent property values across different beans and runners.
     */
    @Bean
    @Primary
    public String embeddingUrl(@Value("${l3agent.llm.gainsight.embedding-url}") String embeddingUrl) {
        return embeddingUrl;
    }
} 