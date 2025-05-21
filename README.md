# L3Agent

L3Agent is an intelligent system designed to answer technical questions through deep code analysis. It serves as a support acceleration tool that helps engineers understand complex codebases, diagnose issues, and provide accurate technical guidance by combining code search, analysis, and domain knowledge.

## Features

- Vector-based semantic code search
- Knowledge graph for code relationships
- Dynamic analysis tools via Model Control Plane (MCP)
- Hybrid query execution for comprehensive answers
- Detailed progress tracking and logging

## Quick Start

1. Clone this repository
2. Set up dependencies (requires Java 21 or higher, Maven)
3. Configure the `application.properties` file
4. Run with `mvn spring-boot:run`

## Important Setup Note

**This application requires code repositories to be placed in the `data/code/` directory.**

The vector embeddings reference code files in the `data/code/` directory. Without these repositories, the application will show file access errors when attempting to provide context for answers.

Required repositories:
- gs-integrations
- dp_dynamic_tasks
- gs-duct
- gainsight-adapter

These repositories are excluded from version control and must be manually added.

## API Endpoints

- `/api/l3agent/chat` - Main endpoint for asking questions
- `/api/l3agent/metrics` - System metrics endpoint
- `/api/l3agent/generate-embeddings` - Generate embeddings on demand

## Project Structure

- `src/` - Java source code
- `data/vector-store/` - Embeddings and vector metadata
- `data/code/` - Source code repositories (not included)
- `data/knowledge-graph/` - Knowledge graph data
- `docs/` - Project documentation

## Configuration

See `application.properties` for configuration options.

## License

Proprietary, all rights reserved. 