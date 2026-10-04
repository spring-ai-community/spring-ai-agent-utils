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
package org.springaicommunity.agent.testsupport;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.DefaultToolCallingChatOptions;

/**
 * A scriptable {@link ChatModel} that replays a fixed sequence of assistant turns —
 * text responses and tool-call requests — in order, for testing agent loops built on
 * this library without a real LLM.
 *
 * <p>
 * Building a {@code ChatModel} test double for tool-calling flows is trap-prone: a stub
 * that returns a plain {@link ChatOptions} from {@link #getOptions()} produces an agent
 * loop that looks correctly wired but silently never executes a tool, because
 * {@code ToolCallingAdvisor} only runs tool execution when the model's options are
 * {@link org.springframework.ai.model.tool.ToolCallingChatOptions}.
 * {@link ScriptedChatModel} gets this right so the tool loop actually runs.
 *
 * <pre>{@code
 * ChatModel model = ScriptedChatModel.builder()
 *     .toolCallTurn("Bash", "{\"command\":\"ls\"}")
 *     .textTurn("Done - the directory contains ...")
 *     .build();
 * }</pre>
 */
public final class ScriptedChatModel implements ChatModel {

	private final Deque<AssistantMessage> scriptedTurns;

	private final List<Prompt> receivedPrompts = new ArrayList<>();

	private ScriptedChatModel(List<AssistantMessage> scriptedTurns) {
		this.scriptedTurns = new ArrayDeque<>(scriptedTurns);
	}

	public static Builder builder() {
		return new Builder();
	}

	@Override
	public ChatResponse call(Prompt prompt) {
		this.receivedPrompts.add(prompt);
		AssistantMessage next = this.scriptedTurns.poll();
		if (next == null) {
			throw new IllegalStateException(
					"ScriptedChatModel ran out of scripted turns; received prompt: " + prompt);
		}
		return new ChatResponse(List.of(new Generation(next)));
	}

	@Override
	public ChatOptions getOptions() {
		return DefaultToolCallingChatOptions.builder().build();
	}

	/**
	 * Returns the prompts this model received, in call order. Useful for asserting on
	 * conversation state built up across a multi-turn tool-calling loop.
	 */
	public List<Prompt> getReceivedPrompts() {
		return List.copyOf(this.receivedPrompts);
	}

	/**
	 * Returns the number of scripted turns not yet consumed.
	 */
	public int remainingTurns() {
		return this.scriptedTurns.size();
	}

	public static final class Builder {

		private final List<AssistantMessage> turns = new ArrayList<>();

		private final AtomicInteger toolCallIdSequence = new AtomicInteger();

		private Builder() {
		}

		/**
		 * Adds a plain text assistant turn.
		 */
		public Builder textTurn(String content) {
			this.turns.add(new AssistantMessage(content));
			return this;
		}

		/**
		 * Adds a turn where the assistant requests a single tool call.
		 * @param toolName the tool name, matching the name the caller's
		 * {@code ToolCallback} is registered under
		 * @param argumentsJson the tool call arguments, as a JSON string
		 */
		public Builder toolCallTurn(String toolName, String argumentsJson) {
			String id = "call_" + this.toolCallIdSequence.getAndIncrement();
			AssistantMessage.ToolCall toolCall = new AssistantMessage.ToolCall(id, "function", toolName,
					argumentsJson);
			this.turns.add(AssistantMessage.builder().content("").toolCalls(List.of(toolCall)).build());
			return this;
		}

		public ScriptedChatModel build() {
			return new ScriptedChatModel(this.turns);
		}

	}

}
