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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
import org.springaicommunity.agent.tools.task.subagent.SubagentReference;
import org.springaicommunity.agent.tools.task.subagent.SubagentType;
import org.springaicommunity.agent.tools.task.subagent.claude.ClaudeSubagentExecutor;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Provides Task and TaskOutput tool callbacks with configured subagents and core tools.
 * Uses deferred initialization to build tools on first access.
 *
 * @author Christian Tzolov
 */
public class TaskToolCallbackProvider implements ToolCallbackProvider {

	private final String braveApiKey;

	private final TaskRepository taskRepository;

	private final Map<String, ChatClient.Builder> chatClientBuilderMap;

	private final List<SubagentReference> subagentReferences;

	private final List<SubagentType> subagentTypes;

	private final List<String> skillsDirectories;

	private volatile ToolCallback[] toolCallbacks;

	private TaskToolCallbackProvider(String braveApiKey, TaskRepository taskRepository,
			Map<String, ChatClient.Builder> chatClientBuilderMap, List<SubagentReference> subagentReferences,
			List<SubagentType> subagentTypes, List<String> skillsDirectories) {
		this.braveApiKey = braveApiKey;
		this.taskRepository = taskRepository;
		this.chatClientBuilderMap = Map.copyOf(chatClientBuilderMap);
		this.subagentReferences = List.copyOf(subagentReferences);
		this.subagentTypes = List.copyOf(subagentTypes);
		this.skillsDirectories = List.copyOf(skillsDirectories);
	}

	@Override
	public ToolCallback[] getToolCallbacks() {
		if (toolCallbacks == null) {
			synchronized (this) {
				if (toolCallbacks == null) {
					this.deferredBuild();
				}
			}
		}
		return Arrays.copyOf(this.toolCallbacks, this.toolCallbacks.length);
	}

	public static Builder builder() {
		return new Builder();
	}

	private void deferredBuild() {

		ToolCallback taskToolCallback2 = TaskTool.builder()
			.subagentReferences(this.subagentReferences)
			.subagentResolvers(this.subagentTypes.stream().map(SubagentType::resolver).toList())
			.subagentExecutors(new ClaudeSubagentExecutor(this.chatClientBuilderMap,
					this.defaultCoreToolCallbacks(this.chatClientBuilderMap.get("default"))))
			.subagentExecutors(this.subagentTypes.stream().map(SubagentType::executor).toList())
			.taskRepository(this.taskRepository)
			// .taskDescriptionTemplate(null)
			.build();

		ToolCallback taskOutputToolCallback = TaskOutputTool.builder().taskRepository(this.taskRepository).build();

		this.toolCallbacks = new ToolCallback[] { taskToolCallback2, taskOutputToolCallback };
	}

	public static class Builder {

		private String braveApiKey = System.getenv("BRAVE_API_KEY");

		private TaskRepository taskRepository = new DefaultTaskRepository();

		private Map<String, ChatClient.Builder> chatClientBuilderMap = new ConcurrentHashMap<>();

		private List<SubagentReference> subagentReferences = new ArrayList<>();

		private List<SubagentType> subagentTypes = new ArrayList<>();

		private List<String> skillsDirectories = new ArrayList<>();

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

		public Builder chatClientBuilder(String modelId, ChatClient.Builder chatClientBuilder) {
			Assert.notNull(modelId, "modelId must not be null");
			Assert.notNull(chatClientBuilder, "chatClientBuilder must not be null");
			this.chatClientBuilderMap.put(modelId, chatClientBuilder);
			return this;
		}

		public Builder chatClientBuilders(Map<String, ChatClient.Builder> chatClientBuilderMap) {
			Assert.notNull(chatClientBuilderMap, "chatClientBuilderMap must not be null");
			this.chatClientBuilderMap.putAll(chatClientBuilderMap);
			return this;
		}

		public Builder subagentReferences(List<SubagentReference> subagentReferences) {
			Assert.notNull(subagentReferences, "subagentReferences must not be null");
			this.subagentReferences.addAll(subagentReferences);
			return this;
		}

		public Builder subagentReferences(SubagentReference... subagentReferences) {
			Assert.notNull(subagentReferences, "subagentReferences must not be null");
			this.subagentReferences.addAll(List.of(subagentReferences));
			return this;
		}

		public Builder subagentTypes(List<SubagentType> subagentTypes) {
			Assert.notNull(subagentTypes, "subagentTypes must not be null");
			this.subagentTypes.addAll(subagentTypes);
			return this;
		}

		public Builder subagentTypes(SubagentType... subagentTypes) {
			Assert.notNull(subagentTypes, "subagentTypes must not be null");
			this.subagentTypes.addAll(List.of(subagentTypes));
			return this;
		}

		// Skills directories
		public Builder skillsResources(List<Resource> skillsRootPaths) {
			for (Resource skillsRootPath : skillsRootPaths) {
				this.skillsResource(skillsRootPath);
			}
			return this;
		}

		public Builder skillsResource(Resource skillsRootPath) {
			try {
				String path = skillsRootPath.getFile().toPath().toAbsolutePath().toString();
				this.skillsDirectories(path);
			}
			catch (IOException ex) {
				throw new RuntimeException("Failed to load skills from directory: " + skillsRootPath, ex);
			}
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

		public TaskToolCallbackProvider build() {
			Assert.isTrue(this.chatClientBuilderMap.containsKey("default"),
					"chatClientBuilderMap must contain a 'default' entry");
			return new TaskToolCallbackProvider(this.braveApiKey, this.taskRepository, this.chatClientBuilderMap,
					this.subagentReferences, this.subagentTypes, this.skillsDirectories);
		}

	}

	private List<ToolCallback> defaultCoreToolCallbacks(ChatClient.Builder chatClientBuilder) {

		List<ToolCallback> callbacks = new ArrayList<>();

		List<ToolCallback> callbacks2 = List.of(MethodToolCallbackProvider.builder()
			.toolObjects(TodoWriteTool.builder().build(), GrepTool.builder().build(), GlobTool.builder().build(),
					ShellTools.builder().build(), FileSystemTools.builder().build(),
					SmartWebFetchTool.builder(chatClientBuilder.clone().build()).build())
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

}
