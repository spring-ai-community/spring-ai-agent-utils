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

import java.time.Duration;
import java.time.Instant;

import org.springframework.util.Assert;

/**
 * Common {@link DreamTrigger} factories.
 *
 * @author Christian Tzolov
 */
public final class DreamTriggers {

	private DreamTriggers() {
	}

	/**
	 * Fires only once <strong>both</strong> conditions hold since the last dream cycle:
	 * at least {@code hours} have elapsed, and at least {@code sessions} conversation
	 * turns have been processed. Mirrors Claude Code's Auto Dream dual-condition
	 * heuristic. A memory store that has never been dreamed on satisfies the time
	 * condition immediately, so only the session-count condition gates the first cycle.
	 * @param hours minimum hours since the last dream cycle; must be positive
	 * @param sessions minimum conversation turns since the last dream cycle; must be
	 * positive
	 */
	public static DreamTrigger hoursAndSessions(long hours, int sessions) {
		Assert.isTrue(hours > 0, "hours must be greater than 0");
		Assert.isTrue(sessions > 0, "sessions must be greater than 0");

		Duration minElapsed = Duration.ofHours(hours);
		return (state, now) -> {
			Instant lastDreamAt = state.lastDreamInstant();
			boolean timeConditionMet = lastDreamAt == null
					|| Duration.between(lastDreamAt, now).compareTo(minElapsed) >= 0;
			boolean sessionConditionMet = state.sessionsSinceLastDream() >= sessions;
			return timeConditionMet && sessionConditionMet;
		};
	}

	/**
	 * Never fires automatically — dream cycles only run when
	 * {@code AutoDreamService.runDreamCycle(...)} is invoked explicitly.
	 */
	public static DreamTrigger manualOnly() {
		return (state, now) -> false;
	}

}
