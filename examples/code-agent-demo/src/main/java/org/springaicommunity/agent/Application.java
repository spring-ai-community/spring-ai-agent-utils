package org.springaicommunity.agent;

import java.util.Scanner;

import org.springaicommunity.agent.tools.BraveWebSearchTool;
import org.springaicommunity.agent.tools.FileSystemTools;
import org.springaicommunity.agent.tools.ShellTools;
import org.springaicommunity.agent.tools.SkillsTool;
import org.springaicommunity.agent.tools.SmartWebFetchTool;
import org.springaicommunity.agent.tools.TodoWriteTool;

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

	// static final String skillsDir = "/Users/christiantzolov/.claude/skills";
	static final String skillsDir = "/Users/christiantzolov/Dev/projects/spring-ai-agent-utils/.claude/skills";

	@Value("classpath:/CODE_AGENT_PROMPT_V2.md")
	Resource systemPrompt;

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}

	@Bean
	CommandLineRunner commandLineRunner(ChatClient.Builder chatClientBuilder) {

		MessageWindowChatMemory chatMemory = MessageWindowChatMemory.builder().maxMessages(500).build();

		return args -> {

			ChatClient chatClient = chatClientBuilder // @formatter:off
				.defaultSystem(systemPrompt)
				.defaultToolCallbacks(SkillsTool.builder().addSkillsDirectory(skillsDir).build()) // skills tool
				.defaultTools(new ShellTools())// built-in shell tools
				.defaultTools(new FileSystemTools())// built-in file system tools
				.defaultTools(SmartWebFetchTool.builder(chatClientBuilder.clone().build()).build())
				.defaultTools(BraveWebSearchTool.builder(System.getenv("BRAVE_API_KEY")).resultCount(15).build())
				.defaultTools(new TodoWriteTool())

				// .defaultToolCallbacks(toolCallbackProvider) // MCP tool provider
				.defaultAdvisors(ToolCallAdvisor.builder().conversationHistoryEnabled(false).build()) // tool calling advisor
				.defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).order(Ordered.HIGHEST_PRECEDENCE + 1000).build())
				.defaultAdvisors(new MyLoggingAdvisor()) // logging advisor
				.build();
				// @formatter:on

			// 3. Start the chat loop
			System.out.println("\nI am your assistant.\n");
			try (Scanner scanner = new Scanner(System.in)) {
				while (true) {
					System.out.print("\nUSER: ");
					System.out.println("\nASSISTANT: " + chatClient.prompt(scanner.nextLine()).call().content());
				}
			}
		};

	}

}
