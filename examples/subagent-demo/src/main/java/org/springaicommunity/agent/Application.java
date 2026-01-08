package org.springaicommunity.agent;

import java.util.List;
import java.util.Map;
import java.util.Scanner;

import org.springaicommunity.agent.tools.BraveWebSearchTool;
import org.springaicommunity.agent.tools.FileSystemTools;
import org.springaicommunity.agent.tools.GlobTool;
import org.springaicommunity.agent.tools.GrepTool;
import org.springaicommunity.agent.tools.ShellTools;
import org.springaicommunity.agent.tools.SkillsTool;
import org.springaicommunity.agent.tools.SmartWebFetchTool;
import org.springaicommunity.agent.tools.TodoWriteTool;
import org.springaicommunity.agent.tools.task.TaskToolCallbackProvider;

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

	@Value("${app.agent.skills.paths}")
	List<String> skillPaths;

	@Value("classpath:/prompt/CODE_AGENT_PROMPT_V2.md")
	Resource systemPrompt;

	@Value("${BRAVE_API_KEY:#{null}}")
	String braveApiKey;

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}

	@Bean
	CommandLineRunner commandLineRunner(ChatClient.Builder chatClientBuilder) {

		return args -> {

			var taskTools = TaskToolCallbackProvider.builder()
				.agentDirectories(
						"/Users/christiantzolov/Dev/projects/spring-ai-agent-utils/examples/subagent-demo/src/main/resources/agents")
				.skillsDirectories(skillPaths)
				.chatClientBuilder(chatClientBuilder.clone()
					.defaultToolContext(Map.of("foo", "bar"))
					.defaultAdvisors(new MyLoggingAdvisor(0, "[TASK]")))
				.build();

			ChatClient chatClient = chatClientBuilder // @formatter:off
				.defaultSystem(systemPrompt)			
				.defaultToolCallbacks(taskTools)
				.defaultToolContext(Map.of("foo", "bar"))

				.defaultToolCallbacks(SkillsTool.builder().addSkillsDirectories(skillPaths).build()) // skills tool
				.defaultTools( // Common agentic tools
					GlobTool.builder().build(),
					ShellTools.builder().build(), // needed by the skills to execute scripts
					FileSystemTools.builder().build(),// needed by the skills to read/write additional resources
					SmartWebFetchTool.builder(chatClientBuilder.clone().build()).build(),
					BraveWebSearchTool.builder(braveApiKey).resultCount(15).build(),
					TodoWriteTool.builder().build(),
					GrepTool.builder().build())

				.defaultAdvisors(
					ToolCallAdvisor.builder()
						.conversationHistoryEnabled(false)
						.build(), // tool calling advisor
					MessageChatMemoryAdvisor.builder(MessageWindowChatMemory.builder().maxMessages(500).build())
						.order(Ordered.HIGHEST_PRECEDENCE + 1000)
						.build(),
					new MyLoggingAdvisor()) // logging advisor
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

}
