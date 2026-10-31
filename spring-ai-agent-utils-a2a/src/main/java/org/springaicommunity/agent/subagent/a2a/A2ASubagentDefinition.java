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

/**
 * Common contract for A2A subagent definitions, implemented per protocol version
 * ({@link A2ASubagentDefinition_v1_0} and {@link A2ASubagentDefinition_v0_3}).
 *
 * <p>Each implementation wraps the {@code AgentCard} of its protocol version and
 * invokes the remote agent through the matching A2A client. This lets the
 * {@link A2ASubagentExecutor} remain protocol-agnostic and dispatch via
 * {@link #invoke(String)}.
 *
 * @author Caio Henrique Silva
 * @see A2ASubagentDefinition_v1_0
 * @see A2ASubagentDefinition_v0_3
 * @see <a href="https://a2a-protocol.org">A2A Protocol Specification</a>
 */
public sealed interface A2ASubagentDefinition extends SubagentDefinition
        permits A2ASubagentDefinition_v1_0, A2ASubagentDefinition_v0_3 {

    String KIND = "A2A";

    String invoke(String prompt);
}
