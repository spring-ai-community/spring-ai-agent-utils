# Migration Guide: 0.4.x to 0.5.0

## Module Changes

The single `spring-ai-agent-utils` module has been split into three:

| New Module | Description |
|---|---|
| `spring-ai-agent-utils-common` | Shared subagent SPI (interfaces & records) |
| `spring-ai-agent-utils` | Core library (tools, skills, Claude subagents) |
| `spring-ai-agent-utils-a2a` | A2A protocol subagent implementation |

Add `spring-ai-agent-utils-common` as a transitive dependency (pulled in automatically by the core module). Add `spring-ai-agent-utils-a2a` only if you use A2A subagents:

```xml
<dependency>
    <groupId>org.springaicommunity</groupId>
    <artifactId>spring-ai-agent-utils-a2a</artifactId>
    <version>0.5.0-SNAPSHOT</version>
</dependency>
```

## Package Relocations

The subagent SPI types moved from `org.springaicommunity.agent.tools.task.subagent` to `org.springaicommunity.agent.common.task.subagent`:

| Old Import | New Import |
|---|---|
| `o.s.agent.tools.task.subagent.SubagentDefinition` | `o.s.agent.common.task.subagent.SubagentDefinition` |
| `o.s.agent.tools.task.subagent.SubagentExecutor` | `o.s.agent.common.task.subagent.SubagentExecutor` |
| `o.s.agent.tools.task.subagent.SubagentResolver` | `o.s.agent.common.task.subagent.SubagentResolver` |
| `o.s.agent.tools.task.subagent.SubagentReference` | `o.s.agent.common.task.subagent.SubagentReference` |
| `o.s.agent.tools.task.subagent.SubagentType` | `o.s.agent.common.task.subagent.SubagentType` |
| `o.s.agent.tools.task.TaskTool.TaskCall` | `o.s.agent.common.task.subagent.TaskCall` |

A2A classes moved from the example package to the new module:

| Old Import | New Import |
|---|---|
| `o.s.agent.a2a.A2ASubagentDefinition` | `o.s.agent.subagent.a2a.A2ASubagentDefinition` |
| `o.s.agent.a2a.A2ASubagentExecutor` | `o.s.agent.subagent.a2a.A2ASubagentExecutor` |
| `o.s.agent.a2a.A2ASubagentResolver` | `o.s.agent.subagent.a2a.A2ASubagentResolver` |

## API Changes

### `TaskToolCallbackProvider` removed

`TaskToolCallbackProvider` has been deleted. Replace it with `TaskTool.builder()` plus `ClaudeSubagentType.builder()`.

**Before:**

```java
var taskTools = TaskToolCallbackProvider.builder()
    .subagentReferences(ClaudeSubagentReferences.fromResources(agentPaths))
    .chatClientBuilder("default", chatClientBuilder.clone())
    .skillsResources(skillPaths)
    .braveApiKey(braveApiKey)
    .build();

// Used as ToolCallbackProvider
chatClientBuilder.defaultToolCallbacks(taskTools);
```

**After:**

```java
var taskTools = TaskTool.builder()
    .subagentTypes(ClaudeSubagentType.builder()
        .chatClientBuilder("default", chatClientBuilder.clone())
        .skillsResources(skillPaths)
        .braveApiKey(braveApiKey)
        .build())
    .build();

// Now returns a single ToolCallback (not a provider)
chatClientBuilder.defaultToolCallbacks(taskTools);
```

Key differences:
- Claude-specific config (`chatClientBuilder`, `skillsResources`, `braveApiKey`) moves into `ClaudeSubagentType.builder()`
- Built-in Claude subagent references are auto-registered when a Claude subagent type is present
- Custom subagent references are added via `TaskTool.builder().subagentReferences(...)`
- `TaskTool.builder().build()` returns a `ToolCallback`, not a `ToolCallbackProvider`

### `TaskTool.Builder` simplified

The separate `subagentResolvers()` and `subagentExecutors()` methods are replaced by `subagentTypes()`:

**Before:**

```java
TaskTool.builder()
    .subagentResolvers(new MyResolver())
    .subagentExecutors(new MyExecutor())
    .taskRepository(repo)
    .build();
```

**After:**

```java
TaskTool.builder()
    .subagentTypes(new SubagentType(new MyResolver(), new MyExecutor()))
    .build();
```

Note: `taskRepository` now defaults to `DefaultTaskRepository` and no longer needs to be set explicitly.

### `TaskCall` is now a top-level record

If you reference `TaskTool.TaskCall` directly, change to `org.springaicommunity.agent.common.task.subagent.TaskCall`.

### A2A subagents with Claude subagents

When mixing local Claude and remote A2A subagents, register each type separately:

```java
var taskTools = TaskTool.builder()
    // Claude (local)
    .subagentReferences(ClaudeSubagentReferences.fromResources(agentPaths))
    .subagentTypes(ClaudeSubagentType.builder()
        .chatClientBuilder("default", chatClientBuilder.clone())
        .build())
    // A2A (remote)
    .subagentReferences(new SubagentReference("http://host:port/path", A2ASubagentDefinition.KIND))
    .subagentTypes(new SubagentType(new A2ASubagentResolver(), new A2ASubagentExecutor()))
    .build();
```
