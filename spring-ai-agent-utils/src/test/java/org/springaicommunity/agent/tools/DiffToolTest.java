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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for {@link DiffTool}.
 *
 * @author Martin Vlčák
 */
@DisplayName("DiffTool Tests")
class DiffToolTest {

	private DiffTool tool;

	@TempDir
	Path tempDir;

	@BeforeEach
	void setUp() {
		this.tool = DiffTool.builder().build();
	}

	@Nested
	@DisplayName("String diff tests")
	class TextDiffTests{

		@Test
		@DisplayName("Should return empty diff for identical inputs")
		void shouldReturnEmptyDiffForIdenticalInputs() {
			String text = "line1\nline2\nline3\n";
			assertThat(tool.diff(text, text, "a", "b", null, null)).isEmpty();
		}

		@Test
		@DisplayName("Should produce unified diff for simple change")
		void shouldProduceUnifiedDiffForSimpleChange() {
			String before = "a\nb\nc\n";
			String after = "a\nB\nc\n";
			String diff = tool.diff(before, after, "a/f", "b/f", 3, null);

			assertThat(diff).isEqualTo("""
				--- a/f
				+++ b/f
				@@ -1,3 +1,3 @@
				 a
				-b
				+B
				 c
				""");
		}

		@Test
		@DisplayName("Should emit zero before-start for pure insert at start")
		void shouldEmitZeroBeforeStartForPureInsertAtStart() {
			String diff = tool.diff("", "x\n", "a", "b", null, null);
			assertThat(diff).isEqualTo("""
				--- a
				+++ b
				@@ -0,0 +1 @@
				+x
				""");
		}

		@Test
		@DisplayName("Should report missing trailing newline")
		void shouldReportMissingTrailingNewline() {
			String diff = tool.diff("a\n", "a", "a", "b", null, null);
			assertThat(diff).contains("\\ No newline at end of file");
		}

		@Test
		@DisplayName("Should match Myers paper edit distance")
		void shouldMatchMyersPaperEditDistance() {
			// Myers 1986, section 4a — canonical example: ABCABBA vs CBABAC, D = 5.
			// Each letter becomes its own line so the line-level diff mirrors the paper.
			String before = "A\nB\nC\nA\nB\nB\nA\n";
			String after = "C\nB\nA\nB\nA\nC\n";
			DiffTool.DiffSummary s = tool.summarize(before, after, null, null);
			assertThat(s.insertions() + s.deletions()).isEqualTo(5L);
		}

		@Test
		@DisplayName("Should treat trailing space as equal in IGNORE_TRAILING mode")
		void shouldTreatTrailingSpaceAsEqualInIgnoreTrailingMode() {
			String before = "hello  \nworld\n";
			String after = "hello\nworld\n";
			String diff = tool.diff(before, after, "a", "b", 3, DiffTool.WhitespaceMode.IGNORE_TRAILING);
			assertThat(diff).isEmpty();
		}

		@Test
		@DisplayName("Should treat whitespace-only change as no-op when summarizing in IGNORE_ALL mode")
		void shouldTreatWhitespaceOnlyChangeAsNoOpWhenSummarizingInIgnoreAllMode() {
			DiffTool.DiffSummary s = tool.summarize(
					"hello   world\n", "hello world\n", null,
					DiffTool.WhitespaceMode.IGNORE_ALL);
			assertThat(s.insertions()).isZero();
			assertThat(s.deletions()).isZero();
			assertThat(s.hunks()).isZero();
		}

		@Test
		@DisplayName("Should collapse internal whitespace in IGNORE_ALL mode")
		void shouldCollapseInternalWhitespaceInIgnoreAllMode() {
			String before = "hello   world\n";
			String after  = "hello world\n";
			assertThat(tool.diff(before, after, "a", "b", 3,
					DiffTool.WhitespaceMode.IGNORE_ALL)).isEmpty();
		}

		@Test
		@DisplayName("Should clamp context lines above max")
		void shouldClampContextLinesAboveMax() {
			// With 20 lines and ctx=999 (clamped to 10), two distant changes still produce two hunks
			// because 18 unchanged lines between them > 2*10+1.
			StringBuilder sb = new StringBuilder();
			for (int i = 1; i <= 30; i++) sb.append("line").append(i).append('\n');
			String before = sb.toString();
			String after = before.replace("line1\n", "LINE1\n").replace("line30\n", "LINE30\n");
			String diff = tool.diff(before, after, "a", "b", 999, null);
			assertThat(diff.split("@@").length - 1).isEqualTo(4);
		}

		@Test
		@DisplayName("Should clamp negative context lines to zero")
		void shouldClampNegativeContextLinesToZero() {
			// ctx=-5 → clamped to 0 → no context lines around the change
			String diff = tool.diff("a\nb\nc\n", "a\nB\nc\n", "before.txt", "after.txt", -5, null);
			assertThat(diff).contains("@@ -2 +2 @@\n-b\n+B\n");
			assertThat(diff).doesNotContain(" a\n");
			assertThat(diff).doesNotContain(" c\n");
		}

		@Test
		@DisplayName("Should report correct summarize counts")
		void shouldReportCorrectSummarizeCounts() {
			DiffTool.DiffSummary s = tool.summarize("a\nb\nc\n", "a\nx\ny\nc\n", null, null);
			assertThat(s.insertions()).isEqualTo(2L);
			assertThat(s.deletions()).isEqualTo(1L);
			assertThat(s.hunks()).isEqualTo(1);
		}

		@Test
		@DisplayName("Should produce two hunks for two distant changes")
		void shouldProduceTwoHunksForTwoDistantChanges() {
			StringBuilder sb = new StringBuilder();
			for (int i = 1; i <= 20; i++) {
				sb.append("line").append(i).append('\n');
			}
			String before = sb.toString();
			String after = before.replace("line1\n", "LINE1\n").replace("line20\n", "LINE20\n");
			String diff = tool.diff(before, after, "a", "b", 3, null);
			assertThat(diff.split("@@").length - 1).isEqualTo(4); // two @@ per hunk
		}

		@ParameterizedTest
		@MethodSource("sampleStringsStream")
		@DisplayName("Should round-trip diff back to after")
		void shouldRoundTripDiffBackToAfter(SampleStrings sample) {
			String before = sample.left();
			String after = sample.right();
			String diff = tool.diff(before, after, "a", "b", 3, null);
			String applied = applyUnifiedDiff(before, diff);
			assertThat(applied).as("round trip for pair: %s -> %s", before, after).isEqualTo(after);
		}

		private record SampleStrings(String left, String right){}

		private static Stream<SampleStrings> sampleStringsStream() {
			return Stream.of(
					new SampleStrings("", ""),
					new SampleStrings("", "a\n"),
					new SampleStrings("a\n", "" ),
					new SampleStrings("a\n", "a\n"),
					new SampleStrings("a\nb\nc\n", "a\nB\nc\n"),
					new SampleStrings("a\nb\nc\n", "a\nc\n"),
					new SampleStrings("a\nc\n", "a\nb\nc\n"),
					new SampleStrings("one\ntwo\nthree\nfour\nfive\n", "one\nTWO\nthree\nFOUR\nfive\n"),
					new SampleStrings("x\ny\nz\n", "z\ny\nx\n"),
					new SampleStrings("same line\n", "same line"),
					new SampleStrings("no trailing", "no trailing"),
					new SampleStrings("no trailing", "has trailing\n"),
					//CRLF inputs
					new SampleStrings("a\r\nb\r\nc\r\n", "a\r\nB\r\nc\r\n"),
					new SampleStrings("a\r\n", "a"),
					new SampleStrings("", "x\r\n")
			);
		}

		@Nested
		@DisplayName("CRLF input handling")
		class CrlfInputTests{

			@Test
			@DisplayName("Should produce CRLF output for CRLF input")
			void shouldProduceCrlfOutputForCrlfInput() {
				String before = "a\r\nb\r\nc\r\n";
				String after  = "a\r\nB\r\nc\r\n";
				String diff = tool.diff(before, after, "a/f", "b/f", 3, null);

				assertThat(diff).isEqualTo(
						"""
                                --- a/f\r
                                +++ b/f\r
                                @@ -1,3 +1,3 @@\r
                                 a\r
                                -b\r
                                +B\r
                                 c\r
                                """);
			}

			@Test
			@DisplayName("Should use CRLF on No-newline marker for CRLF input")
			void shouldUseCrlfOnNoNewlineMarkerForCrlfInput() {
				String diff = tool.diff("a\r\n", "a", "a", "b", null, null);
				assertThat(diff).contains("\\ No newline at end of file\r\n");
			}

			@Test
			@DisplayName("Should use CRLF when before side is CRLF-dominant")
			void shouldUseCrlfWhenBeforeIsCrlfDominant() {
				String diff = tool.diff("a\r\nb\r\n", "a\r\nB\n", "a", "b", 3, null);
				assertThat(diff).contains("@@ -1,2 +1,2 @@\r\n");
				assertThat(diff).contains("-b\r\n");
				assertThat(diff).contains("+B\r\n");
			}

			@Test
			@DisplayName("Should use LF when before side is LF-dominant")
			void shouldUseLfWhenBeforeIsLfDominant() {
				String diff = tool.diff("a\nb\n", "a\r\nB\r\n", "a", "b", 3, null);
				assertThat(diff.lines().count()).isGreaterThan(0);
				assertThat(diff).doesNotContain("\r");
			}

			@Test
			@DisplayName("Should favor LF when terminator counts are tied")
			void shouldFavorLfWhenTerminatorCountsAreTied() {
				// one \r\n, one \n — tie, LF wins because condition is strictly >
				String before = "a\r\nb\n";
				String after  = "a\r\nB\n";
				String diff = tool.diff(before, after, "a", "b", 3, null);
				assertThat(diff).doesNotContain("\r");
			}

			@Test
			@DisplayName("Should use after-side terminator when before side is empty")
			void shouldUseAfterTerminatorWhenBeforeIsEmpty() {
				String diff = tool.diff("", "x\r\n", "a", "b", null, null);
				assertThat(diff).contains("+x\r\n");
			}
		}

	}

	@Nested
	@DisplayName("Diff files tests")
	class DiffFilesTests{
		@Test
		@DisplayName("Should produce empty diff for identical files")
		void shouldProduceEmptyDiffForIdenticalFiles() throws IOException {

			Path before = tempDir.resolve("before.txt");
			Path after = tempDir.resolve("after.txt");
			String content = "Line 1\nLine 2\nLine 3";
			Files.writeString(before, content, StandardCharsets.UTF_8);
			Files.writeString(after, content, StandardCharsets.UTF_8);

			assertEquals("", tool.diffFiles(before.toString(), after.toString(), 3, null));

		}

		@Test
		@DisplayName("Should produce simple diff for different files")
		void shouldProduceSimpleDiffForDifferentFiles() throws IOException {

			Path before = tempDir.resolve("before.txt");
			Path after = tempDir.resolve("after.txt");
			String beforeInput = "A\nB\nC\nA\nB\nB\nA\n";
			String afterInput = "C\nB\nA\nB\nA\nC\n";
			String expected = tool.diff(beforeInput, afterInput, "a/"+before, "b/"+after,3, null);

			Files.writeString(before, beforeInput, StandardCharsets.UTF_8);
			Files.writeString(after, afterInput, StandardCharsets.UTF_8);

			assertEquals(expected, tool.diffFiles(before.toString(), after.toString(), 3, null));

		}

		@Test
		@DisplayName("Should return error string when a file is missing")
		void shouldReturnErrorStringWhenFileIsMissing() {
			String result = tool.diffFiles(
					tempDir.resolve("does-not-exist-1.txt").toString(),
					tempDir.resolve("does-not-exist-2.txt").toString(),
					null, null);
			assertThat(result).startsWith("Error reading files:");
		}
	}

	/**
	 * Minimal applyUnifiedDiff: parses hunk headers and applies ops in order.
	 */
	private String applyUnifiedDiff(String before, String diff) {
		if (diff.isEmpty()) {
			return before;
		}
		List<String> src = new ArrayList<>();
		if (!before.isEmpty()) {
			int s = 0;
			for (int i = 0; i < before.length(); i++) {
				if (before.charAt(i) == '\n') {
					int end = (i > 0 && before.charAt(i - 1) == '\r') ? i - 1 : i;
					src.add(before.substring(s, end));
					s = i + 1;
				}
			}
			if (s < before.length()) {
				src.add(before.substring(s));
			}
		}
		List<String> out = new ArrayList<>();

		String term = diff.contains("\r\n") ? "\r\n" : "\n";
		String[] diffLines = diff.split(java.util.regex.Pattern.quote(term), -1);
		int di = 0;
		// Skip header
		while (di < diffLines.length && !diffLines[di].startsWith("@@")) {
			di++;
		}

		int srcIdx = 0;
		boolean afterEndsWithNewline = true;

		while (di < diffLines.length) {
			String line = diffLines[di];
			if (line.startsWith("@@")) {
				int beforeStart = parseStart(line, '-');
				while (srcIdx < beforeStart - 1) {
					out.add(src.get(srcIdx++));
				}
				di++;
				continue;
			}
			if (line.isEmpty()) {
				di++;
				continue;
			}
			char prefix = line.charAt(0);
			String content = line.substring(1);
			if (prefix == ' ') {
				out.add(content);
				srcIdx++;
			}
			else if (prefix == '-') {
				srcIdx++;
			}
			else if (prefix == '+') {
				out.add(content);
			}
			else if (prefix == '\\' && diffLines[di].contains("No newline")) {
				int prev = di - 1;
				while (prev >= 0 && diffLines[prev].isEmpty()) {
					prev--;
				}
				if (prev >= 0) {
					char prevPrefix = diffLines[prev].charAt(0);
					if (prevPrefix == '+') {
						afterEndsWithNewline = false;
					}
					else if (prevPrefix == ' ') {
						afterEndsWithNewline = false;
					}
				}
			}
			di++;
		}
		while (srcIdx < src.size()) {
			out.add(src.get(srcIdx++));
		}

		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < out.size(); i++) {
			sb.append(out.get(i));
			if (i < out.size() - 1 || afterEndsWithNewline) {
				sb.append(term);
			}
		}
		return sb.toString();
	}

	private static int parseStart(String hunkHeader, char sign) {
		int idx = hunkHeader.indexOf(sign);
		int end = idx + 1;
		while (end < hunkHeader.length() && (Character.isDigit(hunkHeader.charAt(end)))) {
			end++;
		}
		int start = Integer.parseInt(hunkHeader.substring(idx + 1, end));
		return start == 0 ? 1 : start;
	}

}
