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
package org.springaicommunity.agent.dream;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.agent.tools.task.repository.DefaultTaskRepository;
import org.springaicommunity.agent.tools.task.repository.TaskRepository;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.client.advisor.api.BaseChatMemoryAdvisor;
import org.springframework.util.Assert;

/**
 * A {@code ChatClient} advisor that schedules periodic, out-of-band Auto-Dream memory
 * consolidation on top of an {@code AutoMemoryTools} memory store.
 *
 * <p>
 * Unlike {@code AutoMemoryToolsAdvisor.memoryConsolidationTrigger} — an in-band nudge that
 * asks the <em>live</em> agent to tidy up during its <em>current</em> turn —
 * {@code AutoDreamAdvisor} never touches the live request/response. {@link #before} is a
 * no-op, and {@link #after} only ever updates persisted trigger state and, when the
 * configured {@link DreamTrigger} fires, submits a background task via
 * {@link TaskRepository} that runs the dream cycle off the calling thread. The two
 * mechanisms are complementary and are meant to be used together, both pointed at the
 * same memories root directory.
 *
 * <p>
 * {@link DreamState#sessionsSinceLastDream()} here counts conversation turns processed
 * by this advisor instance, not multi-turn "sessions" in any broader sense — there is no
 * dependency on session-transcript storage for triggering.
 *
 * <p>
 * Optionally set {@link Builder#userId(String)} to also enable cross-session recall for
 * the dream cycles this advisor triggers, provided the {@code dreamService} was built
 * with {@code AutoDreamService.Builder#sessionService(...)} configured — see
 * {@link AutoDreamService#runDreamCycle(String, String)}.
 *
 * @author Christian Tzolov
 */
public class AutoDreamAdvisor implements BaseChatMemoryAdvisor {

	private static final Logger logger = LoggerFactory.getLogger(AutoDreamAdvisor.class);

	private final int order;

	private final String memoriesRootDirectory;

	private final DreamTrigger dreamTrigger;

	private final AutoDreamService dreamService;

	private final TaskRepository taskRepository;

	private final String userId;

	private AutoDreamAdvisor(int order, String memoriesRootDirectory, DreamTrigger dreamTrigger,
			AutoDreamService dreamService, TaskRepository taskRepository, String userId) {
		this.order = order;
		this.memoriesRootDirectory = memoriesRootDirectory;
		this.dreamTrigger = dreamTrigger;
		this.dreamService = dreamService;
		this.taskRepository = taskRepository;
		this.userId = userId;
	}

	@Override
	public ChatClientRequest before(ChatClientRequest chatClientRequest, AdvisorChain advisorChain) {
		// Dreaming is entirely out-of-band — the live request is never modified.
		return chatClientRequest;
	}

	@Override
	public ChatClientResponse after(ChatClientResponse chatClientResponse, AdvisorChain advisorChain) {

		Path memoriesDir = Paths.get(this.memoriesRootDirectory);
		DreamState state = DreamState.load(memoriesDir).withSessionIncrement();
		state.save(memoriesDir);

		if (this.dreamTrigger.shouldDream(state, Instant.now())) {
			String taskId = "dream_" + UUID.randomUUID();
			this.taskRepository.putTask(taskId, () -> {
				DreamResult result = this.dreamService.runDreamCycle(this.memoriesRootDirectory, this.userId);
				logger.info("Dream cycle [{}] for '{}' finished with status={}: {}", taskId,
						this.memoriesRootDirectory, result.status(), result.summary());
				return result.summary();
			});
		}

		return chatClientResponse;
	}

	@Override
	public int getOrder() {
		return this.order;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {

		// Higher precedence than AutoMemoryToolsAdvisor's default (+200) so this
		// advisor's after() — which only fires a background task — wraps outermost.
		private int order = BaseAdvisor.HIGHEST_PRECEDENCE + 150;

		private String memoriesRootDirectory = "";

		private DreamTrigger dreamTrigger = DreamTriggers.manualOnly();

		private AutoDreamService dreamService;

		private TaskRepository taskRepository = new DefaultTaskRepository();

		private String userId;

		private Builder() {
		}

		public Builder order(int order) {
			this.order = order;
			return this;
		}

		public Builder memoriesRootDirectory(String memoriesRootDirectory) {
			this.memoriesRootDirectory = memoriesRootDirectory;
			return this;
		}

		public Builder dreamTrigger(DreamTrigger dreamTrigger) {
			Assert.notNull(dreamTrigger, "dreamTrigger must not be null");
			this.dreamTrigger = dreamTrigger;
			return this;
		}

		public Builder dreamService(AutoDreamService dreamService) {
			Assert.notNull(dreamService, "dreamService must not be null");
			this.dreamService = dreamService;
			return this;
		}

		public Builder taskRepository(TaskRepository taskRepository) {
			Assert.notNull(taskRepository, "taskRepository must not be null");
			this.taskRepository = taskRepository;
			return this;
		}

		/**
		 * Optional. When set, dream cycles triggered by this advisor call
		 * {@code AutoDreamService.runDreamCycle(memoriesRootDirectory, userId)} instead of
		 * the single-argument overload, enabling cross-session recall for the Dreamer —
		 * provided {@code dreamService} was itself built with a {@code sessionService}
		 * configured. Has no effect otherwise.
		 */
		public Builder userId(String userId) {
			this.userId = userId;
			return this;
		}

		public AutoDreamAdvisor build() {
			Assert.hasText(this.memoriesRootDirectory, "memoriesRootDirectory must not be empty");
			Assert.notNull(this.dreamService, "dreamService must be provided");
			return new AutoDreamAdvisor(this.order, this.memoriesRootDirectory, this.dreamTrigger, this.dreamService,
					this.taskRepository, this.userId);
		}

	}

}
