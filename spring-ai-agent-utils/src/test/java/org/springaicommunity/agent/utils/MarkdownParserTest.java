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
package org.springaicommunity.agent.utils;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springaicommunity.agent.utils.MarkdownParser;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link MarkdownParser}.
 *
 * @author Christian Tzolov
 */
@DisplayName("MarkdownParser Tests")
class MarkdownParserTest {

	@Nested
	@DisplayName("Front Matter Parsing Tests")
	class FrontMatterParsingTests {

		@Test
		@DisplayName("Should parse front matter with double quotes")
		void shouldParseFrontMatterWithDoubleQuotes() {
			String markdown = """
					---
					title: "My Blog Post"
					author: "John Doe"
					date: "2024-01-15"
					---

					# Content
					""";

			MarkdownParser parser = new MarkdownParser(markdown);
			Map<String, String> frontMatter = parser.getFrontMatter();

			assertThat(frontMatter).hasSize(3)
				.containsEntry("title", "My Blog Post")
				.containsEntry("author", "John Doe")
				.containsEntry("date", "2024-01-15");
		}

		@Test
		@DisplayName("Should parse front matter with single quotes")
		void shouldParseFrontMatterWithSingleQuotes() {
			String markdown = """
					---
					title: 'My Blog Post'
					author: 'John Doe'
					---

					Content here
					""";

			MarkdownParser parser = new MarkdownParser(markdown);
			Map<String, String> frontMatter = parser.getFrontMatter();

			assertThat(frontMatter).hasSize(2)
				.containsEntry("title", "My Blog Post")
				.containsEntry("author", "John Doe");
		}

		@Test
		@DisplayName("Should parse front matter without quotes")
		void shouldParseFrontMatterWithoutQuotes() {
			String markdown = """
					---
					title: My Blog Post
					author: John Doe
					year: 2024
					---

					Content
					""";

			MarkdownParser parser = new MarkdownParser(markdown);
			Map<String, String> frontMatter = parser.getFrontMatter();

			assertThat(frontMatter).hasSize(3)
				.containsEntry("title", "My Blog Post")
				.containsEntry("author", "John Doe")
				.containsEntry("year", "2024");
		}

		@Test
		@DisplayName("Should parse front matter with mixed quote styles")
		void shouldParseFrontMatterWithMixedQuotes() {
			String markdown = """
					---
					title: "My Blog Post"
					author: 'John Doe'
					category: Technology
					---

					# Heading
					""";

			MarkdownParser parser = new MarkdownParser(markdown);
			Map<String, String> frontMatter = parser.getFrontMatter();

			assertThat(frontMatter).hasSize(3)
				.containsEntry("title", "My Blog Post")
				.containsEntry("author", "John Doe")
				.containsEntry("category", "Technology");
		}

		@Test
		@DisplayName("Should handle front matter with empty lines")
		void shouldHandleFrontMatterWithEmptyLines() {
			String markdown = """
					---
					title: My Title

					author: John Doe

					date: 2024-01-15
					---

					Content
					""";

			MarkdownParser parser = new MarkdownParser(markdown);
			Map<String, String> frontMatter = parser.getFrontMatter();

			assertThat(frontMatter).hasSize(3)
				.containsEntry("title", "My Title")
				.containsEntry("author", "John Doe")
				.containsEntry("date", "2024-01-15");
		}

		@Test
		@DisplayName("Should handle front matter values with colons")
		void shouldHandleFrontMatterValuesWithColons() {
			String markdown = """
					---
					title: Introduction: A Deep Dive
					time: 10:30 AM
					url: https://example.com
					---

					Content
					""";

			MarkdownParser parser = new MarkdownParser(markdown);
			Map<String, String> frontMatter = parser.getFrontMatter();

			assertThat(frontMatter).hasSize(3)
				.containsEntry("title", "Introduction: A Deep Dive")
				.containsEntry("time", "10:30 AM")
				.containsEntry("url", "https://example.com");
		}

		@Test
		@DisplayName("Should skip lines without colons in front matter")
		void shouldSkipLinesWithoutColons() {
			String markdown = """
					---
					title: My Title
					This line has no colon
					author: John Doe
					---

					Content
					""";

			MarkdownParser parser = new MarkdownParser(markdown);
			Map<String, String> frontMatter = parser.getFrontMatter();

			assertThat(frontMatter).hasSize(2)
				.containsEntry("title", "My Title")
				.containsEntry("author", "John Doe")
				.doesNotContainKey("This line has no colon");
		}

		@Test
		@DisplayName("Should handle front matter with spaces around keys and values")
		void shouldHandleSpacesAroundKeysAndValues() {
			String markdown = """
					---
					  title  :  My Title
					  author  :  John Doe
					---

					Content
					""";

			MarkdownParser parser = new MarkdownParser(markdown);
			Map<String, String> frontMatter = parser.getFrontMatter();

			assertThat(frontMatter).hasSize(2)
				.containsEntry("title", "My Title")
				.containsEntry("author", "John Doe");
		}
	}

	@Nested
	@DisplayName("Content Extraction Tests")
	class ContentExtractionTests {

		@Test
		@DisplayName("Should extract content after front matter")
		void shouldExtractContentAfterFrontMatter() {
			String markdown = """
					---
					title: My Post
					---

					# Welcome

					This is the content.
					""";

			MarkdownParser parser = new MarkdownParser(markdown);
			String content = parser.getContent();

			assertThat(content).isEqualTo("# Welcome\n\nThis is the content.");
		}

		@Test
		@DisplayName("Should trim whitespace from content")
		void shouldTrimWhitespaceFromContent() {
			String markdown = """
					---
					title: My Post
					---


					Content here


					""";

			MarkdownParser parser = new MarkdownParser(markdown);
			String content = parser.getContent();

			assertThat(content).isEqualTo("Content here");
		}

		@Test
		@DisplayName("Should handle content with markdown formatting")
		void shouldHandleContentWithMarkdownFormatting() {
			String markdown = """
					---
					title: My Post
					---

					# Heading 1

					This is **bold** and *italic* text.

					- Item 1
					- Item 2

					```java
					System.out.println("Hello");
					```
					""";

			MarkdownParser parser = new MarkdownParser(markdown);
			String content = parser.getContent();

			assertThat(content).contains("# Heading 1")
				.contains("**bold**")
				.contains("*italic*")
				.contains("- Item 1")
				.contains("```java");
		}
	}

	@Nested
	@DisplayName("Document Without Front Matter Tests")
	class DocumentWithoutFrontMatterTests {

		@Test
		@DisplayName("Should treat entire document as content when no front matter")
		void shouldTreatEntireDocumentAsContentWhenNoFrontMatter() {
			String markdown = """
					# My Document

					This is content without front matter.
					""";

			MarkdownParser parser = new MarkdownParser(markdown);

			assertThat(parser.getFrontMatter()).isEmpty();
			assertThat(parser.getContent()).isEqualTo(markdown);
		}

		@Test
		@DisplayName("Should handle document starting with --- but no closing delimiter")
		void shouldHandleDocumentWithOpeningDelimiterOnly() {
			String markdown = """
					---
					This looks like front matter
					But there's no closing delimiter

					# Content
					""";

			MarkdownParser parser = new MarkdownParser(markdown);

			// Should treat entire document as content when no closing delimiter
			assertThat(parser.getFrontMatter()).isEmpty();
			assertThat(parser.getContent()).isEqualTo(markdown);
		}

		@Test
		@DisplayName("Should handle --- appearing in content")
		void shouldHandleDashesInContent() {
			String markdown = """
					# My Document

					This is content.

					---

					This is a horizontal rule.
					""";

			MarkdownParser parser = new MarkdownParser(markdown);

			assertThat(parser.getFrontMatter()).isEmpty();
			assertThat(parser.getContent()).isEqualTo(markdown);
		}
	}

	@Nested
	@DisplayName("Edge Cases Tests")
	class EdgeCasesTests {

		@Test
		@DisplayName("Should handle null input")
		void shouldHandleNullInput() {
			MarkdownParser parser = new MarkdownParser(null);

			assertThat(parser.getFrontMatter()).isEmpty();
			assertThat(parser.getContent()).isEmpty();
		}

		@Test
		@DisplayName("Should handle empty string input")
		void shouldHandleEmptyStringInput() {
			MarkdownParser parser = new MarkdownParser("");

			assertThat(parser.getFrontMatter()).isEmpty();
			assertThat(parser.getContent()).isEmpty();
		}

		@Test
		@DisplayName("Should handle front matter only with no content")
		void shouldHandleFrontMatterOnlyWithNoContent() {
			String markdown = """
					---
					title: My Post
					author: John Doe
					---
					""";

			MarkdownParser parser = new MarkdownParser(markdown);
			Map<String, String> frontMatter = parser.getFrontMatter();

			assertThat(frontMatter).hasSize(2)
				.containsEntry("title", "My Post")
				.containsEntry("author", "John Doe");
			assertThat(parser.getContent()).isEmpty();
		}

		@Test
		@DisplayName("Should handle empty front matter section")
		void shouldHandleEmptyFrontMatterSection() {
			String markdown = """
					---
					---

					# Content
					""";

			MarkdownParser parser = new MarkdownParser(markdown);

			assertThat(parser.getFrontMatter()).isEmpty();
			assertThat(parser.getContent()).isEqualTo("# Content");
		}

		@Test
		@DisplayName("Should handle front matter with only whitespace")
		void shouldHandleFrontMatterWithOnlyWhitespace() {
			String markdown = """
					---


					---

					Content here
					""";

			MarkdownParser parser = new MarkdownParser(markdown);

			assertThat(parser.getFrontMatter()).isEmpty();
			assertThat(parser.getContent()).isEqualTo("Content here");
		}

		@Test
		@DisplayName("Should handle value with only one quote character")
		void shouldHandleValueWithOneQuoteCharacter() {
			String markdown = """
					---
					title: "
					author: '
					---

					Content
					""";

			MarkdownParser parser = new MarkdownParser(markdown);
			Map<String, String> frontMatter = parser.getFrontMatter();

			// Single quote characters should not be removed
			assertThat(frontMatter).containsEntry("title", "\"").containsEntry("author", "'");
		}

		@Test
		@DisplayName("Should handle mismatched quotes")
		void shouldHandleMismatchedQuotes() {
			String markdown = """
					---
					title: "My Title'
					author: 'John Doe"
					---

					Content
					""";

			MarkdownParser parser = new MarkdownParser(markdown);
			Map<String, String> frontMatter = parser.getFrontMatter();

			// Mismatched quotes should not be removed
			assertThat(frontMatter).containsEntry("title", "\"My Title'")
				.containsEntry("author", "'John Doe\"");
		}

		@Test
		@DisplayName("Should handle empty key or value")
		void shouldHandleEmptyKeyOrValue() {
			String markdown = """
					---
					: value without key
					key without value:
					:
					---

					Content
					""";

			MarkdownParser parser = new MarkdownParser(markdown);
			Map<String, String> frontMatter = parser.getFrontMatter();

			// Empty keys should be skipped (colonIndex > 0 check)
			// Empty values should be stored as empty strings
			assertThat(frontMatter).containsEntry("key without value", "");
		}
	}

	@Nested
	@DisplayName("Immutability Tests")
	class ImmutabilityTests {

		@Test
		@DisplayName("Should return a copy of front matter map")
		void shouldReturnCopyOfFrontMatterMap() {
			String markdown = """
					---
					title: My Post
					---

					Content
					""";

			MarkdownParser parser = new MarkdownParser(markdown);
			Map<String, String> frontMatter1 = parser.getFrontMatter();
			Map<String, String> frontMatter2 = parser.getFrontMatter();

			// Should be equal but not the same instance
			assertThat(frontMatter1).isEqualTo(frontMatter2).isNotSameAs(frontMatter2);

			// Modifying returned map should not affect subsequent calls
			frontMatter1.put("new-key", "new-value");
			Map<String, String> frontMatter3 = parser.getFrontMatter();

			assertThat(frontMatter3).doesNotContainKey("new-key").hasSize(1);
		}
	}

	@Nested
	@DisplayName("Complex Real-World Examples")
	class RealWorldExamplesTests {

		@Test
		@DisplayName("Should parse blog post with comprehensive front matter")
		void shouldParseBlogPostWithComprehensiveFrontMatter() {
			String markdown = """
					---
					title: "Understanding Spring AI"
					author: John Doe
					date: 2024-01-15
					tags: "spring, ai, machine-learning"
					category: Tutorial
					published: true
					---

					# Introduction to Spring AI

					Spring AI provides a powerful abstraction layer for working with AI models.

					## Key Features

					- Easy integration
					- Multiple model support
					- Flexible configuration
					""";

			MarkdownParser parser = new MarkdownParser(markdown);
			Map<String, String> frontMatter = parser.getFrontMatter();

			assertThat(frontMatter).hasSize(6)
				.containsEntry("title", "Understanding Spring AI")
				.containsEntry("author", "John Doe")
				.containsEntry("date", "2024-01-15")
				.containsEntry("tags", "spring, ai, machine-learning")
				.containsEntry("category", "Tutorial")
				.containsEntry("published", "true");

			assertThat(parser.getContent()).startsWith("# Introduction to Spring AI")
				.contains("## Key Features")
				.contains("- Easy integration");
		}

		@Test
		@DisplayName("Should parse documentation with metadata")
		void shouldParseDocumentationWithMetadata() {
			String markdown = """
					---
					version: 1.0.0
					last-updated: 2024-01-15
					status: draft
					reviewers: "Alice, Bob, Charlie"
					---

					# API Documentation

					## Overview

					This document describes the API endpoints.
					""";

			MarkdownParser parser = new MarkdownParser(markdown);
			Map<String, String> frontMatter = parser.getFrontMatter();

			assertThat(frontMatter).hasSize(4)
				.containsEntry("version", "1.0.0")
				.containsEntry("last-updated", "2024-01-15")
				.containsEntry("status", "draft")
				.containsEntry("reviewers", "Alice, Bob, Charlie");
		}
	}

}
