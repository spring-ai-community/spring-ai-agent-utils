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

### Core Components

```
TaskToolCallbackProvider
    ├── SubagentReference[] ──────► (URIs pointing to subagent definitions)
    ├── SubagentType[] ───────────► (bundles resolver + executor per kind)
    │   └── SubagentType
    │       ├── SubagentResolver ─► (parse references into SubagentDefinition)
    │       └── SubagentExecutor ─► (execute subagents)
    └── Built-in Claude support
        ├── ClaudeSubagentResolver
        └── ClaudeSubagentExecutor
            └── Map<String, ChatClient.Builder> (model routing)
```

### Abstractions

| Interface | Purpose |
|-----------|---------|
| `SubagentDefinition` | Core interface representing a subagent with name, description, kind, and reference |
| `SubagentReference` | Record holding URI, kind, and metadata for locating a subagent definition |
| `SubagentResolver` | Strategy for resolving `SubagentReference` → `SubagentDefinition` |
| `SubagentExecutor` | Strategy for executing a `TaskCall` against a `SubagentDefinition` |
| `SubagentType` | Record bundling a `SubagentResolver` and `SubagentExecutor` together for a specific kind |

### Main Components

1. **TaskTool** - Launches and manages sub-agents using pluggable resolvers and executors
2. **TaskOutputTool** - Retrieves results from background sub-agents
3. **TaskToolCallbackProvider** - Convenience builder for configuring the complete system

## Built-in Sub-Agents

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

## Quick Start

### Basic Setup

```java
import org.springaicommunity.agent.tools.task.TaskToolCallbackProvider;
import org.springframework.ai.chat.client.ChatClient;

@Configuration
public class AgentConfig {

    @Bean
    CommandLineRunner demo(ChatClient.Builder chatClientBuilder) {
        return args -> {
            // Configure Task tools with multi-model support
            var taskTools = TaskToolCallbackProvider.builder()
                .chatClientBuilder("default", chatClientBuilder)
                .build();

            // Build main chat client with Task tools
            ChatClient chatClient = chatClientBuilder
                .defaultToolCallbacks(taskTools)
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

### Using TaskToolCallbackProvider (Recommended)

The `TaskToolCallbackProvider` is a convenience builder that sets up both TaskTool and TaskOutputTool with sensible defaults:

```java
TaskToolCallbackProvider taskTools = TaskToolCallbackProvider.builder()
    // Required: At least one ChatClient builder with key "default"
    .chatClientBuilder("default", chatClientBuilder)

    // Optional: Additional model-specific ChatClient builders
    .chatClientBuilder("sonnet", sonnetChatClientBuilder)
    .chatClientBuilder("opus", opusChatClientBuilder)
    .chatClientBuilder("haiku", haikuChatClientBuilder)

    // Optional: Custom subagent references (for Claude-based subagents)
    .subagentReferences(ClaudeSubagentReferences.fromRootDirectory("/path/to/agents"))

    // Optional: Custom subagent types (bundles resolver + executor for new kinds)
    .subagentTypes(new SubagentType(myResolver, myExecutor))

    // Optional: Skills for sub-agents
    .skillsDirectories(".claude/skills")

    // Optional: For web search
    .braveApiKey(System.getenv("BRAVE_API_KEY"))

    // Optional: Custom task storage
    .taskRepository(new DefaultTaskRepository())

    .build();
```

### Multi-Model Configuration

Route subagents to different models based on their configuration:

```java
// Create model-specific ChatClient builders
ChatClient.Builder sonnetBuilder = ChatClient.builder(sonnetModel);
ChatClient.Builder opusBuilder = ChatClient.builder(opusModel);
ChatClient.Builder haikuBuilder = ChatClient.builder(haikuModel);

TaskToolCallbackProvider taskTools = TaskToolCallbackProvider.builder()
    .chatClientBuilder("default", sonnetBuilder)  // Fallback
    .chatClientBuilder("sonnet", sonnetBuilder)
    .chatClientBuilder("opus", opusBuilder)
    .chatClientBuilder("haiku", haikuBuilder)
    .build();
```

When a subagent specifies `model: opus` in its frontmatter, it will use the corresponding ChatClient builder.

### Loading Subagent References

#### From Directories

```java
import org.springaicommunity.agent.tools.task.subagent.claude.ClaudeSubagentReferences;

List<SubagentReference> refs = ClaudeSubagentReferences.fromRootDirectory("/path/to/agents");

TaskToolCallbackProvider taskTools = TaskToolCallbackProvider.builder()
    .chatClientBuilder("default", chatClientBuilder)
    .subagentReferences(refs)
    .build();
```

#### From Spring Resources

```java
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

@Autowired
private ResourceLoader resourceLoader;

@Bean
public TaskToolCallbackProvider taskTools(ChatClient.Builder chatClientBuilder) {
    Resource agentsResource = resourceLoader.getResource("classpath:.claude/agents");

    List<SubagentReference> refs = ClaudeSubagentReferences.fromResource(agentsResource);

    return TaskToolCallbackProvider.builder()
        .chatClientBuilder("default", chatClientBuilder)
        .subagentReferences(refs)
        .build();
}
```

#### Multiple Sources

```java
List<SubagentReference> refs = ClaudeSubagentReferences.fromRootDirectories(List.of(
    "src/main/resources/agents",
    System.getProperty("user.home") + "/.claude/agents"
));
```

### Manual Configuration with TaskTool

For more control, configure TaskTool directly:

```java
import org.springaicommunity.agent.tools.task.TaskTool;
import org.springaicommunity.agent.tools.task.TaskOutputTool;
import org.springaicommunity.agent.tools.task.subagent.claude.*;

// Shared repository for task management
TaskRepository taskRepository = new DefaultTaskRepository();

// Create subagent references
List<SubagentReference> refs = ClaudeSubagentReferences.fromRootDirectory("/path/to/agents");

// Create executor with multi-model support
Map<String, ChatClient.Builder> chatClientBuilders = Map.of(
    "default", defaultBuilder,
    "sonnet", sonnetBuilder,
    "opus", opusBuilder
);

List<ToolCallback> subAgentTools = List.of(/* ... */);
ClaudeSubagentExecutor executor = new ClaudeSubagentExecutor(chatClientBuilders, subAgentTools);

// Build TaskTool
ToolCallback taskTool = TaskTool.builder()
    .subagentReferences(refs)
    .subagentResolvers(new ClaudeSubagentResolver())
    .subagentExecutors(executor)
    .taskRepository(taskRepository)
    .build();

// Configure TaskOutputTool
ToolCallback taskOutputTool = TaskOutputTool.builder()
    .taskRepository(taskRepository)
    .build();

// Register with main chat client
ChatClient chatClient = chatClientBuilder
    .defaultToolCallbacks(taskTool, taskOutputTool)
    .build();
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
| `model` | No | Model preference: `sonnet`, `opus`, `haiku` |
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

To add support for a new subagent type (e.g., A2A protocol):

#### 1. Define the Subagent Implementation

```java
public class A2ASubagentDefinition implements SubagentDefinition {

    public static final String KIND = "A2A";

    private final String name;
    private final String description;
    private final String endpoint;
    private final SubagentReference reference;

    @Override
    public String getName() { return name; }

    @Override
    public String getDescription() { return description; }

    @Override
    public String getKind() { return KIND; }

    @Override
    public SubagentReference getReference() { return reference; }

    public String getEndpoint() { return endpoint; }
}
```

#### 2. Create a Resolver

```java
public class A2ASubagentResolver implements SubagentResolver {

    @Override
    public boolean canResolve(SubagentReference ref) {
        return ref.kind().equals(A2ASubagentDefinition.KIND);
    }

    @Override
    public SubagentDefinition resolve(SubagentReference ref) {
        // Load A2A agent card from endpoint
        String agentCard = fetchAgentCard(ref.uri());
        return parseA2ASubagentDefinition(ref, agentCard);
    }
}
```

#### 3. Create an Executor

```java
public class A2ASubagentExecutor implements SubagentExecutor {

    private final A2AClient a2aClient;

    @Override
    public String getKind() {
        return A2ASubagentDefinition.KIND;
    }

    @Override
    public String execute(TaskCall taskCall, SubagentDefinition subagent) {
        A2ASubagentDefinition a2a = (A2ASubagentDefinition) subagent;
        return a2aClient.sendTask(a2a.getEndpoint(), taskCall.prompt());
    }
}
```

#### 4. Register with TaskToolCallbackProvider

```java
// Create SubagentType that bundles resolver + executor
SubagentType a2aType = new SubagentType(
    new A2ASubagentResolver(),
    new A2ASubagentExecutor(a2aClient)
);

TaskToolCallbackProvider taskTools = TaskToolCallbackProvider.builder()
    .chatClientBuilder("default", chatClientBuilder)
    .subagentReferences(
        new SubagentReference("http://agent.example.com", A2ASubagentDefinition.KIND)
    )
    .subagentTypes(a2aType)
    .build();
```

Alternatively, register directly with TaskTool for more control:

```java
TaskTool.builder()
    .subagentReferences(
        new SubagentReference("http://agent.example.com", A2ASubagentDefinition.KIND)
    )
    .subagentResolvers(new A2ASubagentResolver())
    .subagentExecutors(new A2ASubagentExecutor(a2aClient))
    .taskRepository(taskRepository)
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

When the main agent calls TaskTool, it uses these parameters:

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

    @Value("${app.agent.skills.paths}")
    List<String> skillPaths;

    @Value("${BRAVE_API_KEY:#{null}}")
    String braveApiKey;

    @Bean
    CommandLineRunner demo(
            ChatClient.Builder defaultBuilder,
            @Qualifier("sonnet") ChatClient.Builder sonnetBuilder,
            @Qualifier("opus") ChatClient.Builder opusBuilder) {

        return args -> {
            // Configure Task tools with custom agents and multi-model support
            var taskTools = TaskToolCallbackProvider.builder()
                .chatClientBuilder("default", defaultBuilder)
                .chatClientBuilder("sonnet", sonnetBuilder)
                .chatClientBuilder("opus", opusBuilder)
                .subagentReferences(
                    ClaudeSubagentReferences.fromRootDirectory("src/main/resources/agents")
                )
                .skillsDirectories(skillPaths)
                .braveApiKey(braveApiKey)
                .build();

            // Build main chat client
            ChatClient chatClient = defaultBuilder
                .defaultToolCallbacks(taskTools)
                .defaultTools(
                    FileSystemTools.builder().build(),
                    GrepTool.builder().build(),
                    GlobTool.builder().build(),
                    new ShellTools(),
                    TodoWriteTool.builder().build()
                )
                .defaultAdvisors(
                    ToolCallAdvisor.builder().build(),
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

### 3. Leverage Multi-Model Routing

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
        Builder subagentExecutors(List<SubagentExecutor> executors);
        Builder subagentExecutors(SubagentExecutor... executors);
        Builder subagentResolvers(List<SubagentResolver> resolvers);
        Builder subagentResolvers(SubagentResolver... resolvers);
        Builder taskRepository(TaskRepository taskRepository);
        Builder taskDescriptionTemplate(String template);

        ToolCallback build();
    }
}
```

### TaskToolCallbackProvider

```java
public class TaskToolCallbackProvider implements ToolCallbackProvider {

    public static Builder builder() { ... }

    public static class Builder {
        Builder chatClientBuilder(String modelId, ChatClient.Builder builder);
        Builder chatClientBuilders(Map<String, ChatClient.Builder> builders);
        Builder subagentReferences(List<SubagentReference> refs);
        Builder subagentReferences(SubagentReference... refs);
        Builder subagentTypes(List<SubagentType> types);
        Builder subagentTypes(SubagentType... types);
        Builder skillsDirectories(List<String> dirs);
        Builder skillsDirectories(String dir);
        Builder skillsResource(Resource resource);
        Builder skillsResources(List<Resource> resources);
        Builder braveApiKey(String apiKey);
        Builder taskRepository(TaskRepository repository);

        TaskToolCallbackProvider build();
    }
}
```

### Subagent Interfaces

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
    public List<String> skills();       // Skills to load
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

- [**Subagent Framework**](Subagent.md) - Protocol-agnostic subagent abstraction for integrating A2A, MCP, and custom agent protocols
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
