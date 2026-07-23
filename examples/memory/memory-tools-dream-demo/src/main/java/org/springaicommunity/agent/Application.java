package org.springaicommunity.agent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.Scanner;

import org.springaicommunity.agent.advisors.AutoMemoryToolsAdvisor;
import org.springaicommunity.agent.dream.AutoDreamAdvisor;
import org.springaicommunity.agent.dream.AutoDreamService;
import org.springaicommunity.agent.dream.DreamResult;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.session.CreateSessionRequest;
import org.springframework.ai.session.DefaultSessionService;
import org.springframework.ai.session.InMemorySessionRepository;
import org.springframework.ai.session.SessionEvent;
import org.springframework.ai.session.SessionService;
import org.springframework.ai.session.advisor.SessionMemoryAdvisor;
import org.springframework.ai.session.compaction.SlidingWindowCompactionStrategy;
import org.springframework.ai.session.compaction.TurnCountTrigger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/**
 * Demonstrates {@code AutoDreamAdvisor} / {@code AutoDreamService} running an
 * out-of-band memory-consolidation ("dream") cycle on top of {@code AutoMemoryToolsAdvisor},
 * including the Phase 2 cross-session recall capability (spring-ai-session's
 * {@code SessionService} + {@code CrossSessionRecallTools}).
 *
 * <p>
 * On first run the memory directory is seeded with two deliberately overlapping memory
 * files (Phase 1 signal), and an in-memory session store is seeded with a past session
 * containing a "decision" and a "correction" (Phase 2 signal) — both are real material a
 * dream cycle can find. Three ways to see a dream cycle run:
 * <ul>
 * <li>Automatically — {@code AutoDreamAdvisor} is wired with a trigger that fires every
 * three conversation turns; watch the console log for a "Dream cycle [...] finished"
 * line appearing on its own, in the background, while you keep chatting.</li>
 * <li>On demand — type {@code /dream} in the REPL to run a synchronous dream cycle and
 * print its result immediately.</li>
 * </ul>
 *
 * <p>
 * The session store is {@code InMemorySessionRepository} — it resets on every run, unlike
 * the file-based memory store, which persists across runs. Swap in
 * {@code spring-ai-session-jdbc} for a persistent store; nothing else in this demo would
 * need to change.
 *
 * @author Christian Tzolov
 */
@SpringBootApplication
public class Application {

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}

	@Bean
	CommandLineRunner commandLineRunner(ChatClient.Builder chatClientBuilder,
			@Value("${agent.memory.dir}") String memoryDir, @Value("${agent.demo.user-id:alice}") String userId)
			throws IOException {

		return args -> {

			Path memoriesDir = Paths.get(memoryDir);
			seedDemoMemoriesIfAbsent(memoriesDir);

			SessionService sessionService = DefaultSessionService.builder()
				.sessionRepository(InMemorySessionRepository.builder().build())
				.build();
			seedDemoSessionHistory(sessionService, userId);

			// A separate, unconfigured clone of the builder for the Dreamer — it must
			// never inherit the main agent's system prompt or tools, only its own.
			AutoDreamService dreamService = AutoDreamService.builder(chatClientBuilder.clone())
				.sessionService(sessionService)
				.build();

			ChatClient chatClient = chatClientBuilder // @formatter:off

				.defaultSystem("""
						You are a helpful assistant with a persistent long-term memory. Use your
						memory tools to recall relevant facts at the start of a conversation and to
						save new durable facts about the user as you learn them.
						""")

				.defaultAdvisors(
					// Long-term memory — read/write tools for the live agent
					AutoMemoryToolsAdvisor.builder()
						.memoriesRootDirectory(memoryDir)
						.build(),

					// Out-of-band dream cycles — fires automatically every 3 turns in this demo.
					// userId enables cross-session recall for the cycles this advisor triggers.
					AutoDreamAdvisor.builder()
						.memoriesRootDirectory(memoryDir)
						.dreamService(dreamService)
						.dreamTrigger((state, now) -> state.sessionsSinceLastDream() >= 3)
						.userId(userId)
						.build(),

					// Short-term conversation window, backed by the same SessionService the
					// Dreamer's cross_session_search reads from — this is what feeds it.
					// Bounded via the same trigger+strategy combo as the project's own
					// quickstart, so a long demo session can't grow the live prompt without
					// limit — compacted-out events stay archived and remain searchable via
					// conversation_search/cross_session_search, they are not deleted.
					SessionMemoryAdvisor.builder(sessionService)
						.defaultUserId(userId)
						.compactionTrigger(new TurnCountTrigger(20))
						.compactionStrategy(SlidingWindowCompactionStrategy.builder().maxEvents(10).build())
						.order(Ordered.HIGHEST_PRECEDENCE + 1000)
						.build(),

					MyLoggingAdvisor.builder()
						.showAvailableTools(true)
						.showSystemMessage(false)
						.order(Ordered.HIGHEST_PRECEDENCE + 1100)
						.build())
				.build();
				// @formatter:on

			System.out.println("\nI am your assistant. Memory is stored at " + memoriesDir);
			System.out.println("Session history (user '" + userId + "') is in-memory and resets each run.");
			System.out.println("Type /dream to run an on-demand dream cycle. Every 3rd turn triggers one automatically.\n");

			try (Scanner scanner = new Scanner(System.in)) {
				while (true) {
					System.out.print("\n\033[1;34mUSER>\033[0m ");
					String userInput = scanner.nextLine();

					if ("/dream".equalsIgnoreCase(userInput.trim())) {
						System.out.println("\n\033[1;35mDREAM>\033[0m running an on-demand dream cycle...");
						DreamResult result = dreamService.runDreamCycle(memoryDir, userId);
						System.out.println("\033[1;35mDREAM>\033[0m status=" + result.status() + " — " + result.summary());
						continue;
					}

					System.out.println("\n\033[1;34mASSISTANT>\033[0m " + chatClient.prompt(userInput)
						.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "session-1"))
						.call()
						.content());
				}
			}
		};

	}

	/**
	 * Seeds the memory directory with two deliberately overlapping feedback memories and
	 * a dangling {@code MEMORY.md} entry, so the first dream cycle has real signal to
	 * consolidate. Only runs once — subsequent launches leave the memory store as the
	 * previous session (and any dream cycles) left it.
	 */
	private static void seedDemoMemoriesIfAbsent(Path memoriesDir) throws IOException {
		Path memoryIndex = memoriesDir.resolve("MEMORY.md");
		if (Files.exists(memoryIndex)) {
			return;
		}

		Files.createDirectories(memoriesDir);

		Files.writeString(memoriesDir.resolve("feedback_testing_a.md"), """
				---
				name: testing-preference-a
				description: Prefer a real database in integration tests
				type: feedback
				---

				Always use a real database in integration tests, never mock it.

				**Why:** Mocked tests passed but the prod migration failed last quarter.
				**How to apply:** Any new integration test touching persistence.
				""", StandardCharsets.UTF_8);

		Files.writeString(memoriesDir.resolve("feedback_testing_b.md"), """
				---
				name: testing-preference-b
				description: Integration tests must hit a real database, not mocks
				type: feedback
				---

				Integration tests must hit a real database, not mocks.

				**Why:** A mock/prod divergence masked a broken migration once.
				**How to apply:** Whenever writing or reviewing integration tests.
				""", StandardCharsets.UTF_8);

		Files.writeString(memoryIndex, """
				- [Testing Preference A](feedback_testing_a.md) — always use a real DB in integration tests
				- [Testing Preference B](feedback_testing_b.md) — integration tests must hit a real database, not mocks
				- [Old Sprint Notes](project_old_sprint.md) — sprint 12 deadline notes (file intentionally missing — dangling link)
				""", StandardCharsets.UTF_8);

		System.out.println("Seeded demo memories at " + memoriesDir
				+ " — two overlapping feedback entries and one dangling MEMORY.md link.");
	}

	/**
	 * Seeds a past session for {@code userId} containing a "decision" and a "correction"
	 * — exactly the kind of signal {@code AUTO_DREAM_SYSTEM_PROMPT.md} tells the Dreamer
	 * to search for via {@code cross_session_search}. The in-memory session store starts
	 * empty on every run, so this always seeds (unlike the file-based memory store, which
	 * only seeds once).
	 */
	private static void seedDemoSessionHistory(SessionService sessionService, String userId) {
		var pastSession = sessionService.create(CreateSessionRequest.builder().userId(userId).build());

		Instant twoDaysAgo = Instant.now().minus(Duration.ofDays(2));

		sessionService.appendEvent(SessionEvent.builder()
			.sessionId(pastSession.id())
			.timestamp(twoDaysAgo)
			.message(new UserMessage("We decided to use PostgreSQL instead of MySQL for the new service."))
			.build());
		sessionService.appendEvent(SessionEvent.builder()
			.sessionId(pastSession.id())
			.timestamp(twoDaysAgo.plusSeconds(30))
			.message(new AssistantMessage("Got it — noted that Postgres is the choice going forward."))
			.build());
		sessionService.appendEvent(SessionEvent.builder()
			.sessionId(pastSession.id())
			.timestamp(twoDaysAgo.plusSeconds(120))
			.message(new UserMessage(
					"Actually, let's go with the blue-green deployment strategy instead of canary."))
			.build());

		System.out.println(
				"Seeded a past session for user '" + userId + "' with a decision and a correction (2 days ago).");
	}

}
