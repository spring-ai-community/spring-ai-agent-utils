# TodoWriteTool

A structured task list management tool for AI agents. Helps track progress, organize complex tasks, and provide visibility into task execution.

Reference: [Claude Code Todo Tracking](https://platform.claude.com/docs/en/agent-sdk/todo-tracking)

## Quick Start

```java
ChatClient chatClient = chatClientBuilder
    .defaultTools(TodoWriteTool.builder().build())
    .defaultAdvisors(
        ToolCallAdvisor.builder().conversationHistoryEnabled(false).build(),
        MessageChatMemoryAdvisor.builder(MessageWindowChatMemory.builder().build()).build())
    .build();
```

> **Note:** Requires [Chat Memory](https://docs.spring.io/spring-ai/reference/api/chat-memory.html) and [ToolCallAdvisor](https://docs.spring.io/spring-ai/reference/api/advisors-recursive.html#_toolcalladvisor). For best results, use a system prompt like [MAIN_AGENT_SYSTEM_PROMPT_V2](https://github.com/spring-ai-community/spring-ai-agent-utils/blob/main/spring-ai-agent-utils/src/main/resources/prompt/MAIN_AGENT_SYSTEM_PROMPT_V2.md#task-management).

## Task Structure

Each task has three fields:

| Field | Description | Example |
|-------|-------------|---------|
| `content` | What needs to be done (imperative) | "Run tests" |
| `activeForm` | What is happening (continuous) | "Running tests" |
| `status` | Current state | `pending`, `in_progress`, `completed` |

**Critical Rule:** Exactly ONE task can be `in_progress` at any time.

## When to Use

**Use when:**
- Task requires 3+ distinct steps
- Multiple files or operations involved
- User provides a list of tasks

**Skip when:**
- Single straightforward task
- Less than 3 trivial steps
- Purely informational query

## Example Output

```
Progress: 2/4 tasks completed (50%)
[✓] Find top 10 Tom Hanks movies
[✓] Group movies in pairs
[→] Print inverted titles
[ ] Final summary
```

## Custom Event Handler

```java
TodoWriteTool.builder()
    .todoEventHandler(event -> {
        // Handle todo updates (e.g., publish events, update UI)
        applicationEventPublisher.publishEvent(new TodoUpdateEvent(this, event.todos()));
    })
    .build();
```

## Best Practices

1. **One in progress** - Always exactly one task `in_progress`
2. **Update immediately** - Mark completed right after finishing
3. **Complete fully** - Only mark completed when 100% done
4. **Handle blockers** - Add new tasks for discovered issues

## See Also

- [todo-demo](https://github.com/spring-ai-community/spring-ai-agent-utils/tree/main/examples/todo-demo) - Example project
- [Blog Post](https://spring.io/blog/2026/01/20/spring-ai-agentic-patterns-3-todowrite) - Detailed explanation
