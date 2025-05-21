package com.l3agent.mcp.tools.internet.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Configuration for the Internet Data Tool components.
 * Enables scheduling for cache and rate limiting cleanup tasks.
 */
@Configuration
@EnableScheduling
public class InternetToolConfig {
} 