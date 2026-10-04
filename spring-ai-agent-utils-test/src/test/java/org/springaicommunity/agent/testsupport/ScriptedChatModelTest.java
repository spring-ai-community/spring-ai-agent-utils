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

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

@DisplayName("ScriptedChatModel Tests")
class ScriptedChatModelTest {

	record EchoInput(String message) {
	}

	@Test
	@DisplayName("getOptions() returns ToolCallingChatOptions, not a plain ChatOptions")
	void optionsAreToolCallingChatOptions() {
		ChatModel model = ScriptedChatModel.builder().textTurn("hi").build();

		ChatOptions options = model.getOptions();

		assertThat(options).isInstanceOf(ToolCallingChatOptions.class);
	}

	@Test
	@DisplayName("textTurn replies are replayed in order")
	void textTurnsReplayInOrder() {
		ScriptedChatModel model = ScriptedChatModel.builder().textTurn("first").textTurn("second").build();

		assertThat(model.call(new org.springframework.ai.chat.prompt.Prompt("go")).getResult().getOutput().getText())
			.isEqualTo("first");
		assertThat(model.call(new org.springframework.ai.chat.prompt.Prompt("go")).getResult().getOutput().getText())
			.isEqualTo("second");
	}

	@Test
	@DisplayName("toolCallTurn produces an AssistantMessage with a matching tool call")
	void toolCallTurnProducesToolCall() {
		ScriptedChatModel model = ScriptedChatModel.builder().toolCallTurn("Bash", "{\"command\":\"ls\"}").build();

		var response = model.call(new org.springframework.ai.chat.prompt.Prompt("run ls"));

		assertThat(response.hasToolCalls()).isTrue();
		var toolCall = response.getResult().getOutput().getToolCalls().get(0);
		assertThat(toolCall.name()).isEqualTo("Bash");
		assertThat(toolCall.arguments()).isEqualTo("{\"command\":\"ls\"}");
	}

	@Test
	@DisplayName("throws once every scripted turn has been consumed")
	void throwsWhenScriptExhausted() {
		ScriptedChatModel model = ScriptedChatModel.builder().textTurn("only turn").build();
		model.call(new org.springframework.ai.chat.prompt.Prompt("go"));

		assertThatIllegalStateException()
			.isThrownBy(() -> model.call(new org.springframework.ai.chat.prompt.Prompt("go again")))
			.withMessageContaining("ran out of scripted turns");
	}

	@Test
	@DisplayName("records every prompt received, in order")
	void recordsReceivedPrompts() {
		ScriptedChatModel model = ScriptedChatModel.builder().textTurn("a").textTurn("b").build();

		model.call(new org.springframework.ai.chat.prompt.Prompt("first prompt"));
		model.call(new org.springframework.ai.chat.prompt.Prompt("second prompt"));

		assertThat(model.getReceivedPrompts()).hasSize(2);
		assertThat(model.getReceivedPrompts().get(0).getContents()).isEqualTo("first prompt");
		assertThat(model.getReceivedPrompts().get(1).getContents()).isEqualTo("second prompt");
	}

	@Test
	@DisplayName("end-to-end: ChatClient actually executes the scripted tool call")
	void chatClientActuallyExecutesScriptedToolCall() {
		// This is the failure mode the issue describes: a ChatModel stub whose
		// getOptions() returns a plain ChatOptions produces an agent loop that looks
		// wired correctly but never invokes the tool, with no error raised anywhere.
		ScriptedChatModel model = ScriptedChatModel.builder()
			.toolCallTurn("echo", "{\"message\":\"hi\"}")
			.textTurn("Done - echoed hi")
			.build();

		AtomicBoolean toolWasCalled = new AtomicBoolean(false);
		AtomicReference<String> receivedMessage = new AtomicReference<>();
		ToolCallback echoTool = FunctionToolCallback.builder("echo", (EchoInput input) -> {
			toolWasCalled.set(true);
			receivedMessage.set(input.message());
			return "echoed";
		}).inputType(EchoInput.class).description("Echoes the given message").build();

		String result = ChatClient.builder(model)
			.build()
			.prompt()
			.user("say hi")
			.toolCallbacks(echoTool)
			.call()
			.content();

		assertThat(toolWasCalled).isTrue();
		assertThat(receivedMessage).hasValue("hi");
		assertThat(result).isEqualTo("Done - echoed hi");
	}

}
