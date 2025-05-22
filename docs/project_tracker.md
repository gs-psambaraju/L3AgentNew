# L3Agent Project Tracker

## Project Overview

L3Agent is an intelligent system designed to answer technical questions through deep code analysis. It serves as a support acceleration tool that helps engineers understand complex codebases, diagnose issues, and provide accurate technical guidance by combining code search, analysis, and domain knowledge.

### Current Status
- **Current Phase**: Enhanced User Experience and Error Handling
- **Key Focus**: Improving response confidence scoring, error handling, and usability
- **Next Major Milestone**: Full API and Documentation Cleanup

## Sprint Backlog

These are stories actively being worked on in the current sprint.

### In Progress

#### [L3-112] Implement dynamic repository structure discovery
- **Story**: As a developer, I need the system to dynamically discover repository structure without hard-coded mappings
- **Description**: Replace hard-coded repository subdirectory mappings with a dynamic discovery mechanism
- **Acceptance Criteria**:
  - System automatically discovers repository structure at startup
  - No hard-coded mappings in VectorBasedCodeRepositoryService
  - Configuration-based override capability for special cases
  - Proper caching for performance
  - Graceful handling of repository structure changes
- **Owner**: Unassigned
- **Priority**: High
- **Story Points**: TBD
- **Status**: In Progress
- **Notes**: Currently, the system uses hard-coded mappings from subdirectories (like "uc-web") to parent repositories (like "gs-integrations")

#### Technical Debt: Refactor VectorStoreService
- **Story**: As a developer, I need the vector store service refactored to improve maintainability
- **Description**: Refactor HnswVectorStoreService into smaller, more focused components
- **Acceptance Criteria**:
  - Extract common HTTP client handling to a shared utility ✓
  - Create separate embedding service component
  - Create dedicated index management component
  - Improve error handling and resource cleanup
- **Owner**: Unassigned
- **Priority**: High
- **Story Points**: TBD
- **Status**: In Progress
- **Notes**: HnswVectorStoreService was disabled by commenting @Service, not fully replaced or refactored as planned

#### Error Handling Consolidation
- **Story**: As a developer, I need standardized error handling across the application
- **Description**: Create a centralized resilience module for standardized error handling
- **Acceptance Criteria**:
  - Implement common retry policies with configurable parameters ✓
  - Develop unified circuit breaker implementation for external services
  - Create standardized error response formatting
  - Build centralized failure logging mechanism
- **Owner**: Unassigned
- **Priority**: Medium
- **Story Points**: TBD
- **Status**: In Progress
- **Notes**: RetryUtil is complete but other components need to be implemented

## Product Backlog

These are prioritized stories for future sprints.

### High Priority

#### [L3-106] Implement Internet Data Retrieval Tool
- **Story**: As a support engineer, I need access to up-to-date API documentation and reference materials
- **Description**: Build a tool to fetch information from trusted internet sources to augment local knowledge
- **Acceptance Criteria**:
  - Tool can retrieve API documentation from major platforms (Salesforce, AWS, etc.)
  - Support for filtering and extracting relevant sections from retrieved content
  - Proper caching mechanism to reduce redundant requests
  - Security controls to limit access to trusted domains only
- **Owner**: Unassigned
- **Priority**: High
- **Story Points**: TBD
- **Status**: Not Started
- **Dependencies**: L3-100 (Completed)

#### Hybrid Query Engine Integration
- **Story**: As a developer, I need to improve the Hybrid Query Engine integration
- **Description**: Address technical concerns in the Hybrid Query Engine integration
- **Acceptance Criteria**:
  - Formalize vector search tool contract and implementation details
  - Define standard tool response contracts instead of using generic Map<String, Object>
  - Create validation mechanism for tool responses
- **Owner**: Unassigned
- **Priority**: High
- **Story Points**: TBD
- **Status**: Not Started

### Medium Priority

#### API Cleanup and Consolidation
- **Story**: As a developer, I need to clean up and consolidate the API
- **Description**: Remove unnecessary endpoints and improve core API functionality
- **Acceptance Criteria**:
  - Remove diagnostic endpoints from production code
  - Refactor ticket management endpoints
  - Optimize knowledge graph exploration endpoints
  - Improve core chat workflow
- **Owner**: Unassigned
- **Priority**: Medium
- **Story Points**: TBD
- **Status**: Not Started

#### Code Cleanup and Refactoring
- **Story**: As a developer, I need to clean up unused code and improve structure
- **Description**: Remove unused components and refactor complex methods
- **Acceptance Criteria**:
  - Remove unused CLI components
  - Clean up model hierarchy
  - Remove unused repository classes
  - Streamline service interfaces
  - Refactor complex methods (e.g., BasicL3AgentService.generateEmbeddingsOnDemand)
- **Owner**: Unassigned
- **Priority**: Medium
- **Story Points**: TBD
- **Status**: Not Started

#### Resource Management Improvements
- **Story**: As a system administrator, I need better resource management
- **Description**: Implement controls to prevent OOM errors and improve performance
- **Acceptance Criteria**:
  - Add file size limits to prevent OOM errors
  - Implement incremental processing for very large files
  - Create memory usage monitoring and adaptive throttling
  - Implement background/scheduled processing for embedding generation
- **Owner**: Unassigned
- **Priority**: Medium
- **Story Points**: TBD
- **Status**: Not Started

#### Documentation Plan
- **Story**: As a user, I need comprehensive documentation
- **Description**: Create detailed documentation for all aspects of the system
- **Acceptance Criteria**:
  - Document hybrid architecture design and implementation
  - Document API endpoints with examples
  - Create integration guides for client applications
  - Document system architecture and data flows
  - Develop operations manual
- **Owner**: Unassigned
- **Priority**: Medium
- **Story Points**: TBD
- **Status**: Partially Complete (MCP design document completed)

## Completed Stories

These stories have been completed and deployed.

### Sprint X (Most Recent)

#### [L3-108] Add response confidence rating
- **Story**: As a support engineer, I need to understand how confident the system is in its answers
- **Description**: Implement a comprehensive confidence scoring mechanism for responses based on evidence quality and tool execution success
- **Acceptance Criteria**:
  - Every response includes an overall confidence score (0.0-1.0)
  - Response includes confidence tier categorization ("Very High", "High", "Medium", "Low", "Very Low")
  - Score calculation factors in weighted components (vector search 40%, tool execution 30%, evidence quality 20%, query clarity 10%)
  - API response includes component breakdown explaining what contributed to the score
  - For low confidence, response includes specific details about missing evidence
  - Anti-hallucination mechanism prevents "best guessing" when confidence is insufficient
  - Documentation and examples of how to interpret confidence scores
- **Owner**: Completed
- **Priority**: High
- **Story Points**: Completed
- **Status**: Completed
- **Notes**: 
  - Implemented three new components: ConfidenceMetrics, ConfidenceCalculator, and ConfidenceEnhancer
  - Used weighted algorithm for calculating confidence
  - Made thresholds configurable via application properties
  - Added detailed confidence explanations in responses
  - Created comprehensive documentation in confidence-rating.md

#### [L3-110] Fix Metadata-Vector Mismatch in Default Namespace
- **Story**: As a support engineer, I need consistent indexing of code embeddings
- **Description**: Fix the metadata entries in the default namespace to match vector files
- **Acceptance Criteria**:
  - All metadata entries properly match their corresponding vector files
  - System starts without warnings related to vector-metadata mismatches
  - All vectors are properly indexed and searchable
- **Owner**: Completed
- **Priority**: High
- **Story Points**: Completed
- **Status**: Completed
- **Notes**: Implemented MetadataFixUtility to scan for mismatches and align metadata with vector files

#### [L3-111] Enhanced Metadata Fix Utility with Orphaned Entry Cleanup
- **Story**: As a system administrator, I need the system to start without warnings
- **Description**: Enhance the MetadataFixUtility to clean up orphaned metadata entries
- **Acceptance Criteria**:
  - Identify metadata entries without matching vectors
  - Remove orphaned entries from the system
  - Ensure clean system startup without warnings
- **Owner**: Completed
- **Priority**: Medium
- **Story Points**: Completed
- **Status**: Completed
- **Notes**: Successfully fixed 7,262 metadata entries in the default namespace

### Sprint Y (Previous)

#### [L3-107] Fix file path resolution in vector search results
- **Story**: As a support engineer, I need reliable file access when vector search identifies relevant code
- **Description**: Fix the mismatch between file paths stored in embeddings and actual file system paths
- **Acceptance Criteria**:
  - System correctly handles relative and absolute paths in embeddings
  - Proper path resolution for files from different repositories
  - Recursive file search capability to locate files in nested repository structures
  - Graceful error handling when files can't be found
- **Owner**: Completed
- **Priority**: Critical
- **Story Points**: Completed
- **Status**: Completed
- **Notes**: Implemented proper repository structure awareness with multiple resolution strategies

#### [L3-109] Integrate MCP tools with chat endpoint
- **Story**: As a support engineer, I need the system to automatically use the right analysis tools for my queries
- **Description**: Enhance the chat endpoint to automatically invoke relevant MCP tools based on query content
- **Acceptance Criteria**:
  - Chat endpoint automatically identifies when to use MCP tools
  - Tool selection based on query content and initial vector search results
  - Results from tools properly integrated into final response
  - API response includes tool-specific findings
  - Performance impact properly managed (parallel execution, timeouts)
- **Owner**: Completed
- **Priority**: Critical
- **Story Points**: Completed
- **Status**: Completed
- **Notes**: Used HybridQueryExecutionEngine to intelligently determine when to use MCP tools

#### Default Namespace Population
- **Story**: As a support engineer, I need all code indexed in the default namespace for consistent search fallback
- **Description**: Ensure all embeddings are duplicated in the default namespace
- **Acceptance Criteria**:
  - All embeddings from repository-specific namespaces copied to default namespace
  - Proper ID prefixing to prevent collisions
  - Verification of successful copying
- **Owner**: Completed
- **Priority**: High
- **Story Points**: Completed
- **Status**: Completed
- **Notes**: Implemented EmbeddingCopyUtility and successfully copied all 7,262 embeddings

### Earlier Sprints

#### [L3-100] Implement Model Control Plane (MCP) core infrastructure
- **Story**: As a developer, I need a framework to manage and execute analysis tools
- **Description**: Build the core Model Control Plane to register, manage and execute analysis tools
- **Acceptance Criteria**:
  - Tool registration mechanism
  - Request handling and execution orchestration
  - Error handling and retry mechanism
  - Configurable settings
- **Owner**: Completed
- **Priority**: High
- **Story Points**: Completed
- **Status**: Completed
- **Notes**: Implemented MCPToolInterface, MCPRequestHandler, MCPToolRegistry

#### [L3-101] Implement dynamic Call Path Analyzer tool
- **Story**: As a support engineer, I need to understand method call relationships
- **Description**: Build a tool that analyzes Java bytecode to construct call graphs
- **Acceptance Criteria**:
  - Accurate bytecode analysis
  - Visual representation of call paths
  - Depth-limited traversal options
- **Owner**: Completed
- **Priority**: High
- **Story Points**: Completed
- **Status**: Completed
- **Notes**: Implemented using ByteBuddy for bytecode analysis

#### [L3-102] Implement Configuration Impact Analyzer tool
- **Story**: As a support engineer, I need to understand how configuration affects behavior
- **Description**: Build a tool that analyzes Spring and custom configuration properties
- **Acceptance Criteria**:
  - Identify configuration property usages
  - Map configuration to affected components
  - Support for various configuration sources
- **Owner**: Completed
- **Priority**: High
- **Story Points**: Completed
- **Status**: Completed
- **Notes**: Implemented with regex pattern-based codebase scanning

#### [L3-103] Implement Error Chain Mapper tool
- **Story**: As a support engineer, I need to understand error propagation
- **Description**: Build a tool that analyzes exception hierarchy and propagation patterns
- **Acceptance Criteria**:
  - Map exception hierarchies
  - Identify error propagation paths
  - Detect error handling anti-patterns
- **Owner**: Completed
- **Priority**: High
- **Story Points**: Completed
- **Status**: Completed
- **Notes**: Integrated with BytecodeAnalyzer for exception analysis

#### [L3-104] Implement Cross-Repository Tracer tool
- **Story**: As a support engineer, I need to trace code across repositories
- **Description**: Build a tool that indexes and searches multiple repositories
- **Acceptance Criteria**:
  - Search across multiple repositories
  - Find class and method usages
  - Resilient to temporary failures
- **Owner**: Completed
- **Priority**: High
- **Story Points**: Completed
- **Status**: Completed
- **Notes**: Used RetryUtil for resilience

#### [L3-105] Implement Hybrid Query Execution Engine
- **Story**: As a developer, I need to intelligently select analysis approaches
- **Description**: Build a query engine to determine when to use pre-computed knowledge vs. dynamic analysis
- **Acceptance Criteria**:
  - Classify queries to determine appropriate tools
  - Create execution plans for complex queries
  - Integrate with chat endpoint
- **Owner**: Completed
- **Priority**: High
- **Story Points**: Completed
- **Status**: Completed
- **Notes**: Implemented with GPT-based classification

## Architectural Evolution
L3Agent has evolved through several architectural approaches:

### Initial Vector-Based Approach (Previous Phase)
- Focused on semantic code search using embeddings
- Generated vector representations of code chunks
- Used similarity search to find relevant code snippets
- Limitations:
  - Only provided code snippets without runtime context
  - Couldn't trace execution flows or configuration impacts
  - Limited to pre-computed knowledge with no dynamic analysis
  - Offered ~60% confidence for complex cross-component issues

### Knowledge Graph Attempt (Discontinued)
- Tried to model code relationships as a graph
- Used Neo4j to store class/method relationships
- Created navigable representation of code structure
- Discontinued due to:
  - Scalability issues with large codebases
  - Complex schema maintenance
  - Difficulty representing dynamic behaviors
  - Redundancy with vector search for most use cases

### Current Hybrid Architecture (Implemented)
- Combines pre-computed knowledge with dynamic tool calling
- Uses Model Control Plane (MCP) to orchestrate analysis tools
- Implements specialized analysis capabilities:
  - Dynamic call path analysis
  - Cross-repository tracing
  - Error chain mapping
  - Configuration impact analysis
- Benefits:
  - Improved confidence for complex issues (estimated 7.5-9/10)
  - Dynamic analysis of runtime behavior
  - Targeted investigations based on initial vector search
  - Extensible tooling architecture

## Key Decision Log
| Date | Decision | Rationale | Alternatives Considered |
|------|----------|-----------|-------------------------|
| Q4 2022 | Adopt vector embeddings approach | Enables semantic search without complex schema maintenance | Graph databases, full-text search, AST parsing |
| Q1 2023 | Use HNSWLIB for vector storage | Superior performance for nearest neighbor search compared to alternatives | PostgreSQL vector, Elasticsearch, Pinecone |
| Q1 2023 | Discontinue Neo4j integration | Excessive maintenance overhead with limited additional value | Optimize graph queries, use hybrid representation |
| Q2 2023 | Remove OpenAI dependency | Consolidate on internal LLM service (Gainsight) for data security | Azure OpenAI Service, self-hosted models |
| Q2 2023 | Adopt hybrid architecture with MCP | Need for dynamic analysis capabilities to improve complex question answering | Enhanced static analysis, specialized embeddings |
| Q2 2023 | Implement configuration-driven retry framework | Standardize resilience across components while enabling flexible configurations | Spring Retry, custom annotations, service-specific implementations |
| Q3 2023 | Increase tool timeout from 10s to 25s | Improved reliability for vector search operations requiring more time | Parallel execution, query optimization |

## Milestones Timeline
| Milestone | Target Date | Status | Notes |
|-----------|-------------|--------|-------|
| Vector Embedding Implementation | -- | Completed | |
| Code Chunking Implementation | -- | Completed | |
| Batch Processing Enhancement | -- | Completed | |
| Architecture Consolidation | -- | Completed | |
| Model Control Plane (MCP) Implementation | Q2 2023 | Completed | |
| Centralized Retry Implementation | Q2 2023 | Partially Complete | Core framework complete but not fully adopted |
| Dynamic Analysis Tools | Q3 2023 | Completed | All tools implemented |
| Hybrid Query Engine | Q3 2023 | Completed | Engine fully integrated with chat endpoint |
| MCP Chat Integration | Q3 2023 | Completed | Successfully integrated with enhanced error handling |
| Error Handling Consolidation | Q3 2023 | In Progress | |
| Code Cleanup | Q4 2023 | Not Started | |
| API Cleanup & Consolidation | Q4 2023 | Not Started | |
| Performance Metrics Implementation | Q4 2023 | Not Started | |

## Success Metrics
- **Technical Accuracy**: Target 90%+ accuracy for complex technical questions
- **Response Time**: 90% of queries answered in <5 seconds
- **Resource Efficiency**: <2GB memory usage per JVM instance
- **Developer Productivity**: Reduce time to resolution by 60%+
- **Support Escalation Reduction**: Decrease L2/L3 escalations by 40%+ 