package com.l3agent.service.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for identifying and filtering boilerplate code.
 * Helps reduce embedding generation for low-value code like simple getters/setters.
 */
@Component("serviceBoilerplateFilter")
public class BoilerplateFilter {
    
    private static final Logger logger = LoggerFactory.getLogger(BoilerplateFilter.class);
    
    // Regular expressions for identifying boilerplate code patterns
    private static final Pattern GETTER_PATTERN = Pattern.compile(
            "\\s*(?:public|private|protected)\\s+\\w+\\s+get(\\w+)\\(\\)\\s*\\{\\s*" +
            "return\\s+\\w+;?\\s*\\}\\s*"
    );
    
    private static final Pattern SETTER_PATTERN = Pattern.compile(
            "\\s*(?:public|private|protected)\\s+void\\s+set(\\w+)\\(\\w+\\s+\\w+\\)\\s*\\{\\s*" +
            "this\\.\\w+\\s*=\\s*\\w+;?\\s*\\}\\s*"
    );
    
    private static final Pattern SIMPLE_TOSTRING_PATTERN = Pattern.compile(
            "\\s*@Override\\s+public\\s+String\\s+toString\\(\\)\\s*\\{[^}]{0,200}\\}\\s*"
    );
    
    private static final Pattern SIMPLE_EQUALS_PATTERN = Pattern.compile(
            "\\s*@Override\\s+public\\s+boolean\\s+equals\\(Object\\s+\\w+\\)\\s*\\{[^}]{0,300}\\}\\s*"
    );
    
    private static final Pattern SIMPLE_HASHCODE_PATTERN = Pattern.compile(
            "\\s*@Override\\s+public\\s+int\\s+hashCode\\(\\)\\s*\\{[^}]{0,200}\\}\\s*"
    );
    
    private static final Pattern SIMPLE_CONSTRUCTOR_PATTERN = Pattern.compile(
            "\\s*(?:public|private|protected)\\s+(\\w+)\\(\\)\\s*\\{\\s*" +
            "(?:super\\(\\);?\\s*)?(?://.*)?\\s*\\}\\s*"
    );
    
    private static final Pattern PARAMETER_CONSTRUCTOR_PATTERN = Pattern.compile(
            "\\s*(?:public|private|protected)\\s+(\\w+)\\([^)]{1,200}\\)\\s*\\{\\s*" +
            "(?:this\\.[\\w\\s=;.]+){1,10}\\s*\\}\\s*"
    );
    
    /**
     * Checks if a chunk of code is likely boilerplate.
     * 
     * @param code The code to check
     * @return True if the code is identified as boilerplate
     */
    public boolean isBoilerplate(String code) {
        if (code == null || code.trim().isEmpty()) {
            return false;
        }
        
        // Check for getter methods
        Matcher getterMatcher = GETTER_PATTERN.matcher(code);
        if (getterMatcher.matches()) {
            logger.debug("Identified getter method: {}", getterMatcher.group(1));
            return true;
        }
        
        // Check for setter methods
        Matcher setterMatcher = SETTER_PATTERN.matcher(code);
        if (setterMatcher.matches()) {
            logger.debug("Identified setter method: {}", setterMatcher.group(1));
            return true;
        }
        
        // Check for simple toString methods
        if (SIMPLE_TOSTRING_PATTERN.matcher(code).matches()) {
            logger.debug("Identified simple toString method");
            return true;
        }
        
        // Check for simple equals methods
        if (SIMPLE_EQUALS_PATTERN.matcher(code).matches()) {
            logger.debug("Identified simple equals method");
            return true;
        }
        
        // Check for simple hashCode methods
        if (SIMPLE_HASHCODE_PATTERN.matcher(code).matches()) {
            logger.debug("Identified simple hashCode method");
            return true;
        }
        
        // Check for simple constructors
        Matcher constructorMatcher = SIMPLE_CONSTRUCTOR_PATTERN.matcher(code);
        if (constructorMatcher.matches()) {
            logger.debug("Identified simple constructor: {}", constructorMatcher.group(1));
            return true;
        }
        
        // Check for simple parameter constructors (just assigning fields)
        Matcher paramConstructorMatcher = PARAMETER_CONSTRUCTOR_PATTERN.matcher(code);
        if (paramConstructorMatcher.matches()) {
            logger.debug("Identified parameter constructor: {}", paramConstructorMatcher.group(1));
            return true;
        }
        
        // Not identified as boilerplate
        return false;
    }
    
    /**
     * Checks if a field declaration is a simple field.
     * 
     * @param code The code to check
     * @return True if the code is a simple field declaration
     */
    public boolean isSimpleField(String code) {
        // Check for simple field declarations
        Pattern simpleField = Pattern.compile(
                "\\s*(?:private|protected|public)\\s+(?:final\\s+)?\\w+(?:<[^>]+>)?\\s+\\w+\\s*;\\s*"
        );
        
        return simpleField.matcher(code).matches();
    }
    
    /**
     * Creates a summarized version of boilerplate code.
     * 
     * @param code The boilerplate code
     * @return A summarized version of the code
     */
    public String summarizeBoilerplate(String code) {
        // For getters/setters, extract the property name
        Matcher getterMatcher = GETTER_PATTERN.matcher(code);
        if (getterMatcher.matches()) {
            return "// Getter for " + getterMatcher.group(1);
        }
        
        Matcher setterMatcher = SETTER_PATTERN.matcher(code);
        if (setterMatcher.matches()) {
            return "// Setter for " + setterMatcher.group(1);
        }
        
        // For toString/equals/hashCode, return a simple comment
        if (SIMPLE_TOSTRING_PATTERN.matcher(code).matches()) {
            return "// toString method";
        }
        
        if (SIMPLE_EQUALS_PATTERN.matcher(code).matches()) {
            return "// equals method";
        }
        
        if (SIMPLE_HASHCODE_PATTERN.matcher(code).matches()) {
            return "// hashCode method";
        }
        
        // For constructors, include the constructor name
        Matcher constructorMatcher = SIMPLE_CONSTRUCTOR_PATTERN.matcher(code);
        if (constructorMatcher.matches()) {
            return "// Empty constructor for " + constructorMatcher.group(1);
        }
        
        Matcher paramConstructorMatcher = PARAMETER_CONSTRUCTOR_PATTERN.matcher(code);
        if (paramConstructorMatcher.matches()) {
            return "// Parameter constructor for " + paramConstructorMatcher.group(1);
        }
        
        // Default fallback
        return "// Boilerplate code";
    }
} 