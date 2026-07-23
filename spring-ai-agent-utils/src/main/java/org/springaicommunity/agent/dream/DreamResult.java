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

/**
 * Outcome of a single {@code AutoDreamService.runDreamCycle(...)} invocation.
 *
 * @param status one of {@code "completed"}, {@code "failed"}, or {@code "skipped"}
 * @param summary the Dreamer's own summary of what changed, the failure message, or the
 * reason the cycle was skipped
 * @author Christian Tzolov
 */
public record DreamResult(String status, String summary) {

	public static DreamResult completed(String summary) {
		return new DreamResult("completed", summary);
	}

	public static DreamResult failed(String summary) {
		return new DreamResult("failed", summary);
	}

	public static DreamResult skipped(String reason) {
		return new DreamResult("skipped", reason);
	}

}
