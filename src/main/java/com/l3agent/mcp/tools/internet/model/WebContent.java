package com.l3agent.mcp.tools.internet.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Represents content retrieved from the web.
 */
public class WebContent {
    
    private String url;
    private String title;
    private String content;
    private String source;
    private Instant timestamp;
    private boolean fromCache;
    private boolean filtered;
    private String filterCriteria;
    
    /**
     * Creates a new WebContent.
     */
    public WebContent() {
        this.timestamp = Instant.now();
        this.fromCache = false;
        this.filtered = false;
    }
    
    /**
     * Creates a new WebContent with the specified values.
     * 
     * @param url The URL the content was retrieved from
     * @param title The title of the content
     * @param content The content text
     * @param source The source of the content (e.g., "AWS Documentation")
     */
    public WebContent(String url, String title, String content, String source) {
        this.url = url;
        this.title = title;
        this.content = content;
        this.source = source;
        this.timestamp = Instant.now();
        this.fromCache = false;
        this.filtered = false;
    }
    
    /**
     * Gets the URL the content was retrieved from.
     * 
     * @return The URL
     */
    public String getUrl() {
        return url;
    }
    
    /**
     * Sets the URL the content was retrieved from.
     * 
     * @param url The URL
     */
    public void setUrl(String url) {
        this.url = url;
    }
    
    /**
     * Gets the title of the content.
     * 
     * @return The title
     */
    public String getTitle() {
        return title;
    }
    
    /**
     * Sets the title of the content.
     * 
     * @param title The title
     */
    public void setTitle(String title) {
        this.title = title;
    }
    
    /**
     * Gets the content text.
     * 
     * @return The content
     */
    public String getContent() {
        return content;
    }
    
    /**
     * Sets the content text.
     * 
     * @param content The content
     */
    public void setContent(String content) {
        this.content = content;
    }
    
    /**
     * Gets the source of the content.
     * 
     * @return The source
     */
    public String getSource() {
        return source;
    }
    
    /**
     * Sets the source of the content.
     * 
     * @param source The source
     */
    public void setSource(String source) {
        this.source = source;
    }
    
    /**
     * Gets the timestamp when the content was retrieved.
     * 
     * @return The timestamp
     */
    public Instant getTimestamp() {
        return timestamp;
    }
    
    /**
     * Sets the timestamp when the content was retrieved.
     * 
     * @param timestamp The timestamp
     */
    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
    
    /**
     * Checks if the content was retrieved from cache.
     * 
     * @return true if from cache, false otherwise
     */
    public boolean isFromCache() {
        return fromCache;
    }
    
    /**
     * Sets whether the content was retrieved from cache.
     * 
     * @param fromCache true if from cache, false otherwise
     */
    public void setFromCache(boolean fromCache) {
        this.fromCache = fromCache;
    }
    
    /**
     * Checks if the content has been filtered.
     * 
     * @return true if filtered, false otherwise
     */
    public boolean isFiltered() {
        return filtered;
    }
    
    /**
     * Sets whether the content has been filtered.
     * 
     * @param filtered true if filtered, false otherwise
     */
    public void setFiltered(boolean filtered) {
        this.filtered = filtered;
    }
    
    /**
     * Gets the filter criteria used to filter the content.
     * 
     * @return The filter criteria
     */
    public String getFilterCriteria() {
        return filterCriteria;
    }
    
    /**
     * Sets the filter criteria used to filter the content.
     * 
     * @param filterCriteria The filter criteria
     */
    public void setFilterCriteria(String filterCriteria) {
        this.filterCriteria = filterCriteria;
        if (filterCriteria != null && !filterCriteria.isEmpty()) {
            this.filtered = true;
        }
    }
    
    /**
     * Creates a filtered copy of this WebContent.
     * 
     * @param filteredContent The filtered content
     * @param filterCriteria The filter criteria used
     * @return A new WebContent with the filtered content
     */
    public WebContent createFilteredCopy(String filteredContent, String filterCriteria) {
        WebContent filtered = new WebContent();
        filtered.setUrl(this.url);
        filtered.setTitle(this.title);
        filtered.setContent(filteredContent);
        filtered.setSource(this.source);
        filtered.setTimestamp(this.timestamp);
        filtered.setFromCache(this.fromCache);
        filtered.setFiltered(true);
        filtered.setFilterCriteria(filterCriteria);
        return filtered;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WebContent that = (WebContent) o;
        return fromCache == that.fromCache && 
               filtered == that.filtered && 
               Objects.equals(url, that.url) && 
               Objects.equals(title, that.title) && 
               Objects.equals(content, that.content) && 
               Objects.equals(source, that.source) && 
               Objects.equals(timestamp, that.timestamp) && 
               Objects.equals(filterCriteria, that.filterCriteria);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(url, title, content, source, timestamp, fromCache, filtered, filterCriteria);
    }
    
    @Override
    public String toString() {
        return "WebContent{" +
                "url='" + url + '\'' +
                ", title='" + title + '\'' +
                ", contentLength=" + (content != null ? content.length() : 0) +
                ", source='" + source + '\'' +
                ", timestamp=" + timestamp +
                ", fromCache=" + fromCache +
                ", filtered=" + filtered +
                ", filterCriteria='" + filterCriteria + '\'' +
                '}';
    }
} 