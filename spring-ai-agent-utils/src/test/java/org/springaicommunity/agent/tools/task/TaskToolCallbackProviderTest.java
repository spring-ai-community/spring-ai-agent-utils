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
package org.springaicommunity.agent.tools.task;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link TaskToolCallbackProvider}.
 *
 * @author Christian Tzolov
 */
class TaskToolCallbackProviderTest {

	private ChatClient.Builder chatClientBuilder;

	@BeforeEach
	void setUp() {
		ChatModel mockChatModel = mock(ChatModel.class);
		this.chatClientBuilder = ChatClient.builder(mockChatModel);
	}

	@Test
	void shouldFailWhenNoDefaultChatClientBuilder() {
		assertThatThrownBy(() -> TaskToolCallbackProvider.builder()
			.chatClientBuilder("sonnet", chatClientBuilder)
			.build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("default");
	}

	@Test
	void shouldBuildWithDefaultChatClientBuilder() {
		TaskToolCallbackProvider provider = TaskToolCallbackProvider.builder()
			.chatClientBuilder("default", chatClientBuilder)
			.build();

		assertThat(provider).isNotNull();
		assertThat(provider.getToolCallbacks()).hasSize(2);
	}

	@Test
	void shouldProvideTaskAndTaskOutputTools() {
		TaskToolCallbackProvider provider = TaskToolCallbackProvider.builder()
			.chatClientBuilder("default", chatClientBuilder)
			.build();

		ToolCallback[] callbacks = provider.getToolCallbacks();

		assertThat(callbacks).extracting(tc -> tc.getToolDefinition().name()).containsExactlyInAnyOrder("Task",
				"TaskOutput");
	}

	@Test
	void shouldAcceptMultipleChatClientBuilders() {
		ChatModel anotherMock = mock(ChatModel.class);
		ChatClient.Builder opusBuilder = ChatClient.builder(anotherMock);

		TaskToolCallbackProvider provider = TaskToolCallbackProvider.builder()
			.chatClientBuilder("default", chatClientBuilder)
			.chatClientBuilder("opus", opusBuilder)
			.build();

		assertThat(provider.getToolCallbacks()).hasSize(2);
	}

	@Test
	void shouldAcceptChatClientBuildersAsMap() {
		ChatModel anotherMock = mock(ChatModel.class);
		ChatClient.Builder opusBuilder = ChatClient.builder(anotherMock);

		TaskToolCallbackProvider provider = TaskToolCallbackProvider.builder()
			.chatClientBuilders(Map.of("default", chatClientBuilder, "opus", opusBuilder))
			.build();

		assertThat(provider.getToolCallbacks()).hasSize(2);
	}

	@Test
	void shouldReturnDefensiveCopyOfToolCallbacks() {
		TaskToolCallbackProvider provider = TaskToolCallbackProvider.builder()
			.chatClientBuilder("default", chatClientBuilder)
			.build();

		ToolCallback[] callbacks1 = provider.getToolCallbacks();
		ToolCallback[] callbacks2 = provider.getToolCallbacks();

		// Should return different array instances (defensive copy)
		assertThat(callbacks1).isNotSameAs(callbacks2);
		// But with same content
		assertThat(callbacks1).containsExactly(callbacks2);
	}

	@Test
	void shouldBeLazyInitialized() {
		TaskToolCallbackProvider provider = TaskToolCallbackProvider.builder()
			.chatClientBuilder("default", chatClientBuilder)
			.build();

		// First call initializes
		ToolCallback[] callbacks1 = provider.getToolCallbacks();
		// Second call returns same underlying data
		ToolCallback[] callbacks2 = provider.getToolCallbacks();

		assertThat(callbacks1).hasSize(2);
		assertThat(callbacks2).hasSize(2);
	}

	@Test
	void shouldBeThreadSafeOnConcurrentAccess() throws InterruptedException {
		TaskToolCallbackProvider provider = TaskToolCallbackProvider.builder()
			.chatClientBuilder("default", chatClientBuilder)
			.build();

		int threadCount = 10;
		CountDownLatch startLatch = new CountDownLatch(1);
		CountDownLatch doneLatch = new CountDownLatch(threadCount);
		AtomicReference<Throwable> error = new AtomicReference<>();

		ExecutorService executor = Executors.newFixedThreadPool(threadCount);
		for (int i = 0; i < threadCount; i++) {
			executor.submit(() -> {
				try {
					startLatch.await();
					ToolCallback[] callbacks = provider.getToolCallbacks();
					assertThat(callbacks).hasSize(2);
				}
				catch (Throwable t) {
					error.compareAndSet(null, t);
				}
				finally {
					doneLatch.countDown();
				}
			});
		}

		// Start all threads simultaneously
		startLatch.countDown();
		doneLatch.await();
		executor.shutdown();

		assertThat(error.get()).isNull();
	}

}
