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
import org.springframework.ai.session.DefaultSessionService;
import org.springframework.ai.session.EventFilter;
import org.springframework.ai.session.InMemorySessionRepository;
import org.springframework.ai.session.SessionService;
import org.springframework.ai.session.advisor.SessionAdvisors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.Resource;
import org.springframework.util.StringUtils;

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

			var localSubagentTypes = ClaudeSubagentType.builder()
				.skillsResources(skillPaths)
				.chatClientBuilder("default",
						chatClientBuilder.clone().defaultAdvisors(new MyLoggingAdvisor(0, "[TASK]")))
				.braveApiKey(braveApiKey)
				.build();

			var taskTools = TaskTool.builder()
				// Add Claude Subagent (local)
				.subagentTypes(localSubagentTypes)
				.subagentReferences(ClaudeSubagentReferences.fromResources(agentPaths))

				// Add A2A Subagent (remote)
				.subagentTypes(new SubagentType(new A2ASubagentResolver(), new A2ASubagentExecutor()))
				.subagentReferences(new SubagentReference("http://localhost:10001/airbnb", A2ASubagentDefinition.KIND))

				.build();

			// Brave web search is optional: register it only when BRAVE_API_KEY is set
			List<Object> webSearchTools = StringUtils.hasText(braveApiKey)
					? List.of(BraveWebSearchTool.builder(braveApiKey).resultCount(15).build()) : List.of();

			// Short-term conversation memory: an in-memory session store, reset on every run
			SessionService sessionService = DefaultSessionService.builder()
				.sessionRepository(InMemorySessionRepository.builder().build())
				.build();

			ChatClient chatClient = chatClientBuilder // @formatter:off
				// system prompt
				.defaultSystem(p -> p.text(systemPrompt) // system prompt
					.param(AgentEnvironment.ENVIRONMENT_INFO_KEY, AgentEnvironment.info())
					.param(AgentEnvironment.GIT_STATUS_KEY, AgentEnvironment.gitStatus())
					.param(AgentEnvironment.AGENT_MODEL_KEY, agentModel)
					.param(AgentEnvironment.AGENT_MODEL_KNOWLEDGE_CUTOFF_KEY, agentModelKnowledgeCutoff))
									
				.defaultTools(
					// Sub-agent task tool callbacks
					taskTools,

					// Agent Skills tool
					SkillsTool.builder().addSkillsResources(skillPaths).build(),

					// Task orchestration tools
					TodoWriteTool.builder().build(),

					// Common agentic tools
					GlobTool.builder().build(),
					GrepTool.builder().build(),
					ShellTools.builder().build(),
					FileSystemTools.builder().build(),
					SmartWebFetchTool.builder(chatClientBuilder.clone().build()).build())

				// Optional web search tool
				.defaultTools(webSearchTools.toArray())

				// Advisors
				.defaultAdvisors(new MyLoggingAdvisor()) // logging advisor

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
