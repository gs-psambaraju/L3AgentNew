# Internet Data Retrieval Tool

## Overview

The Internet Data Retrieval Tool allows L3Agent to access trusted external websites to retrieve up-to-date documentation, reference materials, and API documentation. This tool enables agents to access real-time information while maintaining security controls.

## Features

- **Domain Whitelisting**: Only approved domains can be accessed
- **Rate Limiting**: Prevents excessive requests to external services
- **Content Caching**: Reduces load on external sites and improves performance
- **Content Filtering**: Extract only relevant sections from retrieved content
- **Retry Logic**: Handles transient network failures gracefully

## Configuration

The tool is configured via `internet-data.properties` with the following settings:

```properties
# Comma-separated list of trusted domains
l3agent.internet.trusted-domains=docs.oracle.com,docs.spring.io,github.com,stackoverflow.com

# Cache settings
l3agent.internet.max-cache-size=1000
l3agent.internet.cache-ttl-hours=24

# HTTP settings
l3agent.internet.timeout-seconds=10
l3agent.internet.max-retries=3
```

## Usage Examples

### Basic Usage

```java
// Get tool from MCPToolRegistry
MCPToolInterface internetTool = mcpToolRegistry.getTool("internet_data").orElseThrow();

// Set parameters
Map<String, Object> params = new HashMap<>();
params.put("url", "https://docs.oracle.com/javase/17/docs/api/java.base/java/util/concurrent/package-summary.html");

// Execute
ToolResponse response = internetTool.execute(params);

if (response.isSuccess()) {
    Map<String, Object> data = (Map<String, Object>) response.getData();
    String content = (String) data.get("content");
    System.out.println("Retrieved content: " + content.substring(0, 100) + "...");
}
```

### Filtering Content

```java
Map<String, Object> params = new HashMap<>();
params.put("url", "https://docs.oracle.com/javase/tutorial/essential/concurrency/");
params.put("filter", "thread safety, synchronization, volatile");

ToolResponse response = internetTool.execute(params);
// Will contain only sections related to the specified keywords
```

### Using Search Queries

```java
Map<String, Object> params = new HashMap<>();
params.put("url", "https://docs.spring.io/spring-boot/docs/current/reference/html/");
params.put("query", "how to configure application properties");

ToolResponse response = internetTool.execute(params);
// Will contain sections relevant to the query
```

## Security Considerations

- Always review the trusted domains list before deployment
- Consider adding additional content validation if needed
- Monitor usage patterns to detect potential misuse

## Integration with Agents

The Internet Data Tool integrates seamlessly with the MCP infrastructure. Agents can access this tool by name when requesting external information.

Example LLM prompt for using the tool:

```
To retrieve content from a trusted website, please use the internet_data tool with the following parameters:
- url: The website URL to access (must be from trusted domains)
- query (optional): A search query to filter relevant content
- filter (optional): Specific keywords or sections to extract
- force_refresh (optional): Set to true to bypass cache
``` 