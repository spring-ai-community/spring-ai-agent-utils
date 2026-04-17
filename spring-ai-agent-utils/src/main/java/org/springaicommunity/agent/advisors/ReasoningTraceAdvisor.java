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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Emits per-iteration reasoning traces with timing and tool-call information.
 *
 * @author vaquarkhan
 */
public class ReasoningTraceAdvisor implements BaseAdvisor {

	private final int order;

	private final Consumer<ReasoningTraceEntry> traceSink;

	private final int maxTextLength;

	private final boolean includeToolArguments;

	private final ThreadLocal<Long> iterationStartNanos = new ThreadLocal<>();

	private final ThreadLocal<String> requestInput = new ThreadLocal<>();

	private ReasoningTraceAdvisor(int order, Consumer<ReasoningTraceEntry> traceSink, int maxTextLength,
			boolean includeToolArguments) {
		this.order = order;
		this.traceSink = traceSink;
		this.maxTextLength = maxTextLength;
		this.includeToolArguments = includeToolArguments;
	}

	@Override
	public ChatClientRequest before(ChatClientRequest chatClientRequest, AdvisorChain advisorChain) {
		this.iterationStartNanos.set(System.nanoTime());
		this.requestInput.set(extractUserInput(chatClientRequest));
		return chatClientRequest;
	}

	@Override
	public ChatClientResponse after(ChatClientResponse chatClientResponse, AdvisorChain advisorChain) {
		long startNanos = this.iterationStartNanos.get() != null ? this.iterationStartNanos.get() : System.nanoTime();
		long durationNanos = System.nanoTime() - startNanos;

		List<ToolCallTrace> toolCalls = new ArrayList<>();
		StringBuilder assistantText = new StringBuilder();

		if (chatClientResponse != null && chatClientResponse.chatResponse() != null
				&& chatClientResponse.chatResponse().getResults() != null) {
			for (var generation : chatClientResponse.chatResponse().getResults()) {
				var output = generation.getOutput();
				if (output.getToolCalls() != null) {
					for (var toolCall : output.getToolCalls()) {
						toolCalls.add(new ToolCallTrace(toolCall.name(),
								this.includeToolArguments ? truncate(toolCall.arguments()) : null));
					}
				}
				if (StringUtils.hasText(output.getText())) {
					if (!assistantText.isEmpty()) {
						assistantText.append("\n");
					}
					assistantText.append(output.getText());
				}
			}
		}

		this.traceSink.accept(new ReasoningTraceEntry(Instant.now(), durationNanos / 1_000_000L, this.requestInput.get(),
				toolCalls, truncate(assistantText.toString())));
		this.iterationStartNanos.remove();
		this.requestInput.remove();
		return chatClientResponse;
	}

	private String extractUserInput(ChatClientRequest chatClientRequest) {
		if (chatClientRequest == null || chatClientRequest.prompt() == null) {
			return null;
		}
		Message message = chatClientRequest.prompt().getLastUserOrToolResponseMessage();
		if (message != null && message.getMessageType() == MessageType.USER && StringUtils.hasText(message.getText())) {
			return truncate(message.getText());
		}
		return null;
	}

	private String truncate(String text) {
		if (!StringUtils.hasText(text) || text.length() <= this.maxTextLength) {
			return text;
		}
		return text.substring(0, this.maxTextLength) + "...";
	}

	@Override
	public int getOrder() {
		return this.order;
	}

	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Per-iteration trace entry.
	 *
	 * @param timestamp trace timestamp
	 * @param durationMs advisor iteration duration in milliseconds
	 * @param userInput last user input text
	 * @param toolCalls tool calls emitted in this iteration
	 * @param assistantText assistant text emitted in this iteration
	 */
	public record ReasoningTraceEntry(Instant timestamp, long durationMs, String userInput, List<ToolCallTrace> toolCalls,
			String assistantText) {
	}

	/**
	 * Tool call trace element.
	 *
	 * @param name tool name
	 * @param arguments serialized tool arguments
	 */
	public record ToolCallTrace(String name, String arguments) {
	}

	public static final class Builder {

		// Early advisor, before tool-calling advisor
		private int order = BaseAdvisor.HIGHEST_PRECEDENCE + 210;

		private Consumer<ReasoningTraceEntry> traceSink = trace -> {
		};

		private int maxTextLength = 2000;

		private boolean includeToolArguments = true;

		private Builder() {
		}

		public Builder order(int order) {
			this.order = order;
			return this;
		}

		public Builder traceSink(Consumer<ReasoningTraceEntry> traceSink) {
			Assert.notNull(traceSink, "traceSink must not be null");
			this.traceSink = traceSink;
			return this;
		}

		public Builder maxTextLength(int maxTextLength) {
			Assert.isTrue(maxTextLength > 0, "maxTextLength must be > 0");
			this.maxTextLength = maxTextLength;
			return this;
		}

		public Builder includeToolArguments(boolean includeToolArguments) {
			this.includeToolArguments = includeToolArguments;
			return this;
		}

		public ReasoningTraceAdvisor build() {
			return new ReasoningTraceAdvisor(this.order, this.traceSink, this.maxTextLength, this.includeToolArguments);
		}

	}

}
