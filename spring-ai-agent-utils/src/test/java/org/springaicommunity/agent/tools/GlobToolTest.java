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
 * Tests for {@link GlobTool}.
 *
 * @author Christian Tzolov
 * @author Claude Code
 */
@DisplayName("GlobTool Tests")
class GlobToolTest {

	private GlobTool globTool;

	@TempDir
	Path tempDir;

	@BeforeEach
	void setUp() {
		this.globTool = GlobTool.builder().build();
	}

	@Nested
	@DisplayName("Basic Pattern Matching Tests")
	class BasicPatternMatchingTests {

		@Test
		@DisplayName("Should find files with simple extension pattern")
		void shouldFindFilesWithSimpleExtension() throws IOException {
			// Given
			Path javaFile1 = tempDir.resolve("Test.java");
			Path javaFile2 = tempDir.resolve("Main.java");
			Path txtFile = tempDir.resolve("readme.txt");
			Files.writeString(javaFile1, "public class Test {}", StandardCharsets.UTF_8);
			Files.writeString(javaFile2, "public class Main {}", StandardCharsets.UTF_8);
			Files.writeString(txtFile, "readme", StandardCharsets.UTF_8);

			// When
			String result = globTool.glob("*.java", tempDir.toString());

			// Then
			assertThat(result).contains(javaFile1.toString());
			assertThat(result).contains(javaFile2.toString());
			assertThat(result).doesNotContain(txtFile.toString());
		}

		@Test
		@DisplayName("Should find files with complex glob pattern")
		void shouldFindFilesWithComplexPattern() throws IOException {
			// Given
			Path subDir = tempDir.resolve("src").resolve("main");
			Files.createDirectories(subDir);
			Path javaFile = subDir.resolve("App.java");
			Path rootFile = tempDir.resolve("Test.java");
			Files.writeString(javaFile, "code", StandardCharsets.UTF_8);
			Files.writeString(rootFile, "code", StandardCharsets.UTF_8);

			// When
			String result = globTool.glob("**/*.java", tempDir.toString());

			// Then
			assertThat(result).contains(javaFile.toString());
			assertThat(result).contains(rootFile.toString());
		}

		@Test
		@DisplayName("Should return no matches message when pattern not found")
		void shouldReturnNoMatchesMessage() throws IOException {
			// Given
			Path txtFile = tempDir.resolve("test.txt");
			Files.writeString(txtFile, "content", StandardCharsets.UTF_8);

			// When
			String result = globTool.glob("*.java", tempDir.toString());

			// Then
			assertThat(result).contains("No files found matching pattern: *.java");
		}

		@Test
		@DisplayName("Should handle multiple file extensions")
		void shouldHandleMultipleExtensions() throws IOException {
			// Given
			Path tsFile = tempDir.resolve("component.ts");
			Path tsxFile = tempDir.resolve("app.tsx");
			Path jsFile = tempDir.resolve("script.js");
			Files.writeString(tsFile, "code", StandardCharsets.UTF_8);
			Files.writeString(tsxFile, "code", StandardCharsets.UTF_8);
			Files.writeString(jsFile, "code", StandardCharsets.UTF_8);

			// When
			String result = globTool.glob("*.ts", tempDir.toString());

			// Then
			assertThat(result).contains(tsFile.toString());
			assertThat(result).doesNotContain(tsxFile.toString());
			assertThat(result).doesNotContain(jsFile.toString());
		}

	}

	@Nested
	@DisplayName("Directory Traversal Tests")
	class DirectoryTraversalTests {

		@Test
		@DisplayName("Should search recursively in subdirectories")
		void shouldSearchRecursively() throws IOException {
			// Given
			Path subDir1 = tempDir.resolve("src");
			Path subDir2 = subDir1.resolve("main");
			Files.createDirectories(subDir2);
			Path file1 = tempDir.resolve("root.txt");
			Path file2 = subDir1.resolve("src.txt");
			Path file3 = subDir2.resolve("main.txt");
			Files.writeString(file1, "content", StandardCharsets.UTF_8);
			Files.writeString(file2, "content", StandardCharsets.UTF_8);
			Files.writeString(file3, "content", StandardCharsets.UTF_8);

			// When
			String result = globTool.glob("*.txt", tempDir.toString());

			// Then
			assertThat(result).contains(file1.toString());
			assertThat(result).contains(file2.toString());
			assertThat(result).contains(file3.toString());
		}

		@Test
		@DisplayName("Should ignore common directories")
		void shouldIgnoreCommonDirectories() throws IOException {
			// Given
			Path nodeModules = tempDir.resolve("node_modules");
			Path target = tempDir.resolve("target");
			Path git = tempDir.resolve(".git");
			Files.createDirectories(nodeModules);
			Files.createDirectories(target);
			Files.createDirectories(git);
			Path file1 = nodeModules.resolve("package.json");
			Path file2 = target.resolve("classes.jar");
			Path file3 = git.resolve("config");
			Path file4 = tempDir.resolve("package.json");
			Files.writeString(file1, "content", StandardCharsets.UTF_8);
			Files.writeString(file2, "content", StandardCharsets.UTF_8);
			Files.writeString(file3, "content", StandardCharsets.UTF_8);
			Files.writeString(file4, "content", StandardCharsets.UTF_8);

			// When
			String result = globTool.glob("*", tempDir.toString());

			// Then
			assertThat(result).contains(file4.toString());
			assertThat(result).doesNotContain("node_modules");
			assertThat(result).doesNotContain("target");
			assertThat(result).doesNotContain(".git");
		}

		@Test
		@DisplayName("Should use current directory when path is null")
		void shouldUseCurrentDirectoryWhenPathIsNull() {
			// When
			String result = globTool.glob("*.nonexistent", null);

			// Then - Should not error, searches current directory
			assertThat(result).doesNotContain("Error: Path does not exist");
		}

	}

	@Nested
	@DisplayName("Sorting Tests")
	class SortingTests {

		@Test
		@DisplayName("Should sort files by modification time")
		void shouldSortByModificationTime() throws IOException, InterruptedException {
			// Given
			Path file1 = tempDir.resolve("first.txt");
			Files.writeString(file1, "first", StandardCharsets.UTF_8);
			Thread.sleep(10); // Small delay to ensure different timestamps

			Path file2 = tempDir.resolve("second.txt");
			Files.writeString(file2, "second", StandardCharsets.UTF_8);
			Thread.sleep(10);

			Path file3 = tempDir.resolve("third.txt");
			Files.writeString(file3, "third", StandardCharsets.UTF_8);

			// When
			String result = globTool.glob("*.txt", tempDir.toString());

			// Then - Most recent file should appear first
			String[] lines = result.split("\n");
			assertThat(lines[0]).contains("third.txt");
		}

	}

	@Nested
	@DisplayName("Configuration Tests")
	class ConfigurationTests {

		@Test
		@DisplayName("Should respect maxResults limit")
		void shouldRespectMaxResultsLimit() throws IOException {
			// Given
			GlobTool limitedTool = GlobTool.builder().maxResults(3).build();
			for (int i = 1; i <= 10; i++) {
				Path file = tempDir.resolve("file" + i + ".txt");
				Files.writeString(file, "content", StandardCharsets.UTF_8);
			}

			// When
			String result = limitedTool.glob("*.txt", tempDir.toString());

			// Then
			String[] lines = result.split("\n");
			assertThat(lines).hasSizeLessThanOrEqualTo(3);
		}

		@Test
		@DisplayName("Should respect maxDepth limit")
		void shouldRespectMaxDepthLimit() throws IOException {
			// Given - maxDepth 1 means only root level
			GlobTool shallowTool = GlobTool.builder().maxDepth(1).build();
			Path level1 = tempDir.resolve("level1");
			Files.createDirectories(level1);
			Path file1 = tempDir.resolve("root.txt");
			Path file2 = level1.resolve("level1.txt");
			Files.writeString(file1, "content", StandardCharsets.UTF_8);
			Files.writeString(file2, "content", StandardCharsets.UTF_8);

			// When
			String result = shallowTool.glob("*.txt", tempDir.toString());

			// Then - Should only find root level files
			assertThat(result).contains(file1.toString());
			assertThat(result).doesNotContain(file2.toString());
		}

	}

	@Nested
	@DisplayName("Error Handling Tests")
	class ErrorHandlingTests {

		@Test
		@DisplayName("Should return error for non-existent path")
		void shouldReturnErrorForNonExistentPath() {
			// When
			String result = globTool.glob("*.txt", tempDir.resolve("nonexistent").toString());

			// Then
			assertThat(result).contains("Error: Path does not exist");
		}

		@Test
		@DisplayName("Should return error when path is a file not directory")
		void shouldReturnErrorWhenPathIsFile() throws IOException {
			// Given
			Path file = tempDir.resolve("test.txt");
			Files.writeString(file, "content", StandardCharsets.UTF_8);

			// When
			String result = globTool.glob("*.txt", file.toString());

			// Then
			assertThat(result).contains("Error: Path is not a directory");
		}

	}

	@Nested
	@DisplayName("Special Pattern Tests")
	class SpecialPatternTests {

		@Test
		@DisplayName("Should handle pattern with path prefix")
		void shouldHandlePatternWithPathPrefix() throws IOException {
			// Given
			Path srcDir = tempDir.resolve("src");
			Path testDir = tempDir.resolve("test");
			Files.createDirectories(srcDir);
			Files.createDirectories(testDir);
			Path srcFile = srcDir.resolve("Main.java");
			Path testFile = testDir.resolve("Test.java");
			Files.writeString(srcFile, "code", StandardCharsets.UTF_8);
			Files.writeString(testFile, "code", StandardCharsets.UTF_8);

			// When
			String result = globTool.glob("src/*.java", tempDir.toString());

			// Then
			assertThat(result).contains(srcFile.toString());
			assertThat(result).doesNotContain(testFile.toString());
		}

		@Test
		@DisplayName("Should handle wildcard-only pattern")
		void shouldHandleWildcardPattern() throws IOException {
			// Given
			Path file1 = tempDir.resolve("file1.txt");
			Path file2 = tempDir.resolve("file2.java");
			Path file3 = tempDir.resolve("file3.md");
			Files.writeString(file1, "content", StandardCharsets.UTF_8);
			Files.writeString(file2, "content", StandardCharsets.UTF_8);
			Files.writeString(file3, "content", StandardCharsets.UTF_8);

			// When
			String result = globTool.glob("*", tempDir.toString());

			// Then
			assertThat(result).contains(file1.toString());
			assertThat(result).contains(file2.toString());
			assertThat(result).contains(file3.toString());
		}

		@Test
		@DisplayName("Should handle files with special characters in names")
		void shouldHandleSpecialCharactersInNames() throws IOException {
			// Given
			Path file = tempDir.resolve("test-file_name (1).txt");
			Files.writeString(file, "content", StandardCharsets.UTF_8);

			// When
			String result = globTool.glob("*.txt", tempDir.toString());

			// Then
			assertThat(result).contains(file.toString());
		}

	}

}
