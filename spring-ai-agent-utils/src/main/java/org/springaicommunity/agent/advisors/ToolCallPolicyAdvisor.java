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
import java.util.Objects;
import java.util.function.BiFunction;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * A {@link BaseAdvisor} that enforces a configurable policy for each tool call before
 * execution.
 *
 * @author vaquarkhan
 */
public class ToolCallPolicyAdvisor implements BaseAdvisor {

	private final int order;

	private final ToolCallPolicy policy;

	private final BiFunction<String, ToolCallDecision, String> deniedCallResponse;

	private ToolCallPolicyAdvisor(int order, ToolCallPolicy policy,
			BiFunction<String, ToolCallDecision, String> deniedCallResponse) {
		this.order = order;
		this.policy = policy;
		this.deniedCallResponse = deniedCallResponse;
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
			if (callback instanceof PolicyAwareToolCallback) {
				wrappedCallbacks.add(callback);
			}
			else {
				wrappedCallbacks.add(new PolicyAwareToolCallback(callback, this.policy, this.deniedCallResponse));
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

	/**
	 * Policy invoked immediately before a tool executes.
	 */
	@FunctionalInterface
	public interface ToolCallPolicy {

		ToolCallDecision apply(String toolName, String arguments);

	}

	/**
	 * Decision returned by {@link ToolCallPolicy}.
	 *
	 * @param action policy action for this call
	 * @param rewrittenArguments rewritten arguments for {@link ToolCallAction#REWRITE}
	 * @param reason optional human-readable reason
	 */
	public record ToolCallDecision(ToolCallAction action, String rewrittenArguments, String reason) {

		public ToolCallDecision {
			Assert.notNull(action, "action must not be null");
			if (action == ToolCallAction.REWRITE) {
				Assert.hasText(rewrittenArguments, "rewrittenArguments must not be empty when action is REWRITE");
			}
		}

		public static ToolCallDecision allow() {
			return new ToolCallDecision(ToolCallAction.ALLOW, null, null);
		}

		public static ToolCallDecision deny(String reason) {
			return new ToolCallDecision(ToolCallAction.DENY, null, reason);
		}

		public static ToolCallDecision rewrite(String rewrittenArguments) {
			return rewrite(rewrittenArguments, null);
		}

		public static ToolCallDecision rewrite(String rewrittenArguments, String reason) {
			return new ToolCallDecision(ToolCallAction.REWRITE, rewrittenArguments, reason);
		}

	}

	/**
	 * Allowed policy actions for a tool call.
	 */
	public enum ToolCallAction {

		ALLOW, DENY, REWRITE

	}

	private static final class PolicyAwareToolCallback implements ToolCallback {

		private final ToolCallback delegate;

		private final ToolCallPolicy policy;

		private final BiFunction<String, ToolCallDecision, String> deniedCallResponse;

		private PolicyAwareToolCallback(ToolCallback delegate, ToolCallPolicy policy,
				BiFunction<String, ToolCallDecision, String> deniedCallResponse) {
			this.delegate = delegate;
			this.policy = policy;
			this.deniedCallResponse = deniedCallResponse;
		}

		@Override
		public String call(String toolInput) {
			String toolName = this.delegate.getToolDefinition().name();
			ToolCallDecision decision = Objects.requireNonNull(this.policy.apply(toolName, toolInput),
					"policy must not return null decision");

			return switch (decision.action()) {
				case ALLOW -> this.delegate.call(toolInput);
				case REWRITE -> this.delegate.call(decision.rewrittenArguments());
				case DENY -> this.deniedCallResponse.apply(toolName, decision);
			};
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
		private int order = BaseAdvisor.HIGHEST_PRECEDENCE + 250;

		private ToolCallPolicy policy = (toolName, arguments) -> ToolCallDecision.allow();

		private BiFunction<String, ToolCallDecision, String> deniedCallResponse = (toolName, decision) -> {
			String reason = StringUtils.hasText(decision.reason()) ? " Reason: " + decision.reason() : "";
			return "Tool call denied by policy for tool '%s'.%s".formatted(toolName, reason);
		};

		private Builder() {
		}

		public Builder order(int order) {
			this.order = order;
			return this;
		}

		public Builder policy(ToolCallPolicy policy) {
			Assert.notNull(policy, "policy must not be null");
			this.policy = policy;
			return this;
		}

		public Builder deniedCallResponse(BiFunction<String, ToolCallDecision, String> deniedCallResponse) {
			Assert.notNull(deniedCallResponse, "deniedCallResponse must not be null");
			this.deniedCallResponse = deniedCallResponse;
			return this;
		}

		public ToolCallPolicyAdvisor build() {
			return new ToolCallPolicyAdvisor(this.order, this.policy, this.deniedCallResponse);
		}

	}

}
