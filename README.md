# Spring AI Agent Utils

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Maven Central](https://img.shields.io/maven-central/v/org.springaicommunity/spring-ai-agent-utils.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/org.springaicommunity/spring-ai-agent-utils)
[![Java Version](https://img.shields.io/badge/Java-17%2B-orange)](https://www.oracle.com/java/technologies/javase/jdk17-archive-downloads.html)


A [Spring AI](https://docs.spring.io/spring-ai/reference/2.0-SNAPSHOT/index.html) library that brings [Claude Code](https://code.claude.com)-inspired tools and agent skills to your AI applications.

## Overview

Spring AI Agent Utils reimplements core Claude Code capabilities as Spring AI tools, enabling sophisticated agentic workflows with file operations, shell execution, web access, task management, and extensible agent skills.

This project demonstrates how to reverse-engineer and reimplement Claude Code's powerful features within the Spring AI ecosystem, making them available to Java developers building AI agents.

## Project Structure

```
spring-ai-agent-utils/
├── spring-ai-agent-utils/     # Core library
│   └── README.md              # Detailed documentation
├── examples/
│   ├── code-agent-demo/       # Full-featured AI coding assistant
│   └── skills-demo/           # Focused skills system demo
└── .claude/skills/            # Example agent skills
    └── ai-tuto/               # AI tutor skill example
```

## Agentic Utils

These are the core tools needed to implement any agentic behavior:

- **[FileSystemTools](spring-ai-agent-utils/docs/FileSystemTools.md)** - Read, write, and edit files with precise control
- **[ShellTools](spring-ai-agent-utils/docs/ShellTools.md)** - Execute shell commands with timeout control, background process management, and regex output filtering
- **[GrepTool](spring-ai-agent-utils/docs/GrepTool.md)** - Pure Java grep implementation for code search with regex, glob filtering, and multiple output modes
- **[GlobTool](spring-ai-agent-utils/docs/GlobTool.md)** - Fast file pattern matching tool for finding files by name patterns with glob syntax
- **[TodoWriteTool](spring-ai-agent-utils/docs/TodoWriteTool.md)** - Structured task management with state tracking
- **[AskUserQuestionTool](spring-ai-agent-utils/docs/AskUserQuestionTool.md)** - Ask users clarifying questions with multiple-choice options during agent execution
- **[SmartWebFetchTool](spring-ai-agent-utils/docs/SmartWebFetchTool.md)** - AI-powered web content summarization with caching
- **[BraveWebSearchTool](spring-ai-agent-utils/docs/BraveWebSearchTool.md)** - Web search with domain filtering
- **[SkillsTool](spring-ai-agent-utils/docs/SkillsTool.md)** - Extend AI agent capabilities with reusable, composable knowledge modules defined in Markdown with YAML front-matter
- **[TaskTools](spring-ai-agent-utils/docs/TaskTools.md)** - Hierarchical autonomous sub-agent system for delegating complex tasks to specialized agents with dedicated context windows

While these tools can be used standalone, truly agentic behavior emerges when they are combined. SkillsTool naturally pairs with FileSystemTools and ShellTools to execute domain-specific workflows. BraveWebSearchTool and SmartWebFetchTool provide your AI application with access to real-world information. TaskTools orchestrates complex operations by delegating to specialized sub-agents, each equipped with a tailored subset of these tools.

### Detailed documentation

- **[Agent Utils Library Documentation](spring-ai-agent-utils/README.md)** - Complete API reference, tool capabilities, and skills development guide
- **[Example Applications](#examples)** - Working demos showcasing different use cases


## Quick Start

**1. Add dependency:**

```xml
<dependency>
    <groupId>org.springaicommunity</groupId>
    <artifactId>spring-ai-agent-utils</artifactId>
    <version>0.1.1</version>
</dependency>
```
_Check the latest version:_ [![](https://img.shields.io/maven-central/v/org.springaicommunity/spring-ai-agent-utils.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/org.springaicommunity/spring-ai-agent-utils)

> **Note:** You need Sping-AI version `2.0.0-SNAPSHOT` or `2.0.0-M2` when released.

**2. Configure your agent:**

```java
@SpringBootApplication
public class Application {

    @Bean
    CommandLineRunner demo(ChatClient.Builder chatClientBuilder) {
        
        var taskTools = TaskToolCallbackProvider.builder()
				.agentDirectories(agentsPaths)
				.skillsDirectories(skillPaths)
				.chatClientBuilder(chatClientBuilder.clone()
					.defaultToolContext(Map.of("foo", "bar")))
				.build();

        return args -> {
            ChatClient chatClient = chatClientBuilder
                // Load skills
                .defaultToolCallbacks(SkillsTool.builder()
                    .addSkillsDirectory(".claude/skills")
                    .build())
                
                // Subagents
                .defaultToolCallbacks(taskTools)

                // Register tools
                .defaultTools(
                    ShellTools.builder().build(),
                    FileSystemTools.builder().build(),
                    GrepTool.builder().build(),
                    GlobTool.builder().build(),
                    SmartWebFetchTool.builder(chatClient).build(),
                    BraveWebSearchTool.builder(apiKey).build(),
                    TodoWriteTool.builder().build(),
                    AskUserQuestionTool.builder()
                        .questionAnswerFunction(this::handleUserQuestions)
                        .build())
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
- Spring AI 2.0.0-SNAPSHOT (or 2.0.0-M2 when released)
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

Three comprehensive examples demonstrate different use cases:

- **[code-agent-demo](examples/code-agent-demo)** - Full-featured AI coding assistant with interactive CLI, all tools, conversation memory, and multi-model support. Best for understanding complete agent architectures.

- **[subagent-demo](examples/subagent-demo)** - Demonstrates hierarchical sub-agent system with custom Spring AI expert sub-agent and TaskTools integration. Best for learning hierarchical agent patterns.

- **[skills-demo](examples/skills-demo)** - Focused demonstration of the SkillsTool system with custom skill development, helper scripts, and the ai-tuto educational skill example.

See the [Examples README](examples/README.md) for detailed setup, configuration, and usage guide for all examples.

## License

Apache License 2.0

## Links

- [GitHub Repository](https://github.com/spring-ai-community/spring-ai-agent-utils)
- [Issue Tracker](https://github.com/spring-ai-community/spring-ai-agent-utils/issues)
- [Spring AI Documentation](https://docs.spring.io/spring-ai/reference/)
- [Claude Code Documentation](https://code.claude.com/docs/en/overview)

## Contributing

Contributions are welcome! Please feel free to submit issues or pull requests.
