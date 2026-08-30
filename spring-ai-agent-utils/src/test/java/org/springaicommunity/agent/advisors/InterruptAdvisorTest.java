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

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.core.Ordered;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link InterruptAdvisor}.
 *
 * @author Christian Tzolov
 */
class InterruptAdvisorTest {

	private final ChatClientRequest request = Mockito.mock(ChatClientRequest.class);

	private final ChatClientResponse response = Mockito.mock(ChatClientResponse.class);

	private final AdvisorChain chain = Mockito.mock(AdvisorChain.class);

	@Test
	void passesRequestThroughWhileNotInterrupted() {
		InterruptAdvisor advisor = InterruptAdvisor.builder().interruptSignal(() -> false).build();

		assertThat(advisor.before(this.request, this.chain)).isSameAs(this.request);
		assertThat(advisor.after(this.response, this.chain)).isSameAs(this.response);
	}

	@Test
	void throwsTurnInterruptedWhenSignalFires() {
		AtomicBoolean interrupted = new AtomicBoolean(false);
		InterruptAdvisor advisor = InterruptAdvisor.builder().interruptSignal(interrupted::get).build();

		assertThat(advisor.before(this.request, this.chain)).isSameAs(this.request);

		interrupted.set(true);
		assertThatThrownBy(() -> advisor.before(this.request, this.chain)).isInstanceOf(TurnInterruptedException.class);
	}

	@Test
	void signalIsPolledEveryRoundNotCachedAtBuildTime() {
		AtomicBoolean interrupted = new AtomicBoolean(true);
		InterruptAdvisor advisor = InterruptAdvisor.builder().interruptSignal(interrupted::get).build();

		assertThatThrownBy(() -> advisor.before(this.request, this.chain)).isInstanceOf(TurnInterruptedException.class);

		// Clearing the flag re-enables the loop for the next round
		interrupted.set(false);
		assertThat(advisor.before(this.request, this.chain)).isSameAs(this.request);
	}

	@Test
	void defaultOrderSitsInsideTheToolCallingLoop() {
		InterruptAdvisor advisor = InterruptAdvisor.builder().interruptSignal(() -> false).build();

		assertThat(advisor.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE + 400);

		InterruptAdvisor custom = InterruptAdvisor.builder().interruptSignal(() -> false).order(42).build();
		assertThat(custom.getOrder()).isEqualTo(42);
	}

	@Test
	void builderRequiresASignal() {
		assertThatThrownBy(() -> InterruptAdvisor.builder().build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("interruptSignal");
	}

}
