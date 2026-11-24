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
package org.springaicommunity.agent.advisors;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.DefaultToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("ToolFilterAdvisor Tests")
class ToolFilterAdvisorTest {

	@Test
	@DisplayName("Include and exclude patterns are applied to tool names")
	void filtersByGlobPatterns() {
		ToolCallback read = toolCallback("ReadFile");
		ToolCallback write = toolCallback("WriteFile");
		ToolCallback web = toolCallback("WebSearch");

		DefaultToolCallingChatOptions options = new DefaultToolCallingChatOptions();
		options.setToolCallbacks(List.of(read, write, web));
		ChatClientRequest request = ChatClientRequest.builder().prompt(new Prompt(new UserMessage("hi"), options)).build();

		ToolFilterAdvisor advisor = ToolFilterAdvisor.builder()
			.includePatterns(List.of("*File"))
			.excludePatterns(List.of("Write*"))
			.build();

		List<String> names = ((DefaultToolCallingChatOptions) advisor.before(request, null).prompt().getOptions()).getToolCallbacks()
			.stream()
			.map(tc -> tc.getToolDefinition().name())
			.toList();

		assertThat(names).containsExactly("ReadFile");
	}

	private static ToolCallback toolCallback(String toolName) {
		ToolCallback callback = mock(ToolCallback.class);
		ToolDefinition toolDefinition = mock(ToolDefinition.class);
		when(toolDefinition.name()).thenReturn(toolName);
		when(callback.getToolDefinition()).thenReturn(toolDefinition);
		return callback;
	}

}
