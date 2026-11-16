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
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.session.EventQuery;
import org.springframework.ai.session.Session;
import org.springframework.ai.session.SessionAttributes;
import org.springframework.ai.session.SessionEvent;
import org.springframework.ai.session.SessionService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.util.JsonHelper;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Read-only keyword search across every session of one principal: the
 * {@code cross_session_search} tool.
 * <p>
 * Spring AI's {@code SessionRecallTools} ({@code conversation_search}) searches the one
 * session named in the tool context, which is what a live conversational agent needs. A
 * background maintenance agent, such as the Auto-Dream Dreamer, instead mines signal that
 * spans many past conversations of the same user. This tool finds that user's sessions by
 * their {@link SessionAttributes#PRINCIPAL_NAME} metadata, runs
 * {@link SessionService#searchEvents} on each, and returns the matches oldest first
 * across all of them.
 * <p>
 * The principal is bound when the tool is built, never taken from the model, so a prompt
 * cannot talk the tool into searching another user's sessions. Sessions are found only
 * when they were created with that metadata:
 * <pre>{@code
 * sessionService.create(CreateSessionRequest.builder()
 *     .metadata(SessionAttributes.PRINCIPAL_NAME, "alice")
 *     .build());
 * }</pre>
 * <p>
 * The search follows {@link EventQuery}: only message events, by their text, ignoring
 * case, never synthetic summaries, and only within the first {@link EventQuery#MAX_MATCHES}
 * matches. Invalid input from the model is answered with an {@code Error: ...} text the
 * model can act on, not an exception.
 * <p>
 * Each call reads every session of the principal: one {@code findByMetadata} lookup,
 * then at least one {@code searchEvents} query per session (more for later pages, which
 * re-read the earlier ones). That suits a background agent running a handful of
 * targeted queries; for principals with very many sessions, prefer a store that keeps
 * each search cheap, such as the JDBC repository, whose {@code searchEvents} runs in
 * the database.
 *
 * @author Christian Tzolov
 * @since 0.14.0
 */
public final class CrossSessionRecallTools {

	private static final Logger logger = LoggerFactory.getLogger(CrossSessionRecallTools.class);

	private static final JsonHelper JSON = new JsonHelper();

	/**
	 * The longest text a result carries by default, in characters, not counting the
	 * {@code ...} that marks where an excerpt was cut.
	 */
	public static final int DEFAULT_MAX_TEXT_LENGTH = 1000;

	static final String MISSING_KEYWORD_RESULT = "Error: cross_session_search needs at least one keyword.";

	static final String UNKNOWN_MATCH_RESULT = "Error: matchMode must be 'any' or 'all'.";

	static final String NO_RESULTS = "No results found.";

	static final String NO_MORE_RESULTS = "No more results.";

	private final SessionService sessionService;

	private final String principalName;

	private final int pageSize;

	private final int maxTextLength;

	private CrossSessionRecallTools(SessionService sessionService, String principalName, int pageSize,
			int maxTextLength) {
		this.sessionService = sessionService;
		this.principalName = principalName;
		this.pageSize = pageSize;
		this.maxTextLength = maxTextLength;
	}

	/**
	 * @param sessionService the session store to search
	 * @param principalName the {@link SessionAttributes#PRINCIPAL_NAME} whose sessions the
	 * tool searches; fixed for the lifetime of the tool, never supplied by the model
	 * @return a new builder
	 */
	public static Builder builder(SessionService sessionService, String principalName) {
		return new Builder(sessionService, principalName);
	}

	// @formatter:off
	@Tool(name = "cross_session_search", description = """
		Search across ALL of the user's past sessions (not just the current one) for messages that contain \
		the given keywords, ignoring case. Intended for maintenance and analysis agents that need historical \
		signal spanning many past conversations, not for a live conversational agent. Returns one page of \
		matches, oldest first, as JSON: 'results' with the sessionId, timestamp, type and text of each \
		message, and 'nextPage' when more matches may follow. A long text is cut to an excerpt around the \
		keyword.""")
	public String crossSessionSearch(
			@ToolParam(description = "One or more keywords, separated by commas. Each keyword is matched as literal text, so 'invoice, refund' looks for two words and 'invoice refund' for that exact phrase.") String query,
			@ToolParam(description = "'any' (the default): a message matches when it contains one of the keywords. 'all': it must contain every keyword.", required = false) String matchMode,
			@ToolParam(description = "ISO-8601 instant (e.g. '2026-07-01T00:00:00Z'); only messages at or after this time are considered. Omit to search the full history.", required = false) String since,
			@ToolParam(description = "The page of matches, starting at 0. Omit it for the first page.", required = false) Integer page) { // @formatter:on

		List<String> keywords = (query != null)
				? Arrays.stream(query.split(",")).filter(StringUtils::hasText).map(String::strip).toList() : List.of();
		if (keywords.isEmpty()) {
			return MISSING_KEYWORD_RESULT;
		}

		String matchValue = StringUtils.hasText(matchMode) ? matchMode.strip() : "any";
		if (!"any".equalsIgnoreCase(matchValue) && !"all".equalsIgnoreCase(matchValue)) {
			return UNKNOWN_MATCH_RESULT;
		}
		EventQuery.MatchMode mode = "all".equalsIgnoreCase(matchValue) ? EventQuery.MatchMode.ALL
				: EventQuery.MatchMode.ANY;

		Instant sinceInstant = null;
		if (StringUtils.hasText(since)) {
			try {
				sinceInstant = Instant.parse(since.strip());
			}
			catch (DateTimeParseException ex) {
				return "Error: since '" + since + "' is not an ISO-8601 instant (e.g. '2026-07-01T00:00:00Z').";
			}
		}

		int pageNumber = (page != null) ? Math.max(0, page) : 0;
		// The page and the pages before it, merged across sessions
		long needed = ((long) pageNumber + 1) * this.pageSize;
		if (needed > EventQuery.MAX_MATCHES) {
			return "Error: page " + pageNumber + " is beyond the first " + EventQuery.MAX_MATCHES
					+ " matches a search reads.";
		}

		// Validate the query once, before any session is read, so that the model hears
		// about a bad query even when the principal has no sessions yet
		EventQuery firstBatch;
		try {
			firstBatch = batchQuery(keywords, mode, sinceInstant, 0, Math.min((int) needed, EventQuery.MAX_PAGE_SIZE));
		}
		catch (IllegalArgumentException ex) {
			// Too many keywords, a keyword too long: the model's to fix
			return "Error: " + ex.getMessage() + ".";
		}

		List<Session> sessions = this.sessionService.findByMetadata(SessionAttributes.PRINCIPAL_NAME,
				this.principalName);

		logger.debug(
				"[cross_session_search] principal: {}, sessions: {}, keywords: {}, matchMode: {}, since: {}, page: {}",
				this.principalName, sessions.size(), keywords, mode, sinceInstant, pageNumber);

		List<List<SessionEvent>> perSession = new ArrayList<>();
		for (Session session : sessions) {
			perSession.add(searchSession(session.id(), firstBatch, (int) needed));
		}

		List<SessionEvent> merged = merge(perSession, (int) needed);
		List<SessionEvent> found = merged.subList(Math.min(merged.size(), pageNumber * this.pageSize),
				merged.size());

		if (found.isEmpty()) {
			return (pageNumber == 0) ? NO_RESULTS : NO_MORE_RESULTS;
		}

		Map<String, Object> answer = new LinkedHashMap<>();
		answer.put("results", found.stream().map(event -> result(event, keywords)).toList());
		// A full page may have a next one, as long as the search can still read it
		if (found.size() == this.pageSize && needed + this.pageSize <= EventQuery.MAX_MATCHES) {
			answer.put("nextPage", pageNumber + 1);
		}
		return JSON.toJson(answer);
	}

	private static EventQuery batchQuery(List<String> keywords, EventQuery.MatchMode mode, Instant since, int page,
			int pageSize) {
		return EventQuery.builder()
			.keywords(keywords)
			.matchMode(mode)
			.since(since)
			.page(page)
			.pageSize(pageSize)
			.build();
	}

	/**
	 * The oldest {@code limit} matches of one session, read a batch of up to
	 * {@link EventQuery#MAX_PAGE_SIZE} at a time, starting with {@code firstBatch}.
	 */
	private List<SessionEvent> searchSession(String sessionId, EventQuery firstBatch, int limit) {
		List<SessionEvent> matches = new ArrayList<>();
		int batchSize = firstBatch.pageSize();
		for (int batch = 0; matches.size() < limit; batch++) {
			EventQuery query = (batch == 0) ? firstBatch : batchQuery(firstBatch.keywords(), firstBatch.matchMode(),
					firstBatch.since(), batch, batchSize);
			List<SessionEvent> page = this.sessionService.searchEvents(sessionId, query);
			matches.addAll(page);
			if (page.size() < batchSize) {
				break;
			}
		}
		return (matches.size() > limit) ? matches.subList(0, limit) : matches;
	}

	/**
	 * The first {@code limit} matches across sessions: each session's matches stay in
	 * their log order, the order a search returns them in, and the sessions are
	 * interleaved by timestamp, taking the older head first. Unlike a sort, this needs no
	 * match beyond a session's first {@code limit}, so pages stay consistent even when a
	 * log's timestamps are not in append order (imported or backfilled history).
	 */
	private static List<SessionEvent> merge(List<List<SessionEvent>> perSession, int limit) {
		int[] next = new int[perSession.size()];
		List<SessionEvent> merged = new ArrayList<>();
		while (merged.size() < limit) {
			int oldest = -1;
			for (int s = 0; s < perSession.size(); s++) {
				if (next[s] < perSession.get(s).size() && (oldest < 0 || perSession.get(s)
					.get(next[s])
					.getTimestamp()
					.isBefore(perSession.get(oldest).get(next[oldest]).getTimestamp()))) {
					oldest = s;
				}
			}
			if (oldest < 0) {
				break;
			}
			merged.add(perSession.get(oldest).get(next[oldest]++));
		}
		return merged;
	}

	private Map<String, String> result(SessionEvent event, List<String> keywords) {
		Map<String, String> result = new LinkedHashMap<>();
		result.put("sessionId", event.getSessionId());
		result.put("timestamp", event.getTimestamp().toString());
		result.put("type", event.getMessageType().getValue());
		result.put("text", excerpt(String.valueOf(event.getMessage().getText()), keywords));
		return result;
	}

	/**
	 * The text, or when it is longer than the limit, that many characters of it around
	 * the first keyword it holds, with {@code ...} where it was cut.
	 */
	private String excerpt(String text, List<String> keywords) {
		if (text.length() <= this.maxTextLength) {
			return text;
		}
		int match = keywords.stream()
			.mapToInt(keyword -> EventQuery.indexOfIgnoreCase(text, keyword))
			.filter(index -> index >= 0)
			.min()
			.orElse(0);
		// A quarter of the excerpt before the keyword, the rest from it on
		int start = Math.max(0, Math.min(match - this.maxTextLength / 4, text.length() - this.maxTextLength));
		int end = start + this.maxTextLength;
		// Never cut between the two halves of one character
		if (start > 0 && Character.isLowSurrogate(text.charAt(start))) {
			start++;
		}
		if (end < text.length() && Character.isLowSurrogate(text.charAt(end))) {
			end--;
		}
		// Only malformed text (lone surrogates) with a tiny limit can cross them over
		end = Math.max(start, end);
		return ((start > 0) ? "..." : "") + text.substring(start, end) + ((end < text.length()) ? "..." : "");
	}

	/** Builds {@link CrossSessionRecallTools}. */
	public static final class Builder {

		private final SessionService sessionService;

		private final String principalName;

		private int pageSize = EventQuery.DEFAULT_PAGE_SIZE;

		private int maxTextLength = DEFAULT_MAX_TEXT_LENGTH;

		private Builder(SessionService sessionService, String principalName) {
			Assert.notNull(sessionService, "sessionService must not be null");
			Assert.hasText(principalName, "principalName must not be empty");
			this.sessionService = sessionService;
			this.principalName = principalName;
		}

		/**
		 * The matches the tool returns on a page; {@link EventQuery#DEFAULT_PAGE_SIZE} by
		 * default, at most {@link EventQuery#MAX_PAGE_SIZE}.
		 * @param pageSize the page size
		 * @return this builder
		 */
		public Builder pageSize(int pageSize) {
			Assert.isTrue(pageSize > 0 && pageSize <= EventQuery.MAX_PAGE_SIZE,
					"pageSize must be between 1 and " + EventQuery.MAX_PAGE_SIZE);
			this.pageSize = pageSize;
			return this;
		}

		/**
		 * The longest text a result carries, in characters; a longer message is cut to an
		 * excerpt around the keyword, with {@code ...} added on each side where it was cut
		 * (up to six more characters). {@link #DEFAULT_MAX_TEXT_LENGTH} by default.
		 * @param maxTextLength the maximum text length
		 * @return this builder
		 */
		public Builder maxTextLength(int maxTextLength) {
			Assert.isTrue(maxTextLength > 0, "maxTextLength must be positive");
			this.maxTextLength = maxTextLength;
			return this;
		}

		public CrossSessionRecallTools build() {
			return new CrossSessionRecallTools(this.sessionService, this.principalName, this.pageSize,
					this.maxTextLength);
		}

	}

}
