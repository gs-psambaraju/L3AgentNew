# L3Agent Architecture

## System Overview
The L3Agent is designed as a modular, extensible system that processes support tickets using a combination of knowledge retrieval, context understanding, and intelligent response generation.

## High-Level Architecture

```
┌───────────────┐      ┌─────────────────┐      ┌───────────────┐
│  Ticket       │      │                 │      │  Response     │
│  Interface    │─────▶│  L3Agent Core   │─────▶│  Generation   │
└───────────────┘      └─────────────────┘      └───────────────┘
                               │                        │
                               ▼                        │
       ┌───────────────────────────────────────────┐   │
       │                                           │   │
┌──────▼──────┐  ┌──────────────┐  ┌─────────────┐ │   │   ┌────────────┐
│  Code        │  │  Log         │  │  Knowledge  │ │   └──▶│  LLM       │
│  Repository  │  │  Storage     │  │  Base       │ │       │  Service   │
└──────────────┘  └──────────────┘  └─────────────┘ │       └────────────┘
       │                                             │             │
       ▼                                             ▼             ▼
┌─────────────┐                              ┌─────────────┐ ┌────────────┐
│  Knowledge  │◀─────────────────────────────│  Vector     │ │  Metadata  │
│  Graph      │                              │  Store      │ │  Store     │
└─────────────┘                              └─────────────┘ └────────────┘
```

## Core Components

### 1. Ticket Interface
- Receives and validates incoming support tickets via REST API
- JSON payloads containing ticket details (no direct Jira integration for POC)
- Parses ticket content to extract relevant information
- Manages ticket state and conversation history

### 2. L3Agent Core
- Orchestrates the overall process flow
- Maintains conversation context
- Determines required knowledge sources
- Manages tool selection and execution
- Tracks LLM interactions and metadata
- Ensures complete context assembly before LLM invocation

### 3. Knowledge Sources
- **Code Repository**: Local storage of codebase for reference
- **Log Storage**: Pre-provided log files in a standardized format (no direct Sumologic integration for POC)
- **Knowledge Base**: Documentation, articles, and known solutions
- **Vector Store**: Semantic embeddings for efficient similarity search
- **Knowledge Graph**: Structural relationships between code components
- **Feedback Store**: Historical responses and their effectiveness
- **Metadata Store**: LLM interaction details, provenance, and version tracking

### 4. Response Generation
- Formulates coherent, accurate responses based on retrieved knowledge
- Tailors response format to the nature of the query
- Includes confidence levels and source citations
- Records LLM model information and response metadata
- Tracks response quality metrics

### 5. LLM Service
- Interfaces with LLM provider's API
- Manages prompt engineering and context formatting
- Handles rate limiting and token optimization
- Provides fallback mechanisms for API failures
- Records complete request/response payloads
- Tracks model versions and configuration

## Enhanced Knowledge Retrieval Architecture

### Vector-Based Semantic Search
- Replace basic pattern matching with vector embeddings for advanced semantic understanding
- Generate embeddings for code chunks with selective chunking approaches:
  - **On-demand generation model**: Embeddings are only generated when explicitly requested via API
  - Prioritize service interfaces, controllers, and critical business logic
  - Maintain chunking context with proper metadata
- Store embeddings locally in vector database
- Enable more natural language queries against codebase
- **PRIORITY UPDATE: This feature is critical for accuracy and should be implemented as top priority**
- Implementation approach:
  - Use code-specific embedding models (e.g., CodeBERT, UniXcoder)
  - Process each repository independently with appropriate chunking strategies
  - Provide automatic refresh and retry mechanisms for embedding generation failures

### Knowledge Graph for Code Structure
- Build a knowledge graph representing structural relationships in the code
- **On-demand generation model**: Knowledge graph is only built when explicitly requested via API
- Extract entities (classes, methods, interfaces) and relationships (calls, implements, extends)
- Use the graph for:
  - Finding related components when resolving issues
  - Tracing call paths and dependencies
  - Understanding class hierarchies
- Implementation approach:
  - In-memory graph structure with serialization for persistence
  - Focus on capturing Java-specific relationships initially
  - Command-line and REST API access for building and querying the graph

### Improved Code Understanding
- Combine vector similarity search with graph traversal:
  - Start with semantic matches from vector search
  - Expand through graph relationships for structural context
  - Follow import/usage paths to understand dependencies
- Handle cases where semantic or structural relevance may be stronger

## LLM Integration Architecture

### LLM Provider Abstraction
- Abstract interface to support multiple LLM providers
- Provider-specific implementation classes
- Configuration-driven model selection
- Versioning and compatibility tracking
- Standardized error handling

### Metadata Tracking
- Record all LLM interactions with:
  - Model name and version
  - Temperature and other parameters
  - Request timestamp
  - Complete prompt context
  - Token counts (input and output)
  - Response latency
  - Response quality metrics
  - Knowledge sources used

### Provenance Tracking
- Link responses to source context and prompts
- Tag responses with knowledge source versions
- Track confidence scores for different response components
- Support source attribution in responses
- Enable auditing of LLM decisions

### Request/Response Management
- Store complete request and response payloads
- Implement retention policies for LLM interaction data
- Provide query interface for historical interactions
- Support reprocessing with updated models/knowledge
- Analyze patterns in successful vs. unsuccessful responses

## Query Processing Workflow
1. Question Classification:
   - Determine query type (how-to, error explanation, functionality)
   - Identify required knowledge sources

2. Multi-Phase Retrieval:
   - Initial semantic search via vector embeddings
   - Graph traversal to find structurally related components
   - Integration of results with relevance scoring

3. Context Assembly:
   - Cross-repository context aggregation
   - Explanation of relationships between components
   - Construction of complete execution flows
   - Assembly of LLM prompt with retrieval results

4. LLM Invocation:
   - Send assembled context to LLM service
   - Record complete request metadata
   - Process and validate LLM response
   - Store response with provenance data

5. Response Generation:
   - Formatted explanations with code references
   - Visualization of relationships where relevant
   - Citations to source code locations
   - Transparency about knowledge limitations
   - Clear attribution of information sources
   - Knowledge currency indicators

## Data Flow

1. Support ticket is received through the Ticket Interface API
2. L3Agent Core analyzes the ticket content to determine the nature of the query
3. Relevant knowledge sources are queried based on query classification:
   - Vector store for semantic matches
   - Knowledge graph for structural relationships
4. Retrieved information is processed and contextualized
5. Context is assembled into structured LLM prompt
6. LLM Service invokes the model and records metadata
7. Response Generation creates an appropriate answer
8. Response is delivered back through the API
9. Metadata is stored for auditing and improvement
10. Feedback on response quality is collected and stored for continuous improvement

## POC Implementation Strategy

For the proof of concept:

1. All data sources will be implemented as local files/directories:
   - Code will be stored in a designated directory
   - Logs will be provided as input files in a standardized format
   - Ticket details will be received via a simple REST API as JSON payloads
   - Knowledge base will consist of markdown files
   - Vector store will be a local embedding database
   - Knowledge graph will be generated and stored locally
   - LLM metadata will be stored in a structured database
   - Feedback will be stored in a simple database or JSON files

2. The MCP server will be implemented as a lightweight service that:
   - Exposes APIs for ticket submission and response retrieval
   - Manages the L3Agent lifecycle
   - Provides logging and monitoring capabilities
   - Tracks LLM usage metrics

3. Agent tool calling will be implemented using:
   - Well-defined tool interfaces
   - A simple orchestration mechanism
   - Structured input/output contracts
   - Complete metadata capture

## Technical Considerations

### Scalability
- Component interfaces designed for future distributed deployment
- Clear separation of concerns to allow individual component scaling
- Efficient metadata storage for high-volume LLM interactions

### Security
- All data access will follow principle of least privilege
- Sensitive information handling follows security best practices
- Secure storage of LLM API keys and credentials
- Data minimization in LLM prompts

### Extensibility
- Plugin architecture for adding new knowledge sources
- Configurable processing pipeline for custom workflows
- Interfaces designed to later connect to actual systems (Jira, Sumologic, etc.)
- Support for multiple LLM providers and models

## Next Steps
1. Implement vector embedding generation for code artifacts
2. Develop the knowledge graph construction process
3. Create the bidirectional retrieval mechanism
4. Design and implement LLM metadata tracking
5. Establish LLM integration layer with provider abstraction
6. Enhance the response generation with structural context 