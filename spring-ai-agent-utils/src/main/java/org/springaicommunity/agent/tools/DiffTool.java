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
import org.springframework.util.Assert;

/**
 * Spring AI tool that produces unified-diff output between two text blocks using
 * Eugene W. Myers' O(ND) difference algorithm. Output is compatible with the
 * {@code patch} utility and {@code git apply}.
 *
 * <p>
 * This tool has zero runtime dependencies beyond Spring AI core and the JDK. It
 * complements {@code FileSystemTools} by letting agents show what they changed.
 *
 * <p>
 * <strong>Thread Safety:</strong> This class and all its collaborators are stateless
 * and safe for concurrent use.
 *
 * @author Martin Vlčák
 *
 */
public class DiffTool {

	private static final String NO_NEWLINE_MARKER = "\\ No newline at end of file";

	private static final int DEFAULT_CONTEXT = 3;

	private static final int MAX_CONTEXT = 10;

	/**
	 * An ordered list of {@link EditOp} operations that transforms one sequence into
	 * another.
	 *
	 * @param <T> element type of the operations
	 * @param ops the ordered edit operations; defensively copied on construction
	 */
	private static record EditScript<T>(List<EditOp<T>> ops) {

		public EditScript {
			if (ops == null) {
				throw new IllegalArgumentException("ops must not be null");
			}
			ops = List.copyOf(ops);
		}

		public long insertions() {
			return this.ops.stream().filter(o -> o instanceof EditOp.Insert<T>).count();
		}

		public long deletions() {
			return this.ops.stream().filter(o -> o instanceof EditOp.Delete<T>).count();
		}

	}

	/**
	 * A single operation in an edit script produced by the Myers diff algorithm. An edit
	 * script is a sequence of {@link Equal}, {@link Delete}, and {@link Insert} ops that,
	 * applied in order, transform the {@code before} sequence into the {@code after}
	 * sequence.
	 *
	 * @param <T> element type carried by the operation (typically {@link String} for
	 * line-level diffing)
	 */
	private sealed interface EditOp<T> permits EditOp.Equal, EditOp.Delete, EditOp.Insert {

		/**
		 * Returns the element this operation carries.
		 */
		T value();

		/**
		 * Element that appears unchanged in both {@code before} and {@code after}.
		 */
		record Equal<T>(T value) implements EditOp<T> {
		}

		/**
		 * Element that appears in {@code before} but not in {@code after}.
		 */
		record Delete<T>(T value) implements EditOp<T> {
		}

		/**
		 * Element that appears in {@code after} but not in {@code before}.
		 */
		record Insert<T>(T value) implements EditOp<T> {
		}

	}

	/**
	 * A contiguous region of an edit script containing one or more changes surrounded by
	 * matching context lines, ready to be rendered as a unified-diff hunk.
	 *
	 * <p>
	 * {@code beforeStart} and {@code afterStart} are 1-based line numbers referring to the
	 * first line of the hunk in the corresponding side.
	 *
	 * @param beforeStart 1-based line number of the hunk's first line on the before side
	 * @param beforeCount number of lines the hunk covers on the before side
	 * @param afterStart 1-based line number of the hunk's first line on the after side
	 * @param afterCount number of lines the hunk covers on the after side
	 * @param ops the edit operations making up the hunk, in order
	 */
	private static record Hunk(int beforeStart, int beforeCount, int afterStart, int afterCount, List<EditOp<String>> ops) {

		public Hunk {
			if (ops == null) {
				throw new IllegalArgumentException("ops must not be null");
			}
			ops = List.copyOf(ops);
		}

	}

	/**
	 * Groups an {@link EditScript} into {@link Hunk hunks} with {@code contextLines}
	 * unchanged lines on each side of a change region. Two change regions separated by
	 * {@code 2 * contextLines + 1} or more unchanged lines produce two hunks; a shorter
	 * gap is merged into a single hunk.
	 */
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
		if (hunks == null) {
			throw new IllegalArgumentException("hunks must not be null");
		}
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

	/**
	 * Tokenizes both sides, computes the Myers edit script, groups it into hunks,
	 * and renders the unified-diff body. Returns an empty string when the two
	 * sides are equivalent under {@code ws}.
	 *
	 * <p>Output uses the terminator dominant in {@code before}, or in {@code after}
	 * when {@code before} has no lines.
	 *
	 * @param before raw text of the original side
	 * @param after raw text of the modified side
	 * @param beforeLabel label emitted on the {@code ---} header line
	 * @param afterLabel label emitted on the {@code +++} header line
	 * @param contextLines number of unchanged lines to include around each change region
	 * @param ws whitespace-sensitivity mode for line comparison
	 * @return unified-diff text, or empty string if the inputs are equivalent under {@code ws}
	 */
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

	/**
	 * When the two sides differ only by their trailing newline, the pure content-based
	 * edit script is empty even though the files differ. Rewrite the final {@code Equal}
	 * op as a {@code Delete} + {@code Insert} so the formatter can render the mandatory
	 * {@code \ No newline at end of file} marker.
	 */
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



	/**
	 * Computes the edit script using a custom element equator.
	 * @param before the original sequence
	 * @param after the modified sequence
	 * @param equator equality predicate
	 * @return a minimal edit script
	 */
	private EditScript<String> diff(List<String> before, List<String> after, BiPredicate<String, String> equator) {
		if (before == null) {
			throw new IllegalArgumentException("before must not be null");
		}
		if (after == null) {
			throw new IllegalArgumentException("after must not be null");
		}
		if (equator == null) {
			throw new IllegalArgumentException("equator must not be null");
		}

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
		throw new IllegalStateException("unreachable");
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

	/**
	 * The tokenized result.
	 *
	 * @param lines the lines, without any terminator
	 * @param endsWithNewline whether the original input ended with a line terminator
	 * @param dominantTerminator the terminator observed most often in the input
	 *        ({@code "\n"} or {@code "\r\n"}); {@code "\n"} wins on ties, and
	 *        {@code "\n"} is also the default when the input contained no terminators
	 */
	record Tokenized(List<String> lines, boolean endsWithNewline, String dominantTerminator) {

		public Tokenized {
			Objects.requireNonNull(lines, "lines must not be null");
			Objects.requireNonNull(dominantTerminator, "dominantTerminator must not be null");
			lines = List.copyOf(lines);
		}
	}

	/**
	 * Tokenizes the input into lines.
	 * @param input the raw text, must not be null
	 * @return the tokenized result
	 */
	Tokenized tokenize(String input) {
		Objects.requireNonNull(input, "input must not be null");
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

		/** Compare lines byte-for-byte. */
		STRICT,

		/** Ignore trailing whitespace (see {@link String#stripTrailing()}). */
		IGNORE_TRAILING,

		/** Collapse all whitespace runs to a single space, then trim. */
		IGNORE_ALL;

		/**
		 * Normalizes a line for comparison according to this mode.
		 */
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

		/**
		 * Returns an equator that compares two lines under this whitespace mode.
		 */
		private BiPredicate<String, String> equator() {
			return (a, b) -> {
				String na = normalize(a, this);
				String nb = normalize(b, this);
				return na == null ? nb == null : na.equals(nb);
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

	@Tool(name = "DiffTool",
			description = """
					Produces a unified diff between two text blocks. Output follows the
					standard unified-diff format (--- / +++ / @@ headers) and is compatible
					with the `patch` utility and `git apply`.

					Use this to show what changed after editing a file, summarize edits in
					PR-style output, or audit agent modifications.
					""")
	public String diff(
			@ToolParam(description = "Original text (the 'before' side)") String before,
			@ToolParam(description = "Modified text (the 'after' side)") String after,
			@ToolParam(description = "Label for the before side. Example: 'a/config.yml'", required = false) String beforeLabel,
			@ToolParam(description = "Label for the after side. Example: 'b/config.yml'", required = false) String afterLabel,
			@ToolParam(description = "Unchanged context lines around each change. Default 3, max 10.", required = false) Integer contextLines,
			@ToolParam(description = "Whitespace handling. Default STRICT.", required = false) WhitespaceMode whitespaceMode) {

		int ctx = (contextLines == null) ? DEFAULT_CONTEXT
				: Math.min(MAX_CONTEXT, Math.max(0, contextLines));
		WhitespaceMode ws = (whitespaceMode == null) ? WhitespaceMode.STRICT
				: whitespaceMode;
		String bLabel = (beforeLabel == null) ? "before" : beforeLabel;
		String aLabel = (afterLabel == null) ? "after" : afterLabel;

		return renderDiff(before, after, bLabel, aLabel, ctx, ws);
	}

	@Tool(name = "DiffFiles",
			description = """
					Produces a unified diff between the current contents of two files on
					disk. Paths are resolved against the JVM working directory when not
					absolute. Returns the same unified-diff format as the `diff` tool.
					""")
	public String diffFiles(
			@ToolParam(description = "Path to the original file") String beforePath,
			@ToolParam(description = "Path to the modified file") String afterPath,
			@ToolParam(description = "Unchanged context lines around each change. Default 3, max 10.", required = false) Integer contextLines,
			@ToolParam(description = "Whitespace handling. Default STRICT.", required = false) WhitespaceMode whitespaceMode) {

		Assert.hasText(beforePath, "beforePath must not be blank");
		Assert.hasText(afterPath, "afterPath must not be blank");
		try {
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

	@Tool(name = "DiffSummarize",
			description = """
					Returns aggregate counts (insertions, deletions, hunks) for the diff
					between two text blocks, without emitting the full unified-diff body.
					""")
	public DiffSummary summarize(
			@ToolParam(description = "Original text (the 'before' side)") String before,
			@ToolParam(description = "Modified text (the 'after' side)") String after,
			@ToolParam(description = "Unchanged context lines around each change. Default 3, max 10.", required = false) Integer contextLines,
			@ToolParam(description = "Whitespace handling. Default STRICT.", required = false) WhitespaceMode whitespaceMode) {

		Assert.notNull(before, "before must not be null");
		Assert.notNull(after, "after must not be null");

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

	public static DiffTool.Builder builder() {
		return new Builder();
	}

	public static class Builder {
		public DiffTool build() {
			return new DiffTool();
		}
	}

}