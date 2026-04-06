# BraveWebSearchTool

Web search capabilities using the Brave Search API. Provides up-to-date information from the web with optional domain filtering.

**Features:**
- Search the web using Brave's privacy-focused search API
- Configurable result count (default: 10)
- Domain filtering (include/exclude specific domains)
- Supports both web and video search results
- Client-side domain filtering with subdomain matching
- Structured JSON response format
- Error handling with graceful fallbacks

## Overview

The `BraveWebSearchTool` enables AI agents to search the web for current information beyond their knowledge cutoff. It uses the [Brave Search API](https://brave.com/search/api/) to retrieve search results and returns them in a structured format suitable for AI processing.

**Key Characteristics:**
- Returns search results as JSON with title, URL, and description
- Supports domain-based filtering (client-side)
- Includes both web pages and videos in results
- Privacy-focused (Brave Search doesn't track users)

## Basic Usage

```java
BraveWebSearchTool searchTool = BraveWebSearchTool.builder("your-api-key")
    .build();

String results = searchTool.webSearch("Spring AI framework", null, null);
System.out.println(results);
```

**Response Format:**
```json
[
  {
    "title": "Spring AI - Spring",
    "url": "https://spring.io/projects/spring-ai",
    "description": "Spring AI provides abstractions for developing AI applications..."
  },
  {
    "title": "Spring AI Reference Documentation",
    "url": "https://docs.spring.io/spring-ai/reference/",
    "description": "Comprehensive guide to Spring AI features..."
  }
]
```

## Getting an API Key

1. Visit [Brave Search API](https://brave.com/search/api/)
2. Sign up for an account
3. Subscribe to a plan (Free tier available with rate limits)
4. Copy your API subscription token
5. Store it securely (environment variable recommended)

**Pricing (as of 2025):**
- Free: 2,000 queries/month
- Paid plans: Higher limits and additional features

## Builder Configuration

### Required Parameter

**`apiKey`** - Your Brave Search API subscription token

```java
BraveWebSearchTool.builder("your-api-key-here")
    .build();
```

### Optional Parameters

| Option | Default | Description |
|--------|---------|-------------|
| `resultCount` | 10 | Number of search results to return (max typically 20) |

**Example with custom result count:**

```java
BraveWebSearchTool searchTool = BraveWebSearchTool.builder("your-api-key")
    .resultCount(15)  // Return up to 15 results
    .build();
```

## Domain Filtering

### Client-Side Filtering

The tool supports filtering results by domain. **Important:** Domain filtering is applied **client-side** after fetching results, meaning filtered results still count against your API quota.

#### Include Specific Domains

```java
BraveWebSearchTool searchTool = BraveWebSearchTool.builder("your-api-key").build();

// Only include results from spring.io
String results = searchTool.webSearch(
    "Spring Boot tutorial",
    List.of("spring.io"),  // allowedDomains
    null                   // blockedDomains
);
```

#### Exclude Specific Domains

```java
// Exclude results from example.com and spam.com
String results = searchTool.webSearch(
    "Java programming",
    null,                              // allowedDomains
    List.of("example.com", "spam.com") // blockedDomains
);
```

#### Combine Allowed and Blocked

```java
// Only spring.io and baeldung.com, but exclude outdated-site.com
String results = searchTool.webSearch(
    "Spring Framework 2025",
    List.of("spring.io", "baeldung.com"),  // allowedDomains
    List.of("outdated-site.com")           // blockedDomains
);
```

### Subdomain Matching

Domain filters automatically match subdomains:

```java
// Filter: "spring.io" matches:
// - spring.io
// - docs.spring.io
// - start.spring.io
// - blog.spring.io
```

### Search Operators (Recommended)

**For better API quota efficiency**, use Brave's search operators directly in your query instead of client-side filtering:

```java
BraveWebSearchTool searchTool = BraveWebSearchTool.builder("your-api-key").build();

// Search only on spring.io (more efficient)
String results = searchTool.webSearch("Spring AI site:spring.io", null, null);

// Exclude a domain
String results2 = searchTool.webSearch("Java tutorial -site:example.com", null, null);

// Multiple site operators
String results3 = searchTool.webSearch(
    "Spring Framework (site:spring.io OR site:baeldung.com)",
    null,
    null
);
```

**Why search operators are better:**
- Results are filtered server-side by Brave
- Doesn't waste API quota on filtered-out results
- Faster response (no client-side processing)
- More accurate (Brave's native filtering)

## Spring Boot Integration

### Configuration with Environment Variable

**application.properties:**
```properties
brave.api.key=${BRAVE_API_KEY}
```

**Environment variable:**
```bash
export BRAVE_API_KEY="your-api-key-here"
```

### Bean Configuration

```java
@Configuration
public class SearchConfig {

    @Bean
    public BraveWebSearchTool braveWebSearchTool(
            @Value("${brave.api.key}") String apiKey) {
        return BraveWebSearchTool.builder(apiKey)
            .resultCount(10)
            .build();
    }
}
```

### ChatClient Integration

```java
@Configuration
public class AgentConfig {

    @Bean
    public ChatClient chatClient(
            ChatClient.Builder chatClientBuilder,
            @Value("${brave.api.key}") String braveApiKey) {

        return chatClientBuilder
            .defaultTools(BraveWebSearchTool.builder(braveApiKey)
                .resultCount(15)
                .build())
            .build();
    }
}
```

## Usage Examples

### Example 1: Basic Web Search

```java
BraveWebSearchTool searchTool = BraveWebSearchTool.builder("api-key").build();

String results = searchTool.webSearch("latest Spring AI features 2025", null, null);
// Returns JSON array of search results
```

### Example 2: Current Events

```java
// AI automatically uses current year in query
String results = searchTool.webSearch(
    "Spring Framework news 2025",
    null,
    null
);
```

### Example 3: Domain-Specific Research

```java
// Research from official documentation only
String officialDocs = searchTool.webSearch(
    "Spring AI vector stores",
    List.of("spring.io", "docs.spring.io"),
    null
);

// Exclude tutorial sites, focus on official sources
String research = searchTool.webSearch(
    "Spring Boot 4.0 migration guide",
    List.of("spring.io"),
    List.of("tutorialspoint.com", "w3schools.com")
);
```

### Example 4: Competitive Research

```java
// Compare frameworks, exclude specific domains
String comparison = searchTool.webSearch(
    "Spring vs Quarkus performance benchmarks",
    null,
    List.of("biased-site.com")
);
```

### Example 5: Technology Stack Research

```java
// Find tutorials from trusted sources
String tutorials = searchTool.webSearch(
    "Spring AI LangChain4j integration tutorial",
    List.of("spring.io", "github.com", "medium.com", "dev.to"),
    null
);
```

## AI Agent Integration

When integrated with ChatClient, AI agents can automatically search the web and cite sources:

```java
ChatClient chatClient = chatClientBuilder
    .defaultTools(BraveWebSearchTool.builder(apiKey).build())
    .build();

String response = chatClient.prompt()
    .user("What are the latest Spring AI features announced in 2025?")
    .call()
    .content();

// AI response will include search results and sources:
// "Based on recent announcements, Spring AI 2.0 includes..."
//
// Sources:
// - [Spring AI 2.0 Release Notes](https://spring.io/blog/2025/...)
// - [Spring AI Documentation](https://docs.spring.io/spring-ai/...)
```

## Response Format

The tool returns a JSON array of search results:

```json
[
  {
    "title": "Page Title",
    "url": "https://example.com/page",
    "description": "Brief description or snippet from the page"
  }
]
```

**Fields:**
- `title` (string): The title of the web page or video
- `url` (string): The full URL
- `description` (string): Snippet or summary (may be empty for some results)

## Error Handling

### Empty Query

```java
String results = searchTool.webSearch("", null, null);
// Returns: "[]" (empty JSON array)
```

### API Errors

```java
// Invalid API key, network error, or API down
String results = searchTool.webSearch("query", null, null);
// Returns: "[]" (empty JSON array)
// Error logged but doesn't throw exception
```

### Null/Invalid API Key

```java
// Throws IllegalArgumentException at build time
BraveWebSearchTool searchTool = BraveWebSearchTool.builder(null).build();
// Exception: "API key must not be null or empty"
```

## Best Practices

### 1. Use Search Operators for Filtering

```java
// ✅ GOOD: Server-side filtering (efficient)
searchTool.webSearch("Spring AI site:spring.io", null, null);

// ❌ LESS EFFICIENT: Client-side filtering (wastes quota)
searchTool.webSearch("Spring AI", List.of("spring.io"), null);
```

### 2. Include Year in Queries

```java
// ✅ GOOD: Specifies current year for recent info
searchTool.webSearch("React best practices 2025", null, null);

// ❌ LESS PRECISE: May return outdated results
searchTool.webSearch("React best practices", null, null);
```

### 3. Store API Key Securely

```java
// ✅ GOOD: Environment variable
String apiKey = System.getenv("BRAVE_API_KEY");

// ✅ GOOD: Spring property
@Value("${brave.api.key}") String apiKey;

// ❌ BAD: Hardcoded
String apiKey = "abc123..."; // Never do this!
```

### 4. Optimize Result Count

```java
// ✅ GOOD: Just enough results
BraveWebSearchTool.builder(apiKey).resultCount(10).build();

// ❌ WASTEFUL: Too many results (costs more quota)
BraveWebSearchTool.builder(apiKey).resultCount(50).build();
```

### 5. Handle Empty Results

```java
String jsonResults = searchTool.webSearch("query", null, null);

if (jsonResults.equals("[]")) {
    // No results found or error occurred
    logger.warn("Search returned no results");
}
```

## Advanced Usage

### Custom Result Processing

```java
BraveWebSearchTool searchTool = BraveWebSearchTool.builder(apiKey).build();

String jsonResults = searchTool.webSearch("Spring AI", null, null);

// Parse JSON and process
ObjectMapper mapper = new ObjectMapper();
List<SearchResult> results = mapper.readValue(
    jsonResults,
    new TypeReference<List<SearchResult>>() {}
);

for (SearchResult result : results) {
    System.out.println(result.title() + ": " + result.url());
}
```

### Combining with SmartWebFetchTool

```java
BraveWebSearchTool searchTool = BraveWebSearchTool.builder(apiKey).build();
SmartWebFetchTool fetchTool = SmartWebFetchTool.builder(chatClient).build();

// 1. Search for relevant pages
String searchResults = searchTool.webSearch(
    "Spring AI vector databases tutorial",
    List.of("spring.io", "baeldung.com"),
    null
);

// 2. Parse results
List<SearchResult> results = parseResults(searchResults);

// 3. Fetch and summarize top result
if (!results.isEmpty()) {
    String url = results.get(0).url();
    String summary = fetchTool.webFetch(url, "Summarize the tutorial");
    System.out.println(summary);
}
```

### Multi-Source Research

```java
BraveWebSearchTool searchTool = BraveWebSearchTool.builder(apiKey)
    .resultCount(20)
    .build();

String[] queries = {
    "Spring AI embeddings site:spring.io",
    "Spring AI vector store comparison",
    "Spring AI RAG implementation guide"
};

for (String query : queries) {
    String results = searchTool.webSearch(query, null, null);
    // Process each result set
    analyzeResults(results);
}
```

## Limitations

### API Quota

- Free tier: 2,000 queries/month
- Each query counts even if results are filtered client-side
- Use search operators for efficient filtering

### Geographic Availability

- Web search is optimized for US-based queries
- Results may vary by geographic location
- Some features may be region-specific

### Client-Side Filtering

- Domain filtering happens after API call
- Filtered results still count against quota
- Use `site:` operator for server-side filtering

### Result Count Limits

- Maximum typically ~20 results per query
- Higher counts may not return proportionally more results
- Brave API limits may apply

### No Authentication Support

- Results are from public web only
- Cannot access authenticated/paywalled content
- No support for API keys in search results

## Troubleshooting

### "Empty response from Brave Search API"

**Causes:**
- Invalid API key
- Network connectivity issues
- API service down
- Rate limit exceeded

**Solutions:**
```java
// Verify API key is correct
String apiKey = System.getenv("BRAVE_API_KEY");
logger.info("Using API key: {}...", apiKey.substring(0, 8));

// Check API status at brave.com
```

### Results Count Against Quota

**Problem:** Domain filtering still uses quota

**Solution:** Use search operators instead
```java
// Instead of this:
searchTool.webSearch("query", List.of("example.com"), null);

// Do this:
searchTool.webSearch("query site:example.com", null, null);
```

### No Results Returned

**Causes:**
- Query too specific
- All results filtered by domain rules
- Brave has no indexed results

**Solutions:**
```java
// Broaden the query
searchTool.webSearch("Spring Framework", null, null);

// Relax domain filters
searchTool.webSearch("query", null, null);

// Check if results exist on Brave Search directly
```

### Rate Limit Exceeded

**Solution:** Implement rate limiting
```java
@Component
public class RateLimitedSearchTool {
    private final BraveWebSearchTool searchTool;
    private final RateLimiter rateLimiter;

    public RateLimitedSearchTool(BraveWebSearchTool searchTool) {
        this.searchTool = searchTool;
        // 2000 queries per month = ~1.5 per minute average
        this.rateLimiter = RateLimiter.create(1.0);
    }

    public String search(String query) {
        rateLimiter.acquire();
        return searchTool.webSearch(query, null, null);
    }
}
```

## Security Considerations

### API Key Protection

**Never commit API keys to version control:**

```bash
# .gitignore
.env
application-local.properties
```

**Use environment variables:**
```bash
export BRAVE_API_KEY="your-key"
```

**Or Spring profiles:**
```properties
# application-local.properties (not committed)
brave.api.key=your-actual-key
```

### Input Validation

The tool automatically handles:
- Empty queries (returns empty array)
- Null domain lists (no filtering applied)
- Invalid URLs in results (filtered out)

### Query Sanitization

```java
// Tool automatically handles special characters
String results = searchTool.webSearch(
    "user input with special chars: & ? #",
    null,
    null
);
// URL encoding handled automatically
```

## Performance Tips

### Minimize API Calls

```java
// ✅ GOOD: Single search with site operator
searchTool.webSearch("Spring AI site:spring.io OR site:baeldung.com", null, null);

// ❌ WASTEFUL: Multiple searches
searchTool.webSearch("Spring AI site:spring.io", null, null);
searchTool.webSearch("Spring AI site:baeldung.com", null, null);
```

### Cache Results

```java
@Component
public class CachedSearchTool {
    private final BraveWebSearchTool searchTool;
    private final Cache<String, String> cache;

    public CachedSearchTool(BraveWebSearchTool searchTool) {
        this.searchTool = searchTool;
        this.cache = CacheBuilder.newBuilder()
            .maximumSize(100)
            .expireAfterWrite(15, TimeUnit.MINUTES)
            .build();
    }

    public String search(String query) {
        return cache.get(query, () -> searchTool.webSearch(query, null, null));
    }
}
```

### Optimize Result Count

```java
// Start with fewer results
BraveWebSearchTool searchTool = BraveWebSearchTool.builder(apiKey)
    .resultCount(5)  // Only fetch what you need
    .build();
```

## Source Citation Requirements

**CRITICAL:** When using search results in AI responses, always cite sources:

```java
// AI must include sources section
String response = """
    Based on the search results, Spring AI 2.0 introduces vector store support...

    Sources:
    - [Spring AI Release Notes](https://spring.io/blog/2025/01/spring-ai-2.0)
    - [Vector Store Documentation](https://docs.spring.io/spring-ai/reference/vectors)
    """;
```

**Required format:**
- "Sources:" section at the end of response
- Each source as markdown link: `[Title](URL)`
- Include all relevant sources used

## See Also

- [SmartWebFetchTool](SmartWebFetchTool.md) - Fetch and summarize specific URLs
- [FileSystemTools](FileSystemTools.md) - File operations for storing search results
- [Brave Search API Documentation](https://brave.com/search/api/) - Official API reference
