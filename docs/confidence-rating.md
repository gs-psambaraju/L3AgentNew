# L3Agent Confidence Ratings

The L3Agent provides confidence ratings for all responses to help you understand the reliability of the information provided.

## Understanding Confidence Ratings

Responses include both a numerical confidence score (0.0-1.0) and a human-readable rating:

* **Very High** (0.9-1.0): The system has found high-quality evidence directly relevant to your query and is highly confident in the response.
* **High** (0.75-0.9): The system has found good evidence and is confident in the response.
* **Medium** (0.5-0.75): The system has found moderate evidence. The response is likely accurate but may be incomplete.
* **Low** (0.25-0.5): The system has found limited evidence. The response should be verified.
* **Very Low** (0.0-0.25): The system found minimal or no evidence. The response is best treated as a suggestion rather than a definitive answer.

## Confidence Explanations

Each response includes a detailed explanation of how the confidence score was calculated, including:

1. **Component breakdown**: How each factor contributed to the final score
2. **Key factors**: Specific aspects that influenced the confidence level
3. **Raw metrics**: Detailed measurements used in the calculation

### Example Response

```json
{
  "answer": "The ConfigManager class is responsible for loading and validating configuration from multiple sources...",
  "confidence": 0.85,
  "confidence_rating": "High",
  "confidence_explanation": {
    "confidence_score": 0.85,
    "confidence_rating": "High",
    "components": {
      "vector_search": {
        "raw_score": 0.92,
        "weighted_contribution": 0.37,
        "percentage": "44%"
      },
      "tool_execution": {
        "raw_score": 0.75,
        "tool_success_rate": 0.75,
        "weighted_contribution": 0.23,
        "percentage": "27%"
      },
      "evidence_quality": {
        "raw_score": 0.68,
        "evidence_count": 8,
        "relevant_evidence": 6,
        "weighted_contribution": 0.14,
        "percentage": "16%"
      },
      "query_clarity": {
        "raw_score": 0.85,
        "weighted_contribution": 0.11,
        "percentage": "13%"
      }
    },
    "key_factors": [
      "High-quality vector search matches found",
      "Most or all tool executions completed successfully",
      "Evidence is highly relevant to the query",
      "Query is clear and specific"
    ]
  }
}
```

## How Confidence is Calculated

L3Agent calculates confidence based on a weighted algorithm that considers:

1. **Vector Search Quality (40%)**: The relevance of code snippets and knowledge articles found through semantic search.
2. **Tool Execution Success (30%)**: How well the code analysis tools performed their tasks.
3. **Evidence Quality (20%)**: The quantity, relevance, and quality of supporting evidence.
4. **Query Clarity (10%)**: How clear and specific the initial query was.

## Configuring Confidence Thresholds

System administrators can configure confidence thresholds and component weights by modifying properties in your application configuration file (`application.properties` or `application.yml`).

For reference, you can see a complete example in the sample configuration file at `src/main/resources/application-sample.properties`, which includes:

```properties
# Component weights (must sum to 1.0)
l3agent.confidence.vector-search-weight=0.40
l3agent.confidence.tool-execution-weight=0.30
l3agent.confidence.evidence-quality-weight=0.20
l3agent.confidence.query-clairty-weight=0.10

# Confidence rating thresholds
l3agent.confidence.very-high-threshold=0.90
l3agent.confidence.high-threshold=0.75
l3agent.confidence.medium-threshold=0.50
l3agent.confidence.low-threshold=0.25
```

These properties can also be set using JVM arguments for quick testing or temporary overrides:

```
-Dl3agent.confidence.vector-search-weight=0.35 -Dl3agent.confidence.tool-execution-weight=0.35
```

## Best Practices

* Use confidence ratings to determine when additional verification is needed
* For responses with Low or Very Low confidence, verify the information
* Consider refining queries that consistently receive low confidence ratings
* Monitor confidence trends across your organization to identify knowledge gaps 