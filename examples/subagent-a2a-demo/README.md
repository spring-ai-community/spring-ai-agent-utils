## A2A Subagent Demo

This example extends the hierarchical agent system with support for the [A2A (Agent-to-Agent) protocol](https://google.github.io/A2A/), enabling communication with remote agents.

### Overview

While the base [subagent-demo](../subagent-demo) uses Markdown-defined local subagents, this demo combines local Claude subagents with a remote A2A agent. The A2A classes live in the separate [`spring-ai-agent-utils-a2a`](../../spring-ai-agent-utils-a2a/README.md) module.

### Key Components

| Class | Module | Purpose |
|-------|--------|---------|
| `A2ASubagentDefinition` | `spring-ai-agent-utils-a2a` | Wraps an A2A `AgentCard` as a `SubagentDefinition` |
| `A2ASubagentResolver` | `spring-ai-agent-utils-a2a` | Fetches agent metadata from `/.well-known/agent-card.json` |
| `A2ASubagentExecutor` | `spring-ai-agent-utils-a2a` | Sends tasks to remote agents via JSON-RPC transport |
| `ClaudeSubagentType` | `spring-ai-agent-utils` | Configures local Claude subagents with tools, skills, and model routing |

### Configuration

```java
var taskTool = TaskTool.builder()
    // Local Claude subagents (from Markdown files)
    .subagentReferences(ClaudeSubagentReferences.fromResources(agentPaths))
    .subagentTypes(ClaudeSubagentType.builder()
        .skillsResources(skillPaths)
        .chatClientBuilder("default", chatClientBuilder.clone())
        .braveApiKey(braveApiKey)
        .build())

    // Remote A2A subagent
    .subagentReferences(new SubagentReference("http://localhost:10001/airbnb", A2ASubagentDefinition.KIND))
    .subagentTypes(new SubagentType(new A2ASubagentResolver(), new A2ASubagentExecutor()))

    .build();
```

### Prerequisites

- An A2A-compatible agent running (e.g., at `http://localhost:10001/airbnb`)
- The agent must expose an agent card at its well-known endpoint

### Dependencies

- `spring-ai-agent-utils` - Core library with Task Tools and Claude subagent support
- `spring-ai-agent-utils-a2a` - A2A protocol subagent implementation
- `spring-ai-starter-model-google-genai` - Google GenAI model (configurable)

### Running

```bash
export GOOGLE_GENAI_API_KEY=your-key
mvn spring-boot:run
```

The orchestrator will automatically discover the A2A agent at startup and make it available for task delegation alongside the built-in Claude subagents.

### Related Documentation

- [Task Tools Documentation](../../spring-ai-agent-utils/docs/TaskTools.md)
- [Subagent Framework](../../spring-ai-agent-utils/docs/Subagent.md)
- [spring-ai-agent-utils-a2a](../../spring-ai-agent-utils-a2a/README.md) - A2A module reference
- [Sub-Agent Demo](../subagent-demo) - Local-only Claude subagent demo
