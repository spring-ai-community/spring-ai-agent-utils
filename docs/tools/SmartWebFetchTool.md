# SmartWebFetchTool

AI-powered web content fetching and summarization tool with intelligent caching and safety features. Fetches web pages, converts HTML to Markdown, and uses AI to extract relevant information based on a user prompt.

**Features:**
- HTML to Markdown conversion for clean content processing
- 15-minute TTL cache for faster repeated access to the same URLs
- Automatic retry with exponential backoff on network failures and 5xx errors
- Optional domain safety checking via Claude's domain info API
- Configurable content length limits with automatic truncation
- Fail-open/fail-closed security modes for safety check errors
- Proper charset detection and handling
- Thread-safe concurrent cache access

## Overview

The `SmartWebFetchTool` retrieves content from URLs and processes it using an AI model for intelligent summarization. Unlike simple HTTP clients, it:
1. Fetches HTML content using HTTP GET
2. Converts HTML to clean Markdown format
3. Uses AI to extract information relevant to your specific prompt
4. Caches results for 15 minutes to avoid redundant requests
5. Automatically retries on transient failures

This tool implements `AutoCloseable` for proper resource cleanup.

## Basic Usage

```java
// Build with required ChatClient
SmartWebFetchTool webFetch = SmartWebFetchTool.builder(chatClient).build();

// Fetch and summarize web content
String result = webFetch.webFetch(
    "https://docs.spring.io/spring-ai/reference/",
    "What are the key features of Spring AI?"
);

System.out.println(result);
// Output: "Spring AI provides integration with various AI models including..."
```

## Builder Configuration

### Required Parameters

**`chatClient`** - The ChatClient instance used for AI-powered summarization

```java
SmartWebFetchTool.builder(chatClient)
    .build();
```

### Optional Parameters

| Option | Default | Description |
|--------|---------|-------------|
| `maxContentLength` | 100,000 | Maximum characters to process; content is truncated with warning |
| `domainSafetyCheck` | true | Enable domain safety verification before fetching |
| `failOpenOnSafetyCheckError` | true | Allow fetch if safety check fails (true) or block (false) |
| `maxCacheSize` | 100 | Maximum number of URL+prompt combinations to cache |
| `maxRetries` | 2 | Maximum retry attempts for transient network failures |

**Example with all options:**

```java
SmartWebFetchTool webFetch = SmartWebFetchTool.builder(chatClient)
    .maxContentLength(150_000)           // Process up to 150KB
    .domainSafetyCheck(true)             // Check domain safety
    .failOpenOnSafetyCheckError(true)    // Allow fetch if safety check errors
    .maxCacheSize(200)                   // Cache up to 200 entries
    .maxRetries(3)                       // Retry up to 3 times
    .build();
```

## Configuration Details

### Max Content Length

Controls the maximum number of characters processed from the fetched content. Content exceeding this limit is truncated with a warning logged.

```java
SmartWebFetchTool.builder(chatClient)
    .maxContentLength(50_000)  // For small articles
    .build();

SmartWebFetchTool.builder(chatClient)
    .maxContentLength(200_000)  // For long documentation
    .build();
```

**Use Cases:**
- Smaller limits (50K-100K): Blog posts, news articles
- Medium limits (100K-150K): Technical documentation
- Larger limits (150K-200K): Comprehensive guides, API references

### Domain Safety Check

Verifies domain safety using Claude's domain info API before fetching content.

```java
// Enable safety checks (default)
SmartWebFetchTool.builder(chatClient)
    .domainSafetyCheck(true)
    .build();

// Disable for trusted internal URLs
SmartWebFetchTool.builder(chatClient)
    .domainSafetyCheck(false)
    .build();
```

**When to disable:**
- Internal company documentation
- Localhost development servers
- Known trusted domains in controlled environments

### Fail-Open vs Fail-Closed

Controls behavior when domain safety check encounters an error (not a failed check, but an error performing the check).

```java
// Fail-open: Allow fetch if safety check errors (default, more permissive)
SmartWebFetchTool.builder(chatClient)
    .failOpenOnSafetyCheckError(true)
    .build();

// Fail-closed: Block fetch if safety check errors (more secure)
SmartWebFetchTool.builder(chatClient)
    .failOpenOnSafetyCheckError(false)
    .build();
```

**Security Trade-offs:**
- **Fail-open (true)**: Better availability, accepts risk if safety service is down
- **Fail-closed (false)**: Better security, blocks all fetches if safety service fails

### Max Retries

Configures automatic retry attempts for network failures and 5xx server errors with exponential backoff.

```java
SmartWebFetchTool.builder(chatClient)
    .maxRetries(0)  // No retries, fail immediately
    .build();

SmartWebFetchTool.builder(chatClient)
    .maxRetries(2)  // Default: retry twice (3 total attempts)
    .build();

SmartWebFetchTool.builder(chatClient)
    .maxRetries(5)  // Aggressive retries for unreliable networks
    .build();
```

**Backoff Strategy:**
- Attempt 1: Immediate
- Attempt 2: Wait 1 second
- Attempt 3: Wait 2 seconds
- Attempt 4: Wait 4 seconds
- Attempt N: Wait 2^(N-1) seconds

## Caching Behavior

The tool implements a sophisticated caching system to improve performance and reduce redundant network requests.

### Cache Key Structure

Cache keys include **both** the URL and the prompt:
```
url::prompt::promptHashCode
```

**Example:**
```java
// These create DIFFERENT cache entries
webFetch.webFetch("https://example.com", "What is the main topic?");
webFetch.webFetch("https://example.com", "List all features");

// This reuses the FIRST cache entry (same URL + prompt)
webFetch.webFetch("https://example.com", "What is the main topic?");
```

### Time-To-Live (TTL)

- **TTL**: 15 minutes per cache entry
- **Cleanup**: Automatic when cache size exceeds `maxCacheSize`
- **Thread Safety**: Concurrent access is safe

### Cache Management

```java
// Configure cache size
SmartWebFetchTool webFetch = SmartWebFetchTool.builder(chatClient)
    .maxCacheSize(500)  // Cache up to 500 URL+prompt combinations
    .build();

// Cache is automatically cleared on close
try (SmartWebFetchTool tool = SmartWebFetchTool.builder(chatClient).build()) {
    // Use tool
}
// Cache cleared here
```

## Error Handling

The tool provides comprehensive error handling with descriptive messages.

### Common Error Scenarios

**Invalid URL:**
```java
webFetch.webFetch("not-a-url", "Summarize");
// Returns: "Error: Invalid URL format. Please provide a fully-formed URL (e.g., https://example.com)"
```

**Empty URL:**
```java
webFetch.webFetch("", "Summarize");
// Returns: "Error: URL cannot be empty or null"
```

**Network Error:**
```java
webFetch.webFetch("https://nonexistent-domain-xyz123.com", "Summarize");
// Returns: "Error fetching URL: Network error while fetching URL: ..."
```

**HTTP Error:**
```java
webFetch.webFetch("https://example.com/404-page", "Summarize");
// Returns: "Error: Failed to fetch URL. HTTP status code: 404"
```

**Domain Safety Failure:**
```java
SmartWebFetchTool webFetch = SmartWebFetchTool.builder(chatClient)
    .domainSafetyCheck(true)
    .build();

webFetch.webFetch("https://unsafe-domain.com", "Summarize");
// Returns: "Domain safety check failed for URL 'https://unsafe-domain.com': The domain is not safe to fetch content from."
```

### Retry Behavior

The tool automatically retries on:
- Network errors (IOException)
- Server errors (5xx status codes)

**It does NOT retry on:**
- 4xx client errors (bad request, not found, unauthorized, etc.)
- Invalid URL format
- Failed domain safety checks
- Interrupted requests

## Resource Management

The tool implements `AutoCloseable` for proper cleanup.

### Try-with-Resources (Recommended)

```java
try (SmartWebFetchTool webFetch = SmartWebFetchTool.builder(chatClient).build()) {
    String result = webFetch.webFetch(url, prompt);
    System.out.println(result);
}
// Cache automatically cleared, resources released
```

### Manual Cleanup

```java
SmartWebFetchTool webFetch = SmartWebFetchTool.builder(chatClient).build();
try {
    String result = webFetch.webFetch(url, prompt);
} finally {
    webFetch.close();  // Clear cache
}
```

## Integration Examples

### Spring Boot Configuration

```java
@Configuration
public class ToolsConfig {

    @Bean
    public SmartWebFetchTool smartWebFetchTool(ChatClient.Builder chatClientBuilder) {
        ChatClient chatClient = chatClientBuilder.build();

        return SmartWebFetchTool.builder(chatClient)
            .maxContentLength(150_000)
            .domainSafetyCheck(true)
            .failOpenOnSafetyCheckError(true)
            .maxCacheSize(100)
            .maxRetries(2)
            .build();
    }
}
```

### ChatClient Integration

```java
ChatClient chatClient = chatClientBuilder
    .defaultTools(SmartWebFetchTool.builder(chatClient)
        .domainSafetyCheck(false)  // Disable for internal docs
        .maxRetries(3)             // More retries for reliability
        .build())
    .build();

// AI can now use web fetch automatically
String response = chatClient.prompt()
    .user("Search for Spring AI documentation and tell me about vector stores")
    .call()
    .content();
```

### Custom Prompts

```java
SmartWebFetchTool webFetch = SmartWebFetchTool.builder(chatClient).build();

// Extract specific information
String features = webFetch.webFetch(
    "https://spring.io/projects/spring-ai",
    "List all supported AI model providers"
);

// Compare content
String comparison = webFetch.webFetch(
    "https://example.com/product-a",
    "What are the pricing tiers and features for each tier?"
);

// Technical analysis
String analysis = webFetch.webFetch(
    "https://github.com/spring-projects/spring-ai",
    "What programming languages and frameworks are used in this project?"
);
```

## Advanced Use Cases

### Multiple Sources

```java
SmartWebFetchTool webFetch = SmartWebFetchTool.builder(chatClient)
    .maxCacheSize(500)  // Large cache for multiple URLs
    .build();

String[] urls = {
    "https://docs.spring.io/spring-ai/reference/",
    "https://docs.spring.io/spring-boot/reference/",
    "https://docs.spring.io/spring-framework/reference/"
};

for (String url : urls) {
    String summary = webFetch.webFetch(url, "What are the main features?");
    System.out.println("Summary for " + url + ":\n" + summary + "\n");
}
```

### Different Prompts Same URL

```java
String url = "https://example.com/api-docs";

// Cache miss - fetches and caches
String overview = webFetch.webFetch(url, "Provide an overview");

// Cache miss - different prompt, fetches again
String endpoints = webFetch.webFetch(url, "List all API endpoints");

// Cache hit - same URL and prompt
String overview2 = webFetch.webFetch(url, "Provide an overview");
```

### Internal Documentation

```java
// Optimized for internal trusted sources
SmartWebFetchTool internalWebFetch = SmartWebFetchTool.builder(chatClient)
    .domainSafetyCheck(false)        // Skip safety for internal URLs
    .maxRetries(1)                   // Fewer retries for fast network
    .maxContentLength(200_000)       // Large docs expected
    .build();

String docs = internalWebFetch.webFetch(
    "http://internal-docs.company.local/api-spec",
    "Summarize the authentication requirements"
);
```

## Security Considerations

### Domain Safety API

The tool uses Claude's domain info API (`https://claude.ai/api/web/domain_info`) to verify domain safety before fetching.

**Safety Check Process:**
1. Extract domain from URL
2. Query Claude's API with domain
3. Receive `can_fetch` boolean response
4. Block or allow based on response and configuration

**Disable if:**
- Fetching from trusted internal domains
- Behind corporate firewall with controlled access
- Using allowlist of known-safe domains

### Read-Only Operations

The tool only performs HTTP GET requests and does not:
- Modify any local files
- Send data to fetched URLs (except HTTP headers)
- Execute JavaScript or active content
- Store credentials or sensitive data

### User-Agent and Headers

Standard browser headers are sent for compatibility:
- User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64)...
- Accept: text/html,application/xhtml+xml,application/xml
- Accept-Language: en-US,en;q=0.5

## Performance Tips

### Optimize Cache Size

```java
// Small application, limited URLs
SmartWebFetchTool.builder(chatClient).maxCacheSize(50).build();

// Large application, many URLs/prompts
SmartWebFetchTool.builder(chatClient).maxCacheSize(500).build();
```

### Content Length Limits

```java
// Fast responses for short content
SmartWebFetchTool.builder(chatClient).maxContentLength(50_000).build();

// Comprehensive extraction for long content
SmartWebFetchTool.builder(chatClient).maxContentLength(200_000).build();
```

### Retry Strategy

```java
// Fast-fail for time-sensitive operations
SmartWebFetchTool.builder(chatClient).maxRetries(0).build();

// Resilient for unreliable networks
SmartWebFetchTool.builder(chatClient).maxRetries(5).build();
```

## Limitations

- **Read-only**: Only HTTP GET requests supported
- **No authentication**: Basic auth, OAuth, or API keys not supported in headers
- **No cookies**: Stateless requests, no session management
- **No JavaScript**: Static HTML only, no dynamic content rendering
- **No redirects to different hosts**: Automatically follows same-host redirects only
- **Text content**: Optimized for HTML/text, binary content not supported
- **English-focused**: AI summarization works best with English content

## Troubleshooting

### "Domain safety check failed"
- Disable safety checks if fetching internal/trusted URLs
- Set `failOpenOnSafetyCheckError(true)` to allow fetch on check errors

### "Content too long, truncating"
- Increase `maxContentLength` if you need more content
- Or refine your prompt to extract specific information

### "Failed after N attempts"
- Check network connectivity
- Verify URL is accessible
- Increase `maxRetries` for unreliable connections

### Cache not working as expected
- Remember cache includes both URL AND prompt
- Check if 15-minute TTL has expired
- Verify cache hasn't exceeded `maxCacheSize` (causing eviction)

## See Also

- [FileSystemTools](FileSystemTools.md) - For file operations
- [ShellTools](ShellTools.md) - For shell command execution
- [BraveWebSearchTool](BraveWebSearchTool.md) - For web search capabilities
