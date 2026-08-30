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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springaicommunity.agent.common.workspace.Workspace;

import org.springframework.ai.tool.ToolCallback;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The {@code workspace} builder option on the tools: one call configures working
 * directory and directory confinement together, and SkillsTool maps skill base paths
 * through {@link Workspace#display} so the model sees workspace paths, not host paths.
 *
 * @author Christian Tzolov
 */
class WorkspaceAdoptionTest {

	private static final String DENIED = "Error: Access denied. Path is outside the allowed directories:";

	private static final String SKILL_MD = """
			---
			name: test-skill
			description: A test skill
			---

			This is the test skill content.
			""";

	@TempDir
	Path workspaceDir;

	@TempDir
	Path outside;

	private Workspace workspace;

	@BeforeEach
	void setUp() throws Exception {
		this.workspace = Workspace.local(this.workspaceDir);
		Files.writeString(this.workspaceDir.resolve("inside.txt"), "needle in workspace\n");
		Files.writeString(this.outside.resolve("secret.env"), "needle outside\n");
	}

	@Test
	void fileSystemToolsWorkspaceConfinesOperations() {
		FileSystemTools tools = FileSystemTools.builder().workspace(this.workspace).build();

		assertThat(tools.read(this.workspaceDir.resolve("inside.txt").toString(), null, null)).contains("needle");
		assertThat(tools.read(this.outside.resolve("secret.env").toString(), null, null)).startsWith(DENIED);
	}

	@Test
	void grepToolWorkspaceSetsWorkingDirectoryAndJail() {
		GrepTool grep = GrepTool.builder().workspace(this.workspace).build();

		// No explicit path -> defaults to the workspace root
		String defaulted = grep.grep("needle", null, null, null, null, null, null, null, null, null, null, null, null);
		assertThat(defaulted).contains("inside.txt");

		String escaped = grep.grep("needle", this.outside.toString(), null, null, null, null, null, null, null, null,
				null, null, null);
		assertThat(escaped).startsWith(DENIED);
	}

	@Test
	void globToolWorkspaceSetsWorkingDirectoryAndJail() {
		GlobTool glob = GlobTool.builder().workspace(this.workspace).build();

		assertThat(glob.glob("*.txt", null)).contains("inside.txt");
		assertThat(glob.glob("*.env", this.outside.toString())).startsWith(DENIED);
	}

	@Test
	void listDirectoryToolWorkspaceSetsWorkingDirectoryAndJail() {
		ListDirectoryTool listDirectory = ListDirectoryTool.builder().workspace(this.workspace).build();

		assertThat(listDirectory.listDirectory(null, null, null)).contains("inside.txt");
		assertThat(listDirectory.listDirectory(this.outside.toString(), null, null)).startsWith(DENIED);
	}

	@Test
	void shellToolsWorkspaceRunsCommandsAtWorkspaceRoot() throws Exception {
		ShellTools shell = ShellTools.builder().workspace(this.workspace).build();

		String result = shell.bash("pwd", null, null, null);

		// toRealPath: on macOS the temp dir is reached via the /var -> /private/var
		// symlink and pwd reports the resolved form
		assertThat(result).contains(this.workspaceDir.toRealPath().toString());
	}

	@Test
	void skillsToolDisplaysWorkspaceMappedBasePath() throws Exception {
		Path skillDir = this.workspaceDir.resolve("skills").resolve("test-skill");
		Files.createDirectories(skillDir);
		Files.writeString(skillDir.resolve("SKILL.md"), SKILL_MD, StandardCharsets.UTF_8);

		Workspace sandboxView = new Workspace() {

			@Override
			public Path root() {
				return WorkspaceAdoptionTest.this.workspaceDir;
			}

			@Override
			public String display(String hostPath) {
				return hostPath.replace(WorkspaceAdoptionTest.this.workspaceDir.toString(), "/workspace");
			}

		};

		ToolCallback callback = SkillsTool.builder()
			.addSkillsDirectory(this.workspaceDir.resolve("skills").toString())
			.workspace(sandboxView)
			.build();

		String result = callback.call("{\"command\":\"test-skill\"}");

		assertThat(result).contains("Base directory for this skill: /workspace/skills/test-skill");
		assertThat(result).doesNotContain(this.workspaceDir.toString());
	}

	@Test
	void skillsToolWithoutWorkspaceKeepsHostBasePath() throws Exception {
		Path skillDir = this.workspaceDir.resolve("skills").resolve("test-skill");
		Files.createDirectories(skillDir);
		Files.writeString(skillDir.resolve("SKILL.md"), SKILL_MD, StandardCharsets.UTF_8);

		ToolCallback callback = SkillsTool.builder()
			.addSkillsDirectory(this.workspaceDir.resolve("skills").toString())
			.build();

		assertThat(callback.call("{\"command\":\"test-skill\"}"))
			.contains("Base directory for this skill: " + skillDir);
	}

}
