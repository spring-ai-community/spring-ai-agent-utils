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

	// Relative filePath resolves against the workspace, not the JVM working directory
	// (https://github.com/spring-ai-community/spring-ai-agent-utils/issues/116). The test
	// JVM runs outside the workspace, so resolving against it would be denied.

	@Test
	void fileSystemToolsResolvesRelativeFilePathAgainstWorkspaceNotJvmUserDir() throws Exception {
		Path userDir = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
		assertThat(this.workspace.root().toAbsolutePath().normalize()).isNotEqualTo(userDir);

		Files.createDirectories(this.workspaceDir.resolve("agent"));
		Files.writeString(this.workspaceDir.resolve("agent/steps.md"), "steps content\n");
		Files.createDirectories(this.workspaceDir.resolve("notes"));
		Files.writeString(this.workspaceDir.resolve("notes/plan.md"), "hello plan\n");

		FileSystemTools tools = FileSystemTools.builder().workspace(this.workspace).build();

		assertThat(tools.read("agent/steps.md", null, null)).contains("steps content");
		assertThat(tools.read("notes/plan.md", null, null)).contains("hello plan");

		assertThat(tools.write("notes/written.md", "from write\n")).contains("Successfully created file");
		assertThat(Files.readString(this.workspaceDir.resolve("notes/written.md"))).isEqualTo("from write\n");

		assertThat(tools.edit("notes/plan.md", "hello", "goodbye", null)).contains("has been updated");
		assertThat(Files.readString(this.workspaceDir.resolve("notes/plan.md"))).isEqualTo("goodbye plan\n");

		// Absolute paths are unchanged.
		Path steps = this.workspaceDir.resolve("agent/steps.md").toAbsolutePath();
		assertThat(tools.read(steps.toString(), null, null)).contains("steps content");
		assertThat(tools.write(this.workspaceDir.resolve("notes/abs.md").toAbsolutePath().toString(), "abs\n"))
			.contains("Successfully created file");
		assertThat(Files.readString(this.workspaceDir.resolve("notes/abs.md"))).isEqualTo("abs\n");
		assertThat(tools.edit(this.workspaceDir.resolve("notes/plan.md").toAbsolutePath().toString(), "goodbye",
				"hello", null))
			.contains("has been updated");

		// '..' still reaches the confinement check.
		assertThat(tools.read(escapePath() + "/secret.env", null, null)).startsWith(DENIED);
	}

	@Test
	void fileSystemToolsWorkingDirectoryResolvesRelativeFilePath() throws Exception {
		Files.createDirectories(this.workspaceDir.resolve("agent"));
		Files.writeString(this.workspaceDir.resolve("agent/steps.md"), "steps content\n");

		FileSystemTools tools = FileSystemTools.builder()
			.workingDirectory(this.workspaceDir)
			.allowedDirectory(this.workspaceDir)
			.build();

		assertThat(tools.read("agent/steps.md", null, null)).contains("steps content");
		assertThat(tools.read(this.outside.resolve("secret.env").toString(), null, null)).startsWith(DENIED);
	}

	@Test
	void fileSystemToolsWithoutWorkingDirectoryKeepsJvmRelativeResolution() throws Exception {
		Path userDir = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
		assertThat(this.workspace.root().toAbsolutePath().normalize()).isNotEqualTo(userDir);

		Files.createDirectories(this.workspaceDir.resolve("agent"));
		Files.writeString(this.workspaceDir.resolve("agent/steps.md"), "steps content\n");

		// Confinement without a working directory: a relative filePath is still resolved
		// against the JVM working directory and denied when that lands outside the jail.
		FileSystemTools confined = FileSystemTools.builder().allowedDirectory(this.workspaceDir).build();
		assertThat(confined.read("agent/steps.md", null, null)).startsWith(DENIED);
		assertThat(confined.write("notes/should-not.md", "nope\n")).startsWith(DENIED);
		assertThat(confined.edit("agent/steps.md", "steps", "nope", null)).startsWith(DENIED);
		assertThat(Files.exists(this.workspaceDir.resolve("notes/should-not.md"))).isFalse();

		// Absolute paths still work with no working directory.
		assertThat(confined.read(this.workspaceDir.resolve("agent/steps.md").toString(), null, null))
			.contains("steps content");

		// No confinement and no working directory: the relative path is not the workspace
		// file (it is resolved against user.dir, which is outside this workspace).
		FileSystemTools plain = FileSystemTools.builder().build();
		assertThat(plain.read("agent/steps.md", null, null)).doesNotContain("steps content");
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

	// Relative paths resolve against the workspace, not the JVM working directory
	// (https://github.com/spring-ai-community/spring-ai-agent-utils/issues/94). The test
	// JVM runs outside the workspace, so resolving against it would be denied.

	@Test
	void grepToolResolvesRelativePathAgainstWorkspace() throws Exception {
		createPlaybook();
		GrepTool grep = GrepTool.builder().workspace(this.workspace).build();

		assertThat(grep.grep("needle", "playbook", null, null, null, null, null, null, null, null, null, null, null))
			.contains("steps.md");
		assertThat(grep.grep("needle", escapePath(), null, null, null, null, null, null, null, null, null, null,
				null))
			.startsWith(DENIED);
	}

	@Test
	void globToolResolvesRelativePathAgainstWorkspace() throws Exception {
		createPlaybook();
		GlobTool glob = GlobTool.builder().workspace(this.workspace).build();

		assertThat(glob.glob("*.md", "playbook")).contains("steps.md");
		assertThat(glob.glob("*.env", escapePath())).startsWith(DENIED);
	}

	@Test
	void listDirectoryToolResolvesRelativePathAgainstWorkspace() throws Exception {
		createPlaybook();
		ListDirectoryTool listDirectory = ListDirectoryTool.builder().workspace(this.workspace).build();

		assertThat(listDirectory.listDirectory("playbook", null, null)).contains("steps.md");
		assertThat(listDirectory.listDirectory(escapePath(), null, null)).startsWith(DENIED);
	}

	@Test
	void relativePathResolvesAgainstOverriddenWorkingDirectory() throws Exception {
		createPlaybook();
		Files.createDirectories(this.workspaceDir.resolve("playbook/nested"));
		Files.writeString(this.workspaceDir.resolve("playbook/nested/deep.md"), "needle nested\n");
		ListDirectoryTool listDirectory = ListDirectoryTool.builder()
			.workspace(this.workspace)
			.workingDirectory(this.workspaceDir.resolve("playbook"))
			.build();

		// Resolved against the overridden working directory; confinement stays the
		// workspace root
		assertThat(listDirectory.listDirectory("nested", null, null)).contains("deep.md");
		assertThat(listDirectory.listDirectory("../../" + this.outside.getFileName(), null, null))
			.startsWith(DENIED);
	}

	private void createPlaybook() throws Exception {
		Files.createDirectories(this.workspaceDir.resolve("playbook"));
		Files.writeString(this.workspaceDir.resolve("playbook/steps.md"), "needle in playbook\n");
	}

	/** A relative path from the workspace root to the (sibling) outside directory. */
	private String escapePath() {
		return "../" + this.outside.getFileName();
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
