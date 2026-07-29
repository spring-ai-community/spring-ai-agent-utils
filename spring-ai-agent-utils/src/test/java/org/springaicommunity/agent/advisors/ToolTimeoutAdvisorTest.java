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

import java.time.Duration;
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

@DisplayName("ToolTimeoutAdvisor Tests")
class ToolTimeoutAdvisorTest {

	@Test
	@DisplayName("Timeout returns timeout response")
	void timeoutReturnsTimeoutResponse() {
		ToolCallback callback = toolCallback("SlowTool", input -> {
			try {
				Thread.sleep(120);
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
			return "slow";
		});
		DefaultToolCallingChatOptions options = new DefaultToolCallingChatOptions();
		options.setToolCallbacks(List.of(callback));
		ChatClientRequest request = ChatClientRequest.builder().prompt(new Prompt(new UserMessage("hi"), options)).build();

		ToolTimeoutAdvisor advisor = ToolTimeoutAdvisor.builder().defaultTimeout(Duration.ofMillis(20)).build();
		ToolCallback guarded = ((DefaultToolCallingChatOptions) advisor.before(request, null).prompt().getOptions())
			.getToolCallbacks()
			.get(0);

		String result = guarded.call("{}");
		assertThat(result).contains("timed out");
	}

	@Test
	@DisplayName("Fast tool completes normally")
	void fastToolCompletesNormally() {
		ToolCallback callback = toolCallback("FastTool", input -> "ok");
		DefaultToolCallingChatOptions options = new DefaultToolCallingChatOptions();
		options.setToolCallbacks(List.of(callback));
		ChatClientRequest request = ChatClientRequest.builder().prompt(new Prompt(new UserMessage("hi"), options)).build();

		ToolTimeoutAdvisor advisor = ToolTimeoutAdvisor.builder().defaultTimeout(Duration.ofSeconds(1)).build();
		ToolCallback guarded = ((DefaultToolCallingChatOptions) advisor.before(request, null).prompt().getOptions())
			.getToolCallbacks()
			.get(0);

		assertThat(guarded.call("{}")).isEqualTo("ok");
	}

	private static ToolCallback toolCallback(String toolName, java.util.function.Function<String, String> fn) {
		ToolCallback callback = mock(ToolCallback.class);
		ToolDefinition toolDefinition = mock(ToolDefinition.class);
		when(toolDefinition.name()).thenReturn(toolName);
		when(callback.getToolDefinition()).thenReturn(toolDefinition);
		when(callback.call(org.mockito.ArgumentMatchers.anyString())).thenAnswer(invocation -> fn.apply(invocation.getArgument(0)));
		return callback;
	}

}
