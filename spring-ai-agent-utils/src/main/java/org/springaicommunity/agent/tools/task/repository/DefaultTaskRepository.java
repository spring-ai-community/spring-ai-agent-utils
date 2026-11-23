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

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Default implementation of TaskRepository that manages background tasks using a thread
 * pool. By default at most {@link #DEFAULT_MAX_CONCURRENT_TASKS} tasks run at once; the
 * ones submitted beyond that wait for a free slot, and a task that waits for another
 * background task's result should not fill the pool. Idle threads end after a minute.
 * <p>
 * Tasks run on the pool's threads, without the submitting thread's thread-local context
 * (a security context, for example). Where a task needs it, pass an
 * {@link java.util.concurrent.ExecutorService} that propagates it.
 *
 * @author Christian Tzolov
 */
public class DefaultTaskRepository implements TaskRepository {

	private final Map<String, BackgroundTask> backgroundTasks = new ConcurrentHashMap<>();

	private final ExecutorService executor;

	private final boolean ownsExecutor;

	/** How many background tasks run at once by default. */
	public static final int DEFAULT_MAX_CONCURRENT_TASKS = 25;

	/**
	 * Creates a repository that runs at most {@link #DEFAULT_MAX_CONCURRENT_TASKS} tasks at
	 * once.
	 */
	public DefaultTaskRepository() {
		this(DEFAULT_MAX_CONCURRENT_TASKS);
	}

	/**
	 * Creates a repository that runs at most the given number of tasks at once; later ones
	 * wait for a free slot.
	 * @param maxConcurrentTasks the cap; must be positive
	 */
	public DefaultTaskRepository(int maxConcurrentTasks) {
		this(boundedExecutor(maxConcurrentTasks), true);
	}

	private static ExecutorService boundedExecutor(int maxConcurrentTasks) {
		if (maxConcurrentTasks <= 0) {
			throw new IllegalArgumentException("maxConcurrentTasks must be positive");
		}
		ThreadPoolExecutor executor = new ThreadPoolExecutor(maxConcurrentTasks, maxConcurrentTasks, 60L,
				TimeUnit.SECONDS, new LinkedBlockingQueue<>(), r -> {
					Thread thread = new Thread(r);
					thread.setDaemon(true);
					thread.setName("background-task-" + thread.getId());
					return thread;
				});
		// Idle threads end after a minute, as the cached pool's did, so an idle
		// repository holds no threads
		executor.allowCoreThreadTimeOut(true);
		return executor;
	}

	/**
	 * Creates a repository with a custom executor service.
	 * @param executor the executor service to use for running tasks
	 */
	public DefaultTaskRepository(ExecutorService executor) {
		this(executor, false);
	}

	/**
	 * Internal constructor for specifying executor ownership.
	 * @param executor the executor service
	 * @param ownsExecutor whether this repository owns the executor and should shut it
	 * down
	 */
	public DefaultTaskRepository(ExecutorService executor, boolean ownsExecutor) {
		this.executor = executor;
		this.ownsExecutor = ownsExecutor;
	}

	@Override
	public BackgroundTask getTasks(String taskId) {
		return this.backgroundTasks.get(taskId);
	}

	@Override
	public BackgroundTask putTask(String taskId, Supplier<String> taskExecution) {
		var future = CompletableFuture.supplyAsync(taskExecution, this.executor);
		BackgroundTask backgroundTask = new BackgroundTask(taskId, future);
		this.backgroundTasks.put(taskId, backgroundTask);
		return backgroundTask;
	}

	@Override
	public void removeTask(String taskId) {
		this.backgroundTasks.remove(taskId);
	}

	@Override
	public void clear() {
		this.backgroundTasks.clear();
	}

	
	public void clearCompletedTasks() {
		this.backgroundTasks.entrySet().removeIf(entry -> entry.getValue().isCompleted());
	}

	/**
	 * Shutdown the executor service if this repository owns it. This method should be
	 * called when the repository is no longer needed to ensure proper cleanup of threads.
	 */
	public void shutdown() {
		if (this.ownsExecutor && this.executor != null) {
			this.executor.shutdown();
			try {
				if (!this.executor.awaitTermination(60, TimeUnit.SECONDS)) {
					this.executor.shutdownNow();
				}
			}
			catch (InterruptedException e) {
				this.executor.shutdownNow();
				Thread.currentThread().interrupt();
			}
		}
	}

}
