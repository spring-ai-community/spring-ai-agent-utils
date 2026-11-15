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

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springaicommunity.agent.common.task.subagent.SubagentDefinition;
import org.springaicommunity.agent.common.task.subagent.SubagentExecutor;
import org.springaicommunity.agent.common.task.subagent.SubagentType;
import org.springaicommunity.agent.common.task.subagent.TaskCall;
import org.springaicommunity.agent.tools.AgentToolset.WorkspaceAccess;
import org.springaicommunity.agent.tools.task.TaskTool;
import org.springaicommunity.agent.tools.task.claude.ClaudeSubagentDefinition;
import org.springaicommunity.agent.tools.task.claude.ClaudeSubagentResolver;
import org.springaicommunity.agent.utils.AgentEnvironment;

import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests that {@link AgentToolset#promptVariables(Object...)} matches
 * {@link WorkspaceAccess} and that {@code MAIN_AGENT_SYSTEM_PROMPT_V2.md} drops the
 * sections for tools that were not assembled.
 */
class AgentToolsetPromptTest {

	private static final List<String> PROMPT_FLAGS = List.of(AgentToolset.WORKSPACE_WRITE_AVAILABLE,
			AgentToolset.WORKSPACE_EXECUTE_AVAILABLE, AgentToolset.WORKSPACE_FULL_TOOLS_AVAILABLE,
			AgentToolset.TODO_WRITE_AVAILABLE, AgentToolset.WEB_FETCH_AVAILABLE, AgentToolset.TASK_AVAILABLE,
			AgentToolset.TASK_FILE_SEARCH_AVAILABLE);

	@Test
	void promptVariablesFollowWorkspaceAccess() {
		assertWorkspaceFlags(AgentToolset.builder().workspaceAccess(WorkspaceAccess.NONE).promptVariables(), false,
				false, false);
		assertWorkspaceFlags(AgentToolset.builder().workspaceAccess(WorkspaceAccess.READ).promptVariables(), false,
				false, false);
		assertWorkspaceFlags(AgentToolset.builder().workspaceAccess(WorkspaceAccess.WRITE).promptVariables(), true,
				false, false);
		assertWorkspaceFlags(AgentToolset.builder().workspaceAccess(WorkspaceAccess.EXECUTE).promptVariables(), true,
				true, true);
		assertWorkspaceFlags(AgentToolset.builder().promptVariables(), true, true, true);
	}

	@Test
	void promptVariablesFollowToolsRemovedOrAdded() {
		Map<String, Object> withoutEdit = AgentToolset.builder().without("Edit").promptVariables();
		assertThat(withoutEdit).containsEntry(AgentToolset.WORKSPACE_WRITE_AVAILABLE, true)
			.containsEntry(AgentToolset.WORKSPACE_EXECUTE_AVAILABLE, true)
			.containsEntry(AgentToolset.WORKSPACE_FULL_TOOLS_AVAILABLE, false);

		Map<String, Object> withoutBash = AgentToolset.builder().without("Bash").promptVariables();
		assertThat(withoutBash).containsEntry(AgentToolset.WORKSPACE_WRITE_AVAILABLE, true)
			.containsEntry(AgentToolset.WORKSPACE_EXECUTE_AVAILABLE, false)
			.containsEntry(AgentToolset.WORKSPACE_FULL_TOOLS_AVAILABLE, false);

		Map<String, Object> readOnlyFiles = AgentToolset.builder().without("Write").without("Edit").promptVariables();
		assertThat(readOnlyFiles).containsEntry(AgentToolset.WORKSPACE_WRITE_AVAILABLE, false)
			.containsEntry(AgentToolset.WORKSPACE_FULL_TOOLS_AVAILABLE, false);

		ToolCallback webFetch = FunctionToolCallback.builder("WebFetch", (EchoInput input) -> input.text())
			.description("Fetches")
			.inputType(EchoInput.class)
			.build();
		Map<String, Object> readWithExtras = AgentToolset.builder()
			.workspaceAccess(WorkspaceAccess.READ)
			.with(TodoWriteTool.builder().build())
			.with(webFetch)
			.promptVariables();
		assertThat(readWithExtras).containsEntry(AgentToolset.WORKSPACE_WRITE_AVAILABLE, false)
			.containsEntry(AgentToolset.WORKSPACE_EXECUTE_AVAILABLE, false)
			.containsEntry(AgentToolset.WORKSPACE_FULL_TOOLS_AVAILABLE, false)
			.containsEntry(AgentToolset.TODO_WRITE_AVAILABLE, true)
			.containsEntry(AgentToolset.WEB_FETCH_AVAILABLE, true)
			.containsEntry(AgentToolset.TASK_AVAILABLE, false)
			.containsEntry(AgentToolset.TASK_FILE_SEARCH_AVAILABLE, false);

		assertThat(AgentToolset.builder().promptVariables())
			.isEqualTo(AgentToolset.promptVariables(AgentToolset.builder().build()));
	}

	@Test
	void taskFileSearchFlagRequiresExploreRegistration() {
		ToolCallback exploreTask = TaskTool.builder().subagentTypes(claudeSubagentType()).build();
		assertThat(exploreTask.getToolDefinition().description()).contains("-Explore:");
		assertThat(AgentToolset.promptVariables(exploreTask)).containsEntry(AgentToolset.TASK_AVAILABLE, true)
			.containsEntry(AgentToolset.TASK_FILE_SEARCH_AVAILABLE, true);

		ToolCallback bareTask = FunctionToolCallback.builder("Task", (EchoInput input) -> input.text())
			.description("Task without an Explore subagent")
			.inputType(EchoInput.class)
			.build();
		assertThat(AgentToolset.promptVariables(bareTask)).containsEntry(AgentToolset.TASK_AVAILABLE, true)
			.containsEntry(AgentToolset.TASK_FILE_SEARCH_AVAILABLE, false);
	}

	@Test
	void everyFlagIsPresentAndNamedInThePrompt() throws Exception {
		String template = new ClassPathResource("/prompt/MAIN_AGENT_SYSTEM_PROMPT_V2.md")
			.getContentAsString(StandardCharsets.UTF_8);
		assertThat(AgentToolset.promptVariables().keySet()).containsExactlyInAnyOrderElementsOf(PROMPT_FLAGS);
		for (String flag : PROMPT_FLAGS) {
			assertThat(template).contains(flag);
		}
	}

	@Test
	void renderedPromptDropsToolsTheTierDoesNotHave() {
		String read = render(AgentToolset.builder().workspaceAccess(WorkspaceAccess.READ).promptVariables());
		assertThat(read).contains("Professional objectivity")
			.contains("file_path:line_number")
			.contains("test-model")
			.doesNotContain("{")
			.doesNotContain("WebFetch")
			.doesNotContain("Bash")
			.doesNotContain("bash")
			.doesNotContain("NEVER create files")
			.doesNotContain("TodoWrite")
			.doesNotContain("Task tool")
			.doesNotContain("subagent_type=Explore")
			.doesNotContain("Use specialized tools instead of bash");

		String write = render(AgentToolset.builder().workspaceAccess(WorkspaceAccess.WRITE).promptVariables());
		assertThat(write).contains("NEVER create files unless they're absolutely necessary")
			.doesNotContain("{")
			.doesNotContain("Bash")
			.doesNotContain("bash")
			.doesNotContain("TodoWrite")
			.doesNotContain("Use specialized tools instead of bash");

		String execute = render(AgentToolset.builder().workspaceAccess(WorkspaceAccess.EXECUTE).promptVariables());
		assertThat(execute).contains("Never use tools like Bash")
			.contains("NEVER create files unless they're absolutely necessary")
			.contains("Use specialized tools instead of bash commands")
			.contains("Read for reading files")
			.contains("Edit for editing")
			.contains("Write for creating files")
			.doesNotContain("{")
			.doesNotContain("TodoWrite")
			.doesNotContain("WebFetch")
			.doesNotContain("Task tool");

		String none = render(AgentToolset.builder().workspaceAccess(WorkspaceAccess.NONE).promptVariables());
		assertThat(none).doesNotContain("{")
			.doesNotContain("Bash")
			.doesNotContain("NEVER create files")
			.doesNotContain("TodoWrite")
			.doesNotContain("WebFetch")
			.doesNotContain("Task tool");
	}

	@Test
	void renderedPromptKeepsOptionalSectionsOnlyWhenThoseToolsAreAssembled() {
		ToolCallback webFetch = FunctionToolCallback.builder("WebFetch", (EchoInput input) -> input.text())
			.description("Fetches")
			.inputType(EchoInput.class)
			.build();
		ToolCallback exploreTask = TaskTool.builder().subagentTypes(claudeSubagentType()).build();

		String readAndTodo = render(AgentToolset.builder()
			.workspaceAccess(WorkspaceAccess.READ)
			.with(TodoWriteTool.builder().build())
			.promptVariables());
		assertThat(readAndTodo).contains("You have access to the TodoWrite tools")
			.contains("Use the TodoWrite tool to plan the task if required")
			.contains("Always use the TodoWrite tool")
			.doesNotContain("Examples:")
			.doesNotContain("using Bash")
			.doesNotContain("usage metrics")
			.doesNotContain("{");

		String writeAndTodo = render(AgentToolset.builder()
			.workspaceAccess(WorkspaceAccess.WRITE)
			.with(TodoWriteTool.builder().build())
			.promptVariables());
		assertThat(writeAndTodo).contains("Examples:")
			.contains("usage metrics tracking")
			.doesNotContain("using Bash")
			.doesNotContain("{");

		String full = render(AgentToolset.builder()
			.with(TodoWriteTool.builder().build())
			.with(webFetch)
			.with(exploreTask)
			.promptVariables());
		assertThat(full).contains("use the WebFetch tool to gather information")
			.contains("When WebFetch returns a message about a redirect")
			.contains("using Bash")
			.contains("usage metrics tracking")
			.contains("prefer to use the Task tool in order to reduce context usage")
			.contains("proactively use the Task tool")
			.contains("multiple Task tool calls")
			.contains("subagent_type=Explore")
			.contains("Use specialized tools instead of bash commands")
			.doesNotContain("{");
	}

	@Test
	void missingPromptFlagFailsRendering() {
		Map<String, Object> environmentOnly = environmentVariables();
		assertThatThrownBy(() -> PromptTemplate.builder()
			.resource(new ClassPathResource("/prompt/MAIN_AGENT_SYSTEM_PROMPT_V2.md"))
			.variables(environmentOnly)
			.build()
			.render()).isInstanceOf(IllegalStateException.class).hasMessageContaining("WORKSPACE_WRITE_AVAILABLE");
	}

	private static void assertWorkspaceFlags(Map<String, Object> flags, boolean write, boolean execute, boolean full) {
		assertThat(flags).containsEntry(AgentToolset.WORKSPACE_WRITE_AVAILABLE, write)
			.containsEntry(AgentToolset.WORKSPACE_EXECUTE_AVAILABLE, execute)
			.containsEntry(AgentToolset.WORKSPACE_FULL_TOOLS_AVAILABLE, full)
			.containsEntry(AgentToolset.TODO_WRITE_AVAILABLE, false)
			.containsEntry(AgentToolset.WEB_FETCH_AVAILABLE, false)
			.containsEntry(AgentToolset.TASK_AVAILABLE, false)
			.containsEntry(AgentToolset.TASK_FILE_SEARCH_AVAILABLE, false);
	}

	private static String render(Map<String, Object> flags) {
		Map<String, Object> variables = environmentVariables();
		variables.putAll(flags);
		return PromptTemplate.builder()
			.resource(new ClassPathResource("/prompt/MAIN_AGENT_SYSTEM_PROMPT_V2.md"))
			.variables(variables)
			.build()
			.render();
	}

	private static Map<String, Object> environmentVariables() {
		Map<String, Object> variables = new HashMap<>();
		variables.put(AgentEnvironment.ENVIRONMENT_INFO_KEY, "Working directory: /workspace");
		variables.put(AgentEnvironment.GIT_STATUS_KEY, "gitStatus: clean");
		variables.put(AgentEnvironment.AGENT_MODEL_KEY, "test-model");
		variables.put(AgentEnvironment.AGENT_MODEL_KNOWLEDGE_CUTOFF_KEY, "2025-01-01");
		return variables;
	}

	private static SubagentType claudeSubagentType() {
		SubagentExecutor executor = new SubagentExecutor() {
			@Override
			public String getKind() {
				return ClaudeSubagentDefinition.KIND;
			}

			@Override
			public String execute(TaskCall taskCall, SubagentDefinition subagent) {
				return "ok";
			}
		};
		return new SubagentType(new ClaudeSubagentResolver(), executor);
	}

	record EchoInput(String text) {
	}

}
