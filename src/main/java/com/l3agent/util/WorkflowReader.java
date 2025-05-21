package com.l3agent.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.l3agent.service.CodeWorkflowService.WorkflowStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Utility for reading and displaying workflow step files.
 * Usage: java com.l3agent.util.WorkflowReader path/to/workflow_steps.dat
 */
public class WorkflowReader {
    private static final Logger logger = LoggerFactory.getLogger(WorkflowReader.class);
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java com.l3agent.util.WorkflowReader path/to/workflow_steps.dat");
            System.exit(1);
        }
        
        String filePath = args[0];
        File file = new File(filePath);
        
        if (!file.exists() || !file.isFile()) {
            System.err.println("File not found or is not a regular file: " + filePath);
            System.exit(1);
        }
        
        try {
            readWorkflowSteps(file);
        } catch (Exception e) {
            System.err.println("Error reading workflow steps: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    @SuppressWarnings("unchecked")
    private static void readWorkflowSteps(File file) throws IOException, ClassNotFoundException {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
            Object obj = ois.readObject();
            
            if (obj instanceof List) {
                List<WorkflowStep> steps = (List<WorkflowStep>) obj;
                displayWorkflowSteps(steps);
            } else if (obj instanceof Map) {
                Map<String, List<WorkflowStep>> fileToStepsMap = (Map<String, List<WorkflowStep>>) obj;
                displayWorkflowStepsByFile(fileToStepsMap);
            } else {
                System.out.println("Unknown object type: " + obj.getClass().getName());
            }
        }
    }
    
    private static void displayWorkflowSteps(List<WorkflowStep> steps) {
        System.out.println("Found " + steps.size() + " workflow steps:");
        System.out.println("=".repeat(80));
        
        for (int i = 0; i < steps.size(); i++) {
            WorkflowStep step = steps.get(i);
            System.out.println("Step " + (i + 1) + ":");
            displayStep(step);
            System.out.println("-".repeat(80));
        }
    }
    
    private static void displayWorkflowStepsByFile(Map<String, List<WorkflowStep>> fileToStepsMap) {
        System.out.println("Found workflow steps for " + fileToStepsMap.size() + " files:");
        System.out.println("=".repeat(80));
        
        int totalSteps = 0;
        for (Map.Entry<String, List<WorkflowStep>> entry : fileToStepsMap.entrySet()) {
            String filePath = entry.getKey();
            List<WorkflowStep> steps = entry.getValue();
            totalSteps += steps.size();
            
            System.out.println("File: " + filePath);
            System.out.println("Contains " + steps.size() + " workflow steps:");
            System.out.println("-".repeat(80));
            
            for (int i = 0; i < steps.size(); i++) {
                WorkflowStep step = steps.get(i);
                System.out.println("  Step " + (i + 1) + ":");
                displayStep(step);
                System.out.println("-".repeat(60));
            }
            
            System.out.println("=".repeat(80));
        }
        
        System.out.println("Total steps across all files: " + totalSteps);
    }
    
    private static void displayStep(WorkflowStep step) {
        System.out.println("  Source Repository: " + step.getSourceRepository());
        System.out.println("  Source File: " + step.getSourceFile());
        System.out.println("  Source Method: " + step.getSourceMethod());
        System.out.println("  Target Repository: " + step.getTargetRepository());
        System.out.println("  Target File: " + (step.getTargetFile() != null ? step.getTargetFile() : "[unknown file]"));
        System.out.println("  Target Method: " + step.getTargetMethod());
        System.out.println("  Cross-Repository: " + step.isCrossRepository());
        System.out.println("  Confidence: " + step.getConfidence());
        
        // Display pattern type if available
        if (step.getPatternType() != null && !step.getPatternType().isEmpty()) {
            System.out.println("  Pattern Type: " + step.getPatternType());
        }
        
        // Display data parameters if available
        if (step.getDataParameters() != null && !step.getDataParameters().isEmpty()) {
            System.out.println("  Data Parameters:");
            for (String param : step.getDataParameters()) {
                System.out.println("    - " + param);
            }
        }
    }
    
    private static boolean hasValue(String str) {
        return str != null && !str.trim().isEmpty();
    }
    
    private static String formatMultiLine(String text, int indentSize) {
        if (text == null) return "";
        String indent = " ".repeat(indentSize);
        return text.replace("\n", "\n" + indent);
    }
} 