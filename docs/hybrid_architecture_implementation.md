# Hybrid Architecture Implementation Specification

## Overview
This document provides detailed implementation specifications for the enhanced L3Agent architecture combining pre-computed knowledge with dynamic tool calling through a Model Control Plane (MCP).

## Core Components

### 1. Model Control Plane (MCP) Server

#### Architecture
- **Java-based server** built on Spring Boot 3.x
- **Stateless API Gateway** pattern for request coordination
- **Tool Registry** for dynamic capability registration
- **Request/Response Controller** for handling all client interactions
- **Resource Manager** for orchestrating compute resources

#### Implementation Details
- Create `com.l3agent.mcp` package structure
- Implement core interfaces:
  ```java
  public interface MCPToolInterface {
      String getName();
      String getDescription();
      List<ToolParameter> getParameters();
      ToolResponse execute(Map<String, Object> parameters);
  }
  
  public interface MCPRequestHandler {
      MCPResponse process(MCPRequest request);
      void registerTool(MCPToolInterface tool);
      List<MCPToolInterface> getAvailableTools();
  }
  ```
- Build registry system with runtime tool registration:
  ```java
  @Service
  public class MCPToolRegistry {
      private final Map<String, MCPToolInterface> tools = new ConcurrentHashMap<>();
      
      public void registerTool(MCPToolInterface tool) {
          tools.put(tool.getName(), tool);
      }
      
      public MCPToolInterface getTool(String name) {
          return tools.get(name);
      }
      
      public List<MCPToolInterface> getAllTools() {
          return new ArrayList<>(tools.values());
      }
  }
  ```

### 2. Dynamic Code Analysis Tools

#### Call Path Analyzer
- **Implementation**: Custom AST parser + bytecode analysis using ASM library
- **Storage**: In-memory directed graph with serialization capability
- **Integration**: REST API for invocation + background batch processing

```java
@Service
public class CallPathAnalyzerTool implements MCPToolInterface {
    private final CallGraphService callGraphService;
    
    @Override
    public ToolResponse execute(Map<String, Object> parameters) {
        String className = (String) parameters.get("className");
        String methodName = (String) parameters.get("methodName");
        int depth = parameters.containsKey("depth") ? 
            (Integer) parameters.get("depth") : 3;
            
        CallGraph graph = callGraphService.buildCallGraph(className, methodName, depth);
        return new ToolResponse(true, "Call graph generated", graph);
    }
}
```

#### Cross-Repository Tracer
- **Implementation**: Build on JGit API + custom indexing system
- **Storage**: Index repository information in Elasticsearch
- **Integration**: MCP tool + periodic index updates

```java
@Service
public class CrossRepoTracerTool implements MCPToolInterface {
    private final GitRepositoryService gitService;
    private final ElasticsearchTemplate elasticsearchTemplate;
    
    @Override
    public ToolResponse execute(Map<String, Object> parameters) {
        String className = (String) parameters.get("className");
        boolean includeHistorical = parameters.containsKey("includeHistorical") ? 
            (Boolean) parameters.get("includeHistorical") : false;
            
        List<RepoReference> references = gitService.findClassAcrossRepositories(
            className, includeHistorical);
        return new ToolResponse(true, "Found " + references.size() + " references", references);
    }
}
```

#### Error Chain Mapper
- **Implementation**: Custom exception hierarchy analysis + logging pattern recognition
- **Storage**: In-memory directed graph with serialization capability
- **Integration**: MCP tool + error telemetry collection

```java
@Service
public class ErrorChainMapperTool implements MCPToolInterface {
    private final ExceptionAnalysisService exceptionService;
    private final LogPatternAnalyzer logAnalyzer;
    
    @Override
    public ToolResponse execute(Map<String, Object> parameters) {
        String exceptionClass = (String) parameters.get("exceptionClass");
        String logPattern = parameters.containsKey("logPattern") ? 
            (String) parameters.get("logPattern") : null;
            
        ErrorChain chain = exceptionService.buildErrorChain(exceptionClass, logPattern);
        return new ToolResponse(true, "Error chain analyzed", chain);
    }
}
```

#### Configuration Impact Analyzer
- **Implementation**: Configuration property graph + usage analyzer
- **Storage**: Property graph database (TinkerPop/JanusGraph)
- **Integration**: MCP tool + configuration monitoring

```java
@Service
public class ConfigImpactAnalyzerTool implements MCPToolInterface {
    private final ConfigurationGraphService configGraph;
    
    @Override
    public ToolResponse execute(Map<String, Object> parameters) {
        String propertyName = (String) parameters.get("propertyName");
        boolean includeIndirect = parameters.containsKey("includeIndirect") ? 
            (Boolean) parameters.get("includeIndirect") : true;
            
        ConfigImpact impact = configGraph.analyzePropertyImpact(propertyName, includeIndirect);
        return new ToolResponse(true, "Configuration impact analyzed", impact);
    }
}
```

### 3. Hybrid Query Execution Engine

#### Architecture
- **Two-phase execution model**:
  1. Pre-computed knowledge retrieval (vector search, knowledge graph)
  2. Dynamic tool execution for targeted analysis
- **Query planner** to optimize execution strategy based on question type
- **Result synthesizer** to combine static and dynamic results

#### Implementation
```java
@Service
public class HybridQueryEngine {
    private final VectorBasedCodeRepositoryService vectorService;
    private final MCPRequestHandler mcpHandler;
    private final QueryClassifier classifier;
    
    public QueryResponse execute(String query) {
        // Phase 1: Use pre-computed knowledge
        QueryType type = classifier.classifyQuery(query);
        List<CodeSnippet> relevantCode = vectorService.findRelevantCode(query);
        
        // Phase 2: Determine if dynamic analysis needed
        if (type.requiresDynamicAnalysis()) {
            // Create dynamic analysis plan
            List<ToolExecutionStep> plan = createExecutionPlan(type, query, relevantCode);
            
            // Execute each tool in sequence
            List<ToolResponse> dynamicResults = executeTools(plan);
            
            // Synthesize final response
            return synthesizeResponse(query, relevantCode, dynamicResults);
        } else {
            // Return based on static knowledge only
            return synthesizeResponse(query, relevantCode, Collections.emptyList());
        }
    }
    
    private List<ToolExecutionStep> createExecutionPlan(
            QueryType type, String query, List<CodeSnippet> relevantCode) {
        // Logic to determine which tools to run and in what order
        // based on query type and initially retrieved code
    }
    
    private List<ToolResponse> executeTools(List<ToolExecutionStep> plan) {
        // Execute each tool step and collect results
    }
    
    private QueryResponse synthesizeResponse(
            String query, List<CodeSnippet> staticResults, List<ToolResponse> dynamicResults) {
        // Combine static and dynamic results into coherent response
    }
}
```

## Data Models

### Tool Execution Models
```java
public class ToolParameter {
    private String name;
    private String description;
    private String type;
    private boolean required;
    private Object defaultValue;
}

public class ToolResponse {
    private boolean success;
    private String message;
    private Object data;
    private List<String> warnings;
    private List<String> errors;
}

public class MCPRequest {
    private String query;
    private List<ToolExecutionStep> executionPlan;
    private Map<String, Object> contextData;
}

public class MCPResponse {
    private String responseId;
    private String answer;
    private List<ToolResponse> toolResults;
    private Map<String, Object> metadata;
}

public class ToolExecutionStep {
    private String toolName;
    private Map<String, Object> parameters;
    private int priority;
    private boolean required;
}
```

### Analysis Result Models
```java
public class CallGraph {
    private String rootClass;
    private String rootMethod;
    private List<CallNode> nodes;
    private List<CallEdge> edges;
}

public class CallNode {
    private String id;
    private String className;
    private String methodName;
    private String signature;
    private boolean isExternal;
    private String sourceFile;
}

public class CallEdge {
    private String id;
    private String sourceNodeId;
    private String targetNodeId;
    private int lineNumber;
    private String callType; // STATIC, VIRTUAL, INTERFACE, etc.
}

public class RepoReference {
    private String repositoryName;
    private String repositoryUrl;
    private String branchName;
    private String filePath;
    private int lineStart;
    private int lineEnd;
    private String snippet;
    private LocalDateTime lastModified;
    private String lastAuthor;
}

public class ErrorChain {
    private String rootExceptionClass;
    private List<ErrorNode> nodes;
    private List<ErrorEdge> edges;
}

public class ErrorNode {
    private String id;
    private String exceptionClass;
    private List<String> possibleMessages;
    private List<String> associatedLogPatterns;
    private String exceptionType; // CHECKED, RUNTIME, ERROR
}

public class ErrorEdge {
    private String id;
    private String sourceNodeId;
    private String targetNodeId;
    private String relationship; // CAUSES, WRAPS, SUPPRESSES
}

public class ConfigImpact {
    private String propertyName;
    private String propertyType;
    private List<ConfigUsage> directUsages;
    private List<ConfigUsage> indirectUsages;
    private Map<String, Object> possibleValues;
    private Object defaultValue;
}

public class ConfigUsage {
    private String className;
    private String methodName;
    private int lineNumber;
    private String usageType; // READ, CONDITIONAL, DEFAULT_VALUE
    private String impact; // HIGH, MEDIUM, LOW
}
```

## Non-Functional Requirements

### Performance
- Tool execution timeout: 10 seconds max per tool
- Query response time: <5 seconds for 90% of requests
- Memory usage: <2GB per JVM instance
- Concurrent requests: Support up to 50 simultaneous requests

### Scalability
- Horizontal scaling for MCP server instances
- Rate limiting based on client identity
- Backpressure mechanisms for overload protection

### Reliability
- Circuit breakers for all external dependencies
- Graceful degradation when tools are unavailable
- Comprehensive error handling with fallback responses

### Security
- Authentication for all API endpoints
- Tool execution sandboxing
- Input validation and sanitization
- Audit logging for all tool executions

### Monitoring
- Prometheus metrics for all components
- Tool execution success/failure rates
- Response time histograms
- Resource utilization tracking

## Implementation Phases

### Phase 1: Core Infrastructure
- MCP server implementation
- Tool registry and execution framework
- Basic integration with existing vector search

### Phase 2: Tool Implementation
- Call Path Analyzer
- Configuration Impact Analyzer
- Error Chain Mapper
- Cross-Repository Tracer

### Phase 3: Query Engine Enhancement
- Query classification system
- Execution planning optimization
- Result synthesis improvements
- Client library development 