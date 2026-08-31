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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.DefaultToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link ToolCallPolicyAdvisor}.
 *
 * @author vaquarkhan
 */
@DisplayName("ToolCallPolicyAdvisor Tests")
@ExtendWith(MockitoExtension.class)
class ToolCallPolicyAdvisorTest {

	private final AdvisorChain advisorChain = mock(AdvisorChain.class);

	@Test
	@DisplayName("Default order is HIGHEST_PRECEDENCE + 250")
	void defaultOrder() {
		ToolCallPolicyAdvisor advisor = ToolCallPolicyAdvisor.builder().build();
		assertThat(advisor.getOrder()).isEqualTo(BaseAdvisor.HIGHEST_PRECEDENCE + 250);
	}

	@Test
	@DisplayName("Returns request unchanged when no ToolCallingChatOptions")
	void beforePassesThroughWhenNoToolOptions() {
		ChatClientRequest request = ChatClientRequest.builder().prompt(new Prompt(new UserMessage("hello"))).build();

		ToolCallPolicyAdvisor advisor = ToolCallPolicyAdvisor.builder().build();
		ChatClientRequest result = advisor.before(request, advisorChain);

		assertThat(result).isSameAs(request);
	}

	@Test
	@DisplayName("Policy can deny a tool call")
	void denyToolCall() {
		ToolCallback callback = toolCallback("WriteFile");
		DefaultToolCallingChatOptions options = new DefaultToolCallingChatOptions();
		options.setToolCallbacks(List.of(callback));
		ChatClientRequest request = ChatClientRequest.builder()
			.prompt(new Prompt(new UserMessage("hello"), options))
			.build();

		ToolCallPolicyAdvisor advisor = ToolCallPolicyAdvisor.builder()
			.policy((name, args) -> ToolCallPolicyAdvisor.ToolCallDecision.deny("blocked"))
			.build();

		ToolCallback guardedCallback = ((DefaultToolCallingChatOptions) advisor.before(request, advisorChain)
			.prompt()
			.getOptions()).getToolCallbacks().get(0);

		String result = guardedCallback.call("{\"path\":\"/tmp/x\"}");

		assertThat(result).contains("denied").contains("WriteFile").contains("blocked");
		verify(callback, never()).call(anyString());
	}

	@Test
	@DisplayName("Policy can rewrite tool arguments before execution")
	void rewriteToolArguments() {
		ToolCallback callback = toolCallback("EditFile");
		when(callback.call(anyString())).thenReturn("delegate-result");
		DefaultToolCallingChatOptions options = new DefaultToolCallingChatOptions();
		options.setToolCallbacks(List.of(callback));
		ChatClientRequest request = ChatClientRequest.builder()
			.prompt(new Prompt(new UserMessage("hello"), options))
			.build();

		ToolCallPolicyAdvisor advisor = ToolCallPolicyAdvisor.builder()
			.policy((name, args) -> ToolCallPolicyAdvisor.ToolCallDecision.rewrite("{\"safe\":true}"))
			.build();

		ToolCallback guardedCallback = ((DefaultToolCallingChatOptions) advisor.before(request, advisorChain)
			.prompt()
			.getOptions()).getToolCallbacks().get(0);

		String result = guardedCallback.call("{\"unsafe\":true}");

		assertThat(result).isEqualTo("delegate-result");
		verify(callback).call("{\"safe\":true}");
	}

	@Test
	@DisplayName("Policy allow executes original callback with original arguments")
	void allowCallsOriginalCallback() {
		ToolCallback callback = toolCallback("ReadFile");
		when(callback.call(anyString())).thenReturn("ok");
		DefaultToolCallingChatOptions options = new DefaultToolCallingChatOptions();
		options.setToolCallbacks(List.of(callback));
		ChatClientRequest request = ChatClientRequest.builder()
			.prompt(new Prompt(new UserMessage("hello"), options))
			.build();

		ToolCallPolicyAdvisor advisor = ToolCallPolicyAdvisor.builder()
			.policy((name, args) -> ToolCallPolicyAdvisor.ToolCallDecision.allow())
			.build();

		ToolCallback guardedCallback = ((DefaultToolCallingChatOptions) advisor.before(request, advisorChain)
			.prompt()
			.getOptions()).getToolCallbacks().get(0);

		String result = guardedCallback.call("{\"path\":\"README.md\"}");

		assertThat(result).isEqualTo("ok");
		verify(callback).call("{\"path\":\"README.md\"}");
	}

	@Test
	@DisplayName("Rewrite decision requires non-empty rewritten arguments")
	void rewriteRequiresArguments() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new ToolCallPolicyAdvisor.ToolCallDecision(
					ToolCallPolicyAdvisor.ToolCallAction.REWRITE, "", null));
	}

	private static ToolCallback toolCallback(String toolName) {
		ToolCallback callback = mock(ToolCallback.class);
		ToolDefinition toolDefinition = mock(ToolDefinition.class);
		when(toolDefinition.name()).thenReturn(toolName);
		when(callback.getToolDefinition()).thenReturn(toolDefinition);
		return callback;
	}

}
