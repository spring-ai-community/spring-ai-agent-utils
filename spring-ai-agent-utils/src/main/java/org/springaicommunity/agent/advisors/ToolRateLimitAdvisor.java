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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.util.Assert;

/**
 * Enforces per-loop (per-request) tool call limits.
 *
 * @author vaquarkhan
 */
public class ToolRateLimitAdvisor implements BaseAdvisor {

	private final int order;

	private final int maxToolCallsPerRequest;

	private final BiFunction<String, Integer, String> rateLimitResponse;

	private ToolRateLimitAdvisor(int order, int maxToolCallsPerRequest,
			BiFunction<String, Integer, String> rateLimitResponse) {
		this.order = order;
		this.maxToolCallsPerRequest = maxToolCallsPerRequest;
		this.rateLimitResponse = rateLimitResponse;
	}

	@Override
	public ChatClientRequest before(ChatClientRequest chatClientRequest, AdvisorChain advisorChain) {
		if (!(chatClientRequest.prompt().getOptions() instanceof ToolCallingChatOptions toolOptions)) {
			return chatClientRequest;
		}

		ToolCallingChatOptions toolOptionsCopy = toolOptions.copy();
		List<ToolCallback> callbacks = toolOptionsCopy.getToolCallbacks();
		if (callbacks == null || callbacks.isEmpty() || this.maxToolCallsPerRequest <= 0) {
			return chatClientRequest;
		}

		AtomicInteger callCounter = new AtomicInteger(0);
		List<ToolCallback> wrappedCallbacks = new ArrayList<>(callbacks.size());
		for (ToolCallback callback : callbacks) {
			if (callback instanceof RateLimitedToolCallback) {
				wrappedCallbacks.add(callback);
			}
			else {
				wrappedCallbacks.add(new RateLimitedToolCallback(callback, callCounter, this.maxToolCallsPerRequest,
						this.rateLimitResponse));
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

	private static final class RateLimitedToolCallback implements ToolCallback {

		private final ToolCallback delegate;

		private final AtomicInteger callCounter;

		private final int maxToolCallsPerRequest;

		private final BiFunction<String, Integer, String> rateLimitResponse;

		private RateLimitedToolCallback(ToolCallback delegate, AtomicInteger callCounter, int maxToolCallsPerRequest,
				BiFunction<String, Integer, String> rateLimitResponse) {
			this.delegate = delegate;
			this.callCounter = callCounter;
			this.maxToolCallsPerRequest = maxToolCallsPerRequest;
			this.rateLimitResponse = rateLimitResponse;
		}

		@Override
		public String call(String toolInput) {
			String toolName = this.delegate.getToolDefinition().name();
			int calls = this.callCounter.incrementAndGet();
			if (calls > this.maxToolCallsPerRequest) {
				return this.rateLimitResponse.apply(toolName, this.maxToolCallsPerRequest);
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
		private int order = BaseAdvisor.HIGHEST_PRECEDENCE + 247;

		private int maxToolCallsPerRequest = 0;

		private BiFunction<String, Integer, String> rateLimitResponse = (toolName, maxCalls) -> "Tool rate limit exceeded for request: max %d calls, blocked '%s'"
			.formatted(maxCalls, toolName);

		private Builder() {
		}

		public Builder order(int order) {
			this.order = order;
			return this;
		}

		public Builder maxToolCallsPerRequest(int maxToolCallsPerRequest) {
			Assert.isTrue(maxToolCallsPerRequest >= 0, "maxToolCallsPerRequest must be >= 0");
			this.maxToolCallsPerRequest = maxToolCallsPerRequest;
			return this;
		}

		public Builder rateLimitResponse(BiFunction<String, Integer, String> rateLimitResponse) {
			Assert.notNull(rateLimitResponse, "rateLimitResponse must not be null");
			this.rateLimitResponse = rateLimitResponse;
			return this;
		}

		public ToolRateLimitAdvisor build() {
			return new ToolRateLimitAdvisor(this.order, this.maxToolCallsPerRequest, this.rateLimitResponse);
		}

	}

}
