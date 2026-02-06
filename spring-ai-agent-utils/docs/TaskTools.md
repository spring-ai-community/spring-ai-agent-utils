# Task Tools - Extensible Sub-Agent System

## Overview

The Task Tools provide an extensible hierarchical sub-agent system for Spring AI, inspired by [Claude Code's sub-agents](https://code.claude.com/docs/en/sub-agents). This architecture enables your main AI agent to delegate complex, multi-step tasks to specialized sub-agents while supporting multiple execution backends (Claude-based, A2A, custom implementations).

## Key Concepts

### What Are Sub-Agents?

Sub-agents are specialized AI assistants that your main agent can delegate tasks to. Each operates with:

- **Dedicated context window** - Separate from the main conversation, preventing context pollution
- **Custom system prompt** - Tailored instructions for specific domains (exploration, planning, custom expertise)
- **Configurable tool access** - Limited to only the necessary capabilities for their role
- **Independent execution** - Works autonomously and returns results to the parent agent

### Extensible Architecture

The system is built on pluggable abstractions that allow:

- **Multiple subagent types** - Claude-based, A2A protocol, or custom implementations
- **Pluggable resolvers** - Load subagent definitions from various sources (files, classpath, remote)
- **Pluggable executors** - Execute subagents using different backends (Spring AI ChatClient, A2A, etc.)
- **Multi-model support** - Different ChatClient configurations per model (sonnet, opus, haiku)

### Benefits

| Benefit | Details |
|---------|---------|
| **Context Preservation** | Keeps main conversation focused on high-level objectives while sub-agents handle details |
| **Specialized Expertise** | Fine-tuned for specific domains with higher success rates |
| **Extensibility** | Add new subagent types by implementing simple interfaces |
| **Multi-Model** | Route to different models based on subagent configuration |
| **Flexible Permissions** | Granular control over tool access per sub-agent |
| **Async Execution** | Run sub-agents in background for long-running tasks |

## Architecture

### Module Structure

The subagent system is split across three modules:

```
spring-ai-agent-utils-common      # Core SPI: SubagentDefinition, SubagentResolver,
                                   #   SubagentExecutor, SubagentType, SubagentReference, TaskCall

spring-ai-agent-utils              # TaskTool, TaskOutputTool, ClaudeSubagentType,
                                   #   ClaudeSubagentExecutor, ClaudeSubagentResolver

spring-ai-agent-utils-a2a          # A2ASubagentDefinition, A2ASubagentResolver,
                                   #   A2ASubagentExecutor (optional dependency)
```

### Core Components

```
TaskTool.builder()
    ├── SubagentReference[] ──────► (URIs pointing to subagent definitions)
    ├── SubagentType[] ───────────► (bundles resolver + executor per kind)
    │   └── SubagentType
    │       ├── SubagentResolver ─► (parse references into SubagentDefinition)
    │       └── SubagentExecutor ─► (execute subagents)
    └── ClaudeSubagentType.builder()
        ├── ClaudeSubagentResolver  (auto-registered)
        └── ClaudeSubagentExecutor
            └── Map<String, ChatClient.Builder> (model routing)
```

### Abstractions

These interfaces live in the `spring-ai-agent-utils-common` module and are shared across all subagent implementations.

| Interface | Purpose |
|-----------|---------|
| `SubagentDefinition` | Core interface representing a subagent with name, description, kind, and reference |
| `SubagentReference` | Record holding URI, kind, and metadata for locating a subagent definition |
| `SubagentResolver` | Strategy for resolving `SubagentReference` → `SubagentDefinition` |
| `SubagentExecutor` | Strategy for executing a `TaskCall` against a `SubagentDefinition` |
| `SubagentType` | Record bundling a `SubagentResolver` and `SubagentExecutor` together for a specific kind |
| `TaskCall` | Input record describing the task to execute (prompt, subagent type, model, etc.) |

### Main Components

1. **TaskTool** - Launches and manages sub-agents using pluggable resolvers and executors
2. **TaskOutputTool** - Retrieves results from background sub-agents
3. **ClaudeSubagentType** - Convenience builder for configuring the Claude subagent type with tools, skills, and model routing

## Built-in Sub-Agents

When a `ClaudeSubagentType` is registered, TaskTool automatically adds four built-in Claude subagents.

### General-Purpose Sub-Agent

A versatile agent for complex research and execution tasks:

```markdown
---
name: general-purpose
description: General-purpose agent for researching complex questions, searching for code, and executing multi-step tasks
---
```

**Capabilities:**
- Searching code and configurations across large codebases
- Analyzing multiple files to understand system architecture
- Performing multi-step research tasks
- Full read/write access to tools

### Explore Sub-Agent

A fast, read-only agent specialized for codebase exploration:

```markdown
---
name: Explore
description: Fast agent specialized for exploring codebases. Use for finding files, searching code, or answering questions about the codebase
---
```

**Capabilities:**
- Rapidly finding files using glob patterns
- Searching code with regex patterns
- Reading and analyzing file contents
- **Strictly read-only** - no file modifications

**Thoroughness Levels:**
- `quick` - Basic searches, minimal exploration
- `medium` - Moderate exploration, multiple search strategies
- `very thorough` - Comprehensive analysis across multiple locations

### Plan Sub-Agent

A software architect agent specialized for designing implementation plans:

```markdown
---
name: Plan
description: Software architect agent for designing implementation plans. Use this when you need to plan the implementation strategy for a task.
---
```

**Capabilities:**
- Exploring codebases to understand existing patterns and architecture
- Identifying critical files that need modification or creation
- Designing step-by-step implementation approaches
- Considering architectural trade-offs and presenting alternatives
- Surfacing potential risks or challenges early
- **Strictly read-only** - planning only, no file modifications

### Bash Sub-Agent

A command execution specialist for terminal operations:

```markdown
---
name: Bash
description: Command execution specialist for running bash commands. Use this for git operations, command execution, and other terminal tasks.
---
```

**Capabilities:**
- Git operations (commits, branches, merges, status checks)
- Build and test commands (npm, cargo, make, pytest)
- Package management operations
- System operations and environment setup
- DevOps tasks (Docker, deployment scripts)

**Guidelines:**
- Follows git safety protocols (no force push to main, no destructive commands without confirmation)
- Limited to Bash tool only - file reading/editing handled by parent agent

## Quick Start

### Basic Setup (Claude subagents only)

```java
import org.springaicommunity.agent.tools.task.TaskTool;
import org.springaicommunity.agent.tools.task.claude.ClaudeSubagentType;

@Configuration
public class AgentConfig {

    @Bean
    CommandLineRunner demo(ChatClient.Builder chatClientBuilder) {
        return args -> {
            // Configure Task tool with Claude subagents
            var taskTool = TaskTool.builder()
                .subagentTypes(ClaudeSubagentType.builder()
                    .chatClientBuilder("default", chatClientBuilder)
                    .build())
                .build();

            // Build main chat client with Task tool
            ChatClient chatClient = chatClientBuilder
                .defaultToolCallbacks(taskTool)
                .build();

            // Use naturally - agent will delegate to sub-agents
            String response = chatClient
                .prompt("Explore the authentication module and explain how it works")
                .call()
                .content();
        };
    }
}
```

## Configuration

### Using ClaudeSubagentType

`ClaudeSubagentType` is a convenience builder that creates a `SubagentType` bundling the Claude resolver and executor with default tools (Grep, Glob, Shell, FileSystem, WebFetch, TodoWrite) and optional extras (BraveWebSearch, Skills):

```java
SubagentType claudeType = ClaudeSubagentType.builder()
    // Required: At least one ChatClient builder with key "default"
    .chatClientBuilder("default", chatClientBuilder)

    // Optional: Additional model-specific ChatClient builders
    .chatClientBuilder("opus", opusChatClientBuilder)
    .chatClientBuilder("haiku", haikuChatClientBuilder)

    // Optional: Skills for sub-agents (preloaded into system prompt)
    .skillsResources(skillResources)

    // Optional: For web search
    .braveApiKey(System.getenv("BRAVE_API_KEY"))

    .build();
```

### Using TaskTool.builder()

Register one or more `SubagentType` instances, plus any additional subagent references:

```java
ToolCallback taskTool = TaskTool.builder()
    // Register Claude subagent type (includes built-in subagents)
    .subagentTypes(claudeType)

    // Optional: Custom subagent references (for Claude-based subagents)
    .subagentReferences(ClaudeSubagentReferences.fromRootDirectory("/path/to/agents"))

    // Optional: Custom task storage
    .taskRepository(new DefaultTaskRepository())

    .build();
```

### Combining Claude and A2A Subagents

```java
import org.springaicommunity.agent.common.task.subagent.SubagentReference;
import org.springaicommunity.agent.common.task.subagent.SubagentType;
import org.springaicommunity.agent.subagent.a2a.A2ASubagentDefinition;
import org.springaicommunity.agent.subagent.a2a.A2ASubagentExecutor;
import org.springaicommunity.agent.subagent.a2a.A2ASubagentResolver;

ToolCallback taskTool = TaskTool.builder()
    // Local Claude subagents
    .subagentTypes(ClaudeSubagentType.builder()
        .chatClientBuilder("default", chatClientBuilder)
        .skillsResources(skillPaths)
        .braveApiKey(braveApiKey)
        .build())

    // Remote A2A subagent
    .subagentReferences(new SubagentReference("http://localhost:10001/myagent", A2ASubagentDefinition.KIND))
    .subagentTypes(new SubagentType(new A2ASubagentResolver(), new A2ASubagentExecutor()))

    .build();
```

### Multi-Provider and Multi-Model Configuration

Route subagents to different LLM providers and models based on their frontmatter `model` field. The `chatClientBuilder` keys represent named providers — these can be any Spring AI supported LLM provider (Anthropic, OpenAI, Ollama, etc.), not just Claude variants:

```java
SubagentType claudeType = ClaudeSubagentType.builder()
    .chatClientBuilder("default", anthropicBuilder)  // Fallback provider
    .chatClientBuilder("openai", openAiBuilder)      // OpenAI provider
    .chatClientBuilder("ollama", ollamaBuilder)      // Ollama provider
    .build();
```

The `model` frontmatter field supports two formats:

- **`model`** — Uses the default provider with the specified model. Short aliases (`opus`, `haiku`, `sonnet`) are mapped to their full Claude model identifiers. The model can also be the full model name of any provider (e.g., `gpt-4o`).
- **`provider:model`** — Uses the named provider with the specified model (e.g., `openai:gpt-4o`, `ollama:llama3`).

If the specified provider is not found in the builder map, or if no model is specified, the default builder is used as a fallback.

### Loading Subagent References

#### From Directories

```java
import org.springaicommunity.agent.tools.task.claude.ClaudeSubagentReferences;

List<SubagentReference> refs = ClaudeSubagentReferences.fromRootDirectory("/path/to/agents");
```

#### From Spring Resources

```java
@Value("${agent.tasks.paths}")
List<Resource> agentPaths;

List<SubagentReference> refs = ClaudeSubagentReferences.fromResources(agentPaths);
```

#### Multiple Sources

```java
List<SubagentReference> refs = ClaudeSubagentReferences.fromRootDirectories(List.of(
    "src/main/resources/agents",
    System.getProperty("user.home") + "/.claude/agents"
));
```

## Creating Custom Sub-Agents

### File Structure

Custom sub-agents are defined as Markdown files with YAML front matter:

```
project-root/
├── .claude/
│   └── agents/
│       ├── code-reviewer.md
│       ├── test-runner.md
│       └── spring-ai-expert.md
```

### Sub-Agent File Format

```markdown
---
name: your-sub-agent-name
description: When and how to use this subagent. Include trigger keywords and example scenarios.
tools: Read, Edit, Grep, Glob       # Optional: inherits all if omitted
disallowedTools: Bash, Shell        # Optional: explicitly deny specific tools
model: sonnet                        # Optional: sonnet, opus, haiku
skills: ai-tutor                     # Optional: skills to preload
permissionMode: default              # Optional: permission handling mode
---

# Your Sub-Agent's System Prompt

You are a [role description]. You specialize in [domain].

**Your Primary Responsibilities:**
1. [Responsibility 1]
2. [Responsibility 2]

**Guidelines:**
- [Guideline 1]
- [Guideline 2]
```

### Configuration Fields

| Field | Required | Description |
|-------|----------|-------------|
| `name` | Yes | Unique identifier (lowercase with hyphens) |
| `description` | Yes | Natural language purpose description with usage examples |
| `tools` | No | Comma-separated list of allowed tool names (inherits all if omitted) |
| `disallowedTools` | No | Comma-separated list of tools to explicitly deny |
| `model` | No | Model specification: a short alias (`sonnet`, `opus`, `haiku`), a full model name (`gpt-4o`), or `provider:model` (`openai:gpt-4o`) |
| `skills` | No | Comma-separated list of skill names to preload into the subagent's system prompt |
| `permissionMode` | No | Permission handling mode (default: `default`) |

### Example: Code Reviewer Sub-Agent

```markdown
---
name: code-reviewer
description: Expert code reviewer. Use proactively after writing or modifying code. Focuses on code quality, security, and best practices.
tools: Read, Grep, Glob, Bash
disallowedTools: Edit, Write
model: sonnet
---

You are a senior code reviewer with expertise in software quality and security.

**When Invoked:**
1. Run `git diff` to see recent changes
2. Focus analysis on modified files
3. Check context of surrounding code

**Review Checklist:**
- Code clarity and readability
- Proper naming conventions
- No code duplication
- Comprehensive error handling
- Security (no exposed secrets, SQL injection, XSS)

**Output Format:**
Provide clear, actionable feedback with file references and line numbers.
```

## Extending the System

### Creating a Custom Subagent Type

To add support for a new subagent protocol, implement three interfaces from `spring-ai-agent-utils-common` and register them as a `SubagentType`. See the [Subagent Framework](Subagent.md) documentation for the full SPI reference, or the [spring-ai-agent-utils-a2a](../../spring-ai-agent-utils-a2a/README.md) module for a complete A2A protocol implementation.

```java
// 1. Implement SubagentDefinition, SubagentResolver, SubagentExecutor
// 2. Bundle them into a SubagentType
SubagentType myType = new SubagentType(new MyResolver(), new MyExecutor());

// 3. Register with TaskTool
TaskTool.builder()
    .subagentReferences(new SubagentReference("my://agent-1", "MY_KIND"))
    .subagentTypes(myType)
    .build();
```

## Usage Patterns

### Automatic Delegation

The main agent automatically delegates to sub-agents based on their descriptions:

```java
String response = chatClient
    .prompt("How does authentication work in this codebase?")
    .call()
    .content();
// Main agent recognizes this as exploration task and delegates to Explore sub-agent
```

### Explicit Invocation

Users can explicitly request specific sub-agents:

```java
String response = chatClient
    .prompt("Use the code-reviewer subagent to review my recent changes")
    .call()
    .content();
```

### Background Execution

Long-running tasks can execute in the background:

```java
// Main agent can launch background sub-agents
String response = chatClient
    .prompt("Run the test-runner in the background and let me know when tests complete")
    .call()
    .content();
// Returns task_id for later retrieval

// Later, retrieve results via TaskOutputTool
String results = chatClient
    .prompt("Get the results from task task_12345")
    .call()
    .content();
```

### Tool Parameters

When the main agent calls TaskTool, it uses these parameters (defined in `TaskCall`):

```java
public record TaskCall(
    String description,        // Short 3-5 word description
    String prompt,            // The task for the sub-agent
    String subagent_type,     // Which sub-agent to use
    String model,             // Optional: override model
    String resume,            // Optional: resume previous sub-agent
    Boolean run_in_background // Optional: run async
) {}
```

## Background Task Management

### TaskRepository

The `TaskRepository` manages background task execution:

```java
public interface TaskRepository {
    BackgroundTask putTask(String taskId, Supplier<String> taskExecution);
    BackgroundTask getTasks(String taskId);
}
```

### Default Implementation

```java
import org.springaicommunity.agent.tools.task.repository.DefaultTaskRepository;

TaskRepository repository = new DefaultTaskRepository();
```

`TaskTool.builder()` uses `DefaultTaskRepository` by default. Override with `.taskRepository(...)` for custom implementations.

### Custom Implementation

For distributed systems or persistence:

```java
@Component
public class RedisTaskRepository implements TaskRepository {

    @Override
    public BackgroundTask putTask(String taskId, Supplier<String> taskExecution) {
        // Store in Redis, execute via message queue, etc.
    }

    @Override
    public BackgroundTask getTasks(String taskId) {
        return redisTemplate.opsForValue().get(taskId);
    }
}
```

## Complete Example

```java
@SpringBootApplication
public class Application {

    @Bean
    CommandLineRunner demo(
            ChatClient.Builder chatClientBuilder,
            @Value("${agent.skills.paths}") List<Resource> skillPaths,
            @Value("${BRAVE_API_KEY:#{null}}") String braveApiKey) {

        return args -> {
            // Configure Task tool with Claude subagents
            var taskTool = TaskTool.builder()
                .subagentTypes(ClaudeSubagentType.builder()
                    .chatClientBuilder("default",
                        chatClientBuilder.clone().defaultAdvisors(new MyLoggingAdvisor(0, "[TASK]")))
                    .skillsResources(skillPaths)
                    .braveApiKey(braveApiKey)
                    .build())
                .build();

            // Build main chat client
            ChatClient chatClient = chatClientBuilder
                .defaultToolCallbacks(taskTool)
                .defaultTools(
                    FileSystemTools.builder().build(),
                    GrepTool.builder().build(),
                    GlobTool.builder().build(),
                    ShellTools.builder().build(),
                    TodoWriteTool.builder().build()
                )
                .defaultAdvisors(
                    ToolCallAdvisor.builder()
                        .conversationHistoryEnabled(false)
                        .build(),
                    MessageChatMemoryAdvisor.builder(
                        MessageWindowChatMemory.builder().maxMessages(500).build()
                    ).build()
                )
                .build();

            // Interactive chat loop
            try (Scanner scanner = new Scanner(System.in)) {
                while (true) {
                    System.out.print("\nUSER: ");
                    String response = chatClient
                        .prompt(scanner.nextLine())
                        .call()
                        .content();
                    System.out.println("\nASSISTANT: " + response);
                }
            }
        };
    }
}
```

## Best Practices

### 1. Design Focused Sub-Agents

Each sub-agent should have a single, clear responsibility.

### 2. Use `disallowedTools` for Safety

Explicitly deny dangerous tools for read-only agents:

```markdown
---
name: explorer
tools: Read, Grep, Glob
disallowedTools: Edit, Write, Bash, Shell
---
```

### 3. Leverage Multi-Provider and Multi-Model Routing

Use faster/cheaper models for simple tasks:

```markdown
---
name: quick-search
model: haiku
---
```

Use more capable models for complex analysis:

```markdown
---
name: deep-analyzer
model: opus
---
```

Route to a different provider entirely:

```markdown
---
name: local-searcher
model: ollama:llama3
---
```

### 4. Register Custom Subagent Types

Extend the system for new protocols like A2A rather than modifying core code.

### 5. Version Control Custom Agents

```bash
git add .claude/agents/
git commit -m "Add code-reviewer and test-runner sub-agents"
```

## API Reference

### TaskTool

```java
public class TaskTool {

    public static Builder builder() { ... }

    public static class Builder {
        Builder subagentReferences(List<SubagentReference> refs);
        Builder subagentReferences(SubagentReference... refs);
        Builder subagentTypes(List<SubagentType> types);
        Builder subagentTypes(SubagentType... types);
        Builder taskRepository(TaskRepository taskRepository);
        Builder taskDescriptionTemplate(String template);

        ToolCallback build();
    }
}
```

### ClaudeSubagentType

```java
public class ClaudeSubagentType {

    public static Builder builder() { ... }

    public static class Builder {
        Builder chatClientBuilder(String modelId, ChatClient.Builder builder);
        Builder chatClientBuilders(Map<String, ChatClient.Builder> builders);
        Builder skillsResources(List<Resource> resources);
        Builder skillsResource(Resource resource);
        Builder skillsDirectories(List<String> dirs);
        Builder skillsDirectories(String dir);
        Builder braveApiKey(String apiKey);

        SubagentType build();  // Returns SubagentType (resolver + executor pair)
    }
}
```

### Subagent Interfaces (from spring-ai-agent-utils-common)

```java
public interface SubagentDefinition {
    String getName();
    String getDescription();
    String getKind();
    SubagentReference getReference();
    default String toSubagentRegistrations() { ... }
}

public interface SubagentResolver {
    boolean canResolve(SubagentReference ref);
    SubagentDefinition resolve(SubagentReference ref);
}

public interface SubagentExecutor {
    String getKind();
    String execute(TaskCall taskCall, SubagentDefinition subagent);
}

public record SubagentReference(String uri, String kind, Map<String, String> metadata) {
    public SubagentReference(String uri, String kind) { this(uri, kind, Map.of()); }
}

public record SubagentType(SubagentResolver resolver, SubagentExecutor executor) {
    public String kind() { return executor.getKind(); }
}

public record TaskCall(
    String description, String prompt, String subagent_type,
    String model, String resume, Boolean run_in_background
) {}
```

### ClaudeSubagentDefinition

The built-in Claude subagent definition uses the kind constant `"CLAUDE"`:

```java
public class ClaudeSubagentDefinition implements SubagentDefinition {
    public static final String KIND = "CLAUDE";

    // Additional Claude-specific methods:
    public String getModel();           // Model override (sonnet, opus, haiku)
    public List<String> tools();        // Allowed tool names
    public List<String> disallowedTools(); // Tools to deny
    public List<String> skills();       // Skills to preload
    public String permissionMode();     // Permission handling mode
    public String getContent();         // System prompt content
}
```

### ClaudeSubagentReferences

```java
public class ClaudeSubagentReferences {
    static List<SubagentReference> fromRootDirectory(String rootDirectory);
    static List<SubagentReference> fromRootDirectories(List<String> rootDirectories);
    static List<SubagentReference> fromResource(Resource resource);
    static List<SubagentReference> fromResources(List<Resource> resources);
    static List<SubagentReference> fromResources(Resource... resources);
}
```

## Related Documentation

- [**Subagent Framework**](Subagent.md) - Protocol-agnostic subagent SPI for integrating A2A, MCP, and custom agent protocols
- [**spring-ai-agent-utils-common**](../../spring-ai-agent-utils-common/README.md) - Shared subagent SPI module
- [**spring-ai-agent-utils-a2a**](../../spring-ai-agent-utils-a2a/README.md) - A2A protocol subagent implementation
- [**FileSystemTools**](FileSystemTools.md) - File operations for sub-agents
- [**GrepTool**](GrepTool.md) - Code search capabilities
- [**GlobTool**](GlobTool.md) - File pattern matching
- [**ShellTools**](ShellTools.md) - Command execution
- [**SkillsTool**](SkillsTool.md) - Reusable knowledge modules for sub-agents

## References

- [Claude Code Sub-Agents Documentation](https://code.claude.com/docs/en/sub-agents)
- [Claude Agent SDK Sub-Agents](https://platform.claude.com/docs/en/agent-sdk/subagents)
- [Spring AI Documentation](https://docs.spring.io/spring-ai/reference/)
- [Example: subagent-demo](../../examples/subagent-demo)
- [Example: subagent-a2a-demo](../../examples/subagent-a2a-demo)
