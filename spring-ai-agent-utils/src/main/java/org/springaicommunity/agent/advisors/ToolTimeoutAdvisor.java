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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.util.Assert;

/**
 * Enforces timeout limits for tool execution.
 *
 * @author vaquarkhan
 */
public class ToolTimeoutAdvisor implements BaseAdvisor {

	private final int order;

	private final Duration defaultTimeout;

	private final Map<String, Duration> perToolTimeouts;

	private final ExecutorService executorService;

	private final BiFunction<String, Duration, String> timeoutResponse;

	private ToolTimeoutAdvisor(int order, Duration defaultTimeout, Map<String, Duration> perToolTimeouts,
			ExecutorService executorService, BiFunction<String, Duration, String> timeoutResponse) {
		this.order = order;
		this.defaultTimeout = defaultTimeout;
		this.perToolTimeouts = perToolTimeouts;
		this.executorService = executorService;
		this.timeoutResponse = timeoutResponse;
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
			if (callback instanceof TimeoutToolCallback) {
				wrappedCallbacks.add(callback);
			}
			else {
				wrappedCallbacks.add(new TimeoutToolCallback(callback, this.defaultTimeout, this.perToolTimeouts,
						this.executorService, this.timeoutResponse));
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

	private static final class TimeoutToolCallback implements ToolCallback {

		private final ToolCallback delegate;

		private final Duration defaultTimeout;

		private final Map<String, Duration> perToolTimeouts;

		private final ExecutorService executorService;

		private final BiFunction<String, Duration, String> timeoutResponse;

		private TimeoutToolCallback(ToolCallback delegate, Duration defaultTimeout, Map<String, Duration> perToolTimeouts,
				ExecutorService executorService, BiFunction<String, Duration, String> timeoutResponse) {
			this.delegate = delegate;
			this.defaultTimeout = defaultTimeout;
			this.perToolTimeouts = perToolTimeouts;
			this.executorService = executorService;
			this.timeoutResponse = timeoutResponse;
		}

		@Override
		public String call(String toolInput) {
			String toolName = this.delegate.getToolDefinition().name();
			Duration timeout = this.perToolTimeouts.getOrDefault(toolName, this.defaultTimeout);
			try {
				return CompletableFuture.supplyAsync(() -> this.delegate.call(toolInput), this.executorService)
					.orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
					.join();
			}
			catch (CompletionException ex) {
				if (ex.getCause() instanceof java.util.concurrent.TimeoutException) {
					return this.timeoutResponse.apply(toolName, timeout);
				}
				throw ex;
			}
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
		private int order = BaseAdvisor.HIGHEST_PRECEDENCE + 246;

		private Duration defaultTimeout = Duration.ofSeconds(30);

		private Map<String, Duration> perToolTimeouts = Map.of();

		private ExecutorService executorService = Executors.newCachedThreadPool();

		private BiFunction<String, Duration, String> timeoutResponse = (toolName, timeout) -> "Tool '%s' timed out after %d ms"
			.formatted(toolName, timeout.toMillis());

		private Builder() {
		}

		public Builder order(int order) {
			this.order = order;
			return this;
		}

		public Builder defaultTimeout(Duration defaultTimeout) {
			Assert.notNull(defaultTimeout, "defaultTimeout must not be null");
			Assert.isTrue(!defaultTimeout.isNegative() && !defaultTimeout.isZero(),
					"defaultTimeout must be positive");
			this.defaultTimeout = defaultTimeout;
			return this;
		}

		public Builder perToolTimeouts(Map<String, Duration> perToolTimeouts) {
			Assert.notNull(perToolTimeouts, "perToolTimeouts must not be null");
			this.perToolTimeouts = perToolTimeouts;
			return this;
		}

		public Builder executorService(ExecutorService executorService) {
			Assert.notNull(executorService, "executorService must not be null");
			this.executorService = executorService;
			return this;
		}

		public Builder timeoutResponse(BiFunction<String, Duration, String> timeoutResponse) {
			Assert.notNull(timeoutResponse, "timeoutResponse must not be null");
			this.timeoutResponse = timeoutResponse;
			return this;
		}

		public ToolTimeoutAdvisor build() {
			return new ToolTimeoutAdvisor(this.order, this.defaultTimeout, this.perToolTimeouts, this.executorService,
					this.timeoutResponse);
		}

	}

}
