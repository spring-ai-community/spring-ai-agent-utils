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
var taskTool = TaskTool.builder()
    .subagentTypes(ClaudeSubagentType.builder()
        .skillsResources(skillPaths)
        .chatClientBuilder("default", chatClientBuilder.clone())
        .braveApiKey(braveApiKey)
        .build())
    .build();

ChatClient chatClient = chatClientBuilder
    .defaultToolCallbacks(taskTool)
    // ... other tools and advisors
    .build();
```

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

## Dependencies

- `spring-ai-agent-utils` - Core library with Task Tools and Claude subagent support
- `spring-ai-starter-model-google-genai` - Google Gemini (configurable for Anthropic/OpenAI)

## Related Documentation

- [Task Tools Documentation](../../spring-ai-agent-utils/docs/TaskTools.md)
- [Subagent Framework](../../spring-ai-agent-utils/docs/Subagent.md)
- [Sub-Agent A2A Demo](../subagent-a2a-demo) - Combining local and remote A2A sub-agents
