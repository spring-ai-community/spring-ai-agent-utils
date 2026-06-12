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
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.DefaultToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("ToolApprovalAdvisor Tests")
class ToolApprovalAdvisorTest {

	@Test
	@DisplayName("Denied approval blocks tool execution")
	void deniedApprovalBlocksExecution() {
		ToolCallback callback = toolCallback("WriteFile");
		DefaultToolCallingChatOptions options = new DefaultToolCallingChatOptions();
		options.setToolCallbacks(List.of(callback));
		ChatClientRequest request = ChatClientRequest.builder().prompt(new Prompt(new UserMessage("hi"), options)).build();

		ToolApprovalAdvisor advisor = ToolApprovalAdvisor.builder()
			.approvalManager(req -> CompletableFuture.completedFuture(ToolApprovalAdvisor.ToolApprovalDecision.deny("manual")))
			.build();

		ToolCallback guarded = ((DefaultToolCallingChatOptions) advisor.before(request, null).prompt().getOptions())
			.getToolCallbacks()
			.get(0);
		String result = guarded.call("{\"x\":1}");

		assertThat(result).contains("denied").contains("manual");
		verify(callback, never()).call(anyString());
	}

	@Test
	@DisplayName("Approval rewrite uses rewritten arguments")
	void approvalRewriteUsesRewrittenArguments() {
		ToolCallback callback = toolCallback("EditFile");
		when(callback.call(anyString())).thenReturn("ok");
		DefaultToolCallingChatOptions options = new DefaultToolCallingChatOptions();
		options.setToolCallbacks(List.of(callback));
		ChatClientRequest request = ChatClientRequest.builder().prompt(new Prompt(new UserMessage("hi"), options)).build();

		ToolApprovalAdvisor advisor = ToolApprovalAdvisor.builder()
			.approvalManager(req -> CompletableFuture
				.completedFuture(ToolApprovalAdvisor.ToolApprovalDecision.approveWithRewrite("{\"safe\":true}")))
			.build();

		ToolCallback guarded = ((DefaultToolCallingChatOptions) advisor.before(request, null).prompt().getOptions())
			.getToolCallbacks()
			.get(0);
		String result = guarded.call("{\"unsafe\":true}");

		assertThat(result).isEqualTo("ok");
		verify(callback).call("{\"safe\":true}");
	}

	private static ToolCallback toolCallback(String toolName) {
		ToolCallback callback = mock(ToolCallback.class);
		ToolDefinition toolDefinition = mock(ToolDefinition.class);
		when(toolDefinition.name()).thenReturn(toolName);
		when(callback.getToolDefinition()).thenReturn(toolDefinition);
		return callback;
	}

}
