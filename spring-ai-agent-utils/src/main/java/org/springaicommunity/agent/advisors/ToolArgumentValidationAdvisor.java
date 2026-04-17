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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.util.Assert;

/**
 * Enforces maximum tool argument payload size before execution.
 *
 * @author vaquarkhan
 */
public class ToolArgumentValidationAdvisor implements BaseAdvisor {

	private final int order;

	private final int maxArgumentBytes;

	private final BiFunction<String, Integer, String> validationErrorResponse;

	private ToolArgumentValidationAdvisor(int order, int maxArgumentBytes,
			BiFunction<String, Integer, String> validationErrorResponse) {
		this.order = order;
		this.maxArgumentBytes = maxArgumentBytes;
		this.validationErrorResponse = validationErrorResponse;
	}

	@Override
	public ChatClientRequest before(ChatClientRequest chatClientRequest, AdvisorChain advisorChain) {
		if (!(chatClientRequest.prompt().getOptions() instanceof ToolCallingChatOptions toolOptions)) {
			return chatClientRequest;
		}

		ToolCallingChatOptions toolOptionsCopy = toolOptions.copy();
		List<ToolCallback> callbacks = toolOptionsCopy.getToolCallbacks();
		if (callbacks == null || callbacks.isEmpty()) {
			return chatClientRequest;
		}

		List<ToolCallback> wrappedCallbacks = new ArrayList<>(callbacks.size());
		for (ToolCallback callback : callbacks) {
			if (callback instanceof ArgumentValidationToolCallback) {
				wrappedCallbacks.add(callback);
			}
			else {
				wrappedCallbacks
					.add(new ArgumentValidationToolCallback(callback, this.maxArgumentBytes, this.validationErrorResponse));
			}
		}

		toolOptionsCopy.setToolCallbacks(wrappedCallbacks);
		return chatClientRequest.mutate().prompt(chatClientRequest.prompt().mutate().chatOptions(toolOptionsCopy).build()).build();
	}

	@Override
	public ChatClientResponse after(ChatClientResponse chatClientResponse, AdvisorChain advisorChain) {
		return chatClientResponse;
	}

	@Override
	public int getOrder() {
		return this.order;
	}

	public static Builder builder() {
		return new Builder();
	}

	private static final class ArgumentValidationToolCallback implements ToolCallback {

		private final ToolCallback delegate;

		private final int maxArgumentBytes;

		private final BiFunction<String, Integer, String> validationErrorResponse;

		private ArgumentValidationToolCallback(ToolCallback delegate, int maxArgumentBytes,
				BiFunction<String, Integer, String> validationErrorResponse) {
			this.delegate = delegate;
			this.maxArgumentBytes = maxArgumentBytes;
			this.validationErrorResponse = validationErrorResponse;
		}

		@Override
		public String call(String toolInput) {
			String input = toolInput != null ? toolInput : "";
			int argumentBytes = input.getBytes(StandardCharsets.UTF_8).length;
			String toolName = this.delegate.getToolDefinition().name();
			if (argumentBytes > this.maxArgumentBytes) {
				return this.validationErrorResponse.apply(toolName, argumentBytes);
			}
			return this.delegate.call(toolInput);
		}

		@Override
		public org.springframework.ai.tool.definition.ToolDefinition getToolDefinition() {
			return this.delegate.getToolDefinition();
		}

		@Override
		public org.springframework.ai.tool.metadata.ToolMetadata getToolMetadata() {
			return this.delegate.getToolMetadata();
		}

	}

	public static final class Builder {

		// Before default ToolCallingAdvisor (HIGHEST_PRECEDENCE + 300)
		private int order = BaseAdvisor.HIGHEST_PRECEDENCE + 245;

		private int maxArgumentBytes = 65_536;

		private BiFunction<String, Integer, String> validationErrorResponse = (toolName, actualBytes) -> "Tool '%s' arguments are too large: %d bytes"
			.formatted(toolName, actualBytes);

		private Builder() {
		}

		public Builder order(int order) {
			this.order = order;
			return this;
		}

		public Builder maxArgumentBytes(int maxArgumentBytes) {
			Assert.isTrue(maxArgumentBytes > 0, "maxArgumentBytes must be > 0");
			this.maxArgumentBytes = maxArgumentBytes;
			return this;
		}

		public Builder validationErrorResponse(BiFunction<String, Integer, String> validationErrorResponse) {
			Assert.notNull(validationErrorResponse, "validationErrorResponse must not be null");
			this.validationErrorResponse = validationErrorResponse;
			return this;
		}

		public ToolArgumentValidationAdvisor build() {
			return new ToolArgumentValidationAdvisor(this.order, this.maxArgumentBytes, this.validationErrorResponse);
		}

	}

}
