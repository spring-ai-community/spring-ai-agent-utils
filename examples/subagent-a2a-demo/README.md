## A2A Subagent Demo

This example extends the hierarchical agent system with support for the [A2A (Agent-to-Agent) protocol](https://google.github.io/A2A/), enabling communication with remote agents.

### Overview

While the base `subagent-demo` uses Markdown-defined local subagents, this demo adds A2A protocol support to delegate tasks to external agents over HTTP.

### Key Components

| Class | Purpose |
|-------|---------|
| `A2ASubagentDefinition` | Wraps an A2A `AgentCard` as a `SubagentDefinition` |
| `A2ASubagentResolver` | Fetches agent metadata from `/.well-known/agent-card.json` |
| `A2ASubagentExecutor` | Sends tasks to remote agents via JSON-RPC transport |

### Configuration

```java
var taskTools = TaskToolCallbackProvider.builder()
    // Local Claude subagents (from Markdown files)
    .subagentReferences(ClaudeSubagentReferences.fromResources(agentPaths))
    .chatClientBuilder("default", chatClientBuilder.clone())

    // Remote A2A subagent
    .subagentReferences(new SubagentReference("http://localhost:10001", A2ASubagentDefinition.KIND))
    .subagentTypes(new SubagentType(new A2ASubagentResolver(), new A2ASubagentExecutor()))
    .build();
```

### Prerequisites

- An A2A-compatible agent running at `http://localhost:10001`
- The agent must expose `/.well-known/agent-card.json`

### Dependencies

- `a2a-java-sdk-client` - A2A protocol client
- `a2a-java-sdk-client-transport-jsonrpc` - JSON-RPC transport
- `spring-ai-starter-model-google-genai` - Google GenAI model (configurable)

### Running

```bash
export GOOGLE_GENAI_API_KEY=your-key
mvn spring-boot:run
```

The orchestrator will automatically discover the A2A agent at startup and make it available for task delegation.
