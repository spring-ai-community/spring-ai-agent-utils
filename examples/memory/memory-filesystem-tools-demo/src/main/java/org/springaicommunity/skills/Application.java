package org.springaicommunity.skills;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Scanner;

import org.springaicommunity.agent.tools.FileSystemTools;
import org.springaicommunity.agent.tools.ShellTools;
import org.springaicommunity.agent.utils.AgentEnvironment;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.ToolCallAdvisor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.Resource;

@SpringBootApplication
public class Application {

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}

	private String toString(Resource resource) throws IOException {
		try {
			return resource.getContentAsString(Charset.defaultCharset());
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Bean
	CommandLineRunner commandLineRunner(ChatClient.Builder chatClientBuilder,
			@Value("${agent.model:Unknown}") String agentModel,
			@Value("${agent.model.knowledge.cutoff:Unknown}") String agentModelKnowledgeCutoff,
			@Value("classpath:/prompt/MAIN_AGENT_SYSTEM_PROMPT_V2.md") Resource systemPrompt,
			@Value("classpath:/prompt/MEMORY_FILESYSTEM_TOOLS_SYSTEM_PROMPT.md") Resource memorySystemPrompt,
			@Value("${agent.memory.dir}") String memoryDir) throws IOException {

		return args -> {

			var systemMessage = toString(systemPrompt) + "\n\n" + toString(memorySystemPrompt);

			ChatClient chatClient = chatClientBuilder // @formatter:off
				// system prompt
				.defaultSystem(p -> p.text(systemMessage) // system prompt
					.param(AgentEnvironment.ENVIRONMENT_INFO_KEY, AgentEnvironment.info())
					.param(AgentEnvironment.GIT_STATUS_KEY, AgentEnvironment.gitStatus())
					.param(AgentEnvironment.AGENT_MODEL_KEY, agentModel)
					.param(AgentEnvironment.AGENT_MODEL_KNOWLEDGE_CUTOFF_KEY, agentModelKnowledgeCutoff)
					.param("MEMORIES_ROOT_DIERCTORY", memoryDir))

				// Built-in tools
				.defaultTools(
					//Bash execution tool
					ShellTools.builder().build(),// built-in shell tools
					// Read, Write and Edit files tool
					FileSystemTools.builder().build())
				
				.defaultAdvisors(
					// Tool Calling advisor
					ToolCallAdvisor.builder().build(),
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
					System.out.print("\nUSER: ");
					System.out.println("\nASSISTANT: " + chatClient.prompt(scanner.nextLine()).call().content());
				}
			}
		};

	}

}
