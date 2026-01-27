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

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

import io.a2a.client.Client;
import io.a2a.client.ClientEvent;
import io.a2a.client.TaskEvent;
import io.a2a.client.config.ClientConfig;
import io.a2a.client.transport.jsonrpc.JSONRPCTransport;
import io.a2a.client.transport.jsonrpc.JSONRPCTransportConfig;
import io.a2a.spec.AgentCard;
import io.a2a.spec.Artifact;
import io.a2a.spec.Message;
import io.a2a.spec.Part;
import io.a2a.spec.Task;
import io.a2a.spec.TextPart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.agent.tools.task.TaskTool.TaskCall;
import org.springaicommunity.agent.tools.task.subagent.SubagentDefinition;
import org.springaicommunity.agent.tools.task.subagent.SubagentExecutor;

/**
 * Executes tasks via A2A protocol by sending messages to remote agents.
 * Demonstrates how to implement {@link SubagentExecutor} for remote agent communication.
 *
 * @author Christian Tzolov
 * @see <a href="https://google.github.io/A2A/">A2A Protocol Specification</a>
 */
public class A2ASubagentExecutor implements SubagentExecutor {

	private static final Logger logger = LoggerFactory.getLogger(A2ASubagentExecutor.class);

	@Override
	public String getKind() {
		return A2ASubagentDefinition.KIND;
	}

	@Override
	public String execute(TaskCall taskCall, SubagentDefinition subagent) {

		AgentCard agentCard = ((A2ASubagentDefinition) subagent).getAgentCard();

		try {
			// Create the message
			Message message = new Message.Builder().role(Message.Role.USER)
				.parts(List.of(new TextPart(taskCall.prompt(), null)))
				.build();

			// Use CompletableFuture to wait for the response
			CompletableFuture<String> responseFuture = new CompletableFuture<>();
			AtomicReference<String> responseText = new AtomicReference<>("");

			BiConsumer<ClientEvent, AgentCard> consumer = (event, card) -> {
				if (event instanceof TaskEvent taskEvent) {
					Task completedTask = taskEvent.getTask();
					logger.info("Received task response: status={}", completedTask.getStatus().state());

					// Extract text from artifacts
					if (completedTask.getArtifacts() != null) {
						StringBuilder sb = new StringBuilder();
						for (Artifact artifact : completedTask.getArtifacts()) {
							if (artifact.parts() != null) {
								for (Part<?> part : artifact.parts()) {
									if (part instanceof TextPart textPart) {
										sb.append(textPart.getText());
									}
								}
							}
						}
						responseText.set(sb.toString());
					}
					responseFuture.complete(responseText.get());
				}
			};

			// Create client with consumer via builder
			ClientConfig clientConfig = new ClientConfig.Builder().setAcceptedOutputModes(List.of("text")).build();
			
			Client client = Client.builder(agentCard)
				.clientConfig(clientConfig)
				.withTransport(JSONRPCTransport.class, new JSONRPCTransportConfig())
				.addConsumers(List.of(consumer))
				.build();

			client.sendMessage(message);

			// Wait for response (with timeout)
			String result = responseFuture.get(60, java.util.concurrent.TimeUnit.SECONDS);
			logger.info("Agent '{}' response: {}", subagent.getName(), result);
			return result;
		}
		catch (Exception e) {
			logger.error("Error sending message to agent '{}': {}", subagent.getName(), e.getMessage());
			return String.format("Error communicating with agent '%s': %s", subagent.getName(), e.getMessage());
		}
	}

}
