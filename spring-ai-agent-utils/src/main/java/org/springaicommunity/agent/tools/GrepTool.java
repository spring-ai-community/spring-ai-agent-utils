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
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.util.StringUtils;

/**
 * Pure Java grep implementation that doesn't require external ripgrep installation. Uses
 * Java NIO.2 for file traversal and regex Pattern/Matcher for searching.
 *
 * Generated with Claude Code AI assistant.
 *
 * @author Christian Tzolov
 * @author Claude Code
 */
public class GrepTool {

	private final int maxOutputLength;

	private final int maxDepth;

	private final int maxLineLength;

	// File type mappings (common extensions)
	private static final Map<String, String[]> FILE_TYPE_EXTENSIONS = new HashMap<>();
	static {
		FILE_TYPE_EXTENSIONS.put("java", new String[] { "*.java" });
		FILE_TYPE_EXTENSIONS.put("js", new String[] { "*.js", "*.jsx" });
		FILE_TYPE_EXTENSIONS.put("ts", new String[] { "*.ts", "*.tsx" });
		FILE_TYPE_EXTENSIONS.put("py", new String[] { "*.py" });
		FILE_TYPE_EXTENSIONS.put("rust", new String[] { "*.rs" });
		FILE_TYPE_EXTENSIONS.put("go", new String[] { "*.go" });
		FILE_TYPE_EXTENSIONS.put("cpp", new String[] { "*.cpp", "*.cc", "*.cxx", "*.hpp", "*.h" });
		FILE_TYPE_EXTENSIONS.put("c", new String[] { "*.c", "*.h" });
		FILE_TYPE_EXTENSIONS.put("rb", new String[] { "*.rb" });
		FILE_TYPE_EXTENSIONS.put("php", new String[] { "*.php" });
		FILE_TYPE_EXTENSIONS.put("cs", new String[] { "*.cs" });
		FILE_TYPE_EXTENSIONS.put("xml", new String[] { "*.xml" });
		FILE_TYPE_EXTENSIONS.put("json", new String[] { "*.json" });
		FILE_TYPE_EXTENSIONS.put("yaml", new String[] { "*.yaml", "*.yml" });
		FILE_TYPE_EXTENSIONS.put("md", new String[] { "*.md", "*.markdown" });
		FILE_TYPE_EXTENSIONS.put("txt", new String[] { "*.txt" });
		FILE_TYPE_EXTENSIONS.put("sh", new String[] { "*.sh", "*.bash" });
	}

	/**
	 * Default constructor with default values for all configuration parameters.
	 */
	public GrepTool() {
		this(100000, 100, 10000);
	}

	/**
	 * Constructor with configurable parameters.
	 * @param maxOutputLength Maximum output length before truncation (default: 100000)
	 * @param maxDepth Maximum directory traversal depth to prevent infinite recursion
	 * (default: 100)
	 * @param maxLineLength Maximum line length to process, longer lines are skipped
	 * (default: 10000)
	 */
	public GrepTool(int maxOutputLength, int maxDepth, int maxLineLength) {
		this.maxOutputLength = maxOutputLength;
		this.maxDepth = maxDepth;
		this.maxLineLength = maxLineLength;
	}

	/**
	 * Output modes for grep
	 */
	public enum OutputMode {// @formatter:off
		files_with_matches,
		count,
		content

	}// @formatter:on

	// @formatter:off
	@Tool(name = "Grep", description = """
		A powerful search tool built with pure Java (no external dependencies required)

		Usage:
		- ALWAYS use Grep for search tasks. NEVER invoke `grep` or `rg` as a Bash command. The Grep tool has been optimized for correct permissions and access.
		- Supports full regex syntax (e.g., "log.*Error", "function\\s+\\w+")
		- Filter files with glob parameter (e.g., "*.js", "**/*.tsx") or type parameter (e.g., "js", "py", "rust")
		- Output modes: "content" shows matching lines, "files_with_matches" shows only file paths (default), "count" shows match counts
		- Use Task tool for open-ended searches requiring multiple rounds
		- Pattern syntax: Java regex - use standard Java regex escaping
		- Multiline matching: By default patterns match within single lines only. For cross-line patterns, use `multiline: true`

		Note: This is a pure Java implementation that doesn't require ripgrep installation. But it provides similar functionality.
		""")
	public String grep(
		@ToolParam(description = "The regular expression pattern to search for in file contents") String pattern,
		@ToolParam(description = "File or directory to search in. Defaults to current working directory.", required = false) String path,
		@ToolParam(description = "Glob pattern to filter files (e.g. \"*.js\", \"**/*.tsx\")", required = false) String glob,
		@ToolParam(description = "Output mode: \"content\" shows matching lines (supports -A/-B/-C context, -n line numbers, head_limit), \"files_with_matches\" shows file paths (supports head_limit), \"count\" shows match counts (supports head_limit). Defaults to \"files_with_matches\".", required = false) OutputMode outputMode,
		@ToolParam(description = "Number of lines to show before each match. Requires output_mode: \"content\", ignored otherwise.", required = false) Integer contextBefore,
		@ToolParam(description = "Number of lines to show after each match. Requires output_mode: \"content\", ignored otherwise.", required = false) Integer contextAfter,
		@ToolParam(description = "Number of lines to show before and after each match. Requires output_mode: \"content\", ignored otherwise.", required = false) Integer context,
		@ToolParam(description = "Show line numbers in output. Requires output_mode: \"content\", ignored otherwise. Defaults to true.", required = false) Boolean showLineNumbers,
		@ToolParam(description = "Case insensitive search", required = false) Boolean caseInsensitive,
		@ToolParam(description = "File type to search. Common types: js, py, rust, go, java, etc. More efficient than glob for standard file types.", required = false) String type,
		@ToolParam(description = "Limit output to first N lines/entries. Works across all output modes: content (limits output lines), files_with_matches (limits file paths), count (limits count entries). Defaults to 0 (unlimited).", required = false) Integer headLimit,
		@ToolParam(description = "Skip first N lines/entries before applying head_limit. Works across all output modes. Defaults to 0.", required = false) Integer offset,
		@ToolParam(description = "Enable multiline mode where . matches newlines and patterns can span lines. Default: false.", required = false) Boolean multiline) { // @formatter:on

		try {
			// Determine search path
			Path searchPath = StringUtils.hasText(path) ? Paths.get(path) : Paths.get(".");

			if (!Files.exists(searchPath)) {
				return "Error: Path does not exist: " + searchPath.toAbsolutePath();
			}

			// Compile regex pattern
			int flags = Pattern.MULTILINE;
			if (Boolean.TRUE.equals(caseInsensitive)) {
				flags |= Pattern.CASE_INSENSITIVE;
			}
			if (Boolean.TRUE.equals(multiline)) {
				flags |= Pattern.DOTALL;
			}

			Pattern searchPattern;
			try {
				searchPattern = Pattern.compile(pattern, flags);
			}
			catch (Exception e) {
				return "Error: Invalid regex pattern: " + e.getMessage();
			}

			// Determine output mode
			outputMode = outputMode != null ? outputMode : OutputMode.files_with_matches;

			// Build glob matchers
			List<PathMatcher> globMatchers = this.buildGlobMatchers(glob, type);

			// Perform search based on mode
			String result;
			switch (outputMode) {
				case files_with_matches:
					result = this.searchFilesWithMatches(searchPath, searchPattern, globMatchers, headLimit, offset);
					break;
				case count:
					result = this.searchCount(searchPath, searchPattern, globMatchers, headLimit, offset);
					break;
				case content:
					int beforeContext = context != null ? context : (contextBefore != null ? contextBefore : 0);
					int afterContext = context != null ? context : (contextAfter != null ? contextAfter : 0);
					boolean lineNumbers = showLineNumbers == null || showLineNumbers;
					result = this.searchContent(searchPath, searchPattern, globMatchers, beforeContext, afterContext,
							lineNumbers, headLimit, offset);
					break;
				default:
					result = this.searchFilesWithMatches(searchPath, searchPattern, globMatchers, headLimit, offset);
			}

			// Truncate if too long
			if (result.length() > this.maxOutputLength) {
				result = result.substring(0, this.maxOutputLength) + "\n... (output truncated, "
						+ (result.length() - this.maxOutputLength) + " characters omitted)";
			}

			return result;

		}
		catch (Exception e) {
			return "Error executing grep: " + e.getMessage();
		}
	}

	/**
	 * Build glob matchers from glob pattern or file type
	 */
	private List<PathMatcher> buildGlobMatchers(String glob, String type) {
		List<PathMatcher> matchers = new ArrayList<>();

		// Add type-based matchers
		if (StringUtils.hasText(type)) {
			String[] extensions = FILE_TYPE_EXTENSIONS.get(type.toLowerCase());
			if (extensions != null) {
				for (String ext : extensions) {
					matchers.add(FileSystems.getDefault().getPathMatcher("glob:" + ext));
				}
			}
		}

		// Add explicit glob matcher
		if (StringUtils.hasText(glob)) {
			// Handle both simple globs (*.java) and complex globs (**/*.java)
			String globPattern = glob.startsWith("**/") ? glob : "**/" + glob;
			matchers.add(FileSystems.getDefault().getPathMatcher("glob:" + globPattern));
		}

		return matchers;
	}

	/**
	 * Check if a file matches any of the glob matchers
	 */
	private boolean matchesGlob(Path file, List<PathMatcher> matchers) {
		if (matchers.isEmpty()) {
			return true; // No filters, match all files
		}

		for (PathMatcher matcher : matchers) {
			if (matcher.matches(file)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Search for files containing matches (files_with_matches mode)
	 */
	private String searchFilesWithMatches(Path searchPath, Pattern pattern, List<PathMatcher> matchers,
			Integer headLimit, Integer offset) throws IOException {

		List<String> matchingFiles = new ArrayList<>();
		AtomicInteger count = new AtomicInteger(0);
		int skip = offset != null ? offset : 0;
		int limit = headLimit != null && headLimit > 0 ? headLimit : Integer.MAX_VALUE;

		this.processFiles(searchPath, matchers, file -> {
			if (count.get() >= skip + limit) {
				return false; // Stop processing
			}

			if (this.fileContainsPattern(file, pattern)) {
				if (count.getAndIncrement() >= skip) {
					matchingFiles.add(file.toString());
				}
			}
			return true; // Continue processing
		});

		if (matchingFiles.isEmpty()) {
			return "No matches found for pattern: " + pattern.pattern();
		}

		return String.join("\n", matchingFiles);
	}

	/**
	 * Search and count matches per file (count mode)
	 */
	private String searchCount(Path searchPath, Pattern pattern, List<PathMatcher> matchers, Integer headLimit,
			Integer offset) throws IOException {

		Map<String, Integer> fileCounts = new LinkedHashMap<>();
		AtomicInteger fileCount = new AtomicInteger(0);
		int skip = offset != null ? offset : 0;
		int limit = headLimit != null && headLimit > 0 ? headLimit : Integer.MAX_VALUE;

		this.processFiles(searchPath, matchers, file -> {
			if (fileCount.get() >= skip + limit) {
				return false; // Stop processing
			}

			int matches = this.countMatchesInFile(file, pattern);
			if (matches > 0) {
				if (fileCount.getAndIncrement() >= skip) {
					fileCounts.put(file.toString(), matches);
				}
			}
			return true; // Continue processing
		});

		if (fileCounts.isEmpty()) {
			return "No matches found for pattern: " + pattern.pattern();
		}

		StringBuilder result = new StringBuilder();
		for (Map.Entry<String, Integer> entry : fileCounts.entrySet()) {
			result.append(entry.getKey()).append(":").append(entry.getValue()).append("\n");
		}

		return result.toString().trim();
	}

	/**
	 * Search and show matching content with context (content mode)
	 */
	private String searchContent(Path searchPath, Pattern pattern, List<PathMatcher> matchers, int beforeContext,
			int afterContext, boolean lineNumbers, Integer headLimit, Integer offset) throws IOException {

		StringBuilder result = new StringBuilder();
		AtomicInteger lineCount = new AtomicInteger(0);
		int skip = offset != null ? offset : 0;
		int limit = headLimit != null && headLimit > 0 ? headLimit : Integer.MAX_VALUE;

		this.processFiles(searchPath, matchers, file -> {
			if (lineCount.get() >= skip + limit) {
				return false; // Stop processing
			}

			List<String> matches = this.findMatchesWithContext(file, pattern, beforeContext, afterContext, lineNumbers);
			if (!matches.isEmpty()) {
				// Add file header
				result.append(file.toString()).append("\n");

				// Add matches with offset and limit
				for (String match : matches) {
					if (lineCount.get() >= skip + limit) {
						break;
					}
					if (lineCount.getAndIncrement() >= skip) {
						result.append(match).append("\n");
					}
				}
				result.append("\n");
			}
			return lineCount.get() < skip + limit; // Continue if under limit
		});

		if (result.length() == 0) {
			return "No matches found for pattern: " + pattern.pattern();
		}

		return result.toString().trim();
	}

	/**
	 * Process files in the search path
	 */
	private void processFiles(Path searchPath, List<PathMatcher> matchers, FileProcessor processor) throws IOException {
		if (Files.isRegularFile(searchPath)) {
			// Single file
			if (this.matchesGlob(searchPath, matchers)) {
				processor.process(searchPath);
			}
		}
		else if (Files.isDirectory(searchPath)) {
			// Directory traversal
			try (Stream<Path> paths = Files.walk(searchPath, this.maxDepth, FileVisitOption.FOLLOW_LINKS)) {
				paths.filter(Files::isRegularFile)
					.filter(p -> this.matchesGlob(p, matchers))
					.filter(p -> !this.isIgnoredPath(p))
					.anyMatch(file -> !processor.process(file)); // Stop when processor
																	// returns false
			}
		}
	}

	/**
	 * Check if a file should be ignored (common ignore patterns)
	 */
	private boolean isIgnoredPath(Path path) {
		String pathStr = path.toString();
		return pathStr.contains("/.git/") || pathStr.contains("/node_modules/") || pathStr.contains("/target/")
				|| pathStr.contains("/build/") || pathStr.contains("/.idea/") || pathStr.contains("/.vscode/")
				|| pathStr.contains("/dist/") || pathStr.contains("/__pycache__/");
	}

	/**
	 * Check if file contains the pattern
	 */
	private boolean fileContainsPattern(Path file, Pattern pattern) {
		try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.length() > this.maxLineLength)
					continue;
				if (pattern.matcher(line).find()) {
					return true;
				}
			}
		}
		catch (IOException e) {
			// Skip files that can't be read
		}
		return false;
	}

	/**
	 * Count matches in a file
	 */
	private int countMatchesInFile(Path file, Pattern pattern) {
		int count = 0;
		try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.length() > this.maxLineLength)
					continue;
				Matcher matcher = pattern.matcher(line);
				while (matcher.find()) {
					count++;
				}
			}
		}
		catch (IOException e) {
			// Skip files that can't be read
		}
		return count;
	}

	/**
	 * Find matches with context lines
	 */
	private List<String> findMatchesWithContext(Path file, Pattern pattern, int beforeContext, int afterContext,
			boolean lineNumbers) {
		List<String> results = new ArrayList<>();

		try {
			List<String> allLines = Files.readAllLines(file, StandardCharsets.UTF_8);
			List<Integer> matchingLineNumbers = new ArrayList<>();

			// Find all matching line numbers
			for (int i = 0; i < allLines.size(); i++) {
				String line = allLines.get(i);
				if (line.length() > this.maxLineLength)
					continue;
				if (pattern.matcher(line).find()) {
					matchingLineNumbers.add(i);
				}
			}

			// Extract matches with context
			for (int matchLineNum : matchingLineNumbers) {
				int start = Math.max(0, matchLineNum - beforeContext);
				int end = Math.min(allLines.size() - 1, matchLineNum + afterContext);

				for (int i = start; i <= end; i++) {
					String prefix = "";
					if (lineNumbers) {
						prefix = (i + 1) + ":";
					}
					if (i == matchLineNum) {
						prefix += "  "; // Indicate matching line
					}
					else {
						prefix += "- "; // Indicate context line
					}
					results.add(prefix + allLines.get(i));
				}

				// Add separator between match groups
				if (!matchingLineNumbers.isEmpty()) {
					results.add("--");
				}
			}

			// Remove trailing separator
			if (!results.isEmpty() && results.get(results.size() - 1).equals("--")) {
				results.remove(results.size() - 1);
			}

		}
		catch (IOException e) {
			// Skip files that can't be read
		}

		return results;
	}

	/**
	 * Functional interface for file processing
	 */
	@FunctionalInterface
	private interface FileProcessor {

		/**
		 * Process a file
		 * @return true to continue processing, false to stop
		 */
		boolean process(Path file);

	}

}
