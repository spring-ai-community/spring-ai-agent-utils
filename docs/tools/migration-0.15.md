# Migration Guide: 0.14.0 to 0.15.0

This release gives sub-agents memory, on top of the Spring AI session module. It is opt-in: if you don't turn it on, the dependency bump is the only required change. Two behavioral changes are listed at the end.

## Dependency Version

```xml
<dependency>
    <groupId>org.springaicommunity</groupId>
    <artifactId>spring-ai-agent-utils</artifactId>
    <version>0.15.0</version>
</dependency>
```

## Sub-agent memory

New and opt-in: `TaskTool.builder().sessionService(...)` and `ClaudeSubagentType.builder().sessionService(...)` give sub-agents memory. The calling agent's session advisors put its session ID into the tool context (`SessionAdvisors.builder(svc).sessionIdInToolContext(true)`), and each `Task` call then runs on a child session of it: the exchange is recorded on the caller's session as `SUBAGENT.*` events, the sub-agent's transcript is kept in the child, and the result starts with `agent_id: <child session id>`, the ID the model passes as `resume`. Without the service, or without the session in the tool context, nothing changes: sub-agents keep nothing, as in 0.14. It needs `spring-ai-session-advisor` for the Claude sub-agents. `ClaudeSubagentType.builder().sessionAdvisors(...)` takes session advisors of your choice in place of the default ones, for compaction or the turn advisors of spring-ai-session-extensions. See [Sub-Agent Memory](SubagentMemory.md).

### `SubagentExecutor` gains a default method

`SubagentExecutor.execute(TaskCall, SubagentDefinition, TaskContext)` and `SubagentExecutor.keepsSessions()` are new, with defaults that call the existing two-argument method and return `false`, so existing executors need no change and run without memory. `TaskContext` carries the sub-agent's session ID (when memory is on) and the caller's tool context. An executor that can keep a transcript in that session overrides both; `ClaudeSubagentExecutor` does. Without a session, `ClaudeSubagentExecutor`'s three-argument method calls its two-argument one, so a subclass that overrides that one keeps working.

`TaskTool.TaskFunction` is now a `BiFunction<TaskCall, ToolContext, String>`, since it reads the caller's session from the tool context. Only code that calls it directly, for example in tests, must pass a `ToolContext` (or `null`).

## Behavioral changes

- **A Claude sub-agent whose tool call needs confirmation says so.** When a tool call of a sub-agent needs a person's confirmation (`ToolCallConfirmationPolicy`), the task ends there. The `Task` result used to be the paused reply's text, usually empty; it now names the tools that did not run. See [A tool call that needs confirmation](SubagentMemory.md#a-tool-call-that-needs-confirmation).
- **`DefaultTaskRepository` caps concurrent background tasks.** `new DefaultTaskRepository()` used to run background tasks on an unbounded cached thread pool. It now runs at most **25** at once (`DefaultTaskRepository.DEFAULT_MAX_CONCURRENT_TASKS`); later tasks wait for a free slot, and idle threads end after a minute as before. `new DefaultTaskRepository(int maxConcurrentTasks)` sets the cap; the constructors that take an `ExecutorService` are unchanged.
