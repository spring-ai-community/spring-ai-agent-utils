# Sub-Agent Demo

Demonstrates the **Task Tools** feature for delegating tasks to specialized sub-agents using Spring AI.

## Overview

This example shows how to configure a main agent that can delegate tasks to specialized sub-agents. Each sub-agent has its own context window, system prompt, and tool access.

## Quick Start

1. Set environment variables:
```bash
export GOOGLE_CLOUD_PROJECT=your-project-id
# Or use Anthropic/OpenAI (see application.properties)
```

2. Run the application:
```bash
./mvnw spring-boot:run
```

## Configuration

### Task Tools Setup

```java
SessionService sessionService = DefaultSessionService.builder()
    .sessionRepository(InMemorySessionRepository.builder().build())
    .build();

var taskTool = TaskTool.builder()
    .sessionService(sessionService)                  // sub-agent memory
    .subagentTypes(ClaudeSubagentType.builder()
        .skillsResources(skillPaths)
        .chatClientBuilder("default", chatClientBuilder.clone())
        .sessionService(sessionService)              // sub-agent transcripts
        .braveApiKey(braveApiKey)
        .build())
    .build();

ChatClient chatClient = chatClientBuilder
    .defaultToolCallbacks(taskTool)
    .defaultAdvisors(SessionAdvisors.builder(sessionService)
        .sessionIdInToolContext(true)                // the Task tool finds the main session here
        .build())
    // ... other tools and advisors
    .build();
```

Each `Task` call runs its sub-agent on a session of its own, a child of the main agent's session, and the result starts with `agent_id: <id>`; the model passes that id as `resume` to continue the same sub-agent with its earlier work in context. See [Sub-Agent Memory](../../docs/tools/SubagentMemory.md).

### Application Properties

```properties
agent.skills.paths=classpath:/skills
agent.tasks.paths=classpath:/agents
```

## Project Structure

```
src/main/resources/
├── agents/
│   └── spring-ai-expert.md    # Custom sub-agent definition
├── skills/
│   └── ai-tutor/              # Skills available to sub-agents
└── prompt/
    └── MAIN_AGENT_SYSTEM_PROMPT_V2.md
```

## Custom Sub-Agent Example

Sub-agents are defined as Markdown files with YAML frontmatter (`agents/spring-ai-expert.md`):

```markdown
---
name: spring-ai-expert
description: Use this agent when the user asks questions about Spring AI...
model: sonnet
---

You are a Spring AI Expert...
```

## Features Demonstrated

- **TaskTool** - Launches and manages sub-agents with pluggable resolvers and executors
- **ClaudeSubagentType** - Configures the Claude subagent type with default tools, skills, and model routing
- **Custom Sub-Agents** - Domain-specific expert (Spring AI)
- **Skills Integration** - Sub-agents receive preloaded skill content in their system prompt
- **Multi-Model Support** - Route sub-agents to different models via the `model` frontmatter field
- **Sub-Agent Memory** - Each sub-agent run keeps its transcript in a child session of the main agent's, and can be resumed

## Dependencies

- `spring-ai-agent-utils` - Core library with Task Tools and Claude subagent support
- `spring-ai-starter-model-google-genai` - Google Gemini (configurable for Anthropic/OpenAI)

## Related Documentation

- [Task Tools Documentation](../../docs/tools/TaskTools.md)
- [Subagent Framework](../../docs/tools/Subagent.md)
- [Sub-Agent A2A Demo](../subagent-a2a-demo) - Combining local and remote A2A sub-agents
