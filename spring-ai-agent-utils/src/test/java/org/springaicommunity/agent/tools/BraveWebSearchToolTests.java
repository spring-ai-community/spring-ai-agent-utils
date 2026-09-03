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

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import org.springframework.ai.util.json.JsonParser;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Unit tests for {@link BraveWebSearchTool}.
 *
 * @author Christian Tzolov
 */
class BraveWebSearchToolTests {

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

		private static final String BRAVE_RESPONSE = """
				{
				  "web": {
				    "results": [
				      {
				        "title": "Spring AI",
				        "url": "https://spring.io/projects/spring-ai",
				        "description": "Spring AI project"
				      },
				      {
				        "title": "Spring AI Reference",
				        "url": "https://docs.spring.io/spring-ai/reference/",
				        "description": "Spring AI documentation"
				      },
				      {
				        "title": "Example",
				        "url": "https://example.com/spring-ai",
				        "description": "Unrelated result"
				      }
				    ]
				  }
				}
				""";

		private MockRestServiceServer server;

		private BraveWebSearchTool tool;

		@BeforeEach
		void setUp() {
			RestClient.Builder restClientBuilder = RestClient.builder();
			this.server = MockRestServiceServer.bindTo(restClientBuilder).build();
			this.tool = new BraveWebSearchTool("test-api-key", 10, restClientBuilder);
		}

		@AfterEach
		void verifyServer() {
			this.server.verify();
		}

		@Test
		@DisplayName("Should keep only results from allowed domains")
		void shouldKeepOnlyResultsFromAllowedDomains() {
			this.expectSearchRequest();

			String result = this.tool.webSearch("test query", List.of("spring.io"), null);

			assertThat(result).isEqualTo(JsonParser.toJson(List.of(
				new BraveWebSearchTool.SearchResult("Spring AI", "https://spring.io/projects/spring-ai",
					"Spring AI project"),
				new BraveWebSearchTool.SearchResult("Spring AI Reference",
					"https://docs.spring.io/spring-ai/reference/", "Spring AI documentation"))));
		}

		@Test
		@DisplayName("Should remove results from blocked domains")
		void shouldRemoveResultsFromBlockedDomains() {
			this.expectSearchRequest();

			String result = this.tool.webSearch("test query", null, List.of("example.com"));

			assertThat(result).isEqualTo(JsonParser.toJson(List.of(
				new BraveWebSearchTool.SearchResult("Spring AI", "https://spring.io/projects/spring-ai",
					"Spring AI project"),
				new BraveWebSearchTool.SearchResult("Spring AI Reference",
					"https://docs.spring.io/spring-ai/reference/", "Spring AI documentation"))));
		}

		@Test
		@DisplayName("Should handle empty domain lists")
		void shouldHandleEmptyDomainLists() {
			this.expectSearchRequest();

			String result = this.tool.webSearch("test query", Collections.emptyList(), Collections.emptyList());

			assertThat(result).isEqualTo(JsonParser.toJson(List.of(
				new BraveWebSearchTool.SearchResult("Spring AI", "https://spring.io/projects/spring-ai",
					"Spring AI project"),
				new BraveWebSearchTool.SearchResult("Spring AI Reference",
					"https://docs.spring.io/spring-ai/reference/", "Spring AI documentation"),
				new BraveWebSearchTool.SearchResult("Example", "https://example.com/spring-ai",
					"Unrelated result"))));
		}

		private void expectSearchRequest() {
			this.server.expect(request -> {
				assertThat(request.getMethod()).isEqualTo(HttpMethod.GET);
				assertThat(request.getHeaders().getFirst("X-Subscription-Token")).isEqualTo("test-api-key");
				assertThat(request.getHeaders().getAccept()).contains(MediaType.APPLICATION_JSON);
				assertThat(request.getURI().getScheme()).isEqualTo("https");
				assertThat(request.getURI().getHost()).isEqualTo("api.search.brave.com");
				assertThat(request.getURI().getPath()).isEqualTo("/res/v1/web/search");

				var queryParams = UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams();
				assertThat(UriUtils.decode(queryParams.getFirst("q"), StandardCharsets.UTF_8))
					.isEqualTo("test query");
				assertThat(queryParams.getFirst("count")).isEqualTo("10");
			})
				.andRespond(withSuccess(BRAVE_RESPONSE, MediaType.APPLICATION_JSON));
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
