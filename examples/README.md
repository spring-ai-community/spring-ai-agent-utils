# Spring AI Agent Utils - Examples

This directory contains comprehensive examples demonstrating how to build AI-powered agents using the spring-ai-agent-utils library.

## Available Examples

### [Code Agent Demo](code-agent-demo)

A full-featured AI coding assistant with interactive command-line interface, inspired by Claude Code.

**Best for:**
- Learning how to build a complete AI coding assistant
- Understanding the full capabilities of spring-ai-agent-utils
- Production-ready architecture patterns
- Multi-model integration (Anthropic Claude, OpenAI GPT, Google Gemini)

**Quick Start:**
```bash
cd code-agent-demo
export GOOGLE_CLOUD_PROJECT=your-project-id  # or use other providers
mvn spring-boot:run
```

See the [Code Agent Demo README](code-agent-demo/README.md) for full documentation.

---

### [Ask User Question Demo](ask-user-question-demo)

Demonstrates the AskUserQuestionTool for interactive agent-user communication with structured questions and multiple-choice options.

**Best for:**
- Learning how to build interactive AI agents that ask clarifying questions
- Understanding structured question-answer patterns
- Implementing user preference gathering
- Building agents that validate assumptions before proceeding

**Quick Start:**
```bash
cd ask-user-question-demo
export ANTHROPIC_API_KEY=your-anthropic-key  # or use other providers
mvn spring-boot:run
```

---

### [Sub-Agent Demo](subagent-demo)

Demonstrates the hierarchical sub-agent system with custom sub-agent creation and TaskTools integration.

**Best for:**
- Learning how to create and use custom sub-agents
- Understanding hierarchical agent architectures
- Building specialized expert agents for domains
- Managing complex tasks with autonomous delegation

**Quick Start:**
```bash
cd subagent-demo
export ANTHROPIC_API_KEY=your-anthropic-key  # or use other providers
mvn spring-boot:run
```

See the [TaskTools documentation](../spring-ai-agent-utils/docs/TaskTools.md) for creating custom sub-agents.

---

### [Skills Demo](skills-demo)

Focused demonstration of the SkillsTool system and custom skill development.

**Best for:**
- Learning to create custom agent skills
- Understanding skill composition and structure
- Building domain-specific AI assistants
- Extending agent capabilities with specialized knowledge

**Quick Start:**
```bash
cd skills-demo
export ANTHROPIC_API_KEY=your-anthropic-key  # or use other providers
mvn spring-boot:run
```

See the [Skills Demo README](skills-demo/README.md) for full documentation.


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

## Architecture

All examples demonstrate:
- **ChatClient** configuration with tools, advisors, and system prompts
- **Tool integration** patterns (ShellTools, FileSystemTools, GrepTool, WebFetch, WebSearch, Skills)
- **Advisor pipelines** for tool calling, memory management, and custom processing

See individual demo READMEs for architecture details and code examples.

## Documentation

- [spring-ai-agent-utils Library](../spring-ai-agent-utils/README.md)
- [Tool Documentation](../spring-ai-agent-utils/docs/)
- [Spring AI Reference](https://docs.spring.io/spring-ai/reference/)

## License

Apache License 2.0
