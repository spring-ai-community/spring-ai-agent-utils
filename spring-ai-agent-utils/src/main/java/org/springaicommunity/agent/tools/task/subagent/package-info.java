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

/**
 * Subagent Framework for Multi-Agent Orchestration.
 *
 * <p>This package provides a protocol-agnostic abstraction for integrating various agent
 * communication protocols with the {@link org.springaicommunity.agent.tools.task.TaskTool}.
 * It enables orchestrating agents across different backends - local LLM-based agents,
 * remote A2A protocol agents, or custom agent implementations.
 *
 * <h2>Architecture Overview</h2>
 *
 * <p>The subagent system uses a resolve-then-execute pattern with three core components:
 *
 * <pre>
 * SubagentReference (URI + Kind) → SubagentResolver → SubagentDefinition → SubagentExecutor → Response
 * </pre>
 *
 * <ul>
 *   <li>{@link org.springaicommunity.agent.tools.task.subagent.SubagentReference} - Lightweight
 *       pointer to a subagent (URI, kind, metadata)</li>
 *   <li>{@link org.springaicommunity.agent.tools.task.subagent.SubagentResolver} - Resolves
 *       references into full definitions</li>
 *   <li>{@link org.springaicommunity.agent.tools.task.subagent.SubagentDefinition} - Complete
 *       agent metadata (name, description, configuration)</li>
 *   <li>{@link org.springaicommunity.agent.tools.task.subagent.SubagentExecutor} - Executes
 *       tasks using a specific protocol</li>
 *   <li>{@link org.springaicommunity.agent.tools.task.subagent.SubagentType} - Pairs a resolver
 *       and executor for a specific kind</li>
 * </ul>
 *
 * <h2>Built-in Claude Subagent (Local LLM)</h2>
 *
 * <p>The default implementation uses Claude Code's markdown + YAML frontmatter convention.
 * Subagents are defined in markdown files with configuration in the frontmatter:
 *
 * <pre>{@code
 * ---
 * name: spring-ai-expert
 * description: Expert on Spring AI framework
 * model: sonnet
 * tools: Read, Grep, WebFetch
 * ---
 *
 * You are a Spring AI expert...
 * }</pre>
 *
 * <p>Claude subagent classes:
 * <ul>
 *   <li>{@code ClaudeSubagentDefinition} - Parses frontmatter configuration</li>
 *   <li>{@code ClaudeSubagentResolver} - Loads markdown files from classpath/filesystem</li>
 *   <li>{@code ClaudeSubagentExecutor} - Executes via Spring AI ChatClient</li>
 *   <li>{@code ClaudeSubagentReferences} - Factory methods for discovering agents</li>
 * </ul>
 *
 * <h2>Extending with Custom Protocols (e.g., A2A)</h2>
 *
 * <p>The framework supports integrating other agent communication protocols. For example,
 * the A2A (Agent-to-Agent) protocol integration demonstrates remote agent orchestration:
 *
 * <pre>{@code
 * // 1. Define the subagent definition (wraps protocol-specific metadata)
 * public class A2ASubagentDefinition implements SubagentDefinition {
 *     public static final String KIND = "A2A";
 *     private final AgentCard card; // A2A protocol's agent descriptor
 *
 *     public String getName() { return card.name(); }
 *     public String getDescription() { return card.description(); }
 *     public String getKind() { return KIND; }
 * }
 *
 * // 2. Implement the resolver (discovers agents via protocol)
 * public class A2ASubagentResolver implements SubagentResolver {
 *     public boolean canResolve(SubagentReference ref) {
 *         return ref.kind().equals(A2ASubagentDefinition.KIND);
 *     }
 *     public SubagentDefinition resolve(SubagentReference ref) {
 *         AgentCard card = fetchAgentCard(ref.uri()); // HTTP discovery
 *         return new A2ASubagentDefinition(ref, card);
 *     }
 * }
 *
 * // 3. Implement the executor (communicates via protocol)
 * public class A2ASubagentExecutor implements SubagentExecutor {
 *     public String getKind() { return A2ASubagentDefinition.KIND; }
 *     public String execute(TaskCall task, SubagentDefinition subagent) {
 *         // Send message via A2A protocol and return response
 *     }
 * }
 * }</pre>
 *
 * <h2>Registration with TaskTool</h2>
 *
 * <p>Register custom subagent types via {@code TaskToolCallbackProvider}:
 *
 * <pre>{@code
 * var taskTools = TaskToolCallbackProvider.builder()
 *     // Local Claude subagents (markdown files)
 *     .subagentReferences(ClaudeSubagentReferences.fromResources(agentPaths))
 *     .chatClientBuilder("default", chatClientBuilder)
 *
 *     // Remote A2A subagents
 *     .subagentReferences(new SubagentReference("http://agent-host:10001", "A2A"))
 *     .subagentTypes(new SubagentType(new A2ASubagentResolver(), new A2ASubagentExecutor()))
 *
 *     .build();
 * }</pre>
 *
 * <h2>Supported Protocol Patterns</h2>
 *
 * <p>This abstraction can accommodate various agent communication patterns:
 * <ul>
 *   <li><b>Local LLM</b> - Claude subagent via Spring AI ChatClient</li>
 *   <li><b>A2A Protocol</b> - Google's Agent-to-Agent protocol for remote agents</li>
 *   <li><b>MCP</b> - Model Context Protocol for tool-augmented agents</li>
 *   <li><b>Custom HTTP/gRPC</b> - Any request-response agent API</li>
 * </ul>
 *
 * @see org.springaicommunity.agent.tools.task.TaskTool
 * @see org.springaicommunity.agent.tools.task.TaskToolCallbackProvider
 */
package org.springaicommunity.agent.tools.task.subagent;
