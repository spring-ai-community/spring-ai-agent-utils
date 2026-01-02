package org.springaicommunity.skills;

import java.util.Map;

import org.springaicommunity.ai.agent.skills.SkillsToolProvider;
import org.springaicommunity.ai.agent.tools.BraveWebSearchTool;
import org.springaicommunity.ai.agent.tools.FileSystemTools;
import org.springaicommunity.ai.agent.tools.ShellTools;
import org.springaicommunity.ai.agent.tools.SmartWebFetchTool;
import org.springaicommunity.ai.agent.tools.TodoWriteTool;

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

	@Value("classpath:/CODE_AGENT_PROMPT_V2.md")
	Resource systemPrompt;

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}

	// static final String skillsDir = "/Users/christiantzolov/.claude/skills";
	static final String skillsDir = "/Users/christiantzolov/Dev/projects/spring-ai-agent-utils/.claude/skills";

	// static final String skillsDir =
	// "/Users/christiantzolov/Dev/projects/demo/test-skills/skills/skills/pdf";

	@Bean
	CommandLineRunner commandLineRunner(ChatClient.Builder chatClientBuilder

	) {
		// , ToolCallbackProvider toolCallbackProvider) {

		return args -> {

			ChatClient chatClient = chatClientBuilder // @formatter:off
				// .defaultSystem(systemPrompt)
				.defaultToolCallbacks(SkillsToolProvider.create(skillsDir)) // skills tool
				.defaultTools(new ShellTools())// built-in shell tools
				.defaultTools(new FileSystemTools())// built-in file system tools
				.defaultTools(SmartWebFetchTool.builder(chatClientBuilder.clone().build()).build())
				.defaultTools(BraveWebSearchTool.builder(System.getenv("BRAVE_API_KEY")).resultCount(15).build())
				.defaultTools(new TodoWriteTool())

				// .defaultToolCallbacks(toolCallbackProvider) // MCP tool provider
				.defaultAdvisors(ToolCallAdvisor.builder().build()) // tool calling advisor
				.defaultAdvisors(new MyLoggingAdvisor()) // logging advisor
				.build();
				// @formatter:on

			var answer1 = chatClient
				// .prompt("""
				// Create a PDF explaining the concept of Chain of Thought (CoT) prompting
				// in AI. Don't ask me for more details.
				// """)
				// .prompt("""
				// Explain Spring AI and recursive advisors in simple terms. Do full
				// research before answering. Collect information from internet if needed.
				// """)
				// .prompt("""
				// 		Explain reinforcement learning in simple terms and use. 
				// 		First load the required skills.
				// 		The use the Youtube video https://youtu.be/vXtfdGphr3c?si=xy8U2Al_Um5vE4Jd transcript to support your answer.
				// 		Use absolute paths for the skills and scripts.
				// 		Do not ask me for more details.
				// 		""")
				.prompt("""
						Please review the following class and suggest improvements:
						/Users/christiantzolov/Dev/projects/spring-ai-agent-utils/spring-ai-agent-utils/src/main/java/org/springaicommunity/ai/agent/tools/BraveWebSearchTool.java
						Check also the related tests and make sure they pass.
						""")						
				.toolContext(Map.of("foo", "bar"))
				.call()
				.content();

			System.out.println("CoT Answer: " + answer1);
		};

	}

}
