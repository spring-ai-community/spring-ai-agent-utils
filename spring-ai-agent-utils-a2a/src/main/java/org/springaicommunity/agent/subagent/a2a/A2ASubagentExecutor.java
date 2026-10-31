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
package org.springaicommunity.agent.subagent.a2a;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.agent.common.task.subagent.SubagentDefinition;
import org.springaicommunity.agent.common.task.subagent.SubagentExecutor;
import org.springaicommunity.agent.common.task.subagent.TaskCall;

/**
 * Executes tasks via A2A protocol by sending messages to remote agents.
 * Demonstrates how to implement {@link SubagentExecutor} for remote agent communication.
 *
 * @author Christian Tzolov
 * @see <a href="https://a2a-protocol.org">A2A Protocol Specification</a>
 */
public class A2ASubagentExecutor implements SubagentExecutor {

	private static final Logger logger = LoggerFactory.getLogger(A2ASubagentExecutor.class);

	@Override
	public String getKind() {
		return A2ASubagentDefinition.KIND;
	}

	@Override
	public String execute(TaskCall taskCall, SubagentDefinition subagent) {

		A2ASubagentDefinition a2ASubagentDefinition = (A2ASubagentDefinition) subagent;
		String result = a2ASubagentDefinition.invoke(taskCall.prompt());
		logger.info("Agent '{}' response: {}", subagent.getName(), result);
		return result;
	}

}
