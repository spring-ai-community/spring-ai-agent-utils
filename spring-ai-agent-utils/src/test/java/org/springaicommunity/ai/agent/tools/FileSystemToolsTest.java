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
package org.springaicommunity.ai.agent.tools;

import java.io.IOException;
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
 * Tests for {@link FileSystemTools}.
 *
 * @author Christian Tzolov
 */
@DisplayName("FileSystemTools Tests")
class FileSystemToolsTest {

	private FileSystemTools tools;

	@TempDir
	Path tempDir;

	@BeforeEach
	void setUp() {
		this.tools = new FileSystemTools();
	}

	@Nested
	@DisplayName("Read Tool Tests")
	class ReadToolTests {

		@Test
		@DisplayName("Should read simple file content")
		void shouldReadSimpleFile() throws IOException {
			// Given
			Path file = tempDir.resolve("test.txt");
			String content = "Line 1\nLine 2\nLine 3";
			Files.writeString(file, content, StandardCharsets.UTF_8);

			// When
			String result = tools.read(file.toString(), null, null, null);

			// Then
			assertThat(result).contains("File: " + file.toString());
			assertThat(result).contains("     1\tLine 1");
			assertThat(result).contains("     2\tLine 2");
			assertThat(result).contains("     3\tLine 3");
		}

		@Test
		@DisplayName("Should read file with offset and limit")
		void shouldReadFileWithOffsetAndLimit() throws IOException {
			// Given
			Path file = tempDir.resolve("test.txt");
			StringBuilder content = new StringBuilder();
			for (int i = 1; i <= 100; i++) {
				content.append("Line ").append(i).append("\n");
			}
			Files.writeString(file, content.toString(), StandardCharsets.UTF_8);

			// When - Read lines 50-54 (5 lines starting from line 50)
			String result = tools.read(file.toString(), 50, 5, null);

			// Then
			assertThat(result).contains("    50\tLine 50");
			assertThat(result).contains("    51\tLine 51");
			assertThat(result).contains("    54\tLine 54");
			assertThat(result).doesNotContain("    49\tLine 49");
			assertThat(result).doesNotContain("    55\tLine 55");
		}

		@Test
		@DisplayName("Should handle empty file")
		void shouldHandleEmptyFile() throws IOException {
			// Given
			Path file = tempDir.resolve("empty.txt");
			Files.writeString(file, "", StandardCharsets.UTF_8);

			// When
			String result = tools.read(file.toString(), null, null, null);

			// Then
			assertThat(result).contains("File is empty");
		}

		@Test
		@DisplayName("Should return error for non-existent file")
		void shouldReturnErrorForNonExistentFile() {
			// When
			String result = tools.read(tempDir.resolve("nonexistent.txt").toString(), null, null, null);

			// Then
			assertThat(result).contains("Error: File does not exist");
		}

		@Test
		@DisplayName("Should return error when path is a directory")
		void shouldReturnErrorWhenPathIsDirectory() {
			// When
			String result = tools.read(tempDir.toString(), null, null, null);

			// Then
			assertThat(result).contains("Error: Path is a directory, not a file");
		}

		@Test
		@DisplayName("Should truncate long lines")
		void shouldTruncateLongLines() throws IOException {
			// Given
			Path file = tempDir.resolve("long.txt");
			String longLine = "x".repeat(3000);
			Files.writeString(file, longLine, StandardCharsets.UTF_8);

			// When
			String result = tools.read(file.toString(), null, null, null);

			// Then
			assertThat(result).contains("(line truncated)");
		}

		@Test
		@DisplayName("Should preserve file with trailing newline")
		void shouldPreserveTrailingNewline() throws IOException {
			// Given
			Path file = tempDir.resolve("trailing.txt");
			Files.writeString(file, "Line 1\nLine 2\n", StandardCharsets.UTF_8);

			// When
			String result = tools.read(file.toString(), null, null, null);

			// Then
			assertThat(result).contains("     1\tLine 1");
			assertThat(result).contains("     2\tLine 2");
		}

	}

	@Nested
	@DisplayName("Write Tool Tests")
	class WriteToolTests {

		@Test
		@DisplayName("Should write new file")
		void shouldWriteNewFile() {
			// Given
			Path file = tempDir.resolve("new.txt");
			String content = "Hello World";

			// When
			String result = tools.write(file.toString(), content, null);

			// Then
			assertThat(result).contains("Successfully created file");
			assertThat(result).contains(file.toString());
			assertThat(file).exists();
			assertThat(file).hasContent(content);
		}

		@Test
		@DisplayName("Should overwrite existing file")
		void shouldOverwriteExistingFile() throws IOException {
			// Given
			Path file = tempDir.resolve("existing.txt");
			Files.writeString(file, "Original content", StandardCharsets.UTF_8);
			String newContent = "New content";

			// When
			String result = tools.write(file.toString(), newContent, null);

			// Then
			assertThat(result).contains("Successfully overwrote file");
			assertThat(file).hasContent(newContent);
		}

		@Test
		@DisplayName("Should create parent directories")
		void shouldCreateParentDirectories() {
			// Given
			Path file = tempDir.resolve("subdir1/subdir2/file.txt");
			String content = "Test content";

			// When
			String result = tools.write(file.toString(), content, null);

			// Then
			assertThat(result).contains("Successfully created file");
			assertThat(file).exists();
			assertThat(file.getParent()).exists();
		}

		@Test
		@DisplayName("Should write multi-line content")
		void shouldWriteMultiLineContent() {
			// Given
			Path file = tempDir.resolve("multiline.txt");
			String content = "Line 1\nLine 2\nLine 3";

			// When
			tools.write(file.toString(), content, null);

			// Then
			assertThat(file).hasContent(content);
		}

		@Test
		@DisplayName("Should write empty content")
		void shouldWriteEmptyContent() {
			// Given
			Path file = tempDir.resolve("empty.txt");

			// When
			String result = tools.write(file.toString(), "", null);

			// Then
			assertThat(result).contains("Successfully created file");
			assertThat(file).exists();
			assertThat(file).hasContent("");
		}

		@Test
		@DisplayName("Should preserve exact content including trailing newlines")
		void shouldPreserveExactContent() {
			// Given
			Path file = tempDir.resolve("exact.txt");
			String content = "Line 1\nLine 2\n";

			// When
			tools.write(file.toString(), content, null);

			// Then
			assertThat(file).hasContent(content);
		}

	}

	@Nested
	@DisplayName("Edit Tool Tests")
	class EditToolTests {

		@Test
		@DisplayName("Should edit file by replacing unique string")
		void shouldEditFileByReplacingUniqueString() throws IOException {
			// Given
			Path file = tempDir.resolve("edit.txt");
			String original = "Line 1\nLine 2\nLine 3";
			Files.writeString(file, original, StandardCharsets.UTF_8);

			// When
			String result = tools.edit(file.toString(), "Line 2", "Modified Line 2", null, null);

			// Then
			assertThat(result).contains("The file " + file.toString() + " has been updated");
			assertThat(result).contains("Here's the result of running `cat -n` on a snippet");
			assertThat(result).contains("→");
			assertThat(file).content(StandardCharsets.UTF_8).contains("Modified Line 2");
		}

		@Test
		@DisplayName("Should edit multi-line string replacement")
		void shouldEditMultiLineString() throws IOException {
			// Given
			Path file = tempDir.resolve("multiline.java");
			String original = "package org.example;\n\nimport java.util.List;\nimport java.util.Map;\n\npublic class Test {}";
			Files.writeString(file, original, StandardCharsets.UTF_8);

			String oldString = "import java.util.List;\nimport java.util.Map;";
			String newString = "import java.util.List;\nimport java.util.Map;\nimport java.util.Set;";

			// When
			String result = tools.edit(file.toString(), oldString, newString, null, null);

			// Then
			assertThat(result).contains("has been updated");
			assertThat(file).content(StandardCharsets.UTF_8).contains("import java.util.Set;");
		}

		@Test
		@DisplayName("Should fail when old_string not found")
		void shouldFailWhenOldStringNotFound() throws IOException {
			// Given
			Path file = tempDir.resolve("edit.txt");
			Files.writeString(file, "Line 1\nLine 2", StandardCharsets.UTF_8);

			// When
			String result = tools.edit(file.toString(), "NonExistent", "New", null, null);

			// Then
			assertThat(result).contains("Error: old_string not found in file");
		}

		@Test
		@DisplayName("Should fail when old_string appears multiple times without replace_all")
		void shouldFailWhenMultipleOccurrencesWithoutReplaceAll() throws IOException {
			// Given
			Path file = tempDir.resolve("edit.txt");
			Files.writeString(file, "foo\nbar\nfoo\nbaz", StandardCharsets.UTF_8);

			// When
			String result = tools.edit(file.toString(), "foo", "replaced", null, null);

			// Then
			assertThat(result).contains("Error: old_string appears 2 times in the file");
			assertThat(result).contains("replace_all=true");
		}

		@Test
		@DisplayName("Should replace all occurrences when replace_all is true")
		void shouldReplaceAllOccurrencesWhenFlagSet() throws IOException {
			// Given
			Path file = tempDir.resolve("edit.txt");
			Files.writeString(file, "foo\nbar\nfoo\nbaz", StandardCharsets.UTF_8);

			// When
			String result = tools.edit(file.toString(), "foo", "replaced", true, null);

			// Then
			assertThat(result).contains("has been updated");
			String content = Files.readString(file, StandardCharsets.UTF_8);
			assertThat(content).isEqualTo("replaced\nbar\nreplaced\nbaz");
		}

		@Test
		@DisplayName("Should fail when old_string equals new_string")
		void shouldFailWhenOldEqualsNew() throws IOException {
			// Given
			Path file = tempDir.resolve("edit.txt");
			Files.writeString(file, "content", StandardCharsets.UTF_8);

			// When
			String result = tools.edit(file.toString(), "same", "same", null, null);

			// Then
			assertThat(result).contains("Error: old_string and new_string must be different");
		}

		@Test
		@DisplayName("Should return error for non-existent file")
		void shouldReturnErrorForNonExistentFile() {
			// When
			String result = tools.edit(tempDir.resolve("nonexistent.txt").toString(), "old", "new", null, null);

			// Then
			assertThat(result).contains("Error: File does not exist");
		}

		@Test
		@DisplayName("Should return error when path is a directory")
		void shouldReturnErrorWhenPathIsDirectory() {
			// When
			String result = tools.edit(tempDir.toString(), "old", "new", null, null);

			// Then
			assertThat(result).contains("Error: Path is a directory, not a file");
		}

		@Test
		@DisplayName("Should preserve file with trailing newline")
		void shouldPreserveTrailingNewline() throws IOException {
			// Given
			Path file = tempDir.resolve("trailing.txt");
			Files.writeString(file, "Line 1\nLine 2\n", StandardCharsets.UTF_8);

			// When
			tools.edit(file.toString(), "Line 1", "Modified Line 1", null, null);

			// Then
			String content = Files.readString(file, StandardCharsets.UTF_8);
			assertThat(content).endsWith("\n");
			assertThat(content).isEqualTo("Modified Line 1\nLine 2\n");
		}

		@Test
		@DisplayName("Should preserve file without trailing newline")
		void shouldPreserveNoTrailingNewline() throws IOException {
			// Given
			Path file = tempDir.resolve("notrailing.txt");
			Files.writeString(file, "Line 1\nLine 2", StandardCharsets.UTF_8);

			// When
			tools.edit(file.toString(), "Line 1", "Modified Line 1", null, null);

			// Then
			String content = Files.readString(file, StandardCharsets.UTF_8);
			assertThat(content).doesNotEndWith("\n");
			assertThat(content).isEqualTo("Modified Line 1\nLine 2");
		}

		@Test
		@DisplayName("Should format response with line numbers and arrow")
		void shouldFormatResponseCorrectly() throws IOException {
			// Given
			Path file = tempDir.resolve("format.txt");
			Files.writeString(file, "Line 1\nLine 2\nLine 3\nLine 4\nLine 5", StandardCharsets.UTF_8);

			// When
			String result = tools.edit(file.toString(), "Line 3", "Modified Line 3", null, null);

			// Then
			assertThat(result).contains("→"); // Arrow character
			assertThat(result).matches("(?s).*\\s+\\d+→.*"); // Line number followed by arrow
			assertThat(result).contains("Modified Line 3");
		}

		@Test
		@DisplayName("Should show context around edit in response")
		void shouldShowContextAroundEdit() throws IOException {
			// Given
			Path file = tempDir.resolve("context.txt");
			StringBuilder content = new StringBuilder();
			for (int i = 1; i <= 20; i++) {
				content.append("Line ").append(i);
				if (i < 20)
					content.append("\n");
			}
			Files.writeString(file, content.toString(), StandardCharsets.UTF_8);

			// When
			String result = tools.edit(file.toString(), "Line 10", "Modified Line 10", null, null);

			// Then
			// Should show ~5 lines before and after the edit
			assertThat(result).contains("Line 5"); // Context before
			assertThat(result).contains("Line 9"); // Just before
			assertThat(result).contains("Modified Line 10"); // The edit
			assertThat(result).contains("Line 11"); // Just after
			assertThat(result).contains("Line 15"); // Context after
		}

		@Test
		@DisplayName("Should handle edit at beginning of file")
		void shouldHandleEditAtBeginning() throws IOException {
			// Given
			Path file = tempDir.resolve("beginning.txt");
			Files.writeString(file, "First Line\nSecond Line\nThird Line", StandardCharsets.UTF_8);

			// When
			String result = tools.edit(file.toString(), "First Line", "Modified First Line", null, null);

			// Then
			assertThat(result).contains("Modified First Line");
			assertThat(result).contains("     1→");
		}

		@Test
		@DisplayName("Should handle edit at end of file")
		void shouldHandleEditAtEnd() throws IOException {
			// Given
			Path file = tempDir.resolve("end.txt");
			Files.writeString(file, "First Line\nSecond Line\nLast Line", StandardCharsets.UTF_8);

			// When
			String result = tools.edit(file.toString(), "Last Line", "Modified Last Line", null, null);

			// Then
			assertThat(result).contains("Modified Last Line");
		}

		@Test
		@DisplayName("Should handle literal string replacement (not regex)")
		void shouldHandleLiteralStringReplacement() throws IOException {
			// Given
			Path file = tempDir.resolve("literal.txt");
			Files.writeString(file, "Text with special chars: .*+?[]{}()", StandardCharsets.UTF_8);

			// When
			String result = tools.edit(file.toString(), ".*+?[]{}()", "REPLACED", null, null);

			// Then
			assertThat(result).contains("has been updated");
			String content = Files.readString(file, StandardCharsets.UTF_8);
			assertThat(content).contains("REPLACED");
			assertThat(content).doesNotContain(".*+?[]{}()");
		}

	}

	@Nested
	@DisplayName("Integration Tests")
	class IntegrationTests {

		@Test
		@DisplayName("Should write then read file")
		void shouldWriteThenRead() {
			// Given
			Path file = tempDir.resolve("integration.txt");
			String content = "Integration test content\nLine 2";

			// When
			String writeResult = tools.write(file.toString(), content, null);
			String readResult = tools.read(file.toString(), null, null, null);

			// Then
			assertThat(writeResult).contains("Successfully created file");
			assertThat(readResult).contains("Integration test content");
			assertThat(readResult).contains("     1\tIntegration test content");
		}

		@Test
		@DisplayName("Should write, edit, then read file")
		void shouldWriteEditThenRead() {
			// Given
			Path file = tempDir.resolve("workflow.txt");
			String original = "Original line 1\nOriginal line 2";

			// When
			tools.write(file.toString(), original, null);
			tools.edit(file.toString(), "Original line 1", "Modified line 1", null, null);
			String result = tools.read(file.toString(), null, null, null);

			// Then
			assertThat(result).contains("Modified line 1");
			assertThat(result).contains("Original line 2");
		}

		@Test
		@DisplayName("Should handle multiple edits in sequence")
		void shouldHandleMultipleEdits() throws IOException {
			// Given
			Path file = tempDir.resolve("multiple.txt");
			Files.writeString(file, "Line A\nLine B\nLine C", StandardCharsets.UTF_8);

			// When
			tools.edit(file.toString(), "Line A", "Modified A", null, null);
			tools.edit(file.toString(), "Line B", "Modified B", null, null);
			tools.edit(file.toString(), "Line C", "Modified C", null, null);

			// Then
			String content = Files.readString(file, StandardCharsets.UTF_8);
			assertThat(content).isEqualTo("Modified A\nModified B\nModified C");
		}

	}

}
