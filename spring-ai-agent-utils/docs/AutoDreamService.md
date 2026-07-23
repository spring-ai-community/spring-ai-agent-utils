# AutoDreamService

An out-of-band memory-consolidation ("dream") capability for [`AutoMemoryTools`](AutoMemoryTools.md). A dedicated background subagent — the **Dreamer** — periodically reviews an agent's entire memory store (and, optionally, its owner's full conversation history) to deduplicate, prune, merge, and reorganize memories, without ever blocking or adding latency to the live agent's turn.

## In-band nudge vs. out-of-band dreaming

[`AutoMemoryToolsAdvisor`](AutoMemoryToolsAdvisor.md) already ships a `memoryConsolidationTrigger` — a cheap, stateless nudge appended to the *live* agent's *current* turn, asking it to tidy up whatever memory is already in its context. `AutoDreamService` (with [`AutoDreamAdvisor`](AutoDreamAdvisor.md)) is a different, complementary mechanism:

| | `memoryConsolidationTrigger` | Auto-Dream |
|---|---|---|
| Where it runs | In-band, the live agent, same turn | Out-of-band, a dedicated Dreamer subagent, background |
| Blocks the user | Adds to the current turn's latency/tokens | Never — fires after the response is returned |
| What it sees | Whatever's already in the live agent's context this turn | The entire memory store, and (optionally) the user's full cross-session history |
| State | Stateless approximation, re-evaluated per call | Persisted `DreamState` — real elapsed-time/turn-count conditions survive restarts |
| Cost | Cheap, frequent, shallow | Heavier, periodic, deep |

Use both together: `memoryConsolidationTrigger` for cheap, frequent tidy-nudges; Auto-Dream as the periodic deep-clean layer.

## Quick Start

```java
AutoDreamService dreamService = AutoDreamService.builder(chatClientBuilder.clone())
    .build();

DreamResult result = dreamService.runDreamCycle("/path/to/memories");
System.out.println(result.status() + ": " + result.summary());
```

!!! warning "Always clone the builder"
    Pass a **cloned, unconfigured** `ChatClient.Builder` — one that has not had the main agent's system prompt or tools chained onto it. `AutoDreamService` builds its own `ChatClient` for the Dreamer, scoped to exactly the tools it's allowed to use. If you pass the main agent's already-configured builder, the Dreamer inherits its tools too.

In practice, pair it with [`AutoDreamAdvisor`](AutoDreamAdvisor.md) so dream cycles fire automatically instead of only on demand:

```java
ChatClient chatClient = ChatClient.builder(chatModel)
    .defaultAdvisors(
        AutoMemoryToolsAdvisor.builder().memoriesRootDirectory(memoryDir).build(),
        AutoDreamAdvisor.builder()
            .memoriesRootDirectory(memoryDir)
            .dreamService(dreamService)
            .dreamTrigger(DreamTriggers.hoursAndSessions(24, 5))
            .build())
    .build();
```

## The Dream Cycle

Each call to `runDreamCycle(...)`:

1. **Acquires a lock** (`.dream.lock` in the memories directory) so two cycles never run concurrently against the same store. A lock older than `staleLockAfter` (default 15 minutes) is treated as abandoned — e.g. from a crashed cycle — and reclaimed.
2. **Builds a dedicated `ChatClient`** for the Dreamer, scoped to `AutoMemoryTools` (and, optionally, a read-only cross-session search tool — see below) — never the caller's own tools.
3. **Runs the four-phase pipeline** described in the Dreamer's system prompt:
    1. **Orient** — reads `MEMORY.md` and the memory directory to see the current shape of the store.
    2. **Gather signal** — reads through memory files (and, if available, searches cross-session history) for duplicates, contradictions, staleness, and dangling `MEMORY.md` links.
    3. **Consolidate** — merges duplicates, deletes confirmed-stale/contradicted entries (biased toward merge-and-flag over delete, since this runs unsupervised), normalizes dates.
    4. **Prune & reindex** — fixes dangling links, keeps `MEMORY.md` under 200 lines.
4. **Persists `DreamState`** — records when the cycle ran, its outcome, and a summary — and releases the lock.

A model failure during the cycle is captured as a failed `DreamResult` rather than propagated as an exception.

## Builder Configuration

```java
AutoDreamService dreamService = AutoDreamService.builder(chatClientBuilder)
    .dreamMode(DreamMode.AUTO_APPLY)                 // optional, default AUTO_APPLY
    .dreamSystemPrompt(customPromptResource)          // optional
    .staleLockAfter(Duration.ofMinutes(15))           // optional, default 15 minutes
    .sessionService(sessionService)                   // optional — enables cross-session recall
    .build();
```

| Builder method | Type | Default | Description |
|---|---|---|---|
| `builder(ChatClient.Builder)` | `ChatClient.Builder` | — (**required**) | Base builder the Dreamer's `ChatClient` is cloned from. Must not already carry the main agent's system prompt/tools. |
| `dreamMode(DreamMode)` | `DreamMode` | `AUTO_APPLY` | `AUTO_APPLY` edits memory files in place. `PROPOSE` edits a timestamped copy instead — see [Dream Modes](#dream-modes). |
| `dreamSystemPrompt(Resource)` | `Resource` | `classpath:/prompt/AUTO_DREAM_SYSTEM_PROMPT.md` | Override the Dreamer's operating instructions. |
| `staleLockAfter(Duration)` | `Duration` | `15 minutes` | Age at which an unreleased `.dream.lock` is treated as abandoned and reclaimed. |
| `sessionService(SessionService)` | `SessionService` | none | Optional. See [Cross-Session Recall](#cross-session-recall-optional). |

### Running a cycle

```java
// Memory-only
DreamResult result = dreamService.runDreamCycle(memoriesRootDirectory);

// With cross-session recall (requires sessionService(...) configured on the builder)
DreamResult result = dreamService.runDreamCycle(memoriesRootDirectory, userId);
```

`DreamResult` is a small record: `status()` is `"completed"`, `"failed"`, or `"skipped"` (already locked), and `summary()` is the Dreamer's own account of what it changed.

## Dream Triggers

`DreamTrigger` decides whether a background dream cycle should fire, evaluated against **persisted** `DreamState` rather than in-memory counters — so the decision survives process restarts, unlike a stateless `BiPredicate`.

```java
public interface DreamTrigger {
    boolean shouldDream(DreamState state, Instant now);
}
```

`DreamTriggers` provides two factories:

| Factory | Behavior |
|---|---|
| `DreamTriggers.hoursAndSessions(hours, sessions)` | Fires only once **both** conditions hold: at least `hours` elapsed since the last dream, and at least `sessions` conversation turns processed since then. A store that has never been dreamed on satisfies the time condition immediately. |
| `DreamTriggers.manualOnly()` | Never fires automatically — dream cycles only run when `runDreamCycle(...)` is called explicitly. |

Write a custom trigger for anything else — it's a single-method functional interface.

## Dream Modes

| Mode | Behavior | When to use |
|---|---|---|
| `AUTO_APPLY` (default) | The Dreamer edits memory files directly. | Single-user/single-agent memory store — the common case. |
| `PROPOSE` | The entire memory tree (minus dream bookkeeping files) is copied into `<memoriesDir>/.dream-proposals/<timestamp>/` first, and the Dreamer edits the copy instead. The live memory files are left untouched. | Shared/multi-agent memory stores where blind auto-merge is riskier — review the proposal, then apply changes manually via the ordinary `Memory*` tools if you agree with them. |

`PROPOSE` mode produces a plain directory copy — no diff is generated. Compare it against the live directory yourself (e.g. `diff -r`), or read the Dreamer's summary in the returned `DreamResult`.

## Cross-Session Recall (optional)

When [`spring-ai-session`](https://github.com/spring-ai-community/spring-ai-session) is on the classpath and `sessionService(...)` is configured, `runDreamCycle(memoriesRootDirectory, userId)` additionally gives the Dreamer a **read-only** `cross_session_search` tool (spring-ai-session's `CrossSessionRecallTools`), scoped to that one user's entire session history — not just the memory store.

```java
AutoDreamService dreamService = AutoDreamService.builder(chatClientBuilder.clone())
    .sessionService(sessionService)   // any SessionService — in-memory, JDBC, etc.
    .build();

dreamService.runDreamCycle(memoriesRootDirectory, "alice");
```

Both conditions must hold for cross-session recall to activate: `sessionService(...)` configured on the builder, **and** a non-empty `userId` passed to `runDreamCycle(...)`. Omit either and the Dreamer falls back to memory-only dreaming — no error, no behavior change from the base case.

!!! note "Optional dependency"
    `spring-ai-agent-utils` declares `spring-ai-session` as `provided` + `optional`. Consumers who never call `.sessionService(...)` don't need it on their own classpath — it's never pulled in transitively. Add it directly to your own project's dependencies to use this feature.

The Dreamer's kickoff prompt automatically tells it when `cross_session_search` is available and scopes its queries with `since = <last dream's timestamp>`, so each cycle only mines history since the previous one. See spring-ai-session's [Cross-Session Recall](https://spring-ai-community.github.io/spring-ai-session/session-management/cross-session-recall/) docs for the tool itself.

## System Prompt

The default companion prompt is bundled at `classpath:/prompt/AUTO_DREAM_SYSTEM_PROMPT.md`. It encodes the four-phase pipeline described above, and — when cross-session recall is available — instructs the Dreamer to run a handful of *targeted* queries (not an exhaustive history read) for three signal categories: corrections ("no,", "don't", "actually", "instead"), explicit save requests ("remember that", "keep in mind"), and decisions ("we decided", "let's go with").

Provide your own via `.dreamSystemPrompt(customResource)` if you need different phases or tone.

## Demo Application

See [memory-tools-dream-demo](https://github.com/spring-ai-community/spring-ai-agent-utils/tree/main/examples/memory/memory-tools-dream-demo) for a complete runnable example: seeded overlapping memories and a seeded past session (decision + correction) give both the memory-only and cross-session-recall phases of a dream cycle real signal to find, with both an automatic every-3rd-turn trigger and an on-demand `/dream` REPL command.

## See Also

- [AutoDreamAdvisor](AutoDreamAdvisor.md) — the `ChatClient` advisor that schedules dream cycles automatically
- [AutoMemoryTools](AutoMemoryTools.md) — the memory store the Dreamer consolidates
- [AutoMemoryToolsAdvisor](AutoMemoryToolsAdvisor.md) — `memoryConsolidationTrigger`, the in-band complement to Auto-Dream
- [TaskTools](TaskTools.md) — the background-execution infrastructure Auto-Dream reuses for async cycles
