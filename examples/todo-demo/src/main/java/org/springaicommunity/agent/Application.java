package org.springaicommunity.agent;

import java.util.List;
import java.util.Scanner;

import org.springaicommunity.agent.tools.BraveWebSearchTool;
import org.springaicommunity.agent.tools.TodoWriteTool;
import org.springaicommunity.agent.tools.TodoWriteTool.Todos;
import org.springaicommunity.agent.tools.TodoWriteTool.Todos.TodoItem;
import org.springaicommunity.agent.utils.AgentEnvironment;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.ToolCallAdvisor;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

@SpringBootApplication
public class Application {

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}

	// Find the top 10 Tom Hanks movies, then group them in groups of 2, and finally print
	// the title name in inverted (e.g. last char first). Use TodoWrite to organize your
	// tasks.

	@Bean
	CommandLineRunner commandLineRunner(ChatClient.Builder chatClientBuilder,
			@Value("${BRAVE_API_KEY:#{null}}") String braveApiKey,
			@Value("classpath:/prompt/MAIN_AGENT_SYSTEM_PROMPT_V2.md") Resource systemPrompt,
			ApplicationEventPublisher applicationEventPublisher) {

		return args -> {
			// @formatter:off
			ChatClient chatClient = chatClientBuilder
				.defaultSystem(p -> p.text(systemPrompt) // system prompt
					.param(AgentEnvironment.ENVIRONMENT_INFO_KEY, AgentEnvironment.info())
					.param(AgentEnvironment.GIT_STATUS_KEY, AgentEnvironment.gitStatus())
					.param(AgentEnvironment.AGENT_MODEL_KEY, "Unknown Model")
					.param(AgentEnvironment.AGENT_MODEL_KNOWLEDGE_CUTOFF_KEY, "Unknown Cutoff"))

				// Todo management tool
				.defaultTools(TodoWriteTool.builder()
					// Publish todo update events
					.todoEventHandler(event ->
						applicationEventPublisher.publishEvent(new TodoUpdateEvent(this, event.todos())))
					.build())	

				// Internet search tool
				.defaultTools(BraveWebSearchTool.builder(braveApiKey).resultCount(15).build())

				// Advisors
				.defaultAdvisors(
					ToolCallAdvisor.builder().conversationHistoryEnabled(false).build(),
					MessageChatMemoryAdvisor.builder(MessageWindowChatMemory.builder().maxMessages(500).build()) .build())
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

	public class TodoUpdateEvent extends ApplicationEvent {

		private final List<TodoItem> todos;

		public TodoUpdateEvent(Object source, List<TodoItem> todos) {
			super(source);
			this.todos = List.copyOf(todos);
		}

		public List<TodoItem> getTodos() {
			return todos;
		}

	}

	@Component
	public class TodoProgressListener {

		@EventListener
		public void onTodoUpdate(TodoUpdateEvent event) {
			int completed = (int) event.getTodos().stream().filter(t -> t.status() == Todos.Status.completed).count();
			int total = event.getTodos().size();

			System.out.printf("\nProgress: %d/%d tasks completed (%.0f%%)\n", completed, total,
					(completed * 100.0 / total));

			for (TodoItem item : event.getTodos()) {
				String statusIcon = switch (item.status()) {
					case completed -> "[✓]";
					case in_progress -> "[→]";
					case pending -> "[ ]";
				};
				System.out.printf("  %s %s\n", statusIcon, item.content());
			}
		}

	}

}
