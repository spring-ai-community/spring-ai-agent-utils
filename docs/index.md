# Spring AI Agent Utils

A [Spring AI](https://docs.spring.io/spring-ai/reference/2.0-SNAPSHOT/index.html) library that brings agent  tools and agent skills to your AI applications.

## Overview

Spring AI Agent Utils reimplements core Claude Code capabilities as Spring AI tools, enabling sophisticated agentic workflows with file operations, shell execution, web access, task management, and extensible agent skills.

This project demonstrates how to reverse-engineer and reimplement Claude Code's powerful features within the Spring AI ecosystem, making them available to Java developers building AI agents.

## Project Structure

```
spring-ai-agent-utils/
├── spring-ai-agent-utils-common/        # Shared subagent SPI (interfaces & records)
├── spring-ai-agent-utils/               # Core library (tools, advisors, skills, Claude subagents)
├── spring-ai-agent-utils-a2a/           # A2A protocol subagent implementation
├── spring-ai-agent-utils-bom/           # Bill of Materials for version management
│
└── examples/
    ├── code-agent-demo/                 # Full-featured AI coding assistant
    ├── ask-user-question-demo/          # Interactive question-answer demo
    ├── skills-demo/                     # Focused skills system demo
    ├── subagent-demo/                   # Markdown-defined local sub-agents
    ├── subagent-a2a-demo/               # A2A protocol remote sub-agents
    ├── todo-demo/                       # TodoWriteTool task management demo
    └── memory/
        ├── memory-tools-demo/           # Long-term memory with AutoMemoryTools (manual setup)
        ├── memory-filesystem-tools-demo/# Long-term memory with general FileSystemTools
        └── memory-tools-advisor-demo/   # Long-term memory via AutoAutoMemoryToolsAdvisor
```

## Quick Start

**1. Add dependency:**

Use the BOM to manage versions consistently across all modules:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springaicommunity</groupId>
            <artifactId>spring-ai-agent-utils-bom</artifactId>
            <version>0.6.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>org.springaicommunity</groupId>
        <artifactId>spring-ai-agent-utils</artifactId>
    </dependency>
</dependencies>
```

Or add the core library directly:

```xml
<dependency>
    <groupId>org.springaicommunity</groupId>
    <artifactId>spring-ai-agent-utils</artifactId>
    <version>0.6.0</version>
</dependency>
```

_Check the latest version:_ [![](https://img.shields.io/maven-central/v/org.springaicommunity/spring-ai-agent-utils.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/org.springaicommunity/spring-ai-agent-utils)

!!! note
    You need Spring AI version `2.0.0-M4` or later.

**2. Configure your agent:**

```java
@SpringBootApplication
public class Application {

    @Bean
    CommandLineRunner demo(ChatClient.Builder chatClientBuilder,
        @Value("${BRAVE_API_KEY}") String braveApiKey,
        @Value("${agent.skills.paths}") List<Resource> skillPaths,
        @Value("classpath:/prompt/MAIN_AGENT_SYSTEM_PROMPT_V2.md") Resource agentSystemPrompt) {

        return args -> {
            // Configure Task tool with Claude sub-agents
            var taskTool = TaskTool.builder()
                .subagentTypes(ClaudeSubagentType.builder()
                    .chatClientBuilder("default", chatClientBuilder.clone())
                    .skillsResources(skillPaths)
                    .braveApiKey(braveApiKey)
                    .build())
                .build();

            ChatClient chatClient = chatClientBuilder
                // Main agent prompt
                .defaultSystem(p -> p.text(agentSystemPrompt)
                    .param(AgentEnvironment.ENVIRONMENT_INFO_KEY, AgentEnvironment.info())
                    .param(AgentEnvironment.GIT_STATUS_KEY, AgentEnvironment.gitStatus())
                    .param(AgentEnvironment.AGENT_MODEL_KEY, "claude-sonnet-4-5-20250929")
                    .param(AgentEnvironment.AGENT_MODEL_KNOWLEDGE_CUTOFF_KEY, "2025-01-01"))

                // Sub-Agents
                .defaultToolCallbacks(taskTool)

                // Skills
                .defaultToolCallbacks(SkillsTool.builder()
                    .addSkillsResources(skillPaths)
                    .build())

                // Core Tools
                .defaultTools(
                    ShellTools.builder().build(),
                    FileSystemTools.builder().build(),
                    GrepTool.builder().build(),
                    GlobTool.builder().build(),
                    SmartWebFetchTool.builder(chatClientBuilder.clone().build()).build(),
                    BraveWebSearchTool.builder(braveApiKey).build())

                // Task orchestration
                .defaultTools(TodoWriteTool.builder().build())

                // User feedback tool (use CommandLineQuestionHandler for CLI apps)
                .defaultTools(AskUserQuestionTool.builder()
                    .questionHandler(new CommandLineQuestionHandler())
                    .build())

                // Advisors
                .defaultAdvisors(
                    ToolCallAdvisor.builder().conversationHistoryEnabled(false).build(),
                    MessageChatMemoryAdvisor.builder(MessageWindowChatMemory.builder().maxMessages(500).build()).build())

                .build();

            String response = chatClient
                .prompt("Search for Spring AI documentation and summarize it")
                .call()
                .content();
        };
    }
}
```

## Requirements

- Java 17+
- Spring Boot 3.x / 4.x
- Spring AI 2.0.0-M4 or later
- Maven 3.6+

## Building

```bash
# Build the entire project
mvn clean install

# Run an example
cd examples/code-agent-demo  # or examples/skills-demo
mvn spring-boot:run
```

## Examples

| Example | Description |
|---------|-------------|
| `code-agent-demo` | Full-featured AI coding assistant with interactive CLI, all tools, and multi-model support |
| `todo-demo` | Structured task management with `TodoWriteTool` and real-time progress tracking |
| `subagent-demo` | Hierarchical sub-agent system with Markdown-defined local sub-agents |
| `subagent-a2a-demo` | A2A protocol integration for delegating tasks to remote agents |
| `skills-demo` | SkillsTool system with custom skill development and the ai-tuto example |
| `ask-user-question-demo` | Interactive agent-user communication with `AskUserQuestionTool` |
| `memory/memory-tools-demo` | Long-term memory across conversations using dedicated, sandboxed `AutoMemoryTools` (manual setup) |
| `memory/memory-filesystem-tools-demo` | Long-term memory using general-purpose `FileSystemTools` — no dedicated memory tooling required |
| `memory/memory-tools-advisor-demo` | Long-term memory via `AutoAutoMemoryToolsAdvisor` — advisor-based setup with consolidation trigger |

## License

Apache License 2.0

## Links

- [GitHub Repository](https://github.com/spring-ai-community/spring-ai-agent-utils)
- [Issue Tracker](https://github.com/spring-ai-community/spring-ai-agent-utils/issues)
- [Spring AI Documentation](https://docs.spring.io/spring-ai/reference/)

## Contributing

Contributions are welcome! Please feel free to submit issues or pull requests.
