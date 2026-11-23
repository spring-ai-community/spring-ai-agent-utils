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
package org.springaicommunity.agent.tools.task.claude;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.springaicommunity.agent.common.task.subagent.SubagentDefinition;
import org.springaicommunity.agent.common.task.subagent.SubagentReference;
import org.springaicommunity.agent.common.task.subagent.TaskCall;
import org.springaicommunity.agent.common.task.subagent.TaskContext;
import org.springaicommunity.agent.tools.task.SubagentSessions;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.MemoryAdvisor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallConfirmationPolicy;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.session.CreateSessionRequest;
import org.springframework.ai.session.DefaultSessionService;
import org.springframework.ai.session.InMemorySessionRepository;
import org.springframework.ai.session.Session;
import org.springframework.ai.session.SessionService;
import org.springframework.ai.session.advisor.SessionTurnAdvisor;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the memory of {@link ClaudeSubagentExecutor}: with a session service, a task
 * that comes with a session keeps its transcript there, a resumed sub-agent sees its
 * earlier turns, and its tools are told that session is theirs. Also for the answer of a
 * sub-agent whose tool call needs a person's confirmation.
 *
 * @author Christian Tzolov
 */
class ClaudeSubagentExecutorTest {

	private final List<Prompt> prompts = new ArrayList<>();

	/** How many of the next replies ask for the {@code noop} tool instead of answering. */
	private int toolCallReplies = 0;

	/** Answers every prompt; its options are tool-calling ones, as a real model's are. */
	private final ChatModel model = new ChatModel() {

		@Override
		public ChatResponse call(Prompt prompt) {
			ClaudeSubagentExecutorTest.this.prompts.add(prompt);
			if (ClaudeSubagentExecutorTest.this.toolCallReplies > 0) {
				ClaudeSubagentExecutorTest.this.toolCallReplies--;
				return new ChatResponse(List.of(new Generation(AssistantMessage.builder()
					.content("")
					.toolCalls(List.of(new AssistantMessage.ToolCall(
							"call-" + ClaudeSubagentExecutorTest.this.prompts.size(), "function", "noop", "{}")))
					.build())));
			}
			return new ChatResponse(List.of(new Generation(
					new AssistantMessage("answer " + ClaudeSubagentExecutorTest.this.prompts.size()))));
		}

		@Override
		public ChatOptions getOptions() {
			return ToolCallingChatOptions.builder().build();
		}

	};

	private final SessionService sessions = DefaultSessionService.builder()
		.sessionRepository(InMemorySessionRepository.builder().build())
		.build();

	private final ClaudeSubagentDefinition reviewer = new ClaudeSubagentDefinition(
			new SubagentReference("file:reviewer.md", ClaudeSubagentDefinition.KIND),
			Map.of("name", "reviewer", "description", "Reviews"), "You review.");

	private ClaudeSubagentExecutor executor(SessionService sessionService) {
		ToolCallback tool = FunctionToolCallback.builder("noop", () -> "ok").description("Does nothing").build();
		return new ClaudeSubagentExecutor(Map.of("default", ChatClient.builder(this.model)), List.of(tool), List.of(),
				sessionService);
	}

	/** An executor whose sub-agents need a person's confirmation to call the {@code noop} tool. */
	private ClaudeSubagentExecutor confirmingExecutor() {
		ToolCallback tool = FunctionToolCallback.builder("noop", () -> "ok").description("Does nothing").build();
		ToolCallingManager manager = ToolCallingManager.builder()
			.toolCallConfirmationPolicy(ToolCallConfirmationPolicy.forTools("noop"))
			.build();
		ChatClient.Builder builder = ChatClient.builder(this.model, ObservationRegistry.NOOP, null, null,
				ToolCallingAdvisor.builder().toolCallingManager(manager));
		return new ClaudeSubagentExecutor(Map.of("default", builder), List.of(tool), List.of(), this.sessions);
	}

	/** Counts the calls it sees. */
	private static final class CountingAdvisor implements CallAdvisor {

		private int calls;

		@Override
		public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
			this.calls++;
			return chain.nextCall(request);
		}

		@Override
		public String getName() {
			return "counting";
		}

		@Override
		public int getOrder() {
			return 0;
		}

	}

	/** A sub-agent session, as the Task tool creates it. */
	private Session childOf(Session caller) {
		return this.sessions.create(CreateSessionRequest.builder()
			.metadata(SubagentSessions.PARENT_SESSION_ID, caller.id())
			.build());
	}

	private static TaskCall call(String prompt, String resume) {
		return new TaskCall("desc", prompt, "reviewer", null, resume, null);
	}

	private List<String> texts(Prompt prompt) {
		return prompt.getInstructions().stream().map(Message::getText).toList();
	}

	private static Map<String, Object> toolContext(Prompt prompt) {
		Map<String, Object> toolContext = ((ToolCallingChatOptions) prompt.getOptions()).getToolContext();
		return (toolContext != null) ? toolContext : Map.of();
	}

	@Test
	void aSubagentWithASessionRemembersItsEarlierTurns() {
		Session caller = this.sessions.create(CreateSessionRequest.builder().build());
		Session child = childOf(caller);
		TaskContext context = new TaskContext(child.id(), Map.of(MemoryAdvisor.CONVERSATION_ID, caller.id()));
		ClaudeSubagentExecutor executor = executor(this.sessions);

		assertThat(executor.execute(call("first", null), this.reviewer, context)).isEqualTo("answer 1");
		assertThat(executor.execute(call("second", child.id()), this.reviewer, context)).isEqualTo("answer 2");

		// The second task's prompt carries the first exchange, after the system prompt
		assertThat(texts(this.prompts.get(1))).containsExactly("You review.", "first", "answer 1", "second");
		// The transcript is in the child's log, and only there
		assertThat(this.sessions.getActiveMessages(child.id())).extracting(Message::getText)
			.containsExactly("first", "answer 1", "second", "answer 2");
		assertThat(this.sessions.getActiveMessages(caller.id())).isEmpty();
		// The sub-agent's tools are told the child is their session, not the caller
		assertThat(toolContext(this.prompts.get(1))).containsEntry(MemoryAdvisor.CONVERSATION_ID, child.id());
	}

	@Test
	void aTaskWithoutASessionRunsWithoutMemory() {
		ClaudeSubagentExecutor executor = executor(this.sessions);

		assertThat(executor.execute(call("first", null), this.reviewer)).isEqualTo("answer 1");
		assertThat(executor.execute(call("second", null), this.reviewer, TaskContext.NONE)).isEqualTo("answer 2");

		assertThat(texts(this.prompts.get(1))).containsExactly("You review.", "second");
		assertThat(toolContext(this.prompts.get(1))).doesNotContainKey(MemoryAdvisor.CONVERSATION_ID);
	}

	@Test
	void withoutASessionServiceTheSessionIsIgnored() {
		Session caller = this.sessions.create(CreateSessionRequest.builder().build());
		Session child = childOf(caller);
		ClaudeSubagentExecutor executor = executor(null);

		assertThat(executor.execute(call("first", null), this.reviewer, new TaskContext(child.id(), Map.of())))
			.isEqualTo("answer 1");

		assertThat(this.sessions.getActiveMessages(child.id())).isEmpty();
		assertThat(toolContext(this.prompts.get(0))).doesNotContainKey(MemoryAdvisor.CONVERSATION_ID);
	}

	@Test
	void keepsSessionsOnlyWithASessionService() {
		assertThat(executor(this.sessions).keepsSessions()).isTrue();
		assertThat(executor(null).keepsSessions()).isFalse();
	}

	@Test
	void aSubclassOverrideOfTheTwoArgumentExecuteStaysInEffectWithoutASession() {
		ToolCallback tool = FunctionToolCallback.builder("noop", () -> "ok").description("Does nothing").build();
		ClaudeSubagentExecutor custom = new ClaudeSubagentExecutor(Map.of("default", ChatClient.builder(this.model)),
				List.of(tool), List.of(), this.sessions) {
			@Override
			public String execute(TaskCall taskCall, SubagentDefinition subagent) {
				return "custom " + super.execute(taskCall, subagent);
			}
		};

		assertThat(custom.execute(call("first", null), this.reviewer, TaskContext.NONE)).isEqualTo("custom answer 1");
	}

	@Test
	void sessionAdvisorsOfYourChoiceReplaceTheDefaultOnesAndRunOnlyWithASession() {
		Session caller = this.sessions.create(CreateSessionRequest.builder().build());
		Session child = childOf(caller);
		CountingAdvisor counting = new CountingAdvisor();
		List<Advisor> advisors = new ArrayList<>(ClaudeSubagentExecutor.defaultSessionAdvisors(this.sessions));
		advisors.add(counting);
		ToolCallback tool = FunctionToolCallback.builder("noop", () -> "ok").description("Does nothing").build();
		ClaudeSubagentExecutor executor = new ClaudeSubagentExecutor(
				Map.of("default", ChatClient.builder(this.model)), List.of(tool), List.of(), advisors);

		assertThat(executor.keepsSessions()).isTrue();
		assertThat(executor.execute(call("first", null), this.reviewer, new TaskContext(child.id(), Map.of())))
			.isEqualTo("answer 1");
		assertThat(counting.calls).isEqualTo(1);
		assertThat(this.sessions.getActiveMessages(child.id())).extracting(Message::getText)
			.containsExactly("first", "answer 1");

		// A task without a session does not get them: they would fail without a session ID
		assertThat(executor.execute(call("second", null), this.reviewer, TaskContext.NONE)).isEqualTo("answer 2");
		assertThat(counting.calls).isEqualTo(1);
	}

	@Test
	void theSubagentTypeBuilderTakesSessionAdvisors() {
		var withAdvisors = ClaudeSubagentType.builder()
			.chatClientBuilder("default", ChatClient.builder(this.model))
			.sessionAdvisors(ClaudeSubagentExecutor.defaultSessionAdvisors(this.sessions))
			.build();
		var withService = ClaudeSubagentType.builder()
			.chatClientBuilder("default", ChatClient.builder(this.model))
			.sessionService(this.sessions)
			.build();
		var without = ClaudeSubagentType.builder().chatClientBuilder("default", ChatClient.builder(this.model)).build();

		assertThat(withAdvisors.executor().keepsSessions()).isTrue();
		assertThat(withService.executor().keepsSessions()).isTrue();
		assertThat(without.executor().keepsSessions()).isFalse();
	}

	@Test
	void aSubagentWhoseToolCallNeedsConfirmationSaysSoAndCanBeResumed() {
		Session caller = this.sessions.create(CreateSessionRequest.builder().build());
		Session child = childOf(caller);
		TaskContext context = new TaskContext(child.id(), Map.of());
		ClaudeSubagentExecutor executor = confirmingExecutor();
		this.toolCallReplies = 1;

		String paused = executor.execute(call("first", null), this.reviewer, context);

		assertThat(paused).contains("its call to noop needs a person's confirmation")
			.contains("None of the 1 tool call(s) of its last reply ran")
			.contains("If you resume this agent");
		assertThat(this.prompts).hasSize(1);

		// Resumed, the open call is answered as not run and the sub-agent goes on
		assertThat(executor.execute(call("second", child.id()), this.reviewer, context)).isEqualTo("answer 2");
		List<Message> log = this.sessions.getActiveMessages(child.id());
		assertThat(log).extracting(Message::getMessageType)
			.containsExactly(MessageType.USER, MessageType.ASSISTANT, MessageType.TOOL, MessageType.USER,
					MessageType.ASSISTANT);
		assertThat(((ToolResponseMessage) log.get(2)).getResponses().get(0).responseData())
			.isEqualTo(SessionTurnAdvisor.UNANSWERED_TOOL_RESULT);
	}

	@Test
	void aPausedSubagentWithoutASessionIsNotOfferedAResume() {
		ClaudeSubagentExecutor executor = confirmingExecutor();
		this.toolCallReplies = 1;

		String paused = executor.execute(call("first", null), this.reviewer, TaskContext.NONE);

		assertThat(paused).contains("its call to noop needs a person's confirmation").doesNotContain("resume");
	}

}
