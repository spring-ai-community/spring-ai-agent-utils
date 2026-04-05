# memory-tools-advisor-demo

A runnable Spring Boot console agent demonstrating [`AutoMemoryToolsAdvisor`](../../../spring-ai-agent-utils/docs/AutoMemoryToolsAdvisor.md) in a realistic multi-advisor setup.

## What it demonstrates

- **`AutoMemoryToolsAdvisor`** with a dual-condition consolidation trigger — consolidates when more than 60 seconds pass between turns *or* when the user message contains `"bye"`
- **`ToolCallAdvisor`** with `disableInternalConversationHistory()` — handles recursive tool calling while deferring conversation history to a dedicated advisor
- **`MessageChatMemoryAdvisor`** backed by `MessageWindowChatMemory` (100-message window) — provides short-term session memory alongside the long-term file-based memory
- **`MyLoggingAdvisor`** — a custom advisor that prints tool calls and message text to the console in colour for easy tracing during development

## Advisor stack

```
AutoMemoryToolsAdvisor   (HIGHEST_PRECEDENCE + 200)  ← long-term memory
ToolCallAdvisor                                       ← recursive tool execution
MessageChatMemoryAdvisor                              ← short-term conversation window
MyLoggingAdvisor         (order = 0)                 ← dev console logger
```

## Key source files

| File | Purpose |
|---|---|
| [`Application.java`](src/main/java/org/springaicommunity/agent/Application.java) | Spring Boot entry point — builds the `ChatClient` and runs the console chat loop |
| [`MyLoggingAdvisor.java`](src/main/java/org/springaicommunity/agent/MyLoggingAdvisor.java) | Development advisor logging user messages, tool calls, and assistant responses |
| [`application.properties`](src/main/resources/application.properties) | Model credentials, model selection, and memory directory path |

## Consolidation trigger

The demo uses a stateful lambda that fires on two conditions:

```java
Instant lastInteraction = Instant.now();

.memoryConsolidationTrigger((request, instant) -> {
    var previousInteraction = lastInteraction;
    lastInteraction = Instant.now();

    // Consolidate when more than 60 seconds have passed since the last turn
    if (instant.isAfter(previousInteraction.plusSeconds(60))) {
        return true;
    }

    // Also consolidate when the user says goodbye
    var userMessage = request.prompt().getLastUserOrToolResponseMessage().getText();
    return userMessage != null && userMessage.toLowerCase().contains("bye");
})
```

## Configuration

Memory is stored at `${user.home}/.spring-ai-agent/memory-tools-demo/memory` by default. Change it in `application.properties`:

```properties
agent.memory.dir=/path/to/your/memories
```

The `application.properties` contains commented-out sections for Anthropic and OpenAI SDK. To switch models, uncomment the relevant starter dependency in `pom.xml` and update the `agent.model` property — no Java changes required.

## Running

```bash
cd examples/memory/memory-tools-advisor-demo
ANTHROPIC_API_KEY=sk-... mvn spring-boot:run
```

The agent starts a REPL loop. Type a message and press Enter. Say `bye` to trigger an explicit memory consolidation pass before ending the session.
