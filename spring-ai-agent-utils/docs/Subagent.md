# Subagent Framework - Protocol-Agnostic Agent Orchestration

## Overview

The Subagent framework provides a protocol-agnostic abstraction for integrating various agent communication protocols with the [TaskTool](TaskTools.md). It enables orchestrating heterogeneous agents across different backends - local LLM-based agents, remote A2A protocol agents, or custom implementations - through a unified interface.

The SPI interfaces live in the [`spring-ai-agent-utils-common`](../../spring-ai-agent-utils-common/README.md) module so that different subagent implementations can be developed in separate modules without depending on each other.

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
│  SubagentType[]             ──────────►  Each bundles:               │
│  (one per protocol kind)                   SubagentResolver           │
│                                            SubagentExecutor           │
│                                                                       │
│  SubagentReference[]        ──────────►  Resolved via matching        │
│  (URI + Kind + Metadata)                 SubagentResolver             │
│                                             │                         │
│                                             ▼                         │
│                                      SubagentDefinition               │
│                                      (Name, Description, Config)      │
│                                             │                         │
│                                             ▼                         │
│                                      SubagentExecutor.execute()       │
│                                      (Protocol-specific)              │
│                                             │                         │
│                                             ▼                         │
│                                        Response                       │
└──────────────────────────────────────────────────────────────────────┘
```

## Core Abstractions

All SPI types are in the `org.springaicommunity.agent.common.task.subagent` package (`spring-ai-agent-utils-common` module).

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
new SubagentReference("http://agent.example.com:10001/myagent", "A2A")

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

Bundles a resolver and executor for a specific kind. This is the unit of registration with `TaskTool.builder().subagentTypes(...)`.

```java
public record SubagentType(
    SubagentResolver resolver,
    SubagentExecutor executor
) {
    public String kind() { return executor.getKind(); }
}
```

### TaskCall

Input record describing the task to execute. Used by both `TaskTool` and `SubagentExecutor`.

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
skills: ai-tutor                 # Optional: preloaded skills
permissionMode: default          # Optional: permission handling
---

You are a Spring AI expert...
```

### Claude Components

| Class | Purpose |
|-------|---------|
| `ClaudeSubagentDefinition` | Parses frontmatter fields (model, tools, skills, etc.) |
| `ClaudeSubagentResolver` | Loads markdown from classpath or filesystem |
| `ClaudeSubagentExecutor` | Executes via Spring AI ChatClient with tool filtering and skill preloading |
| `ClaudeSubagentReferences` | Factory methods for discovering agent files |
| `ClaudeSubagentType` | Convenience builder that creates a `SubagentType` with default tools |

### Registration

```java
// Create Claude subagent type with tools and model routing
SubagentType claudeType = ClaudeSubagentType.builder()
    .chatClientBuilder("default", chatClientBuilder)
    .skillsResources(skillPaths)
    .braveApiKey(braveApiKey)
    .build();

// Discover custom Claude agents from directory
List<SubagentReference> refs = ClaudeSubagentReferences.fromResources(agentResources);

// Register with TaskTool (built-in agents are added automatically)
TaskTool.builder()
    .subagentTypes(claudeType)
    .subagentReferences(refs)
    .build();
```

## A2A Protocol Subagent

The [A2A (Agent-to-Agent)](https://google.github.io/A2A/) protocol implementation lives in the separate `spring-ai-agent-utils-a2a` module. See the [A2A module README](../../spring-ai-agent-utils-a2a/README.md) for full details.

### A2A Components

| Class | Purpose |
|-------|---------|
| `A2ASubagentDefinition` | Wraps an A2A `AgentCard` (kind = `"A2A"`) |
| `A2ASubagentResolver` | Fetches agent card from `/.well-known/agent-card.json` |
| `A2ASubagentExecutor` | Sends messages via JSON-RPC transport, extracts text from artifacts |

### Registration

```java
import org.springaicommunity.agent.common.task.subagent.SubagentReference;
import org.springaicommunity.agent.common.task.subagent.SubagentType;
import org.springaicommunity.agent.subagent.a2a.A2ASubagentDefinition;
import org.springaicommunity.agent.subagent.a2a.A2ASubagentExecutor;
import org.springaicommunity.agent.subagent.a2a.A2ASubagentResolver;

TaskTool.builder()
    // Local Claude subagents
    .subagentTypes(ClaudeSubagentType.builder()
        .chatClientBuilder("default", chatClientBuilder)
        .build())

    // Remote A2A subagent
    .subagentReferences(new SubagentReference("http://localhost:10001/myagent", A2ASubagentDefinition.KIND))
    .subagentTypes(new SubagentType(new A2ASubagentResolver(), new A2ASubagentExecutor()))

    .build();
```

## Implementing Custom Protocols

To add a new protocol, implement three interfaces and bundle them into a `SubagentType`.

### 1. Define the SubagentDefinition

Wrap protocol-specific metadata:

```java
public class MySubagentDefinition implements SubagentDefinition {
    public static final String KIND = "MY_PROTOCOL";

    private final SubagentReference reference;
    private final MyAgentMetadata metadata;

    @Override
    public String getName() { return metadata.name(); }

    @Override
    public String getDescription() { return metadata.description(); }

    @Override
    public String getKind() { return KIND; }

    @Override
    public SubagentReference getReference() { return reference; }

    // Protocol-specific accessor
    public MyAgentMetadata getMetadata() { return metadata; }
}
```

### 2. Implement the Resolver

Discover agents via protocol-specific mechanism:

```java
public class MySubagentResolver implements SubagentResolver {

    @Override
    public boolean canResolve(SubagentReference ref) {
        return ref.kind().equals(MySubagentDefinition.KIND);
    }

    @Override
    public SubagentDefinition resolve(SubagentReference ref) {
        MyAgentMetadata metadata = fetchMetadata(ref.uri());
        return new MySubagentDefinition(ref, metadata);
    }
}
```

### 3. Implement the Executor

Execute tasks via protocol transport:

```java
public class MySubagentExecutor implements SubagentExecutor {

    @Override
    public String getKind() { return MySubagentDefinition.KIND; }

    @Override
    public String execute(TaskCall taskCall, SubagentDefinition subagent) {
        MySubagentDefinition myAgent = (MySubagentDefinition) subagent;
        return myClient.send(myAgent.getMetadata(), taskCall.prompt());
    }
}
```

### 4. Register with TaskTool

```java
TaskTool.builder()
    .subagentReferences(new SubagentReference("my://agent-1", MySubagentDefinition.KIND))
    .subagentTypes(new SubagentType(new MySubagentResolver(), new MySubagentExecutor()))
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
│                     TaskTool.builder()                                │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  .subagentTypes(...)          ──► Collected into List<SubagentType>  │
│                                   (Each bundles resolver + executor)  │
│                                                                      │
│  .subagentReferences(...)     ──► Collected into List<SubagentRef>   │
│                                                                      │
│                            .build()                                  │
│                               │                                      │
│                               ▼                                      │
│                                                                      │
│  If Claude SubagentType present:                                    │
│    → Auto-register built-in subagent references                     │
│      (general-purpose, Explore, Plan, Bash)                         │
│                                                                      │
│  For each SubagentReference:                                        │
│    1. Find SubagentResolver (from SubagentTypes) where              │
│       canResolve(ref) == true                                       │
│    2. Call resolver.resolve(ref) → SubagentDefinition               │
│    3. Store definition for TaskTool                                 │
│                                                                      │
│  TaskTool execution:                                                │
│    1. Find SubagentDefinition by name                               │
│    2. Find SubagentExecutor (from SubagentTypes) by kind            │
│    3. Call executor.execute(taskCall, definition)                   │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

## Package Structure

```
spring-ai-agent-utils-common/
  org.springaicommunity.agent.common.task.subagent
  ├── SubagentDefinition.java      # Core interface
  ├── SubagentReference.java       # Lightweight reference record
  ├── SubagentResolver.java        # Resolution strategy interface
  ├── SubagentExecutor.java        # Execution strategy interface
  ├── SubagentType.java            # Resolver + Executor bundle
  └── TaskCall.java                # Task execution input record

spring-ai-agent-utils/
  org.springaicommunity.agent.tools.task
  ├── TaskTool.java                # Main tool with builder
  ├── TaskOutputTool.java          # Background task result retrieval
  └── subagent/claude/             # Built-in Claude implementation
      ├── ClaudeSubagentDefinition.java
      ├── ClaudeSubagentResolver.java
      ├── ClaudeSubagentExecutor.java
      ├── ClaudeSubagentReferences.java
      └── ClaudeSubagentType.java  # Convenience builder

spring-ai-agent-utils-a2a/
  org.springaicommunity.agent.subagent.a2a
  ├── A2ASubagentDefinition.java
  ├── A2ASubagentResolver.java
  └── A2ASubagentExecutor.java
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
- [spring-ai-agent-utils-common](../../spring-ai-agent-utils-common/README.md) - SPI module with all core abstractions
- [spring-ai-agent-utils-a2a](../../spring-ai-agent-utils-a2a/README.md) - A2A protocol implementation
- [SkillsTool](SkillsTool.md) - Reusable knowledge modules for subagents
- [Example: subagent-demo](../../examples/subagent-demo) - Local Claude subagent demo
- [Example: subagent-a2a-demo](../../examples/subagent-a2a-demo) - A2A integration demo

## References

- [Claude Code Sub-Agents](https://code.claude.com/docs/en/sub-agents)
- [Claude Agent SDK](https://platform.claude.com/docs/en/agent-sdk/subagents)
- [A2A Protocol](https://google.github.io/A2A/)
- [MCP Protocol](https://modelcontextprotocol.io/)
