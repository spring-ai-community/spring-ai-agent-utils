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
package org.springaicommunity.agent.tools;

import org.springframework.ai.chat.model.ToolContext;

/**
 * Observes the activation of a {@link SkillsTool.Skill} when successfully loaded by the
 * model.
 *
 * <p>
 * Listeners are attached via {@link SkillsTool.Builder#onSkillLoaded(SkillLoadedListener)}.
 * When multiple listeners are registered, they are called in registration order.
 *
 * <p>
 * <b>Lifecycle and timing:</b>
 * {@link #onSkillLoaded(SkillsTool.Skill, ToolContext)} is called synchronously on the
 * tool execution thread only <em>after</em> the skill has been matched and its content
 * (including any {@link org.springaicommunity.agent.common.workspace.Workspace} path
 * mapping) has been successfully prepared for the model. If skill lookup fails ("Skill not
 * found") or workspace translation throws, this method is not invoked.
 *
 * <p>
 * <b>Exception policy:</b>
 * Exceptions thrown by listener implementations are deliberately not caught or guarded.
 * A throwing listener immediately aborts the tool invocation and propagates the failure
 * to the caller, without rolling back side effects from previously executed listeners.
 * Catch and handle your own non-fatal failures inside the listener.
 *
 * <p>
 * <b>Thread safety:</b>
 * The resulting tool callback may be invoked concurrently across multiple turns or
 * threads when shared. Listener implementations must be thread-safe.
 *
 * @author Christian Tzolov
 */
@FunctionalInterface
public interface SkillLoadedListener {

	/**
	 * Called when a skill has been successfully resolved and prepared for the model.
	 * @param skill the matched skill definition and content
	 * @param toolContext the execution context associated with this invocation; may be
	 * null
	 */
	void onSkillLoaded(SkillsTool.Skill skill, ToolContext toolContext);

}
