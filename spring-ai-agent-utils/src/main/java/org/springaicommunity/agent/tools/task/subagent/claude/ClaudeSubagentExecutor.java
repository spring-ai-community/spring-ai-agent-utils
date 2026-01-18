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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.agent.tools.task.TaskTool.TaskCall;
import org.springaicommunity.agent.tools.task.subagent.Kind;
import org.springaicommunity.agent.tools.task.subagent.Subagent;
import org.springaicommunity.agent.tools.task.subagent.SubagentExecutor;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.ToolCallAdvisor;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * @author Christian Tzolov
 */
public class ClaudeSubagentExecutor implements SubagentExecutor {

	private static final Logger logger = LoggerFactory.getLogger(ClaudeSubagentExecutor.class);

	private final Map<String, ChatClient.Builder> chatClientBuilderMap;

	private final List<ToolCallback> tools;

	public ClaudeSubagentExecutor(Map<String, ChatClient.Builder> chatClientBuilderMap, List<ToolCallback> tools) {

		Assert.notEmpty(chatClientBuilderMap, "chatClientBuilderMap must not be empty");
		Assert.isTrue(chatClientBuilderMap.containsKey("default"),
				"chatClientBuilderMap must contain a default ChatClient.Builder with key 'default'");

		this.chatClientBuilderMap = chatClientBuilderMap;
		this.tools = tools;
	}

	@Override
	public String getKind() {
		return Kind.CLAUDE_SUBAGENT.name();
	}

	@Override
	public String execute(TaskCall taskCall, Subagent subagent) {

		var claudeSubagent = (ClaudeSubagent) subagent;
		var taskChatClient = this.createTaskChatClient(claudeSubagent);

		return taskChatClient.prompt()
			.system(claudeSubagent.getContent()) // Todo add the system suffix
			.user(taskCall.prompt())
			// Todo set model if provided.
			.call()
			.content();
	}

	private ChatClient createTaskChatClient(ClaudeSubagent claudeSubagent) {

		var builder = this.findChatClientBuilder(claudeSubagent).clone();

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

		// 		if (!CollectionUtils.isEmpty(claudeSubagent.skills()) && this.skillsTool != null) {
		// 	// this.skillsTool.
		// }

		// if (!CollectionUtils.isEmpty(taskType.skills())) {
		// logger.warn(
		// "The task skills filtering are not supported yet. skills = " + String.join(",",
		// taskType.skills()));
		// }

		return builder.defaultAdvisors(ToolCallAdvisor.builder().build()).build();
	}

	private ChatClient.Builder findChatClientBuilder(ClaudeSubagent claudeSubagent) {

		if (StringUtils.hasText(claudeSubagent.getModel())
				&& this.chatClientBuilderMap.containsKey(claudeSubagent.getModel())) {
			return this.chatClientBuilderMap.get(claudeSubagent.getModel());
		}

		// Return default chat client builder.
		return this.chatClientBuilderMap.get("default");
	}

}
