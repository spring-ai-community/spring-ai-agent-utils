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
package org.springaicommunity.agent.tools.task.claude;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.agent.common.task.subagent.SubagentDefinition;
import org.springaicommunity.agent.common.task.subagent.SubagentExecutor;
import org.springaicommunity.agent.common.task.subagent.TaskCall;
import org.springaicommunity.agent.common.task.subagent.TaskContext;
import org.springaicommunity.agent.utils.Skills;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.model.tool.ToolCallDecisions;
import org.springframework.ai.session.SessionService;
import org.springframework.ai.session.advisor.SessionAdvisors;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * Executes Claude subagent tasks using Spring AI ChatClient. Configures chat client with
 * appropriate tools based on subagent definition. Given a {@link SessionService}, a task
 * that comes with a session of its own (see {@link TaskContext#sessionId()}) runs with the
 * session advisors ({@link SessionAdvisors}) on that session: the sub-agent's transcript is
 * kept there, a resumed sub-agent continues with its earlier turns, and its tools see that
 * session as theirs, so a nested Task tool creates its children under it.
 * <p>
 * A sub-agent has nobody to ask: when one of its tool calls needs a person's confirmation
 * (see {@code ToolCallConfirmationPolicy}), the task ends there, and its answer says which
 * calls did not run instead of being empty.
 *
 * @author Christian Tzolov
 */
public class ClaudeSubagentExecutor implements SubagentExecutor {

	private static final Logger logger = LoggerFactory.getLogger(ClaudeSubagentExecutor.class);

	private final Map<String, ChatClient.Builder> chatClientBuilderMap;

	private final List<ToolCallback> tools;

	private final List<String> skillsDirectories;

	/** Keep a sub-agent's transcript in its session; {@code null} when sub-agents have no memory. */
	private final List<Advisor> memoryAdvisors;

	public ClaudeSubagentExecutor(Map<String, ChatClient.Builder> chatClientBuilderMap, List<ToolCallback> tools,
			List<String> skillsDirectories) {
		this(chatClientBuilderMap, tools, skillsDirectories, (List<Advisor>) null);
	}

	/**
	 * @param sessionService where the sub-agents' sessions live, the same service the
	 * {@code TaskTool} creates them in; {@code null} when sub-agents have no memory
	 */
	public ClaudeSubagentExecutor(Map<String, ChatClient.Builder> chatClientBuilderMap, List<ToolCallback> tools,
			List<String> skillsDirectories, SessionService sessionService) {
		this(chatClientBuilderMap, tools, skillsDirectories,
				(sessionService != null) ? defaultSessionAdvisors(sessionService) : null);
	}

	/**
	 * @param sessionAdvisors the advisors that keep a sub-agent's transcript in its session,
	 * added only to a task that runs on a session; {@code null} when sub-agents have no
	 * memory. See {@link #defaultSessionAdvisors(SessionService)}.
	 */
	public ClaudeSubagentExecutor(Map<String, ChatClient.Builder> chatClientBuilderMap, List<ToolCallback> tools,
			List<String> skillsDirectories, List<Advisor> sessionAdvisors) {

		Assert.notEmpty(chatClientBuilderMap, "chatClientBuilderMap must not be empty");
		Assert.isTrue(chatClientBuilderMap.containsKey("default"),
				"chatClientBuilderMap must contain a default ChatClient.Builder with key 'default'");

		Assert.notNull(skillsDirectories, "skillsDirectories must not be null");

		this.chatClientBuilderMap = chatClientBuilderMap;
		this.tools = tools;
		this.skillsDirectories = skillsDirectories;
		this.memoryAdvisors = (sessionAdvisors != null) ? List.copyOf(sessionAdvisors) : null;
	}

	/**
	 * The advisors a sub-agent's session gets by default: Spring AI's session advisors,
	 * with the session ID in the tool context, so that a nested Task tool creates its
	 * children under the sub-agent's session.
	 */
	public static List<Advisor> defaultSessionAdvisors(SessionService sessionService) {
		Assert.notNull(sessionService, "sessionService must not be null");
		return SessionAdvisors.builder(sessionService).sessionIdInToolContext(true).build();
	}

	@Override
	public String getKind() {
		return ClaudeSubagentDefinition.KIND;
	}

	@Override
	public String execute(TaskCall taskCall, SubagentDefinition subagent) {
		return run(taskCall, subagent, null);
	}

	/** Whether this executor was given session advisors, so that sub-agents keep a transcript. */
	@Override
	public boolean keepsSessions() {
		return this.memoryAdvisors != null;
	}

	@Override
	public String execute(TaskCall taskCall, SubagentDefinition subagent, TaskContext context) {
		if (this.memoryAdvisors == null || context.sessionId() == null) {
			// Without a session there is nothing to add: the two-argument method runs, so a
			// subclass that overrides it stays in effect
			return execute(taskCall, subagent);
		}
		return run(taskCall, subagent, context.sessionId());
	}

	/** Runs the task, on the sub-agent's session when there is one. */
	private String run(TaskCall taskCall, SubagentDefinition subagent, String sessionId) {

		var claudeSubagent = (ClaudeSubagentDefinition) subagent;
		var taskChatClient = this.createTaskChatClient(claudeSubagent);

		String preloadedSkillsSystemSuffix = "";

		if (!CollectionUtils.isEmpty(claudeSubagent.skills()) && !CollectionUtils.isEmpty(this.skillsDirectories)) {

			// TODO Optimize loading skills only once and cache them.
			var skills = Skills.loadDirectories(this.skillsDirectories);

			preloadedSkillsSystemSuffix = "\n"
					+ skills.stream().filter(s -> claudeSubagent.skills().contains(s.name())).map(skill -> {
						return "%s\nBase directory for this skill: %s\n\n%s".formatted(skill.toXml(),
								skill.basePath(), skill.content());
					}).collect(Collectors.joining("\n\n"));
		}

		var request = taskChatClient.prompt()
			.system(claudeSubagent.getContent() + preloadedSkillsSystemSuffix)
			.user(taskCall.prompt());
		if (sessionId != null) {
			// The transcript goes to the sub-agent's session, and its tools see that
			// session as theirs (a nested Task tool creates its children under it)
			request.advisors(this.memoryAdvisors).advisors(SessionAdvisors.session(sessionId));
		}
		ChatResponse response = request.call().chatResponse();
		List<String> awaiting = ToolCallDecisions.awaitingDecision(response);
		if (!awaiting.isEmpty()) {
			return pausedAnswer(response, awaiting, sessionId != null);
		}
		return textOf(response);
	}

	private static String textOf(ChatResponse response) {
		return (response != null && response.getResult() != null) ? response.getResult().getOutput().getText() : null;
	}

	/**
	 * The answer of a sub-agent whose tool calls wait for a person's confirmation. No call
	 * of that reply ran, and nobody can decide here, so the caller is told what happened
	 * instead of getting the paused reply's text, which is usually empty.
	 */
	private static String pausedAnswer(ChatResponse response, List<String> awaiting, boolean resumable) {
		AssistantMessage reply = response.getResult().getOutput();
		List<String> keys = ToolCallDecisions.toolCallKeys(reply);
		List<String> toolNames = new ArrayList<>();
		for (int i = 0; i < keys.size(); i++) {
			String name = reply.getToolCalls().get(i).name();
			if (awaiting.contains(keys.get(i)) && !toolNames.contains(name)) {
				toolNames.add(name);
			}
		}
		String text = StringUtils.hasText(reply.getText()) ? reply.getText().strip() + "\n\n" : "";
		return text + "[The sub-agent stopped before it finished: its call to " + String.join(", ", toolNames)
				+ " needs a person's confirmation, and a sub-agent cannot ask for one. None of the "
				+ keys.size() + " tool call(s) of its last reply ran."
				+ (resumable ? " If you resume this agent, those calls are answered as not run and it goes on without"
						+ " their results." : "")
				+ "]";
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

			builder.defaultTools(subagentTools);
		}

		if (!claudeSubagent.permissionMode().equals("default")) {
			logger.warn("The task permissionMode is not supported yet. permissionMode = "
					+ claudeSubagent.permissionMode());
		}

		return builder.build();
	}

	private static final Map<String, String> MODEL_NAME_MAPPER = Map.of("opus", "claude-opus-4-64k", "haiku",
			"claude-haiku-4-5-20251001", "sonnet", "claude-sonnet-4-5-20250929");

	/**
	 * Resolves the appropriate {@link ChatClient.Builder} for the given subagent
	 * definition based on its model specification.
	 *
	 * <p>
	 * The model reference supports two formats:
	 * <ul>
	 * <li>{@code model} - uses the default provider with the specified model (e.g.,
	 * {@code "sonnet"} or {@code "gpt-4o"})</li>
	 * <li>{@code provider:model} - uses the named provider with the specified model
	 * (e.g., {@code "openai:gpt-4o"} or {@code "anthropic:claude-sonnet-4-5-20250929"})</li>
	 * </ul>
	 *
	 * <p>
	 * The provider can be any Spring AI supported LLM provider registered in the
	 * builder map, and the model can be either a short alias ({@code opus},
	 * {@code haiku}, {@code sonnet}) or the full model identifier of the target
	 * provider. If the specified provider is not found or no model is specified, the
	 * default builder is returned.
	 * @param claudeSubagent the subagent definition containing the model specification
	 * @return a {@link ChatClient.Builder} configured for the requested provider and
	 * model, or the default builder as a fallback
	 */
	protected ChatClient.Builder doFindChatClientBuilder(ClaudeSubagentDefinition claudeSubagent) {

		if (StringUtils.hasText(claudeSubagent.getModel())) {
			var providerName = "default";

			var modelRef = claudeSubagent.getModel();
			var modelName = modelRef.trim();

			if (modelRef.contains(":")) {
				var parts = modelRef.split(":");
				if (StringUtils.hasText(parts[0])) {
					providerName = parts[0].trim();
				}
				if (StringUtils.hasText(parts[1])) {
					modelName = parts[1].trim();
				}
			}

			if (this.chatClientBuilderMap.containsKey(providerName)) {
				var builder = this.chatClientBuilderMap.get(providerName);
				if (StringUtils.hasText(modelName)) {
					if (MODEL_NAME_MAPPER.containsKey(modelName)) {
						modelName = MODEL_NAME_MAPPER.get(modelName);
					}
					builder = builder.clone().defaultOptions(ChatOptions.builder().model(modelName));
				}
				return builder;
			}
		}

		// Return default chat client builder.
		return this.chatClientBuilderMap.get("default");
	}

}
