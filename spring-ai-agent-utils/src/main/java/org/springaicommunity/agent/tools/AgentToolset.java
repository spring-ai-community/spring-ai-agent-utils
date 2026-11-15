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
package org.springaicommunity.agent.tools;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springaicommunity.agent.common.exec.ExecBackend;
import org.springaicommunity.agent.common.workspace.Workspace;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.util.Assert;

/**
 * Builds the default agent tool callbacks for an {@link ExecBackend} and
 * {@link Workspace} pair: shell ({@code Bash}, {@code BashOutput}, {@code KillShell}),
 * file ({@code Read}, {@code Write}, {@code Edit}) and search ({@code Grep},
 * {@code Glob}, {@code ListDirectory}).
 *
 * <p>
 * Omitting the backend and the workspace keeps each tool's own default (local execution,
 * no directory confinement). When both are set, shell commands go through the backend and
 * the file/search tools are confined to the workspace — the sandbox recipe. Tools that
 * need their own configuration (skills, web search, {@code TodoWrite},
 * {@code AskUserQuestion}) are not part of the default set; add them with
 * {@link Builder#with(Object)}.
 *
 * <pre>{@code
 * List<ToolCallback> tools = AgentToolset.builder()
 *     .execBackend(backend)
 *     .workspace(workspace)
 *     .listener(eventLogListener)
 *     .build();
 * }</pre>
 *
 * {@link WorkspaceAccess} selects a permission tier. {@code READ} publishes only the
 * read-only callbacks (including {@code Read}, and not {@code Write}/{@code Edit}). That
 * is assembly filtering: {@link FileSystemTools} itself is unchanged.
 *
 * <p>
 * {@link #promptVariables(Object...)} returns the boolean flags
 * {@code MAIN_AGENT_SYSTEM_PROMPT_V2.md} uses in its StringTemplate {@code if} blocks,
 * derived from the callbacks that were actually assembled. Pass them into the system
 * prompt together
 * with the {@code AgentEnvironment} placeholders. The default StringTemplate renderer
 * rejects the prompt when a flag is missing, so a {@code READ} session cannot keep the
 * {@code WRITE}/{@code EXECUTE} instructions by forgetting the substitution.
 *
 * @see WorkspaceAccess
 */
public final class AgentToolset {

	/**
	 * Prompt flag. True when {@code Write} or {@code Edit} is assembled ({@code WRITE}
	 * and {@code EXECUTE}).
	 */
	public static final String WORKSPACE_WRITE_AVAILABLE = "WORKSPACE_WRITE_AVAILABLE";

	/**
	 * Prompt flag. True when {@code Bash} is assembled ({@code EXECUTE}).
	 */
	public static final String WORKSPACE_EXECUTE_AVAILABLE = "WORKSPACE_EXECUTE_AVAILABLE";

	/**
	 * Prompt flag. True when {@code Read}, {@code Write}, {@code Edit}, and {@code Bash}
	 * are all assembled. This is the default {@code EXECUTE} set unless one of those
	 * tools was removed.
	 */
	public static final String WORKSPACE_FULL_TOOLS_AVAILABLE = "WORKSPACE_FULL_TOOLS_AVAILABLE";

	/** Prompt flag. True when {@code TodoWrite} is assembled. */
	public static final String TODO_WRITE_AVAILABLE = "TODO_WRITE_AVAILABLE";

	/** Prompt flag. True when {@code WebFetch} is assembled. */
	public static final String WEB_FETCH_AVAILABLE = "WEB_FETCH_AVAILABLE";

	/** Prompt flag. True when {@code Task} is assembled. */
	public static final String TASK_AVAILABLE = "TASK_AVAILABLE";

	/**
	 * Prompt flag. True when a {@code Task} callback's description registers an
	 * {@code Explore} subagent ({@code -Explore:}). The file-search guidance names that
	 * subagent, so a Task tool without it must not receive the section.
	 */
	public static final String TASK_FILE_SEARCH_AVAILABLE = "TASK_FILE_SEARCH_AVAILABLE";

	private static final List<String> PROMPT_VARIABLE_KEYS = List.of(WORKSPACE_WRITE_AVAILABLE,
			WORKSPACE_EXECUTE_AVAILABLE, WORKSPACE_FULL_TOOLS_AVAILABLE, TODO_WRITE_AVAILABLE, WEB_FETCH_AVAILABLE,
			TASK_AVAILABLE, TASK_FILE_SEARCH_AVAILABLE);

	/** Registration line produced by {@code SubagentDefinition.toSubagentRegistrations()}. */
	private static final String EXPLORE_SUBAGENT_REGISTRATION = "-Explore:";

	private AgentToolset() {
	}

	/**
	 * Boolean template variables for {@code MAIN_AGENT_SYSTEM_PROMPT_V2.md}.
	 * <p>
	 * Every flag key is present. A flag is {@code true} only when the matching tool is
	 * among {@code tools}. Each element is an {@code @Tool} object, a
	 * {@link ToolCallback}, a {@link ToolCallbackProvider}, or a collection of those —
	 * the same values {@link Builder#with(Object)} accepts. Workspace flags then match
	 * {@link WorkspaceAccess} for callbacks from {@link Builder#build()}:
	 * <ul>
	 * <li>{@link #WORKSPACE_WRITE_AVAILABLE} — {@code WRITE} and {@code EXECUTE}</li>
	 * <li>{@link #WORKSPACE_EXECUTE_AVAILABLE} — {@code EXECUTE}</li>
	 * <li>{@link #WORKSPACE_FULL_TOOLS_AVAILABLE} — {@code EXECUTE}, unless {@code Read},
	 * {@code Write}, {@code Edit}, or {@code Bash} was removed</li>
	 * </ul>
	 * {@link #TODO_WRITE_AVAILABLE}, {@link #WEB_FETCH_AVAILABLE}, and
	 * {@link #TASK_AVAILABLE} follow optional tools. {@link #TASK_FILE_SEARCH_AVAILABLE}
	 * additionally requires the Task description to register {@code Explore}.
	 * @param tools the tools the agent will actually be given
	 * @return an immutable map of every prompt flag
	 */
	public static Map<String, Object> promptVariables(Object... tools) {
		Assert.notNull(tools, "tools must not be null");
		List<ToolCallback> callbacks = new ArrayList<>();
		for (Object tool : tools) {
			collect(tool, callbacks);
		}
		return flagsFor(callbacks);
	}

	public static Builder builder() {
		return new Builder();
	}

	/**
	 * How much of the default toolset is published.
	 */
	public enum WorkspaceAccess {

		/** No built-in shell, file, or search tools. */
		NONE,

		/** Read-only workspace tools: {@code Read}, {@code Grep}, {@code Glob}, {@code ListDirectory}. */
		READ,

		/** {@link #READ} plus file mutation: {@code Write} and {@code Edit}. */
		WRITE,

		/** {@link #WRITE} plus shell execution: {@code Bash}, {@code BashOutput}, {@code KillShell}. */
		EXECUTE

	}

	public static final class Builder {

		private static final List<String> SHELL_TOOLS = List.of("Bash", "BashOutput", "KillShell");

		private static final List<String> READ_FILE_TOOLS = List.of("Read");

		private static final List<String> WRITE_FILE_TOOLS = List.of("Write", "Edit");

		private ExecBackend execBackend;

		private Workspace workspace;

		private ToolCallListener listener;

		private WorkspaceAccess workspaceAccess = WorkspaceAccess.EXECUTE;

		private final List<Object> additionalTools = new ArrayList<>();

		private final Set<String> excludedToolNames = new LinkedHashSet<>();

		private Builder() {
		}

		/**
		 * Backend for {@code Bash}, {@code BashOutput} and {@code KillShell}. Used only
		 * at {@link WorkspaceAccess#EXECUTE}. When unset, shell commands run on the host
		 * (rooted at {@link #workspace(Workspace)} when that is set).
		 */
		public Builder execBackend(ExecBackend execBackend) {
			Assert.notNull(execBackend, "execBackend must not be null");
			this.execBackend = execBackend;
			return this;
		}

		/**
		 * Workspace for the file and search tools (working directory and confinement).
		 * At {@link WorkspaceAccess#EXECUTE}, also the local shell working directory
		 * when no {@link #execBackend(ExecBackend)} is set. A custom backend keeps its
		 * own working directory.
		 */
		public Builder workspace(Workspace workspace) {
			Assert.notNull(workspace, "workspace must not be null");
			this.workspace = workspace;
			return this;
		}

		/**
		 * Observes every callback this builder returns. Same effect as
		 * {@link ToolCallListeners#wrapAll(List, ToolCallListener)} on the finished list.
		 */
		public Builder listener(ToolCallListener listener) {
			Assert.notNull(listener, "listener must not be null");
			this.listener = listener;
			return this;
		}

		/**
		 * Permission tier. Default: {@link WorkspaceAccess#EXECUTE}.
		 */
		public Builder workspaceAccess(WorkspaceAccess workspaceAccess) {
			Assert.notNull(workspaceAccess, "workspaceAccess must not be null");
			this.workspaceAccess = workspaceAccess;
			return this;
		}

		/**
		 * Appends a tool that is not part of the default set. An annotated tool object
		 * is expanded with {@link ToolCallbacks#from(Object...)}; a {@link ToolCallback}
		 * or {@link ToolCallbackProvider} is added as-is. Extras are appended after the
		 * built-in callbacks, then {@link #without(String)} is applied.
		 */
		public Builder with(Object tool) {
			Assert.notNull(tool, "tool must not be null");
			this.additionalTools.add(tool);
			return this;
		}

		/**
		 * Drops a callback by its tool name (for example {@code "Grep"} or
		 * {@code "Bash"}). The name must be present on the list that would otherwise be
		 * returned, including tools added with {@link #with(Object)}.
		 */
		public Builder without(String toolName) {
			Assert.hasText(toolName, "toolName must not be empty");
			this.excludedToolNames.add(toolName);
			return this;
		}

		/**
		 * Prompt flags for the callbacks {@link #build()} would return. Same map as
		 * {@link AgentToolset#promptVariables(Object...)} applied to that list, so
		 * {@link #without(String)} and {@link #with(Object)} are reflected.
		 */
		public Map<String, Object> promptVariables() {
			return AgentToolset.promptVariables(build());
		}

		/**
		 * @return an immutable list of callbacks, listener-wrapped when a listener was
		 * set
		 */
		public List<ToolCallback> build() {
			List<ToolCallback> callbacks = new ArrayList<>();
			if (this.workspaceAccess != WorkspaceAccess.NONE) {
				if (this.workspaceAccess == WorkspaceAccess.EXECUTE) {
					callbacks.addAll(shellCallbacks());
				}
				callbacks.addAll(fileSystemCallbacks());
				callbacks.addAll(searchCallbacks());
			}
			for (Object extra : this.additionalTools) {
				callbacks.addAll(AgentToolset.callbacksFrom(extra));
			}
			if (!this.excludedToolNames.isEmpty()) {
				applyExclusions(callbacks);
			}
			List<ToolCallback> built = List.copyOf(callbacks);
			if (this.listener != null) {
				return ToolCallListeners.wrapAll(built, this.listener);
			}
			return built;
		}

		private List<ToolCallback> shellCallbacks() {
			ShellTools.Builder shell = ShellTools.builder();
			if (this.execBackend != null) {
				shell.execBackend(this.execBackend);
			}
			else if (this.workspace != null) {
				shell.workspace(this.workspace);
			}
			return select(shell.build(), SHELL_TOOLS);
		}

		private List<ToolCallback> fileSystemCallbacks() {
			FileSystemTools.Builder files = FileSystemTools.builder();
			if (this.workspace != null) {
				files.workspace(this.workspace);
			}
			List<String> names = new ArrayList<>(READ_FILE_TOOLS);
			if (this.workspaceAccess == WorkspaceAccess.WRITE || this.workspaceAccess == WorkspaceAccess.EXECUTE) {
				names.addAll(WRITE_FILE_TOOLS);
			}
			return select(files.build(), names);
		}

		private List<ToolCallback> searchCallbacks() {
			GrepTool.Builder grep = GrepTool.builder();
			GlobTool.Builder glob = GlobTool.builder();
			ListDirectoryTool.Builder listDirectory = ListDirectoryTool.builder();
			if (this.workspace != null) {
				grep.workspace(this.workspace);
				glob.workspace(this.workspace);
				listDirectory.workspace(this.workspace);
			}
			List<ToolCallback> callbacks = new ArrayList<>();
			callbacks.addAll(select(grep.build(), List.of("Grep")));
			callbacks.addAll(select(glob.build(), List.of("Glob")));
			callbacks.addAll(select(listDirectory.build(), List.of("ListDirectory")));
			return callbacks;
		}

		private void applyExclusions(List<ToolCallback> callbacks) {
			List<String> available = callbacks.stream().map(callback -> callback.getToolDefinition().name()).toList();
			List<String> missing = new ArrayList<>();
			for (String name : this.excludedToolNames) {
				if (!available.contains(name)) {
					missing.add(name);
				}
			}
			if (!missing.isEmpty()) {
				throw new IllegalArgumentException("Unknown tool name(s) for without(...): " + missing
						+ ". Available tools: " + available);
			}
			callbacks.removeIf(callback -> this.excludedToolNames.contains(callback.getToolDefinition().name()));
		}

		private static List<ToolCallback> select(Object tool, List<String> namesInOrder) {
			Map<String, ToolCallback> byName = new LinkedHashMap<>();
			for (ToolCallback callback : ToolCallbacks.from(tool)) {
				byName.put(callback.getToolDefinition().name(), callback);
			}
			List<ToolCallback> selected = new ArrayList<>(namesInOrder.size());
			for (String name : namesInOrder) {
				ToolCallback callback = byName.get(name);
				Assert.notNull(callback, "Expected tool '" + name + "' on " + tool.getClass().getName() + " but found "
						+ byName.keySet());
				selected.add(callback);
			}
			return selected;
		}

	}

	private static List<ToolCallback> callbacksFrom(Object tool) {
		if (tool instanceof ToolCallback callback) {
			return List.of(callback);
		}
		if (tool instanceof ToolCallbackProvider provider) {
			ToolCallback[] callbacks = provider.getToolCallbacks();
			Assert.notNull(callbacks, "ToolCallbackProvider returned null");
			return Arrays.asList(callbacks);
		}
		return Arrays.asList(ToolCallbacks.from(tool));
	}

	private static void collect(Object tool, List<ToolCallback> callbacks) {
		Assert.notNull(tool, "tool must not be null");
		if (tool instanceof Collection<?> collection) {
			for (Object item : collection) {
				Assert.notNull(item, "tool must not be null");
				callbacks.addAll(callbacksFrom(item));
			}
			return;
		}
		callbacks.addAll(callbacksFrom(tool));
	}

	private static Map<String, Object> flagsFor(List<ToolCallback> callbacks) {
		Set<String> names = new LinkedHashSet<>();
		boolean explore = false;
		for (ToolCallback callback : callbacks) {
			ToolDefinition definition = callback.getToolDefinition();
			String name = definition.name();
			names.add(name);
			if ("Task".equals(name)) {
				String description = definition.description();
				if (description != null && description.contains(EXPLORE_SUBAGENT_REGISTRATION)) {
					explore = true;
				}
			}
		}
		boolean read = names.contains("Read");
		boolean write = names.contains("Write");
		boolean edit = names.contains("Edit");
		boolean bash = names.contains("Bash");

		Map<String, Object> flags = new LinkedHashMap<>();
		flags.put(WORKSPACE_WRITE_AVAILABLE, write || edit);
		flags.put(WORKSPACE_EXECUTE_AVAILABLE, bash);
		flags.put(WORKSPACE_FULL_TOOLS_AVAILABLE, read && write && edit && bash);
		flags.put(TODO_WRITE_AVAILABLE, names.contains("TodoWrite"));
		flags.put(WEB_FETCH_AVAILABLE, names.contains("WebFetch"));
		flags.put(TASK_AVAILABLE, names.contains("Task"));
		flags.put(TASK_FILE_SEARCH_AVAILABLE, explore);
		Assert.isTrue(flags.keySet().equals(new LinkedHashSet<>(PROMPT_VARIABLE_KEYS)),
				"prompt variable keys must all be set");
		return Map.copyOf(flags);
	}

}
