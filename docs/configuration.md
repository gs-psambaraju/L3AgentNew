# L3Agent Configuration Guide

## Configuration Approach

L3Agent uses a multi-layered configuration approach:

1. **Default Values**: Sensible defaults are provided in code for all configuration properties
2. **Reference Properties**: A complete reference (`application-reference.properties`) is available with all options
3. **Documentation**: Detailed documentation for specific subsystems (this file and others)

## Basic Configuration

The minimal required configuration is included in `application.properties`:

```properties
# LLM provider settings (required)
l3agent.llm.provider=openai  
l3agent.llm.api-key=${OPENAI_API_KEY:your_api_key_here}
l3agent.llm.model=gpt-4

# Vector store settings (required)
l3agent.vector-store.engine=hnswlib

# Database settings
l3agent.database.enabled=true
```

## Advanced Configuration

For advanced configuration options:

1. **Copy from Reference**: Copy any properties you want to customize from `application-reference.properties` to your `application.properties`

2. **Environment-specific Profiles**: Use Spring profiles for environment-specific settings:
   ```properties
   # application-dev.properties
   logging.level.com.l3agent=DEBUG
   ```

3. **Environment Variables**: Use environment variables for sensitive values:
   ```
   export OPENAI_API_KEY=your_api_key_here
   ```

4. **JVM Properties**: Override settings using JVM properties:
   ```
   -Dl3agent.confidence.vector-search-weight=0.35
   ```

## Configuration Priority

Spring Boot uses the following order of precedence:

1. Command line arguments
2. JVM system properties
3. OS environment variables
4. Profile-specific properties
5. Application properties
6. Default values in @ConfigurationProperties classes

## IDE Support

Configuration property auto-completion is available in most IDEs through the Spring Configuration Metadata. This provides code completion and documentation when editing application.properties files.

## Subsystem Documentation

- [Confidence Rating System](confidence-rating.md)
- [Model Control Plane Integration](mcp-integration.md)
- [Database Configuration](database-configuration.md) 