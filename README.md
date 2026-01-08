# Spring AI Agent Utils

A Spring AI library that brings [Claude Code](https://code.claude.com)-inspired tools and agent skills to your AI applications.

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

## Features

- **FileSystemTools** - Read, write, and edit files with precise control
- **ShellTools** - Execute shell commands with timeout control, background process management, and regex output filtering
- **GrepTool** - Pure Java grep implementation for code search with regex, glob filtering, and multiple output modes
- **TodoWriteTool** - Structured task management with state tracking
- **SmartWebFetchTool** - AI-powered web content summarization with caching
- **BraveWebSearchTool** - Web search with domain filtering
- **SkillsTool** - Extend AI agent capabilities with reusable, composable knowledge modules defined in Markdown with YAML front-matter. Skills are automatically invoked through semantic matching, just like in Claude Code.

## Quick Start

**1. Add dependency:**

```xml
<dependency>
    <groupId>org.springaicommunity</groupId>
    <artifactId>spring-ai-agent-utils</artifactId>
    <version>2.0.0-SNAPSHOT</version>
</dependency>
```

**2. Configure your agent:**

```java
@SpringBootApplication
public class Application {

    @Bean
    CommandLineRunner demo(ChatClient.Builder chatClientBuilder) {
        return args -> {
            ChatClient chatClient = chatClientBuilder
                // Load agent skills
                .defaultToolCallbacks(SkillsTool.builder()
                    .addSkillsDirectory(".claude/skills")
                    .build())

                // Register tools
                .defaultTools(new ShellTools())
                .defaultTools(new FileSystemTools())
                .defaultTools(GrepTool.builder().build())
                .defaultTools(SmartWebFetchTool.builder(chatClient).build())
                .defaultTools(BraveWebSearchTool.builder(apiKey).build())
                .defaultTools(TodoWriteTool.builder().build())
                .build();

            String response = chatClient
                .prompt("Search for Spring AI documentation and summarize it")
                .call()
                .content();
        };
    }
}
```

## Documentation

For detailed documentation, see:

- **[Library Documentation](spring-ai-agent-utils/README.md)** - Complete API reference, tool capabilities, and skills development guide
- **[Example Applications](#examples)** - Working demos showcasing different use cases


This project reimplements key Claude Code features based on:

- [Claude Code Documentation](https://code.claude.com/docs/en/overview)
- [Claude Code Agent Skills](https://code.claude.com/docs/en/skills#agent-skills)
- [Claude Code Internals](https://agiflow.io/blog/claude-code-internals-reverse-engineering-prompt-augmentation/) - Reverse engineering prompt augmentation
- [Claude Code Skills](https://mikhail.io/2025/10/claude-code-skills/) - Implementation patterns


## Requirements

- Java 17+
- Spring Boot 3.x / 4.x
- Spring AI 2.0.0+
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

Two comprehensive examples demonstrate different use cases:

- **[code-agent-demo](examples/code-agent-demo)** - Full-featured AI coding assistant with interactive CLI, all 7 tools, conversation memory, and multi-model support. Best for understanding complete agent architectures.

- **[skills-demo](examples/skills-demo)** - Focused demonstration of the SkillsTool system with custom skill development, helper scripts, and the ai-tuto educational skill example.

See the [Examples README](examples/README.md) for detailed setup, configuration, and usage guide for both examples.

## License

Apache License 2.0

## Credits

Created by Christian Tzolov ([@tzolov](https://github.com/tzolov))

Inspired by [Claude Code](https://code.claude.com) by Anthropic.

## Links

- [GitHub Repository](https://github.com/spring-ai-community/spring-ai-agent-utils)
- [Issue Tracker](https://github.com/spring-ai-community/spring-ai-agent-utils/issues)
- [Spring AI Documentation](https://docs.spring.io/spring-ai/reference/)
- [Claude Code Documentation](https://code.claude.com/docs/en/overview)

## Contributing

Contributions are welcome! Please feel free to submit issues or pull requests.
