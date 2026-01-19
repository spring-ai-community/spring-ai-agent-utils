# TodoWrite Demo

Demonstrates the `TodoWriteTool` for structured task management in Spring AI agents. The tool enables LLMs to create, track, and update task lists during execution, transforming implicit planning into explicit, observable workflows.

## What It Shows

- **Task tracking**: Agent decomposes complex requests into trackable todo items
- **Progress states**: Tasks transition through `pending` → `in_progress` → `completed`
- **Event-driven updates**: Real-time progress display via Spring application events
- **Single-task focus**: Only one task can be `in_progress` at a time

## Running the Demo

```bash
# Set API key for your chosen model provider
export GOOGLE_CLOUD_PROJECT=your-project-id
# or: export ANTHROPIC_API_KEY=your-key
# or: export OPENAI_API_KEY=your-key

# Optional: Enable web search
export BRAVE_API_KEY=your-brave-key

# Run
./mvnw spring-boot:run -pl examples/todo-demo
```

## Example Run

**Prompt:**
```
Find the top 10 Tom Hanks movies, then group them in groups of 2,
and finally print the title name inverted (e.g. last char first).
Use TodoWrite to organize your tasks.
```

**Progress output:**
```
Progress: 0/2 tasks completed (0%)
  [→] Find the top 10 Tom Hanks movies using WebSearch
  [ ] Group movies into pairs, reverse titles, and print the result

Progress: 1/2 tasks completed (50%)
  [✓] Find the top 10 Tom Hanks movies using WebSearch
  [→] Group movies into pairs, reverse titles, and print the result
```

**Result:**
```
Group 1
  pmuG tserroF (Forrest Gump)
  nayR etavirP gnivaS (Saving Private Ryan)

Group 2
  eliM neerG ehT (The Green Mile)
  yrotS yoT (Toy Story)

Group 3
  yawA tsaC (Cast Away)
  31 ollopA (Apollo 13)
...
```

## Key Components

| Component | Purpose |
|-----------|---------|
| `TodoWriteTool` | Spring AI tool for task list management |
| `TodoUpdateEvent` | Application event published on todo changes |
| `TodoProgressListener` | Listens to events and displays progress |

## Related Resources

- [TodoWriteTool.java](../../spring-ai-agent-utils/src/main/java/org/springaicommunity/agent/tools/TodoWriteTool.java)
- [Task Tools Documentation](../../spring-ai-agent-utils/docs/TaskTools.md)
- [Blog Post: Spring AI Agentic Patterns - TodoWrite](https://spring.io/blog/2026/01/20/spring-ai-agentic-patterns-3-todowrite)
