package org.springaicommunity.agent;

import java.io.IOException;
import java.time.Instant;
import java.util.Scanner;

import org.springaicommunity.agent.advisors.AutoMemoryToolsAdvisor;
import org.springaicommunity.agent.utils.AgentEnvironment;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.ToolCallAdvisor;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.Resource;

@SpringBootApplication
public class Application {

	Instant lastInteraction = Instant.now();

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}

	@Bean
	CommandLineRunner commandLineRunner(ChatClient.Builder chatClientBuilder,
			@Value("${agent.model:Unknown}") String agentModel,
			@Value("${agent.model.knowledge.cutoff:Unknown}") String agentModelKnowledgeCutoff,
			@Value("classpath:/prompt/MAIN_AGENT_SYSTEM_PROMPT_V2.md") Resource systemPrompt,
			@Value("${agent.memory.dir}") String memoryDir) throws IOException {

		return args -> {		

			ChatClient chatClient = chatClientBuilder // @formatter:off
				// system prompt
				.defaultSystem(p -> p.text(systemPrompt) // system prompt
					.param(AgentEnvironment.ENVIRONMENT_INFO_KEY, AgentEnvironment.info())
					.param(AgentEnvironment.GIT_STATUS_KEY, AgentEnvironment.gitStatus())
					.param(AgentEnvironment.AGENT_MODEL_KEY, agentModel)
					.param(AgentEnvironment.AGENT_MODEL_KNOWLEDGE_CUTOFF_KEY, agentModelKnowledgeCutoff))
				
				.defaultAdvisors(
					// Long-term memory advisor
					AutoMemoryToolsAdvisor.builder()
						.memoriesRootDirectory(memoryDir)
						.memoryConsolidationTrigger((request, instant) -> {
							var previousInteraction = lastInteraction;
							lastInteraction = Instant.now();
							if (instant.isAfter(previousInteraction.plusSeconds(60))) {
								// Consolidate at least every 60 seconds
								return true;
							}							

							// Trigger memory consolidation when the user says "bye" in their last message
							var userMessage = request.prompt().getLastUserOrToolResponseMessage().getText();
							return userMessage != null && userMessage.toLowerCase().contains("bye");
						})
						.build(),

					// Tool Calling advisor
					ToolCallAdvisor.builder().disableInternalConversationHistory().build(),

					MessageChatMemoryAdvisor.builder(MessageWindowChatMemory.builder().maxMessages(100).build()).build(),

					// Custom logging advisor
					MyLoggingAdvisor.builder()
						.showAvailableTools(false)
						.showSystemMessage(false)
						.build())
				.build();
				// @formatter:on

			// Start the chat loop
			System.out.println("\nI am your assistant.\n");

			try (Scanner scanner = new Scanner(System.in)) {
				while (true) {
					System.out.print("\n\033[1;34mUSER>\033[0m ");
					System.out.println(
							"\n\033[1;34mASSISTANT>\033[0m " + chatClient.prompt(scanner.nextLine()).call().content());
				}
			}
		};

	}

}
