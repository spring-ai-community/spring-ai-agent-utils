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

/**
 * Represents a scheduled cron task with its configuration and runtime state.
 *
 * @param id unique identifier (UUID)
 * @param cron 5-field cron expression ({@code minute hour dom month dow})
 * @param prompt the prompt to enqueue at each fire time
 * @param recurring {@code true} for recurring tasks, {@code false} for one-shot
 * @param durable {@code true} to persist across restarts
 * @param nextFireTime the next time this task should fire (may be {@code null} if
 * expired)
 * @param createdAt when this task was originally created (used for expiry)
 */
public record CronTask(String id, String cron, String prompt, boolean recurring, boolean durable, Instant nextFireTime,
		Instant createdAt) {

	/**
	 * Create a copy with an updated nextFireTime (used when recalculating for recurring
	 * tasks).
	 */
	public CronTask withNextFireTime(Instant newNextFireTime) {
		return new CronTask(this.id, this.cron, this.prompt, this.recurring, this.durable, newNextFireTime,
				this.createdAt);
	}

}
