package org.springaicommunity.agent;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicLong;

import org.springaicommunity.agent.advisors.InterruptAdvisor;
import org.springaicommunity.agent.advisors.TurnInterruptedException;
import org.springaicommunity.agent.tools.AgentToolset;
import org.springaicommunity.agent.tools.AskUserQuestionTool;
import org.springaicommunity.agent.tools.BraveWebSearchTool;
import org.springaicommunity.agent.tools.FileSystemTools;
import org.springaicommunity.agent.tools.GrepTool;
import org.springaicommunity.agent.tools.ShellTools;
import org.springaicommunity.agent.tools.SkillsTool;
import org.springaicommunity.agent.tools.SmartWebFetchTool;
import org.springaicommunity.agent.tools.TodoWriteTool;
import org.springaicommunity.agent.tools.ToolCallListeners;
import org.springaicommunity.agent.utils.AgentEnvironment;
import org.springaicommunity.agent.utils.CommandLineQuestionHandler;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.io.Resource;
import org.springframework.util.StringUtils;

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
			@Value("${agent.skills.paths}") List<Resource> skillPaths,
			@Value("${agent.turn.timeout:10m}") Duration turnTimeout, ToolCallbackProvider mcpToolCallbackProvider) {

		return args -> {
			// @formatter:off
			List<ToolCallback> tools = new ArrayList<>();

			// AirBnb MCP Tools
			tools.addAll(Arrays.asList(mcpToolCallbackProvider.getToolCallbacks()));

			// Skills tool
			tools.add(SkillsTool.builder().addSkillsResources(skillPaths).build());

			tools.addAll(Arrays.asList(ToolCallbacks.from(
				// Todo management tool
				TodoWriteTool.builder().build(),

				// Ask user question tool
				AskUserQuestionTool.builder()
					.questionHandler(new CommandLineQuestionHandler())
					.answersValidation(false)
					.build(),

				// Common agentic tools
				ShellTools.builder().build(), // needed by the skills to execute scripts
				FileSystemTools.builder().build(),// needed by the skills to read/write additional resources
				SmartWebFetchTool.builder(chatClientBuilder.clone().build()).build(),
				GrepTool.builder().build())));

			// Brave web search is optional: register it only when BRAVE_API_KEY is set
			if (StringUtils.hasText(braveApiKey)) {
				tools.addAll(Arrays.asList(ToolCallbacks.from(BraveWebSearchTool.builder(braveApiKey).resultCount(15).build())));
			}

			// Trace every tool call on the console (ToolCallListener)
			List<ToolCallback> observedTools = ToolCallListeners.wrapAll(tools, new ConsoleToolCallListener());

			// Cancel a turn that runs past the deadline (InterruptAdvisor). The signal is
			// polled before every model request of the tool-calling loop.
			AtomicLong turnDeadline = new AtomicLong(Long.MAX_VALUE);

			ChatClient chatClient = chatClientBuilder
				.defaultSystem(p -> p.text(systemPrompt) // system prompt
					.param(AgentEnvironment.ENVIRONMENT_INFO_KEY, AgentEnvironment.info())
					.param(AgentEnvironment.GIT_STATUS_KEY, AgentEnvironment.gitStatus())
					.param(AgentEnvironment.AGENT_MODEL_KEY, agentModel)
					.param(AgentEnvironment.AGENT_MODEL_KNOWLEDGE_CUTOFF_KEY, agentModelKnowledgeCutoff)
					.params(AgentToolset.promptVariables(tools)))

				// All tools, wrapped with the console listener
				.defaultTools(observedTools)

				// Advisors
				.defaultAdvisors(
					InterruptAdvisor.builder()
						.interruptSignal(() -> System.currentTimeMillis() > turnDeadline.get())
						.build(),
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
					String userInput = scanner.nextLine();
					turnDeadline.set(System.currentTimeMillis() + turnTimeout.toMillis());
					try {
						System.out.println("\n> ASSISTANT: " + chatClient.prompt(userInput)
							.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "session-1"))
							.call()
							.content());
					}
					catch (TurnInterruptedException e) {
						System.out.println("\n> ASSISTANT: [turn stopped after exceeding " + turnTimeout + "]");
					}
				}
			}
		};
	}

}
