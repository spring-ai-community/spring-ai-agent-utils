# Spring AI Agent Utils

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Maven Central](https://img.shields.io/maven-central/v/org.springaicommunity/spring-ai-agent-utils.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/org.springaicommunity/spring-ai-agent-utils)
[![Java Version](https://img.shields.io/badge/Java-17%2B-orange)](https://www.oracle.com/java/technologies/javase/jdk17-archive-downloads.html)


A [Spring AI](https://docs.spring.io/spring-ai/reference/2.0-SNAPSHOT/index.html) library that brings [Claude Code](https://code.claude.com/docs/en/settings#tools-available-to-claude)-inspired tools and agent skills to your AI applications.

## Overview

<img style="display: block; margin: auto;" align="left" src="./spring-ai-agent-utils/docs/spring-ai-agent-utils-logo.png" width="250" />

Spring AI Agent Utils reimplements core Claude Code capabilities as Spring AI tools, enabling sophisticated agentic workflows with file operations, shell execution, web access, task management, and extensible agent skills.

This project demonstrates how to reverse-engineer and reimplement Claude Code's powerful features within the Spring AI ecosystem, making them available to Java developers building AI agents.

## Project Structure

```
spring-ai-agent-utils/
├── spring-ai-agent-utils-common/   # Shared subagent SPI (interfaces & records)
├── spring-ai-agent-utils/          # Core library (tools, skills, Claude subagents)
├── spring-ai-agent-utils-a2a/      # A2A protocol subagent implementation
│
└── examples/
    ├── code-agent-demo/            # Full-featured AI coding assistant
    ├── ask-user-question-demo/     # Interactive question-answer demo
    ├── skills-demo/                # Focused skills system demo
    ├── subagent-demo/              # Markdown-defined local sub-agents
    └── subagent-a2a-demo/          # A2A protocol remote sub-agents
```

## Agentic Utils

These are the agent tools needed to implement any agentic behavior

#### Core Tools

- **[AgentEnvironment](spring-ai-agent-utils/docs/AgentEnvironment.md)** - Dynamic agent context utility that provides runtime environment information and git repository status to system prompts
- **[FileSystemTools](spring-ai-agent-utils/docs/FileSystemTools.md)** - Read, write, and edit files with precise control
- **[ShellTools](spring-ai-agent-utils/docs/ShellTools.md)** - Execute shell commands with timeout control, background process management, and regex output filtering
- **[GrepTool](spring-ai-agent-utils/docs/GrepTool.md)** - Pure Java grep implementation for code search with regex, glob filtering, and multiple output modes
- **[GlobTool](spring-ai-agent-utils/docs/GlobTool.md)** - Fast file pattern matching tool for finding files by name patterns with glob syntax
- **[SmartWebFetchTool](spring-ai-agent-utils/docs/SmartWebFetchTool.md)** - AI-powered web content summarization with caching
- **[BraveWebSearchTool](spring-ai-agent-utils/docs/BraveWebSearchTool.md)** - Web search with domain filtering

#### User feedback

- **[AskUserQuestionTool](spring-ai-agent-utils/docs/AskUserQuestionTool.md)** - Ask users clarifying questions with multiple-choice options during agent execution

#### Agent Skills

- **[SkillsTool](spring-ai-agent-utils/docs/SkillsTool.md)** - Extend AI agent capabilities with reusable, composable knowledge modules defined in Markdown with YAML front-matter

#### Task orchestration & multi-agent

- **[TodoWriteTool](spring-ai-agent-utils/docs/TodoWriteTool.md)** - Structured task management with state tracking
- **[TaskTools](spring-ai-agent-utils/docs/TaskTools.md)** - Extensible sub-agent system for delegating complex tasks to specialized agents with multi-model routing and pluggable backends

While these tools can be used standalone, truly agentic behavior emerges when they are combined. SkillsTool naturally pairs with FileSystemTools and ShellTools to execute domain-specific workflows. BraveWebSearchTool and SmartWebFetchTool provide your AI application with access to real-world information. TaskTools orchestrates complex operations by delegating to specialized sub-agents, each equipped with a tailored subset of these tools.

### Detailed Documentation

| Module | Description |
|--------|-------------|
| [**spring-ai-agent-utils**](spring-ai-agent-utils/README.md) | Core library - tools, skills, Claude subagents, and full API reference |
| [**spring-ai-agent-utils-common**](spring-ai-agent-utils-common/README.md) | Shared subagent SPI (SubagentDefinition, SubagentResolver, SubagentExecutor, SubagentType) |
| [**spring-ai-agent-utils-a2a**](spring-ai-agent-utils-a2a/README.md) | A2A protocol subagent for remote agent orchestration |
| [**Examples**](#examples) | Working demos showcasing different use cases |


## Quick Start

**1. Add dependency:**

```xml
<dependency>
    <groupId>org.springaicommunity</groupId>
    <artifactId>spring-ai-agent-utils</artifactId>
    <version>0.4.2</version>
</dependency>
```

_Check the latest version:_ [![](https://img.shields.io/maven-central/v/org.springaicommunity/spring-ai-agent-utils.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/org.springaicommunity/spring-ai-agent-utils)

> **Note:** You need Spring AI version `2.0.0-M2` or later.

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
                .defaultSystem(p -> p.text(agentSystemPrompt) // system prompt
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
                    ToolCallAdvisor.builder().conversationHistoryEnabled(false).build(), // Tool Calling
                    MessageChatMemoryAdvisor.builder(MessageWindowChatMemory.builder().maxMessages(500).build()).build()) // Memory

                .build();

            String response = chatClient
                .prompt("Search for Spring AI documentation and summarize it")
                .call()
                .content();
        };
    }
}
```

## References

This project reimplements key Claude Code features based on:

- [Claude Code Documentation](https://code.claude.com/docs/en/overview)
- [Claude Code Agent Skills](https://code.claude.com/docs/en/skills#agent-skills)
- [Claude Code Internals](https://agiflow.io/blog/claude-code-internals-reverse-engineering-prompt-augmentation/) - Reverse engineering prompt augmentation
- [Claude Code Skills](https://mikhail.io/2025/10/claude-code-skills/) - Implementation patterns


## Requirements

- Java 17+
- Spring Boot 3.x / 4.x
- Spring AI 2.0.0-M2 or later
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
| [code-agent-demo](examples/code-agent-demo) | Full-featured AI coding assistant with interactive CLI, all tools, and multi-model support |
| [todo-demo](examples/todo-demo) | Structured task management with `TodoWriteTool` and real-time progress tracking |
| [subagent-demo](examples/subagent-demo) | Hierarchical sub-agent system with Markdown-defined local sub-agents |
| [subagent-a2a-demo](examples/subagent-a2a-demo) | A2A protocol integration for delegating tasks to remote agents |
| [skills-demo](examples/skills-demo) | SkillsTool system with custom skill development and the ai-tuto example |
| [ask-user-question-demo](examples/ask-user-question-demo) | Interactive agent-user communication with `AskUserQuestionTool` |

See [examples/README.md](examples/README.md) for setup and usage details.

## License

Apache License 2.0

## Links

- [GitHub Repository](https://github.com/spring-ai-community/spring-ai-agent-utils)
- [Issue Tracker](https://github.com/spring-ai-community/spring-ai-agent-utils/issues)
- [Spring AI Documentation](https://docs.spring.io/spring-ai/reference/)
- [Claude Code Documentation](https://code.claude.com/docs/en/overview)

## Contributing

Contributions are welcome! Please feel free to submit issues or pull requests.
