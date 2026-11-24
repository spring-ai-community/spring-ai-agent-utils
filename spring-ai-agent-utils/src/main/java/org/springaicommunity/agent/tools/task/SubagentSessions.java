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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.session.CreateSessionRequest;
import org.springframework.ai.session.EventType;
import org.springframework.ai.session.Session;
import org.springframework.ai.session.SessionAttributes;
import org.springframework.ai.session.SessionEvent;
import org.springframework.ai.session.SessionService;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * How sub-agent memory is laid out in Spring AI sessions: every sub-agent run of the
 * {@code Task} tool gets a session of its own, a <em>child</em> of the calling agent's
 * session, and the exchange is recorded on the caller's session.
 * <ul>
 * <li>The child names its caller in its metadata under {@link #PARENT_SESSION_ID}, and
 * carries the sub-agent's {@link SessionAttributes#AGENT_NAME} and, when known,
 * {@link SessionAttributes#AGENT_MODEL}. It inherits the caller's
 * {@link SessionAttributes#PRINCIPAL_NAME}, and has the service's own time to live.</li>
 * <li>The caller's log gets a {@link #CREATED} event when a child is created, and a
 * {@link #MESSAGE_SENT} and a {@link #MESSAGE_RECEIVED} event around every run, which
 * name the child; a run that failed has its error as {@link #PAYLOAD_ERROR}. They link the
 * caller's {@code Task} call, whose arguments and result the caller's log already holds,
 * to the child. They are not {@link EventType#MESSAGE MESSAGE} events, so they never
 * enter the caller's prompt.</li>
 * </ul>
 * <p>
 * {@link #findChildren} looks the children up by their metadata. The in-memory
 * repository answers for any key; the JDBC repository only for the keys it indexes, so add
 * {@link #PARENT_SESSION_ID} to its {@code indexedAttributes} to list children there.
 * Resuming a sub-agent needs no index: it reads the child by its id.
 *
 * @author Christian Tzolov
 * @since 0.15.0
 */
public final class SubagentSessions {

	/** The metadata key of a sub-agent session that names the caller's session. */
	public static final String PARENT_SESSION_ID = "parentSessionId";

	/** On the caller's session: a sub-agent session was created. */
	public static final EventType CREATED = EventType.of("SUBAGENT.CREATED");

	/** On the caller's session: a task was sent to a sub-agent. */
	public static final EventType MESSAGE_SENT = EventType.of("SUBAGENT.MESSAGE_SENT");

	/** On the caller's session: a sub-agent answered. */
	public static final EventType MESSAGE_RECEIVED = EventType.of("SUBAGENT.MESSAGE_RECEIVED");

	/** Payload key: the sub-agent's session id. */
	public static final String PAYLOAD_SESSION_ID = "sessionId";

	/** Payload key: the sub-agent's name. */
	public static final String PAYLOAD_AGENT_NAME = "agentName";

	/** Payload key: the sub-agent's model, on {@link #CREATED} when known. */
	public static final String PAYLOAD_AGENT_MODEL = "agentModel";

	/** Payload key, on {@link #MESSAGE_RECEIVED}: why the run failed. */
	public static final String PAYLOAD_ERROR = "error";

	private final SessionService sessionService;

	public SubagentSessions(SessionService sessionService) {
		Assert.notNull(sessionService, "sessionService must not be null");
		this.sessionService = sessionService;
	}

	/** The sub-agent sessions created under the given session, oldest first. */
	public List<Session> findChildren(String parentSessionId) {
		Assert.hasText(parentSessionId, "parentSessionId must not be empty");
		return this.sessionService.findByMetadata(PARENT_SESSION_ID, parentSessionId);
	}

	/** The session's caller, when it is a sub-agent session. */
	public static String parentSessionId(Session session) {
		Object value = session.metadata().get(PARENT_SESSION_ID);
		return (value != null) ? value.toString() : null;
	}

	/**
	 * A sub-agent run's session: its id and the sub-agent's name, or the error text the
	 * model gets instead of a run.
	 */
	record Run(String sessionId, String agentName, String error) {

		static Run failed(String error) {
			return new Run(null, null, error);
		}

	}

	/**
	 * Opens the session a sub-agent run works on: a new child of the caller's session, or,
	 * with {@code resume}, the caller's child of that id.
	 * @return the run, a {@linkplain Run#error() failed} run when {@code resume} names no
	 * child of the caller's or one of another sub-agent, or {@code null} when the caller's
	 * session does not exist
	 */
	Run open(String callerSessionId, String resume, String agentName, String agentModel) {
		Session caller = this.sessionService.findById(callerSessionId);
		if (caller == null) {
			return null;
		}
		if (!StringUtils.hasText(resume)) {
			return new Run(createChild(caller, agentName, agentModel).id(), agentName, null);
		}
		Session child = this.sessionService.findById(resume);
		if (child == null || !callerSessionId.equals(parentSessionId(child))) {
			return Run.failed("Error: No agent found with ID: " + resume);
		}
		String childAgent = SessionAttributes.agentName(child);
		if (childAgent != null && !childAgent.equals(agentName)) {
			return Run.failed("Error: Agent " + resume + " is a '" + childAgent + "' agent, not '" + agentName + "'");
		}
		return new Run(child.id(), agentName, null);
	}

	/**
	 * Creates a child of the caller's session for a sub-agent, and records it on the
	 * caller's session.
	 */
	private Session createChild(Session parent, String agentName, String agentModel) {
		CreateSessionRequest.Builder child = CreateSessionRequest.builder()
			.metadata(PARENT_SESSION_ID, parent.id())
			.metadata(SessionAttributes.AGENT_NAME, agentName);
		if (StringUtils.hasText(agentModel)) {
			child.metadata(SessionAttributes.AGENT_MODEL, agentModel);
		}
		String principal = SessionAttributes.principalName(parent);
		if (principal != null) {
			child.metadata(SessionAttributes.PRINCIPAL_NAME, principal);
		}
		Session session = this.sessionService.create(child.build());

		Map<String, Object> payload = payload(session.id(), agentName);
		if (StringUtils.hasText(agentModel)) {
			payload.put(PAYLOAD_AGENT_MODEL, agentModel);
		}
		append(parent.id(), CREATED, payload);
		return session;
	}

	/** Records on the caller's session that a task was sent to the sub-agent. */
	void messageSent(String callerSessionId, Run run) {
		append(callerSessionId, MESSAGE_SENT, payload(run.sessionId(), run.agentName()));
	}

	/**
	 * Records on the caller's session that the sub-agent answered, or, with an error,
	 * that its run failed.
	 */
	void messageReceived(String callerSessionId, Run run, String error) {
		Map<String, Object> payload = payload(run.sessionId(), run.agentName());
		if (error != null) {
			payload.put(PAYLOAD_ERROR, error);
		}
		append(callerSessionId, MESSAGE_RECEIVED, payload);
	}

	private static Map<String, Object> payload(String sessionId, String agentName) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put(PAYLOAD_SESSION_ID, sessionId);
		if (agentName != null) {
			payload.put(PAYLOAD_AGENT_NAME, agentName);
		}
		return payload;
	}

	private void append(String sessionId, EventType type, Map<String, Object> payload) {
		this.sessionService
			.appendEvent(SessionEvent.builder().sessionId(sessionId).type(type).payload(payload).build());
	}

}
