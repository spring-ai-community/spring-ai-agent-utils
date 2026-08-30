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
package org.springaicommunity.agent.utils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springaicommunity.agent.common.exec.ExecBackend;
import org.springaicommunity.agent.common.exec.ExecHandle;
import org.springaicommunity.agent.common.exec.ExecResult;
import org.springaicommunity.agent.common.exec.ExecSpec;
import org.springaicommunity.agent.common.workspace.Workspace;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The workspace/backend overloads of {@link AgentEnvironment}: environment info shows the
 * workspace's display path (not the raw host path), and git status runs through the
 * {@link ExecBackend} SPI so a sandbox reports its own view of the repository.
 *
 * @author Christian Tzolov
 */
class AgentEnvironmentWorkspaceTest {

	@TempDir
	Path root;

	@Test
	void infoDescribesWorkspaceNotHostCurrentDirectory() throws Exception {
		Files.createDirectory(this.root.resolve(".git"));
		Workspace workspace = Workspace.local(this.root);

		String info = AgentEnvironment.info(workspace);

		assertThat(info).contains("Working directory: " + this.root);
		assertThat(info).contains("Is directory a git repo: Yes");
		assertThat(info).doesNotContain("Working directory: " + System.getProperty("user.dir") + "\n");
	}

	@Test
	void infoUsesWorkspaceDisplayMapping() {
		Workspace sandboxView = new Workspace() {

			@Override
			public Path root() {
				return AgentEnvironmentWorkspaceTest.this.root;
			}

			@Override
			public String display(String hostPath) {
				return hostPath.replace(AgentEnvironmentWorkspaceTest.this.root.toString(), "/workspace");
			}

		};

		String info = AgentEnvironment.info(sandboxView);

		assertThat(info).contains("Working directory: /workspace");
		assertThat(info).doesNotContain(this.root.toString());
	}

	@Test
	void gitStatusRunsThroughExecBackend() {
		RecordingBackend backend = new RecordingBackend();

		String status = AgentEnvironment.gitStatus(backend);

		assertThat(backend.commands).contains("git --version", "git rev-parse --is-inside-work-tree",
				"git rev-parse --abbrev-ref HEAD", "git status --short");
		assertThat(status).contains("Current branch: feature-x");
		assertThat(status).contains("Working tree clean");
		assertThat(status).contains("Recent commits:\nabc1234 initial commit");
	}

	@Test
	void gitStatusReturnsEmptyWhenBackendHasNoGit() {
		ExecBackend noGit = new ExecBackend() {

			@Override
			public ExecResult run(ExecSpec spec) {
				return ExecResult.completed(127, "", "git: command not found");
			}

			@Override
			public ExecHandle start(ExecSpec spec) {
				throw new UnsupportedOperationException();
			}

		};

		assertThat(AgentEnvironment.gitStatus(noGit)).isEmpty();
	}

	@Test
	void localGitStatusOfNonRepoWorkspaceIsEmpty() {
		assertThat(AgentEnvironment.gitStatus(Workspace.local(this.root))).isEmpty();
	}

	/** Scripted backend standing in for a sandbox: canned git replies, recorded calls. */
	static final class RecordingBackend implements ExecBackend {

		final List<String> commands = new ArrayList<>();

		@Override
		public ExecResult run(ExecSpec spec) {
			this.commands.add(spec.command());
			return switch (spec.command()) {
				case "git --version" -> ExecResult.completed(0, "git version 2.44.0", "");
				case "git rev-parse --is-inside-work-tree" -> ExecResult.completed(0, "true", "");
				case "git rev-parse --abbrev-ref HEAD" -> ExecResult.completed(0, "feature-x", "");
				case "git rev-parse --verify --quiet main" -> ExecResult.completed(0, "abc1234def", "");
				case "git status --short" -> ExecResult.completed(0, "", "");
				case "git log --oneline -n 5" -> ExecResult.completed(0, "abc1234 initial commit", "");
				default -> ExecResult.completed(1, "", "unexpected: " + spec.command());
			};
		}

		@Override
		public ExecHandle start(ExecSpec spec) {
			throw new UnsupportedOperationException("not used by AgentEnvironment");
		}

	}

}
