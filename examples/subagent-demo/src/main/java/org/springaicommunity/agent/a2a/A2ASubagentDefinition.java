/*
* Copyright 2025 - 2025 the original author or authors.
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
package org.springaicommunity.agent.a2a;


import io.a2a.spec.AgentCard;
import org.springaicommunity.agent.tools.task.subagent.SubagentDefinition;
import org.springaicommunity.agent.tools.task.subagent.SubagentReference;

/**
 * A2A protocol subagent definition wrapping an AgentCard.
 * Demonstrates how to implement {@link SubagentDefinition} for remote agent protocols.
 *
 * @author Christian Tzolov
 * @see <a href="https://google.github.io/A2A/">A2A Protocol Specification</a>
 */
public class A2ASubagentDefinition implements SubagentDefinition {

	public static final String KIND = "A2A";

	private final SubagentReference subagentRef;

	private final AgentCard card;

	public A2ASubagentDefinition(SubagentReference subagentRef, AgentCard card) {
		this.subagentRef = subagentRef;
		this.card = card;
	}

	@Override
	public String getName() {
		return card.name();
	}

	@Override
	public String getDescription() {
		return card.description(); // TODO include more details like skills?
	}

	@Override
	public String getKind() {
		return KIND;
	}

	@Override
	public SubagentReference getReference() {
		return subagentRef;
	}

	public AgentCard getAgentCard() {
		return card;
	}

}
