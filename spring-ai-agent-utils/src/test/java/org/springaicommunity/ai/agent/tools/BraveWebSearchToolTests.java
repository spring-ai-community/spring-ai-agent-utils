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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.springframework.ai.util.json.JsonParser;

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

		@Test
		@DisplayName("Should handle null allowed domains")
		void shouldHandleNullAllowedDomains() {
			BraveWebSearchTool tool = BraveWebSearchTool.builder("test-api-key").build();

			// This should not throw an exception
			String result = tool.webSearch("test query", null, Collections.emptyList());
			assertThat(result).isNotNull();
		}

		@Test
		@DisplayName("Should handle null blocked domains")
		void shouldHandleNullBlockedDomains() {
			BraveWebSearchTool tool = BraveWebSearchTool.builder("test-api-key").build();

			// This should not throw an exception
			String result = tool.webSearch("test query", Collections.emptyList(), null);
			assertThat(result).isNotNull();
		}

		@Test
		@DisplayName("Should handle empty domain lists")
		void shouldHandleEmptyDomainLists() {
			BraveWebSearchTool tool = BraveWebSearchTool.builder("test-api-key").build();

			// This should not throw an exception
			String result = tool.webSearch("test query", Collections.emptyList(), Collections.emptyList());
			assertThat(result).isNotNull();
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
