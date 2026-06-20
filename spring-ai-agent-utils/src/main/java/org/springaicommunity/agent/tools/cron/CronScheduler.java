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

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.support.CronExpression;

/**
 * Single-threaded scheduling engine that fires {@link CronTask} instances according to
 * their cron expressions.
 *
 * <p>
 * Runs a daemon thread that continuously checks the earliest upcoming task, sleeps until
 * its fire time, and then invokes the {@link CronEventHandler}. Recurring tasks are
 * re-enqueued with their next computed fire time. The scheduler is notified of insertions
 * and deletions via {@link Object#notify()} so it can immediately recalculate.
 * </p>
 *
 * <p>
 * <b>Jitter:</b> Recurring tasks whose minute field is exactly 0 or 30 receive a small
 * random offset (±2 minutes) to avoid thundering-herd effects. Tasks using intervals
 * ({@code *&#47;N}) or specific minutes are not jittered.
 * </p>
 *
 * <p>
 * <b>Expiry:</b> Durable recurring tasks auto-expire 7 days after creation.
 * </p>
 *
 * @author Christian Tzolov
 */
public class CronScheduler implements Runnable {

	private static final Logger logger = LoggerFactory.getLogger(CronScheduler.class);

	private static final Duration EXPIRY_DURATION = Duration.ofDays(7);

	private final PriorityBlockingQueue<CronTask> taskQueue;

	private final CronEventHandler handler;

	private final Object lock = new Object();

	private final AtomicBoolean running = new AtomicBoolean(true);

	public CronScheduler(CronEventHandler handler) {
		this.handler = handler;
		this.taskQueue = new PriorityBlockingQueue<>(11, (a, b) -> {
			Instant aTime = a.nextFireTime();
			Instant bTime = b.nextFireTime();
			if (aTime == null && bTime == null)
				return 0;
			if (aTime == null)
				return -1;
			if (bTime == null)
				return 1;
			return aTime.compareTo(bTime);
		});
	}

	/**
	 * Start the scheduler in a daemon thread.
	 */
	public void start() {
		Thread schedulerThread = new Thread(this, "cron-scheduler");
		schedulerThread.setDaemon(true);
		schedulerThread.start();
		logger.debug("CronScheduler started");
	}

	/**
	 * Add a task to the queue and wake the scheduler.
	 */
	public void schedule(CronTask task) {
		taskQueue.offer(task);
		synchronized (lock) {
			lock.notify();
		}
		logger.debug("Scheduled task: {} (next fire: {})", task.id(), task.nextFireTime());
	}

	/**
	 * Remove a task by id.
	 * @return the removed task, or {@code null} if not found
	 */
	public CronTask cancel(String id) {
		for (CronTask task : taskQueue) {
			if (task.id().equals(id)) {
				taskQueue.remove(task);
				synchronized (lock) {
					lock.notify();
				}
				logger.debug("Cancelled task: {}", id);
				return task;
			}
		}
		return null;
	}

	/**
	 * Return a snapshot of all currently queued tasks.
	 */
	public List<CronTask> listAll() {
		return List.copyOf(taskQueue);
	}

	/**
	 * Shut down the scheduler. The current thread will finish its current sleep and exit
	 * gracefully.
	 */
	public void shutdown() {
		running.set(false);
		synchronized (lock) {
			lock.notify();
		}
		logger.debug("CronScheduler shutdown requested");
	}

	@Override
	public void run() {
		while (running.get()) {
			try {
				CronTask next = taskQueue.peek();
				if (next == null) {
					synchronized (lock) {
						lock.wait();
					}
					continue;
				}

				Instant fireTime = next.nextFireTime();
				if (fireTime == null) {
					// Task with no fire time — remove and skip
					taskQueue.poll();
					continue;
				}

				long delayMs = Duration.between(Instant.now(), fireTime).toMillis();
				if (delayMs > 0) {
					synchronized (lock) {
						// Wait at most delayMs, but can be woken early by schedule()/cancel()
						lock.wait(Math.min(delayMs, 60_000)); // Check every 60s max
					}
					continue;
				}

				// Time to fire
				taskQueue.poll(); // Remove from queue

				// Check expiry for durable recurring tasks
				if (next.recurring() && next.durable() && next.createdAt() != null) {
					if (Duration.between(next.createdAt(), Instant.now()).compareTo(EXPIRY_DURATION) > 0) {
						logger.info("Task {} expired (created {}), removing", next.id(), next.createdAt());
						continue;
					}
				}

				// Fire the task
				try {
					logger.debug("Firing task: {} (prompt: {})", next.id(), next.prompt());
					String result = handler.handle(next);
					logger.debug("Task {} completed: {}", next.id(),
							result != null ? result.substring(0, Math.min(100, result.length())) : "null");
				}
				catch (Exception e) {
					logger.error("Error handling cron task: {}", next.id(), e);
				}

				// Re-enqueue if recurring
				if (next.recurring()) {
					CronExpression expr = CronExpression.parse(toSixField(next.cron()));
					ZonedDateTime now = ZonedDateTime.now();
					ZonedDateTime nextTimeZoned = expr.next(now);
					Instant nextTime = nextTimeZoned != null ? nextTimeZoned.toInstant() : null;
					if (nextTime != null) {
						// Apply jitter for on-the-hour / on-the-half-hour tasks
						nextTime = applyJitter(next.cron(), nextTime);
						taskQueue.offer(next.withNextFireTime(nextTime));
						logger.debug("Re-enqueued recurring task: {} (next: {})", next.id(), nextTime);
					}
					else {
						logger.info("Recurring task {} has no future fire times, removing", next.id());
					}
				}
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				logger.debug("CronScheduler interrupted");
				break;
			}
			catch (Exception e) {
				logger.error("Unexpected error in CronScheduler loop", e);
			}
		}
		logger.debug("CronScheduler stopped");
	}

	/**
	 * Apply random jitter to avoid thundering-herd effects.
	 *
	 * <p>
	 * Only applies when the user's cron specifies a single, fixed minute value of 0 or
	 * 30. Interval-based schedules ({@code *&#47;N}) and schedules with non-0/30 minute
	 * values pass through unchanged.
	 * </p>
	 */
	public static Instant applyJitter(String cron, Instant nextFireTime) {
		String minuteField = cron.trim().split("\\s+")[0];
		// Only jitter when user specified an exact minute of 0 or 30
		if (minuteField.equals("0") || minuteField.equals("30")) {
			int offsetMinutes = ThreadLocalRandom.current().nextInt(-2, 3); // -2..+2
			if (offsetMinutes != 0) {
				return nextFireTime.plus(Duration.ofMinutes(offsetMinutes));
			}
		}
		return nextFireTime;
	}

	/**
	 * Convert a 5-field cron expression to Spring's required 6-field format by
	 * prepending "0 " as the seconds field. If the expression already has 6 fields it is
	 * returned unchanged.
	 * @param cron the cron expression (5 or 6 fields)
	 * @return a 6-field cron expression
	 */
	public static String toSixField(String cron) {
		String trimmed = cron.trim();
		String[] fields = trimmed.split("\\s+");
		if (fields.length == 5) {
			return "0 " + trimmed;
		}
		return trimmed;
	}

}
