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

import org.a2aproject.sdk.compat03.client.ClientEvent_v0_3;
import org.a2aproject.sdk.compat03.client.Client_v0_3;
import org.a2aproject.sdk.compat03.client.MessageEvent_v0_3;
import org.a2aproject.sdk.compat03.client.TaskEvent_v0_3;
import org.a2aproject.sdk.compat03.client.TaskUpdateEvent_v0_3;
import org.a2aproject.sdk.compat03.client.transport.jsonrpc.JSONRPCTransportConfigBuilder_v0_3;
import org.a2aproject.sdk.compat03.client.transport.jsonrpc.JSONRPCTransport_v0_3;
import org.a2aproject.sdk.compat03.spec.AgentCard_v0_3;
import org.a2aproject.sdk.compat03.spec.AgentSkill_v0_3;
import org.a2aproject.sdk.compat03.spec.Artifact_v0_3;
import org.a2aproject.sdk.compat03.spec.Message_v0_3;
import org.a2aproject.sdk.compat03.spec.Part_v0_3;
import org.a2aproject.sdk.compat03.spec.TaskState_v0_3;
import org.a2aproject.sdk.compat03.spec.Task_v0_3;
import org.a2aproject.sdk.compat03.spec.TextPart_v0_3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.agent.common.task.subagent.SubagentReference;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

/**
 * {@link A2ASubagentDefinition} for the A2A protocol 0.3.
 *
 * <p>Wraps a protocol 0.3 {@link AgentCard_v0_3} and invokes the remote agent through
 * the 0.3 {@code Client_v0_3} over JSON-RPC. Handles both {@code MessageEvent_v0_3}
 * (synchronous responses) and task-based events
 * ({@code TaskEvent_v0_3} / {@code TaskUpdateEvent_v0_3}).
 *
 * @author Christian Tzolov
 * @see A2ASubagentDefinition
 * @see A2ASubagentDefinition_v1_0
 * @see <a href="https://a2a-protocol.org">A2A Protocol Specification</a>
 */
public final class A2ASubagentDefinition_v0_3 implements A2ASubagentDefinition {

    private static final Logger logger = LoggerFactory.getLogger(A2ASubagentDefinition_v0_3.class);
    public static final String PROTOCOL_VERSION = "0.3";

    private final SubagentReference subagentRef;
    private final AgentCard_v0_3 card;
    private final String description;

    public A2ASubagentDefinition_v0_3(SubagentReference subagentRef, AgentCard_v0_3 card) {
        this.subagentRef = Objects.requireNonNull(subagentRef, "subagentRef must not be null");
        this.card = Objects.requireNonNull(card, "card must not be null");
        this.description = buildDescription(card);
    }

    @Override
    public String getName() {
        return card.name();
    }

    @Override
    public String getDescription() {
        return this.description;
    }

    @Override
    public String getKind() {
        return KIND;
    }

    @Override
    public SubagentReference getReference() {
        return subagentRef;
    }

    @Override
    public String invoke(String prompt) {
        try {
            // Create the message
            Message_v0_3 message = new Message_v0_3.Builder()
                    .role(Message_v0_3.Role.USER)
                    .parts(List.of(new TextPart_v0_3(prompt, null)))
                    .build();

            CompletableFuture<String> responseFuture = new CompletableFuture<>();
            AtomicReference<String> responseText = new AtomicReference<>("");

            BiConsumer<ClientEvent_v0_3, AgentCard_v0_3> consumer = (event, card) -> {

                // 1. Resposta síncrona completa
                if (event instanceof MessageEvent_v0_3 messageEvent) {
                    Message_v0_3 msg = messageEvent.getMessage();
                    StringBuilder sb = new StringBuilder();
                    if (msg.parts() != null) {
                        for (Part_v0_3<?> part : msg.parts()) {
                            if (part instanceof TextPart_v0_3 textPart) {
                                sb.append(textPart.text());
                            }
                        }
                    }
                    responseFuture.complete(sb.toString());
                    return;
                }

                // 2. Task completa ou atualização incremental
                Task_v0_3 task;
                if (event instanceof TaskEvent_v0_3 taskEvent) {
                    task = taskEvent.getTask();
                } else if (event instanceof TaskUpdateEvent_v0_3 taskUpdateEvent) {
                    task = taskUpdateEvent.getTask();
                } else {
                    return;
                }

                TaskState_v0_3 taskState = task.status().state();
                logger.info("Received task response: status={}", taskState);

                if (task.artifacts() != null) {
                    StringBuilder sb = new StringBuilder();
                    for (Artifact_v0_3 artifact : task.artifacts()) {
                        if (artifact.parts() != null) {
                            for (Part_v0_3<?> part : artifact.parts()) {
                                if (part instanceof TextPart_v0_3 textPart) {
                                    sb.append(textPart.text());
                                }
                            }
                        }
                    }
                    responseText.set(sb.toString());
                }

                if (taskState.isFinal()) {
                    responseFuture.complete(responseText.get());
                }
            };

            Client_v0_3 client = Client_v0_3.builder(card)
                    .withTransport(JSONRPCTransport_v0_3.class,
                            new JSONRPCTransportConfigBuilder_v0_3())
                    .addConsumer(consumer)
                    .build();

            client.sendMessage(message);

            return responseFuture.get(60, TimeUnit.SECONDS);
        } catch (Exception e) {
            logger.error("Error sending message to agent '{}': {}", getName(), e.getMessage());
            return String.format("Error communicating with agent '%s': %s", getName(), e.getMessage());
        }
    }

    public AgentCard_v0_3 getAgentCard() {
        return card;
    }

    /**
     * Builds the description from the card description and its skills. Skills are
     * indented so they read as part of this agent's entry in the Task tool's agent list,
     * and listed under "Can help with" rather than "Skills" so the model does not confuse
     * them with Agent Skills invoked through the Skill tool.
     */
    private static String buildDescription(AgentCard_v0_3 card) {

        StringBuilder sb = new StringBuilder(card.description());
        if (!card.skills().isEmpty()) {
            sb.append("\n  Can help with:");
            for (AgentSkill_v0_3 skill : card.skills()) {
                sb.append("\n  - ").append(skill.name()).append(": ").append(skill.description());
                if (skill.examples() != null && !skill.examples().isEmpty()) {
                    sb.append("\n    Examples: ").append(String.join("; ", skill.examples()));
                }
            }
        }
        return sb.toString();
    }
}
