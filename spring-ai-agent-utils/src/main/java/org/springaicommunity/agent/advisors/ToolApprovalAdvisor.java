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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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
 * Human-in-the-loop advisor that blocks tool execution until approval is resolved.
 *
 * @author vaquarkhan
 */
public class ToolApprovalAdvisor implements BaseAdvisor {

	private final int order;

	private final ManualApprovalManager approvalManager;

	private final Duration approvalTimeout;

	private final BiFunction<String, ToolApprovalDecision, String> deniedCallResponse;

	private ToolApprovalAdvisor(int order, ManualApprovalManager approvalManager, Duration approvalTimeout,
			BiFunction<String, ToolApprovalDecision, String> deniedCallResponse) {
		this.order = order;
		this.approvalManager = approvalManager;
		this.approvalTimeout = approvalTimeout;
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
			if (callback instanceof ApprovalAwareToolCallback) {
				wrappedCallbacks.add(callback);
			}
			else {
				wrappedCallbacks.add(new ApprovalAwareToolCallback(callback, this.approvalManager, this.approvalTimeout,
						this.deniedCallResponse));
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
	 * Manual approval manager contract.
	 */
	@FunctionalInterface
	public interface ManualApprovalManager {

		CompletableFuture<ToolApprovalDecision> requestApproval(ToolApprovalRequest request);

	}

	/**
	 * Approval request payload.
	 *
	 * @param toolName tool name proposed by the model
	 * @param arguments serialized tool arguments
	 */
	public record ToolApprovalRequest(String toolName, String arguments) {
	}

	/**
	 * Approval decision payload.
	 *
	 * @param approved true to allow execution
	 * @param rewrittenArguments optional rewritten arguments when approved
	 * @param reason optional reason used for deny messaging
	 */
	public record ToolApprovalDecision(boolean approved, String rewrittenArguments, String reason) {

		public static ToolApprovalDecision approve() {
			return new ToolApprovalDecision(true, null, null);
		}

		public static ToolApprovalDecision approveWithRewrite(String rewrittenArguments) {
			Assert.hasText(rewrittenArguments, "rewrittenArguments must not be empty");
			return new ToolApprovalDecision(true, rewrittenArguments, null);
		}

		public static ToolApprovalDecision deny(String reason) {
			return new ToolApprovalDecision(false, null, reason);
		}

	}

	private static final class ApprovalAwareToolCallback implements ToolCallback {

		private final ToolCallback delegate;

		private final ManualApprovalManager approvalManager;

		private final Duration approvalTimeout;

		private final BiFunction<String, ToolApprovalDecision, String> deniedCallResponse;

		private ApprovalAwareToolCallback(ToolCallback delegate, ManualApprovalManager approvalManager, Duration approvalTimeout,
				BiFunction<String, ToolApprovalDecision, String> deniedCallResponse) {
			this.delegate = delegate;
			this.approvalManager = approvalManager;
			this.approvalTimeout = approvalTimeout;
			this.deniedCallResponse = deniedCallResponse;
		}

		@Override
		public String call(String toolInput) {
			String toolName = this.delegate.getToolDefinition().name();
			try {
				CompletableFuture<ToolApprovalDecision> approvalFuture = this.approvalManager
					.requestApproval(new ToolApprovalRequest(toolName, toolInput));
				ToolApprovalDecision decision = (this.approvalTimeout == null) ? approvalFuture.get()
						: approvalFuture.get(this.approvalTimeout.toMillis(), TimeUnit.MILLISECONDS);
				if (decision == null || !decision.approved()) {
					return this.deniedCallResponse.apply(toolName,
							decision != null ? decision : ToolApprovalDecision.deny("No approval decision returned"));
				}
				if (StringUtils.hasText(decision.rewrittenArguments())) {
					return this.delegate.call(decision.rewrittenArguments());
				}
				return this.delegate.call(toolInput);
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				return this.deniedCallResponse.apply(toolName, ToolApprovalDecision.deny("Approval interrupted"));
			}
			catch (TimeoutException ex) {
				return this.deniedCallResponse.apply(toolName, ToolApprovalDecision.deny("Approval timed out"));
			}
			catch (ExecutionException ex) {
				return this.deniedCallResponse.apply(toolName,
						ToolApprovalDecision.deny("Approval failed: " + ex.getCause().getMessage()));
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
		private int order = BaseAdvisor.HIGHEST_PRECEDENCE + 240;

		private ManualApprovalManager approvalManager = request -> CompletableFuture.completedFuture(ToolApprovalDecision.approve());

		private Duration approvalTimeout = null;

		private BiFunction<String, ToolApprovalDecision, String> deniedCallResponse = (toolName, decision) -> {
			String reason = decision != null && StringUtils.hasText(decision.reason()) ? " Reason: " + decision.reason() : "";
			return "Tool call denied by approval manager for tool '%s'.%s".formatted(toolName, reason);
		};

		private Builder() {
		}

		public Builder order(int order) {
			this.order = order;
			return this;
		}

		public Builder approvalManager(ManualApprovalManager approvalManager) {
			Assert.notNull(approvalManager, "approvalManager must not be null");
			this.approvalManager = approvalManager;
			return this;
		}

		public Builder approvalTimeout(Duration approvalTimeout) {
			Assert.isTrue(approvalTimeout == null || !approvalTimeout.isNegative(), "approvalTimeout must not be negative");
			this.approvalTimeout = approvalTimeout;
			return this;
		}

		public Builder deniedCallResponse(BiFunction<String, ToolApprovalDecision, String> deniedCallResponse) {
			Assert.notNull(deniedCallResponse, "deniedCallResponse must not be null");
			this.deniedCallResponse = deniedCallResponse;
			return this;
		}

		public ToolApprovalAdvisor build() {
			return new ToolApprovalAdvisor(this.order, this.approvalManager, this.approvalTimeout, this.deniedCallResponse);
		}

	}

}
