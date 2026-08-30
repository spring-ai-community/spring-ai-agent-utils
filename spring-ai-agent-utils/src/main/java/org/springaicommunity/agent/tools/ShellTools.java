/*
* Copyright 2025 - 2026 the original author or authors.
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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springaicommunity.agent.common.exec.ExecBackend;
import org.springaicommunity.agent.common.exec.ExecHandle;
import org.springaicommunity.agent.common.exec.ExecResult;
import org.springaicommunity.agent.common.exec.ExecSpec;
import org.springaicommunity.agent.common.workspace.Workspace;
import org.springaicommunity.agent.exec.LocalExecBackend;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.util.Assert;

/**
 * Shell tools (Bash, BashOutput, KillShell) executing through a pluggable
 * {@link ExecBackend}. By default commands run on the host JVM
 * ({@link LocalExecBackend}); configure a different backend to execute inside a
 * sandbox/container, or use the {@code workingDirectory} builder option to confine local
 * execution to a workspace directory. Background shells are tracked per
 * {@code ShellTools} instance, so separate agents/sessions have separate shell
 * namespaces.
 *
 * @author Christian Tzolov
 */
public class ShellTools {

	private static final int MAX_OUTPUT_LENGTH = 30_000;

	// Synchronous run ids live in their own "shell_run_" namespace so they can never
	// collide with backend-minted background handle ids ("shell_<n>").
	private static final java.util.concurrent.atomic.AtomicLong SYNC_RUN_SEQUENCE = new java.util.concurrent.atomic.AtomicLong(
			System.currentTimeMillis());

	private final ExecBackend execBackend;

	// Background shells owned by this instance (not process-global).
	private final Map<String, ExecHandle> backgroundShells = new ConcurrentHashMap<>();

	protected ShellTools(ExecBackend execBackend) {
		Assert.notNull(execBackend, "execBackend must not be null");
		this.execBackend = execBackend;
	}

	/**
	 * @deprecated use {@link #builder()} — allows configuring the execution backend and
	 * working directory.
	 */
	@Deprecated
	public ShellTools() {
		this(LocalExecBackend.builder().build());
	}

	//
	// Shell commands
	//

	// @formatter:off
	@Tool(name = "Bash", description = """
		Execute a bash command for terminal operations like npm, docker, make, mvn, python.
		DO NOT use for file operations — use specialized tools instead:
		- File search: Use Glob (NOT find or ls)
		- Content search: Use Grep (NOT grep or rg)
		- Read files: Use Read (NOT cat/head/tail)
		- Edit files: Use Edit (NOT sed/awk)
		- Write files: Use Write (NOT echo >/cat <<EOF)

		Usage notes:
		- The command argument is required.
		- Optional timeout in milliseconds (max 600000ms / 10 minutes). Default: 120000ms (2 minutes).
		- Output truncated at 30000 characters.
		- Use run_in_background for long-running commands.
		- Quote file paths with spaces in double quotes.
		- Chain dependent commands with &&. Use ; if earlier failures are acceptable.
		- Prefer absolute paths over cd.

		Important notes:
		- NEVER run additional commands to read or explore code, besides git bash commands
		- NEVER use the TodoWrite or Task tools
		- DO NOT push to the remote repository unless the user explicitly asks you to do so
		- IMPORTANT: Never use git commands with the -i flag (like git rebase -i or git add -i) since they require interactive input which is not supported.
		- If there are no changes to commit (i.e., no untracked files and no modifications), do not create an empty commit
		- In order to ensure good formatting, ALWAYS pass the commit message via a HEREDOC, a la this example:
		<example>
		git commit -m "$(cat <<'EOF'
		Commit message here.

		🤖 Generated with [Claude Code](https://claude.com/claude-code)

		Co-Authored-By: Claude <noreply@anthropic.com>
		EOF
		)"
		</example>

		# Creating pull requests
		Use the gh command via the Bash tool for ALL GitHub-related tasks including working with issues, pull requests, checks, and releases. If given a Github URL use the gh command to get the information needed.

		IMPORTANT: When the user asks you to create a pull request, follow these steps carefully:

		1. You can call multiple tools in a single response. When multiple independent pieces of information are requested and all commands are likely to succeed, run multiple tool calls in parallel for optimal performance. run the following bash commands in parallel using the Bash tool, in order to understand the current state of the branch since it diverged from the main branch:
		- Run a git status command to see all untracked files
		- Run a git diff command to see both staged and unstaged changes that will be committed
		- Check if the current branch tracks a remote branch and is up to date with the remote, so you know if you need to push to the remote
		- Run a git log command and `git diff [base-branch]...HEAD` to understand the full commit history for the current branch (from the time it diverged from the base branch)
		2. Analyze all changes that will be included in the pull request, making sure to look at all relevant commits (NOT just the latest commit, but ALL commits that will be included in the pull request!!!), and draft a pull request summary
		3. You can call multiple tools in a single response. When multiple independent pieces of information are requested and all commands are likely to succeed, run multiple tool calls in parallel for optimal performance. run the following commands in parallel:
		- Create new branch if needed
		- Push to remote with -u flag if needed
		- Create PR using gh pr create with the format below. Use a HEREDOC to pass the body to ensure correct formatting.
		<example>
		gh pr create --title "the pr title" --body "$(cat <<'EOF'

		## Summary
		<1-3 bullet points>

		## Test plan
		[Bulleted markdown checklist of TODOs for testing the pull request...]

		🤖 Generated with [Claude Code](https://claude.com/claude-code)
		EOF
		)"
		</example>

		Important:
		- DO NOT use the TodoWrite or Task tools
		- Return the PR URL when you're done, so the user can see it

		# Other common operations
		- View comments on a Github PR: gh api repos/foo/bar/pulls/123/comments
		""")
	public String bash(
		@ToolParam(description = "The command to execute") String command,
		@ToolParam(description = "Optional timeout in milliseconds (max 600000)", required = false) Long timeout,
		@ToolParam(description = "Clear, concise description of what this command does in 5-10 words, in active voice. Examples:\nInput: ls\nOutput: List files in current directory\n\nInput: git status\nOutput: Show working tree status\n\nInput: npm install\nOutput: Install package dependencies\n\nInput: mkdir foo\nOutput: Create directory 'foo'", required = false) String description,
		@ToolParam(description = "Set to true to run this command in the background. Use BashOutput to read the output later.", required = false) Boolean runInBackground) { // @formatter:on

		if (command == null || command.isBlank()) {
			return "Error: command must not be blank";
		}
		// Timeout policy (<=0 -> default, clamp to max) is owned by ExecSpec; read the
		// effective value back so diagnostics never report a value we didn't honor.
		ExecSpec spec = new ExecSpec(command, timeout != null ? timeout : ExecSpec.DEFAULT_TIMEOUT_MILLIS, Map.of());

		if (Boolean.TRUE.equals(runInBackground)) {
			ExecHandle handle;
			try {
				handle = this.execBackend.start(spec);
			}
			catch (RuntimeException e) {
				// Backends throw with the raw reason; presentation prefix is added once
				// here.
				return "Error executing command: " + e.getMessage();
			}
			this.backgroundShells.put(handle.id(), handle);
			return String.format(
					"bash_id: %s\n\nBackground shell started with ID: %s\nUse BashOutput tool with bash_id='%s' to retrieve output.",
					handle.id(), handle.id(), handle.id());
		}

		String shellId = "shell_run_" + SYNC_RUN_SEQUENCE.incrementAndGet();
		ExecResult execResult = this.execBackend.run(spec);

		switch (execResult.status()) {
			case TIMED_OUT:
				return String.format("bash_id: %s\n\nCommand timed out after %dms", shellId, spec.timeoutMillis());
			case LAUNCH_FAILED:
				return "Error executing command: " + execResult.stderr();
			case INTERRUPTED:
				return "Command execution interrupted: " + execResult.stderr();
			case COMPLETED:
				break;
		}

		StringBuilder result = new StringBuilder();
		result.append("bash_id: ").append(shellId).append("\n\n");

		if (!execResult.stdout().isEmpty()) {
			result.append(execResult.stdout());
		}
		if (!execResult.stderr().isEmpty()) {
			if (result.length() > result.indexOf("\n\n") + 2) {
				result.append("\n");
			}
			result.append("STDERR:\n").append(execResult.stderr());
		}
		if (execResult.exitCode() != 0) {
			if (result.length() > result.indexOf("\n\n") + 2) {
				result.append("\n");
			}
			result.append("Exit code: ").append(execResult.exitCode());
		}

		// Truncate if too long, keeping the bash_id header
		String output = result.toString();
		if (output.length() > MAX_OUTPUT_LENGTH) {
			String header = output.substring(0, output.indexOf("\n\n") + 2);
			String content = output.substring(output.indexOf("\n\n") + 2);
			output = header + content.substring(0, Math.min(content.length(), MAX_OUTPUT_LENGTH - header.length()))
					+ "\n... (output truncated)";
		}
		return output;
	}

	// @formatter:off
	@Tool(name = "BashOutput", description = """
		- Retrieves output from a running or completed background bash shell
		- Takes a shell_id parameter identifying the shell
		- Always returns only new output since the last check
		- Returns stdout and stderr output along with shell status
		- Supports optional regex filtering to show only lines matching a pattern
		- Use this tool when you need to monitor or check the output of a long-running shell
		- Shell IDs can be found using the /bashes command
		""")
	public String bashOutput(
		@ToolParam(description = "The ID of the background shell to retrieve output from") String bash_id,
		@ToolParam(description = "Optional regular expression to filter the output lines. Only lines matching this regex will be included in the result. Any lines that do not match will no longer be available to read.", required = false) String filter) { // @formatter:on

		ExecHandle handle = this.backgroundShells.get(bash_id);
		if (handle == null) {
			return "Error: No background shell found with ID: " + bash_id;
		}

		String newOutput = handle.newOutput(filter);

		StringBuilder result = new StringBuilder();
		result.append("Shell ID: ").append(bash_id).append("\n");
		result.append("Status: ").append(handle.isAlive() ? "Running" : "Completed").append("\n");

		if (!handle.isAlive()) {
			try {
				result.append("Exit code: ").append(handle.exitCode()).append("\n");
			}
			catch (IllegalThreadStateException e) {
				// Process not yet terminated
			}
		}

		if (!newOutput.isEmpty()) {
			result.append("\nNew output:\n").append(newOutput);
		}
		else {
			result.append("\nNo new output since last check.");
		}
		return result.toString();
	}

	// @formatter:off
	@Tool(name = "KillShell", description = """
		- Kills a running background bash shell by its ID
		- Takes a shell_id parameter identifying the shell to kill
		- Returns a success or failure status
		- Use this tool when you need to terminate a long-running shell
		- Shell IDs can be found using the /bashes command
		""")
	public String killShell(
		@ToolParam(description = "The ID of the background shell to kill") String bash_id) { // @formatter:on

		ExecHandle handle = this.backgroundShells.get(bash_id);
		if (handle == null) {
			return "Error: No background shell found with ID: " + bash_id;
		}

		if (!handle.isAlive()) {
			this.backgroundShells.remove(bash_id);
			return "Shell " + bash_id + " was already terminated. Removed from active shells.";
		}

		handle.kill();

		// Wait a bit to confirm termination
		try {
			Thread.sleep(500);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}

		this.backgroundShells.remove(bash_id);
		return "Successfully killed shell: " + bash_id;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private ExecBackend execBackend;

		private Path workingDirectory;

		private Builder() {
		}

		/**
		 * Execution backend for all commands. Default: {@link LocalExecBackend} on the
		 * host JVM.
		 */
		public Builder execBackend(ExecBackend execBackend) {
			this.execBackend = execBackend;
			return this;
		}

		/**
		 * Convenience: run commands locally with this working directory. Mutually
		 * exclusive with {@link #execBackend(ExecBackend)} — a custom backend owns its
		 * own working directory.
		 */
		public Builder workingDirectory(Path workingDirectory) {
			this.workingDirectory = workingDirectory;
			return this;
		}

		public Builder workingDirectory(String workingDirectory) {
			return workingDirectory(workingDirectory != null ? Paths.get(workingDirectory) : null);
		}

		/**
		 * Convenience: run commands locally rooted at the given workspace — shorthand for
		 * {@code workingDirectory(workspace.root())}. Mutually exclusive with
		 * {@link #execBackend(ExecBackend)}, same as {@code workingDirectory}. Unlike the
		 * {@code workspace} option on the file/search tools, this does NOT restrict what
		 * commands can access — a shell can leave its working directory. Use a sandboxed
		 * {@link ExecBackend} for actual isolation.
		 * @param workspace the workspace whose root becomes the working directory
		 * @return this builder
		 */
		public Builder workspace(Workspace workspace) {
			Assert.notNull(workspace, "workspace must not be null");
			return workingDirectory(workspace.root());
		}

		public ShellTools build() {
			Assert.state(this.execBackend == null || this.workingDirectory == null,
					"Set either execBackend or workingDirectory, not both — a custom backend owns its working directory");
			ExecBackend backend = this.execBackend;
			if (backend == null) {
				backend = LocalExecBackend.builder().workingDirectory(this.workingDirectory).build();
			}
			return new ShellTools(backend);
		}

	}

}
