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
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.springaicommunity.agent.common.workspace.Workspace;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.util.Assert;

/**
 * @author Christian Tzolov
 */
public class FileSystemTools {

	private final AllowedDirectories allowedDirectories;

	protected FileSystemTools(List<Path> allowedDirectories) {
		this.allowedDirectories = new AllowedDirectories(allowedDirectories);
	}

	/**
	 * Validates that the given file path is within at least one configured allowed
	 * directory. Shared semantics with GrepTool/GlobTool/ListDirectoryTool — see
	 * {@link AllowedDirectories}.
	 * @param filePath the path to validate
	 * @return an error string if access is denied, or {@code null} if access is allowed
	 */
	private String validateAllowedAccess(String filePath) {
		return this.allowedDirectories.validate(filePath);
	}

	/**
	 * UTF-8 reader with REPLACE error handling: malformed bytes become U+FFFD instead of
	 * aborting the whole read (matching the tolerance of the legacy FileReader while
	 * standardizing the charset).
	 */
	private static BufferedReader lenientUtf8Reader(Path file) throws IOException {
		return new BufferedReader(new InputStreamReader(Files.newInputStream(file),
				StandardCharsets.UTF_8.newDecoder()
					.onMalformedInput(CodingErrorAction.REPLACE)
					.onUnmappableCharacter(CodingErrorAction.REPLACE)));
	}

	/**
	 * Streaming UTF-8 write with REPLACE error handling: unpaired surrogates are
	 * substituted instead of failing the write (matching the legacy FileWriter), and
	 * content is streamed rather than materialized as one encoded byte array.
	 */
	private static void lenientUtf8Write(Path file, String content) throws IOException {
		try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(Files.newOutputStream(file),
				StandardCharsets.UTF_8.newEncoder()
					.onMalformedInput(CodingErrorAction.REPLACE)
					.onUnmappableCharacter(CodingErrorAction.REPLACE)))) {
			writer.write(content);
		}
	}

	// @formatter:off
	@Tool(name = "Read", description = """
		Reads a UTF-8 text file from the local filesystem. The path must be absolute and, when allowed directories are configured, must be within one of them. It is okay to read a file that does not exist; an error will be returned.
		Results are returned using cat -n format, with line numbers starting at 1. Text only: binary files, including PDFs, images, and office documents, are not decoded.

		Usage:
		- By default, it reads up to 2000 lines starting from the beginning of the file
		- You can optionally specify a line offset and limit (especially handy for long files), but it's recommended to read the whole file by not providing these parameters
		- Any lines longer than 2000 characters will be truncated
		- This tool can only read files, not directories
		- If you read a file that exists but has empty contents you will receive a system reminder warning in place of file contents.
		""")
	public String read(
		@ToolParam(description = "The absolute path to the file to read") String filePath,
		@ToolParam(description = "The line number to start reading from. Only provide if the file is too large to read at once", required = false) Integer offset,
		@ToolParam(description = "The number of lines to read. Only provide if the file is too large to read at once.", required = false) Integer limit) { // @formatter:on

		if (filePath == null || filePath.isBlank()) {
			return "Error: file_path must not be empty";
		}

		String accessError = validateAllowedAccess(filePath);
		if (accessError != null) {
			return accessError;
		}

		try {
			Path file = Paths.get(filePath);

			if (!Files.exists(file)) {
				return "Error: File does not exist: " + filePath;
			}

			if (Files.isDirectory(file)) {
				return "Error: Path is a directory, not a file: " + filePath;
			}

			// Default values
			int startLine = offset != null ? offset : 1;
			int maxLines = limit != null ? limit : 2000;

			if (startLine < 1) {
				startLine = 1;
			}

			List<String> lines = new ArrayList<>();
			int currentLine = 0;
			int linesRead = 0;

			boolean limitHit = false;
			try (BufferedReader reader = lenientUtf8Reader(file)) {
				String line;
				while ((line = reader.readLine()) != null) {
					currentLine++;

					// Skip lines before the offset
					if (currentLine < startLine) {
						continue;
					}

					// Stop if we've read enough lines. The line that triggered the stop
					// was already counted, so the file has at least currentLine lines.
					if (linesRead >= maxLines) {
						limitHit = true;
						break;
					}

					// Truncate long lines to 2000 characters
					if (line.length() > 2000) {
						line = line.substring(0, 2000) + "... (line truncated)";
					}

					lines.add(String.format("%6d\t%s", currentLine, line));
					linesRead++;
				}
			}

			if (lines.isEmpty()) {
				if (currentLine == 0) {
					return "File is empty: " + filePath;
				}
				else {
					return String.format("No lines to read. File has %d lines, but offset was %d", currentLine,
							startLine);
				}
			}

			StringBuilder result = new StringBuilder();
			result.append(String.format("File: %s\n", filePath));
			// When the limit stopped the read, currentLine is a lower bound, not the
			// total — say so, or the model assumes the file was fully read.
			result.append(
					String.format(limitHit ? "Showing lines %d-%d of at least %d\n\n" : "Showing lines %d-%d of %d\n\n",
							startLine, startLine + linesRead - 1, currentLine));

			for (String line : lines) {
				result.append(line).append("\n");
			}

			return result.toString();

		}
		catch (InvalidPathException e) {
			return "Error: Invalid path: " + e.getMessage();
		}
		catch (IOException e) {
			return "Error reading file: " + e.getMessage();
		}
	}

	// @formatter:off
	@Tool(name = "Write", description = """
		Writes a file to the local filesystem.

		Usage:
		- This tool will overwrite the existing file if there is one at the provided path.
		- If this is an existing file, you MUST use the Read tool first to read the file's contents. This tool will fail if you did not read the file first.
		- ALWAYS prefer editing existing files in the codebase. NEVER write new files unless explicitly required.
		- NEVER proactively create documentation files (*.md) or README files. Only create documentation files if explicitly requested by the User.
		- Only use emojis if the user explicitly requests it. Avoid writing emojis to files unless asked.	
		""")
	public String write(
		@ToolParam(description = "The absolute path to the file to write (must be absolute, not relative)") String filePath,
		@ToolParam(description = "The content to write to the file") String content) { // @formatter:on

		if (filePath == null || filePath.isBlank()) {
			return "Error: file_path must not be empty";
		}

		String accessError = validateAllowedAccess(filePath);
		if (accessError != null) {
			return accessError;
		}

		try {
			content = content != null ? content : "";

			Path path = Paths.get(filePath);

			// Create parent directories if they don't exist
			Path parentDir = path.getParent();
			if (parentDir != null && !Files.exists(parentDir)) {
				try {
					Files.createDirectories(parentDir);
				}
				catch (IOException e) {
					return "Error: Failed to create parent directories for: " + filePath;
				}
			}

			// Check if file already exists
			boolean fileExists = Files.exists(path);

			// Write content to file
			lenientUtf8Write(path, content);

			if (fileExists) {
				return String.format("Successfully overwrote file: %s (%d bytes)", filePath, content.length());
			}
			else {
				return String.format("Successfully created file: %s (%d bytes)", filePath, content.length());
			}

		}
		catch (IOException e) {
			return "Error writing file: " + e.getMessage();
		}
		catch (Exception e) {
			return "Error: " + e.getMessage();
		}
	}

	// @formatter:off
	@Tool(name = "Edit", description = """
		Performs exact string replacements in files.

		Usage:
		- You must use your `Read` tool at least once in the conversation before editing. This tool will error if you attempt an edit without reading the file.
		- When editing text from Read tool output, ensure you preserve the exact indentation (tabs/spaces) as it appears AFTER the line number prefix. The line number prefix format is: spaces + line number + tab. Everything after that tab is the actual file content to match. Never include any part of the line number prefix in the old_string or new_string.
		- ALWAYS prefer editing existing files in the codebase. NEVER write new files unless explicitly required.
		- Only use emojis if the user explicitly requests it. Avoid adding emojis to files unless asked.
		- The edit will FAIL if `old_string` is not unique in the file. Either provide a larger string with more surrounding context to make it unique or use `replace_all` to change every instance of `old_string`.
		- Use `replace_all` for replacing and renaming strings across the file. This parameter is useful if you want to rename a variable for instance.	
		""")
	public String edit(
		@ToolParam(description = "The absolute path to the file to modify") String filePath,
		@ToolParam(description = "The text to replace") String old_string,
		@ToolParam(description = "The text to replace it with (must be different from old_string)") String new_string,
		@ToolParam(description = "Replace all occurences of old_string (default false)", required = false) Boolean replace_all) { // @formatter:on

		if (filePath == null || filePath.isBlank()) {
			return "Error: file_path must not be empty";
		}

		String accessError = validateAllowedAccess(filePath);
		if (accessError != null) {
			return accessError;
		}

		try {
			Path file = Paths.get(filePath);

			if (!Files.exists(file)) {
				return "Error: File does not exist: " + filePath;
			}

			if (Files.isDirectory(file)) {
				return "Error: Path is a directory, not a file: " + filePath;
			}

			// Validate that old_string and new_string are different
			if (old_string.equals(new_string)) {
				return "Error: old_string and new_string must be different";
			}

			// Read the entire file content preserving exact line endings
			String originalContent;
			try {
				originalContent = Files.readString(file, StandardCharsets.UTF_8);
			}
			catch (IOException e) {
				return "Error reading file content: " + e.getMessage();
			}

			// Count occurrences
			int occurrences = countOccurrences(originalContent, old_string);

			if (occurrences == 0) {
				return "Error: old_string not found in file: " + filePath;
			}

			boolean replaceAll = Boolean.TRUE.equals(replace_all);

			if (!replaceAll && occurrences > 1) {
				return String.format(
						"Error: old_string appears %d times in the file. Either provide a larger string with more surrounding context to make it unique or use replace_all=true to change all instances.",
						occurrences);
			}

			// Perform replacement
			String newContent;
			if (replaceAll) {
				// Replace all occurrences using literal string replacement
				newContent = replaceAll(originalContent, old_string, new_string);
			}
			else {
				// Replace first occurrence only
				newContent = replaceFirst(originalContent, old_string, new_string);
			}

			// Write the modified content back to the file
			lenientUtf8Write(file, newContent);

			// Generate a snippet showing the context around the edit
			String snippet = generateEditSnippet(newContent, new_string);

			// Return formatted response matching Claude Code's Edit tool format
			return String.format(
					"The file %s has been updated. Here's the result of running `cat -n` on a snippet of the edited file:\n%s",
					filePath, snippet);

		}
		catch (InvalidPathException e) {
			return "Error: Invalid path: " + e.getMessage();
		}
		catch (IOException e) {
			return "Error editing file: " + e.getMessage();
		}
	}

	// Helper method to count occurrences of a substring
	private int countOccurrences(String text, String substring) {
		int count = 0;
		int index = 0;
		while ((index = text.indexOf(substring, index)) != -1) {
			count++;
			index += substring.length();
		}
		return count;
	}

	// Helper method to replace first occurrence
	private String replaceFirst(String text, String old_string, String new_string) {
		int index = text.indexOf(old_string);
		if (index == -1) {
			return text;
		}
		return text.substring(0, index) + new_string + text.substring(index + old_string.length());
	}

	// Helper method to replace all occurrences (literal, not regex)
	private String replaceAll(String text, String old_string, String new_string) {
		StringBuilder result = new StringBuilder();
		int index = 0;
		int lastIndex = 0;

		while ((index = text.indexOf(old_string, lastIndex)) != -1) {
			result.append(text, lastIndex, index);
			result.append(new_string);
			lastIndex = index + old_string.length();
		}
		result.append(text.substring(lastIndex));

		return result.toString();
	}

	/**
	 * Generates a formatted snippet of the file showing context around the edited
	 * section. Matches Claude Code's Edit tool output format with line numbers and arrow
	 * separator.
	 * @param fileContent the complete file content after editing
	 * @param newString the new string that was inserted (used to find the edit location)
	 * @return formatted snippet with line numbers
	 */
	private String generateEditSnippet(String fileContent, String newString) {
		String[] lines = fileContent.split("\n", -1);

		// Find the line where the new content appears
		int editStartLine = -1;
		int editEndLine = -1;

		// Split new_string into lines to find where it appears in the file
		String[] newLines = newString.split("\n", -1);

		// Search for the first line of the new content
		for (int i = 0; i < lines.length; i++) {
			if (newLines.length > 0 && lines[i].contains(newLines[0])) {
				// Check if subsequent lines match (for multi-line edits)
				boolean matches = true;
				for (int j = 1; j < newLines.length && i + j < lines.length; j++) {
					if (!lines[i + j].contains(newLines[j])) {
						matches = false;
						break;
					}
				}
				if (matches) {
					editStartLine = i;
					editEndLine = i + newLines.length - 1;
					break;
				}
			}
		}

		// If we didn't find the edit location, show the beginning of the file
		if (editStartLine == -1) {
			editStartLine = 0;
			editEndLine = Math.min(10, lines.length - 1);
		}

		// Show context: ~5 lines before and ~5 lines after the edit
		int contextBefore = 5;
		int contextAfter = 5;
		int startLine = Math.max(0, editStartLine - contextBefore);
		int endLine = Math.min(lines.length - 1, editEndLine + contextAfter);

		// Build the snippet with line numbers (1-indexed, right-aligned with arrow)
		StringBuilder snippet = new StringBuilder();
		for (int i = startLine; i <= endLine; i++) {
			// Line numbers are 1-indexed and right-aligned to 6 characters
			snippet.append(String.format("%6d→%s", i + 1, lines[i]));
			if (i < endLine) {
				snippet.append("\n");
			}
		}

		return snippet.toString();
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private final AllowedDirectories.Collector allowedDirectories = new AllowedDirectories.Collector();

		private Builder() {
		}

		/**
		 * Adds a directory to the list of allowed paths. All file operations are
		 * restricted to the union of configured allowed directories. Symlinks are
		 * resolved to their real path before the check.
		 * @param allowedDirectory the directory to allow operations within
		 * @return this builder
		 */
		public Builder allowedDirectory(Path allowedDirectory) {
			this.allowedDirectories.add(allowedDirectory);
			return this;
		}

		/**
		 * Adds a directory to the list of allowed paths.
		 * @param allowedDirectory the directory path as a string; ignored if {@code null}
		 * @return this builder
		 */
		public Builder allowedDirectory(String allowedDirectory) {
			this.allowedDirectories.add(allowedDirectory);
			return this;
		}

		/**
		 * Adds multiple directories to the list of allowed paths.
		 * @param allowedDirectories the directories to allow operations within
		 * @return this builder
		 */
		public Builder allowedDirectories(Path... allowedDirectories) {
			this.allowedDirectories.addAll(allowedDirectories);
			return this;
		}

		/**
		 * Confines all file operations to the given workspace — shorthand for
		 * {@code allowedDirectory(workspace.root())}.
		 * @param workspace the workspace to operate within
		 * @return this builder
		 */
		public Builder workspace(Workspace workspace) {
			Assert.notNull(workspace, "workspace must not be null");
			this.allowedDirectories.add(workspace.root());
			return this;
		}

		public FileSystemTools build() {
			return new FileSystemTools(this.allowedDirectories.toList());
		}

	}

}
