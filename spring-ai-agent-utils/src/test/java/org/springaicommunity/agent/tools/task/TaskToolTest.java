/*
 * Copyright 2025 - 2025 the original author or authors.
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
package org.springaicommunity.agent.tools.task;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springaicommunity.agent.tools.task.repository.DefaultTaskRepository;
import org.springaicommunity.agent.tools.task.repository.TaskRepository;
import org.springaicommunity.agent.tools.task.subagent.Kind;
import org.springaicommunity.agent.tools.task.subagent.Subagent;
import org.springaicommunity.agent.tools.task.subagent.SubagentExecutor;

import org.springframework.ai.tool.ToolCallback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link TaskTool}.
 *
 * @author Christian Tzolov
 */
class TaskToolTest {

	private TaskRepository taskRepository;

	private SubagentExecutor mockExecutor;

	@BeforeEach
	void setUp() {
		this.taskRepository = new DefaultTaskRepository();
		this.mockExecutor = new SubagentExecutor() {
			@Override
			public String getKind() {
				return Kind.CLAUDE_SUBAGENT.name();
			}

			@Override
			public String execute(TaskTool.TaskCall taskCall, Subagent subagent) {
				return "Executed: " + taskCall.prompt();
			}
		};
	}

	@Test
	void shouldFailWhenTaskRepositoryIsNull() {
		assertThatThrownBy(() -> TaskTool.builder().subagentExecutors(mockExecutor).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("taskRepository must be provided");
	}

	@Test
	void shouldFailWhenNoExecutorsProvided() {
		assertThatThrownBy(() -> TaskTool.builder().taskRepository(taskRepository).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("At least one subagentExecutor must be provided");
	}

	@Test
	void shouldBuildWithRequiredParameters() {
		ToolCallback tool = TaskTool.builder()
			.taskRepository(taskRepository)
			.subagentExecutors(mockExecutor)
			.build();

		assertThat(tool).isNotNull();
		assertThat(tool.getToolDefinition().name()).isEqualTo("Task");
	}

	@Test
	void shouldIncludeBuiltInSubagentsInDescription() {
		ToolCallback tool = TaskTool.builder()
			.taskRepository(taskRepository)
			.subagentExecutors(mockExecutor)
			.build();

		String description = tool.getToolDefinition().description();
		assertThat(description).contains("general-purpose");
		assertThat(description).contains("Explore");
	}

	@Test
	void shouldAcceptCustomSubagentResolvers() {
		ToolCallback tool = TaskTool.builder()
			.taskRepository(taskRepository)
			.subagentExecutors(mockExecutor)
			.subagentResolvers(new org.springaicommunity.agent.tools.task.subagent.claude.ClaudeSubagentResolver())
			.build();

		assertThat(tool).isNotNull();
	}

	@Test
	void shouldCreateTaskCallWithAllFields() {
		TaskTool.TaskCall call = new TaskTool.TaskCall("desc", "prompt", "general-purpose", "opus", "resume-123",
				true);

		assertThat(call.description()).isEqualTo("desc");
		assertThat(call.prompt()).isEqualTo("prompt");
		assertThat(call.subagent_type()).isEqualTo("general-purpose");
		assertThat(call.model()).isEqualTo("opus");
		assertThat(call.resume()).isEqualTo("resume-123");
		assertThat(call.run_in_background()).isTrue();
	}

	@Test
	void shouldCreateTaskCallWithOptionalFieldsAsNull() {
		TaskTool.TaskCall call = new TaskTool.TaskCall("desc", "prompt", "Explore", null, null, null);

		assertThat(call.model()).isNull();
		assertThat(call.resume()).isNull();
		assertThat(call.run_in_background()).isNull();
	}

}
