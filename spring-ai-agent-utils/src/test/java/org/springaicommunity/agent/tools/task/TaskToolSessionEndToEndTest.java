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

import org.junit.jupiter.api.Test;
import org.springaicommunity.agent.tools.task.claude.ClaudeSubagentType;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.session.DefaultSessionService;
import org.springframework.ai.session.InMemorySessionRepository;
import org.springframework.ai.session.Session;
import org.springframework.ai.session.SessionAttributes;
import org.springframework.ai.session.SessionEvent;
import org.springframework.ai.session.SessionService;
import org.springframework.ai.session.advisor.SessionAdvisors;
import org.springframework.ai.tool.ToolCallback;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sub-agent memory through the real wiring: the main agent's session advisors put its
 * session id into the tool context, the Task tool creates a child session under it, the
 * Claude sub-agent keeps its transcript there, and {@code resume} continues it.
 *
 * @author Christian Tzolov
 */
class TaskToolSessionEndToEndTest {

	private static final String MAIN_SESSION = "main-session";

	private final List<List<Message>> subagentPrompts = new ArrayList<>();

	/**
	 * Plays both agents. The main agent delegates every user message to the
	 * general-purpose sub-agent, resuming the one it already has, and answers "done" once
	 * the Task tool returns; the sub-agent answers "sub answer N".
	 */
	private final ChatModel model = new ChatModel() {

		@Override
		public ChatResponse call(Prompt prompt) {
			List<Message> messages = prompt.getInstructions();
			Message last = messages.get(messages.size() - 1);
			boolean subagent = messages.get(0).getMessageType() == MessageType.SYSTEM
					&& messages.get(0).getText().contains("Given the user's message");
			if (subagent) {
				TaskToolSessionEndToEndTest.this.subagentPrompts.add(List.copyOf(messages));
				return reply(new AssistantMessage("sub answer " + TaskToolSessionEndToEndTest.this.subagentPrompts.size()));
			}
			if (last instanceof ToolResponseMessage) {
				return reply(new AssistantMessage("done"));
			}
			String agentId = lastAgentId(messages);
			String resume = (agentId != null) ? ",\"resume\":\"" + agentId + "\"" : "";
			String arguments = "{\"description\":\"delegate\",\"prompt\":\"" + last.getText()
					+ "\",\"subagent_type\":\"general-purpose\"" + resume + "}";
			return reply(AssistantMessage.builder()
				.content("")
				.toolCalls(List.of(new AssistantMessage.ToolCall("call-" + messages.size(), "function", "Task",
						arguments)))
				.build());
		}

		@Override
		public ChatOptions getOptions() {
			return ToolCallingChatOptions.builder().build();
		}

	};

	private static ChatResponse reply(AssistantMessage message) {
		return new ChatResponse(List.of(new Generation(message)));
	}

	/** The agent id the Task tool returned last, from the main agent's history. */
	private static String lastAgentId(List<Message> messages) {
		String agentId = null;
		for (Message message : messages) {
			if (message instanceof ToolResponseMessage toolResponse) {
				String data = toolResponse.getResponses().get(0).responseData();
				// The tool result reaches the model JSON-encoded: take the id itself
				java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("agent_id: ([A-Za-z0-9-]+)")
					.matcher(data);
				if (matcher.find()) {
					agentId = matcher.group(1);
				}
			}
		}
		return agentId;
	}

	@Test
	void theMainAgentsSessionReachesTheTaskToolAndResumeContinuesTheSubagent() {
		SessionService sessionService = DefaultSessionService.builder()
			.sessionRepository(InMemorySessionRepository.builder().build())
			.build();

		ToolCallback taskTool = TaskTool.builder()
			.sessionService(sessionService)
			.subagentTypes(ClaudeSubagentType.builder()
				.chatClientBuilder("default", ChatClient.builder(this.model))
				.sessionService(sessionService)
				.build())
			.build();

		ChatClient mainAgent = ChatClient.builder(this.model)
			.defaultTools(taskTool)
			.defaultAdvisors(SessionAdvisors.builder(sessionService).sessionIdInToolContext(true).build())
			.build();

		String first = mainAgent.prompt("find the parser").advisors(SessionAdvisors.session(MAIN_SESSION)).call().content();
		String second = mainAgent.prompt("now fix it").advisors(SessionAdvisors.session(MAIN_SESSION)).call().content();

		assertThat(first).isEqualTo("done");
		assertThat(second).isEqualTo("done");

		// One sub-agent, a child of the main agent's session, resumed by the second call
		List<Session> children = new SubagentSessions(sessionService).findChildren(MAIN_SESSION);
		assertThat(children).hasSize(1);
		Session child = children.get(0);
		assertThat(SessionAttributes.agentName(child)).isEqualTo("general-purpose");

		// Its transcript is in its own session, and the resumed run saw the first one
		assertThat(sessionService.getActiveMessages(child.id())).extracting(Message::getText)
			.containsExactly("find the parser", "sub answer 1", "now fix it", "sub answer 2");
		assertThat(this.subagentPrompts.get(1)).extracting(Message::getText)
			.contains("find the parser", "sub answer 1", "now fix it");

		// The main session records the exchange, outside its conversation
		assertThat(sessionService.getEvents(MAIN_SESSION))
			.extracting(event -> event.getType().name())
			.containsSubsequence("SUBAGENT.CREATED", "SUBAGENT.MESSAGE_SENT", "SUBAGENT.MESSAGE_RECEIVED",
					"SUBAGENT.MESSAGE_SENT", "SUBAGENT.MESSAGE_RECEIVED")
			.containsOnlyOnce("SUBAGENT.CREATED");
		assertThat(sessionService.getEvents(MAIN_SESSION)).filteredOn(SessionEvent::hasMessage)
			.extracting(event -> event.getMessage().getMessageType())
			.containsExactly(MessageType.USER, MessageType.ASSISTANT, MessageType.TOOL, MessageType.ASSISTANT,
					MessageType.USER, MessageType.ASSISTANT, MessageType.TOOL, MessageType.ASSISTANT);
	}

}
