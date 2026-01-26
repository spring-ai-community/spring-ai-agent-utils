# Subagent Framework - Protocol-Agnostic Agent Orchestration

## Overview

The Subagent framework provides a protocol-agnostic abstraction for integrating various agent communication protocols with the [TaskTool](TaskTools.md). It enables orchestrating heterogeneous agents across different backends - local LLM-based agents, remote A2A protocol agents, or custom implementations - through a unified interface.

## Design Philosophy

The framework is designed around a simple principle: **decouple agent discovery from agent execution**. This separation allows:

- **Multiple protocols** - Claude markdown, A2A, MCP, custom HTTP APIs
- **Mix local and remote** - Combine local LLM subagents with remote specialized agents
- **Protocol-specific metadata** - Each protocol can define its own configuration format
- **Pluggable execution** - Swap transport layers without changing agent definitions

## Architecture

```
┌──────────────────────────────────────────────────────────────────────┐
│                          TaskTool                                     │
├──────────────────────────────────────────────────────────────────────┤
│  SubagentReference[]    ──────────►  SubagentResolver[]              │
│  (URI + Kind + Metadata)             (Resolve to Definition)          │
│                                             │                         │
│                                             ▼                         │
│                                      SubagentDefinition               │
│                                      (Name, Description, Config)      │
│                                             │                         │
│                                             ▼                         │
│  SubagentExecutor[]     ◄────────────  Execute by Kind               │
│  (Protocol-specific)                                                  │
│                                             │                         │
│                                             ▼                         │
│                                        Response                       │
└──────────────────────────────────────────────────────────────────────┘
```

## Core Abstractions

### SubagentReference

Lightweight pointer to an agent definition resource.

```java
public record SubagentReference(
    String uri,                      // Resource location (URL, classpath, file path)
    String kind,                     // Protocol identifier ("CLAUDE", "A2A", "MCP", etc.)
    Map<String, String> metadata     // Protocol-specific metadata
) {}
```

**Examples:**
```java
// Claude markdown file
new SubagentReference("classpath:/agents/explorer.md", "CLAUDE")

// A2A remote agent
new SubagentReference("http://agent.example.com:10001", "A2A")

// Custom protocol with metadata
new SubagentReference("grpc://agents.internal:443/analyzer", "CUSTOM",
    Map.of("auth", "mtls", "timeout", "30s"))
```

### SubagentResolver

Strategy for resolving references into full definitions.

```java
public interface SubagentResolver {
    /** Returns true if this resolver handles the given reference kind. */
    boolean canResolve(SubagentReference subagentRef);

    /** Resolves the reference to a complete definition. */
    SubagentDefinition resolve(SubagentReference subagentRef);
}
```

### SubagentDefinition

Complete agent metadata and configuration.

```java
public interface SubagentDefinition {
    /** Unique identifier for this subagent. */
    String getName();

    /** Description shown in TaskTool's available agents list. */
    String getDescription();

    /** Protocol kind (e.g., "CLAUDE", "A2A"). */
    String getKind();

    /** Reference used to resolve this definition. */
    SubagentReference getReference();

    /** Format for TaskTool registration display. */
    default String toSubagentRegistrations() {
        return "-%s: /%s".formatted(getName(), getDescription());
    }
}
```

### SubagentExecutor

Executes tasks using protocol-specific communication.

```java
public interface SubagentExecutor {
    /** Returns the kind of subagent this executor handles. */
    String getKind();

    /** Executes the task and returns the response. */
    String execute(TaskCall taskCall, SubagentDefinition subagent);
}
```

### SubagentType

Convenience record bundling resolver and executor for registration.

```java
public record SubagentType(
    SubagentResolver resolver,
    SubagentExecutor executor
) {
    public String kind() { return executor.getKind(); }
}
```

## Built-in: Claude Subagent

The default implementation uses Claude Code's markdown + YAML frontmatter convention.

### Agent Definition Format

```markdown
---
name: spring-ai-expert
description: Expert on Spring AI framework questions and troubleshooting
model: sonnet                    # Optional: model routing
tools: Read, Grep, WebFetch      # Optional: allowed tools
disallowedTools: Edit, Write     # Optional: denied tools
skills: ai-tutor                 # Optional: injected skills
permissionMode: default          # Optional: permission handling
---

You are a Spring AI expert...
```

### Claude Components

| Class | Purpose |
|-------|---------|
| `ClaudeSubagentDefinition` | Parses frontmatter fields (model, tools, skills, etc.) |
| `ClaudeSubagentResolver` | Loads markdown from classpath or filesystem |
| `ClaudeSubagentExecutor` | Executes via Spring AI ChatClient with tool filtering |
| `ClaudeSubagentReferences` | Factory methods for discovering agent files |

### Registration

```java
// Discover Claude agents from directory
List<SubagentReference> refs = ClaudeSubagentReferences.fromRootDirectory("/agents");

// Or from Spring Resources
List<SubagentReference> refs = ClaudeSubagentReferences.fromResources(agentResources);

// Built-in Claude resolver/executor are auto-registered
TaskToolCallbackProvider.builder()
    .subagentReferences(refs)
    .chatClientBuilder("default", chatClientBuilder)
    .build();
```

## Implementing Custom Protocols

### Example: A2A Protocol Integration

The [A2A (Agent-to-Agent)](https://google.github.io/A2A/) protocol demonstrates integrating remote agents.

#### 1. Define the SubagentDefinition

Wrap protocol-specific metadata:

```java
public class A2ASubagentDefinition implements SubagentDefinition {
    public static final String KIND = "A2A";

    private final SubagentReference reference;
    private final AgentCard card;  // A2A protocol's agent descriptor

    public A2ASubagentDefinition(SubagentReference ref, AgentCard card) {
        this.reference = ref;
        this.card = card;
    }

    @Override
    public String getName() { return card.name(); }

    @Override
    public String getDescription() { return card.description(); }

    @Override
    public String getKind() { return KIND; }

    @Override
    public SubagentReference getReference() { return reference; }

    // Protocol-specific accessor
    public AgentCard getAgentCard() { return card; }
}
```

#### 2. Implement the Resolver

Discover agents via protocol-specific mechanism:

```java
public class A2ASubagentResolver implements SubagentResolver {
    public static final String AGENT_CARD_PATH = "/.well-known/agent-card.json";

    @Override
    public boolean canResolve(SubagentReference ref) {
        return ref.kind().equals(A2ASubagentDefinition.KIND);
    }

    @Override
    public SubagentDefinition resolve(SubagentReference ref) {
        // Fetch agent card from well-known endpoint
        AgentCard card = new A2ACardResolver(
            new JdkA2AHttpClient(),
            ref.uri(),
            AGENT_CARD_PATH
        ).getAgentCard();

        return new A2ASubagentDefinition(ref, card);
    }
}
```

#### 3. Implement the Executor

Execute tasks via protocol transport:

```java
public class A2ASubagentExecutor implements SubagentExecutor {

    @Override
    public String getKind() { return A2ASubagentDefinition.KIND; }

    @Override
    public String execute(TaskCall taskCall, SubagentDefinition subagent) {
        A2ASubagentDefinition a2a = (A2ASubagentDefinition) subagent;
        AgentCard card = a2a.getAgentCard();

        // Create A2A message
        Message message = new Message.Builder()
            .role(Message.Role.USER)
            .parts(List.of(new TextPart(taskCall.prompt())))
            .build();

        // Send via A2A client
        Client client = Client.builder(card)
            .withTransport(JSONRPCTransport.class, new JSONRPCTransportConfig())
            .build();

        // Execute and extract response
        CompletableFuture<String> future = new CompletableFuture<>();
        client.sendMessage(message, (event, c) -> {
            if (event instanceof TaskEvent taskEvent) {
                future.complete(extractTextFromArtifacts(taskEvent.getTask()));
            }
        });

        return future.get(60, TimeUnit.SECONDS);
    }
}
```

#### 4. Register with TaskToolCallbackProvider

```java
TaskToolCallbackProvider taskTools = TaskToolCallbackProvider.builder()
    // Local Claude subagents
    .subagentReferences(ClaudeSubagentReferences.fromResources(agentPaths))
    .chatClientBuilder("default", chatClientBuilder)

    // Remote A2A subagent
    .subagentReferences(new SubagentReference("http://localhost:10001", "A2A"))
    .subagentTypes(new SubagentType(new A2ASubagentResolver(), new A2ASubagentExecutor()))

    .build();
```

## Other Protocol Patterns

The abstraction accommodates various agent communication patterns:

### MCP (Model Context Protocol)

```java
public class MCPSubagentDefinition implements SubagentDefinition {
    public static final String KIND = "MCP";
    // Wrap MCP server capabilities
}

public class MCPSubagentResolver implements SubagentResolver {
    // Load from mcp.json or discover via stdio/SSE
}

public class MCPSubagentExecutor implements SubagentExecutor {
    // Execute via MCP tool calls
}
```

### Custom HTTP API

```java
public class HttpSubagentDefinition implements SubagentDefinition {
    public static final String KIND = "HTTP";
    private final String endpoint;
    private final Map<String, String> headers;
}

public class HttpSubagentResolver implements SubagentResolver {
    // Load from OpenAPI spec or configuration
}

public class HttpSubagentExecutor implements SubagentExecutor {
    // Execute via REST calls
}
```

### gRPC-based Agents

```java
public class GrpcSubagentDefinition implements SubagentDefinition {
    public static final String KIND = "GRPC";
    private final ManagedChannel channel;
}
```

## Registration Flow

```
┌─────────────────────────────────────────────────────────────────────┐
│                  TaskToolCallbackProvider.builder()                  │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  .subagentReferences(...)  ──► Collected into List<SubagentRef>     │
│                                                                      │
│  .subagentTypes(...)       ──► Collected into List<SubagentType>    │
│                                (Each bundles resolver + executor)    │
│                                                                      │
│  .chatClientBuilder(...)   ──► For ClaudeSubagentExecutor           │
│                                                                      │
│                            .build()                                  │
│                               │                                      │
│                               ▼                                      │
│                                                                      │
│  For each SubagentReference:                                        │
│    1. Find SubagentResolver where canResolve(ref) == true           │
│    2. Call resolver.resolve(ref) → SubagentDefinition               │
│    3. Store definition for TaskTool                                 │
│                                                                      │
│  TaskTool execution:                                                │
│    1. Find SubagentDefinition by name                               │
│    2. Find SubagentExecutor by kind                                 │
│    3. Call executor.execute(taskCall, definition)                   │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

## Package Structure

```
org.springaicommunity.agent.tools.task.subagent
├── SubagentDefinition.java      # Core interface
├── SubagentReference.java       # Lightweight reference record
├── SubagentResolver.java        # Resolution strategy interface
├── SubagentExecutor.java        # Execution strategy interface
├── SubagentType.java            # Resolver + Executor bundle
└── claude/                      # Built-in Claude implementation
    ├── ClaudeSubagentDefinition.java
    ├── ClaudeSubagentResolver.java
    ├── ClaudeSubagentExecutor.java
    └── ClaudeSubagentReferences.java
```

## Best Practices

### 1. Use Meaningful KIND Constants

```java
public static final String KIND = "A2A";        // Good
public static final String KIND = "type1";      // Bad
```

### 2. Handle Resolution Failures Gracefully

```java
@Override
public SubagentDefinition resolve(SubagentReference ref) {
    try {
        return doResolve(ref);
    } catch (IOException e) {
        throw new RuntimeException("Failed to resolve: " + ref.uri(), e);
    }
}
```

### 3. Include Protocol-Specific Accessors

```java
public class A2ASubagentDefinition implements SubagentDefinition {
    // Standard interface methods...

    // Protocol-specific accessor
    public AgentCard getAgentCard() { return card; }
}
```

### 4. Support Async When Appropriate

```java
@Override
public String execute(TaskCall taskCall, SubagentDefinition subagent) {
    // Use CompletableFuture for async protocols
    return responseFuture.get(timeout, TimeUnit.SECONDS);
}
```

### 5. Log Discovery and Execution

```java
logger.debug("Discovered agent: {} at {}", card.name(), url);
logger.info("Agent '{}' response received", subagent.getName());
```

## Related Documentation

- [TaskTools](TaskTools.md) - Complete TaskTool documentation and usage
- [SkillsTool](SkillsTool.md) - Reusable knowledge modules for subagents
- [Example: subagent-demo](../../examples/subagent-demo) - Full A2A integration example

## References

- [Claude Code Sub-Agents](https://code.claude.com/docs/en/sub-agents)
- [Claude Agent SDK](https://platform.claude.com/docs/en/agent-sdk/subagents)
- [A2A Protocol](https://google.github.io/A2A/)
- [MCP Protocol](https://modelcontextprotocol.io/)
