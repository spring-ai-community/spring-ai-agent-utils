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
package org.springaicommunity.agent.tools;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.session.CreateSessionRequest;
import org.springframework.ai.session.DefaultSessionService;
import org.springframework.ai.session.InMemorySessionRepository;
import org.springframework.ai.session.Session;
import org.springframework.ai.session.SessionAttributes;
import org.springframework.ai.session.SessionEvent;
import org.springframework.ai.session.SessionService;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.util.JsonHelper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link CrossSessionRecallTools}.
 *
 * @author Christian Tzolov
 */
class CrossSessionRecallToolsTest {

	private static final Instant T0 = Instant.parse("2026-07-01T00:00:00Z");

	private static final JsonHelper JSON = new JsonHelper();

	private SessionService sessionService;

	@BeforeEach
	void setUp() {
		this.sessionService = DefaultSessionService.builder()
			.sessionRepository(InMemorySessionRepository.builder().build())
			.build();
	}

	private Session session(String principal) {
		return this.sessionService
			.create(CreateSessionRequest.builder().metadata(SessionAttributes.PRINCIPAL_NAME, principal).build());
	}

	private void append(Session session, int minute, Message message) {
		this.sessionService.appendEvent(SessionEvent.builder()
			.sessionId(session.id())
			.timestamp(T0.plusSeconds(60L * minute))
			.message(message)
			.build());
	}

	private CrossSessionRecallTools tool(String principal) {
		return CrossSessionRecallTools.builder(this.sessionService, principal).build();
	}

	@SuppressWarnings("unchecked")
	private static List<Map<String, Object>> results(String json) {
		Map<String, Object> answer = JSON.fromJsonToMap(json);
		return (List<Map<String, Object>>) answer.get("results");
	}

	@Test
	@DisplayName("Merges matches across the principal's sessions, oldest first")
	void mergesAcrossSessionsOldestFirst() {
		Session first = session("alice");
		Session second = session("alice");
		append(first, 1, new UserMessage("remember that I prefer tabs"));
		append(second, 2, new UserMessage("please remember the deploy window"));
		append(first, 3, new AssistantMessage("noted, I will remember tabs"));
		append(second, 4, new UserMessage("unrelated question"));

		String json = tool("alice").crossSessionSearch("remember", null, null, null);

		List<Map<String, Object>> results = results(json);
		assertThat(results).extracting(r -> r.get("text"))
			.containsExactly("remember that I prefer tabs", "please remember the deploy window",
					"noted, I will remember tabs");
		assertThat(results).extracting(r -> r.get("sessionId"))
			.containsExactly(first.id(), second.id(), first.id());
		assertThat(results.get(2).get("type")).isEqualTo("assistant");
	}

	@Test
	@DisplayName("Never returns another principal's sessions")
	void scopedToPrincipal() {
		append(session("alice"), 1, new UserMessage("alice secret plan"));
		append(session("bob"), 2, new UserMessage("bob secret plan"));
		append(this.sessionService.create(CreateSessionRequest.builder().build()), 3,
				new UserMessage("anonymous secret plan"));

		String json = tool("alice").crossSessionSearch("secret", null, null, null);

		assertThat(results(json)).extracting(r -> r.get("text")).containsExactly("alice secret plan");
	}

	@Test
	@DisplayName("matchMode 'all' requires every keyword")
	void matchModeAll() {
		Session session = session("alice");
		append(session, 1, new UserMessage("invoice sent"));
		append(session, 2, new UserMessage("invoice refund issued"));

		assertThat(results(tool("alice").crossSessionSearch("invoice, refund", "all", null, null)))
			.extracting(r -> r.get("text"))
			.containsExactly("invoice refund issued");
		assertThat(results(tool("alice").crossSessionSearch("invoice, refund", "ANY", null, null))).hasSize(2);
	}

	@Test
	@DisplayName("since drops older messages")
	void sinceFiltersOlderMessages() {
		Session session = session("alice");
		append(session, 1, new UserMessage("old decision"));
		append(session, 10, new UserMessage("new decision"));

		String json = tool("alice").crossSessionSearch("decision", null, T0.plusSeconds(300).toString(), null);

		assertThat(results(json)).extracting(r -> r.get("text")).containsExactly("new decision");
	}

	@Test
	@DisplayName("Pages across sessions and reports the next page")
	void pagesAcrossSessions() {
		Session first = session("alice");
		Session second = session("alice");
		for (int i = 0; i < 5; i++) {
			append(first, 2 * i, new UserMessage("note " + (2 * i)));
			append(second, 2 * i + 1, new UserMessage("note " + (2 * i + 1)));
		}
		CrossSessionRecallTools tool = CrossSessionRecallTools.builder(this.sessionService, "alice")
			.pageSize(4)
			.build();

		String page0 = tool.crossSessionSearch("note", null, null, null);
		String page1 = tool.crossSessionSearch("note", null, null, 1);
		String page2 = tool.crossSessionSearch("note", null, null, 2);
		String page3 = tool.crossSessionSearch("note", null, null, 3);

		assertThat(results(page0)).extracting(r -> r.get("text"))
			.containsExactly("note 0", "note 1", "note 2", "note 3");
		assertThat(JSON.fromJsonToMap(page0)).containsEntry("nextPage", 1);
		assertThat(results(page1)).extracting(r -> r.get("text"))
			.containsExactly("note 4", "note 5", "note 6", "note 7");
		assertThat(results(page2)).extracting(r -> r.get("text")).containsExactly("note 8", "note 9");
		assertThat(JSON.fromJsonToMap(page2)).doesNotContainKey("nextPage");
		assertThat(page3).isEqualTo(CrossSessionRecallTools.NO_MORE_RESULTS);
	}

	@Test
	@DisplayName("Reads a session in several batches for later pages")
	void readsSeveralBatchesPerSession() {
		Session first = session("alice");
		Session second = session("alice");
		for (int i = 0; i < 150; i++) {
			append(first, 2 * i, new UserMessage("match a" + i));
			append(second, 2 * i + 1, new UserMessage("match b" + i));
		}
		CrossSessionRecallTools tool = CrossSessionRecallTools.builder(this.sessionService, "alice")
			.pageSize(100)
			.build();

		// Page 1 needs the oldest 200 matches of each session: two batches of 100
		List<Map<String, Object>> page1 = results(tool.crossSessionSearch("match", null, null, 1));
		List<Map<String, Object>> page2 = results(tool.crossSessionSearch("match", null, null, 2));

		assertThat(page1).hasSize(100);
		assertThat(page1.get(0).get("text")).isEqualTo("match a50");
		assertThat(page1.get(99).get("text")).isEqualTo("match b99");
		assertThat(page2).hasSize(100);
		assertThat(page2.get(0).get("text")).isEqualTo("match a100");
		assertThat(page2.get(99).get("text")).isEqualTo("match b149");
		assertThat(tool.crossSessionSearch("match", null, null, 3)).isEqualTo(CrossSessionRecallTools.NO_MORE_RESULTS);
	}

	@Test
	@DisplayName("Keeps each session's log order when its timestamps are out of order")
	void pagesConsistentlyWithOutOfOrderTimestamps() {
		Session imported = session("alice");
		Session live = session("alice");
		// Backfilled history: appended newest first
		append(imported, 30, new UserMessage("note imported-late"));
		append(imported, 10, new UserMessage("note imported-early"));
		append(live, 20, new UserMessage("note live"));
		CrossSessionRecallTools tool = CrossSessionRecallTools.builder(this.sessionService, "alice")
			.pageSize(1)
			.build();

		List<Object> pages = new java.util.ArrayList<>();
		for (int page = 0; page < 3; page++) {
			pages.add(results(tool.crossSessionSearch("note", null, null, page)).get(0).get("text"));
		}

		// Every match exactly once across the pages
		assertThat(pages).containsExactlyInAnyOrder("note imported-late", "note imported-early", "note live");
		assertThat(tool.crossSessionSearch("note", null, null, 3)).isEqualTo(CrossSessionRecallTools.NO_MORE_RESULTS);
	}

	@Test
	@DisplayName("Survives an excerpt of malformed text with lone surrogates")
	void excerptOfLoneSurrogates() {
		Session session = session("alice");
		// The excerpt starts on a low surrogate and would end on one: both cuts move inward
		append(session, 1, new UserMessage("a\uDC00\uDC00b"));

		String json = CrossSessionRecallTools.builder(this.sessionService, "alice")
			.maxTextLength(1)
			.build()
			.crossSessionSearch("\uDC00", null, null, null);

		assertThat(results(json)).hasSize(1);
	}

	@Test
	@DisplayName("Cuts a long message to an excerpt around the keyword")
	void excerptsLongText() {
		Session session = session("alice");
		append(session, 1, new UserMessage("x".repeat(500) + " needle " + "y".repeat(500)));

		String json = CrossSessionRecallTools.builder(this.sessionService, "alice")
			.maxTextLength(100)
			.build()
			.crossSessionSearch("needle", null, null, null);

		String text = (String) results(json).get(0).get("text");
		assertThat(text).startsWith("...").endsWith("...").contains("needle");
		assertThat(text).hasSize(106);
	}

	@Test
	@DisplayName("Answers invalid input with an error text the model can act on")
	void invalidInputIsReportedNotThrown() {
		session("alice");
		CrossSessionRecallTools tool = tool("alice");

		assertThat(tool.crossSessionSearch(" , ", null, null, null))
			.isEqualTo(CrossSessionRecallTools.MISSING_KEYWORD_RESULT);
		assertThat(tool.crossSessionSearch(null, null, null, null))
			.isEqualTo(CrossSessionRecallTools.MISSING_KEYWORD_RESULT);
		assertThat(tool.crossSessionSearch("x", "some", null, null))
			.isEqualTo(CrossSessionRecallTools.UNKNOWN_MATCH_RESULT);
		assertThat(tool.crossSessionSearch("x", null, "yesterday", null)).startsWith("Error: since 'yesterday'");
		assertThat(tool.crossSessionSearch("x", null, null, 500)).startsWith("Error: page 500");
		String tooMany = String.join(",", java.util.stream.IntStream.range(0, 21).mapToObj(i -> "k" + i).toList());
		assertThat(tool.crossSessionSearch(tooMany, null, null, null)).startsWith("Error: ");
		// Reported even when the principal has no sessions to search yet
		assertThat(tool("nobody").crossSessionSearch(tooMany, null, null, null)).startsWith("Error: ");
		assertThat(tool("nobody").crossSessionSearch("x".repeat(201), null, null, null)).startsWith("Error: ");
		assertThat(tool.crossSessionSearch("nothing", null, null, null))
			.isEqualTo(CrossSessionRecallTools.NO_RESULTS);
	}

	@Test
	@DisplayName("Exposes one cross_session_search tool without a principal parameter")
	void toolDefinition() {
		ToolCallback[] callbacks = ToolCallbacks.from(tool("alice"));

		assertThat(callbacks).hasSize(1);
		assertThat(callbacks[0].getToolDefinition().name()).isEqualTo("cross_session_search");
		assertThat(callbacks[0].getToolDefinition().inputSchema()).contains("query", "matchMode", "since", "page")
			.doesNotContain("principal", "userId");
	}

	@Test
	@DisplayName("Builder validates its arguments")
	void builderValidation() {
		assertThatThrownBy(() -> CrossSessionRecallTools.builder(null, "alice"))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> CrossSessionRecallTools.builder(this.sessionService, " "))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> CrossSessionRecallTools.builder(this.sessionService, "alice").pageSize(0))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> CrossSessionRecallTools.builder(this.sessionService, "alice").pageSize(101))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> CrossSessionRecallTools.builder(this.sessionService, "alice").maxTextLength(0))
			.isInstanceOf(IllegalArgumentException.class);
	}

}
