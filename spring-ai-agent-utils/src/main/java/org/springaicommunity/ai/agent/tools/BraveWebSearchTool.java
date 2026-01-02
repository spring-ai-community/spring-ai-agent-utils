/*
* Copyright 2025 - 2025 the original author or authors.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* https://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package org.springaicommunity.ai.agent.tools;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.util.json.JsonParser;
import org.springframework.http.MediaType;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Brave Web Search Tool for Spring AI.
 * <p>
 * Provides web search capabilities using the Brave Search API. Supports domain filtering
 * and returns structured search results.
 * </p>
 *
 * <h2>Usage Examples</h2>
 *
 * <h3>Basic Usage</h3>
 * <pre>{@code
 * BraveWebSearchTool searchTool = BraveWebSearchTool.builder("your-api-key")
 *     .build();
 *
 * String results = searchTool.webSearch("Spring AI framework", null, null);
 * }</pre>
 *
 * <h3>Custom Result Count</h3>
 * <pre>{@code
 * BraveWebSearchTool searchTool = BraveWebSearchTool.builder("your-api-key")
 *     .resultCount(15)
 *     .build();
 *
 * String results = searchTool.webSearch("machine learning", null, null);
 * }</pre>
 *
 * <h3>Domain Filtering</h3>
 * <pre>{@code
 * BraveWebSearchTool searchTool = BraveWebSearchTool.builder("your-api-key")
 *     .resultCount(10)
 *     .build();
 *
 * // Only include results from spring.io
 * String results = searchTool.webSearch(
 *     "Spring Boot tutorial",
 *     List.of("spring.io"),
 *     null
 * );
 *
 * // Exclude results from specific domains
 * String results2 = searchTool.webSearch(
 *     "Java programming",
 *     null,
 *     List.of("example.com", "spam.com")
 * );
 *
 * // Combine allowed and blocked domains
 * String results3 = searchTool.webSearch(
 *     "Spring Framework 2025",
 *     List.of("spring.io", "baeldung.com"),
 *     List.of("outdated-site.com")
 * );
 * }</pre>
 *
 * <h3>Search with Site Operator (Recommended for Quota Efficiency)</h3>
 * <pre>{@code
 * // Using search operators is more efficient than client-side domain filtering
 * BraveWebSearchTool searchTool = BraveWebSearchTool.builder("your-api-key")
 *     .build();
 *
 * // Search only on spring.io using site operator
 * String results = searchTool.webSearch("Spring AI site:spring.io", null, null);
 *
 * // Exclude a domain using minus operator
 * String results2 = searchTool.webSearch("Java tutorial -site:example.com", null, null);
 * }</pre>
 *
 * <h3>Spring Boot Configuration</h3>
 * <pre>{@code
 * @Configuration
 * public class SearchConfig {
 *
 *     @Bean
 *     public BraveWebSearchTool braveWebSearchTool(
 *             @Value("${brave.api.key}") String apiKey) {
 *         return BraveWebSearchTool.builder(apiKey)
 *             .resultCount(10)
 *             .build();
 *     }
 * }
 * }</pre>
 *
 * @author Christian Tzolov
 * @see <a href="https://mikhail.io/2025/10/claude-code-web-tools/">Claude Code Web
 * Tools</a>
 * @see <a href="https://brave.com/search/api/">Brave Search API</a>
 */
public class BraveWebSearchTool {

	private static final Logger logger = LoggerFactory.getLogger(BraveWebSearchTool.class);

	private static final String BRAVE_API_BASE_URL = "https://api.search.brave.com";

	private static final String WEB_SEARCH_PATH = "/res/v1/web/search";

	private final RestClient restClient;

	private final int resultCount;

	/**
	 * Creates a new BraveWebSearchTool with the specified parameters. Use
	 * {@link #builder(String)} to create instances with custom configuration.
	 * @param apiKey the Brave Search API subscription token (must not be null or empty)
	 * @param resultCount the number of results to return per search
	 */
	private BraveWebSearchTool(String apiKey, int resultCount) {
		Assert.hasText(apiKey, "API key must not be null or empty");
		this.restClient = RestClient.builder()
			.baseUrl(BRAVE_API_BASE_URL)
			.defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
			.defaultHeader("Accept-Encoding", "gzip")
			.defaultHeader("X-Subscription-Token", apiKey)
			.build();

		this.resultCount = resultCount;
	}

	/**
	 * Performs a web search using the Brave Search API.
	 * <p>
	 * <b>Note on Domain Filtering:</b> The Brave Search API does not support native
	 * domain filtering via query parameters. The {@code allowedDomains} and
	 * {@code blockedDomains} parameters are applied client-side after fetching results,
	 * which means filtered results still count against your API quota. For better quota
	 * usage, consider using search operators directly in your query (e.g.,
	 * "Spring AI site:spring.io" or "Java -site:example.com").
	 * </p>
	 * @param query the search query to execute
	 * @param allowedDomains optional list of domains to include in results (client-side
	 * filtering, null for no filtering)
	 * @param blockedDomains optional list of domains to exclude from results (client-side
	 * filtering, null for no filtering)
	 * @return JSON string containing the search results
	 */
	// @formatter:off
	@Tool(name = "WebSearch", description = """
		- Allows Claude to search the web and use the results to inform responses
		- Provides up-to-date information for current events and recent data
		- Returns search result information formatted as search result blocks, including links as markdown hyperlinks
		- Use this tool for accessing information beyond Claude's knowledge cutoff
		- Searches are performed automatically within a single API call

		CRITICAL REQUIREMENT - You MUST follow this:
		- After answering the user's question, you MUST include a "Sources:" section at the end of your response
		- In the Sources section, list all relevant URLs from the search results as markdown hyperlinks: [Title](URL)
		- This is MANDATORY - never skip including sources in your response
		- Example format:

			[Your answer here]

			Sources:
			- [Source Title 1](https://example.com/1)
			- [Source Title 2](https://example.com/2)

		Usage notes:
		- Domain filtering is supported to include or block specific websites (applied client-side after fetching results)
		- For better API quota usage, consider using search operators in your query (e.g., "Spring AI site:spring.io")
		- Web search is only available in the US

		IMPORTANT - Use the correct year in search queries:
		- When searching for recent information, documentation, or current events, always include the current year in your query
		- Example: If searching for latest React docs, search for "React documentation 2025" rather than older years
		""")
	@SuppressWarnings("unchecked")	
	public String webSearch(
		@ToolParam(description = "The search query to use") String query,
		@ToolParam(description = "Only include search results from these domains", required = false) List<String> allowedDomains,
		@ToolParam(description = "Never include search results from these domains", required = false) List<String> blockedDomains) {
		// @formatter:on

		if (!StringUtils.hasText(query)) {
			logger.warn("Empty search query provided");
			return JsonParser.toJson(Collections.emptyList());
		}

		try {
			// Note: Brave Search API doesn't support native domain filtering via query parameters.
			// Domain filtering is applied client-side after results are fetched.
			// For better API quota usage, consider using search operators in the query
			// (e.g., "query site:example.com") instead of the domain filter parameters.
			if (!CollectionUtils.isEmpty(allowedDomains) || !CollectionUtils.isEmpty(blockedDomains)) {
				logger.debug("Client-side domain filtering will be applied. Allowed domains: {}, Blocked domains: {}",
						allowedDomains, blockedDomains);
			}

			Map<String, Object> queryResponse = this.executeSearch(query);

			if (queryResponse == null || queryResponse.isEmpty()) {
				logger.warn("Empty response from Brave Search API for query: {}", query);
				return JsonParser.toJson(Collections.emptyList());
			}

			List<SearchResult> allResults = new ArrayList<>();

			// Parse web results			
			if (queryResponse.containsKey("web")) {				
				allResults.addAll(this.parseResults((Map<String, Object>) queryResponse.get("web")));
			}

			// Parse video results
			if (queryResponse.containsKey("videos")) {
				allResults.addAll(this.parseResults((Map<String, Object>) queryResponse.get("videos")));
			}

			// Apply domain filtering
			List<SearchResult> filteredResults = this.applyDomainFiltering(allResults, allowedDomains,
					blockedDomains);

			if (filteredResults.size() < allResults.size()) {
				int filtered = allResults.size() - filteredResults.size();
				logger.info("Search for '{}' returned {} results, {} filtered out by domain rules, {} remaining",
						query, allResults.size(), filtered, filteredResults.size());
			}
			else {
				logger.debug("Search for '{}' returned {} results (no filtering applied)", query, allResults.size());
			}

			return JsonParser.toJson(filteredResults);

		}
		catch (RestClientException e) {
			logger.error("Error executing Brave Search API request for query: {}", query, e);
			// Return empty list to maintain consistent response format
			return JsonParser.toJson(Collections.emptyList());
		}
	}

	/**
	 * Executes the search request against the Brave Search API.
	 */
	private Map<String, Object> executeSearch(String query) {
		try {
			@SuppressWarnings("unchecked")
			Map<String, Object> response = this.restClient.get()
				.uri(uriBuilder -> uriBuilder.path(WEB_SEARCH_PATH)
					.queryParam("q", query)
					.queryParam("count", this.resultCount)
					.build())
				.retrieve()
				.onStatus(status -> status.is4xxClientError(), (request, errorResponse) -> {
					logger.error("Client error from Brave API: {} for query: {}", errorResponse.getStatusCode(), query);
				})
				.onStatus(status -> status.is5xxServerError(), (request, errorResponse) -> {
					logger.error("Server error from Brave API: {} for query: {}", errorResponse.getStatusCode(), query);
				})
				.body(Map.class);
			return response != null ? response : Collections.emptyMap();
		}
		catch (Exception e) {
			logger.error("Failed to execute search request for query: {}", query, e);
			return Collections.emptyMap();
		}
	}

	public record SearchResult(String title, String url, String description) {
	}

	/**
	 * Parses the results from a Brave Search API response section.
	 */
	private List<SearchResult> parseResults(Map<String, Object> resultSection) {
		if (CollectionUtils.isEmpty(resultSection)) {
			return Collections.emptyList();
		}

		@SuppressWarnings("unchecked")
		List<Map<String, Object>> results = (List<Map<String, Object>>) resultSection.get("results");

		if (CollectionUtils.isEmpty(results)) {
			return Collections.emptyList();
		}

		return results.stream()
			.filter(entry -> entry != null && entry.get("title") != null && entry.get("url") != null)
			.map(entry -> new SearchResult(
				(String) entry.get("title"),
				(String) entry.get("url"),
				entry.get("description") != null ? (String) entry.get("description") : ""))
			.toList();
	}

	/**
	 * Applies domain filtering to the search results.
	 * @param results the original search results
	 * @param allowedDomains domains to include (if specified, only these domains are
	 * kept)
	 * @param blockedDomains domains to exclude
	 * @return filtered list of results
	 */
	private List<SearchResult> applyDomainFiltering(List<SearchResult> results,
			List<String> allowedDomains, List<String> blockedDomains) {

		if (CollectionUtils.isEmpty(allowedDomains) && CollectionUtils.isEmpty(blockedDomains)) {
			return results;
		}

		Set<String> allowedSet = toNormalizedDomainSet(allowedDomains);
		Set<String> blockedSet = toNormalizedDomainSet(blockedDomains);

		return results.stream()
			.filter(result -> filterByDomain(result, allowedSet, blockedSet))
			.toList();
	}

	/**
	 * Converts a list of domains to a normalized (lowercase) set.
	 * @param domains the list of domains to normalize
	 * @return a set of lowercase domain strings, or empty set if input is null/empty
	 */
	private Set<String> toNormalizedDomainSet(List<String> domains) {
		return CollectionUtils.isEmpty(domains) ? Collections.emptySet()
				: domains.stream().map(String::toLowerCase).collect(Collectors.toSet());
	}

	/**
	 * Filters a search result based on allowed and blocked domain sets.
	 * @param result the search result to check
	 * @param allowedSet set of allowed domains (empty set means all allowed)
	 * @param blockedSet set of blocked domains (empty set means none blocked)
	 * @return true if the result should be included, false otherwise
	 */
	private boolean filterByDomain(SearchResult result, Set<String> allowedSet, Set<String> blockedSet) {
		String url = result.url();
		if (url == null) {
			return false;
		}
		String domain = extractDomain(url);

		// If allowed domains specified, URL must match one of them
		if (!allowedSet.isEmpty() && !matchesDomain(domain, allowedSet)) {
			return false;
		}

		// If blocked domains specified, URL must not match any of them
		if (!blockedSet.isEmpty() && matchesDomain(domain, blockedSet)) {
			return false;
		}

		return true;
	}

	/**
	 * Extracts the domain from a URL using java.net.URI for robust parsing.
	 */
	private String extractDomain(String url) {
		try {
			// Handle URLs without protocol
			String normalizedUrl = url;
			if (!url.toLowerCase().startsWith("http://") && !url.toLowerCase().startsWith("https://")) {
				normalizedUrl = "https://" + url;
			}

			URI uri = new URI(normalizedUrl);
			String host = uri.getHost();

			if (host != null) {
				return host.toLowerCase();
			}

			// Fallback: if URI parsing doesn't extract host, fall back to string manipulation
			logger.warn("URI parsing failed to extract host from URL: {}, using fallback", url);
			return extractDomainFallback(url);
		}
		catch (URISyntaxException e) {
			logger.warn("Failed to parse URL: {}, using fallback extraction", url);
			return extractDomainFallback(url);
		}
	}

	/**
	 * Fallback method for extracting domain using string manipulation.
	 */
	private String extractDomainFallback(String url) {
		try {
			String domain = url.toLowerCase();
			// Remove protocol
			if (domain.contains("://")) {
				domain = domain.substring(domain.indexOf("://") + 3);
			}
			// Remove path
			if (domain.contains("/")) {
				domain = domain.substring(0, domain.indexOf("/"));
			}
			// Remove port
			if (domain.contains(":")) {
				domain = domain.substring(0, domain.indexOf(":"));
			}
			return domain;
		}
		catch (Exception e) {
			logger.warn("Fallback domain extraction also failed for URL: {}", url);
			return url.toLowerCase();
		}
	}

	/**
	 * Checks if a domain matches any domain in the given set. Supports subdomain matching
	 * (e.g., "docs.example.com" matches "example.com").
	 */
	private boolean matchesDomain(String domain, Set<String> domainSet) {
		for (String filter : domainSet) {
			if (domain.equals(filter) || domain.endsWith("." + filter)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Creates a new Builder instance with the required API key.
	 * @param apiKey the Brave Search API subscription token (required)
	 * @return a new Builder instance
	 */
	public static Builder builder(String apiKey) {
		return new Builder(apiKey);
	}

	/**
	 * Builder class for creating BraveWebSearchTool instances.
	 */
	public static class Builder {

		private final String apiKey;

		private int resultCount = 10; // Default: 10 results

		/**
		 * Creates a new Builder with the required API key.
		 * @param apiKey the Brave Search API subscription token (required)
		 */
		private Builder(String apiKey) {
			if (!StringUtils.hasText(apiKey)) {
				throw new IllegalArgumentException("API key must not be null or empty");
			}
			this.apiKey = apiKey;
		}

		/**
		 * Sets the number of search results to return per query.
		 * @param resultCount the number of results (must be positive, max typically 20
		 * for Brave API)
		 * @return this Builder instance
		 */
		public Builder resultCount(int resultCount) {
			if (resultCount <= 0) {
				throw new IllegalArgumentException("resultCount must be positive");
			}
			this.resultCount = resultCount;
			return this;
		}

		/**
		 * Builds and returns a new BraveWebSearchTool instance.
		 * @return a new BraveWebSearchTool instance
		 */
		public BraveWebSearchTool build() {
			return new BraveWebSearchTool(this.apiKey, this.resultCount);
		}

	}

}
