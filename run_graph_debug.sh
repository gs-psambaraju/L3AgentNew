#!/bin/bash
mvn spring-boot:run -Dspring-boot.run.profiles=nodb -Dspring-boot.run.arguments="--generate-knowledge-graph --path src/main/java/com/l3agent/config/LLMConfiguration.java" -Dlogging.level.com.l3agent=DEBUG
