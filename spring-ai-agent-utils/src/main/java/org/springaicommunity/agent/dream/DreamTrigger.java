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

/**
 * Decides whether {@code AutoDreamAdvisor} should kick off a background dream cycle,
 * evaluated against the persisted {@link DreamState} rather than in-memory counters, so
 * the decision survives process restarts.
 *
 * @author Christian Tzolov
 * @see DreamTriggers
 */
@FunctionalInterface
public interface DreamTrigger {

	/**
	 * @param state the dream state as of the current conversation turn (already
	 * reflecting this turn's session increment)
	 * @param now wall-clock time of the current turn
	 * @return {@code true} if a dream cycle should be started now
	 */
	boolean shouldDream(DreamState state, Instant now);

}
