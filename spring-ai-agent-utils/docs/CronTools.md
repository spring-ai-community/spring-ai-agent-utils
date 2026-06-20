# CronTools

Schedule prompts to be enqueued at future times using standard 5-field cron expressions. Supports recurring and one-shot tasks, durable (file-persisted) and session-only tasks.

Reference: [Claude Code Cron](https://code.claude.com/docs/en/settings#tools-available-to-claude)

## Quick Start

```java
ChatClient chatClient = chatClientBuilder
    .defaultTools(CronTools.builder().build())
    .defaultAdvisors(
        ToolCallAdvisor.builder().conversationHistoryEnabled(false).build(),
        MessageChatMemoryAdvisor.builder(MessageWindowChatMemory.builder().build()).build())
    .build();
```

## Tools

### CronCreate

Schedule a new cron job. Returns a job ID for use with `CronDelete`.

```
@Tool(name = "CronCreate")
```

| Parameter | Required | Default | Description |
|-----------|----------|---------|-------------|
| `cron` | yes | — | Standard 5-field cron: `M H DoM Mon DoW` |
| `prompt` | yes | — | The prompt to enqueue at each fire time |
| `recurring` | no | `true` | `true` = fire on every match until deleted; `false` = fire once then auto-delete |
| `durable` | no | `false` | `true` = persist to file and survive restarts |

**Cron expression format:** `minute hour day-of-month month day-of-week`

Examples:
- `*/5 * * * *` — Every 5 minutes
- `0 9 * * *` — Daily at 9:00 AM
- `0 9 * * 1-5` — Weekdays at 9:00 AM
- `30 14 28 2 *` — Feb 28 at 2:30 PM (one-shot)

### CronDelete

Cancel a previously scheduled cron job.

```
@Tool(name = "CronDelete")
```

| Parameter | Required | Description |
|-----------|----------|-------------|
| `id` | yes | Job ID returned by `CronCreate` |

### CronList

List all scheduled cron jobs (both durable and session-only).

```
@Tool(name = "CronList")
```

No parameters required.

## Task Types

| Type | Storage | Lifecycle |
|------|---------|-----------|
| Session-only | In-memory | Dies when process exits |
| Durable | `.claude/scheduled_tasks.json` (NDJSON) | Survives restarts; recurring auto-expires after 7 days |

## Durable Persistence

```java
// With durable persistence enabled
CronTools cronTools = CronTools.builder()
    .durableTasksFile(Path.of(".claude/scheduled_tasks.json"))
    .build();
```

Durable tasks are persisted in NDJSON format (one JSON object per line):

```jsonl
{"id":"abc123","cron":"0 9 * * 1-5","prompt":"standup reminder","recurring":true,"durable":true,"nextFireTime":"2026-06-01T09:00:00Z","createdAt":"2026-05-31T00:00:00Z"}
```

On startup, durable tasks are restored from the file:
- **One-shot tasks** whose fire time has passed fire immediately (catch-up).
- **Recurring tasks** get their next future fire time calculated from now.

## Custom Event Handler

By default, fired tasks are logged. Provide a custom handler to enqueue the prompt back into a `ChatClient` or publish events:

```java
CronTools cronTools = CronTools.builder()
    .eventHandler(task -> {
        // Feed the prompt back into the agent
        String response = chatClient.prompt(task.prompt()).call().content();
        return response;
    })
    .build();
```

## Jitter

To avoid thundering-herd effects, recurring tasks whose minute field is exactly `0` or `30` receive a small random offset (±2 minutes). Interval-based schedules (`*/5`) and schedules with other minute values are not jittered.

## Architecture

```
CronTools (@Tool methods)
  ├── CronScheduler (single-threaded scheduling engine)
  │     └── PriorityBlockingQueue<CronTask> (ordered by nextFireTime)
  ├── CronTaskStore (NDJSON file persistence)
  └── CronEventHandler (fire-time callback)
```

The scheduler runs as a single daemon thread that:
1. Peeks at the earliest upcoming task
2. Sleeps until its fire time (with `Object.wait()`)
3. Fires the task via the event handler
4. Re-enqueues recurring tasks with their next fire time

New tasks and cancellations wake the scheduler via `Object.notify()`.

## Best Practices

1. **Default to session-only** — Most "remind me in 5 minutes" tasks don't need durability
2. **Avoid :00 and :30** — Pick off-peak minutes (e.g., `3` or `57`) to spread load
3. **One-shot for reminders** — Use `recurring: false` for one-time reminders
4. **Durable for persistence** — Only use `durable: true` when explicitly asked

## See Also

- [Claude Code Cron Documentation](https://code.claude.com/docs/en/settings#tools-available-to-claude)
- [Linux Crontab(5)](https://man7.org/linux/man-pages/man5/crontab.5.html)
