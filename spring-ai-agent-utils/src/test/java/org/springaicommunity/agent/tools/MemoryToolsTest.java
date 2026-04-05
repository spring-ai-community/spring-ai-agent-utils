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
package org.springaicommunity.agent.tools;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link MemoryTools}.
 *
 * @author Christian Tzolov
 */
@DisplayName("MemoryTools Tests")
class MemoryToolsTest {

	@TempDir
	Path tempDir;

	private MemoryTools tools;

	@BeforeEach
	void setUp() {
		tools = MemoryTools.builder().memoriesDir(tempDir).build();
	}

	// -------------------------------------------------------------------------
	// MemoryView
	// -------------------------------------------------------------------------

	@Nested
	@DisplayName("MemoryView")
	class MemoryViewTests {

		@Test
		@DisplayName("Lists root directory")
		void listRoot() throws IOException {
			Files.writeString(tempDir.resolve("a.md"), "content");
			String result = tools.memoryView("", null);
			assertThat(result).contains("a.md");
		}

		@Test
		@DisplayName("Lists nested directory two levels deep")
		void listTwoLevels() throws IOException {
			Path sub = tempDir.resolve("sub");
			Files.createDirectory(sub);
			Files.writeString(sub.resolve("note.md"), "hi");
			String result = tools.memoryView("", null);
			assertThat(result).contains("sub/").contains("note.md");
		}

		@Test
		@DisplayName("Reads file with line numbers")
		void readFile() throws IOException {
			Files.writeString(tempDir.resolve("mem.md"), "line1\nline2\nline3");
			String result = tools.memoryView("mem.md", null);
			assertThat(result).contains("1\tline1").contains("2\tline2").contains("3\tline3");
		}

		@Test
		@DisplayName("Reads file with line range")
		void readFileWithRange() throws IOException {
			Files.writeString(tempDir.resolve("mem.md"), "A\nB\nC\nD\nE");
			String result = tools.memoryView("mem.md", "2,4");
			assertThat(result).contains("2\tB").contains("3\tC").contains("4\tD");
			assertThat(result).doesNotContain("1\tA").doesNotContain("5\tE");
		}

		@Test
		@DisplayName("Returns error for non-existent path")
		void errorOnMissing() {
			assertThat(tools.memoryView("ghost.md", null)).startsWith("Error:");
		}

		@Test
		@DisplayName("Returns error for invalid view_range format")
		void errorOnBadRange() throws IOException {
			Files.writeString(tempDir.resolve("f.md"), "x");
			assertThat(tools.memoryView("f.md", "bad")).startsWith("Error:");
		}

	}

	// -------------------------------------------------------------------------
	// MemoryCreate
	// -------------------------------------------------------------------------

	@Nested
	@DisplayName("MemoryCreate")
	class MemoryCreateTests {

		@Test
		@DisplayName("Creates new file with content")
		void createNewFile() throws IOException {
			String result = tools.memoryCreate("user.md", "---\nname: user\ntype: user\n---\nContent");
			assertThat(result).contains("Successfully created");
			assertThat(Files.readString(tempDir.resolve("user.md"))).contains("Content");
		}

		@Test
		@DisplayName("Creates parent directories automatically")
		void createWithParentDirs() {
			String result = tools.memoryCreate("sub/dir/note.md", "body");
			assertThat(result).contains("Successfully created");
			assertThat(tempDir.resolve("sub/dir/note.md")).exists();
		}

		@Test
		@DisplayName("Returns error when file already exists")
		void errorWhenExists() throws IOException {
			Files.writeString(tempDir.resolve("existing.md"), "old");
			assertThat(tools.memoryCreate("existing.md", "new")).startsWith("Error:");
		}

	}

	// -------------------------------------------------------------------------
	// MemoryStrReplace
	// -------------------------------------------------------------------------

	@Nested
	@DisplayName("MemoryStrReplace")
	class MemoryStrReplaceTests {

		@Test
		@DisplayName("Replaces unique occurrence")
		void replaceUnique() throws IOException {
			Files.writeString(tempDir.resolve("f.md"), "hello world");
			String result = tools.memoryStrReplace("f.md", "world", "earth");
			assertThat(result).contains("Successfully edited");
			assertThat(Files.readString(tempDir.resolve("f.md"))).isEqualTo("hello earth");
		}

		@Test
		@DisplayName("Deletes text when newStr is empty — no snippet returned")
		void deleteText() throws IOException {
			Files.writeString(tempDir.resolve("f.md"), "keep remove keep");
			String result = tools.memoryStrReplace("f.md", " remove", "");
			assertThat(result).contains("deleted").doesNotContain("snippet");
			assertThat(Files.readString(tempDir.resolve("f.md"))).isEqualTo("keep keep");
		}

		@Test
		@DisplayName("Returns error when oldStr not found")
		void errorWhenNotFound() throws IOException {
			Files.writeString(tempDir.resolve("f.md"), "content");
			assertThat(tools.memoryStrReplace("f.md", "missing", "x")).startsWith("Error:");
		}

		@Test
		@DisplayName("Returns error when oldStr is ambiguous")
		void errorWhenAmbiguous() throws IOException {
			Files.writeString(tempDir.resolve("f.md"), "dup\ndup");
			assertThat(tools.memoryStrReplace("f.md", "dup", "x")).startsWith("Error:");
		}

		@Test
		@DisplayName("Returns error for non-existent file")
		void errorOnMissing() {
			assertThat(tools.memoryStrReplace("ghost.md", "a", "b")).startsWith("Error:");
		}

	}

	// -------------------------------------------------------------------------
	// MemoryInsert
	// -------------------------------------------------------------------------

	@Nested
	@DisplayName("MemoryInsert")
	class MemoryInsertTests {

		@Test
		@DisplayName("Inserts at line 0 (before first line)")
		void insertAtBeginning() throws IOException {
			Files.writeString(tempDir.resolve("f.md"), "B\nC");
			tools.memoryInsert("f.md", 0, "A");
			assertThat(Files.readString(tempDir.resolve("f.md"))).isEqualTo("A\nB\nC");
		}

		@Test
		@DisplayName("Inserts after given line")
		void insertAfterLine() throws IOException {
			Files.writeString(tempDir.resolve("f.md"), "A\nC");
			tools.memoryInsert("f.md", 1, "B");
			assertThat(Files.readString(tempDir.resolve("f.md"))).isEqualTo("A\nB\nC");
		}

		@Test
		@DisplayName("Appends to end of file")
		void appendToEnd() throws IOException {
			Files.writeString(tempDir.resolve("f.md"), "A\nB");
			tools.memoryInsert("f.md", 2, "C");
			assertThat(Files.readString(tempDir.resolve("f.md"))).isEqualTo("A\nB\nC");
		}

		@Test
		@DisplayName("Preserves trailing newline")
		void preservesTrailingNewline() throws IOException {
			Files.writeString(tempDir.resolve("f.md"), "A\nB\n");
			tools.memoryInsert("f.md", 2, "C");
			assertThat(Files.readString(tempDir.resolve("f.md"))).endsWith("\n");
		}

		@Test
		@DisplayName("Returns error when insert_line exceeds file length")
		void errorOnOutOfBounds() throws IOException {
			Files.writeString(tempDir.resolve("f.md"), "A");
			assertThat(tools.memoryInsert("f.md", 99, "X")).startsWith("Error:");
		}

	}

	// -------------------------------------------------------------------------
	// MemoryDelete
	// -------------------------------------------------------------------------

	@Nested
	@DisplayName("MemoryDelete")
	class MemoryDeleteTests {

		@Test
		@DisplayName("Deletes a file")
		void deleteFile() throws IOException {
			Path file = tempDir.resolve("del.md");
			Files.writeString(file, "x");
			tools.memoryDelete("del.md");
			assertThat(file).doesNotExist();
		}

		@Test
		@DisplayName("Deletes a directory recursively")
		void deleteDirectory() throws IOException {
			Path sub = tempDir.resolve("sub");
			Files.createDirectory(sub);
			Files.writeString(sub.resolve("f.md"), "x");
			String result = tools.memoryDelete("sub");
			assertThat(result).contains("Successfully deleted directory");
			assertThat(sub).doesNotExist();
		}

		@Test
		@DisplayName("Returns error for non-existent path")
		void errorOnMissing() {
			assertThat(tools.memoryDelete("ghost.md")).startsWith("Error:");
		}

		@Test
		@DisplayName("Cannot delete memories root")
		void cannotDeleteRoot() {
			assertThat(tools.memoryDelete("")).startsWith("Error:");
		}

	}

	// -------------------------------------------------------------------------
	// MemoryRename
	// -------------------------------------------------------------------------

	@Nested
	@DisplayName("MemoryRename")
	class MemoryRenameTests {

		@Test
		@DisplayName("Renames a file")
		void renameFile() throws IOException {
			Files.writeString(tempDir.resolve("old.md"), "data");
			tools.memoryRename("old.md", "new.md");
			assertThat(tempDir.resolve("old.md")).doesNotExist();
			assertThat(tempDir.resolve("new.md")).exists();
		}

		@Test
		@DisplayName("Creates destination parent directories")
		void createsParentDirs() throws IOException {
			Files.writeString(tempDir.resolve("f.md"), "x");
			tools.memoryRename("f.md", "sub/f.md");
			assertThat(tempDir.resolve("sub/f.md")).exists();
		}

		@Test
		@DisplayName("Returns error when source does not exist")
		void errorOnMissingSource() {
			assertThat(tools.memoryRename("ghost.md", "dest.md")).startsWith("Error:");
		}

		@Test
		@DisplayName("Returns error when destination already exists")
		void errorWhenDestExists() throws IOException {
			Files.writeString(tempDir.resolve("a.md"), "a");
			Files.writeString(tempDir.resolve("b.md"), "b");
			assertThat(tools.memoryRename("a.md", "b.md")).startsWith("Error:");
		}

	}

	// -------------------------------------------------------------------------
	// Security
	// -------------------------------------------------------------------------

	@Nested
	@DisplayName("Security")
	class SecurityTests {

		@Test
		@DisplayName("Blocks path traversal via ..")
		void blocksPathTraversal() {
			assertThat(tools.memoryView("../../etc/passwd", null)).startsWith("Error:");
		}

		@Test
		@DisplayName("Blocks absolute paths")
		void blocksAbsolutePaths() {
			assertThat(tools.memoryCreate("/etc/evil.md", "x")).startsWith("Error:");
		}

	}

	// -------------------------------------------------------------------------
	// Builder
	// -------------------------------------------------------------------------

	@Nested
	@DisplayName("Builder")
	class BuilderTests {

		@Test
		@DisplayName("Auto-creates memories directory on build()")
		void autoCreatesDir(@TempDir Path base) {
			Path newDir = base.resolve("memories");
			MemoryTools.builder().memoriesDir(newDir).build();
			assertThat(newDir).isDirectory();
		}

		@Test
		@DisplayName("Accepts string path")
		void acceptsStringPath(@TempDir Path base) {
			Path newDir = base.resolve("str-mem");
			MemoryTools t = MemoryTools.builder().memoriesDir(newDir.toString()).build();
			assertThat(t.getMemoriesDir()).isEqualTo(newDir.normalize());
		}

		@Test
		@DisplayName("Normalizes memoriesDir")
		void normalizesDir(@TempDir Path base) throws IOException {
			Path sub = base.resolve("a");
			Files.createDirectory(sub);
			// Pass a non-normalized path using ".."
			Path nonNormalized = sub.resolve("../a");
			MemoryTools t = MemoryTools.builder().memoriesDir(nonNormalized).build();
			assertThat(t.getMemoriesDir()).isEqualTo(sub.normalize());
		}

	}

	// -------------------------------------------------------------------------
	// System Prompt
	// -------------------------------------------------------------------------

	@Nested
	@DisplayName("MEMORY_TOOLS_SYSTEM_PROMPT.md")
	class SystemPromptTests {

		private String prompt;

		@BeforeEach
		void loadPrompt() throws IOException {
			try (InputStream in = getClass().getResourceAsStream("/prompt/MEMORY_TOOLS_SYSTEM_PROMPT.md")) {
				assertThat(in).as("MEMORY_TOOLS_SYSTEM_PROMPT.md must exist on classpath").isNotNull();
				prompt = new String(in.readAllBytes(), StandardCharsets.UTF_8);
			}
		}

		@Test
		@DisplayName("Contains all six tool names")
		void containsAllToolNames() {
			assertThat(prompt)
				.contains("MemoryView")
				.contains("MemoryCreate")
				.contains("MemoryStrReplace")
				.contains("MemoryInsert")
				.contains("MemoryDelete")
				.contains("MemoryRename");
		}

		@Test
		@DisplayName("Contains MEMORIES_ROOT_DIERCTORY template placeholder")
		void containsMemoriesRootPlaceholder() {
			assertThat(prompt).contains("{MEMORIES_ROOT_DIERCTORY}");
		}

		@Test
		@DisplayName("Documents all four memory types")
		void documentsMemoryTypes() {
			assertThat(prompt)
				.contains("user")
				.contains("feedback")
				.contains("project")
				.contains("reference");
		}

		@Test
		@DisplayName("Describes the two-step save workflow")
		void describesTwoStepSave() {
			assertThat(prompt).contains("Step 1").contains("Step 2");
		}

		@Test
		@DisplayName("References MEMORY.md as the index")
		void referencesMemoryMd() {
			assertThat(prompt).contains("MEMORY.md");
		}

	}

}
