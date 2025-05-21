# L3Agent Data Flow

## End-to-End Flow Diagram for Enhanced Architecture

```
┌──────────────┐     ┌──────────────┐     ┌─────────────┐
│  Support     │     │              │     │             │
│  Engineer    │────▶│  REST API    │────▶│  L3Agent    │
└──────────────┘     └──────────────┘     └─────────────┘
                                                │
                                                ▼
                                          ┌─────────────┐
                                          │  Query      │
                                          │  Classifier │
                                          └─────────────┘
                                                │
                                                ▼
┌──────────────┐     ┌──────────────┐     ┌─────────────┐
│  Support     │     │              │     │  Knowledge  │
│  Engineer    │◀────│  REST API    │◀────│  Retrieval  │
└──────────────┘     └──────────────┘     └─────────────┘
                                                ▲
                                                │
   ┌───────────────────────────────────────────┐│
   │                                            ││
┌──▼───────────┐     ┌──────────────┐     ┌────▼─────────┐
│  Code        │     │              │     │              │
│  Repository  │     │  Logs        │     │  Knowledge   │
└──────────────┘     └──────────────┘     └──────────────┘
        │                                        │
        ▼                                        ▼
┌──────────────┐     ┌──────────────┐     ┌──────────────┐     ┌──────────────┐
│  Knowledge   │◀───▶│   Vector     │◀───▶│ Bidirectional│────▶│     LLM      │
│  Graph       │     │   Store      │     │ Retrieval    │     │   Service    │
└──────────────┘     └──────────────┘     └──────────────┘     └──────────────┘
                                                                      │
                                                                      ▼
                                                               ┌──────────────┐
                                                               │   Metadata   │
                                                               │    Store     │
                                                               └──────────────┘
```

## Detailed Data Flow

### 1. Ticket Submission Flow
1. Support Engineer submits ticket details via REST API (JSON payload)
2. API validates and processes the request
3. Ticket is passed to the L3Agent for processing
4. For the POC, tickets will be stored locally in JSON files or a lightweight database

### 2. Knowledge Preparation Flow (One-time or Incremental)
1. Code repositories are processed to generate:
   - Vector embeddings for semantic search (HIGH PRIORITY)
     - **ON-DEMAND APPROACH**: Embeddings are only generated when explicitly requested via API
     - Process source files through specialized code embedding models
     - Apply intelligent chunking strategies based on code structure
     - Store embedding vectors with metadata linking to source
     - Build efficient vector indices for rapid similarity search
     - Implement periodic reindexing to maintain accuracy
   - Knowledge graph for structural relationships
     - **ON-DEMAND APPROACH**: Knowledge graph is only built when explicitly requested via API
     - Extract classes, methods, interfaces, and their relationships
     - Transform static code analysis into a queryable graph structure
     - Capture inheritance hierarchies, dependency chains, and call paths
     - Store graph with optimized traversal capabilities
2. Embeddings are stored in a vector database
   - Use HNSW for performance and scalability
   - Implement namespace isolation for multi-repository support
   - Manage embedding metadata in companion storage
3. Knowledge graph is persisted to disk
   - Serialize graph structure for fast loading
   - Track entity and relationship counts
   - Support incremental updates
   - Provide efficient querying capabilities

### 3. Query Processing Flow
1. L3Agent analyzes ticket content to classify the issue type
2. Based on classification, appropriate knowledge retrieval strategies are selected
3. Multi-phase retrieval is initiated:
   - Semantic search using vector embeddings (primary approach)
     - Convert query to embedding vector using the same model
     - Perform similarity search in vector database
     - Retrieve top-K results based on similarity scores
     - Apply post-filtering based on metadata and relevance
   - Structural relationships traced using knowledge graph (secondary/complementary)
   - Results from both approaches are merged and ranked
4. Knowledge sources are queried in parallel:
   - Code Repository for relevant code snippets
   - Logs for error patterns or system behavior
   - Knowledge Base for documentation and known solutions
   - Historical responses for similar issues (if available)

### 4. Structural Analysis Flow
1. Initial semantic matches are used as entry points in the knowledge graph
2. Related components are discovered through graph traversal:
   - Interfaces and their implementations
   - Parent and child classes
   - Methods that call or are called by matched methods
   - Dependencies between components
3. Cross-repository relationships are identified
4. Complete execution flows are reconstructed
5. Context is assembled from multiple repositories

### 5. LLM Interaction Flow
1. Context from all knowledge sources is assembled into a structured prompt
2. LLM API request is prepared with:
   - Model selection based on query complexity
   - Appropriate parameters (temperature, tokens, etc.)
   - Context window optimization
3. Request metadata is recorded:
   - Timestamp
   - Model version
   - Parameters
   - Token counts
   - Context sources
4. LLM API is invoked with the assembled context
5. Response is received and validated
6. Complete response with metadata is stored
7. Response quality metrics are calculated

### 6. Response Generation Flow
1. Relevant information is collected from all knowledge sources
2. Information is ranked by relevance and confidence
3. Structural relationships provide context for semantic matches
4. LLM Service processes the query with assembled context
5. L3Agent generates a comprehensive response addressing the ticket
6. Response includes:
   - Direct answer to the query
   - Supporting evidence from knowledge sources
   - Code/log references where applicable
   - Structural relationships between components
   - Code flow explanations across repositories
   - Knowledge currency indicators
   - Confidence levels
   - Requested additional context (if needed)
   - Suggested next steps or additional diagnostics

### 7. Metadata Recording Flow
1. Complete interaction details are recorded:
   - Request context and prompt
   - Response content
   - Model information
   - Performance metrics
   - Knowledge sources used
2. Context-response linkage is established
3. Provenance data is attached to the response
4. Metadata is indexed for future analysis
5. Usage metrics are updated

### 8. Feedback Flow
1. Response is returned through the API
2. Support Engineer reviews the response
3. Feedback (helpful/not helpful) can be submitted back through the API
4. Additional notes or corrections can be provided
5. Feedback is stored for future learning
6. LLM interaction metadata is updated with feedback

## Data Transformations

### Ticket Data
- Raw JSON payload → Structured issue representation
- Issue classification → Query parameters
- Conversation history → Context for follow-up responses

### Knowledge Data
- Code repositories → Vector embeddings for semantic search
- Code structure → Knowledge graph for relationships
- Code snippets → Relevant code excerpts with context
- Log entries → Filtered, relevant log patterns
- Knowledge base articles → Extracted insights

### LLM Data
- Knowledge context → Structured LLM prompt
- LLM interaction → Metadata records
- LLM response → Processed response with attributions
- Usage patterns → Performance optimizations
- Feedback → Model selection improvements

### Retrieval Strategies
- Semantic search → Conceptually relevant components
- Knowledge graph traversal → Structurally related components
- Hybrid retrieval → Comprehensive understanding of code behavior
- Cross-repository analysis → End-to-end workflows

### Response Data
- Multiple knowledge fragments → Coherent response
- Technical details → Appropriate level of explanation
- Structural relationships → Workflow explanations
- Raw data → Formatted, readable output
- Knowledge limitations → Transparent disclosures
- Missing context → Specific follow-up questions

## Storage Requirements

### Local POC Storage
- **Ticket Data**: JSON files in a structured directory or lightweight database
- **Code Repository**: Git-like structure with versioning
- **Vector Store**: Local embedding database (e.g., FAISS, ChromaDB)
- **Knowledge Graph**: Local graph database or in-memory graph structure
- **Logs**: Pre-provided text files with timestamp and severity levels
- **Knowledge Base**: Markdown files with structured metadata
- **LLM Metadata Store**: Structured database for LLM interaction data
- **Feedback**: JSON files linking to ticket IDs

## Input Data Formats

### Ticket Input Format
```json
{
  "ticket_id": "TICKET-1234",
  "subject": "Application crashes during startup",
  "description": "Our application crashes immediately upon startup with a NullPointerException...",
  "priority": "High",
  "created_at": "2023-06-01T10:30:00Z",
  "attachments": []
}
```

### Log Input Format
```json
[
  {"timestamp": "2023-06-01T10:25:30Z", "level": "ERROR", "service": "main-app", "message": "NullPointerException at com.example.App.initialize:45"},
  {"timestamp": "2023-06-01T10:25:29Z", "level": "INFO", "service": "main-app", "message": "Application starting with config: dev_mode=true"}
]
```

### Knowledge Graph Format
```json
{
  "nodes": [
    {"id": "class:com.example.Service", "type": "class", "name": "Service"},
    {"id": "method:com.example.Service.process", "type": "method", "name": "process"},
    {"id": "interface:com.example.Repository", "type": "interface", "name": "Repository"}
  ],
  "edges": [
    {"source": "method:com.example.Service.process", "target": "interface:com.example.Repository", "type": "uses"},
    {"source": "class:com.example.Service", "target": "method:com.example.Service.process", "type": "contains"}
  ]
}
```

### LLM Metadata Format
```json
{
  "request_id": "req-12345",
  "timestamp": "2023-06-01T10:32:15Z",
  "ticket_id": "TICKET-1234",
  "conversation_id": "conv-5678",
  "message_id": "msg-9101",
  "model": {
    "name": "gpt-4-0125-preview",
    "version": "2023-01-25",
    "provider": "openai"
  },
  "request": {
    "prompt": "Given the following code snippet and error log, explain what's causing the application to crash...",
    "parameters": {
      "temperature": 0.2,
      "max_tokens": 1000
    },
    "token_count": 782
  },
  "knowledge_sources": [
    {"type": "code", "id": "com.example.Service", "relevance": 0.92},
    {"type": "log", "id": "log-entry-12345", "relevance": 0.88},
    {"type": "kb", "id": "KB-567", "relevance": 0.75}
  ],
  "response": {
    "content": "The application is crashing due to a NullPointerException in the initialize method...",
    "token_count": 256,
    "latency_ms": 1250,
    "confidence": 0.87
  },
  "feedback": {
    "helpful": true,
    "comments": "Exactly identified the issue"
  }
}
```

## Future Production Considerations
- REST API would connect to actual ticket management systems like Jira
- Logs would be retrieved from centralized logging systems like Sumologic
- Knowledge Base would connect to document management systems
- Vector Store would be implemented on a distributed platform
- Knowledge Graph would be continuously updated with code changes
- LLM Metadata Store would scale to handle high volumes of interactions
- Multiple LLM providers would be supported through abstraction layer 