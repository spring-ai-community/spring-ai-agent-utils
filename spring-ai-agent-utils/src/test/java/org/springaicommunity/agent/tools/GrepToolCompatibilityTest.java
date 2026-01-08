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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springaicommunity.agent.tools.GrepTool;
import org.junit.jupiter.api.condition.EnabledIf;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Compatibility tests to ensure GrepToolPureJava behaves consistently with ripgrep
 * command line tool. These tests verify that the Java implementation produces
 * compatible results with the actual ripgrep binary.
 *
 * @author Christian Tzolov
 * @author Claude Code
 */
@DisplayName("Grep Tool Compatibility Tests")
class GrepToolCompatibilityTest {

	private GrepTool pureJavaTool;

	private boolean ripgrepAvailable;

	@TempDir
	Path tempDir;

	@BeforeEach
	void setUp() {
		this.pureJavaTool = new GrepTool();
		this.ripgrepAvailable = isRipgrepAvailable();
	}

	static boolean isRipgrepAvailable() {
		try {
			ProcessBuilder pb = new ProcessBuilder("rg", "--version");
			Process process = pb.start();
			return process.waitFor() == 0;
		}
		catch (Exception e) {
			return false;
		}
	}

	/**
	 * Execute ripgrep command line directly and return the output
	 */
	private String executeRipgrep(List<String> args) throws IOException, InterruptedException {
		List<String> command = new ArrayList<>();
		command.add("rg");
		command.addAll(args);

		ProcessBuilder processBuilder = new ProcessBuilder(command);
		processBuilder.redirectErrorStream(false);

		Process process = processBuilder.start();

		StringBuilder stdout = new StringBuilder();
		StringBuilder stderr = new StringBuilder();

		Thread stdoutThread = new Thread(() -> {
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
				String line;
				while ((line = reader.readLine()) != null) {
					stdout.append(line).append("\n");
				}
			}
			catch (IOException e) {
				// Ignore
			}
		});

		Thread stderrThread = new Thread(() -> {
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
				String line;
				while ((line = reader.readLine()) != null) {
					stderr.append(line).append("\n");
				}
			}
			catch (IOException e) {
				// Ignore
			}
		});

		stdoutThread.start();
		stderrThread.start();

		boolean completed = process.waitFor(30, TimeUnit.SECONDS);

		if (!completed) {
			process.destroy();
			process.waitFor(5, TimeUnit.SECONDS);
			throw new RuntimeException("ripgrep command timed out");
		}

		stdoutThread.join(1000);
		stderrThread.join(1000);

		int exitCode = process.exitValue();

		// ripgrep returns exit code 1 when no matches found (not an error)
		if (exitCode == 1 || stdout.toString().trim().isEmpty()) {
			return "No matches found";
		}

		if (exitCode >= 2) {
			String errorMsg = stderr.toString().trim();
			if (!errorMsg.isEmpty()) {
				throw new RuntimeException("ripgrep error: " + errorMsg);
			}
			throw new RuntimeException("ripgrep command failed with exit code " + exitCode);
		}

		return stdout.toString().trim();
	}

	@Nested
	@DisplayName("Basic Pattern Matching Compatibility")
	@EnabledIf("org.springaicommunity.agent.tools.GrepToolCompatibilityTest#isRipgrepAvailable")
	class BasicPatternMatchingCompatibility {

		@Test
		@DisplayName("Both tools should find simple patterns in files")
		void shouldFindSimplePatternsConsistently() throws IOException, InterruptedException {
			// Given
			Path file = tempDir.resolve("test.txt");
			Files.writeString(file, "Hello World\nFoo Bar\nHello Again", StandardCharsets.UTF_8);

			// When
			String pureJavaResult = pureJavaTool.grep("Hello", file.toString(), null, null, null, null, null, null,
					null, null, null, null, null);
			String ripGrepResult = executeRipgrep(
					List.of("--color", "never", "--files-with-matches", "Hello", file.toString()));

			// Then - Both should find the file
			assertThat(pureJavaResult).contains(file.toString());
			assertThat(ripGrepResult).contains(file.toString());
		}

		@Test
		@DisplayName("Both tools should return no matches consistently")
		void shouldReturnNoMatchesConsistently() throws IOException, InterruptedException {
			// Given
			Path file = tempDir.resolve("test.txt");
			Files.writeString(file, "Hello World", StandardCharsets.UTF_8);

			// When
			String pureJavaResult = pureJavaTool.grep("NotFound", file.toString(), null, null, null, null, null, null,
					null, null, null, null, null);
			String ripGrepResult = executeRipgrep(
					List.of("--color", "never", "--files-with-matches", "NotFound", file.toString()));

			// Then - Both should indicate no matches
			assertThat(pureJavaResult).contains("No matches found");
			assertThat(ripGrepResult).contains("No matches found");
		}

		@Test
		@DisplayName("Both tools should handle regex patterns consistently")
		void shouldHandleRegexPatternsConsistently() throws IOException, InterruptedException {
			// Given
			Path file = tempDir.resolve("test.txt");
			Files.writeString(file, "Error: Something\nWarning: Other\nError: Again", StandardCharsets.UTF_8);

			// When
			String pureJavaResult = pureJavaTool.grep("Error:.*", file.toString(), null, null, null, null, null, null,
					null, null, null, null, null);
			String ripGrepResult = executeRipgrep(
					List.of("--color", "never", "--files-with-matches", "Error:.*", file.toString()));

			// Then - Both should find matches
			assertThat(pureJavaResult).contains(file.toString());
			assertThat(ripGrepResult).contains(file.toString());
		}

		@Test
		@DisplayName("Both tools should handle case insensitive search consistently")
		void shouldHandleCaseInsensitiveSearchConsistently() throws IOException, InterruptedException {
			// Given
			Path file = tempDir.resolve("test.txt");
			Files.writeString(file, "Hello World", StandardCharsets.UTF_8);

			// When
			String pureJavaResult = pureJavaTool.grep("HELLO", file.toString(), null, null, null, null, null, null,
					true, null, null, null, null);
			String ripGrepResult = executeRipgrep(
					List.of("--color", "never", "--files-with-matches", "--ignore-case", "HELLO", file.toString()));

			// Then - Both should find matches
			assertThat(pureJavaResult).contains(file.toString());
			assertThat(ripGrepResult).contains(file.toString());
		}

		@Test
		@DisplayName("Both tools should be case sensitive by default")
		void shouldBeCaseSensitiveByDefaultConsistently() throws IOException, InterruptedException {
			// Given
			Path file = tempDir.resolve("test.txt");
			Files.writeString(file, "Hello World", StandardCharsets.UTF_8);

			// When
			String pureJavaResult = pureJavaTool.grep("HELLO", file.toString(), null, null, null, null, null, null,
					null, null, null, null, null);
			String ripGrepResult = executeRipgrep(
					List.of("--color", "never", "--files-with-matches", "HELLO", file.toString()));

			// Then - Both should not find matches
			assertThat(pureJavaResult).contains("No matches found");
			assertThat(ripGrepResult).contains("No matches found");
		}

	}

	@Nested
	@DisplayName("Output Mode Compatibility")
	@EnabledIf("org.springaicommunity.agent.tools.GrepToolCompatibilityTest#isRipgrepAvailable")
	class OutputModeCompatibility {

		@Test
		@DisplayName("Both tools should return file paths in files_with_matches mode")
		void shouldReturnFilePathsConsistently() throws IOException, InterruptedException {
			// Given
			Path file1 = tempDir.resolve("file1.txt");
			Path file2 = tempDir.resolve("file2.txt");
			Path file3 = tempDir.resolve("file3.txt");
			Files.writeString(file1, "match", StandardCharsets.UTF_8);
			Files.writeString(file2, "other", StandardCharsets.UTF_8);
			Files.writeString(file3, "match", StandardCharsets.UTF_8);

			// When
			String pureJavaResult = pureJavaTool.grep("match", tempDir.toString(), null,
					GrepTool.OutputMode.files_with_matches, null, null, null, null, null, null, null, null,
					null);
			String ripGrepResult = executeRipgrep(
					List.of("--color", "never", "--files-with-matches", "match", tempDir.toString()));

			// Then - Both should find the same files
			assertThat(pureJavaResult).contains(file1.toString());
			assertThat(pureJavaResult).contains(file3.toString());
			assertThat(pureJavaResult).doesNotContain(file2.toString());

			assertThat(ripGrepResult).contains(file1.toString());
			assertThat(ripGrepResult).contains(file3.toString());
			assertThat(ripGrepResult).doesNotContain(file2.toString());
		}

		@Test
		@DisplayName("Both tools should return match counts in count mode")
		void shouldReturnMatchCountsConsistently() throws IOException, InterruptedException {
			// Given
			Path file1 = tempDir.resolve("file1.txt");
			Path file2 = tempDir.resolve("file2.txt");
			Files.writeString(file1, "Error\nError\nError", StandardCharsets.UTF_8);
			Files.writeString(file2, "Error\nInfo", StandardCharsets.UTF_8);

			// When
			String pureJavaResult = pureJavaTool.grep("Error", tempDir.toString(), null,
					GrepTool.OutputMode.count, null, null, null, null, null, null, null, null, null);
			String ripGrepResult = executeRipgrep(
					List.of("--color", "never", "--count", "Error", tempDir.toString()));

			// Then - Both should show counts for the same files
			assertThat(pureJavaResult).containsPattern(file1.toString() + ":\\d+");
			assertThat(pureJavaResult).containsPattern(file2.toString() + ":\\d+");

			assertThat(ripGrepResult).containsPattern(file1.toString() + ":\\d+");
			assertThat(ripGrepResult).containsPattern(file2.toString() + ":\\d+");
		}

		@Test
		@DisplayName("Both tools should show content with line numbers in content mode")
		void shouldShowContentConsistently() throws IOException, InterruptedException {
			// Given
			Path file = tempDir.resolve("test.txt");
			Files.writeString(file, "Line 1\nLine 2 Error\nLine 3", StandardCharsets.UTF_8);

			// When
			String pureJavaResult = pureJavaTool.grep("Error", file.toString(), null,
					GrepTool.OutputMode.content, null, null, null, true, null, null, null, null, null);
			String ripGrepResult = executeRipgrep(
					List.of("--color", "never", "--line-number", "Error", file.toString()));

			// Then - Both should show the matching line
			assertThat(pureJavaResult).contains("Error");
			assertThat(ripGrepResult).contains("Error");
		}

	}

	@Nested
	@DisplayName("Glob Pattern Compatibility")
	@EnabledIf("org.springaicommunity.agent.tools.GrepToolCompatibilityTest#isRipgrepAvailable")
	class GlobPatternCompatibility {

		@Test
		@DisplayName("Both tools should filter by simple glob pattern")
		void shouldFilterBySimpleGlobConsistently() throws IOException, InterruptedException {
			// Given
			Path javaFile = tempDir.resolve("Test.java");
			Path txtFile = tempDir.resolve("test.txt");
			Files.writeString(javaFile, "public class Test", StandardCharsets.UTF_8);
			Files.writeString(txtFile, "public class Test", StandardCharsets.UTF_8);

			// When
			String pureJavaResult = pureJavaTool.grep("public", tempDir.toString(), "*.java", null, null, null, null,
					null, null, null, null, null, null);
			String ripGrepResult = executeRipgrep(
					List.of("--color", "never", "--files-with-matches", "--glob", "*.java", "public", tempDir.toString()));

			// Then - Both should find only the Java file
			assertThat(pureJavaResult).contains(javaFile.toString());
			assertThat(pureJavaResult).doesNotContain(txtFile.toString());

			assertThat(ripGrepResult).contains(javaFile.toString());
			assertThat(ripGrepResult).doesNotContain(txtFile.toString());
		}

		@Test
		@DisplayName("Both tools should filter by complex glob pattern")
		void shouldFilterByComplexGlobConsistently() throws IOException, InterruptedException {
			// Given
			Path subDir = tempDir.resolve("src");
			Files.createDirectories(subDir);
			Path tsFile = subDir.resolve("test.ts");
			Path tsxFile = subDir.resolve("component.tsx");
			Path jsFile = subDir.resolve("test.js");
			Files.writeString(tsFile, "interface Test {}", StandardCharsets.UTF_8);
			Files.writeString(tsxFile, "const Component = () => {}", StandardCharsets.UTF_8);
			Files.writeString(jsFile, "function test() {}", StandardCharsets.UTF_8);

			// When
			String pureJavaResult = pureJavaTool.grep("interface|Component", tempDir.toString(), "*.{ts,tsx}", null,
					null, null, null, null, null, null, null, null, null);
			String ripGrepResult = executeRipgrep(
					List.of("--color", "never", "--files-with-matches", "--glob", "*.{ts,tsx}", "interface|Component", tempDir.toString()));

			// Then - Both should find TypeScript files
			assertThat(pureJavaResult).contains(tsFile.toString());
			assertThat(pureJavaResult).contains(tsxFile.toString());
			assertThat(pureJavaResult).doesNotContain(jsFile.toString());

			assertThat(ripGrepResult).contains(tsFile.toString());
			assertThat(ripGrepResult).contains(tsxFile.toString());
			assertThat(ripGrepResult).doesNotContain(jsFile.toString());
		}

	}

	@Nested
	@DisplayName("Directory Traversal Compatibility")
	@EnabledIf("org.springaicommunity.agent.tools.GrepToolCompatibilityTest#isRipgrepAvailable")
	class DirectoryTraversalCompatibility {

		@Test
		@DisplayName("Both tools should search recursively in subdirectories")
		void shouldSearchRecursivelyConsistently() throws IOException, InterruptedException {
			// Given
			Path subDir = tempDir.resolve("subdir");
			Files.createDirectory(subDir);
			Path file1 = tempDir.resolve("file1.txt");
			Path file2 = subDir.resolve("file2.txt");
			Files.writeString(file1, "match", StandardCharsets.UTF_8);
			Files.writeString(file2, "match", StandardCharsets.UTF_8);

			// When
			String pureJavaResult = pureJavaTool.grep("match", tempDir.toString(), null, null, null, null, null, null,
					null, null, null, null, null);
			String ripGrepResult = executeRipgrep(
					List.of("--color", "never", "--files-with-matches", "match", tempDir.toString()));

			// Then - Both should find files in subdirectories
			assertThat(pureJavaResult).contains(file1.toString());
			assertThat(pureJavaResult).contains(file2.toString());

			assertThat(ripGrepResult).contains(file1.toString());
			assertThat(ripGrepResult).contains(file2.toString());
		}

		@Test
		@DisplayName("Both tools should ignore .git directory")
		void shouldIgnoreGitDirectoryConsistently() throws IOException, InterruptedException {
			// Given
			Path gitDir = tempDir.resolve(".git");
			Files.createDirectory(gitDir);
			Path file1 = gitDir.resolve("config");
			Path file2 = tempDir.resolve("test.txt");
			Files.writeString(file1, "match", StandardCharsets.UTF_8);
			Files.writeString(file2, "match", StandardCharsets.UTF_8);

			// When
			String pureJavaResult = pureJavaTool.grep("match", tempDir.toString(), null, null, null, null, null, null,
					null, null, null, null, null);
			String ripGrepResult = executeRipgrep(
					List.of("--color", "never", "--files-with-matches", "match", tempDir.toString()));

			// Then - Both should ignore .git directory
			assertThat(pureJavaResult).contains(file2.toString());
			assertThat(pureJavaResult).doesNotContain(".git");

			assertThat(ripGrepResult).contains(file2.toString());
			assertThat(ripGrepResult).doesNotContain(".git");
		}

	}

	@Nested
	@DisplayName("Multiple File Compatibility")
	@EnabledIf("org.springaicommunity.agent.tools.GrepToolCompatibilityTest#isRipgrepAvailable")
	class MultipleFileCompatibility {

		@Test
		@DisplayName("Both tools should find patterns in multiple files")
		void shouldFindPatternsInMultipleFilesConsistently() throws IOException, InterruptedException {
			// Given
			Path file1 = tempDir.resolve("file1.txt");
			Path file2 = tempDir.resolve("file2.txt");
			Path file3 = tempDir.resolve("file3.txt");
			Files.writeString(file1, "Error occurred", StandardCharsets.UTF_8);
			Files.writeString(file2, "No issues", StandardCharsets.UTF_8);
			Files.writeString(file3, "Error found", StandardCharsets.UTF_8);

			// When
			String pureJavaResult = pureJavaTool.grep("Error", tempDir.toString(), null, null, null, null, null, null,
					null, null, null, null, null);
			String ripGrepResult = executeRipgrep(
					List.of("--color", "never", "--files-with-matches", "Error", tempDir.toString()));

			// Then - Both should find the same files
			assertThat(pureJavaResult).contains(file1.toString());
			assertThat(pureJavaResult).contains(file3.toString());
			assertThat(pureJavaResult).doesNotContain(file2.toString());

			assertThat(ripGrepResult).contains(file1.toString());
			assertThat(ripGrepResult).contains(file3.toString());
			assertThat(ripGrepResult).doesNotContain(file2.toString());
		}

		@Test
		@DisplayName("Both tools should count matches across multiple files")
		void shouldCountMatchesAcrossMultipleFilesConsistently() throws IOException, InterruptedException {
			// Given
			Path file1 = tempDir.resolve("file1.txt");
			Path file2 = tempDir.resolve("file2.txt");
			Files.writeString(file1, "Error\nError\nError", StandardCharsets.UTF_8);
			Files.writeString(file2, "Error", StandardCharsets.UTF_8);

			// When
			String pureJavaResult = pureJavaTool.grep("Error", tempDir.toString(), null,
					GrepTool.OutputMode.count, null, null, null, null, null, null, null, null, null);
			String ripGrepResult = executeRipgrep(
					List.of("--color", "never", "--count", "Error", tempDir.toString()));

			// Then - Both should report the same counts
			assertThat(pureJavaResult).contains(file1.toString() + ":3");
			assertThat(pureJavaResult).contains(file2.toString() + ":1");

			assertThat(ripGrepResult).contains(file1.toString() + ":3");
			assertThat(ripGrepResult).contains(file2.toString() + ":1");
		}

	}

	@Nested
	@DisplayName("Edge Cases Compatibility")
	@EnabledIf("org.springaicommunity.agent.tools.GrepToolCompatibilityTest#isRipgrepAvailable")
	class EdgeCasesCompatibility {

		@Test
		@DisplayName("Both tools should handle empty files consistently")
		void shouldHandleEmptyFilesConsistently() throws IOException, InterruptedException {
			// Given
			Path file = tempDir.resolve("empty.txt");
			Files.writeString(file, "", StandardCharsets.UTF_8);

			// When
			String pureJavaResult = pureJavaTool.grep("test", file.toString(), null, null, null, null, null, null,
					null, null, null, null, null);
			String ripGrepResult = executeRipgrep(
					List.of("--color", "never", "--files-with-matches", "test", file.toString()));

			// Then - Both should report no matches
			assertThat(pureJavaResult).contains("No matches found");
			assertThat(ripGrepResult).contains("No matches found");
		}

		@Test
		@DisplayName("Both tools should handle files with special characters in names")
		void shouldHandleSpecialCharactersInFilenamesConsistently() throws IOException, InterruptedException {
			// Given
			Path file = tempDir.resolve("test-file_name (1).txt");
			Files.writeString(file, "match", StandardCharsets.UTF_8);

			// When
			String pureJavaResult = pureJavaTool.grep("match", file.toString(), null, null, null, null, null, null,
					null, null, null, null, null);
			String ripGrepResult = executeRipgrep(
					List.of("--color", "never", "--files-with-matches", "match", file.toString()));

			// Then - Both should find the file
			assertThat(pureJavaResult).contains(file.toString());
			assertThat(ripGrepResult).contains(file.toString());
		}

		@Test
		@DisplayName("Both tools should handle patterns with special regex characters")
		void shouldHandleSpecialRegexCharactersConsistently() throws IOException, InterruptedException {
			// Given
			Path file = tempDir.resolve("test.txt");
			Files.writeString(file, "Test (with) brackets", StandardCharsets.UTF_8);

			// When
			String pureJavaResult = pureJavaTool.grep("\\(with\\)", file.toString(), null, null, null, null, null,
					null, null, null, null, null, null);
			String ripGrepResult = executeRipgrep(
					List.of("--color", "never", "--files-with-matches", "\\(with\\)", file.toString()));

			// Then - Both should find matches
			assertThat(pureJavaResult).contains(file.toString());
			assertThat(ripGrepResult).contains(file.toString());
		}

	}

	@Nested
	@DisplayName("Context Lines Compatibility")
	@EnabledIf("org.springaicommunity.agent.tools.GrepToolCompatibilityTest#isRipgrepAvailable")
	class ContextLinesCompatibility {

		@Test
		@DisplayName("Both tools should show context before matches")
		void shouldShowContextBeforeConsistently() throws IOException, InterruptedException {
			// Given
			Path file = tempDir.resolve("test.txt");
			Files.writeString(file, "Line 1\nLine 2\nLine 3 Error\nLine 4\nLine 5", StandardCharsets.UTF_8);

			// When
			String pureJavaResult = pureJavaTool.grep("Error", file.toString(), null,
					GrepTool.OutputMode.content, 2, null, null, true, null, null, null, null, null);
			String ripGrepResult = executeRipgrep(
					List.of("--color", "never", "--line-number", "-B", "2", "Error", file.toString()));

			// Then - Both should show context lines
			assertThat(pureJavaResult).contains("Line 1");
			assertThat(pureJavaResult).contains("Line 2");
			assertThat(pureJavaResult).contains("Error");

			assertThat(ripGrepResult).contains("Line 1");
			assertThat(ripGrepResult).contains("Line 2");
			assertThat(ripGrepResult).contains("Error");
		}

		@Test
		@DisplayName("Both tools should show context after matches")
		void shouldShowContextAfterConsistently() throws IOException, InterruptedException {
			// Given
			Path file = tempDir.resolve("test.txt");
			Files.writeString(file, "Line 1\nLine 2 Error\nLine 3\nLine 4\nLine 5", StandardCharsets.UTF_8);

			// When
			String pureJavaResult = pureJavaTool.grep("Error", file.toString(), null,
					GrepTool.OutputMode.content, null, 2, null, true, null, null, null, null, null);
			String ripGrepResult = executeRipgrep(
					List.of("--color", "never", "--line-number", "-A", "2", "Error", file.toString()));

			// Then - Both should show context lines
			assertThat(pureJavaResult).contains("Error");
			assertThat(pureJavaResult).contains("Line 3");
			assertThat(pureJavaResult).contains("Line 4");

			assertThat(ripGrepResult).contains("Error");
			assertThat(ripGrepResult).contains("Line 3");
			assertThat(ripGrepResult).contains("Line 4");
		}

	}

	@Nested
	@DisplayName("Fallback Behavior When RipGrep Unavailable")
	@EnabledIf("org.springaicommunity.agent.tools.GrepToolCompatibilityTest#isRipgrepAvailable")
	class FallbackBehavior {

		@Test
		@DisplayName("PureJava tool should work when ripgrep is not available")
		void pureJavaShouldWorkWithoutRipgrep() throws IOException {
			// Given
			Path file = tempDir.resolve("test.txt");
			Files.writeString(file, "Hello World", StandardCharsets.UTF_8);

			// When
			String result = pureJavaTool.grep("Hello", file.toString(), null, null, null, null, null, null, null, null,
					null, null, null);

			// Then - Should work regardless of ripgrep availability
			assertThat(result).contains(file.toString());
		}

		@Test
		@DisplayName("RipGrep tool should return error when ripgrep is not available")
		void ripGrepShouldReportErrorWithoutRipgrep() throws IOException, InterruptedException {
			// This test only makes sense if ripgrep is NOT available
			if (ripgrepAvailable) {
				return; // Skip if ripgrep is available
			}

			// Given
			Path file = tempDir.resolve("test.txt");
			Files.writeString(file, "Hello World", StandardCharsets.UTF_8);

			// When
			String result = executeRipgrep(
					List.of("--color", "never", "--files-with-matches", "Hello", file.toString()));

			// Then - Should report that ripgrep is not available
			assertThat(result).contains("ripgrep (rg) is not installed");
		}

	}

}
