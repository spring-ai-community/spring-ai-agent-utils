# memory-tools-dream-demo

A runnable Spring Boot console agent demonstrating [`AutoDreamAdvisor` / `AutoDreamService`](../../../spring-ai-agent-utils/docs/design/AutoDream-Design.md) — **both Phase 1 and Phase 2 of Auto-Dream** — running an out-of-band memory-consolidation cycle on top of [`AutoMemoryToolsAdvisor`](../../../spring-ai-agent-utils/docs/AutoMemoryToolsAdvisor.md), with cross-session recall backed by `spring-ai-session`.

## What it demonstrates

- **Seeded, deliberately messy memory (Phase 1 signal)** — on first run the memory directory is seeded with two overlapping `feedback` memories (same fact, worded differently) and one dangling `MEMORY.md` link (points at a file that doesn't exist), so the first dream cycle has real signal to consolidate.
- **Seeded past session (Phase 2 signal)** — on every run (the in-memory session store doesn't persist), a past session is seeded for user `alice` containing a "decision" ("we decided to use PostgreSQL...") and a "correction" ("actually, let's go with blue-green instead of canary...") timestamped two days ago — exactly the kind of signal the Dreamer's `cross_session_search` tool is instructed to look for.
- **`AutoDreamAdvisor` firing automatically** — wired with a demo trigger that fires every 3 conversation turns (`state.sessionsSinceLastDream() >= 3`), independent of the production-realistic `DreamTriggers.hoursAndSessions(24, 5)` default, and with `.userId("alice")` so triggered cycles get cross-session recall too. Watch the console for a `Dream cycle [...] finished with status=...` log line appearing **on its own, in the background**, while you keep chatting — that's the point: dreaming never blocks the conversation.
- **`AutoDreamService` on demand** — type `/dream` in the REPL to run a synchronous dream cycle immediately (via `runDreamCycle(memoryDir, userId)`) and print its result, without waiting for the trigger.
- **A separate `ChatClient.Builder` for the Dreamer** — the demo clones the builder *before* the main agent's system prompt/tools are chained onto it, so the Dreamer only ever sees `AutoMemoryTools` + (when configured) `cross_session_search`, never the main agent's own tools.
- **`AutoMemoryToolsAdvisor`** — the live agent's own read/write long-term memory, unchanged from the other memory demos.
- **`SessionMemoryAdvisor`** — short-term conversation history for the live agent, backed by the *same* `SessionService` the Dreamer's `cross_session_search` reads from. This is what makes the two phases share one substrate: chat here, and it becomes material the next dream cycle can find. Bounded with `TurnCountTrigger(20)` + `SlidingWindowCompactionStrategy.maxEvents(10)` — the same combo used in spring-ai-session's own quickstart — so a long demo session can't grow the live prompt without limit. Compacted-out events are archived, not deleted, and stay searchable via `conversation_search`/`cross_session_search`.

## Advisor stack

```
AutoMemoryToolsAdvisor   (HIGHEST_PRECEDENCE + 200)  ← long-term memory (live agent)
AutoDreamAdvisor         (HIGHEST_PRECEDENCE + 150)  ← schedules background dream cycles, userId="alice"
SessionMemoryAdvisor     (HIGHEST_PRECEDENCE + 1000) ← short-term conversation window, backed by SessionService
MyLoggingAdvisor         (order = 0)                 ← dev console logger
```

## Key source files

| File | Purpose |
|---|---|
| [`Application.java`](src/main/java/org/springaicommunity/agent/Application.java) | Spring Boot entry point — seeds demo memories and a past session, builds the `ChatClient`, `SessionService`, and `AutoDreamService`, runs the console chat loop |
| [`MyLoggingAdvisor.java`](src/main/java/org/springaicommunity/agent/MyLoggingAdvisor.java) | Development advisor logging user messages, tool calls, and assistant responses |
| [`application.properties`](src/main/resources/application.properties) | Model credentials, model selection, memory directory path, and demo user ID |

## Running

```bash
cd examples/memory/memory-tools-dream-demo
ANTHROPIC_API_KEY=sk-... mvn spring-boot:run
```

The agent starts a REPL loop:

1. Say hello, mention a preference or two, chat for a few turns.
2. After the 3rd turn, a dream cycle fires automatically in the background — watch for the `AutoDreamAdvisor` log line reporting what it merged. With `cross_session_search` available, expect it to also surface the seeded decision/correction from the "past session," not just the two `feedback_testing_*.md` entries.
3. At any point, type `/dream` to force a dream cycle immediately and see its result printed synchronously.
4. Inspect `${user.home}/.spring-ai-agent/memory-tools-dream-demo/memory` on disk — after a dream cycle you should see the duplicate feedback entries merged, the dangling `MEMORY.md` link removed, and a `.dream-state.json` recording when the last cycle ran.

## Configuration

Memory is stored at `${user.home}/.spring-ai-agent/memory-tools-dream-demo/memory` by default, and the demo user ID defaults to `alice`. Change either in `application.properties`:

```properties
agent.memory.dir=/path/to/your/memories
agent.demo.user-id=alice
```

To try `DreamMode.PROPOSE` instead of the default `AUTO_APPLY` (edits redirected to a reviewable `.dream-proposals/<timestamp>/` copy rather than the live memory files), add `.dreamMode(DreamMode.PROPOSE)` to the `AutoDreamService.builder(...)` call in `Application.java`.

The `application.properties` contains commented-out sections for Anthropic and OpenAI SDK. To switch models, uncomment the relevant starter dependency in `pom.xml` and update the model property — no other Java changes required.

### Session store is in-memory

`InMemorySessionRepository` backs the demo's `SessionService` — the seeded past session (and anything you chat about) resets every time you restart the app. Swap in `spring-ai-session-jdbc` for a persistent store; nothing else in `Application.java` would need to change, since `AutoDreamService`/`SessionMemoryAdvisor` only depend on the `SessionService` interface.

## Resetting the demo

Delete the memory directory to re-seed the file-based memory store on the next run (the session store resets automatically on every restart, since it's in-memory):

```bash
rm -rf "${HOME}/.spring-ai-agent/memory-tools-dream-demo"
```
