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

import java.util.ArrayList;
import java.util.List;

import org.springaicommunity.agent.tools.BraveWebSearchTool;
import org.springaicommunity.agent.tools.FileSystemTools;
import org.springaicommunity.agent.tools.GlobTool;
import org.springaicommunity.agent.tools.GrepTool;
import org.springaicommunity.agent.tools.ShellTools;
import org.springaicommunity.agent.tools.SkillsTool;
import org.springaicommunity.agent.tools.SmartWebFetchTool;
import org.springaicommunity.agent.tools.TodoWriteTool;
import org.springaicommunity.agent.tools.task.repository.DefaultTaskRepository;
import org.springaicommunity.agent.tools.task.repository.TaskRepository;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * @author Christian Tzolov
 */

public class TaskToolCallbackProvider implements ToolCallbackProvider {

	private final ToolCallback[] toolCallbacks;

	private TaskToolCallbackProvider(ToolCallback[] toolCallbacks) {
		this.toolCallbacks = toolCallbacks;
	}

	@Override
	public ToolCallback[] getToolCallbacks() {
		return toolCallbacks;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private TaskRepository taskRepository = new DefaultTaskRepository();

		private ChatClient.Builder chatClientBuilder;

		private List<String> agentDirectories = new ArrayList<>();

		private List<String> skillsDirectories = new ArrayList<>();

		private String braveApiKey = System.getenv("BRAVE_API_KEY");

		public Builder braveApiKey(String braveApiKey) {
			Assert.notNull(braveApiKey, "braveApiKey must not be null");
			this.braveApiKey = braveApiKey;
			return this;
		}

		public Builder taskRepository(TaskRepository taskRepository) {
			Assert.notNull(taskRepository, "taskRepository must not be null");
			this.taskRepository = taskRepository;
			return this;
		}

		public Builder chatClientBuilder(ChatClient.Builder chatClientBuilder) {
			Assert.notNull(chatClientBuilder, "chatClientBuilder must not be null");
			this.chatClientBuilder = chatClientBuilder;
			return this;
		}

		public Builder agentDirectories(List<String> agentDirectories) {
			Assert.notNull(agentDirectories, "agentDirectories must not be null");
			this.agentDirectories.addAll(agentDirectories);
			return this;
		}

		public Builder agentDirectories(String agentDirectory) {
			Assert.notNull(agentDirectory, "agentDirectory must not be null");
			this.agentDirectories.add(agentDirectory);
			return this;
		}

		public Builder skillsDirectories(List<String> skillsDirectories) {
			Assert.notNull(skillsDirectories, "skillsDirectories must not be null");
			this.skillsDirectories.addAll(skillsDirectories);
			return this;
		}

		public Builder skillsDirectories(String skillsDirectory) {
			Assert.notNull(skillsDirectory, "skillsDirectory must not be null");
			this.skillsDirectories.add(skillsDirectory);
			return this;
		}

		private List<ToolCallback> coreToolCallbacks() {

			List<ToolCallback> callbacks = new ArrayList<>();

			List<ToolCallback> callbacks2 = List.of(MethodToolCallbackProvider.builder()
				.toolObjects(TodoWriteTool.builder().build(), GrepTool.builder().build(), GlobTool.builder().build(), ShellTools.builder().build(),
						FileSystemTools.builder().build(), SmartWebFetchTool.builder(chatClientBuilder.clone().build()).build())
				.build()
				.getToolCallbacks());

			callbacks.addAll(callbacks2);

			if (!this.skillsDirectories.isEmpty()) {
				callbacks.add(SkillsTool.builder().addSkillsDirectories(this.skillsDirectories).build());
			}

			if (StringUtils.hasText(this.braveApiKey)) {
				callbacks.add(MethodToolCallbackProvider.builder()
					.toolObjects(BraveWebSearchTool.builder(braveApiKey).resultCount(15).build())
					.build()
					.getToolCallbacks()[0]);
			}

			return callbacks;
		}

		public TaskToolCallbackProvider build() {

			ToolCallback taskToolCallback = TaskTool.builder()
				.tools(this.coreToolCallbacks())
				.addTaskDirectories(this.agentDirectories)
				.chatClientBuilder(this.chatClientBuilder)
				.taskRepository(this.taskRepository)
				.build();

			ToolCallback taskOutputToolCallback = TaskOutputTool.builder().taskRepository(this.taskRepository).build();

			return new TaskToolCallbackProvider(new ToolCallback[] { taskToolCallback, taskOutputToolCallback });
		}

	}

}
