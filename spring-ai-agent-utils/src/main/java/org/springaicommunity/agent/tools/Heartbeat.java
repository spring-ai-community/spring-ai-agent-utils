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

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.util.Assert;

/**
 * Liveness signal for tool calls that block waiting for human input.
 *
 * <p>
 * Interactive tools — {@link AskUserQuestionTool} above all — publish an event to the
 * client and then block until a separate request carries the answer. In real deployments
 * the frame carrying that event is frequently buffered by CDNs and reverse proxies (nginx
 * without {@code X-Accel-Buffering: no}, Cloudflare, AWS ALB, ...): the client never
 * receives the event, never submits an answer, and the turn <em>deadlocks</em> until the
 * wait times out. The established mitigation is to publish heartbeats — immediately after
 * the interactive event and periodically while it stays pending — which push the buffered
 * frames through the proxy.
 *
 * <p>
 * This class makes that mitigation a framework concern instead of application
 * boilerplate. It is <strong>transport-agnostic</strong>: the framework only invokes
 * {@link #onBeat()}; the application decides what to do with the signal — flush an SSE
 * heartbeat frame, send a WebSocket ping, emit a metric, write a log line.
 *
 * <pre>{@code
 * AskUserQuestionTool tool = AskUserQuestionTool.builder()
 *         .questionHandler(this::handleQuestions)
 *         .heartbeat(Duration.ofSeconds(15), () -> sse.heartbeat())
 *         .build();
 * }</pre>
 *
 * <p>
 * Beats are emitted on a dedicated daemon thread, so a slow or stuck beat callback can
 * never block the waiting call, and a pending wait never holds back JVM shutdown. The
 * waiting call itself runs on the calling thread, and its result — or exception — is
 * passed through untouched. Beats stop as soon as the call completes, successfully or
 * not.
 *
 * <p>
 * <b>Failure policy.</b> A periodic beat that throws does not disturb the waiting call
 * and does not stop the remaining beats — the failure is logged and heartbeats continue,
 * because a broken beat transport is exactly the condition heartbeats exist to surface.
 * The immediate beat, by contrast, runs on the calling thread before the wait starts and
 * its exceptions propagate, matching the loud-failure contract of
 * {@link org.springaicommunity.agent.tools.ToolCallListener#beforeCall}.
 *
 * @author Lyin-OTTF
 * @see AskUserQuestionTool.Builder#heartbeat(Duration, Runnable)
 */
public record Heartbeat(Duration interval, Runnable onBeat) {

	private static final Logger logger = LoggerFactory.getLogger(Heartbeat.class);

	/** Name of the daemon thread that emits the periodic beats. */
	static final String THREAD_NAME = "agent-interaction-heartbeat";

	public Heartbeat {
		Assert.notNull(interval, "interval must not be null");
		Assert.notNull(onBeat, "onBeat must not be null");
		if (interval.isZero() || interval.isNegative()) {
			throw new IllegalArgumentException("interval must be positive, but was: " + interval);
		}
	}

	/**
	 * Runs the given blocking call on the calling thread while emitting heartbeats: once
	 * immediately, before the call starts, and then every {@link #interval()} until the
	 * call completes.
	 *
	 * <p>
	 * The immediate beat fires before the call is entered. Applications that publish the
	 * interactive event <em>inside</em> the call (the typical SSE pattern) can rely on the
	 * periodic beats to flush it, or invoke the beat callback themselves right after
	 * publishing for zero added latency.
	 * @param blockingCall the wait to supervise; must not be null
	 * @return whatever the blocking call returns
	 * @param <T> the blocking call's return type
	 */
	public <T> T await(Supplier<T> blockingCall) {
		Assert.notNull(blockingCall, "blockingCall must not be null");

		ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
			Thread thread = new Thread(runnable, THREAD_NAME);
			thread.setDaemon(true);
			return thread;
		});
		try {
			this.onBeat.run();

			ScheduledFuture<?> beats = scheduler.scheduleAtFixedRate(() -> {
				try {
					this.onBeat.run();
				}
				catch (RuntimeException ex) {
					logger.warn("Heartbeat callback failed; heartbeats continue", ex);
				}
			}, this.interval.toNanos(), this.interval.toNanos(), TimeUnit.NANOSECONDS);

			try {
				return blockingCall.get();
			}
			finally {
				beats.cancel(false);
			}
		}
		finally {
			scheduler.shutdown();
		}
	}

}
