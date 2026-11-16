# Migration Guide: 0.13.0 to 0.14.0

This release moves to **Spring AI 2.1**, whose session module replaces the standalone `spring-ai-session` project. If you don't use `AutoDreamService` cross-session recall or session advisors, the dependency bump is the only change. A few behavioral fixes in the file and web tools are listed at the end.

## Dependency Version

```xml
<dependency>
    <groupId>org.springaicommunity</groupId>
    <artifactId>spring-ai-agent-utils</artifactId>
    <version>0.14.0</version>
</dependency>
```

Spring AI moves from 2.0.1 to **2.1**, which is built on Spring Boot **4.2** (Spring Framework 7.1). Align your Spring AI BOM (or `spring-ai.version`) with it, and move to Spring Boot 4.2 as well; the examples now use it.

## Sessions: `org.springaicommunity:spring-ai-session` → Spring AI

The standalone `spring-ai-session` project is now part of Spring AI. The Java package stays `org.springframework.ai.session`, but the Maven coordinates change, and the version is managed by the Spring AI BOM:

**Before (0.13.0):**

```xml
<dependency>
    <groupId>org.springaicommunity</groupId>
    <artifactId>spring-ai-session</artifactId>
    <version>0.10.0</version>
</dependency>
```

**After (0.14.0):**

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-session</artifactId>
</dependency>
<!-- Only if you use the session advisors (SessionAdvisors, SessionMemoryAdvisor) -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-session-advisor</artifactId>
</dependency>
```

Remove the `org.springaicommunity:spring-ai-session` dependency; having both on the classpath puts two copies of the same packages there.

The session API changed on the way into Spring AI. The changes that affect code written against 0.10.0:

| spring-ai-session 0.10.0 | Spring AI 2.1 |
|---|---|
| `CreateSessionRequest.builder().userId(id)` | `.metadata(SessionAttributes.PRINCIPAL_NAME, id)` |
| `SessionService.findByUserId(id)` | `findByMetadata(SessionAttributes.PRINCIPAL_NAME, id)` |
| `SessionMemoryAdvisor.builder(svc).defaultUserId(id)` | advisor param `SessionMemoryAdvisor.SESSION_METADATA_CONTEXT_KEY` with `Map.of(SessionAttributes.PRINCIPAL_NAME, id)` |
| `SessionMemoryAdvisor.builder(svc).compactionTrigger(t).compactionStrategy(s)` | `SessionAdvisors.builder(svc).compaction(t, s).build()` |
| `SlidingWindowCompactionStrategy.builder().maxEvents(n)` | `WindowCompactionStrategy.builder().lastEvents(n)` |
| `.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, id))` | `.advisors(SessionAdvisors.session(id))` (same context key; `ChatMemory` itself is deprecated) |
| `org.springframework.ai.session.tool.CrossSessionRecallTools` | `org.springaicommunity.agent.tools.CrossSessionRecallTools` (see below) |

See the Spring AI [session documentation](https://docs.spring.io/spring-ai/reference/api/session.html) for the rest of the module.

## Chat memory → session memory

Spring AI 2.1 deprecates `ChatMemory`, `MessageWindowChatMemory` and `MessageChatMemoryAdvisor` for removal in 3.0, in favor of session memory. They still work in 2.1, but the docs and examples here now use the session advisors:

**Before (0.13.0):**

```java
chatClientBuilder
    .defaultAdvisors(MessageChatMemoryAdvisor.builder(
        MessageWindowChatMemory.builder().maxMessages(500).build()).build());

chatClient.prompt(input)
    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "session-1"))
    .call();
```

**After (0.14.0):**

```java
SessionService sessionService = DefaultSessionService.builder()
    .sessionRepository(InMemorySessionRepository.builder().build())
    .build();

chatClientBuilder
    .defaultAdvisors(SessionAdvisors.builder(sessionService)
        .memory(memory -> memory.eventFilter(EventFilter.lastN(500)))  // optional window
        .build());

chatClient.prompt(input)
    .advisors(SessionAdvisors.session("session-1"))
    .call();
```

This needs `org.springframework.ai:spring-ai-session-advisor` (it brings in `spring-ai-session`). **Every request must name its session**: `MessageChatMemoryAdvisor` fell back to a default conversation when no `CONVERSATION_ID` was set, but the session advisors fail the call with `IllegalStateException: No session ID found in advisor context`. Add `.advisors(SessionAdvisors.session(id))` to each `prompt(...)`, including calls that never set a conversation id before. `EventFilter.lastN(n)` replays the last `n` events, extended to the start of their turn, so a turn is never cut; for long sessions prefer compaction, `SessionAdvisors.builder(svc).compaction(trigger, strategy)`. The session advisors store user, assistant and tool messages, never the system prompt: keep supplying it with `defaultSystem(...)`.

## Auto-Dream cross-session recall

`AutoDreamService.runDreamCycle(memoriesRootDirectory, userId)` and `AutoDreamAdvisor.userId(...)` keep their signatures. What changes is **how a user's sessions are found**: Spring AI sessions have no user id, so the Dreamer now searches the sessions whose `SessionAttributes.PRINCIPAL_NAME` metadata equals `userId`. Sessions created without that metadata are no longer searched. Tag them when you create them:

```java
sessionService.create(CreateSessionRequest.builder()
    .metadata(SessionAttributes.PRINCIPAL_NAME, userId)
    .build());
```

or, for sessions the session advisors create on first use:

```java
chatClientBuilder
    .defaultAdvisors(SessionAdvisors.builder(sessionService).build())
    .defaultAdvisors(a -> a.param(SessionMemoryAdvisor.SESSION_METADATA_CONTEXT_KEY,
            Map.of(SessionAttributes.PRINCIPAL_NAME, userId)));
```

Sessions already stored by `spring-ai-session` 0.10.0 with a `userId` are not found until they carry the principal metadata. With the JDBC repository, the Spring AI 2.1 schema also differs from 0.10.0: `AI_SESSION` has no `user_id` column, metadata is indexed in a new `AI_SESSION_INDEX` table, and events have an `event_type` column. Plan a data migration if you need to keep that history.

### `CrossSessionRecallTools` moved into agent-utils

The `cross_session_search` tool did not make it into Spring AI, so it now lives here, rebuilt on Spring AI's keyword search (`SessionService.searchEvents`):

**Before (0.13.0):**

```java
import org.springframework.ai.session.tool.CrossSessionRecallTools;

CrossSessionRecallTools.builder(sessionService, userId).build();
```

**After (0.14.0):**

```java
import org.springaicommunity.agent.tools.CrossSessionRecallTools;

CrossSessionRecallTools.builder(sessionService, principalName)   // matched against PRINCIPAL_NAME
    .pageSize(10)
    .maxTextLength(1000)
    .build();
```

The tool name and its `query`, `matchMode`, `since` and `page` parameters are unchanged; the `innerThought` parameter is gone. A page with matches is now a JSON object, `{"results": [...], "nextPage": n}`, instead of a bare array (no matches is still the plain text `No results found.`, or `No more results.` past the last page), each result keeps `sessionId`, `timestamp`, `type` and `text`, and a long text is cut to an excerpt around the keyword. Invalid input (no keyword, an unknown `matchMode`, a malformed `since`) is answered with an `Error: ...` text instead of an exception. Paging stops after the first 1000 matches.

## Behavioral changes

- **`AutoMemoryToolsAdvisor` default order.** It moves from `HIGHEST_PRECEDENCE + 200` to `+250`, so it no longer ties with the `SessionTurnAdvisor` that `SessionAdvisors` registers at `+200`; it still runs before the tool-calling advisor (`+300`). If you placed your own advisors relative to `+200`, check their order, or pin the old value with `.order(BaseAdvisor.HIGHEST_PRECEDENCE + 200)`.
- **`FileSystemTools` relative paths.** `Read`, `Write` and `Edit` now resolve a relative `filePath` against the configured working directory, as Grep, Glob and ListDirectory already did. `FileSystemTools.builder().workspace(ws)` now sets the working directory to the workspace root as well as confining access to it; `workingDirectory(...)` sets it explicitly. Without either, relative paths still resolve against the JVM working directory.
- **`Edit` rejects an empty `old_string`.** It now returns `Error: old_string must not be empty` and leaves the file unchanged.
- **`SmartWebFetchTool` error text.** When retries are exhausted, the message now ends with the cause of the last attempt, for example `Failed after 3 attempts: Server error: HTTP 503`, and the failure is logged at `WARN` instead of `ERROR`.
- **Grep and Glob.** The `type` filter now works (it never matched before), `offset` without `head_limit` returns the remaining matches instead of nothing, and ignored directories (`node_modules`, `target`, `.git`, ...) are also skipped on Windows.
