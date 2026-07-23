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

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.DefaultToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.session.DefaultSessionService;
import org.springframework.ai.session.InMemorySessionRepository;
import org.springframework.ai.session.SessionService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

/**
 * Tests for {@link AutoDreamService}.
 *
 * @author Christian Tzolov
 */
@DisplayName("AutoDreamService Tests")
@ExtendWith(MockitoExtension.class)
class AutoDreamServiceTest {

	@TempDir
	Path tempDir;

	@Mock
	ChatModel chatModel;

	@BeforeEach
	void stubDefaultModelOptions() {
		// ChatClient merges the model's own default options into the request; a bare
		// Mockito mock returns null here, which NPEs deep inside the tool-calling
		// pipeline before a test's own stubbing is ever reached.
		lenient().when(this.chatModel.getOptions()).thenReturn(DefaultToolCallingChatOptions.builder().build());
	}

	private void stubModelReply(String text) {
		lenient().when(this.chatModel.call(any(Prompt.class)))
			.thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(text)))));
	}

	private AutoDreamService.Builder serviceBuilder() {
		this.stubModelReply("Merged two duplicate entries.");
		return AutoDreamService.builder(ChatClient.builder(this.chatModel));
	}

	@Nested
	@DisplayName("runDreamCycle() — AUTO_APPLY (default)")
	class AutoApplyTests {

		@Test
		@DisplayName("Completes and persists dream state")
		void completesAndPersistsState() {
			AutoDreamService service = serviceBuilder().build();

			DreamResult result = service.runDreamCycle(tempDir.toString());

			assertThat(result.status()).isEqualTo("completed");
			assertThat(result.summary()).isEqualTo("Merged two duplicate entries.");

			DreamState state = DreamState.load(tempDir);
			assertThat(state.lastDreamAt()).isNotNull();
			assertThat(state.sessionsSinceLastDream()).isZero();
			assertThat(state.lastDreamStatus()).isEqualTo("completed");
			assertThat(state.lastDreamSummary()).isEqualTo("Merged two duplicate entries.");
		}

		@Test
		@DisplayName("Creates the memories directory if it does not exist")
		void createsMemoriesDirectory() {
			Path nested = tempDir.resolve("nested/memories");
			AutoDreamService service = serviceBuilder().build();

			service.runDreamCycle(nested.toString());

			assertThat(nested).exists().isDirectory();
		}

		@Test
		@DisplayName("Releases the lock after completion")
		void releasesLockAfterCompletion() {
			AutoDreamService service = serviceBuilder().build();

			service.runDreamCycle(tempDir.toString());

			assertThat(tempDir.resolve(".dream.lock")).doesNotExist();
		}

		@Test
		@DisplayName("Skips the cycle when the lock is already held")
		void skipsWhenLockHeld() {
			DreamLock heldLock = DreamLock.tryAcquire(tempDir, Duration.ofMinutes(15)).orElseThrow();
			AutoDreamService service = AutoDreamService.builder(ChatClient.builder(chatModel)).build();

			DreamResult result = service.runDreamCycle(tempDir.toString());

			assertThat(result.status()).isEqualTo("skipped");
			heldLock.close();
		}

	}

	@Nested
	@DisplayName("runDreamCycle() — PROPOSE")
	class ProposeModeTests {

		@Test
		@DisplayName("Copies existing memory files into a timestamped proposal directory")
		void copiesIntoProposalDirectory() throws Exception {
			Files.writeString(tempDir.resolve("MEMORY.md"), "- [Existing](existing.md) — hook");
			Files.writeString(tempDir.resolve("existing.md"), "---\nname: existing\n---\ncontent");

			AutoDreamService service = serviceBuilder().dreamMode(DreamMode.PROPOSE).build();

			service.runDreamCycle(tempDir.toString());

			Path proposalsDir = tempDir.resolve(".dream-proposals");
			assertThat(proposalsDir).exists().isDirectory();

			try (var children = Files.list(proposalsDir)) {
				Path proposal = children.findFirst().orElseThrow();
				assertThat(proposal.resolve("MEMORY.md")).exists();
				assertThat(proposal.resolve("existing.md")).exists();
			}
		}

		@Test
		@DisplayName("Leaves the live memory files untouched")
		void leavesLiveFilesUntouched() throws Exception {
			Path liveMemory = tempDir.resolve("MEMORY.md");
			Files.writeString(liveMemory, "- [Existing](existing.md) — hook");

			AutoDreamService service = serviceBuilder().dreamMode(DreamMode.PROPOSE).build();
			service.runDreamCycle(tempDir.toString());

			assertThat(Files.readString(liveMemory)).isEqualTo("- [Existing](existing.md) — hook");
		}

		@Test
		@DisplayName("Does not include dream bookkeeping files in the proposal copy")
		void excludesDreamInternalFiles() throws Exception {
			Files.writeString(tempDir.resolve("MEMORY.md"), "index");
			DreamState.initial().withDreamCompleted(Instant.now(), "completed", "prior run").save(tempDir);

			AutoDreamService service = serviceBuilder().dreamMode(DreamMode.PROPOSE).build();
			service.runDreamCycle(tempDir.toString());

			Path proposalsDir = tempDir.resolve(".dream-proposals");
			try (var children = Files.list(proposalsDir)) {
				Path proposal = children.findFirst().orElseThrow();
				assertThat(proposal.resolve(".dream-state.json")).doesNotExist();
				assertThat(proposal.resolve(".dream-proposals")).doesNotExist();
			}
		}

	}

	@Nested
	@DisplayName("runDreamCycle(dir, userId) — cross-session recall")
	class CrossSessionRecallTests {

		private SessionService sessionService() {
			return DefaultSessionService.builder().sessionRepository(InMemorySessionRepository.builder().build()).build();
		}

		@Test
		@DisplayName("Adds cross_session_search when sessionService is configured and userId is given")
		void addsCrossSessionSearchWhenConfigured() {
			ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);

			AutoDreamService service = serviceBuilder().sessionService(sessionService()).build();
			service.runDreamCycle(tempDir.toString(), "alice");

			verify(chatModel).call(promptCaptor.capture());
			ToolCallingChatOptions options = (ToolCallingChatOptions) promptCaptor.getValue().getOptions();
			List<String> toolNames = options.getToolCallbacks().stream().map(tc -> tc.getToolDefinition().name()).toList();

			assertThat(toolNames).contains("cross_session_search");
		}

		@Test
		@DisplayName("Omits cross_session_search when sessionService is not configured")
		void omitsCrossSessionSearchWithoutSessionService() {
			ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);

			AutoDreamService service = serviceBuilder().build();
			service.runDreamCycle(tempDir.toString(), "alice");

			verify(chatModel).call(promptCaptor.capture());
			ToolCallingChatOptions options = (ToolCallingChatOptions) promptCaptor.getValue().getOptions();
			List<String> toolNames = options.getToolCallbacks().stream().map(tc -> tc.getToolDefinition().name()).toList();

			assertThat(toolNames).doesNotContain("cross_session_search");
		}

		@Test
		@DisplayName("Omits cross_session_search when userId is blank, even with sessionService configured")
		void omitsCrossSessionSearchWithoutUserId() {
			ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);

			AutoDreamService service = serviceBuilder().sessionService(sessionService()).build();
			service.runDreamCycle(tempDir.toString());

			verify(chatModel).call(promptCaptor.capture());
			ToolCallingChatOptions options = (ToolCallingChatOptions) promptCaptor.getValue().getOptions();
			List<String> toolNames = options.getToolCallbacks().stream().map(tc -> tc.getToolDefinition().name()).toList();

			assertThat(toolNames).doesNotContain("cross_session_search");
		}

		@Test
		@DisplayName("Kickoff prompt mentions cross_session_search when it's available")
		void kickoffPromptMentionsToolWhenAvailable() {
			ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);

			AutoDreamService service = serviceBuilder().sessionService(sessionService()).build();
			service.runDreamCycle(tempDir.toString(), "alice");

			verify(chatModel).call(promptCaptor.capture());
			String userText = ((UserMessage) promptCaptor.getValue().getUserMessage()).getText();

			assertThat(userText).contains("cross_session_search");
		}

		@Test
		@DisplayName("Kickoff prompt does not mention cross_session_search when unavailable")
		void kickoffPromptOmitsToolWhenUnavailable() {
			ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);

			AutoDreamService service = serviceBuilder().build();
			service.runDreamCycle(tempDir.toString());

			verify(chatModel).call(promptCaptor.capture());
			String userText = ((UserMessage) promptCaptor.getValue().getUserMessage()).getText();

			assertThat(userText).doesNotContain("cross_session_search");
		}

	}

	@Nested
	@DisplayName("Error handling")
	class ErrorHandlingTests {

		@Test
		@DisplayName("A model failure is captured as a failed result rather than propagated")
		void modelFailureIsCaptured() {
			lenient().when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("model unavailable"));
			AutoDreamService service = AutoDreamService.builder(ChatClient.builder(chatModel)).build();

			DreamResult result = service.runDreamCycle(tempDir.toString());

			assertThat(result.status()).isEqualTo("failed");
			assertThat(result.summary()).contains("model unavailable");

			DreamState state = DreamState.load(tempDir);
			assertThat(state.lastDreamStatus()).isEqualTo("failed");
		}

	}

	@Nested
	@DisplayName("Builder")
	class BuilderTests {

		@Test
		@DisplayName("Null chatClientBuilder throws immediately")
		void nullChatClientBuilderThrows() {
			assertThatIllegalArgumentException().isThrownBy(() -> AutoDreamService.builder(null));
		}

		@Test
		@DisplayName("Null dreamMode throws immediately")
		void nullDreamModeThrows() {
			assertThatIllegalArgumentException()
				.isThrownBy(() -> AutoDreamService.builder(ChatClient.builder(chatModel)).dreamMode(null));
		}

		@Test
		@DisplayName("Null staleLockAfter throws immediately")
		void nullStaleLockAfterThrows() {
			assertThatIllegalArgumentException()
				.isThrownBy(() -> AutoDreamService.builder(ChatClient.builder(chatModel)).staleLockAfter(null));
		}

	}

}
