# Spring AI Agent Utils - Examples

This directory contains comprehensive examples demonstrating how to build AI-powered agents using the spring-ai-agent-utils library.

## Available Examples

### [Code Agent Demo](code-agent-demo)

A full-featured AI coding assistant with interactive command-line interface, inspired by Claude Code.

See the [Code Agent Demo README](code-agent-demo/README.md) for full documentation.

---

### [Ask User Question Demo](ask-user-question-demo)

Demonstrates the AskUserQuestionTool for interactive agent-user communication with structured questions and multiple-choice options.

---

### [Sub-Agent Demo](subagent-demo)

Demonstrates the hierarchical sub-agent system using Markdown-defined local subagents with the TaskTool dispatcher pattern.

See the [Sub-Agent Demo README](subagent-demo/README.md) for architecture details.

---

### [Sub-Agent A2A Demo](subagent-a2a-demo)

Extends the sub-agent system with [A2A (Agent-to-Agent) protocol](https://google.github.io/A2A/) support for delegating tasks to remote agents over HTTP.

See the [Sub-Agent A2A Demo README](subagent-a2a-demo/README.md) for setup instructions.

---

### [Skills Demo](skills-demo)

Focused demonstration of the SkillsTool system and custom skill development.

See the [Skills Demo README](skills-demo/README.md) for full documentation.

---

### [Todo Demo](todo-demo)

Demonstrates the `TodoWriteTool` for structured task management in agents. Shows how LLMs can create, track, and update task lists during execution with real-time progress display via Spring application events.

See the [Todo Demo README](todo-demo/README.md) for full documentation.

## Prerequisites

All examples require:
- Java 17 or higher
- Maven 3.6+
- At least one AI provider API key (Anthropic, OpenAI, or Google)
- Optional: Brave API key for web search

### Building

From the project root:
```bash
mvn clean install
```

## Documentation

- [spring-ai-agent-utils Library](../spring-ai-agent-utils/README.md)
- [Tool Documentation](../spring-ai-agent-utils/docs/)
- [Spring AI Reference](https://docs.spring.io/spring-ai/reference/)

## License

Apache License 2.0
