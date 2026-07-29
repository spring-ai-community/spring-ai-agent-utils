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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReasoningTraceAdvisor Tests")
class ReasoningTraceAdvisorTest {

	@Test
	@DisplayName("Emits iteration trace with timing and user input")
	void emitsTrace() {
		List<ReasoningTraceAdvisor.ReasoningTraceEntry> traces = new ArrayList<>();
		ReasoningTraceAdvisor advisor = ReasoningTraceAdvisor.builder().traceSink(traces::add).build();

		ChatClientRequest request = ChatClientRequest.builder().prompt(new Prompt(new UserMessage("hello trace"))).build();
		ChatClientResponse response = ChatClientResponse.builder().context(Map.of()).build();

		advisor.before(request, null);
		advisor.after(response, null);

		assertThat(traces).hasSize(1);
		assertThat(traces.get(0).durationMs()).isGreaterThanOrEqualTo(0);
		assertThat(traces.get(0).userInput()).contains("hello trace");
	}

}
