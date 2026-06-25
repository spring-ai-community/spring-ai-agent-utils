/*
 * Copyright 2026 - 2026 the original author or authors.
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
package org.springaicommunity.agent.tools.task.claude;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springaicommunity.agent.common.task.subagent.SubagentReference;
import org.springaicommunity.agent.common.task.subagent.SubagentType;
import org.springaicommunity.agent.common.task.subagent.TaskCall;
import org.springaicommunity.agent.tools.task.claude.ClaudeSubagentDefinition;
import org.springaicommunity.agent.tools.task.claude.ClaudeSubagentExecutor;
import org.springaicommunity.agent.tools.task.claude.ClaudeSubagentResolver;
import org.springaicommunity.agent.tools.task.claude.ClaudeSubagentType;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link ClaudeSubagentType}.
 *
 * @author Christian Tzolov
 */
class ClaudeSubagentTypeTest {

	private final ChatClient.Builder defaultBuilder = ChatClient.builder(mock(ChatModel.class));

	@Test
	void shouldFailWithoutChatClientBuilder() {
		assertThatThrownBy(() -> ClaudeSubagentType.builder().build()).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void shouldFailWithoutDefaultChatClientBuilder() {
		assertThatThrownBy(() -> ClaudeSubagentType.builder()
			.chatClientBuilder("opus", ChatClient.builder(mock(ChatModel.class)))
			.build()).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("default");
	}

	@Test
	void shouldBuildWithDefaultChatClientBuilder() {
		SubagentType result = ClaudeSubagentType.builder()
			.chatClientBuilder("default", defaultBuilder)
			.build();

		assertThat(result).isNotNull();
		assertThat(result.kind()).isEqualTo(ClaudeSubagentDefinition.KIND);
		assertThat(result.resolver()).isInstanceOf(ClaudeSubagentResolver.class);
		assertThat(result.executor()).isInstanceOf(ClaudeSubagentExecutor.class);
	}

	@Test
	void shouldBuildWithBraveApiKey() {
		SubagentType result = ClaudeSubagentType.builder()
			.chatClientBuilder("default", defaultBuilder)
			.braveApiKey("test-key")
			.build();

		assertThat(result).isNotNull();
	}

	@Test
	void shouldUseConfiguredToolCallingManagerForSubagentToolCalls() {
		ChatModel chatModel = mock(ChatModel.class);
		when(chatModel.getOptions()).thenReturn(ToolCallingChatOptions.builder().build());
		when(chatModel.call(any(Prompt.class))).thenReturn(toolCallResponse(), textResponse("done"));

		ToolCallingManager toolCallingManager = mock(ToolCallingManager.class);
		when(toolCallingManager.resolveToolDefinitions(any())).thenReturn(List.of());
		when(toolCallingManager.executeToolCalls(any(Prompt.class), any(ChatResponse.class)))
			.thenReturn(ToolExecutionResult.builder()
				.conversationHistory(List.of(new UserMessage("tool result")))
				.build());

		SubagentType result = ClaudeSubagentType.builder()
			.chatClientBuilder("default", ChatClient.builder(chatModel))
			.toolCallingManager(toolCallingManager)
			.build();

		String content = result.executor()
			.execute(new TaskCall("desc", "prompt", "test-agent", null, null, null), testSubagent());

		assertThat(content).isEqualTo("done");
		verify(toolCallingManager).executeToolCalls(any(Prompt.class), any(ChatResponse.class));
	}

	private static ClaudeSubagentDefinition testSubagent() {
		return new ClaudeSubagentDefinition(new SubagentReference("file:test.md", ClaudeSubagentDefinition.KIND, null),
				Map.of("name", "test-agent", "description", "desc"), "system prompt");
	}

	private static ChatResponse toolCallResponse() {
		AssistantMessage assistantMessage = AssistantMessage.builder()
			.content("")
			.toolCalls(List.of(new AssistantMessage.ToolCall("call-1", "function", "fetch_rss", "{}")))
			.build();
		return new ChatResponse(List.of(new Generation(assistantMessage)));
	}

	private static ChatResponse textResponse(String content) {
		return new ChatResponse(List.of(new Generation(new AssistantMessage(content))));
	}

}
