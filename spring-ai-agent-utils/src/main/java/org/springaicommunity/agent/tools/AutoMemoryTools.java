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
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Tools for managing persistent memory files in a dedicated memories directory.
 * Implements the memory tool specification from the Claude platform, providing
 * view, create, str_replace, insert, delete, and rename operations scoped to a
 * configurable memories directory.
 *
 * @author Christian Tzolov
 */
public class AutoMemoryTools {

	private final Path memoriesDir;

	protected AutoMemoryTools(Path memoriesDir) {
		Assert.notNull(memoriesDir, "memoriesDir must not be null");
		this.memoriesDir = memoriesDir.normalize();
	}

	public Path getMemoriesDir() {
		return this.memoriesDir;
	}

	// @formatter:off
	@Tool(name = "MemoryView", description = """
		View the contents of a file or list the contents of a directory in the persistent memory store.

		Usage:
		- If path points to a directory: lists it two levels deep showing file sizes.
		- If path points to a file: returns its contents with line numbers.
		- All paths are relative to the memories root directory.
		- Use an empty path or "/" to inspect the root, which shows all memory files.
		- Start by viewing "MEMORY.md" — it is the always-loaded index of all memory entries and should
		  be consulted before reading or writing any memory.
		- Optionally supply a line range 'start,end' to page through large files.

		Memory file structure: each memory file uses YAML frontmatter:
		  ---
		  name: <short name>
		  description: <one-line description used to judge relevance in future conversations>
		  type: <user | feedback | project | reference>
		  ---
		  <memory content>

		Memory types:
		- user     — user's role, goals, expertise, preferences
		- feedback — guidance about how to work (corrections AND validated approaches)
		- project  — ongoing work, decisions, deadlines not derivable from code or git
		- reference — pointers to external systems (Linear projects, dashboards, channels)
		""")
	public String memoryView(
		@ToolParam(description = "Path to the file or directory to view, relative to the memories root. Use empty string or '/' for the root. Use 'MEMORY.md' to read the index.") String path,
		@ToolParam(description = "Optional line range as 'start,end' (e.g. '1,50') when viewing a file. Ignored for directories.", required = false) String viewRange) { // @formatter:on

		try {
			Path target = resolveSafePath(path);

			if (!Files.exists(target)) {
				return "Error: Path does not exist: " + path;
			}

			if (Files.isDirectory(target)) {
				return listDirectory(target, path);
			}
			else {
				return readFile(target, viewRange);
			}
		}
		catch (SecurityException e) {
			return "Error: " + e.getMessage();
		}
		catch (IOException e) {
			return "Error reading path: " + e.getMessage();
		}
	}

	// @formatter:off
	@Tool(name = "MemoryCreate", description = """
		Create a new file in the persistent memory store.

		Usage:
		- All paths are relative to the memories root directory.
		- The file must NOT already exist; use MemoryStrReplace to update existing files.
		- Parent directories are created automatically if they do not exist.
		- Saving a memory is a TWO-STEP process:
		    Step 1 — call MemoryCreate to write the memory file with the frontmatter format below.
		    Step 2 — call MemoryStrReplace (or MemoryInsert) to add a pointer line to MEMORY.md.
		            MEMORY.md entry format: "- [Title](filename.md) — one-line hook (≤150 chars)"
		- Always check MEMORY.md first (via MemoryView) to avoid duplicate memories.
		- Do NOT save: code patterns, git history, fix recipes, items in CLAUDE.md, or ephemeral state.

		Memory file frontmatter format:
		  ---
		  name: <short name>
		  description: <one-line description used to judge relevance in future conversations>
		  type: <user | feedback | project | reference>
		  ---
		  <memory content>

		For feedback/project types, structure the body as:
		  <rule or fact>
		  **Why:** <reason — past incident, constraint, or preference>
		  **How to apply:** <when this kicks in>
		""")
	public String memoryCreate(
		@ToolParam(description = "Path for the new file, relative to the memories root (e.g. 'feedback_testing.md'). Use descriptive names that reflect the topic.") String path,
		@ToolParam(description = "Full file content including the YAML frontmatter block followed by the memory body.") String fileText) { // @formatter:on

		try {
			Path target = resolveSafePath(path);

			if (Files.exists(target)) {
				return "Error: File already exists: " + path + ". Use MemoryStrReplace to modify existing files.";
			}

			Path parent = target.getParent();
			if (parent != null && !Files.exists(parent)) {
				Files.createDirectories(parent);
			}

			Files.writeString(target, fileText != null ? fileText : "", StandardCharsets.UTF_8);

			return "Successfully created file: " + path + " (" + (fileText != null ? fileText.length() : 0) + " bytes)";
		}
		catch (SecurityException e) {
			return "Error: " + e.getMessage();
		}
		catch (IOException e) {
			return "Error creating file: " + e.getMessage();
		}
	}

	// @formatter:off
	@Tool(name = "MemoryStrReplace", description = """
		Replace an exact string in an existing memory file.

		Usage:
		- All paths are relative to the memories root directory.
		- old_str must match exactly (including whitespace and newlines) and must appear exactly once.
		- If old_str appears more than once the edit is rejected — include more surrounding context to disambiguate.
		- new_str can be empty to delete the matched text.
		- Returns a snippet of the file around the edited location with line numbers.

		Common uses:
		- Updating stale memory content in a memory file (change the body, update the frontmatter description).
		- Updating the MEMORY.md index when a memory file is renamed or its description changes.
		- Removing an entry from MEMORY.md when deleting a memory file (use MemoryDelete for the file itself).
		- Keeping the `name` and `description` frontmatter fields in sync after editing memory content.
		""")
	public String memoryStrReplace(
		@ToolParam(description = "Path to the file to edit, relative to the memories root. Use 'MEMORY.md' to update the index.") String path,
		@ToolParam(description = "The exact text to find and replace. Must appear exactly once in the file.") String oldStr,
		@ToolParam(description = "The replacement text. Use empty string to delete the matched text.") String newStr) { // @formatter:on

		try {
			Path target = resolveSafePath(path);

			if (!Files.exists(target)) {
				return "Error: File does not exist: " + path;
			}

			if (Files.isDirectory(target)) {
				return "Error: Path is a directory, not a file: " + path;
			}

			String content = Files.readString(target, StandardCharsets.UTF_8);
			int occurrences = countOccurrences(content, oldStr);

			if (occurrences == 0) {
				return "Error: old_str not found in file: " + path;
			}

			if (occurrences > 1) {
				return String.format(
						"Error: old_str appears %d times in the file. Provide more surrounding context to make it unique.",
						occurrences);
			}

			String replacement = newStr != null ? newStr : "";
			String updated = replaceFirst(content, oldStr, replacement);

			Files.writeString(target, updated, StandardCharsets.UTF_8);

			if (!StringUtils.hasText(replacement)) {
				return String.format("Successfully deleted matched text from %s.", path);
			}
			String snippet = generateEditSnippet(updated, replacement);
			return String.format("Successfully edited %s. Here's a snippet of the result:\n%s", path, snippet);
		}
		catch (SecurityException e) {
			return "Error: " + e.getMessage();
		}
		catch (IOException e) {
			return "Error editing file: " + e.getMessage();
		}
	}

	// @formatter:off
	@Tool(name = "MemoryInsert", description = """
		Insert text at a specific line number in an existing memory file.

		Usage:
		- All paths are relative to the memories root directory.
		- insert_line is the line number AFTER which the new text is inserted (0 inserts at the beginning).
		- Lines are 1-indexed. Providing insert_line equal to the total line count appends to the end.
		- The inserted text should end with a newline if it is to appear as a separate line.

		Common uses:
		- Appending a new pointer line to MEMORY.md after creating a memory file (Step 2 of the two-step save).
		  MEMORY.md entry format: "- [Title](filename.md) — one-line hook (≤150 chars)"
		  Read MEMORY.md first (via MemoryView) to get the current line count, then append at the last line.
		- Inserting new sections into a memory file without replacing existing content.
		""")
	public String memoryInsert(
		@ToolParam(description = "Path to the file to modify, relative to the memories root. Use 'MEMORY.md' to append an index entry.") String path,
		@ToolParam(description = "The line number after which to insert the text. Use 0 to insert before the first line. Pass the total line count to append at the end.") Integer insertLine,
		@ToolParam(description = "The text to insert. For MEMORY.md entries use: '- [Title](filename.md) — one-line hook'") String insertText) { // @formatter:on

		try {
			Path target = resolveSafePath(path);

			if (!Files.exists(target)) {
				return "Error: File does not exist: " + path;
			}

			if (Files.isDirectory(target)) {
				return "Error: Path is a directory, not a file: " + path;
			}

			if (insertLine == null || insertLine < 0) {
				return "Error: insert_line must be a non-negative integer";
			}

			List<String> lines = Files.readAllLines(target, StandardCharsets.UTF_8);

			if (insertLine > lines.size()) {
				return String.format("Error: insert_line %d exceeds file length of %d lines", insertLine, lines.size());
			}

			// Detect whether the original file ends with a newline so we can restore it.
			String originalContent = Files.readString(target, StandardCharsets.UTF_8);
			boolean trailingNewline = originalContent.endsWith("\n");

			lines.add(insertLine, insertText != null ? insertText : "");

			String updated = String.join("\n", lines) + (trailingNewline ? "\n" : "");
			Files.writeString(target, updated, StandardCharsets.UTF_8);

			return "Successfully inserted text at line " + insertLine + " in: " + path;
		}
		catch (SecurityException e) {
			return "Error: " + e.getMessage();
		}
		catch (IOException e) {
			return "Error inserting into file: " + e.getMessage();
		}
	}

	// @formatter:off
	@Tool(name = "MemoryDelete", description = """
		Delete a file or directory (and all its contents) from the memories storage.

		Usage:
		- All paths are relative to the memories root directory.
		- Deleting a directory removes it and all files/subdirectories within it recursively.
		- This operation is irreversible; use with caution.
		- The memories root directory itself cannot be deleted.
		- After deleting a memory file, always remove its corresponding entry from MEMORY.md
		  using MemoryStrReplace to keep the index accurate.
		- Use when a memory is confirmed stale, wrong, or superseded — do not just leave outdated entries.
		""")
	public String memoryDelete(
		@ToolParam(description = "Path to the file or directory to delete, relative to the memories root. Remember to also remove its MEMORY.md entry afterwards.") String path) { // @formatter:on

		try {
			Path target = resolveSafePath(path);

			// Prevent deleting the memories root itself
			if (target.equals(this.memoriesDir)) {
				return "Error: Cannot delete the memories root directory.";
			}

			if (!Files.exists(target)) {
				return "Error: Path does not exist: " + path;
			}

			if (Files.isDirectory(target)) {
				try (Stream<Path> walk = Files.walk(target)) {
					walk.sorted(Comparator.reverseOrder()).forEach(p -> {
						try {
							Files.delete(p);
						}
						catch (IOException e) {
							throw new RuntimeException("Failed to delete: " + p, e);
						}
					});
				}
				return "Successfully deleted directory: " + path;
			}
			else {
				Files.delete(target);
				return "Successfully deleted file: " + path;
			}
		}
		catch (SecurityException e) {
			return "Error: " + e.getMessage();
		}
		catch (RuntimeException e) {
			return "Error deleting path: " + e.getMessage();
		}
		catch (IOException e) {
			return "Error deleting path: " + e.getMessage();
		}
	}

	// @formatter:off
	@Tool(name = "MemoryRename", description = """
		Rename or move a file or directory within the memories storage.

		Usage:
		- Both paths are relative to the memories root directory.
		- The source path must exist; the destination path must NOT already exist.
		- Parent directories of the destination are created automatically if needed.
		- Can be used to reorganize memories by moving files between subdirectories.
		- After renaming a memory file, update its pointer in MEMORY.md using MemoryStrReplace
		  so the index link stays correct.
		""")
	public String memoryRename(
		@ToolParam(description = "Current path of the file or directory, relative to the memories root.") String oldPath,
		@ToolParam(description = "New path for the file or directory, relative to the memories root. Remember to update the MEMORY.md link afterwards.") String newPath) { // @formatter:on

		try {
			Path source = resolveSafePath(oldPath);
			Path destination = resolveSafePath(newPath);

			if (!Files.exists(source)) {
				return "Error: Source path does not exist: " + oldPath;
			}

			if (Files.exists(destination)) {
				return "Error: Destination path already exists: " + newPath;
			}

			Path destParent = destination.getParent();
			if (destParent != null && !Files.exists(destParent)) {
				Files.createDirectories(destParent);
			}

			Files.move(source, destination);

			return String.format("Successfully renamed '%s' to '%s'", oldPath, newPath);
		}
		catch (SecurityException e) {
			return "Error: " + e.getMessage();
		}
		catch (IOException e) {
			return "Error renaming path: " + e.getMessage();
		}
	}

	/**
	 * Resolves a user-supplied relative path against the memories directory,
	 * guarding against path traversal attacks and absolute path injection.
	 */
	private Path resolveSafePath(String relativePath) {
		if (!StringUtils.hasText(relativePath) || relativePath.equals("/")) {
			return this.memoriesDir;
		}
		Path userPath = Paths.get(relativePath);
		if (userPath.isAbsolute()) {
			throw new SecurityException("Absolute paths are not allowed: '" + relativePath + "'");
		}
		Path resolved = this.memoriesDir.resolve(userPath).normalize();
		if (!resolved.startsWith(this.memoriesDir)) {
			throw new SecurityException(
					"Path traversal attempt detected: '" + relativePath + "' escapes the memories directory");
		}
		return resolved;
	}

	/**
	 * Lists a directory up to two levels deep, showing file sizes.
	 */
	private String listDirectory(Path dir, String displayPath) throws IOException {
		StringBuilder sb = new StringBuilder();
		sb.append("Contents of ").append(displayPath.isEmpty() ? "/" : displayPath).append(":\n\n");

		try (Stream<Path> level1 = Files.list(dir)) {
			List<Path> entries = level1.sorted(Comparator.comparing(p -> p.getFileName().toString())).toList();
			for (Path entry : entries) {
				String name = entry.getFileName().toString();
				if (Files.isDirectory(entry)) {
					sb.append("  ").append(name).append("/\n");
					try (Stream<Path> level2 = Files.list(entry)) {
						List<Path> subEntries = level2
							.sorted(Comparator.comparing(p -> p.getFileName().toString()))
							.toList();
						for (Path sub : subEntries) {
							String subName = sub.getFileName().toString();
							if (Files.isDirectory(sub)) {
								sb.append("    ").append(subName).append("/\n");
							}
							else {
								long size = Files.size(sub);
								sb.append("    ").append(subName).append(" (").append(size).append(" bytes)\n");
							}
						}
					}
				}
				else {
					long size = Files.size(entry);
					sb.append("  ").append(name).append(" (").append(size).append(" bytes)\n");
				}
			}
		}
		return sb.toString();
	}

	/**
	 * Reads a file and returns its contents with line numbers, optionally limited to a
	 * line range specified as "start,end".
	 */
	private String readFile(Path file, String viewRange) throws IOException {
		List<String> allLines = Files.readAllLines(file, StandardCharsets.UTF_8);
		int totalLines = allLines.size();

		int startLine = 1;
		int endLine = totalLines;

		if (StringUtils.hasText(viewRange)) {
			String[] parts = viewRange.split(",");
			if (parts.length == 2) {
				try {
					startLine = Math.max(1, Integer.parseInt(parts[0].trim()));
					endLine = Math.min(totalLines, Integer.parseInt(parts[1].trim()));
				}
				catch (NumberFormatException e) {
					return "Error: view_range must be 'start,end' integers (e.g. '1,50')";
				}
			}
			else {
				return "Error: view_range must be 'start,end' (e.g. '1,50')";
			}
		}

		StringBuilder sb = new StringBuilder();
		sb.append(String.format("File: %s\nLines %d-%d of %d\n\n", file.getFileName(), startLine, endLine,
				totalLines));

		for (int i = startLine - 1; i < endLine; i++) {
			sb.append(String.format("%6d\t%s\n", i + 1, allLines.get(i)));
		}

		return sb.toString();
	}

	private int countOccurrences(String text, String substring) {
		int count = 0;
		int index = 0;
		while ((index = text.indexOf(substring, index)) != -1) {
			count++;
			index += substring.length();
		}
		return count;
	}

	private String replaceFirst(String text, String oldStr, String newStr) {
		int index = text.indexOf(oldStr);
		if (index == -1) {
			return text;
		}
		return text.substring(0, index) + newStr + text.substring(index + oldStr.length());
	}

	private String generateEditSnippet(String fileContent, String newStr) {
		String[] lines = fileContent.split("\n", -1);
		String[] newLines = newStr.split("\n", -1);

		int editStartLine = -1;
		int editEndLine = -1;

		for (int i = 0; i < lines.length; i++) {
			if (newLines.length > 0 && lines[i].contains(newLines[0])) {
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

		if (editStartLine == -1) {
			editStartLine = 0;
			editEndLine = Math.min(10, lines.length - 1);
		}

		int startLine = Math.max(0, editStartLine - 5);
		int endLine = Math.min(lines.length - 1, editEndLine + 5);

		StringBuilder snippet = new StringBuilder();
		for (int i = startLine; i <= endLine; i++) {
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

		private Path memoriesDir = Paths.get("/memories");

		private Builder() {
		}

		/**
		 * Set the root directory where all memory files are stored.
		 * Defaults to {@code /memories}.
		 * @param memoriesDir the memories root directory
		 * @return this builder
		 */
		public Builder memoriesDir(Path memoriesDir) {
			this.memoriesDir = memoriesDir;
			return this;
		}

		/**
		 * Set the root directory where all memory files are stored using a string path.
		 * @param memoriesDir the memories root directory as string
		 * @return this builder
		 */
		public Builder memoriesDir(String memoriesDir) {
			this.memoriesDir = memoriesDir != null ? Paths.get(memoriesDir) : Paths.get("/memories");
			return this;
		}

		public AutoMemoryTools build() {
			try {
				Files.createDirectories(memoriesDir);
			}
			catch (IOException e) {
				throw new IllegalStateException("Failed to create memories directory: " + memoriesDir, e);
			}
			return new AutoMemoryTools(memoriesDir);
		}

	}

}
