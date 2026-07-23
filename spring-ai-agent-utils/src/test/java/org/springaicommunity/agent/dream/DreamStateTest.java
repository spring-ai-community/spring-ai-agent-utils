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

import java.nio.file.Path;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link DreamState}.
 *
 * @author Christian Tzolov
 */
@DisplayName("DreamState Tests")
class DreamStateTest {

	@TempDir
	Path tempDir;

	@Test
	@DisplayName("Loading from an empty directory returns the initial state")
	void loadWhenMissingReturnsInitial() {
		DreamState state = DreamState.load(this.tempDir);

		assertThat(state.lastDreamAt()).isNull();
		assertThat(state.sessionsSinceLastDream()).isZero();
		assertThat(state.lastDreamStatus()).isNull();
		assertThat(state.lastDreamSummary()).isNull();
		assertThat(state.lastDreamInstant()).isNull();
	}

	@Test
	@DisplayName("save() followed by load() round-trips all fields")
	void saveThenLoadRoundTrips() {
		DreamState state = DreamState.initial().withSessionIncrement().withDreamCompleted(
				Instant.parse("2026-07-20T09:00:00Z"), "completed", "Merged two duplicate entries.");

		state.save(this.tempDir);
		DreamState loaded = DreamState.load(this.tempDir);

		assertThat(loaded).isEqualTo(state);
		assertThat(loaded.lastDreamInstant()).isEqualTo(Instant.parse("2026-07-20T09:00:00Z"));
		assertThat(loaded.lastDreamStatus()).isEqualTo("completed");
		assertThat(loaded.lastDreamSummary()).isEqualTo("Merged two duplicate entries.");
	}

	@Test
	@DisplayName("withSessionIncrement() increments the counter and preserves other fields")
	void withSessionIncrement() {
		DreamState state = new DreamState("2026-07-01T00:00:00Z", 3, "completed", "summary");

		DreamState incremented = state.withSessionIncrement();

		assertThat(incremented.sessionsSinceLastDream()).isEqualTo(4);
		assertThat(incremented.lastDreamAt()).isEqualTo(state.lastDreamAt());
		assertThat(incremented.lastDreamStatus()).isEqualTo(state.lastDreamStatus());
		assertThat(incremented.lastDreamSummary()).isEqualTo(state.lastDreamSummary());
	}

	@Test
	@DisplayName("withDreamCompleted() resets the session counter and updates status/summary")
	void withDreamCompletedResetsCounter() {
		DreamState state = new DreamState("2026-07-01T00:00:00Z", 7, "failed", "old summary");
		Instant dreamedAt = Instant.parse("2026-07-21T12:00:00Z");

		DreamState completed = state.withDreamCompleted(dreamedAt, "completed", "new summary");

		assertThat(completed.sessionsSinceLastDream()).isZero();
		assertThat(completed.lastDreamAt()).isEqualTo(dreamedAt.toString());
		assertThat(completed.lastDreamStatus()).isEqualTo("completed");
		assertThat(completed.lastDreamSummary()).isEqualTo("new summary");
	}

}
