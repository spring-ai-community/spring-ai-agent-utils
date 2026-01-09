# Spring AI Agent Utils

A Spring AI library that brings Claude Code-inspired tools and skills to your AI agents.

## Overview

Spring AI Agent Utils reimplements core [Claude Code](https://code.claude.com/docs/en/overview) capabilities as Spring AI tools, enabling sophisticated agentic workflows with file operations, shell execution, web access, task management, and extensible skills.

## Features

### Agentic Tools

- **[FileSystemTools](docs/FileSystemTools.md)** - Read, write, and edit files with precise control
- **[ShellTools](docs/ShellTools.md)** - Execute shell commands with timeout control, background process management, and regex output filtering
- **[GrepTool](docs/GrepTool.md)** - Pure Java grep implementation for code search with regex, glob filtering, and multiple output modes
- **[GlobTool](docs/GlobTool.md)** - Fast file pattern matching tool for finding files by name patterns with glob syntax
- **[TodoWriteTool](docs/TodoWriteTool.md)** - Structured task management with state tracking
- **[SmartWebFetchTool](docs/SmartWebFetchTool.md)** - AI-powered web content summarization with caching
- **[BraveWebSearchTool](docs/BraveWebSearchTool.md)** - Web search with domain filtering
- **[SkillsTool](docs/SkillsTool.md)** - Extend AI agent capabilities with reusable, composable knowledge modules defined in Markdown with YAML front-matter
- **[TaskTools](docs/TaskTools.md)** - Hierarchical autonomous sub-agent system for delegating complex tasks to specialized agents with dedicated context windows


## Installation

**Maven:**
```xml
<dependency>
    <groupId>org.springaicommunity</groupId>
    <artifactId>spring-ai-agent-utils</artifactId>
    <version>0.1.1</version>
</dependency>
```

_Check the latest version:_ [![](https://img.shields.io/maven-central/v/org.springaicommunity/spring-ai-agent-utils.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/org.springaicommunity/spring-ai-agent-utils)

> **Note:** You need Sping-AI version `2.0.0-SNAPSHOT` or `2.0.0-M2` when released.


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
                .defaultTools(GrepTool.builder().build())
                .defaultTools(GlobTool.builder().build())
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

## Tool Details

### FileSystemTools

Read, write, and edit files with precise control. Provides three core operations: Read for reading files with pagination, Write for creating/overwriting files, and Edit for precise string replacement with safety checks.

[**View Full Documentation →**](docs/FileSystemTools.md)

**Quick Example:**
```java
FileSystemTools fileTools = FileSystemTools.builder().build();

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
GrepTool grepTool = GrepTool.builder().build();

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
TodoWriteTool todoTool = TodoWriteTool.builder().build();

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
    .defaultTools(FileSystemTools.builder().build())  // For reading reference files
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

### TaskTools - Hierarchical Sub-Agent System

Enable your AI agent to delegate complex, multi-step tasks to specialized sub-agents with dedicated context windows. Based on [Claude Code's sub-agents](https://code.claude.com/docs/en/sub-agents), this system provides autonomous task execution with specialized expertise.

[**View Full Documentation →**](docs/TaskTools.md)

**Quick Example:**
```java
// Configure Task tools with built-in and custom sub-agents
var taskTools = TaskToolCallbackProvider.builder()
    .chatClientBuilder(chatClientBuilder)
    .agentDirectories(".claude/agents")  // Custom sub-agents
    .skillsDirectories(".claude/skills") // Skills for sub-agents
    .build();

// Build main chat client with Task tools
ChatClient chatClient = chatClientBuilder
    .defaultToolCallbacks(taskTools)
    .defaultTools(FileSystemTools.builder().build(), GrepTool.builder().build())
    .build();

// Agent automatically delegates to appropriate sub-agents
String response = chatClient
    .prompt("Explore the authentication module and explain how it works")
    .call()
    .content();
// Main agent recognizes exploration task and delegates to Explore sub-agent
```

**Built-in Sub-Agents:**
- **general-purpose** - Complex research and multi-step tasks with full tool access
- **Explore** - Fast, read-only codebase exploration with thoroughness levels (quick/medium/very thorough)

**Create Custom Sub-Agent:** `.claude/agents/code-reviewer.md`
```markdown
---
name: code-reviewer
description: Expert code reviewer. Use proactively after writing or modifying code.
tools: Read, Grep, Glob, Bash
model: sonnet
---

You are a senior code reviewer with expertise in code quality and security.

**Review Checklist:**
- Code clarity and readability
- Error handling and security
- Test coverage and performance

**Output Format:**
Organize feedback by priority: Critical Issues, Warnings, Suggestions.
```

**Key Features:**
- **Dedicated Context** - Each sub-agent has its own context window, preventing pollution of main conversation
- **Specialized Prompts** - Custom system prompts tailored for specific domains
- **Tool Filtering** - Limit sub-agents to only necessary tools
- **Background Execution** - Run long-running tasks asynchronously with TaskOutputTool
- **Resumable Agents** - Continue long-running research across multiple interactions

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

Three comprehensive examples demonstrate different use cases:

- **[code-agent-demo](../examples/code-agent-demo)** - Full-featured AI coding assistant with interactive CLI, all tools, conversation memory, and multi-model support
- **[skills-demo](../examples/skills-demo)** - Focused demonstration of the SkillsTool system with custom skill development and helper scripts
- **[subagent-demo](../examples/subagent-demo)** - Demonstrates hierarchical sub-agent system with custom Spring AI expert sub-agent and TaskTools integration

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
