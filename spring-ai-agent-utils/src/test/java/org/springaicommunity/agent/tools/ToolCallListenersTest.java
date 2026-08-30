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
package org.springaicommunity.agent.tools;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link ToolCallListeners} / {@link ToolCallListener}.
 *
 * @author Christian Tzolov
 */
class ToolCallListenersTest {

	record EchoInput(String text) {
	}

	private static ToolCallback echoTool() {
		return FunctionToolCallback.builder("Echo", (EchoInput input) -> "echo: " + input.text())
			.description("Echoes the input")
			.inputType(EchoInput.class)
			.build();
	}

	private static ToolCallback failingTool() {
		return FunctionToolCallback.builder("Boom", (Function<EchoInput, String>) input -> {
			throw new IllegalStateException("kaboom");
		}).description("Always fails").inputType(EchoInput.class).build();
	}

	static final class RecordingListener implements ToolCallListener {

		final List<String> log = new ArrayList<>();

		String errorReport;

		@Override
		public Object beforeCall(String toolName, String toolInput) {
			this.log.add("before:" + toolName);
			return "ctx-" + toolName;
		}

		@Override
		public void afterCall(Object context, String toolName, String toolInput, String result) {
			this.log.add("after:" + toolName + ":" + context + ":" + result);
		}

		@Override
		public String onError(Object context, String toolName, String toolInput, RuntimeException exception) {
			this.log.add("error:" + toolName + ":" + context + ":" + exception.getMessage());
			return this.errorReport;
		}

	}

	@Test
	void listenerObservesCallWithCorrelationContext() {
		RecordingListener listener = new RecordingListener();
		ToolCallback wrapped = ToolCallListeners.wrap(echoTool(), listener);

		String result = wrapped.call("{\"text\":\"hi\"}");

		assertThat(result).isEqualTo("\"echo: hi\"");
		assertThat(listener.log).containsExactly("before:Echo", "after:Echo:ctx-Echo:\"echo: hi\"");
	}

	@Test
	void onErrorStringIsReturnedToTheModelInsteadOfThrowing() {
		RecordingListener listener = new RecordingListener();
		listener.errorReport = "Tool 'Boom' failed: kaboom";
		ToolCallback wrapped = ToolCallListeners.wrap(failingTool(), listener);

		String result = wrapped.call("{\"text\":\"x\"}");

		assertThat(result).isEqualTo("Tool 'Boom' failed: kaboom");
		assertThat(listener.log).contains("error:Boom:ctx-Boom:kaboom");
	}

	@Test
	void nullOnErrorRethrowsTheOriginalException() {
		RecordingListener listener = new RecordingListener();
		ToolCallback wrapped = ToolCallListeners.wrap(failingTool(), listener);

		assertThatThrownBy(() -> wrapped.call("{\"text\":\"x\"}")).hasMessageContaining("kaboom");
		assertThat(listener.log).contains("error:Boom:ctx-Boom:kaboom");
	}

	@Test
	void toolDefinitionAndMetadataAreDelegated() {
		ToolCallback original = echoTool();
		ToolCallback wrapped = ToolCallListeners.wrap(original, new ToolCallListener() {
		});

		assertThat(wrapped.getToolDefinition().name()).isEqualTo("Echo");
		assertThat(wrapped.getToolDefinition()).isSameAs(original.getToolDefinition());
		assertThat(wrapped.getToolMetadata()).isSameAs(original.getToolMetadata());
	}

	@Test
	void wrapAllDecoratesEveryCallbackWithTheSameListener() {
		RecordingListener listener = new RecordingListener();
		List<ToolCallback> wrapped = ToolCallListeners.wrapAll(List.of(echoTool(), echoTool()), listener);

		assertThat(wrapped).hasSize(2);
		wrapped.forEach(callback -> callback.call("{\"text\":\"a\"}"));
		assertThat(listener.log).filteredOn(entry -> entry.startsWith("before:")).hasSize(2);
	}

	@Test
	void noOpListenerLeavesBehaviorUnchanged() {
		ToolCallback wrapped = ToolCallListeners.wrap(echoTool(), new ToolCallListener() {
		});

		assertThat(wrapped.call("{\"text\":\"hi\"}")).isEqualTo("\"echo: hi\"");
		assertThatThrownBy(() -> ToolCallListeners.wrap(failingTool(), new ToolCallListener() {
		}).call("{}")).hasMessageContaining("kaboom");
	}

}
