package com.l3agent.mcp.tools.crossrepo.model;

import java.util.*;

/**
 * Contains the results of a cross-repository code search.
 * Includes the original search criteria and the code references found across repositories.
 */
public class CrossRepoResult {
    
    private String searchTerm;
    private boolean useRegex;
    private boolean caseSensitive;
    private Set<String> includedExtensions;
    private List<String> searchedRepositories;
    private List<CodeReference> references;
    private long searchTimeMs;
    
    /**
     * Creates a new cross-repository search result.
     */
    public CrossRepoResult() {
        this.searchedRepositories = new ArrayList<>();
        this.references = new ArrayList<>();
        this.includedExtensions = new HashSet<>();
    }
    
    /**
     * Creates a new cross-repository search result with the specified search term.
     * 
     * @param searchTerm The search term used for the search
     */
    public CrossRepoResult(String searchTerm) {
        this();
        this.searchTerm = searchTerm;
    }
    
    /**
     * Gets the search term used for the search.
     * 
     * @return The search term
     */
    public String getSearchTerm() {
        return searchTerm;
    }
    
    /**
     * Sets the search term used for the search.
     * 
     * @param searchTerm The search term
     */
    public void setSearchTerm(String searchTerm) {
        this.searchTerm = searchTerm;
    }
    
    /**
     * Checks if regex was used for the search.
     * 
     * @return true if regex was used
     */
    public boolean isUseRegex() {
        return useRegex;
    }
    
    /**
     * Sets whether regex was used for the search.
     * 
     * @param useRegex true if regex was used
     */
    public void setUseRegex(boolean useRegex) {
        this.useRegex = useRegex;
    }
    
    /**
     * Checks if the search was case-sensitive.
     * 
     * @return true if the search was case-sensitive
     */
    public boolean isCaseSensitive() {
        return caseSensitive;
    }
    
    /**
     * Sets whether the search was case-sensitive.
     * 
     * @param caseSensitive true if the search was case-sensitive
     */
    public void setCaseSensitive(boolean caseSensitive) {
        this.caseSensitive = caseSensitive;
    }
    
    /**
     * Gets the file extensions that were included in the search.
     * 
     * @return The included file extensions
     */
    public Set<String> getIncludedExtensions() {
        return includedExtensions;
    }
    
    /**
     * Sets the file extensions that were included in the search.
     * 
     * @param includedExtensions The included file extensions
     */
    public void setIncludedExtensions(Set<String> includedExtensions) {
        this.includedExtensions = includedExtensions != null ? includedExtensions : new HashSet<>();
    }
    
    /**
     * Adds a file extension to the included extensions.
     * 
     * @param extension The file extension to include
     */
    public void addIncludedExtension(String extension) {
        if (extension != null && !extension.isEmpty()) {
            if (extension.charAt(0) != '.') {
                extension = "." + extension;
            }
            this.includedExtensions.add(extension.toLowerCase());
        }
    }
    
    /**
     * Gets the list of repositories that were searched.
     * 
     * @return The searched repositories
     */
    public List<String> getSearchedRepositories() {
        return searchedRepositories;
    }
    
    /**
     * Sets the list of repositories that were searched.
     * 
     * @param searchedRepositories The searched repositories
     */
    public void setSearchedRepositories(List<String> searchedRepositories) {
        this.searchedRepositories = searchedRepositories != null ? searchedRepositories : new ArrayList<>();
    }
    
    /**
     * Adds a repository to the list of searched repositories.
     * 
     * @param repository The repository that was searched
     */
    public void addSearchedRepository(String repository) {
        if (repository != null && !repository.isEmpty()) {
            this.searchedRepositories.add(repository);
        }
    }
    
    /**
     * Gets all code references found by the search.
     * 
     * @return The code references
     */
    public List<CodeReference> getReferences() {
        return references;
    }
    
    /**
     * Sets the code references found by the search.
     * 
     * @param references The code references
     */
    public void setReferences(List<CodeReference> references) {
        this.references = references != null ? references : new ArrayList<>();
    }
    
    /**
     * Adds a code reference to the results.
     * 
     * @param reference The code reference to add
     */
    public void addReference(CodeReference reference) {
        if (reference != null) {
            this.references.add(reference);
        }
    }
    
    /**
     * Gets the search time in milliseconds.
     * 
     * @return The search time
     */
    public long getSearchTimeMs() {
        return searchTimeMs;
    }
    
    /**
     * Sets the search time in milliseconds.
     * 
     * @param searchTimeMs The search time
     */
    public void setSearchTimeMs(long searchTimeMs) {
        this.searchTimeMs = searchTimeMs;
    }
    
    /**
     * Gets the total number of references found.
     * 
     * @return The reference count
     */
    public int getReferenceCount() {
        return references.size();
    }
    
    /**
     * Gets the number of repositories that had matches.
     * 
     * @return The number of repositories with matches
     */
    public int getMatchedRepositoryCount() {
        return (int) references.stream()
                .map(CodeReference::getRepository)
                .distinct()
                .count();
    }
    
    @Override
    public String toString() {
        return String.format("CrossRepoResult{term='%s', repositories=%d, matches=%d}", 
                searchTerm, searchedRepositories.size(), references.size());
    }
} 