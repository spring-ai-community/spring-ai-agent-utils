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

import java.net.URI;

import io.a2a.A2A;
import io.a2a.spec.AgentCard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.agent.common.task.subagent.SubagentReference;
import org.springaicommunity.agent.common.task.subagent.SubagentResolver;

/**
 * Resolves A2A subagent references by fetching the AgentCard from the well-known endpoint.
 * Demonstrates how to implement {@link SubagentResolver} for remote agent discovery.
 *
 * @author Christian Tzolov
 * @see <a href="https://google.github.io/A2A/">A2A Protocol Specification</a>
 */
public class A2ASubagentResolver implements SubagentResolver {

	public static final String WELL_KNOWN_AGENT_CARD_PATH = "/.well-known/agent-card.json";

	private static final Logger logger = LoggerFactory.getLogger(A2ASubagentResolver.class);

	private final String agentCardPath;

	public A2ASubagentResolver() {
		this(WELL_KNOWN_AGENT_CARD_PATH);
	}

	public A2ASubagentResolver(String agentCardPath) {
		this.agentCardPath = agentCardPath;
	}

	@Override
	public boolean canResolve(SubagentReference subagentRef) {
		if (subagentRef == null) {
			throw new IllegalArgumentException("SubagentReference must not be null");
		}
		return subagentRef.kind().equals(A2ASubagentDefinition.KIND);
	}

	@Override
	public A2ASubagentDefinition resolve(SubagentReference subagentRef) {
		if (subagentRef == null) {
			throw new IllegalArgumentException("SubagentReference must not be null");
		}
		try {
			String url = subagentRef.uri();
			String path = new URI(url).getPath();
			AgentCard card = A2A.getAgentCard(url, path + agentCardPath, null);
			logger.debug("Discovered agent: {} at {}", card.name(), url);
			return new A2ASubagentDefinition(subagentRef, card);
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

}
