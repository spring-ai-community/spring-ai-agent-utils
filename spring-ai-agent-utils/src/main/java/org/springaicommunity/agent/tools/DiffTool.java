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
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.BiPredicate;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * Spring AI tool that produces unified-diff output between two text blocks using
 * Eugene W. Myers' O(ND) difference algorithm. Output is compatible with the
 * {@code patch} utility and {@code git apply}.
 *
 * @author Martin Vlčák
 */
public class DiffTool {

	private static final String NO_NEWLINE_MARKER = "\\ No newline at end of file";

	private static final int DEFAULT_CONTEXT = 3;

	private static final int MAX_CONTEXT = 10;

	private record EditScript<T>(List<EditOp<T>> ops) {

		public EditScript {
			ops = List.copyOf(ops);
		}

		public long insertions() {
			return this.ops.stream().filter(o -> o instanceof EditOp.Insert<T>).count();
		}

		public long deletions() {
			return this.ops.stream().filter(o -> o instanceof EditOp.Delete<T>).count();
		}

	}

	private sealed interface EditOp<T> permits EditOp.Equal, EditOp.Delete, EditOp.Insert {

		T value();

		record Equal<T>(T value) implements EditOp<T> {
		}

		record Delete<T>(T value) implements EditOp<T> {
		}

		record Insert<T>(T value) implements EditOp<T> {
		}

	}

	private record Hunk(int beforeStart, int beforeCount, int afterStart, int afterCount, List<EditOp<String>> ops) {

		public Hunk {
			ops = List.copyOf(ops);
		}

	}

	private List<Hunk> buildHunk(EditScript<String> script, int contextLines) {
		List<EditOp<String>> ops = script.ops();
		int n = ops.size();
		if (n == 0) {
			return List.of();
		}

		// Precompute 1-based line numbers for each op position (and one past the end).
		int[] beforeLines = new int[n + 1];
		int[] afterLines = new int[n + 1];
		int bl = 1;
		int al = 1;
		for (int i = 0; i < n; i++) {
			beforeLines[i] = bl;
			afterLines[i] = al;
			EditOp<String> op = ops.get(i);
			if (op instanceof EditOp.Equal<String>) {
				bl++;
				al++;
			}
			else if (op instanceof EditOp.Delete<String>) {
				bl++;
			}
			else if (op instanceof EditOp.Insert<String>) {
				al++;
			}
		}
		beforeLines[n] = bl;
		afterLines[n] = al;

		List<Hunk> hunks = new ArrayList<>();
		int i = 0;

		while (i < n) {
			// Skip forward to the next non-Equal op.
			while (i < n && ops.get(i) instanceof EditOp.Equal<String>) {
				i++;
			}
			if (i >= n) {
				break;
			}

			int hunkStart = Math.max(0, i - contextLines);
			int lastChangeIdx = i;
			int j = i + 1;

			while (j < n) {
				EditOp<String> op = ops.get(j);
				if (!(op instanceof EditOp.Equal<String>)) {
					lastChangeIdx = j;
					j++;
					continue;
				}
				int runStart = j;
				while (j < n && ops.get(j) instanceof EditOp.Equal<String>) {
					j++;
				}
				int runLen = j - runStart;
				if (j >= n || runLen >= 2 * contextLines + 1) {
					break;
				}
				// Short run — absorbed into the current hunk; keep extending.
			}

			int hunkEndExclusive = Math.min(n, lastChangeIdx + 1 + contextLines);

			List<EditOp<String>> hunkOps = new ArrayList<>(ops.subList(hunkStart, hunkEndExclusive));
			int bStart = beforeLines[hunkStart];
			int aStart = afterLines[hunkStart];
			int bCount = beforeLines[hunkEndExclusive] - bStart;
			int aCount = afterLines[hunkEndExclusive] - aStart;

			hunks.add(new Hunk(bStart, bCount, aStart, aCount, hunkOps));
			i = hunkEndExclusive;
		}

		return hunks;
	}

	private String formatUnifiedDiff(List<Hunk> hunks, String beforeLabel, String afterLabel, boolean beforeEndsWithNewline,
									boolean afterEndsWithNewline, String terminator) {
		if (hunks.isEmpty()) {
			return "";
		}

		StringBuilder sb = new StringBuilder();
		sb.append("--- ").append(beforeLabel).append(terminator);
		sb.append("+++ ").append(afterLabel).append(terminator);

		int lastHunkIdx = hunks.size() - 1;
		for (int hi = 0; hi < hunks.size(); hi++) {
			Hunk h = hunks.get(hi);
			appendHunkHeader(sb, h, terminator);

			int opsSize = h.ops().size();
			int lastBeforeLineIdx = lastOpIndexOnSide(h.ops(), true);
			int lastAfterLineIdx = lastOpIndexOnSide(h.ops(), false);
			boolean isLastHunk = (hi == lastHunkIdx);

			for (int i = 0; i < opsSize; i++) {
				EditOp<String> op = h.ops().get(i);
				if (op instanceof EditOp.Equal<String> eq) {
					sb.append(' ').append(eq.value()).append(terminator);
				}
				else if (op instanceof EditOp.Delete<String> del) {
					sb.append('-').append(del.value()).append(terminator);
				}
				else if (op instanceof EditOp.Insert<String> ins) {
					sb.append('+').append(ins.value()).append(terminator);
				}

				if (isLastHunk) {
					boolean needBefore = (i == lastBeforeLineIdx) && !beforeEndsWithNewline;
					boolean needAfter = (i == lastAfterLineIdx) && !afterEndsWithNewline;
					if (needBefore || needAfter) {
						sb.append(NO_NEWLINE_MARKER).append(terminator);
					}
				}
			}
		}

		return sb.toString();
	}

	private void appendHunkHeader(StringBuilder sb, Hunk h, String terminator) {
		sb.append("@@ -");
		appendRange(sb, h.beforeStart(), h.beforeCount());
		sb.append(" +");
		appendRange(sb, h.afterStart(), h.afterCount());
		sb.append(" @@").append(terminator);
	}

	private void appendRange(StringBuilder sb, int start, int count) {
		if (count == 0) {
			sb.append(start - 1).append(",0");
		}
		else if (count == 1) {
			sb.append(start);
		}
		else {
			sb.append(start).append(',').append(count);
		}
	}

	private int lastOpIndexOnSide(List<EditOp<String>> ops, boolean beforeSide) {
		for (int i = ops.size() - 1; i >= 0; i--) {
			EditOp<String> op = ops.get(i);
			if (op instanceof EditOp.Equal<String>) {
				return i;
			}
			if (beforeSide && op instanceof EditOp.Delete<String>) {
				return i;
			}
			if (!beforeSide && op instanceof EditOp.Insert<String>) {
				return i;
			}
		}
		return -1;
	}

	private String renderDiff(String before, String after, String beforeLabel, String afterLabel, int contextLines,
	                          WhitespaceMode ws) {
		Tokenized bt = tokenize(before);
		Tokenized at = tokenize(after);
		EditScript<String> script = diff(bt.lines(), at.lines(), ws.equator());
		script = forceTrailingChangeIfNewlineMismatch(script, bt, at);
		List<Hunk> hunks = buildHunk(script, contextLines);
		String term = bt.lines().isEmpty() ? at.dominantTerminator() : bt.dominantTerminator();
		return formatUnifiedDiff(hunks, beforeLabel, afterLabel,
				bt.endsWithNewline(), at.endsWithNewline(), term);
	}

	private EditScript<String> forceTrailingChangeIfNewlineMismatch(EditScript<String> script,
	                                                                Tokenized bt, Tokenized at) {
		if (bt.endsWithNewline() == at.endsWithNewline()) {
			return script;
		}
		if (bt.lines().isEmpty() || at.lines().isEmpty()) {
			return script;
		}
		List<EditOp<String>> ops = script.ops();
		if (ops.isEmpty()) {
			return script;
		}
		EditOp<String> last = ops.get(ops.size() - 1);
		if (!(last instanceof EditOp.Equal<String> eq)) {
			return script;
		}
		List<EditOp<String>> rewritten = new ArrayList<>(ops);
		rewritten.remove(rewritten.size() - 1);
		rewritten.add(new EditOp.Delete<>(eq.value()));
		rewritten.add(new EditOp.Insert<>(eq.value()));
		return new EditScript<>(rewritten);
	}

	private EditScript<String> diff(List<String> before, List<String> after, BiPredicate<String, String> equator) {

		int n = before.size();
		int m = after.size();

		// Edge case: both empty — nothing to do.
		if (n == 0 && m == 0) {
			return new EditScript<>(List.of());
		}

		int max = n + m;
		int[] v = new int[2 * max + 1];
		List<int[]> trace = new ArrayList<>();

		for (int d = 0; d <= max; d++) {
			trace.add(v.clone());
			for (int k = -d; k <= d; k += 2) {
				int x;
				if (k == -d || (k != d && v[k - 1 + max] < v[k + 1 + max])) {
					x = v[k + 1 + max]; // insertion (move down)
				}
				else {
					x = v[k - 1 + max] + 1; // deletion (move right)
				}
				int y = x - k;
				while (x < n && y < m && equator.test(before.get(x), after.get(y))) {
					x++;
					y++;
				}
				v[k + max] = x;
				if (x >= n && y >= m) {
					return backtrack(before, after, trace, n, m, max);
				}
			}
		}
		return new EditScript<>(List.of());  // unreachable: Myers always terminates
	}

	private EditScript<String> backtrack(List<String> before, List<String> after, List<int[]> trace, int n, int m, int max) {
		List<EditOp<String>> ops = new ArrayList<>();
		int x = n;
		int y = m;

		for (int d = trace.size() - 1; d >= 0; d--) {
			int[] v = trace.get(d);
			int k = x - y;

			int prevK;
			if (k == -d || (k != d && v[k - 1 + max] < v[k + 1 + max])) {
				prevK = k + 1; // came from insertion
			}
			else {
				prevK = k - 1; // came from deletion
			}

			int prevX = v[prevK + max];
			int prevY = prevX - prevK;

			// Emit diagonal matches (in reverse order)
			while (x > prevX && y > prevY) {
				ops.add(new EditOp.Equal<>(before.get(x - 1)));
				x--;
				y--;
			}

			// Emit the edit at this depth (d == 0 is the origin, no edit)
			if (d > 0) {
				if (x == prevX) {
					// Insertion: the inserted element is after[prevY]
					ops.add(new EditOp.Insert<>(after.get(prevY)));
				}
				else {
					// Deletion: the deleted element is before[prevX]
					ops.add(new EditOp.Delete<>(before.get(prevX)));
				}
			}

			x = prevX;
			y = prevY;
		}

		Collections.reverse(ops);
		return new EditScript<>(ops);
	}

	private record Tokenized(List<String> lines, boolean endsWithNewline, String dominantTerminator) {

		public Tokenized {
			lines = List.copyOf(lines);
		}
	}

	private Tokenized tokenize(String input) {
		if (input.isEmpty()) {
			return new Tokenized(List.of(), false, "\n");
		}

		List<String> lines = new ArrayList<>();
		int start = 0;
		boolean endsWithNewline = false;
		int unixCount = 0;
		int windowsCount = 0;

		for (int i = 0; i < input.length(); i++) {
			if (input.charAt(i) == '\n') {
				int end = i;
				if (i > 0 && input.charAt(i - 1) == '\r') {
					end = i - 1;
					windowsCount++;
				}
				else {
					unixCount++;
				}
				lines.add(input.substring(start, end));
				start = i + 1;
				endsWithNewline = (start == input.length());
			}
		}

		if (start < input.length()) {
			lines.add(input.substring(start));
			endsWithNewline = false;
		}

		String dominant = windowsCount > unixCount ? "\r\n" : "\n";
		return new Tokenized(lines, endsWithNewline, dominant);
	}

	public enum WhitespaceMode {

		STRICT,

		IGNORE_TRAILING,

		IGNORE_ALL;

		private static String normalize(String line, WhitespaceMode mode) {
			if (line == null) {
				return null;
			}
			return switch (mode) {
				case STRICT -> line;
				case IGNORE_TRAILING -> line.stripTrailing();
				case IGNORE_ALL -> line.replaceAll("\\s+", " ").strip();
			};
		}

		private BiPredicate<String, String> equator() {
			return (a, b) -> {
				String na = normalize(a, this);
				String nb = normalize(b, this);
				return Objects.equals(na, nb);
			};
		}

	}

	/**
	 * Aggregate counts for a diff between two text blocks.
	 *
	 * @param insertions number of inserted lines
	 * @param deletions number of deleted lines
	 * @param hunks number of contiguous change regions
	 */
	public record DiffSummary(
			@JsonPropertyDescription("Number of inserted lines") long insertions,
			@JsonPropertyDescription("Number of deleted lines") long deletions,
			@JsonPropertyDescription("Number of contiguous change regions (hunks)") int hunks) {
	}

	// @formatter:off
	@Tool(name = "DiffTool", description = """
		Produces a unified diff between two text blocks using Eugene W. Myers' O(ND) difference algorithm. The output follows the standard unified-diff format (--- / +++ / @@ headers) and is compatible with the `patch` utility and `git apply`.

		Use this to show what changed after editing a file, summarize edits in PR-style output, or audit agent modifications.

		Usage:
		- The `before` and `after` parameters are required and should be the raw text of the original and modified content.
		- Optionally provide `beforeLabel` and `afterLabel` to control the header lines (e.g. `a/config.yml` and `b/config.yml` for git-style labels). Defaults: "before" / "after".
		- `contextLines` controls the number of unchanged lines shown around each change region. Default: 3, maximum: 10. Values above the maximum are clamped; negatives are clamped to 0.
		- `whitespaceMode` controls line-equality: STRICT (default, byte-for-byte), IGNORE_TRAILING (ignores trailing whitespace), or IGNORE_ALL (collapses all whitespace runs to a single space, then trims).
		- Returns an empty string when the two sides are equivalent under the selected whitespace mode.
		- The output uses the line terminator dominant in `before` (or in `after` when `before` has no lines).
		- When either side is missing a trailing newline, a `\\ No newline at end of file` marker is emitted so patch tools can round-trip correctly.
		- For a lightweight count-only variant that skips rendering, use the `DiffSummarize` tool instead.
		""")
	public String diff(
			@ToolParam(description = "Original text (the 'before' side)") String before,
			@ToolParam(description = "Modified text (the 'after' side)") String after,
			@ToolParam(description = "Label for the before side. Example: 'a/config.yml'", required = false) String beforeLabel,
			@ToolParam(description = "Label for the after side. Example: 'b/config.yml'", required = false) String afterLabel,
			@ToolParam(description = "Unchanged context lines around each change. Default 3, max 10.", required = false) Integer contextLines,
			@ToolParam(description = "Whitespace handling. Default STRICT.", required = false) WhitespaceMode whitespaceMode) { // @formatter:on

		int ctx = (contextLines == null) ? DEFAULT_CONTEXT
				: Math.min(MAX_CONTEXT, Math.max(0, contextLines));
		WhitespaceMode ws = (whitespaceMode == null) ? WhitespaceMode.STRICT
				: whitespaceMode;
		String bLabel = (beforeLabel == null) ? "before" : beforeLabel;
		String aLabel = (afterLabel == null) ? "after" : afterLabel;

		return renderDiff(before, after, bLabel, aLabel, ctx, ws);
	}

	// @formatter:off
	@Tool(name = "DiffFiles", description = """
		Produces a unified diff between the current contents of two files on disk. Returns the same unified-diff format as the `DiffTool` tool and is compatible with the `patch` utility and `git apply`.

		Use this when the before/after content already lives in files and you want to avoid reading them into memory yourself.

		Usage:
		- The `beforePath` and `afterPath` parameters are required. Relative paths are resolved against the JVM working directory.
		- Header lines are automatically emitted as `a/<beforePath>` and `b/<afterPath>` so `git apply -p1` strips the prefix correctly.
		- `contextLines` controls the number of unchanged lines shown around each change region. Default: 3, maximum: 10.
		- `whitespaceMode` controls line-equality: STRICT (default), IGNORE_TRAILING, or IGNORE_ALL.
		- Returns an empty string when the two files are equivalent under the selected whitespace mode.
		- Returns `Error: ...` if either path is blank, or `Error reading files: ...` if either file cannot be read.
		- Files are read with the platform default charset.
		""")
	public String diffFiles(
			@ToolParam(description = "Path to the original file") String beforePath,
			@ToolParam(description = "Path to the modified file") String afterPath,
			@ToolParam(description = "Unchanged context lines around each change. Default 3, max 10.", required = false) Integer contextLines,
			@ToolParam(description = "Whitespace handling. Default STRICT.", required = false) WhitespaceMode whitespaceMode) { // @formatter:on
		try {
			if (beforePath == null || beforePath.isBlank()) {
				return "Error: beforePath must not be blank";
			}
			if (afterPath == null || afterPath.isBlank()) {
				return "Error: afterPath must not be blank";
			}
			Path bp = Paths.get(beforePath);
			Path ap = Paths.get(afterPath);
			String before = Files.readString(bp);
			String after = Files.readString(ap);

			int ctx = (contextLines == null) ? DEFAULT_CONTEXT
					: Math.min(MAX_CONTEXT, Math.max(0, contextLines));
			WhitespaceMode ws = (whitespaceMode == null) ? WhitespaceMode.STRICT
					: whitespaceMode;

			return renderDiff(before, after, "a/" + beforePath, "b/" + afterPath, ctx, ws);
		}
		catch (IOException e) {
			return "Error reading files: " + e.getMessage();
		}
	}

	// @formatter:off
	@Tool(name = "DiffSummarize", description = """
		Returns aggregate counts (insertions, deletions, hunks) for the diff between two text blocks, without emitting the full unified-diff body.

		Use this to cheaply gate further work — for example, checking whether any changes exist, reporting the size of an edit, or deciding whether to render a full diff afterwards.

		Usage:
		- The `before` and `after` parameters are required and should be the raw text of the original and modified content.
		- `contextLines` only affects how changes are grouped into hunks (it does not affect insertion/deletion counts). Default: 3, maximum: 10.
		- `whitespaceMode` controls line-equality: STRICT (default), IGNORE_TRAILING, or IGNORE_ALL.
		- Returns a `DiffSummary` with `insertions`, `deletions`, and `hunks` fields. All three are zero when the two sides are equivalent under the selected whitespace mode.
		- Counts match exactly what the `DiffTool` tool would emit for the same inputs.
		""")
	public DiffSummary summarize(
			@ToolParam(description = "Original text (the 'before' side)") String before,
			@ToolParam(description = "Modified text (the 'after' side)") String after,
			@ToolParam(description = "Unchanged context lines around each change. Default 3, max 10.", required = false) Integer contextLines,
			@ToolParam(description = "Whitespace handling. Default STRICT.", required = false) WhitespaceMode whitespaceMode) { // @formatter:on

		int ctx = (contextLines == null) ? DEFAULT_CONTEXT
				: Math.min(MAX_CONTEXT, Math.max(0, contextLines));
		WhitespaceMode ws = (whitespaceMode == null) ? WhitespaceMode.STRICT
				: whitespaceMode;

		Tokenized bt = tokenize(before);
		Tokenized at = tokenize(after);
		EditScript<String> script = diff(bt.lines(), at.lines(), ws.equator());
		List<Hunk> hunks = buildHunk(script, ctx);
		return new DiffSummary(script.insertions(), script.deletions(), hunks.size());
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {
		public DiffTool build() {
			return new DiffTool();
		}
	}

}