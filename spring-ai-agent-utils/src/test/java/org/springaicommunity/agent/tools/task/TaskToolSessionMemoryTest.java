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
package org.springaicommunity.agent.tools.task;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springaicommunity.agent.common.task.subagent.SubagentDefinition;
import org.springaicommunity.agent.common.task.subagent.SubagentExecutor;
import org.springaicommunity.agent.common.task.subagent.SubagentReference;
import org.springaicommunity.agent.common.task.subagent.TaskCall;
import org.springaicommunity.agent.common.task.subagent.TaskContext;
import org.springaicommunity.agent.tools.task.claude.ClaudeSubagentDefinition;
import org.springaicommunity.agent.tools.task.repository.BackgroundTask;
import org.springaicommunity.agent.tools.task.repository.DefaultTaskRepository;

import org.springframework.ai.chat.client.advisor.api.MemoryAdvisor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.session.CreateSessionRequest;
import org.springframework.ai.session.DefaultSessionService;
import org.springframework.ai.session.InMemorySessionRepository;
import org.springframework.ai.session.Session;
import org.springframework.ai.session.SessionAttributes;
import org.springframework.ai.session.SessionEvent;
import org.springframework.ai.session.SessionService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springaicommunity.agent.tools.task.SubagentSessions.CREATED;
import static org.springaicommunity.agent.tools.task.SubagentSessions.MESSAGE_RECEIVED;
import static org.springaicommunity.agent.tools.task.SubagentSessions.MESSAGE_SENT;

/**
 * Tests for the sub-agent memory of {@link TaskTool.TaskFunction}: a sub-agent runs on a
 * child of the caller's session, the exchange is recorded on the caller's, and
 * {@code resume} continues the child.
 *
 * @author Christian Tzolov
 */
class TaskToolSessionMemoryTest {

	private final SessionService sessions = DefaultSessionService.builder()
		.sessionRepository(InMemorySessionRepository.builder().build())
		.build();

	private final SubagentSessions subagentSessions = new SubagentSessions(this.sessions);

	private final List<TaskContext> contexts = new ArrayList<>();

	private final SubagentExecutor executor = new SubagentExecutor() {
		@Override
		public String getKind() {
			return ClaudeSubagentDefinition.KIND;
		}

		@Override
		public String execute(TaskCall taskCall, SubagentDefinition subagent) {
			return "Executed without context: " + taskCall.prompt();
		}

		@Override
		public String execute(TaskCall taskCall, SubagentDefinition subagent, TaskContext context) {
			TaskToolSessionMemoryTest.this.contexts.add(context);
			if (taskCall.prompt().startsWith("fail")) {
				throw new IllegalStateException("model unavailable");
			}
			if (TaskToolSessionMemoryTest.this.blockRun != null) {
				TaskToolSessionMemoryTest.this.started.countDown();
				awaitQuietly(TaskToolSessionMemoryTest.this.blockRun);
			}
			return "Executed: " + taskCall.prompt();
		}

		@Override
		public boolean keepsSessions() {
			return true;
		}
	};

	/** When set, a run waits for it, after counting down {@link #started}. */
	private volatile CountDownLatch blockRun;

	private final CountDownLatch started = new CountDownLatch(1);

	private static void awaitQuietly(CountDownLatch latch) {
		try {
			latch.await(5, TimeUnit.SECONDS);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

	private final List<SubagentDefinition> subagents = List.of(definition("reviewer"), definition("writer"));

	private final DefaultTaskRepository repository = new DefaultTaskRepository(2);

	private final TaskTool.TaskFunction task = new TaskTool.TaskFunction(this.subagents, List.of(this.executor),
			this.repository, this.sessions);

	private static SubagentDefinition definition(String name) {
		return new ClaudeSubagentDefinition(new SubagentReference("file:" + name + ".md", ClaudeSubagentDefinition.KIND),
				Map.of("name", name, "description", "A " + name, "model", "sonnet"), "You are a " + name);
	}

	private static ToolContext calledFrom(String sessionId) {
		return new ToolContext(Map.of(MemoryAdvisor.CONVERSATION_ID, sessionId));
	}

	private static TaskCall call(String prompt, String subagent, String resume) {
		return new TaskCall("desc", prompt, subagent, null, resume, null);
	}

	@Test
	void aSubagentRunsOnAChildOfTheCallersSessionAndTheExchangeIsRecorded() {
		Session caller = this.sessions.create(CreateSessionRequest.builder().build());

		String result = this.task.apply(new TaskCall("desc", "review it", "reviewer", "haiku", null, null),
				calledFrom(caller.id()));

		List<Session> children = this.subagentSessions.findChildren(caller.id());
		assertThat(children).hasSize(1);
		Session child = children.get(0);
		assertThat(result).isEqualTo("agent_id: " + child.id() + "\n\nExecuted: review it");
		assertThat(SessionAttributes.agentName(child)).isEqualTo("reviewer");
		// The model the definition declares, the one the executor runs it on, not the call's
		assertThat(SessionAttributes.agentModel(child)).isEqualTo("sonnet");
		// The executor got the child as the sub-agent's session, and the caller's tool context
		assertThat(this.contexts).singleElement().satisfies(context -> {
			assertThat(context.sessionId()).isEqualTo(child.id());
			assertThat(context.toolContext()).containsEntry(MemoryAdvisor.CONVERSATION_ID, caller.id());
		});
		// The caller's log has the child's creation and the exchange, the child's log nothing yet
		List<SessionEvent> log = this.sessions.getEvents(caller.id());
		assertThat(log).extracting(SessionEvent::getType)
			.containsExactly(CREATED, MESSAGE_SENT, MESSAGE_RECEIVED);
		assertThat(log.get(2).getPayload()).containsEntry(SubagentSessions.PAYLOAD_SESSION_ID, child.id())
			.containsEntry(SubagentSessions.PAYLOAD_AGENT_NAME, "reviewer")
			.doesNotContainKey(SubagentSessions.PAYLOAD_ERROR);
		// The markers stay out of the caller's prompt
		assertThat(this.sessions.getActiveMessages(caller.id())).isEmpty();
		assertThat(SubagentSessions.parentSessionId(child)).isEqualTo(caller.id());
		assertThat(this.sessions.getEvents(child.id())).isEmpty();
	}

	@Test
	void resumeContinuesTheSameSubagent() {
		Session caller = this.sessions.create(CreateSessionRequest.builder().build());
		this.task.apply(call("first", "reviewer", null), calledFrom(caller.id()));
		Session child = this.subagentSessions.findChildren(caller.id()).get(0);

		String result = this.task.apply(call("second", "reviewer", child.id()), calledFrom(caller.id()));

		assertThat(result).startsWith("agent_id: " + child.id());
		assertThat(this.subagentSessions.findChildren(caller.id())).hasSize(1);
		// Without a model on the call, the child carries the definition's
		assertThat(SessionAttributes.agentName(child)).isEqualTo("reviewer");
		assertThat(SessionAttributes.agentModel(child)).isEqualTo("sonnet");
		assertThat(this.contexts).extracting(TaskContext::sessionId).containsExactly(child.id(), child.id());
		assertThat(this.sessions.getEvents(caller.id())).extracting(SessionEvent::getType)
			.containsExactly(CREATED, MESSAGE_SENT, MESSAGE_RECEIVED,
					MESSAGE_SENT, MESSAGE_RECEIVED);
	}

	@Test
	void resumeOfAnAgentThatIsNotTheCallersIsAnError() {
		Session caller = this.sessions.create(CreateSessionRequest.builder().build());
		Session other = this.sessions.create(CreateSessionRequest.builder().build());
		this.task.apply(call("first", "reviewer", null), calledFrom(other.id()));
		Session othersChild = this.subagentSessions.findChildren(other.id()).get(0);

		assertThat(this.task.apply(call("again", "reviewer", "nope"), calledFrom(caller.id())))
			.isEqualTo("Error: No agent found with ID: nope");
		assertThat(this.task.apply(call("again", "reviewer", othersChild.id()), calledFrom(caller.id())))
			.isEqualTo("Error: No agent found with ID: " + othersChild.id());
		// Nor can a sub-agent of one type be resumed as another
		assertThat(this.task.apply(call("again", "writer", othersChild.id()), calledFrom(other.id())))
			.isEqualTo("Error: Agent " + othersChild.id() + " is a 'reviewer' agent, not 'writer'");
		assertThat(this.contexts).hasSize(1);
	}

	@Test
	void withoutACallersSessionTheSubagentRunsWithoutMemory() {
		// No session named by the request: no child, no markers, no agent id
		assertThat(this.task.apply(call("review it", "reviewer", null), new ToolContext(Map.of())))
			.isEqualTo("Executed: review it");
		assertThat(this.task.apply(call("review it", "reviewer", null), null)).isEqualTo("Executed: review it");
		// Nor without a session service, even when the request names one
		Session caller = this.sessions.create(CreateSessionRequest.builder().build());
		TaskTool.TaskFunction withoutMemory = new TaskTool.TaskFunction(this.subagents, List.of(this.executor),
				this.repository);
		// A resume that cannot be honoured is not silent
		assertThat(withoutMemory.apply(call("review it", "reviewer", "stale-id"), calledFrom(caller.id())))
			.startsWith("Note: agent stale-id could not be resumed")
			.endsWith("\n\nExecuted: review it");
		assertThat(this.subagentSessions.findChildren(caller.id())).isEmpty();
		assertThat(this.sessions.getEvents(caller.id())).isEmpty();
		assertThat(this.contexts).extracting(TaskContext::sessionId).containsExactly(null, null, null);
	}

	@Test
	void anExecutorThatKeepsNoSessionsRunsWithoutMemory() {
		SubagentExecutor plain = new SubagentExecutor() {
			@Override
			public String getKind() {
				return ClaudeSubagentDefinition.KIND;
			}

			@Override
			public String execute(TaskCall taskCall, SubagentDefinition subagent) {
				return "Plain: " + taskCall.prompt();
			}
		};
		TaskTool.TaskFunction task = new TaskTool.TaskFunction(this.subagents, List.of(plain), this.repository,
				this.sessions);
		Session caller = this.sessions.create(CreateSessionRequest.builder().build());

		String result = task.apply(call("review it", "reviewer", null), calledFrom(caller.id()));

		// No agent id is promised for a sub-agent that could not be resumed
		assertThat(result).isEqualTo("Plain: review it");
		assertThat(this.subagentSessions.findChildren(caller.id())).isEmpty();
		assertThat(this.sessions.getEvents(caller.id())).isEmpty();
		assertThat(task.apply(call("again", "reviewer", "agent-1"), calledFrom(caller.id())))
			.startsWith("Note: agent agent-1 could not be resumed");
	}

	@Test
	void aBackgroundTaskRecordsTheExchangeWhenItRuns() throws InterruptedException {
		Session caller = this.sessions.create(CreateSessionRequest.builder().build());

		String started = this.task.apply(new TaskCall("desc", "review it", "reviewer", null, null, true),
				calledFrom(caller.id()));

		String taskId = started.substring("task_id: ".length(), started.indexOf('\n'));
		BackgroundTask background = this.repository.getTasks(taskId);
		assertThat(background.waitForCompletion(5000)).isTrue();
		Session child = this.subagentSessions.findChildren(caller.id()).get(0);
		assertThat(background.getResult()).isEqualTo("agent_id: " + child.id() + "\n\nExecuted: review it");
		assertThat(this.sessions.getEvents(caller.id())).extracting(SessionEvent::getType)
			.containsExactly(CREATED, MESSAGE_SENT, MESSAGE_RECEIVED);
	}

	@Test
	void theChildInheritsTheCallersPrincipal() {
		Session caller = this.sessions.create(CreateSessionRequest.builder()
			.metadata(SessionAttributes.PRINCIPAL_NAME, "alice")
			.build());

		this.task.apply(call("review it", "reviewer", null), calledFrom(caller.id()));

		Session child = this.subagentSessions.findChildren(caller.id()).get(0);
		assertThat(SessionAttributes.principalName(child)).isEqualTo("alice");
		// Found among the principal's sessions, as cross-session recall sees them
		assertThat(this.sessions.findByMetadata(SessionAttributes.PRINCIPAL_NAME, "alice"))
			.extracting(Session::id)
			.containsExactly(caller.id(), child.id());
	}

	@Test
	void aCallerSessionThatDoesNotExistRunsTheSubagentWithoutMemory() {
		String result = this.task.apply(call("review it", "reviewer", null), calledFrom("no-such-session"));

		assertThat(result).isEqualTo("Executed: review it");
		assertThat(this.contexts).extracting(TaskContext::sessionId).containsExactly((String) null);
		assertThat(this.subagentSessions.findChildren("no-such-session")).isEmpty();
	}

	@Test
	void aFailedRunIsRecordedAndTheErrorPropagates() {
		Session caller = this.sessions.create(CreateSessionRequest.builder().build());

		assertThatThrownBy(() -> this.task.apply(call("fail now", "reviewer", null), calledFrom(caller.id())))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("model unavailable");

		List<SessionEvent> log = this.sessions.getEvents(caller.id());
		assertThat(log).extracting(SessionEvent::getType).containsExactly(CREATED, MESSAGE_SENT, MESSAGE_RECEIVED);
		assertThat(log.get(2).getPayload()).containsEntry(SubagentSessions.PAYLOAD_ERROR, "model unavailable");
	}

	@Test
	void aSubagentStillRunningCannotBeResumed() throws Exception {
		Session caller = this.sessions.create(CreateSessionRequest.builder().build());
		this.task.apply(call("first", "reviewer", null), calledFrom(caller.id()));
		Session child = this.subagentSessions.findChildren(caller.id()).get(0);

		CountDownLatch release = new CountDownLatch(1);
		this.blockRun = release;
		String started = this.task.apply(new TaskCall("desc", "second", "reviewer", null, child.id(), true),
				calledFrom(caller.id()));
		assertThat(this.started.await(5, TimeUnit.SECONDS)).isTrue();

		// A second run on the same sub-agent while the first is still going
		assertThat(this.task.apply(call("third", "reviewer", child.id()), calledFrom(caller.id())))
			.isEqualTo("Error: Agent " + child.id() + " is still running a task; wait for its result before resuming it");

		release.countDown();
		BackgroundTask background = this.repository
			.getTasks(started.substring("task_id: ".length(), started.indexOf('\n')));
		assertThat(background.waitForCompletion(5000)).isTrue();
		this.blockRun = null;
		// Free again once it finished
		assertThat(this.task.apply(call("fourth", "reviewer", child.id()), calledFrom(caller.id())))
			.startsWith("agent_id: " + child.id());
	}

}
