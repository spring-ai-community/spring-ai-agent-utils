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
package org.springaicommunity.agent.utils;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;
import reactor.test.publisher.PublisherProbe;
import reactor.test.publisher.TestPublisher;

import org.springframework.core.ResolvableType;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.http.codec.ServerSentEventHttpMessageWriter;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class HeartbeatsTest {

	private static final Duration INTERVAL = Duration.ofSeconds(15);

	private static final Duration VERIFY_TIMEOUT = Duration.ofSeconds(5);

	@Test
	void sendsTheRequestBeforeTheImmediateAndPeriodicHeartbeats() {
		TestPublisher<String> answer = TestPublisher.create();

		StepVerifier.withVirtualTime(() -> Flux.just("question").concatWith(Heartbeats.until(answer, INTERVAL, "beat")))
			.expectNext("question", "beat")
			.expectNoEvent(INTERVAL.minusSeconds(1))
			.thenAwait(Duration.ofSeconds(1))
			.expectNext("beat")
			.thenAwait(INTERVAL)
			.expectNext("beat")
			.then(() -> answer.next("yes"))
			.expectComplete()
			.verify(VERIFY_TIMEOUT);
		answer.assertCancelled();
	}

	@Test
	void stopsWhenTheSignalCompletesWithoutAValue() {
		TestPublisher<Void> stop = TestPublisher.create();

		StepVerifier.withVirtualTime(() -> Heartbeats.until(stop, INTERVAL, "beat"))
			.expectNext("beat")
			.then(stop::complete)
			.expectComplete()
			.verify(VERIFY_TIMEOUT);
	}

	@Test
	void propagatesTheStopSignalError() {
		TestPublisher<Void> stop = TestPublisher.create();
		RuntimeException failure = new IllegalStateException("answer timed out");

		StepVerifier.withVirtualTime(() -> Heartbeats.until(stop, INTERVAL, "beat"))
			.expectNext("beat")
			.then(() -> stop.error(failure))
			.expectErrorMatches(error -> error == failure)
			.verify(VERIFY_TIMEOUT);
	}

	@Test
	void cancelsTheStopSubscriptionWhenTheClientDisconnects() {
		TestPublisher<Void> stop = TestPublisher.create();

		StepVerifier.withVirtualTime(() -> Heartbeats.until(stop, INTERVAL, "beat"))
			.expectNext("beat")
			.thenAwait(INTERVAL)
			.expectNext("beat")
			.thenCancel()
			.verify(VERIFY_TIMEOUT);
		stop.assertCancelled();
	}

	@Test
	void skipsPeriodicHeartbeatsDuringBackpressure() {
		TestPublisher<Void> stop = TestPublisher.create();

		StepVerifier.withVirtualTime(() -> Heartbeats.until(stop, INTERVAL, "beat"), 1)
			.expectNext("beat")
			.expectNoEvent(INTERVAL.multipliedBy(3))
			.thenRequest(1)
			.expectNoEvent(INTERVAL.minusSeconds(1))
			.thenAwait(Duration.ofSeconds(1))
			.expectNext("beat")
			.then(stop::complete)
			.expectComplete()
			.verify(VERIFY_TIMEOUT);
	}

	@Test
	void alreadyAnsweredQuestionSkipsTheFirstBeat() {
		StepVerifier.create(Heartbeats.until(Mono.just("yes"), INTERVAL, "beat"))
			.expectComplete()
			.verify(VERIFY_TIMEOUT);
	}

	@Test
	void observesAHotAnswerEmittedDuringTheFirstBeat() {
		Sinks.Many<String> answers = Sinks.many().multicast().directBestEffort();
		Flux<String> beats = Heartbeats.until(answers.asFlux(), INTERVAL, "beat")
			.doOnNext(beat -> assertThat(answers.tryEmitNext("yes")).isEqualTo(Sinks.EmitResult.OK));
		StepVerifier.create(beats).expectNext("beat").expectComplete().verify(VERIFY_TIMEOUT);
	}

	@Test
	void writesModelTextAsSseDataAndKeepAlivesAsComments() {
		MockServerHttpResponse response = new MockServerHttpResponse();
		Flux<String> content = Flux.just("hello\nworld").concatWith(Flux.never());
		ResolvableType eventType = ResolvableType.forClassWithGenerics(ServerSentEvent.class, String.class);
		StepVerifier
			.withVirtualTime(() -> new ServerSentEventHttpMessageWriter()
				.write(Heartbeats.sse(content).take(2), eventType, MediaType.TEXT_EVENT_STREAM, response, Map.of())
				.then(Mono.defer(response::getBodyAsString)))
			.thenAwait(INTERVAL)
			.expectNext("data: hello\ndata: world\n\n:keep-alive\n\n")
			.expectComplete()
			.verify(VERIFY_TIMEOUT);
	}

	@Test
	void sendsCommentOnlyHeartbeatsEveryFifteenSecondsByDefault() {
		TestPublisher<ServerSentEvent<Integer>> source = TestPublisher.create();
		ServerSentEvent<Integer> event = ServerSentEvent.builder(42).id("answer").build();
		StepVerifier.withVirtualTime(() -> Heartbeats.keepAlive(source))
			.expectSubscription()
			.then(() -> source.next(event))
			.assertNext(actual -> assertThat(actual).isSameAs(event))
			.expectNoEvent(Duration.ofSeconds(14))
			.thenAwait(Duration.ofSeconds(1))
			.assertNext(beat -> {
				assertThat(beat.comment()).isEqualTo("keep-alive");
				assertThat(beat.data()).isNull();
				assertThat(beat.id()).isNull();
				assertThat(beat.event()).isNull();
			})
			.then(source::complete)
			.expectComplete()
			.verify(VERIFY_TIMEOUT);
	}

	@Test
	void usesTheConfiguredIntervalForHeartbeatComments() {
		Flux<ServerSentEvent<String>> source = Flux.never();
		StepVerifier.withVirtualTime(() -> Heartbeats.keepAlive(source, Duration.ofSeconds(30)))
			.expectSubscription()
			.expectNoEvent(Duration.ofSeconds(29))
			.thenAwait(Duration.ofSeconds(1))
			.assertNext(beat -> assertThat(beat.comment()).isEqualTo("keep-alive"))
			.thenCancel()
			.verify(VERIFY_TIMEOUT);
	}

	@Test
	void keepsAResponseAliveFromSubscriptionUntilCompletion() {
		record ChatEvent(String type, String text) {
		}
		ChatEvent first = new ChatEvent("token", "one");
		ChatEvent last = new ChatEvent("token", "two");
		ChatEvent heartbeat = new ChatEvent("heartbeat", "");
		TestPublisher<ChatEvent> events = TestPublisher.create();
		PublisherProbe<ChatEvent> source = PublisherProbe.of(events.flux());
		StepVerifier.withVirtualTime(() -> Heartbeats.keepAlive(source.flux(), INTERVAL, heartbeat))
			.expectSubscription()
			.expectNoEvent(INTERVAL.minusSeconds(1))
			.then(() -> events.next(first))
			.expectNext(first)
			.thenAwait(Duration.ofSeconds(1))
			.expectNext(heartbeat)
			.thenAwait(INTERVAL)
			.expectNext(heartbeat)
			.then(() -> events.emit(last))
			.expectNext(last)
			.expectComplete()
			.verify(VERIFY_TIMEOUT);
		assertThat(source.subscribeCount()).isOne();
	}

	@Test
	void propagatesResponseErrors() {
		TestPublisher<String> events = TestPublisher.create();
		RuntimeException failure = new IllegalStateException("response failed");
		StepVerifier.create(Heartbeats.keepAlive(events, INTERVAL, "beat"))
			.then(() -> events.error(failure))
			.expectErrorMatches(error -> error == failure)
			.verify(VERIFY_TIMEOUT);

	}

	@Test
	void cancelsTheSourceOnDisconnect() {
		PublisherProbe<String> pending = PublisherProbe.of(Flux.never());
		StepVerifier.withVirtualTime(() -> Heartbeats.keepAlive(pending.flux(), INTERVAL, "beat"))
			.thenAwait(INTERVAL)
			.expectNext("beat")
			.thenCancel()
			.verify(VERIFY_TIMEOUT);
		pending.assertWasCancelled();
	}

	@Test
	void buffersAtMostOneHeartbeatWithoutDroppingResponseData() {
		TestPublisher<String> events = TestPublisher.create();
		StringBuilder beat = new StringBuilder("beat");
		StepVerifier.withVirtualTime(() -> Heartbeats.<CharSequence>keepAlive(events, INTERVAL, beat), 0)
			.thenAwait(INTERVAL.multipliedBy(10))
			.thenRequest(1)
			.expectNext(beat)
			.then(() -> events.emit("answer"))
			.thenRequest(1)
			.expectNext("answer")
			.expectComplete()
			.verify(VERIFY_TIMEOUT);
	}

	@Test
	void rejectsIntervalsThatAreNotPositive() {
		TestPublisher<Void> stop = TestPublisher.create();

		assertThatIllegalArgumentException().isThrownBy(() -> Heartbeats.until(stop, Duration.ZERO, "beat"));
		assertThatIllegalArgumentException().isThrownBy(() -> Heartbeats.until(stop, Duration.ofSeconds(-1), "beat"));
	}

}
