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
package org.springaicommunity.agent.tools.task.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link DefaultTaskRepository}.
 *
 * @author Christian Tzolov
 */
@DisplayName("DefaultTaskRepository Tests")
class DefaultTaskRepositoryTest {

	private DefaultTaskRepository repository;

	@BeforeEach
	void setUp() {
		repository = new DefaultTaskRepository();
	}

	@AfterEach
	void tearDown() {
		if (repository != null) {
			repository.shutdown();
		}
	}

	@Nested
	@DisplayName("Basic Operations")
	class BasicOperationsTests {

		@Test
		@DisplayName("Should put and get task")
		void shouldPutAndGetTask() {
			// Given
			String taskId = "task-1";

			// When
			BackgroundTask task = repository.putTask(taskId, () -> "result");
			BackgroundTask retrieved = repository.getTasks(taskId);

			// Then
			assertThat(retrieved).isNotNull();
			assertThat(retrieved).isSameAs(task);
			assertThat(retrieved.getTaskId()).isEqualTo(taskId);
		}

		@Test
		@DisplayName("Should remove task")
		void shouldRemoveTask() {
			// Given
			String taskId = "task-to-remove";
			repository.putTask(taskId, () -> "result");

			// When
			repository.removeTask(taskId);
			BackgroundTask retrieved = repository.getTasks(taskId);

			// Then
			assertThat(retrieved).isNull();
		}

		@Test
		@DisplayName("Should clear all tasks")
		void shouldClearAllTasks() {
			// Given
			repository.putTask("task-1", () -> "result");
			repository.putTask("task-2", () -> "result");
			repository.putTask("task-3", () -> "result");

			// When
			repository.clear();

			// Then
			assertThat(repository.getTasks("task-1")).isNull();
			assertThat(repository.getTasks("task-2")).isNull();
			assertThat(repository.getTasks("task-3")).isNull();
		}

	}

	@Nested
	@DisplayName("Multiple Tasks")
	class MultipleTasksTests {

		@Test
		@DisplayName("Should update existing task")
		void shouldUpdateExistingTask() {
			// Given
			String taskId = "updatable-task";
			BackgroundTask originalTask = repository.putTask(taskId, () -> "original");

			// When
			BackgroundTask newTask = repository.putTask(taskId, () -> "new");

			// Then
			BackgroundTask retrieved = repository.getTasks(taskId);
			assertThat(retrieved).isSameAs(newTask);
			assertThat(retrieved).isNotSameAs(originalTask);
		}

		@Test
		@DisplayName("Should handle many tasks")
		void shouldHandleManyTasks() {
			// Given
			int taskCount = 1000;
			List<BackgroundTask> tasks = new ArrayList<>();

			// When
			for (int i = 0; i < taskCount; i++) {
				String taskId = "task-" + i;
				final int index = i;
				BackgroundTask task = repository.putTask(taskId, () -> "result-" + index);
				tasks.add(task);
			}

			// Then
			for (int i = 0; i < taskCount; i++) {
				String taskId = "task-" + i;
				BackgroundTask retrieved = repository.getTasks(taskId);
				assertThat(retrieved).isSameAs(tasks.get(i));
			}
		}

	}

	@Nested
	@DisplayName("Thread Safety")
	class ThreadSafetyTests {

		@Test
		@DisplayName("Should handle concurrent puts")
		void shouldHandleConcurrentPuts() throws InterruptedException {
			// Given
			int threadCount = 10;
			int tasksPerThread = 100;
			CountDownLatch latch = new CountDownLatch(threadCount);
			AtomicInteger successCount = new AtomicInteger(0);

			// When - multiple threads adding tasks concurrently
			for (int i = 0; i < threadCount; i++) {
				final int threadId = i;
				new Thread(() -> {
					for (int j = 0; j < tasksPerThread; j++) {
						String taskId = "thread-" + threadId + "-task-" + j;
						repository.putTask(taskId, () -> "result");
						successCount.incrementAndGet();
					}
					latch.countDown();
				}).start();
			}

			// Then
			latch.await(5, TimeUnit.SECONDS);
			assertThat(successCount.get()).isEqualTo(threadCount * tasksPerThread);

			// Verify all tasks are retrievable
			for (int i = 0; i < threadCount; i++) {
				for (int j = 0; j < tasksPerThread; j++) {
					String taskId = "thread-" + i + "-task-" + j;
					assertThat(repository.getTasks(taskId)).isNotNull();
				}
			}
		}

		@Test
		@DisplayName("Should handle concurrent gets")
		void shouldHandleConcurrentGets() throws InterruptedException {
			// Given
			String taskId = "shared-task";
			BackgroundTask task = repository.putTask(taskId, () -> "result");

			int threadCount = 20;
			CountDownLatch latch = new CountDownLatch(threadCount);
			AtomicInteger successCount = new AtomicInteger(0);

			// When - multiple threads reading same task
			for (int i = 0; i < threadCount; i++) {
				new Thread(() -> {
					BackgroundTask retrieved = repository.getTasks(taskId);
					if (retrieved == task) {
						successCount.incrementAndGet();
					}
					latch.countDown();
				}).start();
			}

			// Then
			latch.await(2, TimeUnit.SECONDS);
			assertThat(successCount.get()).isEqualTo(threadCount);
		}

		@Test
		@DisplayName("Should handle concurrent puts and gets")
		void shouldHandleConcurrentPutsAndGets() throws InterruptedException {
			// Given
			int operationCount = 100;
			CountDownLatch latch = new CountDownLatch(operationCount * 2);

			// When - concurrent puts and gets
			for (int i = 0; i < operationCount; i++) {
				final int index = i;

				// Put thread
				new Thread(() -> {
					String taskId = "task-" + index;
					repository.putTask(taskId, () -> "result-" + index);
					latch.countDown();
				}).start();

				// Get thread
				new Thread(() -> {
					repository.getTasks("task-" + index);
					latch.countDown();
				}).start();
			}

			// Then - should complete without errors
			boolean completed = latch.await(5, TimeUnit.SECONDS);
			assertThat(completed).isTrue();
		}

		@Test
		@DisplayName("Should handle concurrent removes")
		void shouldHandleConcurrentRemoves() throws InterruptedException {
			// Given
			int taskCount = 50;
			for (int i = 0; i < taskCount; i++) {
				String taskId = "removable-task-" + i;
				final int index = i;
				repository.putTask(taskId, () -> "result-" + index);
			}

			CountDownLatch latch = new CountDownLatch(taskCount);

			// When - concurrent removes
			for (int i = 0; i < taskCount; i++) {
				final int index = i;
				new Thread(() -> {
					repository.removeTask("removable-task-" + index);
					latch.countDown();
				}).start();
			}

			// Then
			latch.await(2, TimeUnit.SECONDS);

			// Verify all removed
			for (int i = 0; i < taskCount; i++) {
				assertThat(repository.getTasks("removable-task-" + i)).isNull();
			}
		}

		@Test
		@DisplayName("Should handle concurrent clear operations")
		void shouldHandleConcurrentClearOperations() throws InterruptedException {
			// Given
			for (int i = 0; i < 100; i++) {
				final int index = i;
				repository.putTask("task-" + i, () -> "result-" + index);
			}

			int threadCount = 5;
			CountDownLatch latch = new CountDownLatch(threadCount);

			// When - multiple threads clearing
			for (int i = 0; i < threadCount; i++) {
				new Thread(() -> {
					repository.clear();
					latch.countDown();
				}).start();
			}

			// Then - should complete without errors
			latch.await(2, TimeUnit.SECONDS);
			assertThat(repository.getTasks("task-0")).isNull();
		}

	}

	@Nested
	@DisplayName("Repository State")
	class RepositoryStateTests {

		@Test
		@DisplayName("Should maintain independent instances")
		void shouldMaintainIndependentInstances() {
			// Given
			DefaultTaskRepository repo1 = new DefaultTaskRepository();
			DefaultTaskRepository repo2 = new DefaultTaskRepository();

			// When
			BackgroundTask task1 = repo1.putTask("task-1", () -> "result1");
			BackgroundTask task2 = repo2.putTask("task-2", () -> "result2");

			// Then
			assertThat(repo1.getTasks("task-1")).isSameAs(task1);
			assertThat(repo1.getTasks("task-2")).isNull();

			assertThat(repo2.getTasks("task-2")).isSameAs(task2);
			assertThat(repo2.getTasks("task-1")).isNull();

			// Cleanup
			repo1.shutdown();
			repo2.shutdown();
		}

	}


	@Test
	@DisplayName("Should run at most the configured number of tasks at once")
	void shouldRunAtMostTheConfiguredNumberOfTasksAtOnce() throws InterruptedException {
		DefaultTaskRepository bounded = new DefaultTaskRepository(1);
		CountDownLatch firstStarted = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		try {
			bounded.putTask("first", () -> {
				firstStarted.countDown();
				try {
					release.await(5, TimeUnit.SECONDS);
				}
				catch (InterruptedException ex) {
					Thread.currentThread().interrupt();
				}
				return "first";
			});
			BackgroundTask second = bounded.putTask("second", () -> "second");

			assertThat(firstStarted.await(5, TimeUnit.SECONDS)).isTrue();
			// The second waits for the slot the first holds
			assertThat(second.waitForCompletion(200)).isFalse();
			release.countDown();
			assertThat(second.waitForCompletion(5000)).isTrue();
			assertThat(second.getResult()).isEqualTo("second");
		}
		finally {
			bounded.shutdown();
		}
	}

}
