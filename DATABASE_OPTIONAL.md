# Database-Optional Mode

## Overview

The L3Agent now supports a "database-optional" mode that allows operations like embedding generation to run without initializing the database. This feature is particularly useful for:

1. Generating embeddings for large codebases without database locking issues
2. Running in resource-constrained environments
3. Running in environments where database access isn't necessary

## Configuration

To enable or disable the database, use the following configuration property:

```properties
# In application.properties
l3agent.database.enabled=true|false
```

Or as a command-line parameter:

```bash
java -jar l3agent.jar --l3agent.database.enabled=false
```

## Implementation Details

When database is disabled:

1. Database auto-configurations are filtered out by the `ConditionalDatabaseAutoConfiguration` class
2. Repository beans use no-op implementations like `NullLLMMetadataRepository` 
3. Service layer methods have added checks to gracefully handle database-disabled scenarios
4. Proper exception handling ensures core functionality works even if database operations fail

## Running with Database Disabled

### For Development

Use the provided scripts:

- `./run-with-db-disabled.sh` - Runs the application with database disabled
- `./test-db-disabled.sh` - Runs the embedding generation test with database disabled

### For Production

When deploying for embedding generation only:

```bash
java -jar l3agent.jar --l3agent.database.enabled=false --spring.profiles.active=embedding
```

## Design Considerations

1. **Graceful Degradation**: When database is disabled, the system continues to function with reduced capabilities
2. **Clean Architecture**: Database access is isolated in repository layer and conditionally loaded
3. **Error Resilience**: Service layer includes error handling to prevent cascading failures
4. **Resource Optimization**: Database connections are properly managed and cleaned up through shutdown hooks

## Troubleshooting

If you encounter issues:

1. Check logs for any errors related to the database configuration
2. Verify the `l3agent.database.enabled` property is properly set
3. If using Spring profiles, ensure they're correctly configured
4. For embedding issues, look for API-related errors in the logs 