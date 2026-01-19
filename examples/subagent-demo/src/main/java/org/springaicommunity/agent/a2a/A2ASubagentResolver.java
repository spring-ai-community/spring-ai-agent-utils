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

import io.a2a.client.http.A2ACardResolver;
import io.a2a.client.http.JdkA2AHttpClient;
import io.a2a.spec.AgentCard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.agent.tools.task.subagent.SubagentReference;
import org.springaicommunity.agent.tools.task.subagent.SubagentResolver;

import org.springframework.util.Assert;

/**
 * @author Christian Tzolov
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
		Assert.notNull(subagentRef, "SubagentReference must not be null");
		return subagentRef.kind().equals(A2ASubagentDefinition.KIND);
	}

	@Override
	public A2ASubagentDefinition resolve(SubagentReference subagentRef) {
		Assert.notNull(subagentRef, "SubagentReference must not be null");
		try {
			String url = subagentRef.uri();
			AgentCard card = new A2ACardResolver(new JdkA2AHttpClient(), url, agentCardPath, null).getAgentCard();
			logger.debug("Discovered agent: {} at {}", card.name(), url);
			return new A2ASubagentDefinition(subagentRef, card);
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

}
