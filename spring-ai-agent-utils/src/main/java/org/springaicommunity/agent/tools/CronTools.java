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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.agent.tools.cron.CronEventHandler;
import org.springaicommunity.agent.tools.cron.CronScheduler;
import org.springaicommunity.agent.tools.cron.CronTask;
import org.springaicommunity.agent.tools.cron.CronTaskStore;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.scheduling.support.CronExpression;

/**
 * Spring AI tool implementation of cron-based task scheduling.
 *
 * <p>
 * Exposes three tools ({@code CronCreate}, {@code CronDelete}, and
 * {@code CronList}) that allow an agent to schedule prompts to be enqueued at
 * future times using standard 5-field cron expressions. Supports recurring and
 * one-shot tasks, durable (file-persisted) and session-only tasks, jitter for
 * common top-of-the-hour schedules, and automatic expiry of durable recurring
 * tasks after 7 days.
 *
 * @author Christian Tzolov
 */
public class CronTools implements AutoCloseable {

	private static final Logger logger = LoggerFactory.getLogger(CronTools.class);

	private final CronScheduler scheduler;

	private final CronEventHandler eventHandler;

	private final CronTaskStore taskStore;

	private final Path durableTasksFile;

	private final AtomicBoolean closed = new AtomicBoolean(false);

	protected CronTools(CronEventHandler eventHandler, Path durableTasksFile) {
		this.eventHandler = eventHandler;
		this.durableTasksFile = durableTasksFile;
		this.taskStore = durableTasksFile != null ? new CronTaskStore(durableTasksFile) : null;
		this.scheduler = new CronScheduler(wrapEventHandler(eventHandler));
		this.scheduler.start();
		if (this.taskStore != null) {
			loadDurableTasks();
		}
	}

	private CronEventHandler wrapEventHandler(CronEventHandler handler) {
		return task -> {
			String result = handler.handle(task);
			if (task.durable()) {
				saveDurableTasks();
			}
			return result;
		};
	}

	private void loadDurableTasks() {
		List<CronTask> tasks = taskStore.load();
		for (CronTask task : tasks) {
			Instant nextFireTime = computeInitialNextFireTime(task);
			if (nextFireTime == null) {
				logger.info("Skipping expired durable task: {}", task.id());
				continue;
			}
			scheduler.schedule(task.withNextFireTime(nextFireTime));
		}
		saveDurableTasks();
	}

	private Instant computeInitialNextFireTime(CronTask task) {
		if (task.recurring()) {
			return computeNextFromCron(task.cron());
		}
		// One-shot: preserve existing future fire time, otherwise compute from cron.
		if (task.nextFireTime() != null && task.nextFireTime().isAfter(Instant.now())) {
			return task.nextFireTime();
		}
		return task.nextFireTime() != null ? task.nextFireTime() : computeNextFromCron(task.cron());
	}

	private Instant computeNextFromCron(String cron) {
		CronExpression expr = CronExpression.parse(CronScheduler.toSixField(cron));
		ZonedDateTime next = expr.next(ZonedDateTime.now());
		if (next == null) {
			return null;
		}
		return CronScheduler.applyJitter(cron, next.toInstant());
	}

	private synchronized void saveDurableTasks() {
		if (taskStore == null) {
			return;
		}
		List<CronTask> durable = scheduler.listAll().stream().filter(CronTask::durable).toList();
		if (durable.isEmpty()) {
			taskStore.delete();
		}
		else {
			taskStore.save(durable);
		}
	}

	// @formatter:off
	@Tool(name = "CronCreate", description = """
		Schedule a new cron job. Returns a job ID for use with CronDelete.

		Parameters:
		- cron: Standard 5-field cron expression (minute hour day-of-month month day-of-week).
		- prompt: The prompt to enqueue at each fire time.
		- recurring: Optional. true = fire on every match until deleted (default); false = fire once then auto-delete.
		- durable: Optional. true = persist to file and survive restarts; false = session-only (default).

		Cron expression format: minute hour day-of-month month day-of-week
		Examples:
		- */5 * * * * — Every 5 minutes
		- 0 9 * * * — Daily at 9:00 AM
		- 0 9 * * 1-5 — Weekdays at 9:00 AM
		- 30 14 28 2 * — Feb 28 at 2:30 PM (one-shot)
		""")
	public String cronCreate(
			@ToolParam(description = "Standard 5-field cron expression: minute hour day-of-month month day-of-week") String cron,
			@ToolParam(description = "The prompt to enqueue at each fire time") String prompt,
			@ToolParam(description = "true = recurring, false = one-shot; defaults to true", required = false) Boolean recurring,
			@ToolParam(description = "true = persist across restarts, false = session-only; defaults to false", required = false) Boolean durable) { // @formatter:on

		if (cron == null || cron.isBlank()) {
			return "Error: cron expression is required";
		}
		if (prompt == null || prompt.isBlank()) {
			return "Error: prompt is required";
		}

		String normalizedCron = cron.trim();
		try {
			CronExpression.parse(CronScheduler.toSixField(normalizedCron));
		}
		catch (Exception e) {
			return "Error: invalid cron expression '" + normalizedCron + "'";
		}

		boolean isRecurring = recurring == null || recurring;
		boolean isDurable = durable != null && durable;
		String id = "cron_" + UUID.randomUUID().toString().substring(0, 8);
		Instant createdAt = Instant.now();
		Instant nextFireTime = computeNextFromCron(normalizedCron);
		if (nextFireTime == null) {
			return "Error: cron expression has no future fire times: '" + normalizedCron + "'";
		}

		CronTask task = new CronTask(id, normalizedCron, prompt, isRecurring, isDurable, nextFireTime, createdAt);
		scheduler.schedule(task);
		if (isDurable) {
			saveDurableTasks();
		}

		String typeLabel = isRecurring ? "recurring" : "one-shot";
		String persistenceLabel = isDurable ? "durable" : "session-only";
		return String.format(
				"Job scheduled.\n  ID: %s\n  Cron: %s\n  Type: %s, %s\n  Prompt: %s",
				id, normalizedCron, typeLabel, persistenceLabel, prompt);
	}

	// @formatter:off
	@Tool(name = "CronDelete", description = """
		Cancel a previously scheduled cron job.

		Parameters:
		- id: Job ID returned by CronCreate.
		""")
	public String cronDelete(
			@ToolParam(description = "Job ID returned by CronCreate") String id) { // @formatter:on
		if (id == null || id.isBlank()) {
			return "Error: id is required";
		}
		String trimmedId = id.trim();
		CronTask removed = scheduler.cancel(trimmedId);
		if (removed == null) {
			return "Error: no scheduled job found with id '" + trimmedId + "'";
		}
		if (removed.durable()) {
			saveDurableTasks();
		}
		return "Successfully cancelled job: " + trimmedId;
	}

	// @formatter:off
	@Tool(name = "CronList", description = """
		List all scheduled cron jobs (both durable and session-only).

		No parameters required.
		""")
	public String cronList() { // @formatter:on
		List<CronTask> tasks = scheduler.listAll();
		if (tasks.isEmpty()) {
			return "No scheduled cron jobs.";
		}

		StringBuilder sb = new StringBuilder();
		sb.append("Scheduled cron jobs (").append(tasks.size()).append(" total):\n");
		for (CronTask task : tasks) {
			String typeLabel = task.recurring() ? "recurring" : "one-shot";
			String persistenceLabel = task.durable() ? "durable" : "session-only";
			sb.append(String.format("\n  ID: %s\n  Cron: %s\n  Type: %s, %s\n  Next fire: %s\n  Prompt: %s\n",
					task.id(), task.cron(), typeLabel, persistenceLabel, task.nextFireTime(), task.prompt()));
		}
		return sb.toString().trim();
	}

	/**
	 * Shut down the scheduler. The current thread will finish its current sleep and
	 * exit gracefully.
	 */
	@Override
	public void close() {
		if (closed.compareAndSet(false, true)) {
			scheduler.shutdown();
		}
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private CronEventHandler eventHandler = task -> {
			logger.info("Cron task fired: {} (prompt: {})", task.id(), task.prompt());
			return "fired";
		};

		private Path durableTasksFile;

		private Builder() {
		}

		/**
		 * Set the custom handler invoked when a cron task fires.
		 * @param eventHandler the fire-time callback
		 * @return this builder
		 */
		public Builder eventHandler(CronEventHandler eventHandler) {
			this.eventHandler = eventHandler != null ? eventHandler : this.eventHandler;
			return this;
		}

		/**
		 * Set the file used to persist durable tasks.
		 * @param durableTasksFile the persistence file path
		 * @return this builder
		 */
		public Builder durableTasksFile(Path durableTasksFile) {
			this.durableTasksFile = durableTasksFile;
			return this;
		}

		/**
		 * Set the file used to persist durable tasks using a string path.
		 * @param durableTasksFile the persistence file path as string
		 * @return this builder
		 */
		public Builder durableTasksFile(String durableTasksFile) {
			this.durableTasksFile = durableTasksFile != null ? Paths.get(durableTasksFile) : null;
			return this;
		}

		public CronTools build() {
			return new CronTools(eventHandler, durableTasksFile);
		}

	}

}
