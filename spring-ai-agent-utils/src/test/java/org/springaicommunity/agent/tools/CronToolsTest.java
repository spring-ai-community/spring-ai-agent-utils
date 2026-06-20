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
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springaicommunity.agent.tools.cron.CronEventHandler;
import org.springaicommunity.agent.tools.cron.CronTask;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link CronTools}.
 *
 * @author Christian Tzolov
 */
@DisplayName("CronTools Tests")
class CronToolsTest {

	@Nested
	@DisplayName("CronCreate Tests")
	class CronCreateTests {

		private CronTools tools;

		private List<String> fired;

		@BeforeEach
		void setUp() {
			fired = new ArrayList<>();
			tools = CronTools.builder().eventHandler((CronEventHandler) task -> {
				fired.add(task.id());
				return "fired";
			}).build();
		}

		@Test
		@DisplayName("Should create a valid recurring cron job")
		void shouldCreateValidRecurringJob() {
			String result = tools.cronCreate("*/5 * * * *", "test prompt", true, false);

			assertThat(result).contains("Job scheduled");
			assertThat(result).contains("*/5 * * * *");
			assertThat(result).contains("recurring");
			assertThat(result).contains("session-only");
			assertThat(result).contains("test prompt");
		}

		@Test
		@DisplayName("Should create a one-shot cron job")
		void shouldCreateOneShotJob() {
			String result = tools.cronCreate("30 14 31 5 *", "one-time task", false, false);

			assertThat(result).contains("Job scheduled");
			assertThat(result).contains("one-shot");
		}

		@Test
		@DisplayName("Should create a durable cron job")
		void shouldCreateDurableJob() {
			String result = tools.cronCreate("0 9 * * 1-5", "weekday standup", true, true);

			assertThat(result).contains("Job scheduled");
			assertThat(result).contains("durable");
		}

		@Test
		@DisplayName("Should default recurring to true when not specified")
		void shouldDefaultRecurringToTrue() {
			String result = tools.cronCreate("*/10 * * * *", "default recurring", null, false);

			assertThat(result).contains("recurring");
			assertThat(result).doesNotContain("one-shot");
		}

		@Test
		@DisplayName("Should reject empty cron expression")
		void shouldRejectEmptyCron() {
			String result = tools.cronCreate("", "prompt", true, false);

			assertThat(result).contains("Error");
			assertThat(result).contains("cron expression is required");
		}

		@Test
		@DisplayName("Should reject invalid cron expression")
		void shouldRejectInvalidCron() {
			String result = tools.cronCreate("not a valid cron expression at all", "prompt", true, false);

			assertThat(result).contains("Error");
		}

		@Test
		@DisplayName("Should generate unique IDs for each task")
		void shouldGenerateUniqueIds() {
			String result1 = tools.cronCreate("*/5 * * * *", "task1", true, false);
			String result2 = tools.cronCreate("*/5 * * * *", "task2", true, false);

			assertThat(result1).isNotEqualTo(result2);
		}

	}

	@Nested
	@DisplayName("CronDelete Tests")
	class CronDeleteTests {

		private CronTools tools;

		@BeforeEach
		void setUp() {
			tools = CronTools.builder().eventHandler((CronEventHandler) task -> "fired").build();
		}

		@Test
		@DisplayName("Should delete an existing job")
		void shouldDeleteExistingJob() {
			// Create a job first
			String createResult = tools.cronCreate("*/5 * * * *", "test", true, false);
			// Extract the ID from the result (format: "ID: cron_XXXXXXXX")
			String id = extractId(createResult);

			String result = tools.cronDelete(id);

			assertThat(result).contains("Successfully cancelled");
			assertThat(result).contains(id);
		}

		@Test
		@DisplayName("Should return error for non-existent job")
		void shouldReturnErrorForNonExistentJob() {
			String result = tools.cronDelete("no-such-id");

			assertThat(result).contains("Error");
			assertThat(result).contains("no-such-id");
		}

		@Test
		@DisplayName("Should reject null or blank ID")
		void shouldRejectNullOrBlankId() {
			assertThat(tools.cronDelete(null)).contains("Error");
			assertThat(tools.cronDelete("   ")).contains("Error");
		}

		private String extractId(String createResult) {
			// Result format: "Job scheduled.\n  ID: cron_XXXXXXXX\n  ..."
			int idStart = createResult.indexOf("ID: ") + 4;
			int idEnd = createResult.indexOf("\n", idStart);
			return createResult.substring(idStart, idEnd).trim();
		}

	}

	@Nested
	@DisplayName("CronList Tests")
	class CronListTests {

		private CronTools tools;

		@BeforeEach
		void setUp() {
			tools = CronTools.builder().eventHandler((CronEventHandler) task -> "fired").build();
		}

		@Test
		@DisplayName("Should return empty message when no jobs")
		void shouldReturnEmptyMessageWhenNoJobs() {
			String result = tools.cronList();

			assertThat(result).contains("No scheduled cron jobs");
		}

		@Test
		@DisplayName("Should list all scheduled jobs")
		void shouldListAllScheduledJobs() {
			tools.cronCreate("*/5 * * * *", "task-a", true, false);
			tools.cronCreate("0 9 * * 1-5", "task-b", true, false);

			String result = tools.cronList();

			assertThat(result).contains("Scheduled cron jobs (2 total)");
			assertThat(result).contains("*/5 * * * *");
			assertThat(result).contains("0 9 * * 1-5");
			assertThat(result).contains("task-a");
			assertThat(result).contains("task-b");
		}

	}

	@Nested
	@DisplayName("Durable Persistence Tests")
	class DurablePersistenceTests {

		@TempDir
		Path tempDir;

		@Test
		@DisplayName("Should persist and restore durable tasks")
		void shouldPersistAndRestoreDurableTasks() throws IOException {
			Path file = tempDir.resolve("scheduled_tasks.json");

			// Create tools with durable persistence
			CronTools tools1 = CronTools.builder()
				.durableTasksFile(file)
				.eventHandler((CronEventHandler) task -> "fired")
				.build();

			tools1.cronCreate("0 9 * * 1-5", "daily standup", true, true);
			tools1.cronCreate("*/5 * * * *", "session-only task", true, false); // not durable

			// Verify file was written
			assertThat(file).exists();
			String content = Files.readString(file, StandardCharsets.UTF_8);
			assertThat(content).contains("daily standup");
			assertThat(content).doesNotContain("session-only task");

			// Create new tools instance (simulating restart)
			CronTools tools2 = CronTools.builder()
				.durableTasksFile(file)
				.eventHandler((CronEventHandler) task -> "fired")
				.build();

			// Should have restored the durable task
			String listResult = tools2.cronList();
			assertThat(listResult).contains("daily standup");
			assertThat(listResult).doesNotContain("session-only task");
		}

		@Test
		@DisplayName("Should delete file when all durable tasks are removed")
		void shouldDeleteFileWhenAllDurableTasksRemoved() throws IOException {
			Path file = tempDir.resolve("scheduled_tasks.json");

			CronTools tools = CronTools.builder()
				.durableTasksFile(file)
				.eventHandler((CronEventHandler) task -> "fired")
				.build();

			String createResult = tools.cronCreate("0 9 * * 1-5", "to-be-deleted", true, true);
			assertThat(file).exists();

			String id = extractId(createResult);
			tools.cronDelete(id);

			assertThat(file).doesNotExist();
		}

		private String extractId(String createResult) {
			int idStart = createResult.indexOf("ID: ") + 4;
			int idEnd = createResult.indexOf("\n", idStart);
			return createResult.substring(idStart, idEnd).trim();
		}

	}

	@Nested
	@DisplayName("Builder Tests")
	class BuilderTests {

		@Test
		@DisplayName("Should create tools with default configuration")
		void shouldCreateWithDefaults() {
			CronTools tools = CronTools.builder().build();
			assertThat(tools).isNotNull();
			assertThat(tools.cronList()).contains("No scheduled cron jobs");
		}

		@Test
		@DisplayName("Should create tools with custom event handler")
		void shouldCreateWithCustomEventHandler() {
			CronTools tools = CronTools.builder().eventHandler((CronEventHandler) task -> "custom handled").build();

			assertThat(tools).isNotNull();
		}

		@Test
		@DisplayName("Should create tools with durable file path as string")
		void shouldCreateWithDurableFileAsString(@TempDir Path tempDir) {
			String filePath = tempDir.resolve("tasks.json").toString();
			CronTools tools = CronTools.builder().durableTasksFile(filePath).build();

			assertThat(tools).isNotNull();
		}

	}

}
