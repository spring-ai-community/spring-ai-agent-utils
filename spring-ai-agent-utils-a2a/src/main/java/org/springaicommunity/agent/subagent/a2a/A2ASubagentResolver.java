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

import org.a2aproject.sdk.client.http.A2ACardResolver;
import org.a2aproject.sdk.compat03.A2A_v0_3;
import org.a2aproject.sdk.compat03.spec.AgentCard_v0_3;
import org.a2aproject.sdk.spec.AgentCard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.agent.common.task.subagent.SubagentReference;
import org.springaicommunity.agent.common.task.subagent.SubagentResolver;

/**
 * Resolves A2A subagent references by fetching the AgentCard from the well-known endpoint.
 * Demonstrates how to implement {@link SubagentResolver} for remote agent discovery.
 *
 * @author Christian Tzolov
 * @see <a href="https://a2a-protocol.org">A2A Protocol Specification</a>
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
            String protocolVersion = resolveProtocolVersion(subagentRef);

            if (A2ASubagentDefinition_v1_0.PROTOCOL_VERSION.equals(protocolVersion)) {
                AgentCard card = A2ACardResolver.builder()
                        .baseUrl(url)
                        .agentCardPath(this.agentCardPath)
                        .build()
                        .getAgentCard();
                logger.debug("Discovered agent: {} at {}", card.name(), url);
                return new A2ASubagentDefinition_v1_0(subagentRef, card);
            }

            AgentCard_v0_3 card = A2A_v0_3.getAgentCard(url, this.agentCardPath, null);
            logger.debug("Discovered v0.3 agent: {} at {}", card.name(), url);
            return new A2ASubagentDefinition_v0_3(subagentRef, card);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Reads the protocol version from reference metadata, defaulting to 0.3 when absent.
     * The kind alone ("A2A") does not carry version information.
     */
    private static String resolveProtocolVersion(SubagentReference ref) {
        return ref.metadata().getOrDefault(
                SubagentReference.META_PROTOCOL_VERSION,
                A2ASubagentDefinition_v0_3.PROTOCOL_VERSION);
    }
}
