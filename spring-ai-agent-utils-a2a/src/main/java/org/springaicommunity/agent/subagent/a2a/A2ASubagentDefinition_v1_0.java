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


import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

import org.a2aproject.sdk.client.Client;
import org.a2aproject.sdk.client.ClientEvent;
import org.a2aproject.sdk.client.MessageEvent;
import org.a2aproject.sdk.client.TaskEvent;
import org.a2aproject.sdk.client.TaskUpdateEvent;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransport;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransportConfigBuilder;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentSkill;
import org.a2aproject.sdk.spec.Artifact;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.Part;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TextPart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.agent.common.task.subagent.SubagentReference;

/**
 * {@link A2ASubagentDefinition} for the A2A protocol 1.0.
 *
 * <p>Wraps a protocol 1.0 {@link AgentCard} and invokes the remote agent through the
 * 1.0 {@code Client} over JSON-RPC. Handles both {@code MessageEvent} (synchronous
 * responses) and task-based events ({@code TaskEvent} / {@code TaskUpdateEvent}).
 *
 * @author Caio Henrique Silva
 * @see A2ASubagentDefinition
 * @see A2ASubagentDefinition_v0_3
 * @see <a href="https://a2a-protocol.org">A2A Protocol Specification</a>
 */
public final class A2ASubagentDefinition_v1_0 implements A2ASubagentDefinition {

    private static final Logger logger = LoggerFactory.getLogger(A2ASubagentDefinition_v1_0.class);
    public static final String PROTOCOL_VERSION = "1.0";

    private final SubagentReference subagentRef;

    private final AgentCard card;

    private final String description;

    public A2ASubagentDefinition_v1_0(SubagentReference subagentRef, AgentCard card) {
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
            Message message = Message.builder()
                    .role(Message.Role.ROLE_USER)
                    .parts(List.of(new TextPart(prompt, null)))
                    .build();

            // Use CompletableFuture to wait for the response
            CompletableFuture<String> responseFuture = new CompletableFuture<>();
            AtomicReference<String> responseText = new AtomicReference<>("");

            BiConsumer<ClientEvent, AgentCard> consumer = (event, card) -> {

                if (event instanceof MessageEvent messageEvent) {
                    Message msg = messageEvent.getMessage();
                    StringBuilder sb = new StringBuilder();
                    for (Part<?> part : msg.parts()) {
                        if (part instanceof TextPart textPart) {
                            sb.append(textPart.text());
                        }
                    }
                    responseFuture.complete(sb.toString());
                    return;
                }

                Task task;
                if (event instanceof TaskEvent taskEvent) {
                    task = taskEvent.getTask();
                } else if (event instanceof TaskUpdateEvent taskUpdateEvent) {
                    task = taskUpdateEvent.getTask();
                } else {
                    return;
                }

                TaskState taskState = task.status().state();
                logger.info("Received task response: status={}", taskState);

                // Extract text from artifacts
                if (task.artifacts() != null) {
                    StringBuilder sb = new StringBuilder();
                    for (Artifact artifact : task.artifacts()) {
                        for (Part<?> part : artifact.parts()) {
                            if (part instanceof TextPart textPart) {
                                sb.append(textPart.text());
                            }
                        }
                    }
                    responseText.set(sb.toString());
                }

                if (taskState.isFinal()) {
                    responseFuture.complete(responseText.get());
                }
            };

            // Create client with consumer via builder
            Client client = Client.builder(card)
                    .withTransport(JSONRPCTransport.class, new JSONRPCTransportConfigBuilder())
                    .addConsumer(consumer)
                    .build();

            client.sendMessage(message);

            return responseFuture.get(60, TimeUnit.SECONDS);
        } catch (Exception e) {
            logger.error("Error sending message to agent '{}': {}", getName(), e.getMessage());
            return String.format("Error communicating with agent '%s': %s", getName(), e.getMessage());
        }
    }

    public AgentCard getAgentCard() {
        return card;
    }

    /**
     * Builds the description from the card description and its skills. Skills are
     * indented so they read as part of this agent's entry in the Task tool's agent list,
     * and listed under "Can help with" rather than "Skills" so the model does not confuse
     * them with Agent Skills invoked through the Skill tool.
     */
    private static String buildDescription(AgentCard card) {
        StringBuilder sb = new StringBuilder(card.description());
        if (!card.skills().isEmpty()) {
            sb.append("\n  Can help with:");
            for (AgentSkill skill : card.skills()) {
                sb.append("\n  - ").append(skill.name()).append(": ").append(skill.description());
                if (skill.examples() != null && !skill.examples().isEmpty()) {
                    sb.append("\n    Examples: ").append(String.join("; ", skill.examples()));
                }
            }
        }
        return sb.toString();
    }

}
