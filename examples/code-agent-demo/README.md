# Code Agent Demo

A comprehensive demonstration of building an AI-powered coding assistant using Spring AI and the [spring-ai-agent-utils](../../spring-ai-agent-utils) library. This example showcases how to create a Claude Code-inspired agentic system with file operations, shell execution, web access, and extensible skills.

## Overview

This demo implements an interactive command-line AI assistant capable of:

- **Code Navigation**: Search and read files using grep and file system operations
- **Shell Execution**: Run commands, manage background processes
- **Web Research**: Search the web and fetch/summarize content intelligently
- **Task Management**: Track multi-step operations with structured todo lists
- **Extensible Skills**: Dynamically load custom capabilities from Markdown files
- **Multi-Model Support**: Switch between Anthropic Claude, OpenAI GPT, and Google Gemini

## Features

### Core Capabilities

This demo integrates the full suite of spring-ai-agent-utils tools:

1. **[SkillsTool](../../spring-ai-agent-utils/docs/SkillsTool.md)** - Extensible skill system for custom capabilities
2. **[ShellTools](../../spring-ai-agent-utils/docs/ShellTools.md)** - Execute shell commands (sync/async)
3. **[FileSystemTools](../../spring-ai-agent-utils/docs/FileSystemTools.md)** - Read, write, and edit files
4. **[GrepTool](../../spring-ai-agent-utils/docs/GrepTool.md)** - Search code with regex patterns
5. **[SmartWebFetchTool](../../spring-ai-agent-utils/docs/SmartWebFetchTool.md)** - AI-powered web content fetching
6. **[BraveWebSearchTool](../../spring-ai-agent-utils/docs/BraveWebSearchTool.md)** - Web search integration
7. **[TodoWriteTool](../../spring-ai-agent-utils/docs/TodoWriteTool.md)** - Task planning and tracking

### Advanced Features

- **Conversation Memory**: 500-message window for context retention
- **Custom System Prompt**: Configured for professional coding assistance
- **Advisor Pipeline**: Tool calling, memory management, and custom logging
- **Multi-Provider Support**: Easy switching between AI model providers

## Prerequisites

- Java 17 or higher
- Maven 3.6+
- API keys for desired AI providers:
  - Google Gemini: `GOOGLE_CLOUD_PROJECT` (default provider)
  - Anthropic Claude: `ANTHROPIC_API_KEY`
  - OpenAI GPT: `OPENAI_API_KEY`
  - Brave Search: `BRAVE_API_KEY` (for web search)

## Quick Start

### 1. Set Environment Variables

```bash
# Google Gemini (currently active)
export GOOGLE_CLOUD_PROJECT=your-project-id

# Optional: For web search
export BRAVE_API_KEY=your-brave-api-key

# Alternative providers (uncomment dependencies in pom.xml)
# export ANTHROPIC_API_KEY=your-anthropic-key
# export OPENAI_API_KEY=your-openai-key
```

### 2. Build and Run

```bash
# From the code-agent-demo directory
mvn clean install

# Run the application
mvn spring-boot:run
```

### 3. Interact with the Agent

```
I am your assistant.

USER: Search for TODO comments in this project

ASSISTANT: Let me search for TODO comments using the grep tool...
[Agent uses GrepTool to find TODOs]

USER: Create a summary of the Application.java file

ASSISTANT: I'll read the file and create a summary...
[Agent uses FileSystemTools to read and analyze]
```

## Configuration

### Application Properties

Configure the AI provider and skills location in [application.properties](src/main/resources/application.properties):

```properties
# Application settings
spring.main.web-application-type=none
spring.application.name=code-agent-demo

# Skills configuration
app.agent.skills.paths=/Users/christiantzolov/Dev/projects/spring-ai-agent-utils/.claude/skills

# AI Model (Google Gemini - active)
spring.ai.google.genai.api-key=${GOOGLE_CLOUD_API_KEY}
spring.ai.google.genai.location=global
spring.ai.google.genai.project-id=${GOOGLE_CLOUD_PROJECT}
spring.ai.google.genai.options.model=gemini-3-pro-preview

# Alternative providers (commented out)
# Anthropic Claude
# spring.ai.anthropic.api-key=${ANTHROPIC_API_KEY}
# spring.ai.anthropic.options.model=claude-sonnet-4-5-20250929

# OpenAI GPT
# spring.ai.openai.api-key=${OPENAI_API_KEY}
# spring.ai.openai.options.model=gpt-5-mini-2025-08-07
# spring.ai.openai.options.temperature=1.0
```

### Switching AI Providers

To use a different AI model:

1. **Comment out** the current provider dependency in [pom.xml](pom.xml)
2. **Uncomment** your desired provider dependency
3. **Update** application.properties with the corresponding configuration
4. **Rebuild** the project

Example for Anthropic Claude:

```xml
<!-- In pom.xml -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-anthropic</artifactId>
</dependency>
```

```properties
# In application.properties
spring.ai.anthropic.api-key=${ANTHROPIC_API_KEY}
spring.ai.anthropic.options.model=claude-sonnet-4-5-20250929
```

### Skills Directory

Skills are loaded from the path specified in `app.agent.skills.paths`. Create custom skills by adding Markdown files with YAML frontmatter:

```markdown
---
name: my-skill
description: Description of what the skill does and when to use it
allowed-tools: Read, Bash, Grep
model: claude-sonnet-4-5-20250929
---

# Your skill prompt content here
```

See the [SkillsTool documentation](../../spring-ai-agent-utils/docs/SkillsTool.md) for details.

## Architecture

### ChatClient Configuration

The demo builds a fully-configured ChatClient with:

```java
ChatClient chatClient = chatClientBuilder
    .defaultSystem(systemPrompt)                    // Custom system prompt
    .defaultToolCallbacks(skillsTool)               // Skills tool (ToolCallback)
    .defaultTools(                                  // Standard tools
        new ShellTools(),
        FileSystemTools.builder().build(),
        smartWebFetchTool,
        braveWebSearchTool,
        TodoWriteTool.builder().build(),
        GrepTool.builder().build())
    .defaultAdvisors(                               // Advisor chain
        ToolCallAdvisor.builder()
            .conversationHistoryEnabled(false)
            .build(),
        MessageChatMemoryAdvisor.builder(chatMemory)
            .order(Ordered.HIGHEST_PRECEDENCE + 1000)
            .build(),
        new MyLoggingAdvisor())
    .build();
```

### System Prompt

The agent uses a comprehensive system prompt ([CODE_AGENT_PROMPT_V2.md](../../spring-ai-agent-utils/src/main/resources/prompt/CODE_AGENT_PROMPT_V2.md)) that configures:

- Professional, concise communication style
- Security-aware coding practices (OWASP Top 10)
- Task management best practices
- Tool usage guidelines
- Git workflow patterns

### Custom Advisors

**MyLoggingAdvisor** - Custom advisor for debugging that logs:
- User messages and system context
- Available tools before each request
- Assistant responses with tool calls

## Example Use Cases

### Code Exploration

```
USER: Find all Java classes that extend SpringBootApplication

[Agent uses GrepTool to search for patterns]
```

### Running Tests

```
USER: Run the Maven tests and show me the results

[Agent uses ShellTools to execute 'mvn test']
```

### Research and Documentation

```
USER: Search for best practices on Spring AI advisors and summarize

[Agent uses BraveWebSearchTool and SmartWebFetchTool]
```

### Multi-Step Tasks

```
USER: Create a new utility class for date formatting, write tests, and run them

[Agent uses TodoWriteTool to plan, FileSystemTools to create files,
 ShellTools to run tests]
```

## Project Structure

```
code-agent-demo/
├── src/main/java/org/springaicommunity/agent/
│   ├── Application.java           # Main application with ChatClient setup
│   └── MyLoggingAdvisor.java      # Custom logging advisor
├── src/main/resources/
│   ├── application.properties     # Configuration
│   └── mcp-servers-config.json   # MCP server configuration
├── pom.xml                        # Maven dependencies
└── README.md                      # This file
```

## Dependencies

Key dependencies from [pom.xml](pom.xml):

- **Spring Boot 4.0.0** - Framework foundation
- **Spring AI 2.0.0-SNAPSHOT** - AI integration
- **spring-ai-agent-utils 2.0.0-SNAPSHOT** - Agent tools library
- **spring-ai-starter-model-google-genai** - Google Gemini provider (active)
- **netty-resolver-dns-native-macos** - macOS networking support

Alternative providers (commented out):
- spring-ai-starter-model-anthropic
- spring-ai-starter-model-openai-sdk
- spring-ai-starter-mcp-client-webflux

## How It Works

1. **Initialization**: Application starts and builds the ChatClient with all tools and advisors
2. **Chat Loop**: User enters prompts via console
3. **Agent Processing**:
   - System prompt guides the agent's behavior
   - Agent analyzes the request and determines which tools to use
   - Tools are invoked with appropriate parameters
   - Conversation memory maintains context
4. **Response**: Agent synthesizes tool results into a coherent response

### Tool Flow Example

```
User: "Find all TODO comments in Java files"
  ↓
Agent decides to use GrepTool
  ↓
GrepTool.execute(pattern="TODO", glob="**/*.java")
  ↓
Agent receives results and formats response
  ↓
User sees: "I found 5 TODO comments: ..."
```

## Advanced Configuration

### Adjusting Memory Window

Change the message history size:

```java
MessageWindowChatMemory chatMemory = MessageWindowChatMemory.builder()
    .maxMessages(1000)  // Increase from default 500
    .build();
```

### Customizing Tool Behavior

Configure individual tools:

```java
SmartWebFetchTool.builder(chatClient)
    .maxContentLength(150_000)              // Increase content limit
    .domainSafetyCheck(true)                // Enable safety checking
    .maxRetries(3)                          // More retry attempts
    .build()

BraveWebSearchTool.builder(braveApiKey)
    .resultCount(20)                        // More search results
    .build()
```

### Adding More Skills

1. Create skill files in your skills directory
2. Update `app.agent.skills.paths` if using a different location
3. Skills are automatically discovered on startup

## Troubleshooting

### Agent doesn't respond

- Check that your API key is set correctly
- Verify the model name matches your provider's offering
- Check logs from MyLoggingAdvisor for errors

### "API key not found" error

```bash
# Make sure environment variables are exported
echo $GOOGLE_CLOUD_PROJECT
echo $BRAVE_API_KEY
```

### Tools not working

- Verify tool permissions (file system access, shell execution)
- Check that skills directory path is correct and accessible
- Review logs for tool invocation errors

### Out of memory

```bash
# Increase Java heap size
export MAVEN_OPTS="-Xmx2g"
mvn spring-boot:run
```

## Learn More

- [Spring AI Agent Utils Documentation](../../spring-ai-agent-utils/README.md)
- [Individual Tool Documentation](../../spring-ai-agent-utils/docs/)
- [Spring AI Documentation](https://docs.spring.io/spring-ai/reference/)

## Contributing

This is a demonstration project. For contributing to the core library, see the main [spring-ai-agent-utils](../../spring-ai-agent-utils) project.

## License

Apache License 2.0 - See the project root for details.

## Related Examples

Explore other examples in this repository:
- Additional examples may be available in the [examples](../) directory

## Support

For issues or questions:
- Open an issue in the spring-ai-agent-utils repository
- Check the [tool documentation](../../spring-ai-agent-utils/docs/) for detailed usage
- Review the code-agent prompt for agent behavior guidelines
