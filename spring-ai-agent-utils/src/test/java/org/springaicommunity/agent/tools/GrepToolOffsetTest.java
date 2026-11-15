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
 * Tests for the {@code offset} pagination argument of {@link GrepTool} when no
 * {@code head_limit} is supplied. {@code offset} is documented as usable on its own
 * ("Skip first N lines/entries before applying head_limit"), so an offset without a
 * head_limit must still return the remaining matches instead of nothing.
 *
 * @author Christian Tzolov
 */
@DisplayName("GrepTool Offset Without HeadLimit Tests")
class GrepToolOffsetTest {

	private GrepTool grepTool;

	@TempDir
	Path tempDir;

	@BeforeEach
	void setUp() {
		this.grepTool = GrepTool.builder().build();
	}

	/**
	 * Writes {@code count} files, each holding exactly one line that matches the pattern.
	 */
	private void createMatchingFiles(int count) throws IOException {
		for (int i = 1; i <= count; i++) {
			Path file = this.tempDir.resolve("file" + i + ".txt");
			Files.writeString(file, "match", StandardCharsets.UTF_8);
		}
	}

	private String grep(GrepTool.OutputMode outputMode, Integer headLimit, Integer offset) {
		return this.grepTool.grep("match", this.tempDir.toString(), null, outputMode, null, null, null, null, null,
				null, headLimit, offset, null);
	}

	@Nested
	@DisplayName("files_with_matches mode")
	class FilesWithMatchesTests {

		@Test
		@DisplayName("offset alone returns the remaining files")
		void offsetAloneReturnsRemainingFiles() throws IOException {
			// Given
			createMatchingFiles(5);

			// When - skip 2, no head_limit
			String result = grep(GrepTool.OutputMode.files_with_matches, null, 2);

			// Then - the 3 remaining files, not "no matches"
			assertThat(result).doesNotContain("No matches found");
			assertThat(result.split("\n")).hasSize(3);
		}

		@Test
		@DisplayName("head_limit 0 (documented as unlimited) keeps offset working")
		void zeroHeadLimitKeepsOffsetWorking() throws IOException {
			// Given
			createMatchingFiles(5);

			// When - skip 2, head_limit explicitly 0 = unlimited
			String result = grep(GrepTool.OutputMode.files_with_matches, 0, 2);

			// Then
			assertThat(result).doesNotContain("No matches found");
			assertThat(result.split("\n")).hasSize(3);
		}

		@Test
		@DisplayName("offset with head_limit still skips and limits")
		void offsetWithHeadLimitStillWorks() throws IOException {
			// Given
			createMatchingFiles(5);

			// When - skip 2, take 2
			String result = grep(GrepTool.OutputMode.files_with_matches, 2, 2);

			// Then
			assertThat(result).doesNotContain("No matches found");
			assertThat(result.split("\n")).hasSize(2);
		}

	}

	@Nested
	@DisplayName("count mode")
	class CountTests {

		@Test
		@DisplayName("offset alone returns the remaining counts")
		void offsetAloneReturnsRemainingCounts() throws IOException {
			// Given
			createMatchingFiles(5);

			// When
			String result = grep(GrepTool.OutputMode.count, null, 2);

			// Then
			assertThat(result).doesNotContain("No matches found");
			assertThat(result.split("\n")).hasSize(3);
		}

	}

	@Nested
	@DisplayName("content mode")
	class ContentTests {

		@Test
		@DisplayName("offset alone returns the remaining matching lines")
		void offsetAloneReturnsRemainingContent() throws IOException {
			// Given
			createMatchingFiles(5);

			// When
			String result = grep(GrepTool.OutputMode.content, null, 1);

			// Then
			assertThat(result).doesNotContain("No matches found");
			assertThat(result).contains("match");
		}

	}

}
