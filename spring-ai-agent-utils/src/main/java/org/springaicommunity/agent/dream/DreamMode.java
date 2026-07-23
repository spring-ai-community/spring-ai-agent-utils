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
 * Controls whether a dream cycle edits the live memory store directly, or writes its
 * edits to a reviewable shadow copy.
 *
 * @author Christian Tzolov
 */
public enum DreamMode {

	/**
	 * The Dreamer edits memory files directly via {@code AutoMemoryTools}. The default —
	 * appropriate for a single-user/single-agent memory store.
	 */
	AUTO_APPLY,

	/**
	 * The Dreamer's edits are redirected to a timestamped copy under
	 * {@code <memoriesDir>/.dream-proposals/}, leaving the live memory store untouched.
	 * The proposal can be reviewed and applied later via the ordinary memory tools.
	 */
	PROPOSE

}
