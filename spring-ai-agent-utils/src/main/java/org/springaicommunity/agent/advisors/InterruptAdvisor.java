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
package org.springaicommunity.agent.advisors;

import java.util.function.BooleanSupplier;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.core.Ordered;
import org.springframework.util.Assert;

/**
 * Cooperative cancellation of an in-flight agentic turn. The advisor checks an
 * application-supplied interrupt signal in {@link #before} and, when it reports true,
 * unwinds the turn by throwing {@link TurnInterruptedException} — which the caller of
 * {@code ChatClient.call()} catches to decide what an interrupted turn means (discard,
 * partial save, quiet end-of-turn, ...).
 *
 * <p>
 * The default order ({@code HIGHEST_PRECEDENCE + 400}) places it just <em>inside</em> the
 * auto-registered tool-calling advisor ({@code HIGHEST_PRECEDENCE + 300}), so
 * {@link #before} re-runs on <b>every tool-call round</b>: an interrupt raised while
 * tools execute lands before the next model request, not only between turns. Typical
 * uses: a UI "stop" button, a session-level interrupt event, a budget or wall-clock
 * guard.
 *
 * <pre>{@code
 * AtomicBoolean interrupted = new AtomicBoolean();
 *
 * ChatClient chatClient = builder
 *     .defaultAdvisors(memoryAdvisor,
 *             InterruptAdvisor.builder().interruptSignal(interrupted::get).build())
 *     .build();
 *
 * try {
 *     chatClient.prompt().user("...").call().content();
 * }
 * catch (TurnInterruptedException ex) {
 *     // the turn was cancelled cooperatively
 * }
 * }</pre>
 *
 * <p>
 * With {@code stream()} the check runs inside the reactive pipeline, so the
 * {@link TurnInterruptedException} arrives as the Flux's error signal (handle it via
 * {@code onErrorResume}/{@code doOnError}) rather than as a synchronous throw at the call
 * site.
 *
 * @author Christian Tzolov
 * @see TurnInterruptedException
 */
public final class InterruptAdvisor implements BaseAdvisor {

	/** Just inside the auto-registered tool-calling advisor: re-checked every round. */
	public static final int DEFAULT_ORDER = Ordered.HIGHEST_PRECEDENCE + 400;

	private final BooleanSupplier interruptSignal;

	private final int order;

	private InterruptAdvisor(BooleanSupplier interruptSignal, int order) {
		this.interruptSignal = interruptSignal;
		this.order = order;
	}

	@Override
	public ChatClientRequest before(ChatClientRequest chatClientRequest, AdvisorChain advisorChain) {
		if (this.interruptSignal.getAsBoolean()) {
			throw new TurnInterruptedException();
		}
		return chatClientRequest;
	}

	@Override
	public ChatClientResponse after(ChatClientResponse chatClientResponse, AdvisorChain advisorChain) {
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

		private BooleanSupplier interruptSignal;

		private int order = DEFAULT_ORDER;

		private Builder() {
		}

		/**
		 * The signal polled before each model request; returning true cancels the turn.
		 * Must be cheap and thread-safe — an {@code AtomicBoolean::get}, a deadline
		 * check, a budget check.
		 */
		public Builder interruptSignal(BooleanSupplier interruptSignal) {
			this.interruptSignal = interruptSignal;
			return this;
		}

		/**
		 * Advisor order. Default {@link #DEFAULT_ORDER} — inside the tool-calling loop.
		 * Orders below the tool-calling advisor's ({@code HIGHEST_PRECEDENCE + 300})
		 * check only once per turn.
		 */
		public Builder order(int order) {
			this.order = order;
			return this;
		}

		public InterruptAdvisor build() {
			Assert.notNull(this.interruptSignal, "interruptSignal must not be null");
			return new InterruptAdvisor(this.interruptSignal, this.order);
		}

	}

}
