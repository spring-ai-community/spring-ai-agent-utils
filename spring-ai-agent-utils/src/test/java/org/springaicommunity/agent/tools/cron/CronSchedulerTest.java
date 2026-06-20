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

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link CronScheduler}.
 *
 * @author Christian Tzolov
 */
@DisplayName("CronScheduler Tests")
class CronSchedulerTest {

	private CronScheduler scheduler;

	@BeforeEach
	void setUp() {
		scheduler = new CronScheduler(task -> "handled: " + task.id());
		scheduler.start();
	}

	@AfterEach
	void tearDown() {
		scheduler.shutdown();
	}

	@Nested
	@DisplayName("Schedule and List Tests")
	class ScheduleAndListTests {

		@Test
		@DisplayName("Should schedule a task and list it")
		void shouldScheduleTaskAndListIt() {
			CronTask task = new CronTask("test-1", "*/5 * * * *", "test prompt", true, false,
					Instant.now().plusSeconds(60), Instant.now());

			scheduler.schedule(task);

			List<CronTask> tasks = scheduler.listAll();
			assertThat(tasks).hasSize(1);
			assertThat(tasks.get(0).id()).isEqualTo("test-1");
		}

		@Test
		@DisplayName("Should schedule multiple tasks")
		void shouldScheduleMultipleTasks() {
			scheduler.schedule(
					new CronTask("t1", "0 * * * *", "p1", true, false, Instant.now().plusSeconds(30), Instant.now()));
			scheduler
				.schedule(new CronTask("t2", "30 * * * *", "p2", true, false, Instant.now().plusSeconds(60),
						Instant.now()));
			scheduler.schedule(
					new CronTask("t3", "0 9 * * *", "p3", true, false, Instant.now().plusSeconds(90), Instant.now()));

			assertThat(scheduler.listAll()).hasSize(3);
		}

		@Test
		@DisplayName("Should return empty list when no tasks scheduled")
		void shouldReturnEmptyListWhenNoTasks() {
			assertThat(scheduler.listAll()).isEmpty();
		}

	}

	@Nested
	@DisplayName("Cancel Tests")
	class CancelTests {

		@Test
		@DisplayName("Should cancel an existing task")
		void shouldCancelExistingTask() {
			CronTask task = new CronTask("to-cancel", "0 * * * *", "p", true, false, Instant.now().plusSeconds(120),
					Instant.now());
			scheduler.schedule(task);

			CronTask removed = scheduler.cancel("to-cancel");

			assertThat(removed).isNotNull();
			assertThat(removed.id()).isEqualTo("to-cancel");
			assertThat(scheduler.listAll()).isEmpty();
		}

		@Test
		@DisplayName("Should return null when cancelling non-existent task")
		void shouldReturnNullForNonExistentTask() {
			assertThat(scheduler.cancel("no-such-id")).isNull();
		}

	}

	@Nested
	@DisplayName("Fire Tests")
	class FireTests {

		@Test
		@DisplayName("Should fire a task whose time has arrived")
		void shouldFireTaskWhoseTimeHasArrived() throws InterruptedException {
			List<String> fired = new CopyOnWriteArrayList<>();
			CronScheduler fireScheduler = new CronScheduler(task -> {
				fired.add(task.id());
				return "done";
			});
			fireScheduler.start();

			try {
				// Schedule a task with fire time in the past (fires immediately)
				CronTask task = new CronTask("fire-now", "*/5 * * * *", "prompt", false, false,
						Instant.now().minusSeconds(1), Instant.now());
				fireScheduler.schedule(task);

				// Wait for fire
				Thread.sleep(500);

				assertThat(fired).contains("fire-now");
				// One-shot task should NOT be re-enqueued
				assertThat(fireScheduler.listAll()).isEmpty();
			}
			finally {
				fireScheduler.shutdown();
			}
		}

		@Test
		@DisplayName("Should re-enqueue recurring task after firing")
		void shouldReenqueueRecurringTask() throws InterruptedException {
			List<String> fired = new CopyOnWriteArrayList<>();
			CronScheduler fireScheduler = new CronScheduler(task -> {
				fired.add(task.id());
				return "done";
			});
			fireScheduler.start();

			try {
				// Schedule a recurring task with fire time in the past
				CronTask task = new CronTask("recurring-1", "*/5 * * * *", "prompt", true, false,
						Instant.now().minusSeconds(1), Instant.now());
				fireScheduler.schedule(task);

				// Wait for fire and re-enqueue
				Thread.sleep(500);

				assertThat(fired).contains("recurring-1");
				// Recurring task SHOULD be re-enqueued with a future fire time
				List<CronTask> remaining = fireScheduler.listAll();
				assertThat(remaining).hasSize(1);
				assertThat(remaining.get(0).id()).isEqualTo("recurring-1");
				assertThat(remaining.get(0).nextFireTime()).isAfter(Instant.now());
			}
			finally {
				fireScheduler.shutdown();
			}
		}

	}

	@Nested
	@DisplayName("Jitter Tests")
	class JitterTests {

		@Test
		@DisplayName("Should apply jitter for minute 0")
		void shouldApplyJitterForMinute0() {
			Instant base = Instant.parse("2026-05-31T09:00:00Z");
			// Run multiple times and check the result is within ±2 minutes
			boolean foundOffset = false;
			for (int i = 0; i < 20; i++) {
				Instant result = CronScheduler.applyJitter("0 9 * * *", base);
				long diffSeconds = result.getEpochSecond() - base.getEpochSecond();
				assertThat(diffSeconds).isBetween(-120L, 120L);
				if (diffSeconds != 0) {
					foundOffset = true;
				}
			}
			// At least some runs should have offset (probability ~80% per run with range -2..+2)
			assertThat(foundOffset).as("Jitter should produce offset at least sometimes").isTrue();
		}

		@Test
		@DisplayName("Should apply jitter for minute 30")
		void shouldApplyJitterForMinute30() {
			Instant base = Instant.parse("2026-05-31T09:30:00Z");
			boolean foundOffset = false;
			for (int i = 0; i < 20; i++) {
				Instant result = CronScheduler.applyJitter("30 14 * * *", base);
				long diffSeconds = result.getEpochSecond() - base.getEpochSecond();
				assertThat(diffSeconds).isBetween(-120L, 120L);
				if (diffSeconds != 0) {
					foundOffset = true;
				}
			}
			assertThat(foundOffset).isTrue();
		}

		@Test
		@DisplayName("Should NOT apply jitter for interval cron (*/5)")
		void shouldNotApplyJitterForIntervalCron() {
			Instant base = Instant.parse("2026-05-31T09:00:00Z");
			for (int i = 0; i < 10; i++) {
				Instant result = CronScheduler.applyJitter("*/5 * * * *", base);
				assertThat(result).isEqualTo(base);
			}
		}

		@Test
		@DisplayName("Should NOT apply jitter for non-0/30 minute")
		void shouldNotApplyJitterForNonSpecialMinute() {
			Instant base = Instant.parse("2026-05-31T09:15:00Z");
			for (int i = 0; i < 10; i++) {
				Instant result = CronScheduler.applyJitter("15 9 * * *", base);
				assertThat(result).isEqualTo(base);
			}
		}

		@Test
		@DisplayName("Should NOT apply jitter for comma-separated minutes")
		void shouldNotApplyJitterForCommaSeparatedMinutes() {
			Instant base = Instant.parse("2026-05-31T09:00:00Z");
			for (int i = 0; i < 10; i++) {
				Instant result = CronScheduler.applyJitter("0,30 9 * * *", base);
				assertThat(result).isEqualTo(base);
			}
		}

	}

	@Nested
	@DisplayName("Concurrency Tests")
	class ConcurrencyTests {

		@Test
		@DisplayName("Should handle concurrent schedule and cancel")
		void shouldHandleConcurrentScheduleAndCancel() throws InterruptedException {
			int taskCount = 50;
			CountDownLatch latch = new CountDownLatch(taskCount * 2);

			for (int i = 0; i < taskCount; i++) {
				final int index = i;
				new Thread(() -> {
					scheduler.schedule(new CronTask("t-" + index, "0 * * * *", "p", true, false,
							Instant.now().plusSeconds(3600), Instant.now()));
					latch.countDown();
				}).start();

				new Thread(() -> {
					scheduler.listAll();
					latch.countDown();
				}).start();
			}

			assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
			// All tasks should be scheduled (no data loss from concurrency)
			assertThat(scheduler.listAll()).hasSize(taskCount);
		}

	}

}
