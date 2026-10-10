package org.springaicommunity.agent;

import java.nio.file.Path;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.springaicommunity.agent.advisors.InterruptAdvisor;
import org.springaicommunity.agent.advisors.TurnInterruptedException;
import org.springaicommunity.agent.tools.AgentToolset;
import org.springaicommunity.agent.tools.ToolCallListener;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * Observing and interrupting the agent tool-calling loop:
 * <ul>
 * <li>a {@link ToolCallListener} traces and counts every tool call;</li>
 * <li>an {@link InterruptAdvisor} stops the turn when the user types {@code /stop} or
 * when the turn exceeds its tool-call budget.</li>
 * </ul>
 */
@SpringBootApplication
public class Application {

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}

	@Bean
	CommandLineRunner commandLineRunner(ChatClient.Builder chatClientBuilder,
			@Value("${agent.max-tool-calls-per-turn:20}") int maxToolCallsPerTurn) {

		return args -> {
			AtomicBoolean stopRequested = new AtomicBoolean();
			AtomicInteger toolCalls = new AtomicInteger();

			// 1. ToolCallListener: trace and count every tool call of the turn
			ToolCallListener listener = new ToolCallListener() {

				@Override
				public Object beforeCall(String toolName, String toolInput) {
					int callNumber = toolCalls.incrementAndGet();
					System.out.println("  [tool #" + callNumber + "] " + toolName + " " + abbreviate(toolInput));
					// Correlation context handed back to afterCall/onError: the start time
					return System.nanoTime();
				}

				@Override
				public void afterCall(Object context, String toolName, String toolInput, String result) {
					System.out.println("  [tool] " + toolName + " ok, " + elapsedMillis(context) + " ms, "
							+ result.length() + " chars");
				}

				@Override
				public String onError(Object context, String toolName, String toolInput, RuntimeException ex) {
					System.out.println("  [tool] " + toolName + " FAILED after " + elapsedMillis(context) + " ms: "
							+ ex.getMessage());
					// Report the failure to the model as the tool result; null would rethrow
					return "Tool '" + toolName + "' failed: " + ex.getMessage();
				}

			};

			List<ToolCallback> tools = AgentToolset.builder().listener(listener).build();

			// @formatter:off
			ChatClient chatClient = chatClientBuilder
				.defaultSystem("You are a helpful assistant with shell and file system tools. "
						+ "The current working directory is " + Path.of("").toAbsolutePath() + ".")
				.defaultTools(tools)
				.defaultAdvisors(
					// 2. InterruptAdvisor: polled before every model request of the
					// tool-calling loop. Stops on /stop or when the tool-call budget is used up.
					InterruptAdvisor.builder()
						.interruptSignal(() -> stopRequested.get() || toolCalls.get() >= maxToolCallsPerTurn)
						.build(),
					MessageChatMemoryAdvisor.builder(MessageWindowChatMemory.builder().maxMessages(100).build())
						.build())
				.build();
			// @formatter:on

			System.out.println("""

					Observability demo: every tool call is traced below the prompt.
					While the assistant works, type /stop (and Enter) to stop the turn.
					A turn also stops after %d tool calls. Type /exit to quit.
					""".formatted(maxToolCallsPerTurn));

			// The turn runs on a worker thread so the console stays free for /stop
			ExecutorService worker = Executors.newSingleThreadExecutor();
			Future<?> turn = CompletableFuture.completedFuture(null);

			try (Scanner scanner = new Scanner(System.in)) {
				System.out.print("> USER: ");
				while (scanner.hasNextLine()) {
					String line = scanner.nextLine().trim();

					if (!turn.isDone()) {
						if ("/stop".equals(line)) {
							stopRequested.set(true);
							System.out.println("  (stopping before the next model request...)");
						}
						else {
							System.out.println("  (busy - type /stop to stop the current turn)");
						}
						continue;
					}
					if ("/exit".equals(line)) {
						break;
					}
					if (line.isEmpty()) {
						System.out.print("> USER: ");
						continue;
					}

					// Re-arm both interrupt conditions for the new turn
					stopRequested.set(false);
					toolCalls.set(0);

					turn = worker.submit(() -> {
						try {
							String answer = chatClient.prompt(line)
								.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "session-1"))
								.call()
								.content();
							System.out.println("\n> ASSISTANT: " + answer);
						}
						catch (TurnInterruptedException e) {
							System.out.println("\n> ASSISTANT: [turn stopped "
									+ (stopRequested.get() ? "by /stop" : "after " + toolCalls.get() + " tool calls")
									+ "]");
						}
						catch (RuntimeException e) {
							System.out.println("\n> ASSISTANT: [error: " + e.getMessage() + "]");
						}
						System.out.print("\n> USER: ");
					});
				}
			}
			finally {
				worker.shutdownNow();
			}
		};
	}

	private static long elapsedMillis(Object startNanos) {
		return (System.nanoTime() - (long) startNanos) / 1_000_000;
	}

	private static String abbreviate(String text) {
		String oneLine = text.replaceAll("\\s+", " ");
		return (oneLine.length() <= 100) ? oneLine : oneLine.substring(0, 100) + "...";
	}

}
