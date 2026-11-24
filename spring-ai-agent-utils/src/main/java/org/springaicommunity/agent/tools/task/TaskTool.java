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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.agent.common.task.subagent.SubagentDefinition;
import org.springaicommunity.agent.common.task.subagent.SubagentExecutor;
import org.springaicommunity.agent.common.task.subagent.SubagentReference;
import org.springaicommunity.agent.common.task.subagent.SubagentResolver;
import org.springaicommunity.agent.common.task.subagent.SubagentType;
import org.springaicommunity.agent.common.task.subagent.TaskCall;
import org.springaicommunity.agent.common.task.subagent.TaskContext;
import org.springaicommunity.agent.tools.task.claude.ClaudeSubagentDefinition;
import org.springaicommunity.agent.tools.task.repository.DefaultTaskRepository;
import org.springaicommunity.agent.tools.task.repository.TaskRepository;

import org.springframework.ai.chat.client.advisor.api.MemoryAdvisor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.session.SessionService;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

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

	/**
	 * The function behind the tool. With a {@link SessionService}, and a calling request
	 * whose tool context names its session under {@link MemoryAdvisor#CONVERSATION_ID}
	 * (the session advisors put it there with {@code sessionIdInToolContext(true)}), every
	 * sub-agent run gets a session of its own under the caller's: the task and the answer
	 * are recorded on the caller's session (see {@link SubagentSessions}), the sub-agent's
	 * transcript is kept in the child, and the answer carries the child's id as the agent id
	 * to {@code resume} with.
	 */
	public static class TaskFunction implements BiFunction<TaskCall, ToolContext, String> {

		private static final Logger logger = LoggerFactory.getLogger(TaskFunction.class);

		private final TaskRepository taskRepository;

		private final Map<String, SubagentDefinition> subagents;

		private final Map<String, SubagentExecutor> subagentExecutors;

		/** Where the sub-agents' sessions live, or {@code null} when they have no memory. */
		private final SubagentSessions sessions;

		/** The sub-agent sessions with a task running now. */
		private final Set<String> runningAgents = ConcurrentHashMap.newKeySet();

		public TaskFunction(List<SubagentDefinition> subagents, List<SubagentExecutor> subagentExecutors,
				TaskRepository taskRepository) {
			this(subagents, subagentExecutors, taskRepository, null);
		}

		public TaskFunction(List<SubagentDefinition> subagents, List<SubagentExecutor> subagentExecutors,
				TaskRepository taskRepository, SessionService sessionService) {
			this.taskRepository = taskRepository;
			this.subagents = subagents.stream().collect(Collectors.toMap(sa -> sa.getName(), sa -> sa));
			this.subagentExecutors = subagentExecutors.stream().collect(Collectors.toMap(se -> se.getKind(), se -> se));
			this.sessions = (sessionService != null) ? new SubagentSessions(sessionService) : null;
		}

		@Override
		public String apply(TaskCall taskCall, ToolContext toolContext) {
			String subagentName = taskCall.subagent_type();
			if (!this.subagents.containsKey(subagentName)) {
				throw new RuntimeException("No subagent found with name: " + subagentName);
			}
			SubagentDefinition subagent = this.subagents.get(subagentName);
			SubagentExecutor subagentExecutor = this.subagentExecutors.get(subagent.getKind());
			if (subagentExecutor == null) {
				throw new RuntimeException("No subagent executor found for subagent kind: " + subagent.getKind());
			}
			Map<String, Object> context = (toolContext != null) ? toolContext.getContext() : Map.of();
			String callerSessionId = callerSessionId(context);

			if (Boolean.TRUE.equals(taskCall.run_in_background())) {
				var bgTask = this.taskRepository.putTask("task_" + UUID.randomUUID(),
						() -> run(subagentExecutor, taskCall, subagent, callerSessionId, context));
				return String.format(
						"task_id: %s\n\nBackground task started with ID: %s\nUse TaskOutput tool with task_id='%s' to retrieve results.",
						bgTask.getTaskId(), bgTask.getTaskId(), bgTask.getTaskId());
			}
			return run(subagentExecutor, taskCall, subagent, callerSessionId, context);
		}

		/** The calling agent's session, when sub-agents have memory and the request named it. */
		private String callerSessionId(Map<String, Object> toolContext) {
			if (this.sessions == null) {
				return null;
			}
			Object value = toolContext.get(MemoryAdvisor.CONVERSATION_ID);
			return (value instanceof String id && StringUtils.hasText(id)) ? id : null;
		}

		/**
		 * Runs the task. On a caller's session, and with an executor that keeps sessions,
		 * the sub-agent runs on a child of it, new or resumed, and the exchange is recorded
		 * on the caller's session.
		 */
		private String run(SubagentExecutor subagentExecutor, TaskCall taskCall, SubagentDefinition subagent,
				String callerSessionId, Map<String, Object> toolContext) {
			if (callerSessionId == null || !subagentExecutor.keepsSessions()) {
				if (callerSessionId != null) {
					logger.debug("Task: '{}' sub-agents ({} executor) keep no session; running without memory",
							subagent.getName(), subagent.getKind());
				}
				return withoutMemory(subagentExecutor, taskCall, subagent, toolContext);
			}
			SubagentSessions.Run run = this.sessions.open(callerSessionId, taskCall.resume(), subagent.getName(),
					modelOf(subagent));
			if (run == null) {
				logger.warn("Task: no session '{}' found for the calling agent; the sub-agent runs without memory",
						callerSessionId);
				return withoutMemory(subagentExecutor, taskCall, subagent, toolContext);
			}
			if (run.error() != null) {
				return run.error();
			}
			// One run at a time per sub-agent: two would interleave their turns in its log
			if (!this.runningAgents.add(run.sessionId())) {
				return "Error: Agent " + run.sessionId()
						+ " is still running a task; wait for its result before resuming it";
			}
			try {
				this.sessions.messageSent(callerSessionId, run);
				String result;
				try {
					result = execute(subagentExecutor, taskCall, subagent,
							new TaskContext(run.sessionId(), toolContext));
				}
				catch (RuntimeException ex) {
					this.sessions.messageReceived(callerSessionId, run,
							(ex.getMessage() != null) ? ex.getMessage() : ex.getClass().getName());
					throw ex;
				}
				this.sessions.messageReceived(callerSessionId, run, null);
				return "agent_id: " + run.sessionId() + "\n\n" + result;
			}
			finally {
				this.runningAgents.remove(run.sessionId());
			}
		}

		/**
		 * Runs the task on a fresh sub-agent. A {@code resume} that cannot be honoured is
		 * not silent: the answer tells the model that its earlier context was not there.
		 */
		private String withoutMemory(SubagentExecutor subagentExecutor, TaskCall taskCall,
				SubagentDefinition subagent, Map<String, Object> toolContext) {
			String result = execute(subagentExecutor, taskCall, subagent, new TaskContext(null, toolContext));
			if (!StringUtils.hasText(taskCall.resume())) {
				return result;
			}
			logger.warn("Task resume='{}' ignored: sub-agent memory is off. Give TaskTool and the subagent type a"
					+ " SessionService, and put the caller's session id in the tool context under '{}'"
					+ " (SessionAdvisors.builder(...).sessionIdInToolContext(true))", taskCall.resume(),
					MemoryAdvisor.CONVERSATION_ID);
			return "Note: agent " + taskCall.resume() + " could not be resumed, because sub-agent memory is off;"
					+ " this task ran on a new agent without its earlier context.\n\n" + result;
		}

		/** The model the sub-agent's definition declares, the one its executor runs it on. */
		private static String modelOf(SubagentDefinition subagent) {
			if (subagent instanceof ClaudeSubagentDefinition claude && StringUtils.hasText(claude.getModel())) {
				return claude.getModel();
			}
			return null;
		}

		private String execute(SubagentExecutor subagentExecutor, TaskCall taskCall, SubagentDefinition subagent,
				TaskContext context) {
			try {
				return subagentExecutor.execute(taskCall, subagent, context);
			}
			catch (Exception ex) {
				logger.error("Subagent '{}' (kind '{}') execution failed", subagent.getName(), subagent.getKind(), ex);
				throw ex;
			}
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

		private SessionService sessionService;

		private Builder() {
		}

		/**
		 * Gives sub-agents memory: each run gets a session of its own, a child of the
		 * calling agent's session, that keeps its transcript and can be resumed. The
		 * calling request must name its session in the tool context under
		 * {@link MemoryAdvisor#CONVERSATION_ID}, which the session advisors do with
		 * {@code SessionAdvisors.builder(sessionService).sessionIdInToolContext(true)};
		 * without it, or without this service, sub-agents keep nothing. Give the same
		 * service to the subagent types whose executors keep a transcript, like
		 * {@code ClaudeSubagentType}.
		 * @param sessionService where the sessions live
		 * @return this builder
		 */
		public Builder sessionService(SessionService sessionService) {
			Assert.notNull(sessionService, "sessionService must not be null");
			this.sessionService = sessionService;
			return this;
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
				.builder("Task",
						new TaskFunction(subagentDefinitions, executors, this.taskRepository, this.sessionService))
				.description(this.taskDescriptionTemplate.formatted(subagentRegistrations))
				.inputType(TaskCall.class)
				.build();
		}

	}

}
