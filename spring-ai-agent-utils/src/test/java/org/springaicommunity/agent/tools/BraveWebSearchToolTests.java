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

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import org.springframework.ai.util.json.JsonParser;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestToUriTemplate;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Unit tests for {@link BraveWebSearchTool}.
 *
 * @author Christian Tzolov
 */
class BraveWebSearchToolTests {

	/**
	 * Canned Brave API response with results from three distinct domains:
	 * spring.io, baeldung.com, and example.com. Used by DomainFilteringTests
	 * to verify client-side include/exclude logic without hitting the network.
	 */
	private static final String CANNED_RESPONSE = """
			{
			  "web": {
			    "results": [
			      {
			        "title": "Spring IO",
			        "url": "https://spring.io/blog/spring-ai",
			        "description": "Official Spring blog"
			      },
			      {
			        "title": "Baeldung Spring AI",
			        "url": "https://baeldung.com/spring-ai",
			        "description": "Baeldung tutorial"
			      },
			      {
			        "title": "Example Site",
			        "url": "https://example.com/java",
			        "description": "Example domain"
			      }
			    ]
			  }
			}
			""";

	/**
	 * Creates a BraveWebSearchTool wired to a MockRestServiceServer that returns
	 * {@code CANNED_RESPONSE} for any request to the Brave web search path.
	 * <p>
	 * How it works: we hand a {@code RestClient.Builder} to both the mock server and
	 * the tool. The mock server intercepts every HTTP call the builder's client makes
	 * and returns the programmed canned response — no network required.
	 */
	private BraveWebSearchTool toolWithMock() {
		RestClient.Builder builder = RestClient.builder()
				.baseUrl("https://api.search.brave.com");
		MockRestServiceServer mockServer = MockRestServiceServer.bindTo(builder).build();
		mockServer.expect(requestToUriTemplate("https://api.search.brave.com/res/v1/web/search?q={q}&count={count}",
				"test query", 10))
			.andRespond(withSuccess(CANNED_RESPONSE, MediaType.APPLICATION_JSON));
		return new BraveWebSearchTool(builder, 10);
	}

	@Nested
	@DisplayName("Builder Tests")
	class BuilderTests {

		@Test
		@DisplayName("Should create tool with valid API key")
		void shouldCreateToolWithValidApiKey() {
			BraveWebSearchTool tool = BraveWebSearchTool.builder("test-api-key").build();
			assertThat(tool).isNotNull();
		}

		@Test
		@DisplayName("Should throw exception when API key is null")
		void shouldThrowExceptionWhenApiKeyIsNull() {
			assertThatThrownBy(() -> BraveWebSearchTool.builder(null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("API key must not be null or empty");
		}

		@Test
		@DisplayName("Should throw exception when API key is empty")
		void shouldThrowExceptionWhenApiKeyIsEmpty() {
			assertThatThrownBy(() -> BraveWebSearchTool.builder(""))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("API key must not be null or empty");
		}

		@Test
		@DisplayName("Should throw exception when API key is whitespace")
		void shouldThrowExceptionWhenApiKeyIsWhitespace() {
			assertThatThrownBy(() -> BraveWebSearchTool.builder("   "))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("API key must not be null or empty");
		}

		@Test
		@DisplayName("Should set custom result count")
		void shouldSetCustomResultCount() {
			BraveWebSearchTool tool = BraveWebSearchTool.builder("test-api-key")
				.resultCount(15)
				.build();
			assertThat(tool).isNotNull();
		}

		@Test
		@DisplayName("Should throw exception for zero result count")
		void shouldThrowExceptionForZeroResultCount() {
			assertThatThrownBy(() -> BraveWebSearchTool.builder("test-api-key").resultCount(0))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("resultCount must be positive");
		}

		@Test
		@DisplayName("Should throw exception for negative result count")
		void shouldThrowExceptionForNegativeResultCount() {
			assertThatThrownBy(() -> BraveWebSearchTool.builder("test-api-key").resultCount(-1))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("resultCount must be positive");
		}

	}

	@Nested
	@DisplayName("Domain Filtering Tests")
	class DomainFilteringTests {

		@Test
		@DisplayName("No filters — all three results returned")
		void noFilters_returnsAllResults() {
			BraveWebSearchTool tool = toolWithMock();
			String result = tool.webSearch("test query", null, null);

			assertThat(result).contains("spring.io");
			assertThat(result).contains("baeldung.com");
			assertThat(result).contains("example.com");
		}

		@Test
		@DisplayName("Allowed domains — only matching domain kept")
		void allowedDomains_keepsOnlyMatchingDomain() {
			BraveWebSearchTool tool = toolWithMock();
			String result = tool.webSearch("test query", List.of("spring.io"), null);

			assertThat(result).contains("spring.io");
			assertThat(result).doesNotContain("baeldung.com");
			assertThat(result).doesNotContain("example.com");
		}

		@Test
		@DisplayName("Blocked domains — matching domain excluded")
		void blockedDomains_excludesMatchingDomain() {
			BraveWebSearchTool tool = toolWithMock();
			String result = tool.webSearch("test query", null, List.of("example.com"));

			assertThat(result).contains("spring.io");
			assertThat(result).contains("baeldung.com");
			assertThat(result).doesNotContain("example.com");
		}

		@Test
		@DisplayName("Combined allowed + blocked — allowed wins, blocked excluded")
		void combinedFilters_appliesBothRules() {
			BraveWebSearchTool tool = toolWithMock();
			// Allow spring.io and baeldung.com, but also block baeldung.com
			// Result: only spring.io survives (allowed list applied first, then block)
			String result = tool.webSearch("test query",
					List.of("spring.io", "baeldung.com"),
					List.of("baeldung.com"));

			assertThat(result).contains("spring.io");
			assertThat(result).doesNotContain("baeldung.com");
			assertThat(result).doesNotContain("example.com");
		}

		@Test
		@DisplayName("Subdomain matching — subdomain kept when parent domain is allowed")
		void subdomainMatching_subdomainMatchesParentAllowedDomain() {
			// Build a tool whose mock returns a subdomain URL
			RestClient.Builder builder = RestClient.builder()
					.baseUrl("https://api.search.brave.com");
			MockRestServiceServer mockServer = MockRestServiceServer.bindTo(builder).build();
			mockServer.expect(requestToUriTemplate(
					"https://api.search.brave.com/res/v1/web/search?q={q}&count={count}",
					"test query", 10))
				.andRespond(withSuccess("""
						{
						  "web": {
						    "results": [
						      {
						        "title": "Spring Docs",
						        "url": "https://docs.spring.io/spring-ai/reference/",
						        "description": "Spring AI docs"
						      },
						      {
						        "title": "Other",
						        "url": "https://other.com/page",
						        "description": "Other site"
						      }
						    ]
						  }
						}
						""", MediaType.APPLICATION_JSON));

			BraveWebSearchTool tool = new BraveWebSearchTool(builder, 10);
			// Allow the parent domain — subdomain should match
			String result = tool.webSearch("test query", List.of("spring.io"), null);

			assertThat(result).contains("docs.spring.io");
			assertThat(result).doesNotContain("other.com");
		}

		@Test
		@DisplayName("Empty domain lists — all results returned without network hang")
		void emptyDomainLists_returnsAllResults() {
			BraveWebSearchTool tool = toolWithMock();
			String result = tool.webSearch("test query", Collections.emptyList(), Collections.emptyList());

			assertThat(result).contains("spring.io");
			assertThat(result).contains("baeldung.com");
			assertThat(result).contains("example.com");
		}

	}

	@Nested
	@DisplayName("Search Query Tests")
	class SearchQueryTests {

		@Test
		@DisplayName("Should return empty list for null query")
		void shouldReturnEmptyListForNullQuery() {
			BraveWebSearchTool tool = BraveWebSearchTool.builder("test-api-key").build();

			String result = tool.webSearch(null, null, null);

			assertThat(result).isEqualTo(JsonParser.toJson(Collections.emptyList()));
		}

		@Test
		@DisplayName("Should return empty list for empty query")
		void shouldReturnEmptyListForEmptyQuery() {
			BraveWebSearchTool tool = BraveWebSearchTool.builder("test-api-key").build();

			String result = tool.webSearch("", null, null);

			assertThat(result).isEqualTo(JsonParser.toJson(Collections.emptyList()));
		}

		@Test
		@DisplayName("Should return empty list for whitespace query")
		void shouldReturnEmptyListForWhitespaceQuery() {
			BraveWebSearchTool tool = BraveWebSearchTool.builder("test-api-key").build();

			String result = tool.webSearch("   ", null, null);

			assertThat(result).isEqualTo(JsonParser.toJson(Collections.emptyList()));
		}

	}

	@Nested
	@DisplayName("SearchResult Record Tests")
	class SearchResultTests {

		@Test
		@DisplayName("Should create SearchResult with all fields")
		void shouldCreateSearchResultWithAllFields() {
			BraveWebSearchTool.SearchResult result = new BraveWebSearchTool.SearchResult(
				"Test Title",
				"https://example.com",
				"Test Description"
			);

			assertThat(result.title()).isEqualTo("Test Title");
			assertThat(result.url()).isEqualTo("https://example.com");
			assertThat(result.description()).isEqualTo("Test Description");
		}

		@Test
		@DisplayName("Should handle null description")
		void shouldHandleNullDescription() {
			BraveWebSearchTool.SearchResult result = new BraveWebSearchTool.SearchResult(
				"Test Title",
				"https://example.com",
				null
			);

			assertThat(result.title()).isEqualTo("Test Title");
			assertThat(result.url()).isEqualTo("https://example.com");
			assertThat(result.description()).isNull();
		}

		@Test
		@DisplayName("Should support record equality")
		void shouldSupportRecordEquality() {
			BraveWebSearchTool.SearchResult result1 = new BraveWebSearchTool.SearchResult(
				"Test Title",
				"https://example.com",
				"Test Description"
			);

			BraveWebSearchTool.SearchResult result2 = new BraveWebSearchTool.SearchResult(
				"Test Title",
				"https://example.com",
				"Test Description"
			);

			assertThat(result1).isEqualTo(result2);
			assertThat(result1.hashCode()).isEqualTo(result2.hashCode());
		}

	}

}
