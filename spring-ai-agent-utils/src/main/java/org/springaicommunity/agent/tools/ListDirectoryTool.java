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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * Lists directory contents with optional depth and result limit. Skips common noise
 * directories (.git, node_modules, target, build, etc.).
 */
public class ListDirectoryTool {

	private static final List<String> IGNORED_DIRS = List.of(".git", "node_modules", "target", "build", ".idea",
			".vscode", "dist", "__pycache__", ".gradle", ".mvn");

	private final Path workingDirectory;

	private final AllowedDirectories allowedDirectories;

	protected ListDirectoryTool(Path workingDirectory) {
		this(workingDirectory, List.of());
	}

	protected ListDirectoryTool(Path workingDirectory, List<Path> allowedDirectories) {
		// Operator-supplied config: normalize so the model-input '..' defense in the
		// confinement check does not reject the tool's own working directory.
		this.workingDirectory = (workingDirectory != null) ? workingDirectory.toAbsolutePath().normalize() : null;
		this.allowedDirectories = new AllowedDirectories(allowedDirectories);
	}

	// @formatter:off
	@Tool(name = "ListDirectory", description = """
		List the contents of a directory.
		- Returns files and subdirectories, sorted: directories first, then files, both alphabetically
		- Skips common noise directories: .git, node_modules, target, build, .idea, dist, __pycache__
		- Use `depth` to recurse into subdirectories (default 1 = immediate children only)
		- Use `limit` to cap the number of entries returned (default 50)
		- Prefer this over `bash ls` for simple directory listing — cleaner output, no timestamps or permission bits
		""")
	public String listDirectory(
		@ToolParam(description = "Absolute path to the directory to list. If omitted, lists the working directory.", required = false) String path,
		@ToolParam(description = "How many levels deep to recurse (1 = immediate children only, 2 = one level of subdirs, etc.). Default: 1.", required = false) Integer depth,
		@ToolParam(description = "Maximum number of entries to return. Default: 50.", required = false) Integer limit) { // @formatter:on

		int maxDepth = (depth != null && depth > 0) ? depth : 1;
		int maxResults = (limit != null && limit > 0) ? limit : 50;

		Path targetDir;
		if (path != null && !path.isBlank()) {
			targetDir = Paths.get(path);
		}
		else if (this.workingDirectory != null) {
			targetDir = this.workingDirectory;
		}
		else {
			targetDir = Paths.get(System.getProperty("user.dir"));
		}

		// Confinement check shared with FileSystemTools (see AllowedDirectories)
		String accessError = this.allowedDirectories.validate(targetDir.toString());
		if (accessError != null) {
			return accessError;
		}

		if (!Files.exists(targetDir)) {
			return "Error: Path does not exist: " + targetDir.toAbsolutePath();
		}
		if (!Files.isDirectory(targetDir)) {
			return "Error: Path is not a directory: " + targetDir.toAbsolutePath();
		}

		List<Entry> entries = new ArrayList<>();
		try (Stream<Path> stream = Files.walk(targetDir, maxDepth)) {
			stream.filter(p -> !p.equals(targetDir))
				.filter(p -> !isIgnored(p))
				.limit(maxResults)
				.forEach(p -> entries.add(new Entry(p, Files.isDirectory(p))));
		}
		catch (IOException e) {
			return "Error listing directory: " + e.getMessage();
		}

		if (entries.isEmpty()) {
			return "Directory is empty: " + targetDir.toAbsolutePath();
		}

		entries.sort(Comparator.<Entry, Integer>comparing(e -> e.isDir() ? 0 : 1)
			.thenComparing(e -> e.path().getFileName().toString()));

		StringBuilder sb = new StringBuilder();
		sb.append(targetDir.toAbsolutePath()).append("\n");
		for (Entry e : entries) {
			Path rel = targetDir.relativize(e.path());
			sb.append(e.isDir() ? "  [dir]  " : "  [file] ").append(rel).append("\n");
		}
		if (entries.size() == maxResults) {
			sb.append("  ... (limit of ")
				.append(maxResults)
				.append(" reached — use a larger limit or narrow the path)");
		}
		return sb.toString().stripTrailing();
	}

	private boolean isIgnored(Path path) {
		for (Path part : path) {
			if (IGNORED_DIRS.contains(part.toString())) {
				return true;
			}
		}
		return false;
	}

	private record Entry(Path path, boolean isDir) {
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private Path workingDirectory;

		private final AllowedDirectories.Collector allowedDirectories = new AllowedDirectories.Collector();

		private Builder() {
		}

		public Builder workingDirectory(Path workingDirectory) {
			this.workingDirectory = workingDirectory;
			return this;
		}

		public Builder workingDirectory(String workingDirectory) {
			this.workingDirectory = workingDirectory != null ? Paths.get(workingDirectory) : null;
			return this;
		}

		/**
		 * Adds a directory to the list of allowed paths. When configured, the resolved
		 * target directory (explicit {@code path} argument or the workingDirectory
		 * fallback) must fall within the union of allowed directories — same semantics as
		 * FileSystemTools. Empty (the default) means unrestricted.
		 * @param allowedDirectory the directory to allow listings within
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
		 * @param allowedDirectories the directories to allow listings within
		 * @return this builder
		 */
		public Builder allowedDirectories(Path... allowedDirectories) {
			this.allowedDirectories.addAll(allowedDirectories);
			return this;
		}

		public ListDirectoryTool build() {
			return new ListDirectoryTool(workingDirectory, this.allowedDirectories.toList());
		}

	}

}
