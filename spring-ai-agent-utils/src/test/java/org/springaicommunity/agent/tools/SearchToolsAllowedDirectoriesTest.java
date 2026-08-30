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

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Directory confinement for the search tools (GrepTool, GlobTool, ListDirectoryTool) —
 * the {@code allowedDirectories} option shares {@link AllowedDirectories} with
 * FileSystemTools, closing the bypass where an explicit {@code path} argument escaped the
 * {@code workingDirectory}.
 *
 * @author Christian Tzolov
 */
class SearchToolsAllowedDirectoriesTest {

	private static final String DENIED = "Error: Access denied. Path is outside the allowed directories:";

	@TempDir
	Path workspace;

	@TempDir
	Path outside;

	@BeforeEach
	void setUp() throws Exception {
		Files.writeString(this.workspace.resolve("inside.txt"), "needle in workspace\n");
		Files.writeString(this.outside.resolve("secret.env"), "needle outside\n");
	}

	@Test
	void grepExplicitPathOutsideAllowedDirectoriesIsDenied() {
		GrepTool grep = GrepTool.builder().workingDirectory(this.workspace).allowedDirectory(this.workspace).build();

		String outsideResult = grep.grep("needle", this.outside.toString(), null, null, null, null, null, null, null,
				null, null, null, null);
		assertThat(outsideResult).startsWith(DENIED);

		String insideResult = grep.grep("needle", this.workspace.toString(), null, null, null, null, null, null, null,
				null, null, null, null);
		assertThat(insideResult).contains("inside.txt");
	}

	@Test
	void grepWorkingDirectoryFallbackStaysAllowed() {
		GrepTool grep = GrepTool.builder().workingDirectory(this.workspace).allowedDirectory(this.workspace).build();

		// No explicit path -> workingDirectory fallback, which is inside the jail
		String result = grep.grep("needle", null, null, null, null, null, null, null, null, null, null, null, null);
		assertThat(result).contains("inside.txt");
	}

	@Test
	void grepTraversalEscapeIsDenied() {
		GrepTool grep = GrepTool.builder().allowedDirectory(this.workspace).build();

		String result = grep.grep("needle", this.workspace + "/../", null, null, null, null, null, null, null, null,
				null, null, null);
		assertThat(result).startsWith(DENIED);
	}

	@Test
	void globExplicitPathOutsideAllowedDirectoriesIsDenied() {
		GlobTool glob = GlobTool.builder().workingDirectory(this.workspace).allowedDirectory(this.workspace).build();

		assertThat(glob.glob("**/*.env", this.outside.toString())).startsWith(DENIED);
		assertThat(glob.glob("*.txt", this.workspace.toString())).contains("inside.txt");
	}

	@Test
	void listDirectoryExplicitPathOutsideAllowedDirectoriesIsDenied() {
		ListDirectoryTool listDirectory = ListDirectoryTool.builder()
			.workingDirectory(this.workspace)
			.allowedDirectory(this.workspace)
			.build();

		assertThat(listDirectory.listDirectory(this.outside.toString(), null, null)).startsWith(DENIED);
		assertThat(listDirectory.listDirectory(null, null, null)).contains("inside.txt");
	}

	@Test
	void symlinkInsideWorkspaceDoesNotLeakOutsideContentWhenConfined() throws Exception {
		Files.createSymbolicLink(this.workspace.resolve("link"), this.outside);

		GrepTool grep = GrepTool.builder().allowedDirectory(this.workspace).build();
		String grepResult = grep.grep("needle", this.workspace.toString(), null, null, null, null, null, null, null,
				null, null, null, null);
		assertThat(grepResult).doesNotContain("secret.env");

		GlobTool glob = GlobTool.builder().allowedDirectory(this.workspace).build();
		assertThat(glob.glob("**/*.env", this.workspace.toString())).doesNotContain("secret.env");
	}

	@Test
	void unconfinedTraversalStillFollowsSymlinks() throws Exception {
		Files.createSymbolicLink(this.workspace.resolve("link"), this.outside);

		GrepTool grep = GrepTool.builder().build();
		String result = grep.grep("needle", this.workspace.toString(), null, null, null, null, null, null, null, null,
				null, null, null);
		assertThat(result).contains("secret.env");
	}

	@Test
	void canonicalPathInsideSymlinkedAllowedDirectoryIsAccepted() throws Exception {
		// Configured allowed dir reached through a symlink; target given canonically
		Path real = Files.createDirectory(this.workspace.resolve("real"));
		Path link = Files.createSymbolicLink(this.workspace.resolve("linkdir"), real);
		Files.writeString(real.resolve("data.txt"), "needle canonical\n");

		GrepTool grep = GrepTool.builder().allowedDirectory(link).build();
		String result = grep.grep("needle", real.toRealPath().toString(), null, null, null, null, null, null, null,
				null, null, null, null);
		assertThat(result).contains("data.txt");
	}

	@Test
	void relativeWorkingDirectoryWithDotDotIsNormalizedAtBuildTime() {
		Path dotted = this.workspace.resolve("sub").resolve("..");

		GrepTool grep = GrepTool.builder().workingDirectory(dotted).allowedDirectory(this.workspace).build();
		String result = grep.grep("needle", null, null, null, null, null, null, null, null, null, null, null, null);
		assertThat(result).contains("inside.txt");
	}

	@Test
	void unrestrictedByDefault() {
		GrepTool grep = GrepTool.builder().build();
		GlobTool glob = GlobTool.builder().build();
		ListDirectoryTool listDirectory = ListDirectoryTool.builder().build();

		assertThat(grep.grep("needle", this.outside.toString(), null, null, null, null, null, null, null, null, null,
				null, null))
			.contains("secret.env");
		assertThat(glob.glob("*.env", this.outside.toString())).contains("secret.env");
		assertThat(listDirectory.listDirectory(this.outside.toString(), null, null)).contains("secret.env");
	}

}
