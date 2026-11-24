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

import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import org.springframework.http.codec.ServerSentEvent;
import org.springframework.util.Assert;

/**
 * Utilities for adding application-defined heartbeat events to reactive streams.
 *
 * @author git-syl
 */
public final class Heartbeats {

	private static final Duration DEFAULT_SSE_INTERVAL = Duration.ofSeconds(15);

	private Heartbeats() {
	}

	/**
	 * Emits a heartbeat immediately on subscription (subject to downstream demand), then
	 * repeats it at the given interval. The first value or completion from {@code stop}
	 * ends the stream, and an error from {@code stop} fails it. A stop signal available
	 * on subscription takes precedence over the first heartbeat. Completion, error, and
	 * cancellation all release the timer. Periodic ticks are dropped when there is no
	 * downstream demand.
	 * @param stop the signal that the pending operation has ended
	 * @param interval the positive interval between periodic heartbeats
	 * @param heartbeat the non-null event to emit on each beat
	 * @param <T> the type of heartbeat event
	 * @return the heartbeat stream
	 */
	public static <T> Flux<T> until(Publisher<?> stop, Duration interval, T heartbeat) {
		Assert.notNull(stop, "stop must not be null");
		// Scope the timer to the result so a failing stop publisher also cancels it.
		return periodic(interval, heartbeat)
			.publish(beats -> beats.onBackpressureDrop().startWith(heartbeat).takeUntilOther(stop), 1);
	}

	/**
	 * Wrap each data item in a server-sent event and send a keep-alive comment every 15
	 * seconds. For an already framed stream, use {@link #keepAlive(Publisher)}.
	 * @param data the application data to send
	 * @param <T> the event data type
	 * @return the data events interleaved with heartbeat comments
	 */
	public static <T> Flux<ServerSentEvent<T>> sse(Publisher<T> data) {
		return sse(data, DEFAULT_SSE_INTERVAL);
	}

	/**
	 * Wrap each data item in a server-sent event and send keep-alive comments at the
	 * given interval.
	 * @param data the application data to send
	 * @param interval the positive interval between heartbeat comments
	 * @param <T> the event data type
	 * @return the data events interleaved with heartbeat comments
	 */
	public static <T> Flux<ServerSentEvent<T>> sse(Publisher<T> data, Duration interval) {
		Assert.notNull(data, "data must not be null");
		return keepAlive(Flux.from(data).map(item -> ServerSentEvent.builder(item).build()), interval);
	}

	/**
	 * Keep a server-sent event stream alive with a {@code keep-alive} comment every 15
	 * seconds. The first comment is sent after one full interval.
	 * @param source the server-sent events to keep alive
	 * @param <T> the event data type
	 * @return the source events interleaved with heartbeat comments
	 */
	public static <T> Flux<ServerSentEvent<T>> keepAlive(Publisher<ServerSentEvent<T>> source) {
		return keepAlive(source, DEFAULT_SSE_INTERVAL);
	}

	/**
	 * Keep a server-sent event stream alive with a {@code keep-alive} comment at the
	 * given interval.
	 * @param source the server-sent events to keep alive
	 * @param interval the positive interval between heartbeat comments
	 * @param <T> the event data type
	 * @return the source events interleaved with heartbeat comments
	 */
	public static <T> Flux<ServerSentEvent<T>> keepAlive(Publisher<ServerSentEvent<T>> source, Duration interval) {
		return keepAlive(source, interval, ServerSentEvent.<T>builder().comment("keep-alive").build());
	}

	/**
	 * Interleave the source events with a heartbeat every {@code interval}, starting
	 * after one full interval. Each subscriber gets its own timer and one subscription to
	 * the source. At most one heartbeat is buffered for a slow subscriber. Source
	 * completion, source failure, and cancellation stop the timer.
	 * @param source the events to keep alive
	 * @param interval the positive interval between heartbeats
	 * @param heartbeat the event to emit on each beat
	 * @param <T> the type of event
	 * @return the source events interleaved with heartbeats
	 */
	public static <T> Flux<T> keepAlive(Publisher<? extends T> source, Duration interval, T heartbeat) {
		Assert.notNull(source, "source must not be null");
		Flux<T> beats = periodic(interval, heartbeat).onBackpressureDrop();
		return Flux.defer(() -> {
			Sinks.Empty<Void> completed = Sinks.empty();
			Flux<? extends T> events = Flux.from(source).doOnComplete(() -> completed.tryEmitEmpty());
			return Flux.merge(1, beats.takeUntilOther(completed.asMono()), events);
		});
	}

	private static <T> Flux<T> periodic(Duration interval, T heartbeat) {
		Assert.notNull(interval, "interval must not be null");
		Assert.isTrue(!interval.isZero() && !interval.isNegative(), "interval must be positive");
		Assert.notNull(heartbeat, "heartbeat must not be null");
		return Flux.interval(interval).map(tick -> heartbeat);
	}

}
