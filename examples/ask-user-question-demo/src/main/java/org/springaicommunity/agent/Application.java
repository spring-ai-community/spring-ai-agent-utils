package org.springaicommunity.agent;

import java.util.Scanner;

import org.springaicommunity.agent.tools.AskUserQuestionTool;
import org.springaicommunity.agent.utils.CommandLineQuestionHandler;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.session.DefaultSessionService;
import org.springframework.ai.session.EventFilter;
import org.springframework.ai.session.InMemorySessionRepository;
import org.springframework.ai.session.SessionService;
import org.springframework.ai.session.advisor.SessionAdvisors;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class Application {

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}

	@Bean
	CommandLineRunner commandLineRunner(ChatClient.Builder chatClientBuilder) {

		return args -> {
			// Short-term conversation memory: an in-memory session store, reset on every run
			SessionService sessionService = DefaultSessionService.builder()
				.sessionRepository(InMemorySessionRepository.builder().build())
				.build();

			// @formatter:off
			ChatClient chatClient = chatClientBuilder
			
				// Ask user question tool
				.defaultTools(AskUserQuestionTool.builder()
					.questionHandler(new CommandLineQuestionHandler())
					.answersValidation(false)
					.build())

				// Conversation memory: the session advisors replay the last 500 events of the session
				.defaultAdvisors(SessionAdvisors.builder(sessionService)
					.memory(memory -> memory.eventFilter(EventFilter.lastN(500)))
					.build())

				.build();
				// @formatter:on

			// Start the chat loop
			System.out.println("\nI am your assistant.\n");

			try (Scanner scanner = new Scanner(System.in)) {
				while (true) {
					System.out.print("\nUSER: ");
					System.out.println("\nASSISTANT: " + chatClient.prompt(scanner.nextLine())
						.advisors(SessionAdvisors.session("session-1"))
						.call()
						.content());
				}
			}
		};
	}

}
