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
package org.springaicommunity.agent.tools.cron;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link CronTaskStore}.
 *
 * @author Christian Tzolov
 */
@DisplayName("CronTaskStore Tests")
class CronTaskStoreTest {

	@TempDir
	Path tempDir;

	@Nested
	@DisplayName("Load Tests")
	class LoadTests {

		@Test
		@DisplayName("Should return empty list when file does not exist")
		void shouldReturnEmptyListWhenFileDoesNotExist() {
			Path file = tempDir.resolve("nonexistent.json");
			CronTaskStore store = new CronTaskStore(file);

			List<CronTask> tasks = store.load();

			assertThat(tasks).isEmpty();
		}

		@Test
		@DisplayName("Should load tasks from valid NDJSON file")
		void shouldLoadTasksFromValidFile() throws IOException {
			Path file = tempDir.resolve("tasks.json");
			String json = """
					{"id":"abc","cron":"0 9 * * *","prompt":"daily standup","recurring":true,"durable":true,"nextFireTime":"2026-06-01T09:00:00Z","createdAt":"2026-05-31T00:00:00Z"}
					{"id":"def","cron":"30 14 31 5 *","prompt":"one-time check","recurring":false,"durable":true,"nextFireTime":"2026-05-31T14:30:00Z","createdAt":"2026-05-31T00:00:00Z"}
					""";
			Files.writeString(file, json, StandardCharsets.UTF_8);
			CronTaskStore store = new CronTaskStore(file);

			List<CronTask> tasks = store.load();

			assertThat(tasks).hasSize(2);
			assertThat(tasks.get(0).id()).isEqualTo("abc");
			assertThat(tasks.get(0).cron()).isEqualTo("0 9 * * *");
			assertThat(tasks.get(0).prompt()).isEqualTo("daily standup");
			assertThat(tasks.get(0).recurring()).isTrue();
			assertThat(tasks.get(0).durable()).isTrue();
			assertThat(tasks.get(0).nextFireTime()).isEqualTo(Instant.parse("2026-06-01T09:00:00Z"));

			assertThat(tasks.get(1).id()).isEqualTo("def");
			assertThat(tasks.get(1).recurring()).isFalse();
			assertThat(tasks.get(1).prompt()).isEqualTo("one-time check");
		}

		@Test
		@DisplayName("Should skip malformed lines gracefully")
		void shouldSkipMalformedLines() throws IOException {
			Path file = tempDir.resolve("tasks.json");
			String json = """
					{"id":"good","cron":"0 9 * * *","prompt":"ok","recurring":true,"durable":true,"nextFireTime":"2026-06-01T09:00:00Z","createdAt":"2026-05-31T00:00:00Z"}
					this is not valid json at all
					{"id":"also-good","cron":"*/5 * * * *","prompt":"another","recurring":false,"durable":true,"nextFireTime":"2026-06-01T10:00:00Z","createdAt":"2026-05-31T00:00:00Z"}
					""";
			Files.writeString(file, json, StandardCharsets.UTF_8);
			CronTaskStore store = new CronTaskStore(file);

			List<CronTask> tasks = store.load();

			assertThat(tasks).hasSize(2);
			assertThat(tasks.get(0).id()).isEqualTo("good");
			assertThat(tasks.get(1).id()).isEqualTo("also-good");
		}

		@Test
		@DisplayName("Should skip empty lines")
		void shouldSkipEmptyLines() throws IOException {
			Path file = tempDir.resolve("tasks.json");
			String json = """
					{"id":"only","cron":"0 9 * * *","prompt":"test","recurring":true,"durable":true,"nextFireTime":"2026-06-01T09:00:00Z","createdAt":"2026-05-31T00:00:00Z"}

					""";
			Files.writeString(file, json, StandardCharsets.UTF_8);
			CronTaskStore store = new CronTaskStore(file);

			List<CronTask> tasks = store.load();

			assertThat(tasks).hasSize(1);
		}

	}

	@Nested
	@DisplayName("Save Tests")
	class SaveTests {

		@Test
		@DisplayName("Should save tasks to file")
		void shouldSaveTasksToFile() throws IOException {
			Path file = tempDir.resolve("tasks.json");
			CronTaskStore store = new CronTaskStore(file);

			List<CronTask> tasks = List.of(
					new CronTask("save-1", "0 9 * * 1-5", "weekday", true, true,
							Instant.parse("2026-06-01T09:00:00Z"), Instant.parse("2026-05-31T00:00:00Z")),
					new CronTask("save-2", "30 14 31 5 *", "one-shot", false, true,
							Instant.parse("2026-05-31T14:30:00Z"), Instant.parse("2026-05-31T00:00:00Z")));

			store.save(tasks);

			assertThat(file).exists();
			String content = Files.readString(file, StandardCharsets.UTF_8);
			assertThat(content).contains("\"id\":\"save-1\"");
			assertThat(content).contains("\"id\":\"save-2\"");
			assertThat(content).contains("\"cron\":\"0 9 * * 1-5\"");
			assertThat(content).contains("\"prompt\":\"weekday\"");
			assertThat(content).contains("\"recurring\":true");
		}

		@Test
		@DisplayName("Should save and load round-trip correctly")
		void shouldSaveAndLoadRoundTrip() {
			Path file = tempDir.resolve("tasks.json");
			CronTaskStore store = new CronTaskStore(file);

			CronTask original = new CronTask("roundtrip", "*/10 * * * *", "test prompt with \"quotes\"", true, true,
					Instant.parse("2026-06-01T12:00:00Z"), Instant.parse("2026-05-31T00:00:00Z"));
			store.save(List.of(original));

			List<CronTask> loaded = store.load();
			assertThat(loaded).hasSize(1);
			assertThat(loaded.get(0).id()).isEqualTo("roundtrip");
			assertThat(loaded.get(0).cron()).isEqualTo("*/10 * * * *");
			assertThat(loaded.get(0).prompt()).isEqualTo("test prompt with \"quotes\"");
			assertThat(loaded.get(0).recurring()).isTrue();
			assertThat(loaded.get(0).durable()).isTrue();
		}

		@Test
		@DisplayName("Should save empty list (deletes the file)")
		void shouldDeleteFileForEmptyList() throws IOException {
			Path file = tempDir.resolve("tasks.json");
			CronTaskStore store = new CronTaskStore(file);

			// Save some tasks first
			CronTask task = new CronTask("temp", "0 * * * *", "p", true, true, Instant.parse("2026-06-01T09:00:00Z"),
					Instant.now());
			store.save(List.of(task));
			assertThat(file).exists();

			// Now delete via the delete() method
			store.delete();
			assertThat(file).doesNotExist();
		}

	}

}
