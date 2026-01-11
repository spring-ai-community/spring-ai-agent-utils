package org.springaicommunity.agent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import org.springaicommunity.agent.tools.AskUserQuestionTool;
import org.springaicommunity.agent.tools.AskUserQuestionTool.Question;
import org.springaicommunity.agent.tools.AskUserQuestionTool.Question.Option;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.ToolCallAdvisor;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.io.Resource;

@SpringBootApplication
public class Application {

	@Value("${agent.skills.paths}")
	List<String> skillPaths;

	@Value("${agent.model:Unknown}")
	String agentModel;

	@Value("${agent.model.knowledge.cutoff:Unknown}")
	String agentModelKnowledgeCutoff;

	@Value("classpath:/prompt/MAIN_AGENT_SYSTEM_PROMPT_V2.md")
	Resource systemPrompt;

	@Value("${BRAVE_API_KEY:#{null}}")
	String braveApiKey;

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}

	@Bean
	CommandLineRunner commandLineRunner(ChatClient.Builder chatClientBuilder) {

		return args -> {
			// @formatter:off
			ChatClient chatClient = chatClientBuilder
			
				// Ask user question tool
				.defaultTools(AskUserQuestionTool.builder()
					.questionAnswerFunction(Application::handleQuestions)
					.answersValidation(false)
					.build())

				.defaultAdvisors(
					// Tool calling advisor
					ToolCallAdvisor.builder().conversationHistoryEnabled(false).build(),
					// Chat memory advisor - after the tool calling advisor to remember tool calls
					MessageChatMemoryAdvisor.builder(MessageWindowChatMemory.builder().maxMessages(500).build()).build())

				.build();
				// @formatter:on

			// Start the chat loop
			System.out.println("\nI am your assistant.\n");

			try (Scanner scanner = new Scanner(System.in)) {
				while (true) {
					System.out.print("\nUSER: ");
					System.out.println("\nASSISTANT: " + chatClient.prompt(scanner.nextLine()).call().content());
				}
			}
		};
	}

	private static Map<String, String> handleQuestions(List<Question> questions) {
		Map<String, String> answers = new HashMap<>();
		Scanner scanner = new Scanner(System.in);

		for (Question q : questions) {
			System.out.println("\n" + q.header() + ": " + q.question());

			List<Option> options = q.options();
			for (int i = 0; i < options.size(); i++) {
				Option opt = options.get(i);
				System.out.printf("  %d. %s - %s%n", i + 1, opt.label(), opt.description());
			}

			if (q.multiSelect()) {
				System.out.println("  (Enter numbers separated by commas, or type custom text)");
			}
			else {
				System.out.println("  (Enter a number, or type custom text)");
			}

			String response = scanner.nextLine().trim();
			answers.put(q.question(), parseResponse(response, options));
		}

		return answers;
	}

	private static String parseResponse(String response, List<Option> options) {
		try {
			// Try parsing as option number(s)
			String[] parts = response.split(",");
			List<String> labels = new ArrayList<>();
			for (String part : parts) {
				int index = Integer.parseInt(part.trim()) - 1;
				if (index >= 0 && index < options.size()) {
					labels.add(options.get(index).label());
				}
			}
			return labels.isEmpty() ? response : String.join(", ", labels);
		}
		catch (NumberFormatException e) {
			// Not a number, use as free text
			return response;
		}
	}

}
