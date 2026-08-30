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
package org.springaicommunity.agent.utils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import org.springaicommunity.agent.common.exec.ExecBackend;
import org.springaicommunity.agent.common.exec.ExecResult;
import org.springaicommunity.agent.common.exec.ExecSpec;
import org.springaicommunity.agent.common.workspace.Workspace;
import org.springaicommunity.agent.exec.LocalExecBackend;

/**
 * Renders environment context blocks (working directory, git status) for inclusion in
 * agent system prompts. The no-argument methods describe the host JVM's current
 * directory; the {@link Workspace} and {@link ExecBackend} overloads describe the
 * environment the agent's tools actually operate in, so a sandboxed agent is not shown
 * host paths or host git state it cannot reach.
 *
 * @author Christian Tzolov
 */
public class AgentEnvironment {

	public static final String ENVIRONMENT_INFO_KEY = "ENVIRONMENT_INFO";

	public static final String GIT_STATUS_KEY = "GIT_STATUS";

	public static final String AGENT_MODEL_KEY = "AGENT_MODEL";

	public static final String AGENT_MODEL_KNOWLEDGE_CUTOFF_KEY = "AGENT_MODEL_KNOWLEDGE_CUTOFF";

	private static final long GIT_COMMAND_TIMEOUT_MILLIS = 30_000;

	/** Force untranslated git output so the parsing below is locale-independent. */
	private static final Map<String, String> GIT_ENV = Map.of("LC_ALL", "C", "LANG", "C");

	/** Environment info for the host JVM's current working directory. */
	public static String info() {
		return info(hostWorkspace());
	}

	/**
	 * Environment info for the given workspace: the working directory line shows
	 * {@link Workspace#display(Path) workspace.display(root)} — the path as the model
	 * should see it — and the git-repo check runs against the workspace root. Platform,
	 * OS version and date describe the JVM host; override the rendered block if the
	 * execution environment differs.
	 * @param workspace the workspace the agent's tools operate in
	 * @return the rendered environment info block
	 */
	public static String info(Workspace workspace) {

		Path root = workspace.root();
		String workingDirectory = workspace.display(root);
		boolean isGitRepo = Files.exists(root.resolve(".git"));
		String platform = System.getProperty("os.name").toLowerCase();
		String osVersion = System.getProperty("os.name") + " " + System.getProperty("os.version");
		String todayDate = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);

		StringBuilder sb = new StringBuilder();
		sb.append("Working directory: ").append(workingDirectory).append("\n");
		sb.append("Is directory a git repo: ").append(isGitRepo ? "Yes" : "No").append("\n");
		sb.append("Platform: ").append(platform).append("\n");
		sb.append("OS Version: ").append(osVersion).append("\n");
		sb.append("Today's date: ").append(todayDate).append("\n");

		return sb.toString();
	}

	/** Git status snapshot of the host JVM's current working directory. */
	public static String gitStatus() {
		return gitStatus(hostWorkspace());
	}

	/**
	 * Git status snapshot of the given workspace, executed locally at the workspace root.
	 * @param workspace the workspace whose root to inspect
	 * @return the rendered git status block, or an empty string when git is unavailable
	 * or the root is not a git work tree
	 */
	public static String gitStatus(Workspace workspace) {
		return gitStatus(LocalExecBackend.builder().workingDirectory(workspace.root()).build());
	}

	/**
	 * Git status snapshot produced by running git through the given execution backend —
	 * inside a sandbox, the reported status is the sandbox's view of the repository. The
	 * backend's own working directory determines which repository is inspected.
	 * @param execBackend the backend to run git commands on
	 * @return the rendered git status block, or an empty string when git is unavailable
	 * or the working directory is not a git work tree
	 */
	public static String gitStatus(ExecBackend execBackend) {

		// Check if git is available
		if (!isGitAvailable(execBackend)) {
			System.out.println("Git is not available or not in PATH.\n");
			return "";
		}

		// Check if we're in a git repository
		String gitCheck = runGit(execBackend, "git rev-parse --is-inside-work-tree");
		if (!"true".equals(gitCheck)) {
			System.out.println("Not inside a git repository.\n");
			return "";
		}

		StringBuilder sb = new StringBuilder();
		sb.append("gitStatus: This is the git status at the start of the conversation. ");
		sb.append("Note that this status is a snapshot in time, and will not update during the conversation.\n");

		// Get current branch
		String currentBranch = runGit(execBackend, "git rev-parse --abbrev-ref HEAD");
		sb.append("Current branch: ").append(currentBranch).append("\n\n");

		// Get main/master branch (for PRs)
		String mainBranch = getMainBranch(execBackend);
		sb.append("Main branch (you will usually use this for PRs): ").append(mainBranch).append("\n\n");

		// Get git status
		String status = runGit(execBackend, "git status --short");
		sb.append("Status:\n").append(status.isEmpty() ? "Working tree clean\n\n" : status).append("\n\n");

		// Get recent commits
		String recentCommits = runGit(execBackend, "git log --oneline -n 5");
		sb.append("Recent commits:\n").append(recentCommits);

		return sb.toString();
	}

	private static Workspace hostWorkspace() {
		return Workspace.local(Paths.get(System.getProperty("user.dir")));
	}

	private static boolean isGitAvailable(ExecBackend execBackend) {
		String result = runGit(execBackend, "git --version");
		return result.contains("git version");
	}

	private static String getMainBranch(ExecBackend execBackend) {
		// Try to detect the main branch name
		String[] possibleMains = { "main", "master" };
		for (String branch : possibleMains) {
			String result = runGit(execBackend, "git rev-parse --verify --quiet " + branch);
			if (!result.isEmpty()) {
				return branch;
			}
		}
		// Try to get from remote
		String remoteBranch = runGit(execBackend, "git symbolic-ref refs/remotes/origin/HEAD --short");
		if (!remoteBranch.isEmpty()) {
			return remoteBranch.replace("origin/", "");
		}
		return "main";
	}

	/**
	 * Runs a git command through the execution backend and returns its trimmed stdout, or
	 * an empty string when the command did not complete or exited non-zero.
	 */
	private static String runGit(ExecBackend execBackend, String command) {
		try {
			ExecResult result = execBackend.run(new ExecSpec(command, GIT_COMMAND_TIMEOUT_MILLIS, GIT_ENV));
			if (result.status() != ExecResult.Status.COMPLETED || result.exitCode() != 0) {
				return "";
			}
			return result.stdout().trim();
		}
		catch (Exception e) {
			return "";
		}
	}

}
