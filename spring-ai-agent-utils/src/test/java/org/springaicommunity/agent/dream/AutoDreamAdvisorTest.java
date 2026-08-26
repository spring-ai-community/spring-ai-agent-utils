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
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springaicommunity.agent.tools.task.repository.DefaultTaskRepository;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link AutoDreamAdvisor}.
 *
 * @author Christian Tzolov
 */
@DisplayName("AutoDreamAdvisor Tests")
@ExtendWith(MockitoExtension.class)
class AutoDreamAdvisorTest {

	@TempDir
	Path tempDir;

	@Mock
	AdvisorChain advisorChain;

	@Mock
	AutoDreamService dreamService;

	private AutoDreamAdvisor.Builder advisorBuilder() {
		return AutoDreamAdvisor.builder()
			.memoriesRootDirectory(this.tempDir.toString())
			.dreamService(this.dreamService);
	}

	@Nested
	@DisplayName("Builder")
	class BuilderTests {

		@Test
		@DisplayName("Default order is HIGHEST_PRECEDENCE + 150")
		void defaultOrder() {
			AutoDreamAdvisor advisor = advisorBuilder().build();
			assertThat(advisor.getOrder()).isEqualTo(BaseAdvisor.HIGHEST_PRECEDENCE + 150);
		}

		@Test
		@DisplayName("Custom order is respected")
		void customOrder() {
			AutoDreamAdvisor advisor = advisorBuilder().order(42).build();
			assertThat(advisor.getOrder()).isEqualTo(42);
		}

		@Test
		@DisplayName("Empty memoriesRootDirectory throws")
		void emptyDirectoryThrows() {
			assertThatIllegalArgumentException()
				.isThrownBy(() -> AutoDreamAdvisor.builder().dreamService(dreamService).build());
		}

		@Test
		@DisplayName("Missing dreamService throws")
		void missingDreamServiceThrows() {
			assertThatIllegalArgumentException()
				.isThrownBy(() -> AutoDreamAdvisor.builder().memoriesRootDirectory(tempDir.toString()).build());
		}

		@Test
		@DisplayName("Null dreamTrigger throws immediately")
		void nullDreamTriggerThrows() {
			assertThatIllegalArgumentException().isThrownBy(() -> AutoDreamAdvisor.builder().dreamTrigger(null));
		}

	}

	@Test
	@DisplayName("before() passes the request through unchanged")
	void beforePassesThrough() {
		AutoDreamAdvisor advisor = advisorBuilder().build();
		ChatClientRequest request = ChatClientRequest.builder().prompt(new Prompt(new UserMessage("hi"))).build();

		assertThat(advisor.before(request, this.advisorChain)).isSameAs(request);
	}

	@Test
	@DisplayName("after() always increments and persists the session counter")
	void afterIncrementsSessionCounter() {
		AutoDreamAdvisor advisor = advisorBuilder().dreamTrigger(DreamTriggers.manualOnly()).build();
		ChatClientResponse response = ChatClientResponse.builder().context(Map.of()).build();

		advisor.after(response, this.advisorChain);
		advisor.after(response, this.advisorChain);

		DreamState state = DreamState.load(this.tempDir);
		assertThat(state.sessionsSinceLastDream()).isEqualTo(2);
	}

	@Test
	@DisplayName("after() returns the response unchanged")
	void afterReturnsResponseUnchanged() {
		AutoDreamAdvisor advisor = advisorBuilder().dreamTrigger(DreamTriggers.manualOnly()).build();
		ChatClientResponse response = ChatClientResponse.builder().context(Map.of()).build();

		assertThat(advisor.after(response, this.advisorChain)).isSameAs(response);
	}

	@Test
	@DisplayName("Trigger returning false never invokes the dream service")
	void triggerFalseNeverInvokesDreamService() {
		AutoDreamAdvisor advisor = advisorBuilder().dreamTrigger(DreamTriggers.manualOnly()).build();
		ChatClientResponse response = ChatClientResponse.builder().context(Map.of()).build();

		advisor.after(response, this.advisorChain);

		verifyNoInteractions(this.dreamService);
	}

	@Test
	@DisplayName("Trigger returning true submits a background task that runs the dream cycle")
	void triggerTrueSubmitsBackgroundTask() {
		when(this.dreamService.runDreamCycle(this.tempDir.toString(), null))
			.thenReturn(DreamResult.completed("all clean"));

		AutoDreamAdvisor advisor = advisorBuilder().dreamTrigger((state, now) -> true)
			.taskRepository(new DefaultTaskRepository())
			.build();
		ChatClientResponse response = ChatClientResponse.builder().context(Map.of()).build();

		advisor.after(response, this.advisorChain);

		// The dream cycle runs on a background thread — wait for it rather than assert
		// immediately.
		verify(this.dreamService, timeout(2000)).runDreamCycle(this.tempDir.toString(), null);
	}

	@Test
	@DisplayName("Configured userId is passed through to the session-aware overload")
	void configuredUserIdIsPassedThrough() {
		when(this.dreamService.runDreamCycle(this.tempDir.toString(), "alice"))
			.thenReturn(DreamResult.completed("merged cross-session signal"));

		AutoDreamAdvisor advisor = advisorBuilder().dreamTrigger((state, now) -> true)
			.taskRepository(new DefaultTaskRepository())
			.userId("alice")
			.build();
		ChatClientResponse response = ChatClientResponse.builder().context(Map.of()).build();

		advisor.after(response, this.advisorChain);

		verify(this.dreamService, timeout(2000)).runDreamCycle(this.tempDir.toString(), "alice");
	}

}
