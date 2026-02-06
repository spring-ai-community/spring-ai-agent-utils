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
 * Claude Code Subagent Implementation.
 *
 * <p>Default subagent implementation using Claude Code's markdown + YAML frontmatter
 * convention. Agents are defined as markdown files with configuration frontmatter:
 *
 * <pre>{@code
 * ---
 * name: my-agent
 * description: Agent description for TaskTool registration
 * model: sonnet              # Optional: model override (default inherits parent)
 * tools: Read, Grep, Bash    # Optional: allowed tools (default inherits all)
 * disallowedTools: Write     # Optional: denied tools
 * skills: ai-tutor           # Optional: skills to inject at startup
 * permissionMode: default    # Optional: permission handling mode
 * ---
 *
 * System prompt content goes here...
 * }</pre>
 *
 * <h2>Components</h2>
 *
 * <ul>
 *   <li>{@link org.springaicommunity.agent.tools.task.claude.ClaudeSubagentDefinition}
 *       - Parsed representation of a Claude subagent from markdown frontmatter</li>
 *   <li>{@link org.springaicommunity.agent.tools.task.claude.ClaudeSubagentResolver}
 *       - Resolves markdown URIs (classpath/file) into definitions</li>
 *   <li>{@link org.springaicommunity.agent.tools.task.claude.ClaudeSubagentExecutor}
 *       - Executes tasks via Spring AI ChatClient with tool filtering</li>
 *   <li>{@link org.springaicommunity.agent.tools.task.claude.ClaudeSubagentReferences}
 *       - Factory methods for discovering agent files from directories/resources</li>
 * </ul>
 *
 * <h2>Frontmatter Fields</h2>
 *
 * <table border="1">
 *   <tr><th>Field</th><th>Required</th><th>Description</th></tr>
 *   <tr><td>name</td><td>Yes</td><td>Unique identifier for the subagent</td></tr>
 *   <tr><td>description</td><td>Yes</td><td>Displayed in TaskTool's available agents list</td></tr>
 *   <tr><td>model</td><td>No</td><td>Model override (sonnet, opus, haiku)</td></tr>
 *   <tr><td>tools</td><td>No</td><td>Comma-separated allowed tool names</td></tr>
 *   <tr><td>disallowedTools</td><td>No</td><td>Comma-separated denied tool names</td></tr>
 *   <tr><td>skills</td><td>No</td><td>Skills injected into system prompt</td></tr>
 *   <tr><td>permissionMode</td><td>No</td><td>Permission handling strategy</td></tr>
 * </table>
 *
 * <h2>Usage</h2>
 *
 * <pre>{@code
 * // Discover agents from a directory
 * List<SubagentReference> refs = ClaudeSubagentReferences.fromRootDirectory("/agents");
 *
 * // Or from Spring Resources
 * List<SubagentReference> refs = ClaudeSubagentReferences.fromResources(agentResources);
 *
 * // Register with TaskToolCallbackProvider
 * TaskToolCallbackProvider.builder()
 *     .subagentReferences(refs)
 *     .chatClientBuilder("default", chatClientBuilder)
 *     .build();
 * }</pre>
 *
 * @see <a href="https://code.claude.com/docs/en/sub-agents">Claude Code Sub-agents Documentation</a>
 */
package org.springaicommunity.agent.tools.task.claude;
