# Spring AI Agent Utils Common

Shared subagent SPI (Service Provider Interface) for the [Spring AI Agent Utils](../spring-ai-agent-utils/README.md) project.

This module defines the core abstractions that allow different subagent implementations (Claude-based, [A2A protocol](https://google.github.io/A2A/), or custom) to plug into the `TaskTool` orchestration system.

## SPI Interfaces

| Interface | Description |
|-----------|-------------|
| `SubagentDefinition` | Defines a subagent's identity, description, kind, and reference metadata |
| `SubagentResolver` | Resolves a `SubagentReference` into a fully populated `SubagentDefinition` |
| `SubagentExecutor` | Executes a task against a resolved subagent and returns the result |
| `SubagentType` | Pairs a `SubagentResolver` with its `SubagentExecutor` for a specific kind |
| `SubagentReference` | Lightweight pointer to a subagent resource (URI + kind + optional metadata) |
| `TaskCall` | Input record describing the task to execute (prompt, subagent type, model, etc.) |

## How It Fits Together

```
SubagentReference ──► SubagentResolver ──► SubagentDefinition
                                                    │
                              TaskCall ──► SubagentExecutor ──► result
```

1. A `SubagentReference` (e.g., a classpath URI or remote URL) is passed to a `SubagentResolver`
2. The resolver loads and parses the reference into a `SubagentDefinition` with full metadata
3. At execution time, a `TaskCall` and the resolved definition are passed to a `SubagentExecutor`
4. The executor runs the task and returns a string result

## Implementing a Custom Subagent Type

```java
// 1. Define your subagent definition
public class MySubagentDefinition implements SubagentDefinition {
    @Override public String getName() { return "my-agent"; }
    @Override public String getDescription() { return "Does something useful"; }
    @Override public String getKind() { return "MY_KIND"; }
    @Override public SubagentReference getReference() { return this.ref; }
}

// 2. Implement the resolver
public class MySubagentResolver implements SubagentResolver {
    @Override
    public boolean canResolve(SubagentReference ref) {
        return ref.kind().equals("MY_KIND");
    }

    @Override
    public SubagentDefinition resolve(SubagentReference ref) {
        // Load and parse the reference into a full definition
        return new MySubagentDefinition(ref);
    }
}

// 3. Implement the executor
public class MySubagentExecutor implements SubagentExecutor {
    @Override
    public String getKind() { return "MY_KIND"; }

    @Override
    public String execute(TaskCall taskCall, SubagentDefinition subagent) {
        // Execute the task and return the result
        return "Result from my agent";
    }
}

// 4. Register with TaskTool
TaskTool.builder()
    .subagentReferences(new SubagentReference("my://agent-1", "MY_KIND"))
    .subagentTypes(new SubagentType(new MySubagentResolver(), new MySubagentExecutor()))
    .build();
```

## Installation

**Maven:**
```xml
<dependency>
    <groupId>org.springaicommunity</groupId>
    <artifactId>spring-ai-agent-utils-common</artifactId>
    <version>0.5.0-SNAPSHOT</version>
</dependency>
```

> **Note:** This module is typically consumed as a transitive dependency of `spring-ai-agent-utils` or `spring-ai-agent-utils-a2a`. You only need to depend on it directly when implementing a custom subagent type in a separate module.

## Requirements

- Java 17+
- Spring AI 2.0.0-SNAPSHOT (> M1)

## License

Apache License 2.0
