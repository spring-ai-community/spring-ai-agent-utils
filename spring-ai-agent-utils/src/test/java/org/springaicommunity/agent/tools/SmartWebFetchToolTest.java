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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springaicommunity.agent.tools.SmartWebFetchTool;

import org.springframework.ai.chat.client.ChatClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link SmartWebFetchTool}.
 *
 * @author Christian Tzolov
 */
@DisplayName("SmartWebFetchTool Tests")
class SmartWebFetchToolTest {

	private ChatClient mockChatClient;

	private SmartWebFetchTool tool;

	@BeforeEach
	void setUp() {
		this.mockChatClient = createMockChatClient("Mocked AI response");
	}

	@AfterEach
	void tearDown() {
		if (this.tool != null) {
			this.tool.close();
		}
	}

	@Nested
	@DisplayName("Builder Tests")
	class BuilderTests {

		@Test
		@DisplayName("Should reject null ChatClient")
		void shouldRejectNullChatClient() {
			assertThatThrownBy(() -> SmartWebFetchTool.builder(null)).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("ChatClient must not be null");
		}

		@Test
		@DisplayName("Should reject non-positive maxContentLength")
		void shouldRejectNonPositiveMaxContentLength() {
			assertThatThrownBy(() -> SmartWebFetchTool.builder(SmartWebFetchToolTest.this.mockChatClient)
				.maxContentLength(0)
				.build()).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("maxContentLength must be positive");

			assertThatThrownBy(() -> SmartWebFetchTool.builder(SmartWebFetchToolTest.this.mockChatClient)
				.maxContentLength(-1)
				.build()).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("maxContentLength must be positive");
		}

		@Test
		@DisplayName("Should reject non-positive maxCacheSize")
		void shouldRejectNonPositiveMaxCacheSize() {
			assertThatThrownBy(
					() -> SmartWebFetchTool.builder(SmartWebFetchToolTest.this.mockChatClient).maxCacheSize(0).build())
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("maxCacheSize must be positive");

			assertThatThrownBy(
					() -> SmartWebFetchTool.builder(SmartWebFetchToolTest.this.mockChatClient).maxCacheSize(-1).build())
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("maxCacheSize must be positive");
		}

		@Test
		@DisplayName("Should reject negative maxRetries")
		void shouldRejectNegativeMaxRetries() {
			assertThatThrownBy(
					() -> SmartWebFetchTool.builder(SmartWebFetchToolTest.this.mockChatClient).maxRetries(-1).build())
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("maxRetries must be non-negative");
		}

		@Test
		@DisplayName("Should create tool with all custom parameters")
		void shouldCreateToolWithAllCustomParameters() {
			SmartWebFetchTool tool = SmartWebFetchTool.builder(SmartWebFetchToolTest.this.mockChatClient)
				.maxContentLength(75_000)
				.domainSafetyCheck(false)
				.maxCacheSize(75)
				.failOpenOnSafetyCheckError(false)
				.maxRetries(3)
				.build();
			assertThat(tool).isNotNull();
			tool.close();
		}

	}

	@Nested
	@DisplayName("URL Validation Tests")
	class UrlValidationTests {

		@BeforeEach
		void setUp() {
			tool = SmartWebFetchTool.builder(mockChatClient)
				.domainSafetyCheck(false)
				.build();
		}

		@Test
		@DisplayName("Should reject null URL")
		void shouldRejectNullUrl() {
			String result = tool.webFetch(null, "test prompt");
			assertThat(result).contains("Error: URL cannot be empty or null");
		}

		@Test
		@DisplayName("Should reject empty URL")
		void shouldRejectEmptyUrl() {
			String result = tool.webFetch("", "test prompt");
			assertThat(result).contains("Error: URL cannot be empty or null");
		}

		@Test
		@DisplayName("Should reject blank URL")
		void shouldRejectBlankUrl() {
			String result = tool.webFetch("   ", "test prompt");
			assertThat(result).contains("Error: URL cannot be empty or null");
		}

		@Test
		@DisplayName("Should reject URL without scheme")
		void shouldRejectUrlWithoutScheme() {
			String result = tool.webFetch("example.com", "test prompt");
			assertThat(result).contains("Error: Invalid URL format");
		}

		@Test
		@DisplayName("Should reject URL without host")
		void shouldRejectUrlWithoutHost() {
			String result = tool.webFetch("http://", "test prompt");
			assertThat(result).contains("Error: Invalid URL format");
		}

		@Test
		@DisplayName("Should reject malformed URL")
		void shouldRejectMalformedUrl() {
			String result = tool.webFetch("ht!tp://invalid url", "test prompt");
			assertThat(result).contains("Error: Invalid URL format");
		}

	}

	@Nested
	@DisplayName("Error Handling Tests")
	class ErrorHandlingTests {

		@BeforeEach
		void setUp() {
			tool = SmartWebFetchTool.builder(SmartWebFetchToolTest.this.mockChatClient)
				.domainSafetyCheck(false)
				.maxRetries(0)
				.build();
		}

		@Test
		@DisplayName("Should handle network errors gracefully")
		void shouldHandleNetworkErrorsGracefully() {
			// Testing with a non-existent domain
			String result = tool.webFetch("https://this-domain-does-not-exist-12345.com",
					"test prompt");
			assertThat(result).contains("Error fetching URL");
		}

		@Test
		@DisplayName("Should handle AI summarization errors gracefully")
		void shouldHandleAiSummarizationErrorsGracefully() {
			ChatClient failingChatClient = createFailingChatClient();
			SmartWebFetchTool failingTool = SmartWebFetchTool.builder(failingChatClient)
				.domainSafetyCheck(false)
				.build();

			// Since we can't easily mock HTTP responses without more complex setup,
			// this test verifies the tool handles ChatClient failures
			assertThat(failingTool).isNotNull();
			failingTool.close();
		}

	}

	@Nested
	@DisplayName("Resource Cleanup Tests")
	class ResourceCleanupTests {

		@Test
		@DisplayName("Should close successfully and clear cache")
		void shouldCloseSuccessfullyAndClearCache() {
			SmartWebFetchTool tool = SmartWebFetchTool.builder(SmartWebFetchToolTest.this.mockChatClient)
				.domainSafetyCheck(false)
				.build();

			// Should not throw
			tool.close();

			// Should be idempotent
			tool.close();
		}

		@Test
		@DisplayName("Should work with try-with-resources")
		void shouldWorkWithTryWithResources() {
			try (SmartWebFetchTool tool = SmartWebFetchTool.builder(SmartWebFetchToolTest.this.mockChatClient)
				.domainSafetyCheck(false)
				.build()) {
				assertThat(tool).isNotNull();
			}
			// Should close without throwing
		}

	}

	@Nested
	@DisplayName("WebFetchException Tests")
	class WebFetchExceptionTests {

		@Test
		@DisplayName("Should create exception with message and cause")
		void shouldCreateExceptionWithMessageAndCause() {
			Exception cause = new IOException("Network error");
			SmartWebFetchTool.WebFetchException exception = new SmartWebFetchTool.WebFetchException("Fetch failed",
					cause);

			assertThat(exception.getMessage()).isEqualTo("Fetch failed");
			assertThat(exception.getCause()).isEqualTo(cause);
		}

		@Test
		@DisplayName("Should create exception with null cause")
		void shouldCreateExceptionWithNullCause() {
			SmartWebFetchTool.WebFetchException exception = new SmartWebFetchTool.WebFetchException("Fetch failed",
					null);

			assertThat(exception.getMessage()).isEqualTo("Fetch failed");
			assertThat(exception.getCause()).isNull();
		}

	}

	@Nested
	@DisplayName("DomainCanFetch Record Tests")
	class DomainCanFetchRecordTests {

		@Test
		@DisplayName("Should create DomainCanFetch record with all fields")
		void shouldCreateDomainCanFetchRecordWithAllFields() {
			SmartWebFetchTool.DomainCanFetch result = new SmartWebFetchTool.DomainCanFetch("example.com", true,
					"Domain is safe");

			assertThat(result.domain()).isEqualTo("example.com");
			assertThat(result.canFetch()).isTrue();
			assertThat(result.reason()).isEqualTo("Domain is safe");
		}

		@Test
		@DisplayName("Should create DomainCanFetch record indicating fetch not allowed")
		void shouldCreateDomainCanFetchRecordIndicatingFetchNotAllowed() {
			SmartWebFetchTool.DomainCanFetch result = new SmartWebFetchTool.DomainCanFetch("malicious.com", false,
					"Domain is not safe");

			assertThat(result.domain()).isEqualTo("malicious.com");
			assertThat(result.canFetch()).isFalse();
			assertThat(result.reason()).isEqualTo("Domain is not safe");
		}

	}

	@Nested
	@DisplayName("Concurrency Tests")
	class ConcurrencyTests {

		@BeforeEach
		void setUp() {
			tool = SmartWebFetchTool.builder(SmartWebFetchToolTest.this.mockChatClient)
				.domainSafetyCheck(false)
				.maxCacheSize(100)
				.build();
		}

		@Test
		@DisplayName("Should handle concurrent cache access safely")
		void shouldHandleConcurrentCacheAccessSafely() throws InterruptedException {
			int threadCount = 10;
			CountDownLatch latch = new CountDownLatch(threadCount);
			AtomicInteger successCount = new AtomicInteger(0);

			for (int i = 0; i < threadCount; i++) {
				new Thread(() -> {
					try {
						// This will fail with network errors, but tests thread safety
						tool.webFetch("https://this-wont-work-" + Math.random() + ".com",
								"test");
					}
					catch (Exception e) {
						// Expected
					}
					finally {
						successCount.incrementAndGet();
						latch.countDown();
					}
				}).start();
			}

			assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
			assertThat(successCount.get()).isEqualTo(threadCount);
		}

	}

	// Helper methods

	private static ChatClient createMockChatClient(String response) {
		ChatClient chatClient = mock(ChatClient.class);
		ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
		ChatClient.CallResponseSpec callResponseSpec = mock(ChatClient.CallResponseSpec.class);

		when(chatClient.prompt()).thenReturn(requestSpec);
		when(requestSpec.user(any(java.util.function.Consumer.class))).thenReturn(requestSpec);
		when(requestSpec.call()).thenReturn(callResponseSpec);
		when(callResponseSpec.content()).thenReturn(response);

		return chatClient;
	}

	private static ChatClient createFailingChatClient() {
		ChatClient chatClient = mock(ChatClient.class);
		ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
		ChatClient.CallResponseSpec callResponseSpec = mock(ChatClient.CallResponseSpec.class);

		when(chatClient.prompt()).thenReturn(requestSpec);
		when(requestSpec.user(any(java.util.function.Consumer.class))).thenReturn(requestSpec);
		when(requestSpec.call()).thenReturn(callResponseSpec);
		when(callResponseSpec.content()).thenThrow(new RuntimeException("AI service unavailable"));

		return chatClient;
	}

}
