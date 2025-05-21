package com.l3agent.mcp.tools.callpath;

import com.l3agent.mcp.config.L3AgentPathConfig;
import com.l3agent.mcp.tools.callpath.model.CallGraph;
import com.l3agent.mcp.tools.callpath.model.MethodNode;
import com.l3agent.mcp.tools.errorchain.model.ExceptionNode;
import com.l3agent.mcp.tools.errorchain.model.PropagationChain;
import net.bytebuddy.ClassFileVersion;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.pool.TypePool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.RegexPatternTypeFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import jakarta.annotation.PostConstruct;
import java.util.regex.Pattern;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/**
 * Analyzes Java bytecode to construct a call graph of method invocations.
 * Uses ByteBuddy for bytecode analysis.
 */
@Component
public class BytecodeAnalyzer {
    
    private static final Logger logger = LoggerFactory.getLogger(BytecodeAnalyzer.class);
    
    @Value("${l3agent.callpath.max-depth:10}")
    private int maxDepth;
    
    @Value("${l3agent.callpath.include-libraries:false}")
    private boolean includeLibraries;
    
    @Value("${l3agent.callpath.max-nodes:500}")
    private int maxNodes;
    
    @Autowired
    private ApplicationContext applicationContext;
    
    @Autowired
    private L3AgentPathConfig pathConfig;
    
    // Base package to scan - could be configured via properties
    @Value("${l3agent.callpath.base-package:${l3agent.paths.base-package:com.l3agent}}")
    private String basePackage;
    
    private final TypePool typePool;
    private final Map<String, TypeDescription> typeCache;
    private final Set<String> processedMethods;
    private final Map<String, ExceptionNode> exceptionNodeCache;
    
    // Call graph: caller -> list of callees
    private final Map<String, Set<String>> callGraph;
    
    // Reverse call graph: callee -> list of callers
    private final Map<String, Set<String>> reverseCallGraph;
    
    /**
     * Creates a new BytecodeAnalyzer with default configuration.
     */
    public BytecodeAnalyzer() {
        this.typePool = TypePool.Default.of(ClassFileLocator.ForClassLoader.of(getClass().getClassLoader()));
        this.typeCache = new ConcurrentHashMap<>();
        this.processedMethods = ConcurrentHashMap.newKeySet();
        this.exceptionNodeCache = new ConcurrentHashMap<>();
        this.callGraph = new ConcurrentHashMap<>();
        this.reverseCallGraph = new ConcurrentHashMap<>();
    }
    
    /**
     * Initializes the call graph by scanning all classes in the classpath.
     * This helps to build a more comprehensive call graph for analysis.
     */
    @PostConstruct
    public void initializeCallGraph() {
        // Use common configuration
        basePackage = pathConfig.getBasePackage();
        
        try {
            logger.info("Initializing call graph by scanning classes in {}", basePackage);
            
            // Create a classpath scanner that accepts all classes
            ClassPathScanningCandidateComponentProvider scanner = 
                new ClassPathScanningCandidateComponentProvider(false);
            scanner.addIncludeFilter(new RegexPatternTypeFilter(Pattern.compile(".*")));
            
            // Scan classpath for classes
            int classCount = 0;
            int methodCount = 0;
            
            for (org.springframework.beans.factory.config.BeanDefinition bd : 
                     scanner.findCandidateComponents(basePackage)) {
                
                String className = bd.getBeanClassName();
                if (className == null) {
                    continue;
                }
                
                try {
                    // Load the class and analyze its methods
                    TypeDescription typeDesc = resolveClass(className);
                    typeCache.put(className, typeDesc);
                    classCount++;
                    
                    // For each method, analyze its calls
                    for (MethodDescription.InDefinedShape methodDesc : typeDesc.getDeclaredMethods()) {
                        String methodName = methodDesc.getName();
                        
                        // Skip synthetic methods and other special cases
                        if (methodName.contains("$") || methodName.equals("<clinit>")) {
                            continue;
                        }
                        
                        // Create a method node
                        MethodNode methodNode = createMethodNode(typeDesc, methodName);
                        
                        // Find method calls - this will populate our call graph
                        findMethodCalls(methodNode);
                        methodCount++;
                    }
                } catch (Exception e) {
                    // Just log and continue to the next class
                    logger.debug("Error analyzing class {}: {}", className, e.getMessage());
                }
                
                // Log progress periodically
                if (classCount % 100 == 0) {
                    logger.debug("Analyzed {} classes, {} methods so far", classCount, methodCount);
                }
            }
            
            logger.info("Call graph initialization complete: {} classes, {} methods, {} call relationships", 
                      classCount, methodCount, callGraph.values().stream().mapToInt(Set::size).sum());
            
        } catch (Exception e) {
            logger.warn("Error initializing call graph: {}", e.getMessage());
        }
    }
    
    /**
     * Analyzes a method and constructs a call graph.
     * 
     * @param methodPath Fully qualified path to the method (e.g., com.example.Service.method)
     * @param customMaxDepth Optional custom max depth for the analysis
     * @return The constructed call graph
     * @throws AnalysisException If analysis fails
     */
    public CallGraph analyzeMethod(String methodPath, Integer customMaxDepth) throws AnalysisException {
        try {
            // Reset state for a new analysis
            processedMethods.clear();
            
            // Use custom depth if provided, else use the configured default
            int depth = customMaxDepth != null ? customMaxDepth : maxDepth;
            
            // Parse method path
            MethodPathInfo pathInfo = parseMethodPath(methodPath);
            
            // Initialize the call graph
            CallGraph callGraph = new CallGraph();
            
            // Find the method and create the root node
            TypeDescription typeDescription = resolveClass(pathInfo.className);
            MethodNode rootNode = createMethodNode(typeDescription, pathInfo.methodName);
            
            callGraph.setRootNode(rootNode);
            
            // Track node count to enforce limits
            AtomicInteger nodeCount = new AtomicInteger(1);
            
            // Start the recursive analysis
            analyzeMethodCalls(callGraph, rootNode, 0, depth, nodeCount);
            
            logger.info("Completed call path analysis for {}: {} nodes, {} edges", 
                    methodPath, callGraph.getNodeCount(), callGraph.getEdgeCount());
            
            return callGraph;
        } catch (Exception e) {
            logger.error("Failed to analyze method {}", methodPath, e);
            throw new AnalysisException("Failed to analyze method: " + e.getMessage(), e);
        }
    }
    
    /**
     * Analyzes an exception class and builds its hierarchy.
     * 
     * @param exceptionClassName Fully qualified name of the exception class
     * @return The root exception node with its hierarchy
     * @throws AnalysisException If analysis fails
     */
    public ExceptionNode analyzeExceptionHierarchy(String exceptionClassName) throws AnalysisException {
        try {
            // Check if already in cache
            if (exceptionNodeCache.containsKey(exceptionClassName)) {
                return exceptionNodeCache.get(exceptionClassName);
            }
            
            logger.info("Analyzing exception hierarchy for: {}", exceptionClassName);
            
            // Resolve the class
            TypeDescription typeDescription = resolveClass(exceptionClassName);
            
            // Create the root exception node
            ExceptionNode rootNode = new ExceptionNode(exceptionClassName);
            exceptionNodeCache.put(exceptionClassName, rootNode);
            
            // Check if this is actually an exception class
            boolean isException = false;
            TypeDescription currentType = typeDescription;
            
            while (currentType != null) {
                String currentName = currentType.getName();
                if (currentName.equals("java.lang.Throwable") || 
                    currentName.equals("java.lang.Exception") || 
                    currentName.equals("java.lang.RuntimeException") || 
                    currentName.equals("java.lang.Error")) {
                    isException = true;
                    break;
                }
                
                // Move up to parent type
                try {
                    TypeDescription.Generic superClass = currentType.getSuperClass();
                    if (superClass != null) {
                        currentType = superClass.asErasure();
                    } else {
                        currentType = null;
                    }
                } catch (Exception e) {
                    // If we can't resolve the superclass, just stop
                    currentType = null;
                }
            }
            
            if (!isException) {
                logger.warn("{} is not an exception class", exceptionClassName);
                return rootNode;
            }
            
            // Now build the hierarchy
            buildExceptionHierarchy(rootNode, typeDescription);
            
            // Find commonly used constructors and messages
            analyzeExceptionMessages(rootNode, typeDescription);
            
            return rootNode;
        } catch (Exception e) {
            logger.error("Failed to analyze exception hierarchy for {}", exceptionClassName, e);
            throw new AnalysisException("Failed to analyze exception hierarchy: " + e.getMessage(), e);
        }
    }
    
    /**
     * Builds the exception hierarchy for a given exception node.
     */
    private void buildExceptionHierarchy(ExceptionNode node, TypeDescription typeDescription) {
        try {
            // Get the superclass
            TypeDescription.Generic superClass = typeDescription.getSuperClass();
            if (superClass != null && !superClass.getTypeName().equals("java.lang.Object")) {
                String superClassName = superClass.asErasure().getName();
                
                // Create or get the parent node
                ExceptionNode parentNode;
                if (exceptionNodeCache.containsKey(superClassName)) {
                    parentNode = exceptionNodeCache.get(superClassName);
                } else {
                    parentNode = new ExceptionNode(superClassName);
                    exceptionNodeCache.put(superClassName, parentNode);
                    
                    // Recursively build the parent's hierarchy
                    buildExceptionHierarchy(parentNode, superClass.asErasure());
                }
                
                // Add the parent-child relationship
                node.addParent(parentNode);
            }
            
            // Find all direct sub-classes in the cache
            for (TypeDescription cachedType : typeCache.values()) {
                try {
                    TypeDescription.Generic cachedSuperClass = cachedType.getSuperClass();
                    if (cachedSuperClass != null && 
                        cachedSuperClass.asErasure().getName().equals(typeDescription.getName())) {
                        
                        String subClassName = cachedType.getName();
                        if (!exceptionNodeCache.containsKey(subClassName)) {
                            ExceptionNode childNode = new ExceptionNode(subClassName);
                            exceptionNodeCache.put(subClassName, childNode);
                            childNode.addParent(node);
                        }
                    }
                } catch (Exception e) {
                    // Ignore any errors in subclass resolution, just continue
                    logger.debug("Error checking subclass relationship for {}", cachedType.getName(), e);
                }
            }
        } catch (Exception e) {
            // Log error but continue - we want to get as much hierarchy info as possible
            logger.warn("Error building exception hierarchy for {}", node.getClassName(), e);
        }
    }
    
    /**
     * Analyzes exception class to find common error messages.
     */
    private void analyzeExceptionMessages(ExceptionNode node, TypeDescription typeDescription) {
        try {
            // Look at constructors to find common message patterns
            for (MethodDescription.InDefinedShape methodDesc : typeDescription.getDeclaredMethods()) {
                if (methodDesc.isConstructor()) {
                    String signature = methodDesc.toString();
                    
                    // Look for common patterns in constructor signatures
                    if (signature.contains("(String)") || 
                        signature.contains("(java.lang.String)")) {
                        node.addCommonMessage("[Custom message]");
                    }
                    else if (signature.contains("(String, Throwable)") || 
                             signature.contains("(java.lang.String, java.lang.Throwable)")) {
                        node.addCommonMessage("[Custom message with cause]");
                    }
                    else if (signature.contains("(Throwable)") || 
                             signature.contains("(java.lang.Throwable)")) {
                        node.addCommonMessage("[Exception cause only]");
                    }
                }
            }
        } catch (Exception e) {
            // Log error but continue
            logger.warn("Error analyzing exception messages for {}", node.getClassName(), e);
        }
    }
    
    /**
     * Recursively analyzes method calls and builds the call graph.
     */
    private void analyzeMethodCalls(CallGraph callGraph, MethodNode methodNode, int currentDepth, int maxDepth, 
                                   AtomicInteger nodeCount) {
        // Stop conditions
        if (currentDepth >= maxDepth || 
            processedMethods.contains(methodNode.getFullyQualifiedName()) || 
            nodeCount.get() >= maxNodes) {
            return;
        }
        
        // Mark this method as processed to avoid cycles
        processedMethods.add(methodNode.getFullyQualifiedName());
        
        try {
            // If this is an interface or abstract method, find implementations
            if (methodNode.isInterface() || methodNode.isAbstract()) {
                List<MethodNode> implementations = findImplementations(methodNode);
                
                for (MethodNode impl : implementations) {
                    callGraph.addMethodCall(methodNode, impl);
                    
                    // Add to our internal call graph maps
                    addToCallGraph(methodNode.getFullyQualifiedName(), impl.getFullyQualifiedName());
                    
                    if (nodeCount.incrementAndGet() < maxNodes) {
                        analyzeMethodCalls(callGraph, impl, currentDepth + 1, maxDepth, nodeCount);
                    } else {
                        break;
                    }
                }
            }
            
            // Find method calls within this method
            List<MethodNode> callees = findMethodCalls(methodNode);
            
            for (MethodNode callee : callees) {
                callGraph.addMethodCall(methodNode, callee);
                
                // Add to our internal call graph maps
                addToCallGraph(methodNode.getFullyQualifiedName(), callee.getFullyQualifiedName());
                
                if (nodeCount.incrementAndGet() < maxNodes) {
                    analyzeMethodCalls(callGraph, callee, currentDepth + 1, maxDepth, nodeCount);
                } else {
                    break;
                }
            }
        } catch (Exception e) {
            // Log and continue - don't let one method failure stop the entire analysis
            logger.warn("Error analyzing calls from method {}: {}", methodNode.getFullyQualifiedName(), e.getMessage());
        }
    }
    
    /**
     * Adds a method call relationship to the internal call graph.
     * This creates both caller->callee and callee->caller links for bidirectional traversal.
     * 
     * @param caller Fully qualified name of the calling method
     * @param callee Fully qualified name of the called method
     */
    private void addToCallGraph(String caller, String callee) {
        // Add to forward call graph (caller -> callees)
        callGraph.computeIfAbsent(caller, k -> new HashSet<>()).add(callee);
        
        // Add to reverse call graph (callee -> callers)
        reverseCallGraph.computeIfAbsent(callee, k -> new HashSet<>()).add(caller);
    }
    
    /**
     * Analyzes exception propagation through the codebase by tracing call chains.
     * This connects exception sources (throws) with handlers (catch blocks).
     * 
     * @param exceptionClass The exception class to analyze
     * @param maxDepth Maximum call depth to analyze
     * @return A list of propagation chains for the exception
     * @throws AnalysisException If analysis fails
     */
    public List<PropagationChain> analyzeExceptionPropagation(String exceptionClass, Integer maxDepth) throws AnalysisException {
        try {
            logger.info("Analyzing exception propagation for: {}", exceptionClass);
            
            // Find methods that throw this exception
            List<String> throwingMethods = findClassesThatThrow(exceptionClass);
            
            // Store the results
            List<PropagationChain> propagationChains = new ArrayList<>();
            
            // Use a reasonable default if no custom depth provided
            int depth = maxDepth != null ? maxDepth : this.maxDepth;
            
            // Track processed methods to avoid cycles
            Set<String> processedMethods = new HashSet<>();
            
            // For each method that throws this exception, analyze call paths
            for (String throwingMethod : throwingMethods) {
                if (propagationChains.size() >= 10) {
                    // Limit to 10 chains to avoid excessive processing
                    logger.info("Limiting analysis to first 10 propagation chains");
                    break;
                }
                
                // Create a propagation chain for this method
                PropagationChain chain = new PropagationChain(exceptionClass);
                
                try {
                    // Parse the method path
                    MethodPathInfo pathInfo = parseMethodPath(throwingMethod);
                    
                    // Mark that this method throws the exception
                    chain.addDetailedNode(
                        pathInfo.className, 
                        "THROWS", 
                        throwingMethod,
                        "Declares " + exceptionClass + " in throws clause"
                    );
                    
                    // Look for callers of this method - they would need to handle the exception
                    analyzeCallerChain(pathInfo.className, pathInfo.methodName, chain, processedMethods, 0, depth, exceptionClass);
                    
                    // Only add chains that have at least two nodes (source and at least one handler)
                    if (chain.getLength() > 1) {
                        propagationChains.add(chain);
                    }
                } catch (Exception e) {
                    logger.warn("Error analyzing propagation for method {}: {}", throwingMethod, e.getMessage());
                    // Continue with next method
                }
            }
            
            return propagationChains;
        } catch (Exception e) {
            logger.error("Failed to analyze exception propagation for {}", exceptionClass, e);
            throw new AnalysisException("Failed to analyze exception propagation: " + e.getMessage(), e);
        }
    }
    
    /**
     * Recursively analyzes the caller chain to find exception handlers.
     */
    private void analyzeCallerChain(String className, String methodName, PropagationChain chain, 
                                   Set<String> processedMethods, int currentDepth, int maxDepth, 
                                   String targetException) {
        // Stop if we've reached max depth or already processed this method
        String methodKey = className + "." + methodName;
        if (currentDepth >= maxDepth || processedMethods.contains(methodKey)) {
            return;
        }
        
        // Mark this method as processed to avoid cycles
        processedMethods.add(methodKey);
        
        try {
            // Find potential callers of this method
            TypeDescription typeDesc = resolveClass(className);
            
            // Check if this method handles the exception (catches it)
            boolean handlesDeclaredExceptions = false;
            for (MethodDescription.InDefinedShape methodDesc : typeDesc.getDeclaredMethods()) {
                if (methodDesc.getName().equals(methodName)) {
                    // Check if this method has a catch block
                    if (hasCatchBlock(className, methodName, targetException)) {
                        chain.addDetailedNode(
                            className,
                            "CATCHES",
                            className + "." + methodName,
                            "Has catch block for " + targetException
                        );
                        handlesDeclaredExceptions = true;
                    }
                    break;
                }
            }
            
            // If this method doesn't handle the exception, it must propagate it upward
            if (!handlesDeclaredExceptions) {
                // Find potential callers using our reverse call graph
                List<String> potentialCallers = findPotentialCallers(className, methodName);
                
                if (!potentialCallers.isEmpty()) {
                    // This method propagates the exception to its callers
                    chain.addDetailedNode(
                        className,
                        "PROPAGATES",
                        className + "." + methodName,
                        "Propagates " + targetException + " to callers"
                    );
                    
                    // Recursively analyze the callers
                    for (String caller : potentialCallers) {
                        try {
                            MethodPathInfo pathInfo = parseMethodPath(caller);
                            analyzeCallerChain(
                                pathInfo.className, 
                                pathInfo.methodName, 
                                chain, 
                                processedMethods, 
                                currentDepth + 1, 
                                maxDepth, 
                                targetException
                            );
                        } catch (Exception e) {
                            logger.debug("Error analyzing caller {}: {}", caller, e.getMessage());
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("Error in caller chain analysis for {}.{}: {}", className, methodName, e.getMessage());
        }
    }
    
    /**
     * Checks if a method has a catch block for the specified exception.
     * Uses bytecode analysis to find actual catch blocks in the method.
     * 
     * @param className The class containing the method
     * @param methodName The method name to check
     * @param exceptionClass The exception class to look for in catch blocks
     * @return true if the method has a catch block for this exception
     */
    private boolean hasCatchBlock(String className, String methodName, String exceptionClass) {
        try {
            // Convert className to ASM internal format
            String internalClassName = className.replace('.', '/');
            
            // Get the simple name of the exception
            String simpleExceptionName = getSimpleName(exceptionClass);
            
            // Load bytecode
            ClassReader reader = loadClassBytecode(internalClassName);
            if (reader == null) {
                logger.warn("Could not load bytecode for class: {}", className);
                return false;
            }
            
            // Use ASM to analyze exception handlers
            CatchBlockVisitor visitor = new CatchBlockVisitor(methodName, exceptionClass, simpleExceptionName);
            reader.accept(visitor, ClassReader.SKIP_DEBUG);
            
            return visitor.hasCatchBlock();
        } catch (Exception e) {
            logger.debug("Error checking for catch blocks in {}.{}: {}", className, methodName, e.getMessage());
            return false;
        }
    }
    
    /**
     * ASM visitor to analyze method catch blocks.
     */
    private static class CatchBlockVisitor extends ClassVisitor {
        private final String targetMethodName;
        private final String exceptionClass;
        private final String simpleExceptionName;
        private boolean foundCatchBlock = false;
        
        public CatchBlockVisitor(String targetMethodName, String exceptionClass, String simpleExceptionName) {
            super(Opcodes.ASM9);
            this.targetMethodName = targetMethodName;
            this.exceptionClass = exceptionClass;
            this.simpleExceptionName = simpleExceptionName;
        }
        
        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, 
                                       String signature, String[] exceptions) {
            // Skip if this is not our target method
            if (!name.equals(targetMethodName)) {
                return null;
            }
            
            return new MethodVisitor(Opcodes.ASM9) {
                @Override
                public void visitTryCatchBlock(org.objectweb.asm.Label start, org.objectweb.asm.Label end, 
                                             org.objectweb.asm.Label handler, String type) {
                    if (type != null) {
                        // Convert internal format (org/example/Exception) to FQN (org.example.Exception)
                        String catchType = type.replace('/', '.');
                        
                        // Check if this catch block handles our target exception
                        if (catchType.equals(exceptionClass) || 
                            catchType.endsWith(simpleExceptionName) ||
                            exceptionClass.endsWith(catchType)) {
                            foundCatchBlock = true;
                        }
                    }
                }
            };
        }
        
        public boolean hasCatchBlock() {
            return foundCatchBlock;
        }
    }
    
    /**
     * Finds classes that throw a specific exception.
     * 
     * @param exceptionClass The fully qualified exception class name
     * @return A list of classes that throw this exception
     * @throws AnalysisException If analysis fails
     */
    public List<String> findClassesThatThrow(String exceptionClass) throws AnalysisException {
        List<String> results = new ArrayList<>();
        String simpleExceptionName = getSimpleName(exceptionClass);
        
        // Check all cached classes
        for (TypeDescription type : typeCache.values()) {
            try {
                // Check method throws clauses
                for (MethodDescription.InDefinedShape method : type.getDeclaredMethods()) {
                    for (TypeDescription.Generic throwableType : method.getExceptionTypes()) {
                        String throwableName = throwableType.asErasure().getName();
                        if (throwableName.equals(exceptionClass) ||
                            getSimpleName(throwableName).equals(simpleExceptionName)) {
                            results.add(type.getName() + "." + method.getName());
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                // Skip any errors in analysis
                logger.debug("Error analyzing throws clauses in {}", type.getName(), e);
            }
        }
        
        return results;
    }
    
    /**
     * Finds potential callers of a method using the reverse call graph.
     * This method leverages the call graph built through bytecode analysis
     * to find all methods that call the specified method.
     * 
     * @param className The class containing the method
     * @param methodName The method name to find callers for
     * @return A list of method fully qualified names that call this method
     */
    private List<String> findPotentialCallers(String className, String methodName) {
        String methodKey = className + "." + methodName;
        
        // Get callers from the reverse call graph
        Set<String> callers = reverseCallGraph.getOrDefault(methodKey, new HashSet<>());
        
        // Also search for method signatures with parameters
        for (String key : reverseCallGraph.keySet()) {
            if (key.startsWith(methodKey + "(")) {
                Set<String> additionalCallers = reverseCallGraph.get(key);
                if (additionalCallers != null) {
                    callers.addAll(additionalCallers);
                }
            }
        }
        
        return new ArrayList<>(callers);
    }
    
    /**
     * Finds implementations of an interface method using bytecode analysis.
     * This method examines the type hierarchy to identify concrete implementations
     * of interface methods or overrides of abstract methods.
     * 
     * @param methodNode The interface or abstract method node to find implementations for
     * @return A list of concrete implementations of the method
     */
    private List<MethodNode> findImplementations(MethodNode methodNode) {
        List<MethodNode> implementations = new ArrayList<>();
        String interfaceName = methodNode.getClassName();
        String methodName = methodNode.getMethodName();
        String methodSignature = methodNode.getSignature();
        
        logger.debug("Finding implementations of {}.{}{}", interfaceName, methodName, methodSignature);
        
        // Scan all cached classes to find implementations
        for (TypeDescription typeDesc : typeCache.values()) {
            try {
                // Skip the interface/abstract class itself
                if (typeDesc.getName().equals(interfaceName)) {
                    continue;
                }
                
                // Skip other interfaces and abstract classes - we want concrete implementations
                if (typeDesc.isInterface() || typeDesc.isAbstract()) {
                    continue;
                }
                
                boolean isImplementation = false;
                
                // Case 1: Check if this class directly implements the interface
                for (TypeDescription.Generic interfaceType : typeDesc.getInterfaces()) {
                    if (interfaceType.asErasure().getName().equals(interfaceName)) {
                        isImplementation = true;
                        break;
                    }
                }
                
                // Case 2: Check if this class extends the abstract class
                if (!isImplementation && methodNode.isAbstract() && !methodNode.isInterface()) {
                    TypeDescription.Generic superClass = typeDesc.getSuperClass();
                    while (superClass != null && !superClass.asErasure().getName().equals("java.lang.Object")) {
                        if (superClass.asErasure().getName().equals(interfaceName)) {
                            isImplementation = true;
                            break;
                        }
                        
                        // Move up the inheritance chain
                        try {
                            TypeDescription superTypeDesc = resolveClass(superClass.asErasure().getName());
                            superClass = superTypeDesc.getSuperClass();
                        } catch (Exception e) {
                            superClass = null;
                        }
                    }
                }
                
                // Case 3: Check for interface inheritance (implementing classes might implement a subinterface)
                if (!isImplementation) {
                    for (TypeDescription.Generic interfaceType : typeDesc.getInterfaces()) {
                        try {
                            TypeDescription interfaceDesc = resolveClass(interfaceType.asErasure().getName());
                            // Look for interface inheritance
                            isImplementation = isInterfaceExtending(interfaceDesc, interfaceName);
                            if (isImplementation) {
                                break;
                            }
                        } catch (Exception e) {
                            // Skip this interface if we can't resolve it
                            logger.debug("Could not analyze interface hierarchy for: {}", 
                                interfaceType.asErasure().getName());
                        }
                    }
                }
                
                // If this class implements the interface/extends the abstract class, check if it has the method
                if (isImplementation) {
                    try {
                        // Only consider classes that actually implement the method
                        for (MethodDescription.InDefinedShape methodDesc : typeDesc.getDeclaredMethods()) {
                            if (methodDesc.getName().equals(methodName)) {
                                // Create a method node for this implementation
                                MethodNode implNode = createMethodNode(typeDesc, methodName);
                                if (!implNode.isInterface() && !implNode.isAbstract()) {
                                    implementations.add(implNode);
                                    logger.debug("Found implementation: {}.{}", typeDesc.getName(), methodName);
                                }
                                break;
                            }
                        }
                        
                        // Also check inherited methods from parent classes
                        if (implementations.isEmpty()) {
                            MethodNode implNode = findInheritedMethod(typeDesc, methodName);
                            if (implNode != null && !implNode.isInterface() && !implNode.isAbstract()) {
                                implementations.add(implNode);
                                logger.debug("Found inherited implementation: {}.{}", implNode.getClassName(), methodName);
                            }
                        }
                    } catch (Exception e) {
                        logger.debug("Error analyzing methods in class {}: {}", typeDesc.getName(), e.getMessage());
                    }
                }
            } catch (Exception e) {
                // Skip any errors in analysis
                logger.debug("Error analyzing implementation for {}: {}", typeDesc.getName(), e.getMessage());
            }
        }
        
        // For bytecode analysis, also check explicitly mapped implementations via ASM
        // This helps catch cases that might not be in the type cache yet
        List<String> additionalImpls = findImplementationsViaASM(interfaceName, methodName);
        for (String implClassName : additionalImpls) {
            try {
                if (!implementationExists(implementations, implClassName)) {
                    TypeDescription typeDesc = resolveClass(implClassName);
                    MethodNode implNode = createMethodNode(typeDesc, methodName);
                    if (!implNode.isInterface() && !implNode.isAbstract()) {
                        implementations.add(implNode);
                        logger.debug("Found implementation via ASM: {}.{}", implClassName, methodName);
                    }
                }
            } catch (Exception e) {
                logger.debug("Could not resolve implementation class from ASM: {}", implClassName);
            }
        }
        
        return implementations;
    }
    
    /**
     * Checks if an implementation with the given class name already exists in the list.
     */
    private boolean implementationExists(List<MethodNode> implementations, String className) {
        for (MethodNode node : implementations) {
            if (node.getClassName().equals(className)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Checks if an interface extends another interface.
     */
    private boolean isInterfaceExtending(TypeDescription interfaceDesc, String targetInterfaceName) {
        // Check direct inheritance
        for (TypeDescription.Generic parent : interfaceDesc.getInterfaces()) {
            if (parent.asErasure().getName().equals(targetInterfaceName)) {
                return true;
            }
            
            // Recursive check for interface inheritance
            try {
                TypeDescription parentDesc = resolveClass(parent.asErasure().getName());
                if (isInterfaceExtending(parentDesc, targetInterfaceName)) {
                    return true;
                }
            } catch (Exception e) {
                // Skip this interface
            }
        }
        
        return false;
    }
    
    /**
     * Finds an inherited method implementation from superclasses.
     */
    private MethodNode findInheritedMethod(TypeDescription typeDesc, String methodName) throws AnalysisException {
        TypeDescription.Generic superClass = typeDesc.getSuperClass();
        while (superClass != null && !superClass.asErasure().getName().equals("java.lang.Object")) {
            try {
                TypeDescription superTypeDesc = resolveClass(superClass.asErasure().getName());
                
                // Check if the superclass implements the method
                for (MethodDescription.InDefinedShape methodDesc : superTypeDesc.getDeclaredMethods()) {
                    if (methodDesc.getName().equals(methodName)) {
                        return createMethodNode(superTypeDesc, methodName);
                    }
                }
                
                // Move up the inheritance chain
                superClass = superTypeDesc.getSuperClass();
            } catch (Exception e) {
                superClass = null;
            }
        }
        
        return null;
    }
    
    /**
     * Finds implementations of an interface method using direct ASM bytecode analysis.
     * This catches implementations that might not be in the type cache yet.
     */
    private List<String> findImplementationsViaASM(String interfaceName, String methodName) {
        List<String> implementations = new ArrayList<>();
        
        // Convert interface name to internal format
        String internalInterfaceName = interfaceName.replace('.', '/');
        
        try {
            // Create a provider that finds classes in the specified package
            ClassPathScanningCandidateComponentProvider scanner = 
                new ClassPathScanningCandidateComponentProvider(false);
            scanner.addIncludeFilter(new RegexPatternTypeFilter(Pattern.compile(".*")));
            
            // Scan classpath for classes
            for (org.springframework.beans.factory.config.BeanDefinition bd : 
                     scanner.findCandidateComponents(basePackage)) {
                
                String className = bd.getBeanClassName();
                if (className == null) {
                    continue;
                }
                
                try {
                    // Convert to internal format
                    String internalClassName = className.replace('.', '/');
                    
                    // Load bytecode
                    ClassReader reader = loadClassBytecode(internalClassName);
                    if (reader == null) {
                        continue;
                    }
                    
                    // Check if this class implements the interface
                    ImplementationVisitor visitor = new ImplementationVisitor(internalInterfaceName, methodName);
                    reader.accept(visitor, ClassReader.SKIP_DEBUG);
                    
                    if (visitor.isImplementation()) {
                        implementations.add(className);
                    }
                } catch (Exception e) {
                    // Skip this class
                    logger.debug("Error in ASM analysis for {}: {}", className, e.getMessage());
                }
            }
        } catch (Exception e) {
            logger.warn("Error scanning for implementations: {}", e.getMessage());
        }
        
        return implementations;
    }
    
    /**
     * ASM visitor to check if a class implements a specific interface and method.
     */
    private class ImplementationVisitor extends ClassVisitor {
        private final String targetInterfaceName;
        private final String targetMethodName;
        private boolean isImplementation = false;
        private boolean hasMethod = false;
        
        public ImplementationVisitor(String targetInterfaceName, String targetMethodName) {
            super(Opcodes.ASM9);
            this.targetInterfaceName = targetInterfaceName;
            this.targetMethodName = targetMethodName;
        }
        
        @Override
        public void visit(int version, int access, String name, String signature, 
                        String superName, String[] interfaces) {
            // Skip interfaces and abstract classes
            if ((access & Opcodes.ACC_INTERFACE) != 0 || (access & Opcodes.ACC_ABSTRACT) != 0) {
                return;
            }
            
            // Check if this class implements the target interface
            if (interfaces != null) {
                for (String iface : interfaces) {
                    if (iface.equals(targetInterfaceName) || 
                        checkInterfaceHierarchy(iface, targetInterfaceName)) {
                        isImplementation = true;
                        break;
                    }
                }
            }
        }
        
        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, 
                                       String signature, String[] exceptions) {
            // Check if this class has the target method
            if (name.equals(targetMethodName)) {
                hasMethod = true;
            }
            return null;
        }
        
        /**
         * Checks the interface hierarchy to determine if the given interface extends the target interface.
         * Uses bytecode analysis to examine interface inheritance relationships.
         * 
         * @param interfaceName The interface name to check
         * @param targetInterface The target interface name we're looking for
         * @return true if interfaceName extends or equals targetInterface
         */
        private boolean checkInterfaceHierarchy(String interfaceName, String targetInterface) {
            if (interfaceName.equals(targetInterface)) {
                return true;
            }
            
            try {
                // Load the interface bytecode
                ClassReader reader = BytecodeAnalyzer.this.loadClassBytecode(interfaceName);
                if (reader == null) {
                    return false;
                }
                
                // Use a custom visitor to check the parent interfaces
                InterfaceHierarchyVisitor visitor = new InterfaceHierarchyVisitor(targetInterface);
                reader.accept(visitor, ClassReader.SKIP_DEBUG);
                
                return visitor.extendsTargetInterface();
            } catch (Exception e) {
                // If we encounter an error, conservatively return false
                return false;
            }
        }
        
        public boolean isImplementation() {
            return isImplementation && hasMethod;
        }
    }
    
    /**
     * ASM visitor to check if an interface extends another interface through its hierarchy.
     */
    private class InterfaceHierarchyVisitor extends ClassVisitor {
        private final String targetInterface;
        private boolean extendsTarget = false;
        
        public InterfaceHierarchyVisitor(String targetInterface) {
            super(Opcodes.ASM9);
            this.targetInterface = targetInterface;
        }
        
        @Override
        public void visit(int version, int access, String name, String signature, 
                         String superName, String[] interfaces) {
            // Check if this interface directly extends the target interface
            if (interfaces != null) {
                for (String iface : interfaces) {
                    if (iface.equals(targetInterface)) {
                        extendsTarget = true;
                        return;
                    }
                }
            }
        }
        
        public boolean extendsTargetInterface() {
            return extendsTarget;
        }
    }
    
    /**
     * Gets the simple name of a class from its fully qualified name.
     */
    public String getSimpleName(String fullyQualifiedName) {
        int lastDot = fullyQualifiedName.lastIndexOf('.');
        if (lastDot > 0) {
            return fullyQualifiedName.substring(lastDot + 1);
        }
        return fullyQualifiedName;
    }
    
    /**
     * Finds all methods called from within a method.
     */
    private List<MethodNode> findMethodCalls(MethodNode methodNode) {
        List<MethodNode> callees = new ArrayList<>();
        String className = methodNode.getClassName();
        String methodName = methodNode.getMethodName();
        
        try {
            // Convert className from com.example.Class to com/example/Class for ASM
            String internalClassName = className.replace('.', '/');
            
            // Get class bytecode using ASM
            ClassReader reader = loadClassBytecode(internalClassName);
            if (reader == null) {
                logger.warn("Could not load bytecode for class: {}", className);
                return callees;
            }
            
            // Use ASM visitor to analyze method calls
            MethodCallVisitor visitor = new MethodCallVisitor(methodName, methodNode.getSignature());
            reader.accept(visitor, ClassReader.SKIP_DEBUG);
            
            // Get the call references from the visitor
            Map<String, String> callReferences = visitor.getCallReferences();
            
            // Convert call references to MethodNode objects
            for (Map.Entry<String, String> entry : callReferences.entrySet()) {
                try {
                    String calleeClassName = entry.getKey().replace('/', '.');
                    String calleeMethodName = entry.getValue();
                    
                    // Skip calls to JDK classes if includeLibraries is false
                    if (!includeLibraries && isJdkClass(calleeClassName)) {
                        continue;
                    }
                    
                    // Attempt to resolve the class
                    TypeDescription calleeTypeDesc = resolveClass(calleeClassName);
                    MethodNode calleeNode = createMethodNode(calleeTypeDesc, calleeMethodName);
                    callees.add(calleeNode);
                    
                    // Add to our call graph immediately
                    addToCallGraph(methodNode.getFullyQualifiedName(), calleeNode.getFullyQualifiedName());
                } catch (Exception e) {
                    // Log and continue - don't let one resolution failure stop all analysis
                    logger.debug("Could not resolve call reference: {}.{}", entry.getKey(), entry.getValue());
                }
            }
        } catch (Exception e) {
            logger.warn("Error analyzing method calls in {}.{}: {}", className, methodName, e.getMessage());
        }
        
        return callees;
    }
    
    /**
     * Loads bytecode for a class using ASM.
     * @param internalClassName Class name in internal format (e.g., com/example/Class)
     * @return ClassReader for the bytecode, or null if not found
     */
    private ClassReader loadClassBytecode(String internalClassName) {
        ClassLoader classLoader = getClass().getClassLoader();
        try (InputStream inputStream = classLoader.getResourceAsStream(internalClassName + ".class")) {
            if (inputStream != null) {
                return new ClassReader(inputStream);
            }
        } catch (IOException e) {
            logger.debug("Error loading bytecode for class {}: {}", internalClassName, e.getMessage());
        }
        return null;
    }
    
    /**
     * ASM ClassVisitor that finds method calls within a method.
     */
    private class MethodCallVisitor extends ClassVisitor {
        private final String targetMethodName;
        private final String targetParameterSignature;
        private final Map<String, Set<MethodCallInfo>> callReferences = new HashMap<>();
        
        public MethodCallVisitor(String targetMethodName, String targetParameterSignature) {
            super(Opcodes.ASM9);
            this.targetMethodName = targetMethodName;
            this.targetParameterSignature = targetParameterSignature;
        }
        
        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, 
                                       String signature, String[] exceptions) {
            // Skip if this is not our target method
            if (!name.equals(targetMethodName)) {
                return null;
            }
            
            // If parameter signature specified, verify it matches
            if (targetParameterSignature != null && !targetParameterSignature.isEmpty() &&
                !targetParameterSignature.equals("()") && !descriptor.startsWith(targetParameterSignature)) {
                return null;
            }
            
            // This is our target method, analyze its calls
            return new MethodVisitor(Opcodes.ASM9) {
                private int lineNumber = -1;
                
                @Override
                public void visitLineNumber(int line, org.objectweb.asm.Label start) {
                    this.lineNumber = line;
                }
                
                @Override
                public void visitMethodInsn(int opcode, String owner, String name, 
                                          String descriptor, boolean isInterface) {
                    // Add the method call to our references with complete information
                    MethodCallInfo callInfo = new MethodCallInfo(name, descriptor, lineNumber, opcode);
                    
                    // Store in the map by owner class name
                    callReferences.computeIfAbsent(owner, k -> new HashSet<>()).add(callInfo);
                }
            };
        }
        
        public Map<String, String> getCallReferences() {
            // Convert to simplified map for backward compatibility
            Map<String, String> result = new HashMap<>();
            for (Map.Entry<String, Set<MethodCallInfo>> entry : callReferences.entrySet()) {
                for (MethodCallInfo info : entry.getValue()) {
                    result.put(entry.getKey(), info.methodName);
                }
            }
            return result;
        }
        
        /**
         * Gets the full method call references with complete context information.
         * @return Map from class name to set of method call information
         */
        public Map<String, Set<MethodCallInfo>> getDetailedCallReferences() {
            return callReferences;
        }
    }
    
    /**
     * Represents detailed information about a method call.
     */
    private static class MethodCallInfo {
        private final String methodName;
        private final String methodDescriptor;
        private final int lineNumber;
        private final int opcode;
        
        public MethodCallInfo(String methodName, String methodDescriptor, int lineNumber, int opcode) {
            this.methodName = methodName;
            this.methodDescriptor = methodDescriptor;
            this.lineNumber = lineNumber;
            this.opcode = opcode;
        }
        
        public String getMethodName() {
            return methodName;
        }
        
        public String getMethodDescriptor() {
            return methodDescriptor;
        }
        
        public int getLineNumber() {
            return lineNumber;
        }
        
        public int getOpcode() {
            return opcode;
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            MethodCallInfo that = (MethodCallInfo) o;
            return methodName.equals(that.methodName) && 
                   methodDescriptor.equals(that.methodDescriptor);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(methodName, methodDescriptor);
        }
    }
    
    /**
     * Checks if a class is from the JDK.
     */
    private boolean isJdkClass(String className) {
        return className.startsWith("java.") || 
               className.startsWith("javax.") || 
               className.startsWith("sun.") || 
               className.startsWith("com.sun.") ||
               className.startsWith("jdk.");
    }
    
    private MethodNode createMethodNode(TypeDescription typeDescription, String methodName) throws AnalysisException {
        for (MethodDescription.InDefinedShape methodDesc : typeDescription.getDeclaredMethods()) {
            if (methodDesc.getName().equals(methodName)) {
                boolean isInterface = typeDescription.isInterface();
                boolean isAbstract = methodDesc.isAbstract();
                String signature = methodDesc.toString();
                
                // Extract just the parameter part of the signature
                String paramSignature = "(";
                if (signature.contains("(") && signature.contains(")")) {
                    paramSignature = signature.substring(signature.indexOf('('), signature.indexOf(')') + 1);
                }
                
                return new MethodNode(
                    typeDescription.getName(),
                    methodName,
                    paramSignature,
                    isInterface,
                    isAbstract
                );
            }
        }
        
        throw new AnalysisException("Method not found: " + methodName + " in class " + typeDescription.getName());
    }
    
    /**
     * Resolves a class name to a TypeDescription.
     */
    private TypeDescription resolveClass(String className) throws AnalysisException {
        if (typeCache.containsKey(className)) {
            return typeCache.get(className);
        }
        
        try {
            TypeDescription typeDesc = typePool.describe(className).resolve();
            typeCache.put(className, typeDesc);
            return typeDesc;
        } catch (Exception e) {
            throw new AnalysisException("Could not resolve class: " + className, e);
        }
    }
    
    /**
     * Parses a method path into class name and method name.
     */
    private MethodPathInfo parseMethodPath(String methodPath) throws AnalysisException {
        if (methodPath == null || methodPath.isEmpty()) {
            throw new AnalysisException("Method path cannot be empty");
        }
        
        int lastDotIndex = methodPath.lastIndexOf('.');
        if (lastDotIndex <= 0 || lastDotIndex == methodPath.length() - 1) {
            throw new AnalysisException("Invalid method path format: " + methodPath);
        }
        
        String className = methodPath.substring(0, lastDotIndex);
        String methodName = methodPath.substring(lastDotIndex + 1);
        
        // Handle method overloads specified with parameters (e.g., method(String,int))
        if (methodName.contains("(")) {
            methodName = methodName.substring(0, methodName.indexOf('('));
        }
        
        return new MethodPathInfo(className, methodName);
    }
    
    /**
     * Helper class to store parsed method path information.
     */
    private static class MethodPathInfo {
        final String className;
        final String methodName;
        
        MethodPathInfo(String className, String methodName) {
            this.className = className;
            this.methodName = methodName;
        }
    }
    
    /**
     * Exception thrown when analysis fails.
     */
    public static class AnalysisException extends Exception {
        public AnalysisException(String message) {
            super(message);
        }
        
        public AnalysisException(String message, Throwable cause) {
            super(message, cause);
        }
    }
} 