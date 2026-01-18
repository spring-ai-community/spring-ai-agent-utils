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
package org.springaicommunity.agent.tools.task.subagent.claude;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springaicommunity.agent.tools.task.subagent.Kind;
import org.springaicommunity.agent.tools.task.subagent.SubagentReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link ClaudeSubagentReferences}.
 *
 * @author Christian Tzolov
 */
class ClaudeSubagentReferencesTest {

	@Test
	void shouldFindMarkdownFilesInDirectory(@TempDir Path tempDir) throws IOException {
		Files.writeString(tempDir.resolve("agent1.md"), "---\nname: agent1\n---\nContent");
		Files.writeString(tempDir.resolve("agent2.md"), "---\nname: agent2\n---\nContent");
		Files.writeString(tempDir.resolve("readme.txt"), "Not an agent");

		List<SubagentReference> refs = ClaudeSubagentReferences.fromRootDirectory(tempDir.toString());

		assertThat(refs).hasSize(2);
		assertThat(refs).allMatch(r -> r.kind().equals(Kind.CLAUDE_SUBAGENT.name()));
		assertThat(refs).allMatch(r -> r.uri().endsWith(".md"));
	}

	@Test
	void shouldFindMarkdownFilesRecursively(@TempDir Path tempDir) throws IOException {
		Path subDir = tempDir.resolve("subdir");
		Files.createDirectories(subDir);
		Files.writeString(tempDir.resolve("root.md"), "content");
		Files.writeString(subDir.resolve("nested.md"), "content");

		List<SubagentReference> refs = ClaudeSubagentReferences.fromRootDirectory(tempDir.toString());

		assertThat(refs).hasSize(2);
	}

	@Test
	void shouldReturnEmptyListForEmptyDirectory(@TempDir Path tempDir) {
		List<SubagentReference> refs = ClaudeSubagentReferences.fromRootDirectory(tempDir.toString());

		assertThat(refs).isEmpty();
	}

	@Test
	void shouldFailForNonExistentDirectory() {
		assertThatThrownBy(() -> ClaudeSubagentReferences.fromRootDirectory("/non/existent/path"))
			.isInstanceOf(RuntimeException.class)
			.hasMessageContaining("Root directory does not exist");
	}

	@Test
	void shouldFailWhenPathIsNotDirectory(@TempDir Path tempDir) throws IOException {
		Path file = tempDir.resolve("file.txt");
		Files.writeString(file, "content");

		assertThatThrownBy(() -> ClaudeSubagentReferences.fromRootDirectory(file.toString()))
			.isInstanceOf(RuntimeException.class)
			.hasMessageContaining("Path is not a directory");
	}

	@Test
	void shouldCombineMultipleDirectories(@TempDir Path tempDir) throws IOException {
		Path dir1 = tempDir.resolve("dir1");
		Path dir2 = tempDir.resolve("dir2");
		Files.createDirectories(dir1);
		Files.createDirectories(dir2);
		Files.writeString(dir1.resolve("a.md"), "content");
		Files.writeString(dir2.resolve("b.md"), "content");

		List<SubagentReference> refs = ClaudeSubagentReferences
			.fromRootDirectories(List.of(dir1.toString(), dir2.toString()));

		assertThat(refs).hasSize(2);
	}

}
