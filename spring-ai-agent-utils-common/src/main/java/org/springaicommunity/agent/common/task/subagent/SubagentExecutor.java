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

/**
 * Executes subagent tasks for a specific subagent kind.
 *
 * @author Christian Tzolov
 */
public interface SubagentExecutor {

	/** Returns the kind of subagent this executor handles. */
	String getKind();

	/** Executes the task using the specified subagent definition. */
	String execute(TaskCall taskCall, SubagentDefinition subagent);

	/**
	 * Executes the task with what the Task tool knows about the call: the sub-agent's
	 * session when sub-agents have memory, and the caller's tool context. An executor that
	 * can keep the sub-agent's transcript in that session overrides this; the default
	 * ignores the context and runs {@link #execute(TaskCall, SubagentDefinition)}.
	 */
	default String execute(TaskCall taskCall, SubagentDefinition subagent, TaskContext context) {
		return execute(taskCall, subagent);
	}

	/**
	 * Whether {@link #execute(TaskCall, SubagentDefinition, TaskContext)} keeps the
	 * sub-agent's transcript in {@link TaskContext#sessionId()}, so that a later task can
	 * resume it. The Task tool gives a sub-agent a session of its own, and returns an agent
	 * id to resume with, only when its executor does; {@code false} by default.
	 */
	default boolean keepsSessions() {
		return false;
	}

}
