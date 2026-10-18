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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link Heartbeat}.
 *
 * @author Lyin-OTTF
 */
@DisplayName("Heartbeat Tests")
class HeartbeatTest {

	private static final Duration SHORT_INTERVAL = Duration.ofMillis(50);

	private final ExecutorService executor = Executors.newCachedThreadPool();

	@AfterEach
	void tearDown() {
		this.executor.shutdownNow();
	}

	@Nested
	@DisplayName("Constructor Validation")
	class ConstructorValidation {

		@Test
		@DisplayName("Should reject null interval")
		void shouldRejectNullInterval() {
			assertThatThrownBy(() -> new Heartbeat(null, () -> {
			})).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("interval");
		}

		@Test
		@DisplayName("Should reject null beat callback")
		void shouldRejectNullBeatCallback() {
			assertThatThrownBy(() -> new Heartbeat(SHORT_INTERVAL, null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("onBeat");
		}

		@Test
		@DisplayName("Should reject non-positive interval")
		void shouldRejectNonPositiveInterval() {
			assertThatThrownBy(() -> new Heartbeat(Duration.ZERO, () -> {
			})).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("positive");
			assertThatThrownBy(() -> new Heartbeat(Duration.ofSeconds(-1), () -> {
			})).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("positive");
		}

	}

	@Nested
	@DisplayName("Beat Emission")
	class BeatEmission {

		@Test
		@DisplayName("Should fire the immediate beat before the blocking call starts")
		void shouldFireImmediateBeatBeforeBlockingCall() throws Exception {
			List<String> events = Collections.synchronizedList(new ArrayList<>());
			CountDownLatch callStarted = new CountDownLatch(1);
			CountDownLatch releaseCall = new CountDownLatch(1);

			// A long interval keeps the periodic beats out of the picture, so the only
			// beat before the call can be the immediate one.
			Heartbeat heartbeat = new Heartbeat(Duration.ofMinutes(1), () -> events.add("beat"));

			Future<String> result = HeartbeatTest.this.executor.submit(() -> heartbeat.await(() -> {
				events.add("call");
				callStarted.countDown();
				try {
					releaseCall.await(5, TimeUnit.SECONDS);
				}
				catch (InterruptedException ex) {
					Thread.currentThread().interrupt();
				}
				return "answered";
			}));

			assertThat(callStarted.await(5, TimeUnit.SECONDS)).isTrue();
			assertThat(events).containsExactly("beat", "call");

			releaseCall.countDown();
			assertThat(result.get(5, TimeUnit.SECONDS)).isEqualTo("answered");
		}

		@Test
		@DisplayName("Should emit periodic beats while the call is pending")
		void shouldEmitPeriodicBeatsWhilePending() throws Exception {
			AtomicInteger beats = new AtomicInteger();
			CountDownLatch releaseCall = new CountDownLatch(1);

			Heartbeat heartbeat = new Heartbeat(SHORT_INTERVAL, beats::incrementAndGet);

			Future<String> result = HeartbeatTest.this.executor.submit(() -> heartbeat.await(() -> {
				try {
					releaseCall.await(5, TimeUnit.SECONDS);
				}
				catch (InterruptedException ex) {
					Thread.currentThread().interrupt();
				}
				return "answered";
			}));

			// 1 immediate beat + several periodic beats over ~250ms
			awaitUntil(() -> beats.get() >= 3, 5, TimeUnit.SECONDS);
			assertThat(beats.get()).isGreaterThanOrEqualTo(3);

			releaseCall.countDown();
			assertThat(result.get(5, TimeUnit.SECONDS)).isEqualTo("answered");
		}

		@Test
		@DisplayName("Should stop beating once the call returns")
		void shouldStopBeatingWhenCallReturns() throws Exception {
			AtomicInteger beats = new AtomicInteger();
			Heartbeat heartbeat = new Heartbeat(SHORT_INTERVAL, beats::incrementAndGet);

			String result = heartbeat.await(() -> "answered");

			assertThat(result).isEqualTo("answered");
			assertThat(beats.get()).isEqualTo(1);

			int beatsAfterReturn = beats.get();
			Thread.sleep(5 * SHORT_INTERVAL.toMillis());
			assertThat(beats.get()).isEqualTo(beatsAfterReturn);
		}

		@Test
		@DisplayName("Should stop beating once the call throws")
		void shouldStopBeatingWhenCallThrows() {
			AtomicInteger beats = new AtomicInteger();
			Heartbeat heartbeat = new Heartbeat(SHORT_INTERVAL, beats::incrementAndGet);

			RuntimeException failure = new IllegalStateException("user input failed");
			assertThatThrownBy(() -> heartbeat.await(() -> {
				throw failure;
			})).isSameAs(failure);

			assertThat(beats.get()).isEqualTo(1);
			int beatsAfterFailure = beats.get();
			try {
				Thread.sleep(5 * SHORT_INTERVAL.toMillis());
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
			assertThat(beats.get()).isEqualTo(beatsAfterFailure);
		}

		@Test
		@DisplayName("Should pass the blocking call's result through untouched")
		void shouldPassResultThrough() {
			Heartbeat heartbeat = new Heartbeat(Duration.ofMinutes(1), () -> {
			});
			assertThat(heartbeat.await(() -> 42)).isEqualTo(42);
		}

	}

	@Nested
	@DisplayName("Failure Policy")
	class FailurePolicy {

		@Test
		@DisplayName("Should propagate an immediate beat failure before the call runs")
		void shouldPropagateImmediateBeatFailure() {
			AtomicInteger calls = new AtomicInteger();
			RuntimeException beatFailure = new IllegalStateException("sse is gone");
			Heartbeat heartbeat = new Heartbeat(Duration.ofMinutes(1), () -> {
				throw beatFailure;
			});

			assertThatThrownBy(() -> heartbeat.await(calls::incrementAndGet)).isSameAs(beatFailure);
			assertThat(calls.get()).isZero();
		}

		@Test
		@DisplayName("Should keep beating when a periodic beat fails")
		void shouldKeepBeatingWhenPeriodicBeatFails() throws Exception {
			AtomicInteger beats = new AtomicInteger();
			AtomicInteger callReturned = new AtomicInteger();
			CountDownLatch releaseCall = new CountDownLatch(1);

			// Succeed for the immediate beat and the first periodic one, fail afterwards.
			Heartbeat heartbeat = new Heartbeat(SHORT_INTERVAL, () -> {
				if (beats.incrementAndGet() > 2) {
					throw new IllegalStateException("sse is gone");
				}
			});

			Future<Integer> result = HeartbeatTest.this.executor.submit(() -> heartbeat.await(() -> {
				try {
					releaseCall.await(5, TimeUnit.SECONDS);
				}
				catch (InterruptedException ex) {
					Thread.currentThread().interrupt();
				}
				return callReturned.incrementAndGet();
			}));

			// Beats keep coming past the failures.
			awaitUntil(() -> beats.get() >= 4, 5, TimeUnit.SECONDS);

			releaseCall.countDown();
			assertThat(result.get(5, TimeUnit.SECONDS)).isEqualTo(1);
		}

	}

	@Nested
	@DisplayName("Threading")
	class Threading {

		@Test
		@DisplayName("Should not leak heartbeat threads")
		void shouldNotLeakHeartbeatThreads() throws InterruptedException {
			Heartbeat heartbeat = new Heartbeat(SHORT_INTERVAL, () -> {
			});

			for (int i = 0; i < 5; i++) {
				heartbeat.await(() -> "answered");
			}

			awaitUntil(() -> !hasLiveHeartbeatThread(), 5, TimeUnit.SECONDS);
			assertThat(hasLiveHeartbeatThread()).isFalse();
		}

		@Test
		@DisplayName("Should supervise concurrent waits independently")
		void shouldSuperviseConcurrentWaits() throws Exception {
			AtomicInteger beats = new AtomicInteger();
			CountDownLatch releaseCalls = new CountDownLatch(2);
			Heartbeat heartbeat = new Heartbeat(SHORT_INTERVAL, beats::incrementAndGet);

			Supplier<Integer> wait = () -> {
				try {
					releaseCalls.await(5, TimeUnit.SECONDS);
				}
				catch (InterruptedException ex) {
					Thread.currentThread().interrupt();
				}
				return 7;
			};
			Future<Integer> first = HeartbeatTest.this.executor.submit(() -> heartbeat.await(wait));
			Future<Integer> second = HeartbeatTest.this.executor.submit(() -> heartbeat.await(wait));

			// Both waits are pending, so both heartbeat schedulers are running.
			awaitUntil(() -> beats.get() >= 2, 5, TimeUnit.SECONDS);

			releaseCalls.countDown();
			assertThat(first.get(5, TimeUnit.SECONDS)).isEqualTo(7);
			assertThat(second.get(5, TimeUnit.SECONDS)).isEqualTo(7);
		}

	}

	private static boolean hasLiveHeartbeatThread() {
		return Thread.getAllStackTraces().keySet().stream()
			.anyMatch(thread -> thread.getName().equals(Heartbeat.THREAD_NAME) && thread.isAlive());
	}

	private static void awaitUntil(java.util.function.BooleanSupplier condition, long timeout, TimeUnit unit)
			throws InterruptedException {
		long deadline = System.nanoTime() + unit.toNanos(timeout);
		while (System.nanoTime() < deadline) {
			if (condition.getAsBoolean()) {
				return;
			}
			Thread.sleep(10);
		}
	}

}
