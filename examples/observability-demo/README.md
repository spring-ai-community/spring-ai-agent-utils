# Observability Demo

Shows how to observe and interrupt the agent's tool-calling loop. A `ChatClient.call()` normally runs every tool-call round to completion; this demo traces each tool call as it happens and lets you stop a running turn.

## What It Shows

- **[ToolCallListener](../../docs/tools/ToolCallListener.md)**: every tool call is printed with its input, duration and result size, and counted per turn. Tool failures are reported back to the model instead of failing the turn.
- **[InterruptAdvisor](../../docs/tools/InterruptAdvisor.md)**: the turn stops when you type `/stop`, or when it has made `agent.max-tool-calls-per-turn` tool calls. Both conditions are combined in one `interruptSignal`.
- **Composition**: the listener's per-turn counter drives the advisor's budget check.

Tools: the default [`AgentToolset`](../../docs/tools/AgentToolset.md) (`Bash` / `BashOutput` / `KillShell`, `Read` / `Write` / `Edit`, `Grep`, `Glob`, `ListDirectory`), with the demo's `ToolCallListener` attached by the builder.

## Running the Demo

```bash
# To use another AI provider, swap the model starter in pom.xml first,
# then set API key for your chosen model provider

export GOOGLE_CLOUD_PROJECT=your-project-id
# or: export ANTHROPIC_API_KEY=your-key
# or: export OPENAI_API_KEY=your-key

./mvnw spring-boot:run -pl examples/observability-demo
```

While the assistant works, type `/stop` and press Enter to stop the turn. Type `/exit` to quit.

## Example Session

Illustrative output (the exact tool calls depend on the model):

```
> USER: Run "sleep 5" in the shell three times, one command per tool call, then tell me the date.
  [tool #1] Bash {"command":"sleep 5"}
/stop
  (stopping before the next model request...)
  [tool] Bash ok, 5012 ms, 21 chars

> ASSISTANT: [turn stopped by /stop]
```

`InterruptAdvisor` checks its signal before every model request, so a tool that is already running finishes first, and the turn stops before the model is called again.

## How It Works

```java
AtomicBoolean stopRequested = new AtomicBoolean();
AtomicInteger toolCalls = new AtomicInteger();

// Trace and count every tool call of the default toolset
List<ToolCallback> tools = AgentToolset.builder()
    .listener(listener)
    .build();

ChatClient chatClient = chatClientBuilder
    .defaultTools(tools)
    .defaultAdvisors(
        InterruptAdvisor.builder()
            .interruptSignal(() -> stopRequested.get() || toolCalls.get() >= maxToolCallsPerTurn)
            .build(),
        memoryAdvisor)
    .build();
```

- Each turn runs on a worker thread, so the console stays free to read `/stop`. Both conditions are reset at the start of every turn.
- A stopped turn ends with a `TurnInterruptedException`, which the worker catches.
- The listener's `beforeCall` returns the start time as the correlation context, which `afterCall`/`onError` use to compute the duration.

See [Application.java](src/main/java/org/springaicommunity/agent/Application.java) for the full example.

## Configuration

[application.properties](src/main/resources/application.properties):

```properties
# Stop a turn after this many tool calls
agent.max-tool-calls-per-turn=20
```

## See Also

- [Code Agent Demo](../code-agent-demo): uses the same two features with a per-turn time limit instead of `/stop`
