# Spring AI Agent Utils

A Spring AI library that brings Claude Code-inspired tools and skills to your AI agents.

## Overview

Spring AI Agent Utils reimplements core [Claude Code](https://code.claude.com/docs/en/overview) capabilities as Spring AI tools, enabling sophisticated agentic workflows with file operations, shell execution, web access, task management, and extensible skills.

## Features

### Tools

- **FileSystemTools** - Read, write, and edit files with precise control
- **ShellTools** - Execute shell commands with timeout control, background process management, and regex output filtering
- **GrepTool** - Pure Java grep implementation for code search with regex, glob filtering, and multiple output modes
- **GlobTool** - Fast file pattern matching tool for finding files by name patterns with glob syntax
- **TodoWriteTool** - Structured task management with state tracking
- **SmartWebFetchTool** - AI-powered web content summarization with caching
- **BraveWebSearchTool** - Web search with domain filtering
- **SkillsTool** - Extend AI agent capabilities with reusable, composable knowledge modules defined in Markdown with YAML front-matter.

## Installation

**Maven:**
```xml
<dependency>
    <groupId>org.springaicommunity</groupId>
    <artifactId>spring-ai-agent-utils</artifactId>
    <version>2.0.0-SNAPSHOT</version>
</dependency>
```

## Quick Start

```java
@SpringBootApplication
public class Application {

    @Bean
    CommandLineRunner demo(ChatClient.Builder chatClientBuilder) {
        return args -> {
            ChatClient chatClient = chatClientBuilder
                // Load skills
                .defaultToolCallbacks(SkillsTool.builder()
                    .addSkillsDirectory(".claude/skills")
                    .build())

                // Register tools
                .defaultTools(new ShellTools())
                .defaultTools(new FileSystemTools())
                .defaultTools(new GrepTool())
                .defaultTools(GlobTool.builder().build())
                .defaultTools(SmartWebFetchTool.builder(chatClient).build())
                .defaultTools(BraveWebSearchTool.builder(apiKey).build())
                .defaultTools(new TodoWriteTool())
                .build();

            String response = chatClient
                .prompt("Search for Spring AI documentation and summarize it")
                .call()
                .content();
        };
    }
}
```

## Tool Details

### FileSystemTools

Read, write, and edit files with precise control. Provides three core operations: Read for reading files with pagination, Write for creating/overwriting files, and Edit for precise string replacement with safety checks.

[**View Full Documentation →**](docs/FileSystemTools.md)

**Quick Example:**
```java
FileSystemTools fileTools = new FileSystemTools();

// Read a file
String content = fileTools.read("/path/to/file.txt", null, null, toolContext);

// Edit with precise replacement
fileTools.edit(filePath, "oldValue", "newValue", null, toolContext);
```

### ShellTools

Execute shell commands with background process support. Includes Bash for command execution with optional timeout and background mode, BashOutput for monitoring background processes with regex filtering, and KillShell for graceful process termination.

[**View Full Documentation →**](docs/ShellTools.md)

**Quick Example:**
```java
ShellTools shellTools = new ShellTools();

// Run command in background
String result = shellTools.bash("npm run dev", null, "Start dev server", true);
// Returns: "bash_id: shell_1234567890\n\nBackground shell started..."

// Monitor output with optional filtering
String output = shellTools.bashOutput("shell_1234567890", null);

// Kill background process
String killResult = shellTools.killShell("shell_1234567890");
```

### GrepTool

Pure Java grep implementation for code search with regex, glob filtering, and multiple output modes. No external ripgrep dependency required.

[**View Full Documentation →**](docs/GrepTool.md)

**Quick Example:**
```java
GrepTool grepTool = new GrepTool();

// Search Java files for pattern
String result = grepTool.grep("public class.*", "./src", null,
    OutputMode.files_with_matches, null, null, null, null, null, "java", null, null, null);
```

### GlobTool

Fast file pattern matching tool for finding files by name patterns. Uses pure Java implementation with glob syntax support, sorted by modification time.

[**View Full Documentation →**](docs/GlobTool.md)

**Quick Example:**
```java
GlobTool globTool = GlobTool.builder().build();

// Find all Java files
String files = globTool.glob("**/*.java", "./src");

// Find TypeScript components
String components = globTool.glob("**/*Component.tsx", "./src");
```

### SmartWebFetchTool

AI-powered web content fetching and summarization tool with intelligent caching and safety features. Fetches web pages, converts HTML to Markdown, and uses AI to extract relevant information based on a user prompt.

[**View Full Documentation →**](docs/SmartWebFetchTool.md)

**Quick Example:**
```java
// Build with required ChatClient
SmartWebFetchTool webFetch = SmartWebFetchTool.builder(chatClient)
    .maxContentLength(150_000)    // Optional: default 100KB
    .domainSafetyCheck(true)      // Optional: default true
    .maxRetries(2)                // Optional: default 2
    .build();

// Fetch and summarize web content
String result = webFetch.webFetch(
    "https://docs.spring.io/spring-ai/reference/",
    "What are the key features of Spring AI?"
);
```

### TodoWriteTool

Structured task list management for AI coding sessions. Helps AI agents track progress, organize complex tasks, and provide visibility into task execution.

[**View Full Documentation →**](docs/TodoWriteTool.md)

**Quick Example:**
```java
TodoWriteTool todoTool = new TodoWriteTool();

// Create and manage task list
Todos todos = new Todos(List.of(
    new TodoItem("Read configuration", Status.completed, "Reading configuration"),
    new TodoItem("Parse settings", Status.in_progress, "Parsing settings"),
    new TodoItem("Validate config", Status.pending, "Validating config")
));

todoTool.todoWrite(todos);
```

### BraveWebSearchTool

Web search capabilities using the Brave Search API. Provides up-to-date information from the web with optional domain filtering.

[**View Full Documentation →**](docs/BraveWebSearchTool.md)

**Quick Example:**
```java
// Build with API key
BraveWebSearchTool searchTool = BraveWebSearchTool.builder(apiKey)
    .resultCount(10)  // Optional: default 10
    .build();

// Search the web
String results = searchTool.webSearch(
    "Spring AI features 2025",
    null,  // allowedDomains (optional)
    null   // blockedDomains (optional)
);

// Or use search operators for efficiency
String results2 = searchTool.webSearch("Spring AI site:spring.io", null, null);
```

### SkillsTool - Agent Skills System

Extend AI agent capabilities with reusable, composable knowledge modules defined in Markdown with YAML front-matter. Based on [Claude Code's Agent Skills](https://code.claude.com/docs/en/skills#agent-skills), skills enable specialized task handling through semantic matching.

[**View Full Documentation →**](docs/SkillsTool.md)

**Quick Example:**
```java
// Register SkillsTool with skill directories
ChatClient chatClient = chatClientBuilder
    .defaultToolCallbacks(SkillsTool.builder()
        .addSkillsDirectory(".claude/skills")
        .build())
    .defaultTools(new FileSystemTools())  // For reading reference files
    .defaultTools(new ShellTools())       // For executing scripts
    .build();
```

**Create a Skill:** `.claude/skills/my-skill/SKILL.md`
```markdown
---
name: my-skill
description: What this skill does and when to use it. Include trigger keywords.
---

# My Skill
Instructions for the AI agent to follow...
```

## Configuration

**application.properties:**
```properties
# Model selection (supports Anthropic, OpenAI, Google)
spring.ai.anthropic.api-key=${ANTHROPIC_API_KEY}
spring.ai.anthropic.options.model=claude-sonnet-4-5-20250929

# Web tools
brave.api.key=${BRAVE_API_KEY}
```

## Examples

Two comprehensive examples demonstrate different use cases:

- **[code-agent-demo](../examples/code-agent-demo)** - Full-featured AI coding assistant with interactive CLI, all 7 tools, conversation memory, and multi-model support
- **[skills-demo](../examples/skills-demo)** - Focused demonstration of the SkillsTool system with custom skill development and helper scripts

See the [Examples README](../examples/README.md) for detailed setup, configuration, and usage guide.

## Requirements

- Java 17+
- Spring Boot 3.x / 4.x
- Spring AI 2.0.0+

## License

Apache License 2.0

## Credits

Inspired by [Claude Code](https://code.claude.com) by Anthropic. Architecture insights from:
- [Claude Code Documentation](https://code.claude.com/docs/en/overview)
- [Claude Code Agent Skills](https://code.claude.com/docs/en/skills#agent-skills)
- [Claude Code Internals](https://agiflow.io/blog/claude-code-internals-reverse-engineering-prompt-augmentation/)
- [Claude Code Skills](https://mikhail.io/2025/10/claude-code-skills/)

## Links

- [GitHub Repository](https://github.com/spring-ai-community/spring-ai-agent-utils)
- [Issue Tracker](https://github.com/spring-ai-community/spring-ai-agent-utils/issues)
- [Spring AI Documentation](https://docs.spring.io/spring-ai/reference/)
