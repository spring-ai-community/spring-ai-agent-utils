package org.springaicommunity.agent;

import java.util.List;
import java.util.Scanner;

import org.springaicommunity.agent.tools.AskUserQuestionTool;
import org.springaicommunity.agent.tools.BraveWebSearchTool;
import org.springaicommunity.agent.tools.FileSystemTools;
import org.springaicommunity.agent.tools.GrepTool;
import org.springaicommunity.agent.tools.ShellTools;
import org.springaicommunity.agent.tools.SkillsTool;
import org.springaicommunity.agent.tools.SmartWebFetchTool;
import org.springaicommunity.agent.tools.TodoWriteTool;
import org.springaicommunity.agent.utils.AgentEnvironment;
import org.springaicommunity.agent.utils.CommandLineQuestionHandler;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.ToolCallAdvisor;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.io.Resource;

@SpringBootApplication
public class Application {

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}

	@Bean
	CommandLineRunner commandLineRunner(ChatClient.Builder chatClientBuilder,
			@Value("${BRAVE_API_KEY:#{null}}") String braveApiKey,
			@Value("classpath:/prompt/MAIN_AGENT_SYSTEM_PROMPT_V2.md") Resource systemPrompt,
			@Value("${agent.model.knowledge.cutoff:Unknown}") String agentModelKnowledgeCutoff,
			@Value("${agent.model:Unknown}") String agentModel,
			@Value("${agent.skills.paths}") List<Resource> skillPaths, ToolCallbackProvider mcpToolCallbackProvider) {

		return args -> {
			// @formatter:off
			ChatClient chatClient = chatClientBuilder
				.defaultSystem(p -> p.text(systemPrompt) // system prompt
					.param(AgentEnvironment.ENVIRONMENT_INFO_KEY, AgentEnvironment.info())
					.param(AgentEnvironment.GIT_STATUS_KEY, AgentEnvironment.gitStatus())
					.param(AgentEnvironment.AGENT_MODEL_KEY, agentModel)
					.param(AgentEnvironment.AGENT_MODEL_KNOWLEDGE_CUTOFF_KEY, agentModelKnowledgeCutoff))

				// AirBnb MCP Tools
				.defaultToolCallbacks(mcpToolCallbackProvider)

				// Skills tool
				.defaultToolCallbacks(SkillsTool.builder().addSkillsResources(skillPaths).build())

				// Todo management tool
				.defaultTools(TodoWriteTool.builder().build())

				// Ask user question tool
				.defaultTools(AskUserQuestionTool.builder()
					.questionHandler(new CommandLineQuestionHandler())
					.answersValidation(false)
					.build())

				// Common agentic tools
				.defaultTools(
					ShellTools.builder().build(), // needed by the skills to execute scripts
					FileSystemTools.builder().build(),// needed by the skills to read/write additional resources
					SmartWebFetchTool.builder(chatClientBuilder.clone().build()).build(),
					BraveWebSearchTool.builder(braveApiKey).resultCount(15).build(),				
					GrepTool.builder().build())

				// Advisors
				.defaultAdvisors(
					ToolCallAdvisor.builder()
						.conversationHistoryEnabled(false)
						.build(), // tool calling advisor
					MessageChatMemoryAdvisor.builder(MessageWindowChatMemory.builder().maxMessages(500).build())
						.order(Ordered.HIGHEST_PRECEDENCE + 1000)
						.build())
					// logging advisor	
					// MyLoggingAdvisor.builder()
					// 	.showAvailableTools(false)
					// 	.showSystemMessage(false)
					// 	.build()) 
				.build();
				// @formatter:on

			// Start the chat loop
			System.out.println("\nI am your assistant.\n");

			try (Scanner scanner = new Scanner(System.in)) {
				while (true) {
					System.out.print("\n> USER: ");
					System.out.println("\n> ASSISTANT: " + chatClient.prompt(scanner.nextLine()).call().content());
				}
			}
		};
	}
}
