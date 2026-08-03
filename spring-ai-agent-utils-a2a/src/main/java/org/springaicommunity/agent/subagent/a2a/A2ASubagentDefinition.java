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


import io.a2a.spec.AgentCard;
import io.a2a.spec.AgentSkill;
import org.springaicommunity.agent.common.task.subagent.SubagentDefinition;
import org.springaicommunity.agent.common.task.subagent.SubagentReference;

import java.util.List;
import java.util.Objects;

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

    private final String description;

    public A2ASubagentDefinition(SubagentReference subagentRef, AgentCard card) {
        this.subagentRef = Objects.requireNonNull(subagentRef);
        this.card = Objects.requireNonNull(card);
        this.description = buildDescription(card);
    }

    @Override
    public String getName() {
        return card.name();
    }

    @Override
    public String getDescription() {
        return description;
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

    private static String buildDescription(AgentCard card) {
        StringBuilder sb = new StringBuilder(card.description());
        List<AgentSkill> agentSkills = card.skills();

        if (agentSkills.isEmpty()) {
            return sb.toString();
        }

        sb.append(System.lineSeparator())
                .append("Capabilities:");

        for (AgentSkill skill : agentSkills) {
            sb.append(System.lineSeparator())
                    .append("- ").append(skill.name())
                    .append(": ").append(skill.description());

            if (skill.examples() != null && !skill.examples().isEmpty()) {
                skill.examples().forEach(example -> sb.append(System.lineSeparator())
                        .append("  - Example: ")
                        .append(Objects.toString(example, "")));
            }
        }

        return sb.toString();
    }
}
