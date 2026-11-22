/*
 * Copyright 2026 - 2026 the original author or authors.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at https://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.springaicommunity.agent.tools;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.util.JsonHelper;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.Assert;
import org.springframework.web.client.RestClient;

/**
 * Optional Context.dev replacement for the WebSearch tool, returning query-relevant
 * Highlights as result descriptions. Register this tool or BraveWebSearchTool, not both.
 */
public class ContextWebSearchTool {

	private static final JsonHelper JSON = new JsonHelper();

	private static final int MAX_RESPONSE_BYTES = 5 * 1024 * 1024;

	private final RestClient restClient;

	private final int resultCount;

	private ContextWebSearchTool(String apiKey, int resultCount) {
		this(apiKey, resultCount, defaultClient());
	}

	ContextWebSearchTool(String apiKey, int resultCount, RestClient.Builder builder) {
		validateKey(apiKey);
		Assert.isTrue(resultCount >= 1 && resultCount <= 100, "resultCount must be between 1 and 100");
		this.restClient = builder.baseUrl("https://api.context.dev/v1")
			.defaultHeader("Authorization", "Bearer " + apiKey)
			.build();
		this.resultCount = resultCount;
	}

	private static RestClient.Builder defaultClient() {
		HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER)
			.connectTimeout(Duration.ofSeconds(10)).build();
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
		factory.setReadTimeout(Duration.ofSeconds(30));
		return RestClient.builder().requestFactory(factory);
	}

	@Tool(name = "WebSearch", description = "Search the web for current information and query-relevant source passages. Include relevant source URLs in your answer. Results contain titles, URLs and Highlights descriptions; unavailable page Highlights are omitted.")
	public String webSearch(
		@ToolParam(description = "The search query, between 1 and 500 characters") String query,
		@ToolParam(description = "Only include these domains", required = false) List<String> allowedDomains,
		@ToolParam(description = "Exclude these domains", required = false) List<String> blockedDomains) {
		Assert.isTrue(query != null && !query.isBlank() && query.codePointCount(0, query.length()) <= 500,
			"query must contain between 1 and 500 characters");
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("query", query);
		body.put("numResults", this.resultCount);
		body.put("highlightsOptions", Map.of("enabled", true, "maxCharacters", 1000));
		body.put("timeoutOpts", Map.of("milliseconds", 25000, "behavior", "fail"));
		addDomains(body, "includeDomains", allowedDomains);
		addDomains(body, "excludeDomains", blockedDomains);
		if (Thread.currentThread().isInterrupted()) {
			throw new IllegalStateException("Context search interrupted");
		}

		Response response;
		try {
			response = this.restClient.post().uri("/web/search").contentType(MediaType.APPLICATION_JSON)
				.body(body).exchange((request, result) -> {
					int status = result.getStatusCode().value();
					if (status != 200) {
						return new Response(status, null);
					}
					if (result.getHeaders().getContentLength() > MAX_RESPONSE_BYTES) {
						throw new IOException("Response too large");
					}
					byte[] raw = result.getBody().readNBytes(MAX_RESPONSE_BYTES + 1);
					if (raw.length > MAX_RESPONSE_BYTES) {
						throw new IOException("Response too large");
					}
					return new Response(status, new String(raw, StandardCharsets.UTF_8));
				});
		}
		catch (RuntimeException e) {
			// Tool listeners receive exception chains; never retain transport payloads.
			throw new IllegalStateException("Context search request failed");
		}
		if (response == null || response.status() != 200) {
			throw new IllegalStateException("Context search API returned HTTP " + (response == null ? "unknown" : response.status()));
		}
		try {
			return JSON.toJson(parseResults(response.body()));
		}
		catch (RuntimeException e) {
			throw new IllegalStateException("Context search returned an invalid, incomplete or unavailable Highlights response");
		}
	}

	private static void addDomains(Map<String, Object> body, String key, List<String> domains) {
		if (domains != null && !domains.isEmpty()) {
			Assert.isTrue(domains.size() <= 100 && domains.stream().allMatch(d -> d != null && !d.isBlank() && d.length() >= 3),
				"domain filters must contain at most 100 nonempty domains");
			body.put(key, domains);
		}
	}

	private List<SearchResult> parseResults(String raw) {
		Map<?, ?> data = JSON.fromJson(raw, Map.class);
		Assert.isTrue(data != null && (!data.containsKey("partial") || Boolean.FALSE.equals(data.get("partial"))),
			"Incomplete response");
		Assert.isTrue(data.get("results") instanceof List<?>, "Invalid results");
		List<?> rows = (List<?>) data.get("results");
		List<SearchResult> results = new ArrayList<>();
		for (Object row : rows) {
			Assert.isTrue(row instanceof Map<?, ?>, "Invalid result");
			Map<?, ?> item = (Map<?, ?>) row;
			Assert.isTrue(item.get("title") instanceof String && !((String) item.get("title")).isBlank()
				&& item.get("url") instanceof String, "Invalid source");
			URI url = URI.create((String) item.get("url"));
			Assert.isTrue(("https".equals(url.getScheme()) || "http".equals(url.getScheme()))
				&& url.getHost() != null && url.getRawUserInfo() == null, "Invalid source URL");
			if (item.get("highlights") == null) {
				continue;
			}
			Assert.isTrue(item.get("highlights") instanceof Map<?, ?>, "Invalid Highlights");
			Map<?, ?> highlights = (Map<?, ?>) item.get("highlights");
			if (!"SUCCESS".equals(highlights.get("code"))) {
				Assert.isTrue(Set.of("NOT_REQUESTED", "TIMEOUT", "CONTENT_TOO_LARGE", "WEBSITE_ACCESS_ERROR", "WEBSITE_BLOCKED", "ERROR")
					.contains(highlights.get("code")), "Invalid Highlights status");
				continue;
			}
			Assert.isTrue(highlights.get("highlights") instanceof List<?>, "Invalid Highlights");
			List<?> passages = (List<?>) highlights.get("highlights");
			Assert.isTrue(passages.stream().allMatch(String.class::isInstance), "Invalid passage");
			String description = String.join("\n", passages.stream().map(p -> ((String) p).strip()).filter(p -> !p.isEmpty()).toList());
			if (!description.isEmpty()) {
				results.add(new SearchResult((String) item.get("title"), url.toString(), description));
			}
			if (results.size() == this.resultCount) {
				break;
			}
		}
		Assert.isTrue(rows.isEmpty() || !results.isEmpty(), "No usable Highlights");
		return results;
	}

	public record SearchResult(String title, String url, String description) {
	}

	private record Response(int status, String body) {
	}

	private static void validateKey(String key) {
		Assert.isTrue(key != null && !key.isBlank() && key.chars().allMatch(c -> c >= 33 && c <= 126),
			"API key must contain printable ASCII characters without spaces");
	}

	public static Builder builder(String apiKey) {
		return new Builder(apiKey);
	}

	public static class Builder {

		private final String apiKey;

		private int resultCount = 10;

		private Builder(String apiKey) {
			validateKey(apiKey);
			this.apiKey = apiKey;
		}

		public Builder resultCount(int resultCount) {
			Assert.isTrue(resultCount >= 1 && resultCount <= 100, "resultCount must be between 1 and 100");
			this.resultCount = resultCount;
			return this;
		}

		public ContextWebSearchTool build() {
			return new ContextWebSearchTool(this.apiKey, this.resultCount);
		}

	}

}
