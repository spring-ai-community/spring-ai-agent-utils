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

import org.junit.jupiter.api.Test;
import org.springaicommunity.agent.common.task.subagent.SubagentType;
import org.springaicommunity.agent.tools.task.claude.ClaudeSubagentDefinition;
import org.springaicommunity.agent.tools.task.claude.ClaudeSubagentExecutor;
import org.springaicommunity.agent.tools.task.claude.ClaudeSubagentResolver;
import org.springaicommunity.agent.tools.task.claude.ClaudeSubagentType;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

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

}
