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
package org.springaicommunity.agent.tools;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/**
 * A smart web fetch tool that retrieves content from URLs and processes it using an AI
 * model for summarization.
 *
 * <p>
 * Features:
 * <ul>
 * <li>Fetches HTML content and converts it to Markdown</li>
 * <li>Includes a 15-minute cache for faster repeated access</li>
 * <li>Optional domain safety checking via Claude's domain info API</li>
 * <li>Automatic content truncation with configurable limits</li>
 * </ul>
 *
 * <p>
 * This class implements {@link AutoCloseable} to ensure proper cleanup of HTTP client
 * resources. It's recommended to use try-with-resources or explicitly call
 * {@link #close()} when done using this tool.
 *
 * @author Christian Tzolov
 * @see <a href="https://mikhail.io/2025/10/claude-code-web-tools/">Reference</a>
 */
public class SmartWebFetchTool implements AutoCloseable {

	private static final Logger logger = LoggerFactory.getLogger(SmartWebFetchTool.class);

	private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
			+ "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

	private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(10);

	private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(30);

	private static final Duration CACHE_TTL = Duration.ofMinutes(15);

	private static final String DOMAIN_SAFETY_CHECK_URL = "https://claude.ai/api/web/domain_info";

	private static final Pattern CHARSET_PATTERN = Pattern.compile("charset=([^;\\s]+)", Pattern.CASE_INSENSITIVE);

	private static final String FETCH_SUMMARIZE_PROMPT = """
			Web page content:
			---
			{content}
			---

			{userQuery}

			Provide a concise response based only on the content above. In your response:
			- Enforce a strict 125-character maximum for quotes from any source document. Open Source Software is ok as long as we respect the license.
			- Use quotation marks for exact language from articles; any language outside of the quotation should never be word-for-word the same.
			- You are not a lawyer and never comment on the legality of your own prompts and responses.
			- Never produce or reproduce exact song lyrics.
			""";

	private final HttpClient httpClient;

	private final ChatClient chatClient;

	private final int maxContentLength;

	private final boolean domainSafetyCheck;

	private final DefaultDomainCanFetchChecker domainCanFetchChecker;

	private final FlexmarkHtmlConverter htmlToMarkdownConverter;

	private final Map<String, CacheEntry> urlCache;

	private final int maxCacheSize;

	private final Object cacheLock = new Object();

	private final boolean failOpenOnSafetyCheckError;

	private final int maxRetries;

	/**
	 * Creates a new SmartWebFetchTool with the specified parameters.
	 * @param chatClient the ChatClient to use for summarization
	 * @param maxContentLength the maximum content length to process
	 * @param domainSafetyCheck whether to perform domain safety checks
	 * @param maxCacheSize the maximum number of entries to keep in cache
	 * @param failOpenOnSafetyCheckError whether to allow fetch if safety check fails
	 * @param maxRetries maximum number of retry attempts for transient failures
	 */
	private SmartWebFetchTool(ChatClient chatClient, int maxContentLength, boolean domainSafetyCheck, int maxCacheSize,
			boolean failOpenOnSafetyCheckError, int maxRetries) {
		this.httpClient = HttpClient.newBuilder()
			.followRedirects(HttpClient.Redirect.ALWAYS)
			.connectTimeout(DEFAULT_CONNECT_TIMEOUT)
			.build();

		this.chatClient = chatClient;
		this.maxContentLength = maxContentLength;
		this.domainSafetyCheck = domainSafetyCheck;
		this.maxCacheSize = maxCacheSize;
		this.failOpenOnSafetyCheckError = failOpenOnSafetyCheckError;
		this.maxRetries = maxRetries;
		this.htmlToMarkdownConverter = FlexmarkHtmlConverter.builder().build();
		this.domainCanFetchChecker = new DefaultDomainCanFetchChecker();
		this.urlCache = new ConcurrentHashMap<>();
	}

	// @formatter:off
	@Tool(name = "WebFetch", description = """
		Fetches content from a specified URL and processes it using an AI model.

		Features:
		- Takes a URL and a prompt as input
		- Fetches the URL content using HTTP GET method
		- Converts HTML to markdown
		- Processes the content with the prompt using a small, fast model
		- Returns the model's response about the content
		- Includes a self-cleaning 15-minute cache for faster responses
		- Automatic retry on network errors and 5xx server errors

		Usage notes:
		- IMPORTANT: If an MCP-provided web fetch tool is available, prefer using that tool instead.
		- The URL must be a fully-formed valid URL (e.g., https://example.com)
		- HTTP URLs will be automatically upgraded to HTTPS
		- Only HTTP GET requests are supported (read-only)
		- The prompt should describe what information you want to extract from the page
		- This tool is read-only and does not modify any files or send any data
		- Results may be summarized if the content is very large
		- Retries up to 2 times (configurable) on transient failures with exponential backoff
		""")
	// @formatter:on
	public String webFetch(@ToolParam(description = "The URL to fetch content from") String url,
			@ToolParam(description = "The prompt to run on the fetched content") String prompt) {

		// Validate URL
		if (!StringUtils.hasText(url)) {
			return "Error: URL cannot be empty or null";
		}

		url = url.trim();

		// Validate URL format
		URI uri;
		try {
			uri = URI.create(url);
			if (uri.getScheme() == null || uri.getHost() == null) {
				return "Error: Invalid URL format. Please provide a fully-formed URL (e.g., https://example.com)";
			}
		}
		catch (IllegalArgumentException e) {
			return "Error: Invalid URL format: " + e.getMessage();
		}

		// Domain safety check
		if (this.domainSafetyCheck) {
			DomainCanFetch check = this.domainCanFetchChecker.check(url, this.failOpenOnSafetyCheckError);
			if (!check.canFetch()) {
				return "Domain safety check failed for URL '" + url + "': " + check.reason();
			}
		}

		// Check cache first (cache key includes both URL and prompt)
		String cacheKey = this.buildCacheKey(url, prompt);
		String content = this.getCachedContent(cacheKey);

		if (content != null) {
			logger.debug("Cache hit for URL: {} with prompt hash: {}", url, prompt.hashCode());
			return content;
		}

		logger.debug("Cache miss for URL: {} with prompt hash: {}", url, prompt.hashCode());

		// Fetch HTML content with retry logic
		String htmlContent;
		try {
			HttpResponse<String> response = this.fetchHtmlWithRetry(url);
			if (response.statusCode() >= 400) {
				return "Error: Failed to fetch URL. HTTP status code: " + response.statusCode();
			}
			htmlContent = response.body();
			if (htmlContent == null || htmlContent.isBlank()) {
				return "Error: Retrieved empty content from URL";
			}
		}
		catch (WebFetchException e) {
			logger.error("Failed to fetch URL: {}", url, e);
			return "Error fetching URL: " + e.getMessage();
		}

		// Convert HTML to Markdown
		String mdContent = this.htmlToMarkdownConverter.convert(htmlContent);

		mdContent = this.truncate(mdContent);

		// Summarize with AI
		String summary = this.summarize(mdContent, prompt);

		// Cache the content
		this.cacheContent(cacheKey, summary);

		return summary;
	}

	/**
	 * Builds a cache key that includes both URL and prompt to avoid collisions when the
	 * same URL is queried with different prompts.
	 * @param url the URL
	 * @param prompt the prompt
	 * @return a unique cache key
	 */
	private String buildCacheKey(String url, String prompt) {
		return url + "::prompt::" + prompt.hashCode();
	}

	private HttpResponse<String> fetchHtmlWithRetry(String url) {
		int attempt = 0;
		Exception lastException = null;

		while (attempt <= this.maxRetries) {
			try {
				if (attempt > 0) {
					// Exponential backoff: 1s, 2s, 4s, etc.
					long backoffMs = (long) Math.pow(2, attempt - 1) * 1000;
					logger.debug("Retrying fetch for URL: {} (attempt {}/{}), waiting {}ms", url, attempt,
							this.maxRetries, backoffMs);
					Thread.sleep(backoffMs);
				}

				HttpResponse<String> response = this.fetchHtml(url);

				// Retry on 5xx server errors (transient failures)
				if (response.statusCode() >= 500 && response.statusCode() < 600) {
					lastException = new WebFetchException(
							"Server error: HTTP " + response.statusCode(), null);
					logger.warn("Fetch attempt {} returned server error {} for URL: {}",
							attempt + 1, response.statusCode(), url);
					attempt++;
					continue;
				}

				return response;
			}
			catch (WebFetchException e) {
				lastException = e;
				// Only retry on network errors, not on interruptions
				if (e.getCause() instanceof InterruptedException) {
					throw e;
				}
				logger.warn("Fetch attempt {} failed for URL: {}: {}", attempt + 1, url, e.getMessage());
				attempt++;
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new WebFetchException("Retry interrupted", e);
			}
		}

		// All retries exhausted
		if (lastException == null) {
			throw new WebFetchException("Failed after " + (this.maxRetries + 1) + " attempts", null);
		}
		else if (lastException instanceof WebFetchException) {
			throw new WebFetchException("Failed after " + (this.maxRetries + 1) + " attempts", lastException);
		}
		else {
			throw new WebFetchException("Failed after " + (this.maxRetries + 1) + " attempts: "
					+ lastException.getMessage(), lastException);
		}
	}

	private HttpResponse<String> fetchHtml(String url) {
		HttpRequest request = HttpRequest.newBuilder()
			.uri(URI.create(url))
			.timeout(DEFAULT_REQUEST_TIMEOUT)
			.header("User-Agent", USER_AGENT)
			.header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
			.header("Accept-Language", "en-US,en;q=0.5")
			.GET()
			.build();

		try {
			// First fetch as bytes to handle charset properly
			HttpResponse<byte[]> byteResponse = this.httpClient.send(request,
					HttpResponse.BodyHandlers.ofByteArray());

			// Extract charset from Content-Type header
			Charset charset = this.extractCharset(byteResponse).orElse(StandardCharsets.UTF_8);

			// Convert bytes to string using the detected charset
			String body = new String(byteResponse.body(), charset);

			// Create a string response with the same metadata
			return new HttpResponse<String>() {
				@Override
				public int statusCode() {
					return byteResponse.statusCode();
				}

				@Override
				public HttpRequest request() {
					return byteResponse.request();
				}

				@Override
				public Optional<HttpResponse<String>> previousResponse() {
					return Optional.empty();
				}

				@Override
				public java.net.http.HttpHeaders headers() {
					return byteResponse.headers();
				}

				@Override
				public String body() {
					return body;
				}

				@Override
				public Optional<javax.net.ssl.SSLSession> sslSession() {
					return byteResponse.sslSession();
				}

				@Override
				public URI uri() {
					return byteResponse.uri();
				}

				@Override
				public java.net.http.HttpClient.Version version() {
					return byteResponse.version();
				}
			};
		}
		catch (IOException e) {
			throw new WebFetchException("Network error while fetching URL: " + e.getMessage(), e);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new WebFetchException("Request was interrupted", e);
		}
	}

	/**
	 * Extracts the charset from the Content-Type header.
	 * @param response the HTTP response
	 * @return the detected charset, or empty if not found
	 */
	private Optional<Charset> extractCharset(HttpResponse<?> response) {
		return response.headers()
			.firstValue("Content-Type")
			.flatMap(contentType -> {
				Matcher matcher = CHARSET_PATTERN.matcher(contentType);
				if (matcher.find()) {
					String charsetName = matcher.group(1);
					try {
						return Optional.of(Charset.forName(charsetName));
					}
					catch (Exception e) {
						logger.warn("Unsupported charset '{}', falling back to UTF-8", charsetName);
						return Optional.empty();
					}
				}
				return Optional.empty();
			});
	}

	private String summarize(String content, String userQuery) {
		try {
			String response = this.chatClient.prompt()
				.user(u -> u.text(FETCH_SUMMARIZE_PROMPT).param("content", content).param("userQuery", userQuery))
				.call()
				.content();
			return response != null ? response : "Error: Received empty response from AI model";
		}
		catch (Exception e) {
			logger.error("Failed to summarize content", e);
			return "Error summarizing content: " + e.getMessage();
		}
	}

	private String truncate(String content) {
		if (content == null) {
			return "";
		}
		if (content.length() > this.maxContentLength) {
			logger.warn("Content too long ({} characters). Truncating to {} characters.", content.length(),
					this.maxContentLength);
			return content.substring(0, this.maxContentLength);
		}
		return content;
	}

	private String getCachedContent(String url) {
		CacheEntry entry = this.urlCache.get(url);
		if (entry != null && !entry.isExpired()) {
			return entry.content();
		}
		// Remove expired entry
		if (entry != null) {
			this.urlCache.remove(url);
		}
		return null;
	}

	private void cacheContent(String cacheKey, String content) {
		// Clean up expired entries periodically with thread safety
		if (this.urlCache.size() > this.maxCacheSize) {
			synchronized (this.cacheLock) {
				// Double-check after acquiring lock
				if (this.urlCache.size() > this.maxCacheSize) {
					this.cleanExpiredEntries();
				}
			}
		}
		this.urlCache.put(cacheKey, new CacheEntry(content, System.currentTimeMillis()));
	}

	private void cleanExpiredEntries() {
		// This method should only be called while holding cacheLock
		this.urlCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
		logger.debug("Cleaned up expired cache entries. Current cache size: {}", this.urlCache.size());
	}

	/**
	 * Closes the HTTP client and cleans up resources. This method is idempotent and can
	 * be called multiple times safely.
	 */
	@Override
	public void close() {
		// HttpClient doesn't implement AutoCloseable, but we can shut down its executor
		// For now, we'll clear the cache and let the HttpClient be garbage collected
		this.urlCache.clear();
		logger.debug("SmartWebFetchTool closed and resources cleaned up");
	}

	/**
	 * Cache entry record with content and timestamp.
	 */
	private record CacheEntry(String content, long timestamp) {
		boolean isExpired() {
			return System.currentTimeMillis() - timestamp > CACHE_TTL.toMillis();
		}
	}

	/**
	 * Record representing the result of a domain fetch check.
	 */
	public record DomainCanFetch(String domain, boolean canFetch, String reason) {
	}

	/**
	 * Custom exception for web fetch errors.
	 */
	public static class WebFetchException extends RuntimeException {

		public WebFetchException(String message, Throwable cause) {
			super(message, cause);
		}

	}

	/**
	 * Domain safety checker using Claude's domain info API.
	 */
	private static class DefaultDomainCanFetchChecker {

		private final RestClient restClient;

		public DefaultDomainCanFetchChecker() {
			this.restClient = RestClient.builder().baseUrl(DOMAIN_SAFETY_CHECK_URL).build();
		}

		public DomainCanFetch check(String url, boolean failOpenOnError) {
			String domain;
			try {
				domain = URI.create(url).getHost();
				if (domain == null) {
					return new DomainCanFetch(url, false, "Could not extract domain from URL");
				}
			}
			catch (IllegalArgumentException e) {
				return new DomainCanFetch(url, false, "Invalid URL format: " + e.getMessage());
			}

			try {
				ResponseEntity<DomainSafetyResponse> response = this.checkDomainSafety(domain);

				if (!response.hasBody()) {
					return new DomainCanFetch(domain, false,
							"Failed to check domain safety. Status: " + response.getStatusCode());
				}

				DomainSafetyResponse body = response.getBody();
				if (body == null || body.can_fetch() != Boolean.TRUE) {
					return new DomainCanFetch(domain, false, "The domain is not safe to fetch content from.");
				}

				return new DomainCanFetch(domain, true, "Domain is safe to fetch.");
			}
			catch (Exception e) {
				logger.warn("Failed to check domain safety for {}: {}", domain, e.getMessage());
				// Use configurable fail-open/fail-closed behavior
				if (failOpenOnError) {
					return new DomainCanFetch(domain, true, "Safety check unavailable, proceeding with fetch.");
				}
				else {
					return new DomainCanFetch(domain, false,
							"Safety check failed: " + e.getMessage() + ". Blocking fetch for security.");
				}
			}
		}

		private record DomainSafetyResponse(String domain, Boolean can_fetch) {
		}

		private ResponseEntity<DomainSafetyResponse> checkDomainSafety(String domain) {
			return this.restClient.get()
				.uri(uriBuilder -> uriBuilder.queryParam("domain", domain).build())
				.retrieve()
				.toEntity(DomainSafetyResponse.class);
		}

	}

	/**
	 * Creates a new Builder instance with the required ChatClient.
	 * @param chatClient the ChatClient to use for summarization (required)
	 * @return a new Builder instance
	 * @throws IllegalArgumentException if chatClient is null
	 */
	public static Builder builder(ChatClient chatClient) {
		return new Builder(chatClient);
	}

	/**
	 * Builder class for creating SmartWebFetchTool instances.
	 */
	public static class Builder {

		private final ChatClient chatClient;

		private int maxContentLength = 100_000; // Default: 100 KB

		private boolean domainSafetyCheck = true;

		private int maxCacheSize = 100;

		private boolean failOpenOnSafetyCheckError = true;

		private int maxRetries = 2;

		/**
		 * Creates a new Builder with the required ChatClient.
		 * @param chatClient the ChatClient to use for summarization (required)
		 * @throws IllegalArgumentException if chatClient is null
		 */
		private Builder(ChatClient chatClient) {
			if (chatClient == null) {
				throw new IllegalArgumentException("ChatClient must not be null");
			}
			this.chatClient = chatClient;
		}

		/**
		 * Sets the maximum content length to process. Content longer than this will be
		 * truncated with a warning.
		 * @param maxContentLength the maximum content length in characters (must be
		 * positive)
		 * @return this Builder instance
		 * @throws IllegalArgumentException if maxContentLength is not positive
		 */
		public Builder maxContentLength(int maxContentLength) {
			if (maxContentLength <= 0) {
				throw new IllegalArgumentException("maxContentLength must be positive");
			}
			this.maxContentLength = maxContentLength;
			return this;
		}

		/**
		 * Sets whether to perform domain safety checks before fetching URLs.
		 * @param domainSafetyCheck true to enable domain safety checks, false to disable
		 * @return this Builder instance
		 */
		public Builder domainSafetyCheck(boolean domainSafetyCheck) {
			this.domainSafetyCheck = domainSafetyCheck;
			return this;
		}

		/**
		 * Sets the maximum number of entries to keep in the cache.
		 * @param maxCacheSize the maximum cache size (must be positive)
		 * @return this Builder instance
		 * @throws IllegalArgumentException if maxCacheSize is not positive
		 */
		public Builder maxCacheSize(int maxCacheSize) {
			if (maxCacheSize <= 0) {
				throw new IllegalArgumentException("maxCacheSize must be positive");
			}
			this.maxCacheSize = maxCacheSize;
			return this;
		}

		/**
		 * Sets whether to fail open (allow fetch) when domain safety check encounters an
		 * error. If set to false, will fail closed (block fetch) on safety check errors.
		 * @param failOpenOnSafetyCheckError true to allow fetch on safety check errors
		 * (default), false to block
		 * @return this Builder instance
		 */
		public Builder failOpenOnSafetyCheckError(boolean failOpenOnSafetyCheckError) {
			this.failOpenOnSafetyCheckError = failOpenOnSafetyCheckError;
			return this;
		}

		/**
		 * Sets the maximum number of retry attempts for transient network failures.
		 * @param maxRetries the maximum number of retries (must be non-negative, default
		 * is 2)
		 * @return this Builder instance
		 * @throws IllegalArgumentException if maxRetries is negative
		 */
		public Builder maxRetries(int maxRetries) {
			if (maxRetries < 0) {
				throw new IllegalArgumentException("maxRetries must be non-negative");
			}
			this.maxRetries = maxRetries;
			return this;
		}

		/**
		 * Builds and returns a new SmartWebFetchTool instance.
		 * @return a new SmartWebFetchTool instance
		 */
		public SmartWebFetchTool build() {
			return new SmartWebFetchTool(this.chatClient, this.maxContentLength, this.domainSafetyCheck,
					this.maxCacheSize, this.failOpenOnSafetyCheckError, this.maxRetries);
		}

	}

}
