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
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.agent.common.task.subagent.SubagentDefinition;
import org.springaicommunity.agent.common.task.subagent.SubagentExecutor;
import org.springaicommunity.agent.common.task.subagent.SubagentReference;
import org.springaicommunity.agent.common.task.subagent.SubagentResolver;
import org.springaicommunity.agent.common.task.subagent.SubagentType;
import org.springaicommunity.agent.common.task.subagent.TaskCall;
import org.springaicommunity.agent.tools.task.repository.DefaultTaskRepository;
import org.springaicommunity.agent.tools.task.repository.TaskRepository;
import org.springaicommunity.agent.tools.task.subagent.claude.ClaudeSubagentDefinition;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.util.Assert;

/**
 * Tool for launching specialized subagents to handle complex, multi-step tasks. Supports
 * both synchronous and background execution modes.
 *
 * @author Christian Tzolov
 */
public class TaskTool {

	private static final String TASK_DESCRIPTION_TEMPLATE = """
			Launch a new agent to handle complex, multi-step tasks autonomously.

			The Task tool launches specialized agents (subprocesses) that autonomously handle complex tasks. Each agent type has specific capabilities and tools available to it.

			Available agent types and the tools they have access to:
			- general-purpose: General-purpose agent for researching complex questions, searching for code, and executing multi-step tasks. When you are searching for a keyword or file and are not confident that you will find the right match in the first few tries use this agent to perform the search for you. (Tools: *)
			- statusline-setup: Use this agent to configure the user's Claude Code status line setting. (Tools: Read, Edit)
			- Explore: Fast agent specialized for exploring codebases. Use this when you need to quickly find files by patterns (eg. "src/components/**/*.tsx"), search code for keywords (eg. "API endpoints"), or answer questions about the codebase (eg. "how do API endpoints work?"). When calling this agent, specify the desired thoroughness level: "quick" for basic searches, "medium" for moderate exploration, or "very thorough" for comprehensive analysis across multiple locations and naming conventions. (Tools: All tools)
			- Plan: Software architect agent for designing implementation plans. Use this when you need to plan the implementation strategy for a task. Returns step-by-step plans, identifies critical files, and considers architectural trade-offs. (Tools: All tools)
			- claude-code-guide: Use this agent when the user asks questions ("Can Claude...", "Does Claude...", "How do I...") about: (1) Claude Code (the CLI tool) - features, hooks, slash commands, MCP servers, settings, IDE integrations, keyboard shortcuts; (2) Claude Agent SDK - building custom agents; (3) Claude API (formerly Anthropic API) - API usage, tool use, Anthropic SDK usage. **IMPORTANT:** Before spawning a new agent, check if there is already a running or recently completed claude-code-guide agent that you can resume using the "resume" parameter. (Tools: Glob, Grep, Read, WebFetch, WebSearch)
			%s

			When using the Task tool, you must specify a subagent_type parameter to select which agent type to use.

			When NOT to use the Task tool:
			- If you want to read a specific file path, use the Read or Glob tool instead of the Task tool, to find the match more quickly
			- If you are searching for a specific class definition like "class Foo", use the Glob tool instead, to find the match more quickly
			- If you are searching for code within a specific file or set of 2-3 files, use the Read tool instead of the Task tool, to find the match more quickly
			- Other tasks that are not related to the agent descriptions above


			Usage notes:
			- Always include a short description (3-5 words) summarizing what the agent will do
			- Launch multiple agents concurrently whenever possible, to maximize performance; to do that, use a single message with multiple tool uses
			- When the agent is done, it will return a single message back to you. The result returned by the agent is not visible to the user. To show the user the result, you should send a text message back to the user with a concise summary of the result.
			- You can optionally run agents in the background using the run_in_background parameter. When an agent runs in the background, you will need to use TaskOutput to retrieve its results once it's done. You can continue to work while background agents run - When you need their results to continue you can use TaskOutput in blocking mode to pause and wait for their results.
			- When running tasks in the background, the Task tool will return a task_id immediately. Use the TaskOutput tool with this task_id to check status and retrieve results.
			- Agents can be resumed using the `resume` parameter by passing the agent ID from a previous invocation. When resumed, the agent continues with its full previous context preserved. When NOT resuming, each invocation starts fresh and you should provide a detailed task description with all necessary context.
			- When the agent is done, it will return a single message back to you along with its agent ID. You can use this ID to resume the agent later if needed for follow-up work.
			- Provide clear, detailed prompts so the agent can work autonomously and return exactly the information you need.
			- Agents with "access to current context" can see the full conversation history before the tool call. When using these agents, you can write concise prompts that reference earlier context (e.g., "investigate the error discussed above") instead of repeating information. The agent will receive all prior messages and understand the context.
			- The agent's outputs should generally be trusted
			- Clearly tell the agent whether you expect it to write code or just to do research (search, file reads, web fetches, etc.), since it is not aware of the user's intent
			- If the agent description mentions that it should be used proactively, then you should try your best to use it without the user having to ask for it first. Use your judgement.
			- If the user specifies that they want you to run agents "in parallel", you MUST send a single message with multiple Task tool use content blocks. For example, if you need to launch both a code-reviewer agent and a test-runner agent in parallel, send a single message with both tool calls.

			Example usage:

			<example_agent_descriptions>
			"code-reviewer": use this agent after you are done writing a signficant piece of code
			"greeting-responder": use this agent when to respond to user greetings with a friendly joke
			</example_agent_description>

			<example>
			user: "Please write a function that checks if a number is prime"
			assistant: Sure let me write a function that checks if a number is prime
			assistant: First let me use the Write tool to write a function that checks if a number is prime
			assistant: I'm going to use the Write tool to write the following code:
			<code>
			function isPrime(n) {
			if (n <= 1) return false
			for (let i = 2; i * i <= n; i++) {
				if (n %% i === 0) return false
			}
			return true
			}
			</code>
			<commentary>
			Since a signficant piece of code was written and the task was completed, now use the code-reviewer agent to review the code
			</commentary>
			assistant: Now let me use the code-reviewer agent to review the code
			assistant: Uses the Task tool to launch the code-reviewer agent
			</example>

			<example>
			user: "Hello"
			<commentary>
			Since the user is greeting, use the greeting-responder agent to respond with a friendly joke
			</commentary>
			assistant: "I'm going to use the Task tool to launch the greeting-responder agent"
			</example>
			""";

	public static class TaskFunction implements Function<TaskCall, String> {

		private static final Logger logger = LoggerFactory.getLogger(TaskFunction.class);

		// Storage for background tasks
		private final TaskRepository taskRepository;

		private final Map<String, SubagentDefinition> subagents;

		private final Map<String, SubagentExecutor> subagentExecutors;

		public TaskFunction(List<SubagentDefinition> subagents, List<SubagentExecutor> subagentExecutors,
				TaskRepository taskRepository) {
			this.taskRepository = taskRepository;
			this.subagents = subagents.stream().collect(Collectors.toMap(sa -> sa.getName(), sa -> sa));
			this.subagentExecutors = subagentExecutors.stream().collect(Collectors.toMap(se -> se.getKind(), se -> se));
		}

		@Override
		public String apply(TaskCall taskCall) {

			String subagentName = taskCall.subagent_type();

			if (!this.subagents.containsKey(subagentName)) {
				throw new RuntimeException("No subagent found with name: " + subagentName);
			}

			SubagentDefinition subagent = this.subagents.get(subagentName);

			SubagentExecutor subagentExecutor = this.subagentExecutors.get(subagent.getKind());

			if (subagentExecutor == null) {
				throw new RuntimeException("No subagent executor found for subagent kind: " + subagent.getKind());
			}

			if (Boolean.TRUE.equals(taskCall.run_in_background())) {
				// Create background task using CompletableFuture
				var bgTask = this.taskRepository.putTask("task_" + UUID.randomUUID(),
						() -> subagentExecutor.execute(taskCall, subagent));

				return String.format(
						"task_id: %s\n\nBackground task started with ID: %s\nUse TaskOutput tool with task_id='%s' to retrieve results.",
						bgTask.getTaskId(), bgTask.getTaskId(), bgTask.getTaskId());
			}

			// Synchronous execution (existing behavior)
			return subagentExecutor.execute(taskCall, subagent);
		}

	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private List<SubagentReference> subagentReferences = new ArrayList<>();

		private List<SubagentType> subagentTypes = new ArrayList<>();

		private String taskDescriptionTemplate = TASK_DESCRIPTION_TEMPLATE;

		private TaskRepository taskRepository = new DefaultTaskRepository();

		private Builder() {
		}

		public Builder subagentReferences(List<SubagentReference> subagentReferences) {
			this.subagentReferences.addAll(subagentReferences);
			return this;
		}

		public Builder subagentReferences(SubagentReference... subagentReference) {
			this.subagentReferences.addAll(List.of(subagentReference));
			return this;
		}

		public Builder taskRepository(TaskRepository taskRepository) {
			Assert.notNull(taskRepository, "taskRepository must not be null");
			this.taskRepository = taskRepository;
			return this;
		}

		public Builder taskDescriptionTemplate(String template) {
			Assert.hasText(template, "template must not be empty");
			this.taskDescriptionTemplate = template;
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

		private SubagentDefinition resolve(SubagentReference subagentReference) {
			for (SubagentResolver subagentResolver : this.subagentTypes.stream().map(st -> st.resolver()).toList()) {
				if (subagentResolver.canResolve(subagentReference)) {
					return subagentResolver.resolve(subagentReference);
				}
			}
			throw new RuntimeException(
					"No SubagentResolver found that can resolve subagent reference: " + subagentReference);
		}

		public ToolCallback build() {
			Assert.notNull(this.taskRepository, "taskRepository must be provided");

			if (this.subagentTypes.stream().anyMatch(st -> st.kind().equals(ClaudeSubagentDefinition.KIND))) {
				// Register built-in Claude subagent references
				this.subagentReferences.add(new SubagentReference("classpath:/agent/GENERAL_PURPOSE_SUBAGENT.md",
						ClaudeSubagentDefinition.KIND));
				this.subagentReferences
					.add(new SubagentReference("classpath:/agent/EXPLORE_SUBAGENT.md", ClaudeSubagentDefinition.KIND));
				this.subagentReferences
					.add(new SubagentReference("classpath:/agent/PLAN_SUBAGENT.md", ClaudeSubagentDefinition.KIND));
				this.subagentReferences
					.add(new SubagentReference("classpath:/agent/BASH_SUBAGENT.md", ClaudeSubagentDefinition.KIND));
			}

			Assert.notEmpty(this.subagentTypes, "At least one subagentTypes must be provided");

			List<SubagentDefinition> subagentDefinitions = this.subagentReferences.stream()
				.map(sr -> this.resolve(sr))
				.toList();

			String subagentRegistrations = subagentDefinitions.stream()
				.map(sa -> sa.toSubagentRegistrations())
				.collect(Collectors.joining("\n"));

			var executors = this.subagentTypes.stream().map(st -> st.executor()).toList();

			return FunctionToolCallback
				.builder("Task", new TaskFunction(subagentDefinitions, executors, this.taskRepository))
				.description(this.taskDescriptionTemplate.formatted(subagentRegistrations))
				.inputType(TaskCall.class)
				.build();
		}

	}

}
