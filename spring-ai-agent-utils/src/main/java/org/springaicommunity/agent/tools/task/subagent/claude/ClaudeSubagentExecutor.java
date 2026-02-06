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
package org.springaicommunity.agent.tools.task.subagent.claude;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.agent.common.task.subagent.SubagentDefinition;
import org.springaicommunity.agent.common.task.subagent.SubagentExecutor;
import org.springaicommunity.agent.common.task.subagent.TaskCall;
import org.springaicommunity.agent.utils.Skills;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.ToolCallAdvisor;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * Executes Claude subagent tasks using Spring AI ChatClient. Configures chat client with
 * appropriate tools based on subagent definition.
 *
 * @author Christian Tzolov
 */
public class ClaudeSubagentExecutor implements SubagentExecutor {

	private static final Logger logger = LoggerFactory.getLogger(ClaudeSubagentExecutor.class);

	private final Map<String, ChatClient.Builder> chatClientBuilderMap;

	private final List<ToolCallback> tools;

	private final List<String> skillsDirectories;

	public ClaudeSubagentExecutor(Map<String, ChatClient.Builder> chatClientBuilderMap, List<ToolCallback> tools,
			List<String> skillsDirectories) {

		Assert.notEmpty(chatClientBuilderMap, "chatClientBuilderMap must not be empty");
		Assert.isTrue(chatClientBuilderMap.containsKey("default"),
				"chatClientBuilderMap must contain a default ChatClient.Builder with key 'default'");

		Assert.notNull(skillsDirectories, "skillsDirectories must not be null");

		this.chatClientBuilderMap = chatClientBuilderMap;
		this.tools = tools;
		this.skillsDirectories = skillsDirectories;
	}

	@Override
	public String getKind() {
		return ClaudeSubagentDefinition.KIND;
	}

	@Override
	public String execute(TaskCall taskCall, SubagentDefinition subagent) {

		var claudeSubagent = (ClaudeSubagentDefinition) subagent;
		var taskChatClient = this.createTaskChatClient(claudeSubagent);

		String preloadedSkillsSystemSuffix = "";

		if (!CollectionUtils.isEmpty(claudeSubagent.skills()) && !CollectionUtils.isEmpty(this.skillsDirectories)) {

			// TODO Optimize loading skills only once and cache them.
			var skills = Skills.loadDirectories(this.skillsDirectories);

			preloadedSkillsSystemSuffix = "\n"
					+ skills.stream().filter(s -> claudeSubagent.skills().contains(s.name())).map(skill -> {
						var skillBaseDirectory = skill.path().getParent().toString();
						return "%s\nBase directory for this skill: %s\n\n%s".formatted(skill.toXml(),
								skillBaseDirectory, skill.content());
					}).collect(Collectors.joining("\n\n"));
		}

		return taskChatClient.prompt()
			.system(claudeSubagent.getContent() + preloadedSkillsSystemSuffix)
			.user(taskCall.prompt())
			// Todo set model if provided.
			.call()
			.content();
	}

	private ChatClient createTaskChatClient(ClaudeSubagentDefinition claudeSubagent) {

		var builder = this.doFindChatClientBuilder(claudeSubagent).clone();

		if (!CollectionUtils.isEmpty(this.tools)) {

			List<ToolCallback> subagentTools = new ArrayList<>(this.tools);

			// allowed tools filtering
			if (!CollectionUtils.isEmpty(claudeSubagent.tools())) {
				subagentTools = this.tools.stream()
					.filter(tc -> claudeSubagent.tools().contains(tc.getToolDefinition().name()))
					.toList();
			}

			// disallowed tools filtering
			if (!CollectionUtils.isEmpty(claudeSubagent.disallowedTools())) {
				subagentTools = subagentTools.stream()
					.filter(tc -> !claudeSubagent.disallowedTools().contains(tc.getToolDefinition().name()))
					.toList();
			}

			builder.defaultToolCallbacks(subagentTools);
		}

		if (!claudeSubagent.permissionMode().equals("default")) {
			logger.warn("The task permissionMode is not supported yet. permissionMode = "
					+ claudeSubagent.permissionMode());
		}

		// TODO Add ToolCallAdvisors only if not already present in the
		// ChatClient.Builder.
		return builder.defaultAdvisors(ToolCallAdvisor.builder().build()).build();
	}

	private static final Map<String, String> MODEL_NAME_MAPPER = Map.of("opus", "claude-opus-4-64k", "haiku",
			"claude-haiku-4-5-20251001", "sonnet", "claude-sonnet-4-5-20250929");

	protected ChatClient.Builder doFindChatClientBuilder(ClaudeSubagentDefinition claudeSubagent) {

		if (StringUtils.hasText(claudeSubagent.getModel())) {
			var providerName = "default";

			var modelRef = claudeSubagent.getModel();
			var modelName = modelRef.trim();

			if (modelRef.contains(":")) {
				var parts = modelRef.split(":");
				providerName = parts[0].trim();
				modelName = parts[1].trim();
			}

			if (this.chatClientBuilderMap.containsKey(providerName)) {
				var builder = this.chatClientBuilderMap.get(providerName);
				if (StringUtils.hasText(modelName)) {
					if (MODEL_NAME_MAPPER.containsKey(modelName)) {
						modelName = MODEL_NAME_MAPPER.get(modelName);
					}
					builder = builder.clone().defaultOptions(ChatOptions.builder().model(modelName).build());
				}
				return builder;
			}
		}

		// Return default chat client builder.
		return this.chatClientBuilderMap.get("default");
	}

}
