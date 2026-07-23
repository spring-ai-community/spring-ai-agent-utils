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

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * Tests for {@link DreamTriggers}.
 *
 * @author Christian Tzolov
 */
@DisplayName("DreamTriggers Tests")
class DreamTriggersTest {

	private static final Instant NOW = Instant.parse("2026-07-22T00:00:00Z");

	@Test
	@DisplayName("hoursAndSessions rejects non-positive hours")
	void rejectsNonPositiveHours() {
		assertThatIllegalArgumentException().isThrownBy(() -> DreamTriggers.hoursAndSessions(0, 5));
	}

	@Test
	@DisplayName("hoursAndSessions rejects non-positive sessions")
	void rejectsNonPositiveSessions() {
		assertThatIllegalArgumentException().isThrownBy(() -> DreamTriggers.hoursAndSessions(24, 0));
	}

	@Test
	@DisplayName("Never dreamed before: time condition is satisfied immediately, session count still gates")
	void neverDreamedBeforeGatesOnSessionsOnly() {
		DreamTrigger trigger = DreamTriggers.hoursAndSessions(24, 5);

		DreamState notEnoughSessions = new DreamState(null, 4, null, null);
		DreamState enoughSessions = new DreamState(null, 5, null, null);

		assertThat(trigger.shouldDream(notEnoughSessions, NOW)).isFalse();
		assertThat(trigger.shouldDream(enoughSessions, NOW)).isTrue();
	}

	@Test
	@DisplayName("Both conditions must hold")
	void bothConditionsMustHold() {
		DreamTrigger trigger = DreamTriggers.hoursAndSessions(24, 5);
		String recentDream = NOW.minusSeconds(3600).toString(); // 1 hour ago — time not met
		String oldDream = NOW.minusSeconds(48 * 3600).toString(); // 48 hours ago — time met

		assertThat(trigger.shouldDream(new DreamState(recentDream, 10, null, null), NOW)).isFalse();
		assertThat(trigger.shouldDream(new DreamState(oldDream, 2, null, null), NOW)).isFalse();
		assertThat(trigger.shouldDream(new DreamState(oldDream, 10, null, null), NOW)).isTrue();
	}

	@Test
	@DisplayName("Exact boundary values satisfy their condition")
	void exactBoundaryIsSatisfied() {
		DreamTrigger trigger = DreamTriggers.hoursAndSessions(24, 5);
		String exactlyTwentyFourHoursAgo = NOW.minusSeconds(24 * 3600).toString();

		assertThat(trigger.shouldDream(new DreamState(exactlyTwentyFourHoursAgo, 5, null, null), NOW)).isTrue();
	}

	@Test
	@DisplayName("manualOnly never fires")
	void manualOnlyNeverFires() {
		DreamTrigger trigger = DreamTriggers.manualOnly();

		assertThat(trigger.shouldDream(new DreamState(null, 1_000_000, null, null), NOW)).isFalse();
	}

}
