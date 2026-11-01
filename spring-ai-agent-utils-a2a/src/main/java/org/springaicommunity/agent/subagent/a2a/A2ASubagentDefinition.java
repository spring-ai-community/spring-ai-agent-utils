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

import org.springaicommunity.agent.common.task.subagent.SubagentDefinition;
import org.springaicommunity.agent.common.task.subagent.SubagentReference;

import java.util.Objects;

/**
 * Base type for A2A subagent definitions. A definition wraps an agent card
 * discovered from a remote A2A agent and knows how to invoke that agent using
 * the A2A client matching its protocol version.
 *
 * <p>The hierarchy is sealed so the set of supported protocol versions stays
 * explicit: adding a version requires a new {@code permits} entry here, which
 * forces any exhaustive switch over the hierarchy to be revisited at compile
 * time instead of failing at runtime. Currently supported:
 * {@link A2ASubagentDefinition_v1_0} (1.0) and
 * {@link A2ASubagentDefinition_v0_3} (0.3).
 *
 * <p>Instances are created by {@link A2ASubagentResolver}, which picks the
 * implementation from the protocol version declared in the
 * {@code SubagentReference}, and consumed polymorphically by
 * {@link A2ASubagentExecutor} through {@link #invoke(String)}, so the executor
 * has no knowledge of protocol versions.
 *
 * @author Caio Henrique Silva
 * @see A2ASubagentDefinition_v1_0
 * @see A2ASubagentDefinition_v0_3
 * @see A2ASubagentResolver
 * @see A2ASubagentExecutor
 * @see <a href="https://a2a-protocol.org">A2A Protocol Specification</a>
 */
public abstract sealed class A2ASubagentDefinition implements SubagentDefinition
        permits A2ASubagentDefinition_v1_0, A2ASubagentDefinition_v0_3 {

    public static final String KIND = "A2A";

    private final SubagentReference subagentRef;
    private final String description;

    protected A2ASubagentDefinition(SubagentReference subagentRef, String description) {
        this.subagentRef = Objects.requireNonNull(subagentRef, "subagentRef must not be null");
        this.description = Objects.requireNonNull(description, "description must not be null");
    }

    @Override
    public final String getDescription() {
        return this.description;
    }

    @Override
    public final String getKind() {
        return KIND;
    }

    @Override
    public final SubagentReference getReference() {
        return this.subagentRef;
    }

    public abstract String invoke(String prompt);
}
