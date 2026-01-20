# Spring AI Agent Utils

<img style="display: block; margin: auto;" align="left" src="./docs/spring-ai-agent-utils-logo.png" width="200" />

A Spring AI library that brings Claude Code-inspired tools and skills to your AI agents.

[Spring AI](https://docs.spring.io/spring-ai/reference/2.0-SNAPSHOT/index.html) Agent Utils reimplements core [Claude Code](https://code.claude.com/docs/en/settings#tools-available-to-claude) capabilities as Spring AI tools, enabling sophisticated agentic workflows with file operations, shell execution, web access, task management, and extensible skills.

## Agentic Utils

These are the agent tools needed to implement any agentic behavior

#### Core Tools

- **[AgentEnvironment](docs/AgentEnvironment.md)** - Dynamic agent context utility that provides runtime environment information and git repository status to system prompts
- **[FileSystemTools](docs/FileSystemTools.md)** - Read, write, and edit files with precise control
- **[ShellTools](docs/ShellTools.md)** - Execute shell commands with timeout control, background process management, and regex output filtering
- **[GrepTool](docs/GrepTool.md)** - Pure Java grep implementation for code search with regex, glob filtering, and multiple output modes
- **[GlobTool](docs/GlobTool.md)** - Fast file pattern matching tool for finding files by name patterns with glob syntax
- **[SmartWebFetchTool](docs/SmartWebFetchTool.md)** - AI-powered web content summarization with caching
- **[BraveWebSearchTool](docs/BraveWebSearchTool.md)** - Web search with domain filtering

#### User feedback

- **[AskUserQuestionTool](docs/AskUserQuestionTool.md)** - Ask users clarifying questions with multiple-choice options during agent execution

#### Agent Skills

- **[SkillsTool](docs/SkillsTool.md)** - Extend AI agent capabilities with reusable, composable knowledge modules defined in Markdown with YAML front-matter

#### Task orchestration & multi-agent

- **[TodoWriteTool](docs/TodoWriteTool.md)** - Structured task management with state tracking
- **[TaskTools](docs/TaskTools.md)** - Hierarchical autonomous sub-agent system for delegating complex tasks to specialized agents with dedicated context windows

While these tools can be used standalone, truly agentic behavior emerges when they are combined. SkillsTool naturally pairs with FileSystemTools and ShellTools to execute domain-specific workflows. BraveWebSearchTool and SmartWebFetchTool provide your AI application with access to real-world information. TaskTools orchestrates complex operations by delegating to specialized sub-agents, each equipped with a tailored subset of these tools.


## Installation

**Maven:**
```xml
<dependency>
    <groupId>org.springaicommunity</groupId>
    <artifactId>spring-ai-agent-utils</artifactId>
    <version>0.4.0</version>
</dependency>
```

_Check the latest version:_ [![](https://img.shields.io/maven-central/v/org.springaicommunity/spring-ai-agent-utils.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/org.springaicommunity/spring-ai-agent-utils)

> **Note:** You need Sping-AI version `2.0.0-SNAPSHOT` or `2.0.0-M2` when released.


## Quick Start

```java
@SpringBootApplication
public class Application {

    @Bean
    CommandLineRunner demo(ChatClient.Builder chatClientBuilder, 
        @Value("${BRAVE_API_KEY}") String braveApiKey,
        @Value("classpath:/prompt/MAIN_AGENT_SYSTEM_PROMPT_V2.md") Resource agentSystemPrompt) {
        
        return args -> {
            ChatClient chatClient = chatClientBuilder
                // Main agent prompt
				.defaultSystem(p -> p.text(agentSystemPrompt) // system prompt
					.param(AgentEnvironment.ENVIRONMENT_INFO_KEY, AgentEnvironment.info())
					.param(AgentEnvironment.GIT_STATUS_KEY, AgentEnvironment.gitStatus())
					.param(AgentEnvironment.AGENT_MODEL_KEY, "claude-sonnet-4-5-20250929")
					.param(AgentEnvironment.AGENT_MODEL_KNOWLEDGE_CUTOFF_KEY, "2025-01-01"))
                

                // Sub-Agents (with multi-model support)
                .defaultToolCallbacks(TaskToolCallbackProvider.builder()
                    .chatClientBuilder("default", chatClientBuilder.clone())
                    .subagentReferences(ClaudeSubagentReferences.fromRootDirectory(".claude/agents"))
                    .skillsDirectories(".claude/skills")
                    .build())

                // Skills
                .defaultToolCallbacks(SkillsTool.builder()
                    .addSkillsDirectory(".claude/skills")
                    .build())

                // Core Tools
                .defaultTools(
                    ShellTools.builder().build(),
                    FileSystemTools.builder().build(),
                    GrepTool.builder().build(),
                    GlobTool.builder().build(),
                    SmartWebFetchTool.builder(chatClient).build(),
                    BraveWebSearchTool.builder(braveApiKey).build())

                // Task orchestration
                .defaultTools(TodoWriteTool.builder().build())

                // User feedback tool
                .defaultTools(AskUserQuestionTool.builder()
                    .questionHandler(questions -> handleUserQuestions(questions))
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

### Examples

Four examples demonstrate different use cases:

- **[code-agent-demo](../examples/code-agent-demo)** - Full-featured AI coding assistant with interactive CLI, all tools, conversation memory, and multi-model support
- **[skills-demo](../examples/skills-demo)** - Focused demonstration of the SkillsTool system with custom skill development and helper scripts
- **[subagent-demo](../examples/subagent-demo)** - Demonstrates hierarchical sub-agent system with custom Spring AI expert sub-agent and TaskTools integration
- **[ask-user-question-demo](../examples/ask-user-question-demo)** - Console-based chat application demonstrating the AskUserQuestionTool with single/multi-select questions and custom answer handling

See the [Examples README](../examples/README.md) for detailed setup, configuration, and usage guide.


## Agent Tool Details

### CORE TOOLS

### AgentEnvironment

Provide AI agents with runtime environment information and git repository context through dynamic system prompt parameters. Makes agents context-aware by injecting environment metadata and git status into system prompts.

[**View Full Documentation →**](docs/AgentEnvironment.md)

**Quick Example:**
```java
import org.springaicommunity.agent.utils.AgentEnvironment;

@Value("${agent.model:Unknown}")
String agentModel;

@Value("${agent.model.knowledge.cutoff:Unknown}")
String agentModelKnowledgeCutoff;

@Value("classpath:/prompt/MAIN_AGENT_SYSTEM_PROMPT_V2.md")
Resource systemPrompt;

// Configure ChatClient with dynamic environment context
ChatClient chatClient = chatClientBuilder
    .defaultSystem(p -> p.text(systemPrompt)
        .param(AgentEnvironment.ENVIRONMENT_INFO_KEY, AgentEnvironment.info())
        .param(AgentEnvironment.GIT_STATUS_KEY, AgentEnvironment.gitStatus())
        .param(AgentEnvironment.AGENT_MODEL_KEY, agentModel)
        .param(AgentEnvironment.AGENT_MODEL_KNOWLEDGE_CUTOFF_KEY, agentModelKnowledgeCutoff))
    .defaultTools(/* your tools */)
    .build();
```

**System Prompt Template:** `src/main/resources/prompt/MAIN_AGENT_SYSTEM_PROMPT_V2.md`
```markdown
Here is useful information about the environment you are running in:
<env>
{ENVIRONMENT_INFO}
</env>
You are powered by the model: {AGENT_MODEL}

Assistant knowledge cutoff is {AGENT_MODEL_KNOWLEDGE_CUTOFF}.

{GIT_STATUS}
```

**Application Properties:**
```properties
# AGENT CONFIGURATION
agent.model=claude-sonnet-4-5-20250929
agent.model.knowledge.cutoff=2025-09-29
```

**Benefits:**
- Agents understand their runtime environment (OS, working directory, date)
- Git-aware agents know current branch, uncommitted changes, recent commits
- Model-specific prompts with accurate knowledge cutoff dates
- Easy multi-model support through configuration

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
---

### USER FEEDBACK

### AskUserQuestionTool

Ask users clarifying questions during AI agent execution. Enables agents to gather user preferences, clarify ambiguous requirements, and get decisions on implementation choices with multiple-choice or free-text input.

[**View Full Documentation →**](docs/AskUserQuestionTool.md)

**Quick Example:**
```java
AskUserQuestionTool askTool = AskUserQuestionTool.builder()
    .questionHandler(questions -> {
        // Display questions to user via your UI
        Map<String, String> answers = collectUserAnswers(questions);
        return answers;
    })
    .build();

// AI agent will automatically call this tool when it needs clarification
// Example: "Which framework should we use?" with options like React, Vue, Angular
```

**Demo Application:**

See the [ask-user-question-demo](../examples/ask-user-question-demo) for a complete console-based implementation.

---

### AGENT SKILLS

### SkillsTool

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
---

### TASK ORCHESTRATION & SUB-AGENTS

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

### TaskTools - Extensible Sub-Agent System

Enable your AI agent to delegate complex, multi-step tasks to specialized sub-agents with dedicated context windows. Based on [Claude Code's sub-agents](https://code.claude.com/docs/en/sub-agents), this system provides autonomous task execution with specialized expertise and an extensible architecture supporting multiple sub-agent types.

[**View Full Documentation →**](docs/TaskTools.md)

**Quick Example:**
```java
import org.springaicommunity.agent.tools.task.subagent.claude.ClaudeSubagentReferences;

// Configure Task tools with multi-model support
var taskTools = TaskToolCallbackProvider.builder()
    .chatClientBuilder("default", chatClientBuilder)
    .chatClientBuilder("opus", opusChatClientBuilder)     // Optional: for complex tasks
    .chatClientBuilder("haiku", haikuChatClientBuilder)   // Optional: for quick tasks
    .subagentReferences(ClaudeSubagentReferences.fromRootDirectory(".claude/agents"))
    .skillsDirectories(".claude/skills")
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
disallowedTools: Edit, Write
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
- **Multi-Model Routing** - Route sub-agents to different models (sonnet, opus, haiku) based on task complexity
- **Extensible Architecture** - Pluggable resolvers and executors support Claude-based, A2A, or custom sub-agent types
- **Dedicated Context** - Each sub-agent has its own context window, preventing pollution of main conversation
- **Tool Filtering** - Allow or disallow specific tools per sub-agent with `tools` and `disallowedTools`
- **Background Execution** - Run long-running tasks asynchronously with TaskOutputTool
- **Resumable Agents** - Continue long-running research across multiple interactions


## Configuration

**application.properties:**
```properties
# Model selection (supports Anthropic, OpenAI, Google)

# Anthropic Claude
spring.ai.anthropic.api-key=${ANTHROPIC_API_KEY}
spring.ai.anthropic.options.model=claude-sonnet-4-5-20250929

# or OpenAI 
spring.ai.openai-sdk.api-key=${OPENAI_API_KEY}
spring.ai.openai-sdk.chat.options.model=gpt-5-mini-2025-08-07
spring.ai.openai-sdk.chat.options.temperature=1.0

# or Google Gemini
spring.ai.google.genai.project-id=${GOOGLE_CLOUD_PROJECT}
spring.ai.google.genai.chat.options.model=gemini-3-pro-preview
spring.ai.google.genai.location=global

# Web tools (used by the BraveWebSearchTool )
brave.api.key=${BRAVE_API_KEY}
```

## Requirements

- Java 17+
- Spring Boot 3.x / 4.x
- Spring AI 2.0.0-SNAPSHOT (> M1)

## License

Apache License 2.0

## Links

- [GitHub Repository](https://github.com/spring-ai-community/spring-ai-agent-utils)
- [Issue Tracker](https://github.com/spring-ai-community/spring-ai-agent-utils/issues)
- [Spring AI Documentation](https://docs.spring.io/spring-ai/reference/)
- Architecture insights:
    - [Claude Code Documentation](https://code.claude.com/docs/en/overview)
    - [Claude Code Agent Skills](https://code.claude.com/docs/en/skills#agent-skills)
    - [Claude Code Internals](https://agiflow.io/blog/claude-code-internals-reverse-engineering-prompt-augmentation/)
    - [Claude Code Skills](https://mikhail.io/2025/10/claude-code-skills/)
