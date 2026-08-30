/*
* Copyright 2026 - 2026 the original author or authors.
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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Behavior preserved across the java.io → NIO migration of FileSystemTools: lenient
 * charset handling, graceful invalid-path and empty-path errors, and an honest header
 * when the read limit truncates.
 *
 * @author Christian Tzolov
 */
class FileSystemToolsNioRegressionTest {

	@TempDir
	Path tempDir;

	private final FileSystemTools tools = FileSystemTools.builder().build();

	@Test
	void readToleratesNonUtf8Bytes() throws Exception {
		Path file = this.tempDir.resolve("latin1.log");
		// 0xE9 is 'é' in ISO-8859-1 — an invalid byte sequence in UTF-8
		byte[] content = ("before " + (char) 0xE9 + " after\nline two\n").getBytes(StandardCharsets.ISO_8859_1);
		Files.write(file, content);

		String result = this.tools.read(file.toString(), null, null);

		assertThat(result).doesNotStartWith("Error").contains("line two").contains("before");
	}

	@Test
	void writeToleratesLoneSurrogates() {
		Path file = this.tempDir.resolve("surrogate.txt");
		// A high surrogate with no low surrogate — e.g. an emoji truncated at a token
		// boundary in model output
		String loneSurrogate = "truncated emoji: " + '\uD83D';

		String result = this.tools.write(file.toString(), loneSurrogate);

		assertThat(result).contains("Successfully created file");
		assertThat(Files.exists(file)).isTrue();
	}

	@Test
	void invalidPathReturnsErrorStringInsteadOfThrowing() {
		// An embedded NUL is rejected by Paths.get with InvalidPathException
		String invalid = "invalid\0path.txt";

		assertThat(this.tools.read(invalid, null, null)).startsWith("Error: Invalid path:");
		assertThat(this.tools.edit(invalid, "a", "b", null)).startsWith("Error: Invalid path:");
	}

	@Test
	void emptyPathReturnsCleanError() {
		assertThat(this.tools.read("", null, null)).isEqualTo("Error: file_path must not be empty");
		assertThat(this.tools.write("  ", "x")).isEqualTo("Error: file_path must not be empty");
		assertThat(this.tools.edit(null, "a", "b", null)).isEqualTo("Error: file_path must not be empty");
	}

	@Test
	void limitHitHeaderSaysAtLeast() throws Exception {
		Path file = this.tempDir.resolve("big.txt");
		StringBuilder sb = new StringBuilder();
		for (int i = 1; i <= 50; i++) {
			sb.append("line ").append(i).append("\n");
		}
		Files.writeString(file, sb.toString());

		String truncated = this.tools.read(file.toString(), null, 10);
		assertThat(truncated).contains("Showing lines 1-10 of at least 11");

		String full = this.tools.read(file.toString(), null, null);
		assertThat(full).contains("Showing lines 1-50 of 50");
	}

}
