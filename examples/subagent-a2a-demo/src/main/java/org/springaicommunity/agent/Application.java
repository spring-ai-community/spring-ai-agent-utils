package org.springaicommunity.agent;

import java.util.List;
import java.util.Scanner;

import org.springaicommunity.agent.common.task.subagent.SubagentReference;
import org.springaicommunity.agent.common.task.subagent.SubagentType;
import org.springaicommunity.agent.subagent.a2a.A2ASubagentDefinition;
import org.springaicommunity.agent.subagent.a2a.A2ASubagentExecutor;
import org.springaicommunity.agent.subagent.a2a.A2ASubagentResolver;
import org.springaicommunity.agent.tools.BraveWebSearchTool;
import org.springaicommunity.agent.tools.FileSystemTools;
import org.springaicommunity.agent.tools.GlobTool;
import org.springaicommunity.agent.tools.GrepTool;
import org.springaicommunity.agent.tools.ShellTools;
import org.springaicommunity.agent.tools.SkillsTool;
import org.springaicommunity.agent.tools.SmartWebFetchTool;
import org.springaicommunity.agent.tools.TodoWriteTool;
import org.springaicommunity.agent.tools.task.TaskTool;
import org.springaicommunity.agent.tools.task.claude.ClaudeSubagentReferences;
import org.springaicommunity.agent.tools.task.claude.ClaudeSubagentType;
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
import org.springframework.core.Ordered;
import org.springframework.core.io.Resource;

@SpringBootApplication
public class Application {

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}

	@Bean
	CommandLineRunner commandLineRunner(ChatClient.Builder chatClientBuilder,
			@Value("${agent.model:Unknown}") String agentModel,
			@Value("${agent.model.knowledge.cutoff:Unknown}") String agentModelKnowledgeCutoff,
			@Value("${agent.skills.paths}") List<Resource> skillPaths,
			@Value("${agent.tasks.paths}") List<Resource> agentPaths,
			@Value("classpath:/prompt/MAIN_AGENT_SYSTEM_PROMPT_V2.md") Resource systemPrompt,
			@Value("${BRAVE_API_KEY:#{null}}") String braveApiKey) {

		return args -> {

			var taskTools = TaskTool.builder()
				// Add Claude Subagent (local)
				.subagentReferences(ClaudeSubagentReferences.fromResources(agentPaths))
				.subagentTypes(ClaudeSubagentType.builder()
					.skillsResources(skillPaths)
					// configuration used by the local Claude subagents
					.chatClientBuilder("default",
							chatClientBuilder.clone().defaultAdvisors(new MyLoggingAdvisor(0, "[TASK]")))

					.braveApiKey(braveApiKey)
					.build())

				// Add A2A Subagent (remote)
				.subagentReferences(new SubagentReference("http://localhost:10001/airbnb", A2ASubagentDefinition.KIND))
				.subagentTypes(new SubagentType(new A2ASubagentResolver(), new A2ASubagentExecutor()))

				.build();

			ChatClient chatClient = chatClientBuilder // @formatter:off
				// system prompt
				.defaultSystem(p -> p.text(systemPrompt) // system prompt
					.param(AgentEnvironment.ENVIRONMENT_INFO_KEY, AgentEnvironment.info())
					.param(AgentEnvironment.GIT_STATUS_KEY, AgentEnvironment.gitStatus())
					.param(AgentEnvironment.AGENT_MODEL_KEY, agentModel)
					.param(AgentEnvironment.AGENT_MODEL_KNOWLEDGE_CUTOFF_KEY, agentModelKnowledgeCutoff))

				// sub-agent task tool callbacks
				.defaultToolCallbacks(taskTools)

				// skills tool
				.defaultToolCallbacks(SkillsTool.builder().addSkillsResources(skillPaths).build())
				
				.defaultTools(
					// task orchestration tools
					TodoWriteTool.builder().build(),

					// common agentic tools
					GlobTool.builder().build(),
					GrepTool.builder().build(),
					ShellTools.builder().build(),
					FileSystemTools.builder().build(),

					SmartWebFetchTool.builder(chatClientBuilder.clone().build()).build(),
					BraveWebSearchTool.builder(braveApiKey).resultCount(15).build())

				// Advisors
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
