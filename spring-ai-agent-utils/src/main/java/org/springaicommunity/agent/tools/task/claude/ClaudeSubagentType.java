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
package org.springaicommunity.agent.tools.task.claude;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springaicommunity.agent.common.task.subagent.SubagentType;
import org.springaicommunity.agent.tools.BraveWebSearchTool;
import org.springaicommunity.agent.tools.FileSystemTools;
import org.springaicommunity.agent.tools.GlobTool;
import org.springaicommunity.agent.tools.GrepTool;
import org.springaicommunity.agent.tools.ShellTools;
import org.springaicommunity.agent.tools.SmartWebFetchTool;
import org.springaicommunity.agent.tools.TodoWriteTool;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.session.SessionService;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * @author Christian Tzolov
 */

public class ClaudeSubagentType {

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private String braveApiKey = System.getenv("BRAVE_API_KEY");

		private Map<String, ChatClient.Builder> chatClientBuilderMap = new ConcurrentHashMap<>();

		private List<String> skillsDirectories = new ArrayList<>();

		private SessionService sessionService;

		private List<Advisor> sessionAdvisors;

		public Builder braveApiKey(String braveApiKey) {
			Assert.notNull(braveApiKey, "braveApiKey must not be null");
			this.braveApiKey = braveApiKey;
			return this;
		}

		public Builder chatClientBuilder(String modelId, ChatClient.Builder chatClientBuilder) {
			Assert.notNull(modelId, "modelId must not be null");
			Assert.notNull(chatClientBuilder, "chatClientBuilder must not be null");
			this.chatClientBuilderMap.put(modelId, chatClientBuilder);
			return this;
		}

		public Builder chatClientBuilders(Map<String, ChatClient.Builder> chatClientBuilderMap) {
			Assert.notNull(chatClientBuilderMap, "chatClientBuilderMap must not be null");
			this.chatClientBuilderMap.putAll(chatClientBuilderMap);
			return this;
		}

		// Skills directories
		public Builder skillsResources(List<Resource> skillsRootPaths) {
			for (Resource skillsRootPath : skillsRootPaths) {
				this.skillsResource(skillsRootPath);
			}
			return this;
		}

		public Builder skillsResource(Resource skillsRootPath) {
			try {
				String path = skillsRootPath.getFile().toPath().toAbsolutePath().toString();
				this.skillsDirectories(path);
			}
			catch (IOException ex) {
				throw new RuntimeException("Failed to load skills from directory: " + skillsRootPath, ex);
			}
			return this;
		}

		public Builder skillsDirectories(List<String> skillsDirectories) {
			Assert.notNull(skillsDirectories, "skillsDirectories must not be null");
			this.skillsDirectories.addAll(skillsDirectories);
			return this;
		}

		public Builder skillsDirectories(String skillsDirectory) {
			Assert.notNull(skillsDirectory, "skillsDirectory must not be null");
			this.skillsDirectories.add(skillsDirectory);
			return this;
		}

		/**
		 * Gives the Claude sub-agents memory: a task that comes with a session of its own
		 * keeps its transcript there and can be resumed. Use the service the
		 * {@code TaskTool} was given.
		 * @param sessionService where the sub-agents' sessions live
		 * @return this builder
		 */
		public Builder sessionService(SessionService sessionService) {
			Assert.notNull(sessionService, "sessionService must not be null");
			this.sessionService = sessionService;
			return this;
		}

		/**
		 * Gives the Claude sub-agents memory with advisors of your choice, in place of the
		 * ones {@link #sessionService(SessionService)} sets up
		 * ({@link ClaudeSubagentExecutor#defaultSessionAdvisors(SessionService)}): session
		 * advisors with compaction, for example, or advisors that also track the turn.
		 * They are added only to a task that runs on a session, so a sub-agent that runs
		 * without memory is not affected; do not put them on the sub-agents'
		 * {@code ChatClient.Builder}. Build them on the service the {@code TaskTool} was
		 * given, and with the session ID in the tool context
		 * ({@code sessionIdInToolContext(true)}): a sub-agent's own Task tool finds its
		 * session there.
		 * @param sessionAdvisors the advisors that keep a sub-agent's transcript in its
		 * session
		 * @return this builder
		 */
		public Builder sessionAdvisors(List<Advisor> sessionAdvisors) {
			Assert.notEmpty(sessionAdvisors, "sessionAdvisors must not be empty");
			Assert.noNullElements(sessionAdvisors, "sessionAdvisors must not contain null elements");
			this.sessionAdvisors = List.copyOf(sessionAdvisors);
			return this;
		}

		public SubagentType build() {

			Assert.notEmpty(this.chatClientBuilderMap, "At least one chatClientBuilder map not be empty");
			Assert.isTrue(this.chatClientBuilderMap.containsKey("default"),
					"chatClientBuilderMap must contain a 'default' builder for the SmartWebFetchTool");

			List<Advisor> advisors = this.sessionAdvisors;
			if (advisors == null && this.sessionService != null) {
				advisors = ClaudeSubagentExecutor.defaultSessionAdvisors(this.sessionService);
			}

			ClaudeSubagentExecutor executor = new ClaudeSubagentExecutor(this.chatClientBuilderMap,
					this.defaultClaudeSubagentTools(), this.skillsDirectories, advisors);

			return new SubagentType(new ClaudeSubagentResolver(), executor);
		}

		private List<ToolCallback> defaultClaudeSubagentTools() {

			// Use a dedicated "webfetch" builder for the WebFetch tool's internal
			// summarization calls when provided, so they can be configured (e.g. model,
			// logging advisor label) independently of the subagent's default builder.
			// Falls back to the "default" builder when no "webfetch" builder is registered.
			ChatClient.Builder webFetchChatClientBuilder = this.chatClientBuilderMap.getOrDefault("webfetch",
					this.chatClientBuilderMap.get("default"));

			List<ToolCallback> defaultCallbacks = new ArrayList<>();

			List<ToolCallback> commonTools = List.of(MethodToolCallbackProvider.builder()
				.toolObjects(TodoWriteTool.builder().build(), GrepTool.builder().build(), GlobTool.builder().build(),
						ShellTools.builder().build(), FileSystemTools.builder().build(),
						SmartWebFetchTool.builder(webFetchChatClientBuilder.clone().build()).build())
				.build()
				.getToolCallbacks());

			defaultCallbacks.addAll(commonTools);

			if (StringUtils.hasText(this.braveApiKey)) {
				defaultCallbacks.add(MethodToolCallbackProvider.builder()
					.toolObjects(BraveWebSearchTool.builder(braveApiKey).resultCount(15).build())
					.build()
					.getToolCallbacks()[0]);
			}

			return defaultCallbacks;
		}

	}

}
