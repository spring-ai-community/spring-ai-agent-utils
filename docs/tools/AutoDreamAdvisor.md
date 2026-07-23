# AutoDreamAdvisor

A `ChatClient` advisor that schedules periodic, out-of-band [`AutoDreamService`](AutoDreamService.md) memory-consolidation cycles on top of an [`AutoMemoryTools`](AutoMemoryTools.md) memory store — the same relationship [`AutoMemoryToolsAdvisor`](AutoMemoryToolsAdvisor.md) has to `AutoMemoryTools`, but for background dreaming instead of live-turn tool injection.

## What it does

Unlike `AutoMemoryToolsAdvisor`, which injects tools and augments the system prompt on every request, `AutoDreamAdvisor` never touches the live request or response:

- **`before()` is a no-op.** Dreaming never adds latency or tokens to the turn the user is waiting on.
- **`after()`** loads the persisted `DreamState`, increments its turn counter, evaluates the configured `DreamTrigger`, and — only if it fires — submits the dream cycle to a background task and returns immediately. The dream cycle itself runs off the calling thread.

Because it fires from `after()` rather than `before()`, `AutoDreamAdvisor` is order-insensitive relative to other advisors in practice: it doesn't depend on what any other advisor did to the response, and nothing downstream depends on it having run yet.

## Quick Start

```java
AutoDreamService dreamService = AutoDreamService.builder(chatClientBuilder.clone()).build();

AutoDreamAdvisor dreamAdvisor = AutoDreamAdvisor.builder()
    .memoriesRootDirectory("/home/user/.agent/memories")
    .dreamService(dreamService)
    .dreamTrigger(DreamTriggers.hoursAndSessions(24, 5))
    .build();

ChatClient chatClient = ChatClient.builder(chatModel)
    .defaultAdvisors(
        AutoMemoryToolsAdvisor.builder().memoriesRootDirectory(dir).build(),
        dreamAdvisor,
        MessageChatMemoryAdvisor.builder(chatMemory).build())
    .build();
```

Point `AutoDreamAdvisor` and `AutoMemoryToolsAdvisor` at the same `memoriesRootDirectory` — one gives the live agent read/write memory tools, the other schedules the Dreamer that periodically cleans that same store up.

## Builder Configuration

```java
AutoDreamAdvisor advisor = AutoDreamAdvisor.builder()
    .memoriesRootDirectory("/path/to/memories")          // required
    .dreamService(dreamService)                           // required
    .dreamTrigger(DreamTriggers.hoursAndSessions(24, 5))  // optional
    .userId("alice")                                      // optional — enables cross-session recall
    .taskRepository(customTaskRepository)                 // optional
    .order(BaseAdvisor.HIGHEST_PRECEDENCE + 150)          // optional
    .build();
```

| Builder method | Type | Default | Description |
|---|---|---|---|
| `memoriesRootDirectory(String)` | `String` | — (**required**) | Same memory store an `AutoMemoryToolsAdvisor` is pointed at. |
| `dreamService(AutoDreamService)` | `AutoDreamService` | — (**required**) | The service that actually runs dream cycles. |
| `dreamTrigger(DreamTrigger)` | `DreamTrigger` | `DreamTriggers.manualOnly()` | Decides whether a cycle should fire on each turn. See [AutoDreamService — Dream Triggers](AutoDreamService.md#dream-triggers). |
| `userId(String)` | `String` | none | Optional. When set, triggered cycles call the cross-session-aware `runDreamCycle(dir, userId)` overload instead of the memory-only one — see below. |
| `taskRepository(TaskRepository)` | `TaskRepository` | a fresh `DefaultTaskRepository` | Where the background dream task is submitted. Override to share a `TaskRepository` with `TaskTool` or other background work. |
| `order(int)` | `int` | `HIGHEST_PRECEDENCE + 150` | Advisor order — higher precedence than `AutoMemoryToolsAdvisor`'s default (`+200`). |

### `userId(String)` and cross-session recall

Setting `.userId(...)` only has an effect if the advisor's `dreamService` was itself built with `.sessionService(...)` configured (see [AutoDreamService — Cross-Session Recall](AutoDreamService.md#cross-session-recall-optional)). If the service has no `sessionService`, `userId` is passed through but ignored — the Dreamer still only sees the memory store. There's no error in either misconfiguration direction; both degrade gracefully to memory-only dreaming.

## Turn counting

`DreamState.sessionsSinceLastDream()` counts **conversation turns processed by this advisor instance** — every call to `after()` increments it, regardless of whether the turn involved memory tools at all. This is not the same thing as a `spring-ai-session` "session": there is no dependency on session-transcript storage for the trigger itself, only (optionally) for what the Dreamer can search once a cycle starts.

## Relationship to `memoryConsolidationTrigger`

`AutoDreamAdvisor` and `AutoMemoryToolsAdvisor`'s `memoryConsolidationTrigger` are complementary, not alternatives — see the comparison table in [AutoDreamService](AutoDreamService.md#in-band-nudge-vs-out-of-band-dreaming). A typical setup uses both: the in-band nudge for cheap, frequent tidy-ups, and `AutoDreamAdvisor` for periodic, deeper background cleanup.

## Demo Application

See [memory-tools-dream-demo](https://github.com/spring-ai-community/spring-ai-agent-utils/tree/main/examples/memory/memory-tools-dream-demo) — combines `AutoMemoryToolsAdvisor`, `AutoDreamAdvisor`, and `SessionMemoryAdvisor` in one runnable example.

## See Also

- [AutoDreamService](AutoDreamService.md) — the service this advisor schedules; dream cycle mechanics, `DreamTrigger`/`DreamMode`, cross-session recall
- [AutoMemoryToolsAdvisor](AutoMemoryToolsAdvisor.md) — the live-turn counterpart this advisor complements
- [AutoMemoryTools](AutoMemoryTools.md) — the memory store both advisors operate on
