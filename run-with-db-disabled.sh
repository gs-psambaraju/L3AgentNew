#!/bin/bash

# Script to run the application with database disabled

echo "Starting L3Agent with database disabled..."

# Run the application with database disabled
mvn spring-boot:run -Dl3agent.database.enabled=false

echo "Application stopped." 