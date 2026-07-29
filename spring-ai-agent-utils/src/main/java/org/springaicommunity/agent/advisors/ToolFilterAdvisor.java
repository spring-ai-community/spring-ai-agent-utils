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

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.util.Assert;
import org.springframework.util.PatternMatchUtils;

/**
 * Filters available tool callbacks by include/exclude glob patterns.
 *
 * @author vaquarkhan
 */
public class ToolFilterAdvisor implements BaseAdvisor {

	private final int order;

	private final List<String> includePatterns;

	private final List<String> excludePatterns;

	private ToolFilterAdvisor(int order, List<String> includePatterns, List<String> excludePatterns) {
		this.order = order;
		this.includePatterns = includePatterns;
		this.excludePatterns = excludePatterns;
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

		List<ToolCallback> filtered = callbacks.stream().filter(this::isAllowed).toList();
		toolOptionsCopy.setToolCallbacks(filtered);
		return chatClientRequest.mutate().prompt(chatClientRequest.prompt().mutate().chatOptions(toolOptionsCopy).build()).build();
	}

	private boolean isAllowed(ToolCallback callback) {
		String toolName = callback.getToolDefinition().name();
		boolean included = this.includePatterns.isEmpty()
				|| PatternMatchUtils.simpleMatch(this.includePatterns.toArray(String[]::new), toolName);
		if (!included) {
			return false;
		}
		boolean excluded = !this.excludePatterns.isEmpty()
				&& PatternMatchUtils.simpleMatch(this.excludePatterns.toArray(String[]::new), toolName);
		return !excluded;
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

	public static final class Builder {

		// Before default ToolCallingAdvisor (HIGHEST_PRECEDENCE + 300)
		private int order = BaseAdvisor.HIGHEST_PRECEDENCE + 248;

		private List<String> includePatterns = List.of();

		private List<String> excludePatterns = List.of();

		private Builder() {
		}

		public Builder order(int order) {
			this.order = order;
			return this;
		}

		public Builder includePatterns(List<String> includePatterns) {
			Assert.notNull(includePatterns, "includePatterns must not be null");
			this.includePatterns = includePatterns;
			return this;
		}

		public Builder excludePatterns(List<String> excludePatterns) {
			Assert.notNull(excludePatterns, "excludePatterns must not be null");
			this.excludePatterns = excludePatterns;
			return this;
		}

		public ToolFilterAdvisor build() {
			return new ToolFilterAdvisor(this.order, this.includePatterns, this.excludePatterns);
		}

	}

}
