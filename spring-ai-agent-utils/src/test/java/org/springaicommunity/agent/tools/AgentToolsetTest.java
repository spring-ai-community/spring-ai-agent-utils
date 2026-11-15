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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.springaicommunity.agent.common.exec.ExecBackend;
import org.springaicommunity.agent.common.exec.ExecHandle;
import org.springaicommunity.agent.common.exec.ExecResult;
import org.springaicommunity.agent.common.exec.ExecSpec;
import org.springaicommunity.agent.common.workspace.Workspace;
import org.springaicommunity.agent.tools.AgentToolset.WorkspaceAccess;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.function.FunctionToolCallback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link AgentToolset}.
 */
class AgentToolsetTest {

	private static final List<String> EXECUTE_TOOLS = List.of("Bash", "BashOutput", "KillShell", "Read", "Write",
			"Edit", "Grep", "Glob", "ListDirectory");

	private static final List<String> WRITE_TOOLS = List.of("Read", "Write", "Edit", "Grep", "Glob", "ListDirectory");

	private static final List<String> READ_TOOLS = List.of("Read", "Grep", "Glob", "ListDirectory");

	@TempDir
	Path workspaceDir;

	@TempDir
	Path outsideDir;

	@Test
	void defaultToolsetIsExecuteInStableOrder() {
		List<ToolCallback> tools = AgentToolset.builder().build();

		assertThat(names(tools)).containsExactlyElementsOf(EXECUTE_TOOLS);
		assertThat(names(AgentToolset.builder().workspaceAccess(WorkspaceAccess.EXECUTE).build()))
			.containsExactlyElementsOf(EXECUTE_TOOLS);
	}

	@Test
	void workspaceAccessSelectsReadWriteOrNothing() {
		assertThat(names(AgentToolset.builder().workspaceAccess(WorkspaceAccess.READ).build()))
			.containsExactlyElementsOf(READ_TOOLS);
		assertThat(names(AgentToolset.builder().workspaceAccess(WorkspaceAccess.WRITE).build()))
			.containsExactlyElementsOf(WRITE_TOOLS);
		assertThat(AgentToolset.builder().workspaceAccess(WorkspaceAccess.NONE).build()).isEmpty();
	}

	@Test
	void withoutRemovesABuiltInAndRejectsUnknownNames() {
		List<ToolCallback> tools = AgentToolset.builder().without("Grep").without("Bash").build();

		assertThat(names(tools)).doesNotContain("Grep", "Bash").contains("Glob", "BashOutput", "Read", "Write");

		assertThatThrownBy(() -> AgentToolset.builder().without("grep").build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("grep")
			.hasMessageContaining("Grep");
		assertThatThrownBy(() -> AgentToolset.builder().workspaceAccess(WorkspaceAccess.READ).without("Bash").build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Bash");
		assertThatThrownBy(() -> AgentToolset.builder().without("  ").build())
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void withAppendsAnnotatedToolsAndCallbacks() {
		ToolCallback extra = FunctionToolCallback.builder("Echo", (EchoInput input) -> "echo:" + input.text())
			.description("Echoes")
			.inputType(EchoInput.class)
			.build();

		List<ToolCallback> tools = AgentToolset.builder()
			.workspaceAccess(WorkspaceAccess.NONE)
			.with(TodoWriteTool.builder().build())
			.with(extra)
			.build();

		assertThat(names(tools)).containsExactly("TodoWrite", "Echo");
		assertThat(tools.get(1).call("{\"text\":\"hi\"}")).contains("echo:hi");

		List<ToolCallback> fromProvider = AgentToolset.builder()
			.workspaceAccess(WorkspaceAccess.NONE)
			.with(ToolCallbackProvider.from(extra))
			.build();
		assertThat(names(fromProvider)).containsExactly("Echo");
	}

	@Test
	void withoutDropsAnAddedTool() {
		List<ToolCallback> tools = AgentToolset.builder()
			.workspaceAccess(WorkspaceAccess.READ)
			.with(TodoWriteTool.builder().build())
			.without("TodoWrite")
			.without("Grep")
			.build();

		assertThat(names(tools)).containsExactly("Read", "Glob", "ListDirectory");
	}

	@Test
	void listenerObservesCallsAndKeepsTheToolName() {
		RecordingBackend backend = new RecordingBackend();
		RecordingListener listener = new RecordingListener();

		List<ToolCallback> tools = AgentToolset.builder().execBackend(backend).listener(listener).build();
		ToolCallback bash = callback(tools, "Bash");

		assertThat(bash.getToolDefinition().name()).isEqualTo("Bash");
		assertThat(bash.call("{\"command\":\"echo hi\"}")).contains("from-backend");
		assertThat(backend.commands).containsExactly("echo hi");
		assertThat(listener.events).containsExactly("before:Bash", "after:Bash");
	}

	@Test
	void returnedListIsUnmodifiableAndALaterBuildSeesNewOptions() {
		AgentToolset.Builder builder = AgentToolset.builder();
		List<ToolCallback> first = builder.build();

		assertThatThrownBy(() -> first.add(first.get(0))).isInstanceOf(UnsupportedOperationException.class);

		List<ToolCallback> second = builder.workspaceAccess(WorkspaceAccess.NONE).build();

		assertThat(names(first)).containsExactlyElementsOf(EXECUTE_TOOLS);
		assertThat(second).isEmpty();
	}

	@Test
	void nullArgumentsAreRejected() {
		AgentToolset.Builder builder = AgentToolset.builder();

		assertThatThrownBy(() -> builder.execBackend(null)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> builder.workspace(null)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> builder.listener(null)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> builder.workspaceAccess(null)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> builder.with(null)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void workspaceConfinesFileAndSearchTools() throws Exception {
		Files.writeString(this.workspaceDir.resolve("inside.txt"), "needle in workspace\n");
		Files.writeString(this.outsideDir.resolve("secret.env"), "needle outside\n");
		Workspace workspace = Workspace.local(this.workspaceDir);

		List<ToolCallback> tools = AgentToolset.builder()
			.workspace(workspace)
			.workspaceAccess(WorkspaceAccess.READ)
			.build();

		String inside = this.workspaceDir.resolve("inside.txt").toString();
		String outside = this.outsideDir.resolve("secret.env").toString();

		assertThat(callback(tools, "Read").call("{\"filePath\":" + json(inside) + "}")).contains("needle in workspace");
		assertThat(callback(tools, "Read").call("{\"filePath\":" + json(outside) + "}")).contains("Access denied");

		assertThat(callback(tools, "Grep").call("{\"pattern\":\"needle\"}")).contains("inside.txt")
			.doesNotContain("secret.env");
		assertThat(callback(tools, "Grep").call("{\"pattern\":\"needle\",\"path\":" + json(outside) + "}"))
			.contains("Access denied");

		assertThat(callback(tools, "Glob").call("{\"pattern\":\"*.txt\"}")).contains("inside.txt");
		assertThat(callback(tools, "Glob").call("{\"pattern\":\"*.env\",\"path\":" + json(outside) + "}"))
			.contains("Access denied");

		assertThat(callback(tools, "ListDirectory").call("{}")).contains("inside.txt");
		assertThat(callback(tools, "ListDirectory").call("{\"path\":" + json(outside) + "}"))
			.contains("Access denied");
	}

	@Test
	void execBackendIsUsedForShellWhileWorkspaceStillConfinesFiles() throws Exception {
		Files.writeString(this.workspaceDir.resolve("inside.txt"), "needle in workspace\n");
		Files.writeString(this.outsideDir.resolve("secret.env"), "needle outside\n");
		RecordingBackend backend = new RecordingBackend();

		List<ToolCallback> tools = AgentToolset.builder()
			.execBackend(backend)
			.workspace(Workspace.local(this.workspaceDir))
			.build();

		assertThat(callback(tools, "Bash").call("{\"command\":\"echo hi\"}")).contains("from-backend");
		assertThat(backend.commands).containsExactly("echo hi");
		String outsideFile = this.outsideDir.resolve("secret.env").toString();
		assertThat(callback(tools, "Read").call("{\"filePath\":" + json(outsideFile) + "}")).contains("Access denied");
		assertThat(names(tools)).doesNotContain("TodoWrite");
	}

	@Test
	@DisabledOnOs(OS.WINDOWS)
	void localWorkspaceRootsTheShellAndShellCallbacksShareOneInstance() throws Exception {
		List<ToolCallback> tools = AgentToolset.builder().workspace(Workspace.local(this.workspaceDir)).build();

		String pwd = callback(tools, "Bash").call("{\"command\":\"pwd\"}");
		assertThat(pwd).contains(this.workspaceDir.toRealPath().toString());

		String started = callback(tools, "Bash").call("{\"command\":\"sleep 30\",\"runInBackground\":true}");
		String bashId = bashId(started);
		assertThat(callback(tools, "BashOutput").call("{\"bash_id\":" + json(bashId) + "}"))
			.contains("Status: Running");
		assertThat(callback(tools, "KillShell").call("{\"bash_id\":" + json(bashId) + "}"))
			.contains("Successfully killed shell");
	}

	private static List<String> names(List<ToolCallback> tools) {
		return tools.stream().map(callback -> callback.getToolDefinition().name()).toList();
	}

	private static ToolCallback callback(List<ToolCallback> tools, String name) {
		return tools.stream()
			.filter(callback -> callback.getToolDefinition().name().equals(name))
			.findFirst()
			.orElseThrow(() -> new AssertionError("missing tool " + name));
	}

	private static String json(String value) {
		return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
	}

	private static String bashId(String toolResult) {
		String marker = "bash_id: ";
		int start = toolResult.indexOf(marker);
		assertThat(start).isGreaterThanOrEqualTo(0);
		int from = start + marker.length();
		int end = from;
		while (end < toolResult.length()) {
			char current = toolResult.charAt(end);
			if (current == '\\' || Character.isWhitespace(current) || current == '"') {
				break;
			}
			end++;
		}
		return toolResult.substring(from, end);
	}

	record EchoInput(String text) {
	}

	static final class RecordingBackend implements ExecBackend {

		final List<String> commands = new ArrayList<>();

		@Override
		public ExecResult run(ExecSpec spec) {
			this.commands.add(spec.command());
			return ExecResult.completed(0, "from-backend", "");
		}

		@Override
		public ExecHandle start(ExecSpec spec) {
			throw new UnsupportedOperationException("not used");
		}

	}

	static final class RecordingListener implements ToolCallListener {

		final List<String> events = new ArrayList<>();

		@Override
		public Object beforeCall(String toolName, String toolInput) {
			this.events.add("before:" + toolName);
			return toolName;
		}

		@Override
		public void afterCall(Object context, String toolName, String toolInput, String result) {
			this.events.add("after:" + toolName);
		}

	}

}
