# Spring AI Agent Utils A2A

[A2A (Agent-to-Agent)](https://google.github.io/A2A/) protocol subagent implementation for the [Spring AI Agent Utils](../spring-ai-agent-utils/README.md) project.

This module enables your AI agent to delegate tasks to remote agents over the A2A protocol. It implements the [subagent SPI](../spring-ai-agent-utils-common/README.md) so that A2A agents are discovered and invoked through the same `TaskTool` as local Claude subagents.

## How It Works

```
TaskTool
  │
  ├── SubagentReference("http://host:port/path", "A2A")
  │         │
  │         ▼
  │   A2ASubagentResolver
  │     ── fetches /.well-known/agent-card.json
  │     ── returns A2ASubagentDefinition (wraps AgentCard)
  │
  └── A2ASubagentExecutor
        ── sends message via JSON-RPC transport
        ── waits for task completion (60s timeout)
        ── extracts text from response artifacts
```

## Components

| Class | Description |
|-------|-------------|
| `A2ASubagentDefinition` | Wraps an A2A `AgentCard` as a `SubagentDefinition` (kind = `"A2A"`) |
| `A2ASubagentResolver` | Fetches the agent card from the well-known endpoint and creates the definition |
| `A2ASubagentExecutor` | Sends a text message to the remote agent and returns the response |

## Usage

### Register an A2A subagent alongside local Claude subagents

```java
import org.springaicommunity.agent.common.task.subagent.SubagentReference;
import org.springaicommunity.agent.common.task.subagent.SubagentType;
import org.springaicommunity.agent.subagent.a2a.A2ASubagentDefinition;
import org.springaicommunity.agent.subagent.a2a.A2ASubagentExecutor;
import org.springaicommunity.agent.subagent.a2a.A2ASubagentResolver;

var taskTools = TaskTool.builder()
    // Local Claude subagents
    .subagentTypes(ClaudeSubagentType.builder()
        .chatClientBuilder("default", chatClientBuilder)
        .build())

    // Remote A2A subagent
    .subagentReferences(new SubagentReference("http://localhost:10001/myagent", A2ASubagentDefinition.KIND))
    .subagentTypes(new SubagentType(new A2ASubagentResolver(), new A2ASubagentExecutor()))

    .build();
```

### Use A2A subagents only

```java
var taskTools = TaskTool.builder()
    .subagentReferences(
        new SubagentReference("http://host-a:10001/agent-a", A2ASubagentDefinition.KIND),
        new SubagentReference("http://host-b:10002/agent-b", A2ASubagentDefinition.KIND))
    .subagentTypes(new SubagentType(new A2ASubagentResolver(), new A2ASubagentExecutor()))
    .build();
```

### Custom agent card path

By default, the resolver fetches the agent card from `<uri>/.well-known/agent-card.json`. You can customize the path:

```java
var resolver = new A2ASubagentResolver("/custom/agent-card.json");
```

## Agent Discovery

The resolver expects each A2A-compatible agent to expose an [Agent Card](https://google.github.io/A2A/#/documentation?id=agent-card) at its well-known endpoint. The card provides the agent's name, description, and capabilities, which are used to populate the `SubagentDefinition` and register the agent with `TaskTool`.

## Installation

**Maven:**
```xml
<dependency>
    <groupId>org.springaicommunity</groupId>
    <artifactId>spring-ai-agent-utils-a2a</artifactId>
    <version>0.5.0-SNAPSHOT</version>
</dependency>
```

This brings in the [A2A Java SDK](https://github.com/a2aproject/a2a-java-sdk) client and JSON-RPC transport as transitive dependencies.

## Example

See the [subagent-a2a-demo](../examples/subagent-a2a-demo) for a complete example that combines local Claude subagents with a remote A2A agent.

## Requirements

- Java 17+
- Spring AI 2.0.0-SNAPSHOT (> M1)
- A running A2A-compatible agent server

## License

Apache License 2.0
