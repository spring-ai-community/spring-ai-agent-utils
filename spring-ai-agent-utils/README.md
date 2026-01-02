# Spring AI Agent Utils

A Spring AI library that brings Claude Code-inspired tools and skills to your AI agents.

## Overview

Spring AI Agent Utils reimplements core [Claude Code](https://code.claude.com/docs/en/overview) capabilities as Spring AI tools, enabling sophisticated agentic workflows with file operations, shell execution, web access, task management, and extensible skills.

## Features

### Core Tools

- **FileSystemTools** - Read, write, and edit files with precise control
- **ShellTools** - Execute shell commands with background process support
- **TodoWriteTool** - Structured task management with state tracking
- **SmartWebFetchTool** - AI-powered web content summarization with caching
- **BraveWebSearchTool** - Web search with domain filtering

### Agent Skills

Extend agent capabilities with reusable, composable knowledge modules defined in Markdown with YAML front-matter:

```yaml
---
name: ai-tutor
description: Use when user asks to explain technical concepts
---

# AI Tutor
[Detailed skill documentation...]
```

Skills can include executable scripts and reference materials, loaded dynamically from `.claude/skills/` directories.

## Installation

**Maven:**
```xml
<dependency>
    <groupId>org.springaicommunity</groupId>
    <artifactId>spring-ai-agent-utils</artifactId>
    <version>2.0.0-SNAPSHOT</version>
</dependency>
```

## Quick Start

```java
@SpringBootApplication
public class Application {

    @Bean
    CommandLineRunner demo(ChatClient.Builder chatClientBuilder) {
        return args -> {
            ChatClient chatClient = chatClientBuilder
                // Load skills
                .defaultToolCallbacks(SkillsToolProvider.create(".claude/skills"))

                // Register tools
                .defaultTools(new ShellTools())
                .defaultTools(new FileSystemTools())
                .defaultTools(SmartWebFetchTool.builder(chatClient).build())
                .defaultTools(BraveWebSearchTool.builder(apiKey).build())
                .defaultTools(new TodoWriteTool())
                .build();

            String response = chatClient
                .prompt("Search for Spring AI documentation and summarize it")
                .call()
                .content();
        };
    }
}
```


## Skills Development

Skills are markdown files that teach the AI agent how to perform specific tasks. Based on [Claude Code's Agent Skills](https://code.claude.com/docs/en/skills#agent-skills), the AI automatically invokes relevant skills through semantic matching.

### Skill File Structure

Every skill requires a `SKILL.md` file with YAML frontmatter and markdown instructions:

```
.claude/skills/
└── my-skill/
    ├── SKILL.md          # Required: Skill definition
    ├── reference.md      # Optional: Detailed documentation
    ├── examples.md       # Optional: Usage examples
    ├── scripts/          # Optional: Helper scripts
    └── pyproject.toml    # Optional: Python dependencies
```

### Required Frontmatter Fields

```markdown
---
name: my-skill
description: What this skill does and when to use it. Include specific
  capabilities and trigger keywords users would naturally say.
allowed-tools: Read, Grep, Bash
model: claude-sonnet-4-5-20250929
---

# My Skill

## Instructions
Provide clear, step-by-step guidance for the AI agent.

## Examples
Show concrete examples of using this skill.

## Additional Resources
- For complete details, see [reference.md](reference.md)
- For usage examples, see [examples.md](examples.md)
```

| Field | Required | Description |
|-------|----------|-------------|
| `name` | Yes | Lowercase letters, numbers, hyphens only (max 64 chars) |
| `description` | Yes | What it does + when to use it (max 1024 chars). Used for semantic matching |
| `allowed-tools` | No | Comma-separated tools the agent can use without asking permission |
| `model` | No | Specific model to use when this skill is active |

### Skill Locations

Where you store a skill determines its scope:

| Location | Path | Scope |
|----------|------|-------|
| **Personal** | `~/.claude/skills/` | User, across all projects |
| **Project** | `.claude/skills/` | Team in this repository |

**Tip**: Use project skills (`.claude/skills/`) for team collaboration by committing them to version control.

### How Skills Are Invoked

The `SkillsToolProvider` implements a three-step process:

1. **Discovery**: At startup, loads skill names and descriptions (lightweight)
2. **Activation**: When a request semantically matches a skill's description, the AI invokes it
3. **Execution**: The full `SKILL.md` content is loaded and the AI follows its instructions

### Best Practices

**Write Effective Descriptions:**

✅ **Good**: Include specific capabilities and trigger keywords
```yaml
description: Extract text and tables from PDF files, fill forms, merge documents.
  Use when working with PDF files or when the user mentions PDFs, forms,
  or document extraction.
```

❌ **Poor**: Too vague
```yaml
description: Helps with documents
```

**Keep Skills Focused:**
- Keep `SKILL.md` under 500 lines
- Use supporting files (`reference.md`, `examples.md`) for detailed content
- Link to supporting files using relative paths

**Progressive Disclosure:**
```markdown
## Quick Start
[Essential instructions here]

## Additional Resources
- For API details, see [reference.md](reference.md)
- For examples, see [examples.md](examples.md)
```

The AI loads supporting files only when needed, preserving context.

**Tool Access for Skills:**

To enable skills to load additional references or run scripts, include the appropriate tools when registering the `SkillsToolProvider`:

```java
ChatClient chatClient = chatClientBuilder
    .defaultToolCallbacks(SkillsToolProvider.create(".claude/skills"))

    // Required for skills to load reference files (reference.md, examples.md, etc.)
    .defaultTools(new FileSystemTools())

    // Required for skills to execute scripts (Python, shell scripts, etc.)
    .defaultTools(new ShellTools())

    // Other tools...
    .build();
```

Without these tools registered:
- Skills cannot read supporting files like `reference.md` or `examples.md`
- Skills cannot execute scripts in the `scripts/` directory
- The AI will be limited to only the content in `SKILL.md`

**Note**: You can restrict tool access per skill using the `allowed-tools` frontmatter field to limit which operations the AI can perform when a specific skill is active.

## Architecture

The library implements Claude Code's tool augmentation patterns:

- **Tool Callbacks** - Spring AI's tool registration mechanism
- **Builder Pattern** - Fluent configuration for complex tools
- **Front-Matter Parsing** - YAML metadata extraction from Markdown
- **Retry & Caching** - Network resilience and performance optimization
- **Safety Checks** - Optional domain validation and content filtering

## Configuration

**application.properties:**
```properties
# Model selection (supports Anthropic, OpenAI, Google)
spring.ai.anthropic.api-key=${ANTHROPIC_API_KEY}
spring.ai.anthropic.options.model=claude-sonnet-4-5-20250929

# Web tools
brave.api.key=${BRAVE_API_KEY}
```

## Examples

See [examples/skills-demo](../examples/skills-demo) for a complete working application demonstrating all tools and agent skills.

## Requirements

- Java 17+
- Spring Boot 3.x / 4.x
- Spring AI 2.0.0+

## License

Apache License 2.0

## Credits

Inspired by [Claude Code](https://code.claude.com) by Anthropic. Architecture insights from:
- [Claude Code Documentation](https://code.claude.com/docs/en/overview)
- [Claude Code Agent Skills](https://code.claude.com/docs/en/skills#agent-skills)
- [Claude Code Internals](https://agiflow.io/blog/claude-code-internals-reverse-engineering-prompt-augmentation/)
- [Claude Code Skills](https://mikhail.io/2025/10/claude-code-skills/)

## Links

- [GitHub Repository](https://github.com/spring-ai-community/spring-ai-agent-utils)
- [Issue Tracker](https://github.com/spring-ai-community/spring-ai-agent-utils/issues)
- [Spring AI Documentation](https://docs.spring.io/spring-ai/reference/)
