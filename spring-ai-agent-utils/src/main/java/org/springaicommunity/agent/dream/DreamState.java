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
package org.springaicommunity.agent.dream;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import org.springframework.ai.util.JsonHelper;

/**
 * Persisted state for the Auto-Dream trigger, stored as {@code .dream-state.json} inside
 * the memories root directory so it survives process restarts.
 *
 * @param lastDreamAt ISO-8601 instant the last dream cycle completed, or {@code null} if
 * a dream cycle has never run
 * @param sessionsSinceLastDream number of conversation turns {@code AutoDreamAdvisor} has
 * processed since the last dream cycle completed
 * @param lastDreamStatus outcome of the last dream cycle ({@code "completed"},
 * {@code "failed"}, or {@code null} if none has run yet)
 * @param lastDreamSummary short summary returned by the Dreamer at the end of the last
 * dream cycle
 * @author Christian Tzolov
 */
public record DreamState(String lastDreamAt, int sessionsSinceLastDream, String lastDreamStatus,
		String lastDreamSummary) {

	static final String STATE_FILE_NAME = ".dream-state.json";

	private static final JsonHelper JSON = new JsonHelper();

	/**
	 * The state before any dream cycle has ever run.
	 */
	public static DreamState initial() {
		return new DreamState(null, 0, null, null);
	}

	/**
	 * Loads the dream state from {@code <memoriesDir>/.dream-state.json}, or returns
	 * {@link #initial()} if the file does not exist yet.
	 */
	public static DreamState load(Path memoriesDir) {
		Path stateFile = memoriesDir.resolve(STATE_FILE_NAME);
		if (!Files.exists(stateFile)) {
			return initial();
		}
		try {
			String json = Files.readString(stateFile, StandardCharsets.UTF_8);
			DreamState state = JSON.fromJson(json, DreamState.class);
			return state != null ? state : initial();
		}
		catch (IOException e) {
			throw new UncheckedIOException("Failed to read dream state at " + stateFile, e);
		}
	}

	/**
	 * Persists this state to {@code <memoriesDir>/.dream-state.json}.
	 */
	public void save(Path memoriesDir) {
		Path stateFile = memoriesDir.resolve(STATE_FILE_NAME);
		try {
			Files.writeString(stateFile, JSON.toJson(this), StandardCharsets.UTF_8);
		}
		catch (IOException e) {
			throw new UncheckedIOException("Failed to write dream state at " + stateFile, e);
		}
	}

	/**
	 * Parses {@link #lastDreamAt()}, or returns {@code null} if a dream cycle has never
	 * run.
	 */
	public Instant lastDreamInstant() {
		return this.lastDreamAt != null ? Instant.parse(this.lastDreamAt) : null;
	}

	/**
	 * Returns a copy with {@link #sessionsSinceLastDream()} incremented by one. Called by
	 * {@code AutoDreamAdvisor} once per conversation turn it processes.
	 */
	public DreamState withSessionIncrement() {
		return new DreamState(this.lastDreamAt, this.sessionsSinceLastDream + 1, this.lastDreamStatus,
				this.lastDreamSummary);
	}

	/**
	 * Returns a copy reflecting a just-completed dream cycle: the session counter resets
	 * to zero and {@link #lastDreamAt()} is set to {@code dreamedAt}.
	 */
	public DreamState withDreamCompleted(Instant dreamedAt, String status, String summary) {
		return new DreamState(dreamedAt.toString(), 0, status, summary);
	}

}
