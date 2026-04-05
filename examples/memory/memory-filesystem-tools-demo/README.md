# Memory Filesystem Tools Demo

Demonstrates **long-term memory** for AI agents using general-purpose `FileSystemTools` and `ShellTools` with Spring AI. Implements the same cross-conversation memory pattern as [memory-tools-demo](../memory-tools-demo) but without dedicated memory tools — the agent manages memory files itself using the same `Read`, `Write`, `Edit`, and `Bash` tools it would use for any other file operation.

## How This Differs from memory-tools-demo

Both demos implement the same long-term memory concept: typed memory files, a `MEMORY.md` index, and a two-step save workflow. The difference is in **which tools the agent uses**:

| | [memory-tools-demo](../../memory/memory-tools-demo) | **This demo** |
|---|---|---|
| **Tools** | Dedicated `MemoryTools` (6 purpose-built operations) | General-purpose `FileSystemTools` + `ShellTools` |
| **Path model** | Relative paths, sandboxed to the memories root | Absolute paths, full filesystem access |
| **Safety** | Built-in traversal guard, absolute path rejection | No sandbox — agent is trusted to stay in the memory dir |
| **Memory dir** | Configured in `MemoryTools.builder()` | Injected into the system prompt as `{MEMORIES_ROOT_DIERCTORY}` |
| **Inspiration** | [Claude API SDK Memory Tool](https://platform.claude.com/docs/en/agents-and-tools/tool-use/memory-tool) | [Claude Code auto memory](https://code.claude.com/docs/en/memory) |

**When to choose this approach:**
- The agent already has `FileSystemTools` / `ShellTools` for other tasks and you don't want to add a dependency on `MemoryTools`
- You want the agent to have full filesystem access alongside memory (e.g. read source files, then write memories about them)
- You're following the Claude Code pattern directly

**When to choose memory-tools-demo instead:**
- You want memory operations isolated in a sandbox with no filesystem side-effects
- You prefer explicit, purpose-named tool calls (`MemoryCreate`, `MemoryView`, …) over generic `Write` / `Read`

## Overview

The agent manages memory using the same `Read`, `Write`, and `Edit` operations it uses for any other file work. The system prompt (`MEMORY_FILESYSTEM_TOOLS_SYSTEM_PROMPT.md`) tells it where to store memories and how to structure them. No special memory tooling is required.

```
Session 1:                              Session 2 (new JVM process):
  User: "I prefer concise answers"        Agent reads MEMORY.md (via Read)
  Agent: Write → feedback_style.md   →    Agent: already knows your preference
         Edit  → MEMORY.md (index)         "Here's the short version..."
```

## Quick Start

### Prerequisites

- Java 17+
- Maven 3.6+
- An AI provider API key

### Setup

1. Add the memory directory property to `src/main/resources/application.properties`:
```properties
agent.memory.dir=${user.home}/.spring-ai-agent/memory-filesystem-tools-demo/memory
```

2. Set your API key (Anthropic is configured by default):
```bash
export ANTHROPIC_API_KEY=your-key-here
# Or Google: export GOOGLE_CLOUD_PROJECT=your-project-id
```

3. Run the demo:
```bash
mvn spring-boot:run
```

4. Start a conversation:
```
USER: My name is Alice. I'm a backend engineer and I prefer short answers.
USER: We're migrating from PostgreSQL to CockroachDB this quarter.
USER: exit
```

5. Run again — the agent recalls what it stored:
```
USER: What do you know about me?
ASSISTANT: You're Alice, a backend engineer. You prefer short answers.
           You're migrating from PostgreSQL to CockroachDB this quarter.
```

## Project Structure

```
memory-filesystem-tools-demo/
├── src/main/java/org/springaicommunity/skills/
│   ├── Application.java                          # Wires FileSystemTools + ShellTools + chat loop
│   └── MyLoggingAdvisor.java                     # Logs tool calls and responses to stdout
├── src/main/resources/
│   ├── application.properties                    # Model configuration
│   └── prompt/
│       └── MEMORY_FILESYSTEM_TOOLS_SYSTEM_PROMPT.md  # Memory behaviour instructions
└── target/
    └── memory/                                   # Memory files written here at runtime
        ├── MEMORY.md                             # Index of all memory entries
        └── user_profile.md                       # Example: user type memory
```

The `classpath:/prompt/MAIN_AGENT_SYSTEM_PROMPT_V2.md` prompt is loaded from the `spring-ai-agent-utils` dependency jar. `MEMORY_FILESYSTEM_TOOLS_SYSTEM_PROMPT.md` lives locally in this demo.

## How It Works

### Application Wiring

```java
ChatClient chatClient = chatClientBuilder
    .defaultSystem(p -> p
        .text(mainPrompt + "\n\n" + memoryToolsPrompt)
        .param("MEMORIES_ROOT_DIERCTORY", memoryDir))   // tells the agent where to write
    .defaultTools(
        ShellTools.builder().build(),          // Bash — for ls, mkdir, etc.
        FileSystemTools.builder().build())     // Read, Write, Edit — memory file operations
    .defaultAdvisors(
        ToolCallAdvisor.builder().build(),
        MyLoggingAdvisor.builder().build())
    .build();
```

The `{MEMORIES_ROOT_DIERCTORY}` placeholder is injected into the system prompt so the agent knows the absolute path to use in every `Write` / `Read` / `Edit` call. Unlike `memory-tools-demo`, there is no sandbox enforcing this — the agent follows the instruction by convention.

### Memory Lifecycle

When the agent decides to save a memory it performs two file operations:

**Step 1 — `Write`**: creates a typed `.md` file with YAML frontmatter:
```markdown
---
name: user profile
description: Alice — backend engineer, prefers short answers
type: user
---

Backend engineer named Alice.
Prefers concise, direct responses without trailing summaries.
```

**Step 2 — `Edit`**: appends a pointer line to `MEMORY.md` (or `Write` if it doesn't yet exist):
```markdown
- [User Profile](user_profile.md) — Alice, backend engineer, prefers short answers
```

On the next session the agent calls `Read` on `MEMORY.md` to load the index, then `Read` on any relevant memory file.

### Memory Types

| Type | Saved when | Example |
|---|---|---|
| `user` | User shares background, goals, or preferences | name, role, communication style |
| `feedback` | User corrects the agent or confirms an approach | "stop summarizing at the end" |
| `project` | Project decisions, deadlines, constraints | migration target, freeze dates |
| `reference` | Pointers to external systems | Linear board, Grafana dashboard |

### System Prompt Role

All memory behaviour — when to save, how to structure files, how to maintain `MEMORY.md`, what not to save — is driven entirely by `MEMORY_FILESYSTEM_TOOLS_SYSTEM_PROMPT.md`. There are no dedicated tool semantics to fall back on. The prompt is the only guardrail.

## Configuration

```properties
# application.properties

## AI provider (Anthropic shown; see "Switching AI Providers" for others)
spring.ai.anthropic.api-key=${ANTHROPIC_API_KEY}
spring.ai.anthropic.chat.options.model=claude-sonnet-4-5-20250929

## Agent metadata shown to the model in the system prompt
agent.model=claude-sonnet-4-5-20250929
agent.model.knowledge.cutoff=2025-01-01

## Memory directory — must be set; persists across restarts
agent.memory.dir=${user.home}/.spring-ai-agent/memory-filesystem-tools-demo/memory
```

> **Note:** `agent.memory.dir` has no default and must be explicitly set. The application will fail to start without it.

## Switching AI Providers

Edit `pom.xml` to uncomment the desired starter and update `application.properties`:

**Google Gemini:**
```properties
spring.ai.google.genai.project-id=${GOOGLE_CLOUD_PROJECT}
spring.ai.google.genai.chat.options.model=gemini-3.1-pro-preview
agent.model=gemini-3.1-pro-preview
agent.model.knowledge.cutoff=Unknown
```

**OpenAI:**
```properties
spring.ai.openai-sdk.api-key=${OPENAI_API_KEY}
spring.ai.openai-sdk.chat.options.model=gpt-5-mini-2025-08-07
agent.model=gpt-5-mini-2025-08-07
agent.model.knowledge.cutoff=2025-08-07
```

## Related Documentation

- [memory-tools-demo](../../memorymemory-tools-demo) — same pattern with dedicated, sandboxed `MemoryTools`
- [MemoryTools Documentation](../../spring-ai-agent-utils/docs/MemoryTools.md) — full reference for the dedicated tool alternative
- [FileSystemTools Documentation](../../spring-ai-agent-utils/docs/FileSystemTools.md) — the tools used in this demo
- [Claude Code — Memory](https://code.claude.com/docs/en/memory) — the pattern this demo directly follows
- [Claude API SDK — Memory Tool](https://platform.claude.com/docs/en/agents-and-tools/tool-use/memory-tool) — the dedicated tool specification
