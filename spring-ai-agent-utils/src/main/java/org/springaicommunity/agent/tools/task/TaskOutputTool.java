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

import java.util.function.Function;

import org.springaicommunity.agent.tools.task.repository.BackgroundTask;
import org.springaicommunity.agent.tools.task.repository.TaskRepository;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.util.Assert;

/**
 * Tool for retrieving output from running or completed background tasks.
 * Supports blocking and non-blocking modes with configurable timeouts.
 *
 * @author Christian Tzolov
 */
public class TaskOutputTool {

	private static final String TASK_DESCRIPTION_TEMPLATE = """
			- Retrieves output from a running or completed task (background agent)
			- Takes a task_id parameter identifying the task
			- Returns the task output along with status information
			- Use block=true (default) to wait for task completion
			- Use block=false for non-blocking check of current status
			- Task IDs can be found using the /tasks command
			""";

	public static record TaskOutputCall( // @formatter:off
		@ToolParam(description = "The task ID to get output from") String task_id,
		@ToolParam(description = "Whether to wait for completion", required = false) Boolean block,
		@ToolParam(description = "Max wait time in ms", required = false) Long timeout ) { // @formatter:on
	}

	public static class TaskOutputFunction implements Function<TaskOutputCall, String> {

		// Storage for background tasks
		private final TaskRepository taskRepository;

		public TaskOutputFunction(TaskRepository taskRepository) {
			this.taskRepository = taskRepository;
		}

		@Override
		public String apply(TaskOutputCall taskOutputCall) {

			BackgroundTask bgTask = taskRepository.getTasks(taskOutputCall.task_id());

			if (bgTask == null) {
				return "Error: No background task found with ID: " + taskOutputCall.task_id();
			}

			boolean shouldBlock = taskOutputCall.block() == null || taskOutputCall.block();
			long timeoutMs = taskOutputCall.timeout() != null ? Math.min(taskOutputCall.timeout(), 600000) : 30000;
			if (shouldBlock && !bgTask.isCompleted()) {
				try {
					bgTask.waitForCompletion(timeoutMs);
				}
				catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					return "Error: Wait for task interrupted";
				}
			}

			StringBuilder result = new StringBuilder();
			result.append("Task ID: ").append(taskOutputCall.task_id()).append("\n");
			result.append("Status: ").append(bgTask.getStatus()).append("\n\n");

			if (bgTask.isCompleted() && bgTask.getResult() != null) {
				result.append("Result:\n").append(bgTask.getResult());
			}
			else if (bgTask.getError() != null) {
				result.append("Error:\n").append(bgTask.getError().getMessage());
				if (bgTask.getError().getCause() != null) {
					result.append("\nCause: ").append(bgTask.getError().getCause().getMessage());
				}
			}
			else if (!bgTask.isCompleted()) {
				result.append("Task still running...");
			}

			return result.toString();
		}

	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private String taskDescriptionTemplate = TASK_DESCRIPTION_TEMPLATE;

		private TaskRepository taskRepository;

		private Builder() {
		}

		public Builder taskDescriptionTemplate(String template) {
			this.taskDescriptionTemplate = template;
			return this;
		}

		public Builder taskRepository(TaskRepository taskRepository) {
			Assert.notNull(taskRepository, "taskRepository must not be null");
			this.taskRepository = taskRepository;
			return this;
		}

		public ToolCallback build() {
			Assert.notNull(this.taskRepository, "taskRepository must be provided");
			return FunctionToolCallback.builder("TaskOutput", new TaskOutputFunction(this.taskRepository))
				.description(this.taskDescriptionTemplate)
				.inputType(TaskOutputCall.class)
				.build();
		}

	}

}
