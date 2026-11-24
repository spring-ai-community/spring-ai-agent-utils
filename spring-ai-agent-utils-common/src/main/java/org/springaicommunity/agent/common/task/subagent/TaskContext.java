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
package org.springaicommunity.agent.common.task.subagent;

import java.util.Map;

import org.springframework.util.Assert;

/**
 * What the Task tool knows about a call beyond the {@link TaskCall} itself: the sub-agent's
 * own session, when sub-agents have memory, and the tool context of the request that made
 * the call.
 *
 * @param sessionId the sub-agent's session, a child of the caller's session that keeps the
 * sub-agent's transcript; {@code null} when sub-agents have no memory
 * @param toolContext the tool context of the calling request; empty when there is none
 * @author Christian Tzolov
 */
public record TaskContext(String sessionId, Map<String, Object> toolContext) {

	/** A call without a session or a tool context. */
	public static final TaskContext NONE = new TaskContext(null, Map.of());

	public TaskContext {
		Assert.notNull(toolContext, "toolContext must not be null");
	}

}
