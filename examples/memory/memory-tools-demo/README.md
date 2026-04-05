# Memory Tools Demo

Demonstrates **long-term memory** for AI agents using `MemoryTools` with Spring AI. The agent remembers facts about the user, project context, and behavioral feedback across independent conversations — information that would otherwise be lost when the session ends.

## Overview

This demo runs a console chat loop where the agent can persist and recall knowledge between conversations. Each time the agent learns something worth keeping — a user preference, a project decision, a behavioral correction — it writes a typed memory file and registers it in a `MEMORY.md` index. On the next run the agent reads that index and selectively loads relevant memories before answering.

```
Session 1:                          Session 2 (new JVM process):
  User: "I prefer concise answers"    Agent reads MEMORY.md
  Agent: saves feedback_style.md  →   Agent: already knows your preference
         updates MEMORY.md            "Here's the short version..."
```

This is **long-term memory** sitting alongside the session's conversation history:

| | Conversation history | MemoryTools (long-term) |
|---|---|---|
| Scope | Current session | Persists across sessions |
| Storage | In-process (`ChatMemory`) | Files on disk |
| Content | Full message exchange | Curated facts worth keeping |
| Managed by | Spring AI advisors | Agent via tool calls |

## Quick Start

### Prerequisites

- Java 17+
- Maven 3.6+
- An AI provider API key

### Setup

1. Set your API key (Google GenAI is configured by default):
```bash
export GOOGLE_CLOUD_PROJECT=your-project-id
# Or Anthropic — see "Switching AI Providers" below
```

2. Run the demo:
```bash
mvn spring-boot:run
```

3. Start a conversation:
```
USER> My name is Alice. I'm a backend engineer and I prefer short answers.
USER> Remember that we're migrating from PostgreSQL to CockroachDB this quarter.
USER> exit
```

4. Run again — the agent recalls what it stored:
```
USER> What do you know about me?
ASSISTANT> You're Alice, a backend engineer. You prefer short answers.
           You're migrating from PostgreSQL to CockroachDB this quarter.
```

## Project Structure

```
memory-tools-demo/
├── src/main/java/org/springaicommunity/agent/
│   ├── Application.java          # Main app, wires MemoryTools + chat loop
│   └── MyLoggingAdvisor.java     # Logs tool calls and responses to stdout
├── src/main/resources/
│   └── application.properties    # Model and memory directory configuration
```

The `classpath:/prompt/MAIN_AGENT_SYSTEM_PROMPT_V2.md` and `classpath:/prompt/MEMORY_TOOLS_SYSTEM_PROMPT.md` prompts are loaded from the `spring-ai-agent-utils` dependency jar — no local copies needed.

## How It Works

### Application Wiring

```java
MemoryTools memoryTools = MemoryTools.builder()
    .memoriesDir(memoryDir)          // from application.properties
    .build();

ChatClient chatClient = chatClientBuilder
    .defaultSystem(p -> p
        .text(mainPrompt + "\n\n" + memoryToolsPrompt)
        .param("MEMORIES_ROOT_DIERCTORY", memoryDir))
    .defaultTools(
        memoryTools,                 // long-term memory
        TodoWriteTool.builder().build())
    .defaultAdvisors(
        ToolCallAdvisor.builder().build(),
        MyLoggingAdvisor.builder().build())
    .build();
```

### Memory Lifecycle

When the agent decides to save a memory it performs two tool calls automatically:

**Step 1 — `MemoryCreate`**: writes a typed `.md` file with YAML frontmatter:
```markdown
---
name: user profile
description: Alice — backend engineer, prefers short answers
type: user
---

Backend engineer named Alice.
Prefers concise, direct responses without trailing summaries.
```

**Step 2 — `MemoryInsert`**: appends a pointer to `MEMORY.md`:
```markdown
- [User Profile](user_profile.md) — Alice, backend engineer, prefers short answers
```

On the next session the agent calls `MemoryView("MEMORY.md", null)` to load the index, then `MemoryView("user_profile.md", null)` for any entry that looks relevant.

### Memory Types

| Type | Saved when | Example |
|---|---|---|
| `user` | User shares background, goals, or preferences | name, role, communication style |
| `feedback` | User corrects the agent or confirms an approach | "stop summarizing at the end" |
| `project` | Project decisions, deadlines, constraints | migration target, freeze dates |
| `reference` | Pointers to external systems | Linear board, Grafana dashboard |

## Configuration

```properties
# application.properties

## AI provider
spring.ai.google.genai.project-id=${GOOGLE_CLOUD_PROJECT}
spring.ai.google.genai.chat.options.model=gemini-3.1-pro-preview

## Agent metadata (shown to the model in the system prompt)
agent.model=gemini-3.1-pro-preview
agent.model.knowledge.cutoff=Unknown

## Memory directory — persists across restarts
agent.memory.dir=${user.home}/.spring-ai-agent/memory-tools-demo/memory
```

The `agent.memory.dir` property uses the Spring `${user.home}` placeholder so it resolves to the correct home directory on any machine. The directory is created automatically on first run.

## Switching AI Providers

Edit `pom.xml` to uncomment the desired starter and update `application.properties`:

**Anthropic Claude:**
```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-anthropic</artifactId>
</dependency>
```
```properties
spring.ai.anthropic.api-key=${ANTHROPIC_API_KEY}
spring.ai.anthropic.chat.options.model=claude-sonnet-4-6
agent.model=claude-sonnet-4-6
agent.model.knowledge.cutoff=2025-08-01
```

**OpenAI:**
```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-openai-sdk</artifactId>
</dependency>
```
```properties
spring.ai.openai-sdk.api-key=${OPENAI_API_KEY}
spring.ai.openai-sdk.chat.options.model=gpt-5-mini-2025-08-07
agent.model=gpt-5-mini-2025-08-07
agent.model.knowledge.cutoff=2025-08-07
```

## Related Documentation

- [MemoryTools Documentation](../../spring-ai-agent-utils/docs/MemoryTools.md) — full API reference, security model, and system prompt guide
- [Claude Code — Memory](https://code.claude.com/docs/en/memory) — the file-based memory pattern this demo implements
- [Claude API SDK — Memory Tool](https://platform.claude.com/docs/en/agents-and-tools/tool-use/memory-tool) — the tool specification the operations are modelled after
- [TodoWriteTool Documentation](../../spring-ai-agent-utils/docs/TodoWriteTool.md) — the other tool included in this demo
