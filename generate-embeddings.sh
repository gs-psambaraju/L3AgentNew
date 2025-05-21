#!/bin/bash

# Script to generate embeddings via the L3Agent API
# Usage: ./generate-embeddings.sh [path] [recursive]
# Example: ./generate-embeddings.sh ./src/main/java true

# Default values
PATH_TO_EMBED=${1:-""}
RECURSIVE=${2:-"false"}

# Make sure to run with database disabled to avoid locking issues
DATABASE_ENABLED=${3:-"false"}

echo "Generating embeddings for path: $PATH_TO_EMBED (recursive: $RECURSIVE, database: $DATABASE_ENABLED)"

# Start the application in the background with database disabled
echo "Starting L3Agent with database.enabled=$DATABASE_ENABLED"
if [ "$DATABASE_ENABLED" = "false" ]; then
  nohup mvn spring-boot:run -Dl3agent.database.enabled=false > embedding-generation.log 2>&1 &
else
  nohup mvn spring-boot:run > embedding-generation.log 2>&1 &
fi

APP_PID=$!
echo "Application started with PID: $APP_PID"

# Wait for the application to start (adjust time as needed)
echo "Waiting for application to start..."
sleep 20

# Call the API endpoint to generate embeddings
echo "Calling API to generate embeddings..."
RESPONSE=$(curl -s -X POST "http://localhost:8080/l3agent/generate-embeddings?path=$PATH_TO_EMBED&recursive=$RECURSIVE")

# Print the response
echo "Response:"
echo $RESPONSE | python -m json.tool

# Kill the application when done
echo "Shutting down application..."
kill $APP_PID

echo "Embedding generation process completed." 