#!/bin/bash

# Script to run a test with database disabled

# Make sure script is executable
echo "Running embedding generation test with database disabled..."

# Run only the specific test we created
mvn test -Dtest=EmbeddingGenerationTest -Dspring.profiles.active=test-no-db

echo "Test completed." 