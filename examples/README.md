# Spring AI Agent Utils - Examples

This directory contains comprehensive examples demonstrating how to build AI-powered agents using the spring-ai-agent-utils library.

## Available Examples

### [Code Agent Demo](code-agent-demo)

A full-featured AI coding assistant with interactive command-line interface, inspired by Claude Code.

**What it demonstrates:**
- Interactive chat loop for continuous conversation
- Complete integration of all 7 core tools (Shell, FileSystem, Grep, WebFetch, WebSearch, Todo, Skills)
- Message history with 500-message window for context retention
- Multi-model support (Anthropic Claude, OpenAI GPT, Google Gemini)
- Custom advisor pipeline (tool calling, memory management, logging)
- Professional coding assistant system prompt

**Best for:**
- Learning how to build a complete AI coding assistant
- Understanding the full capabilities of spring-ai-agent-utils
- Production-ready architecture patterns
- Multi-model integration

**Key Features:**
- Code exploration and navigation using GrepTool and FileSystemTools
- Shell command execution with background process support
- Web research with intelligent content fetching and search
- Multi-step task execution with TodoWriteTool tracking
- Extensible skills system for custom capabilities

**Quick Start:**
```bash
cd code-agent-demo
export GOOGLE_CLOUD_PROJECT=your-project-id  # or use other providers
export BRAVE_API_KEY=your-brave-key          # optional, for web search
mvn spring-boot:run
```

**Interactive Usage:**
```
I am your assistant.

USER: Search for TODO comments in this project
ASSISTANT: [Uses GrepTool to find TODOs across the codebase]

USER: Create a test for the BraveWebSearchTool class
ASSISTANT: [Uses FileSystemTools to read existing code, create tests]

USER: Run the tests
ASSISTANT: [Uses ShellTools to execute mvn test]
```

See the [Code Agent Demo README](code-agent-demo/README.md) for detailed setup, configuration, and advanced usage.

---

### [Skills Demo](skills-demo)

Focused demonstration of the SkillsTool system and custom skill development.

**What it demonstrates:**
- Agent skills system with Markdown-based skill definitions
- Progressive disclosure pattern with reference files
- Skills with helper scripts (Python for YouTube transcripts, PDF generation)
- Semantic matching for automatic skill invocation
- Single-prompt execution patterns

**Best for:**
- Learning to create custom agent skills
- Understanding skill composition and structure
- Building domain-specific AI assistants
- Extending agent capabilities with specialized knowledge

**Key Features:**
- **ai-tuto** skill - Educational skill that creates PDF tutorials explaining technical concepts
- YouTube transcript integration for research-based explanations
- YAML frontmatter configuration for skill metadata
- Helper script integration (Python, Bash, etc.)
- Skill-specific tool restrictions

**Quick Start:**
```bash
cd skills-demo
export ANTHROPIC_API_KEY=your-anthropic-key  # or use other providers
export BRAVE_API_KEY=your-brave-key          # optional, for web search
mvn spring-boot:run
```

**Example Usage:**
```java
// The demo includes example prompts like:
var answer = chatClient.prompt("""
    Explain reinforcement learning in simple terms.
    Use the YouTube video https://youtu.be/vXtfdGphr3c transcript
    to support your answer.
    """).call().content();
```

The agent automatically:
1. Recognizes this matches the ai-tuto skill
2. Loads the skill and its helper scripts
3. Uses the Python script to fetch YouTube transcripts
4. Creates an educational explanation
5. Optionally generates a PDF tutorial

---

## Comparison

| Feature | Code Agent Demo | Skills Demo |
|---------|----------------|-------------|
| **Focus** | Complete AI coding assistant | Skills system deep dive |
| **Interaction** | Interactive CLI loop | Single-prompt execution |
| **Tools** | All 7 tools integrated | Subset focused on skills |
| **Complexity** | Production-ready | Learning-focused |
| **Memory** | 500-message window | Basic configuration |
| **Best for** | Building complete agents | Creating custom skills |

## Common Setup

Both examples require:

### Prerequisites
- Java 17 or higher
- Maven 3.6+
- At least one AI provider API key

### AI Provider Options

Choose one or more providers:

**Google Gemini** (code-agent-demo default):
```bash
export GOOGLE_CLOUD_PROJECT=your-project-id
```

**Anthropic Claude** (skills-demo default):
```bash
export ANTHROPIC_API_KEY=your-anthropic-key
```

**OpenAI GPT**:
```bash
export OPENAI_API_KEY=your-openai-key
```

**Brave Search** (optional, for web search):
```bash
export BRAVE_API_KEY=your-brave-key
```

### Building

From the project root:
```bash
mvn clean install
```

Or build individual examples:
```bash
cd code-agent-demo  # or skills-demo
mvn clean install
```

## Skills Directory

Both examples use skills from the `.claude/skills` directory in the project root:

```
spring-ai-agent-utils/
└── .claude/skills/
    └── ai-tuto/              # AI tutor skill
        ├── SKILL.md          # Skill definition
        ├── REFERENCE.md      # Supporting documentation
        └── scripts/
            └── youtube_transcript.py  # Helper script
```

### Creating Your Own Skills

1. Create a directory in `.claude/skills/` or your custom skills path
2. Add a `SKILL.md` file with YAML frontmatter:

```markdown
---
name: my-skill
description: What the skill does and when to use it
allowed-tools: Read, Bash, Grep
model: claude-sonnet-4-5-20250929
---

# Skill Instructions

Your detailed prompt for the AI agent...
```

3. Optionally add:
   - `REFERENCE.md` - Supporting documentation
   - `scripts/` - Helper scripts in any language
   - Additional resource files

4. Configure the skills path in your application:

```java
SkillsTool.builder()
    .addSkillsDirectory("/path/to/skills")
    .build()
```

See the [SkillsTool documentation](../spring-ai-agent-utils/docs/SkillsTool.md) for comprehensive guide on skill development.

## Architecture Patterns

Both examples demonstrate:

### ChatClient Configuration
```java
ChatClient chatClient = chatClientBuilder
    .defaultSystem(systemPrompt)              // Agent behavior guidance
    .defaultToolCallbacks(skillsTool)         // Skills (ToolCallback type)
    .defaultTools(                            // Standard tools
        new ShellTools(),
        new FileSystemTools(),
        new GrepTool(),
        smartWebFetchTool,
        braveWebSearchTool,
        new TodoWriteTool())
    .defaultAdvisors(                         // Processing pipeline
        toolCallAdvisor,
        memoryChatAdvisor,
        loggingAdvisor)
    .build();
```

### Tool Integration Pattern
```java
// Simple tools - instantiate directly
new ShellTools()
new FileSystemTools()
new GrepTool()
new TodoWriteTool()

// Complex tools - use builders
SmartWebFetchTool.builder(chatClient)
    .maxContentLength(100_000)
    .domainSafetyCheck(true)
    .build()

BraveWebSearchTool.builder(apiKey)
    .resultCount(15)
    .build()

SkillsTool.builder()
    .addSkillsDirectory(".claude/skills")
    .addSkillsDirectory("~/.claude/skills")
    .build()
```

### Advisor Pipeline Pattern
```java
// Tool calling - manages tool invocations
ToolCallAdvisor.builder()
    .conversationHistoryEnabled(false)
    .build()

// Memory management - maintains context
MessageChatMemoryAdvisor.builder(chatMemory)
    .order(Ordered.HIGHEST_PRECEDENCE + 1000)
    .build()

// Custom advisors - logging, monitoring, etc.
new MyLoggingAdvisor()
```

## Switching AI Providers

Both examples support multiple providers. To switch:

1. **Update pom.xml** - Uncomment desired provider dependency:
```xml
<!-- Anthropic Claude -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-anthropic</artifactId>
</dependency>

<!-- OpenAI GPT -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-openai-sdk</artifactId>
</dependency>

<!-- Google Gemini -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-google-genai</artifactId>
</dependency>
```

2. **Update application.properties** - Configure the provider:
```properties
# Anthropic
spring.ai.anthropic.api-key=${ANTHROPIC_API_KEY}
spring.ai.anthropic.options.model=claude-sonnet-4-5-20250929

# OpenAI
spring.ai.openai.api-key=${OPENAI_API_KEY}
spring.ai.openai.options.model=gpt-5-mini-2025-08-07

# Google
spring.ai.google.genai.project-id=${GOOGLE_CLOUD_PROJECT}
spring.ai.google.genai.options.model=gemini-3-pro-preview
```

3. **Set environment variables** and rebuild

## Next Steps

1. **Start with code-agent-demo** to understand the full system
2. **Explore skills-demo** to learn skill development
3. **Read the tool documentation** in [spring-ai-agent-utils/docs/](../spring-ai-agent-utils/docs/)
4. **Create your own skills** for domain-specific tasks
5. **Build your own agent** combining patterns from both examples

## Troubleshooting

### Common Issues

**"API key not found"**
```bash
# Verify environment variables are set
echo $ANTHROPIC_API_KEY
echo $GOOGLE_CLOUD_PROJECT
```

**"Cannot find skills directory"**
- Check the path in application.properties or Application.java
- Use absolute paths or ensure relative paths are correct
- Verify the `.claude/skills` directory exists

**"Tool execution failed"**
- Check file permissions for FileSystemTools and ShellTools
- Verify helper scripts are executable (chmod +x)
- Review logs from MyLoggingAdvisor

**Out of memory**
```bash
export MAVEN_OPTS="-Xmx2g"
mvn spring-boot:run
```

## Learn More

- [Library Documentation](../spring-ai-agent-utils/README.md)
- [Tool Documentation](../spring-ai-agent-utils/docs/)
- [Spring AI Documentation](https://docs.spring.io/spring-ai/reference/)
- [Claude Code Documentation](https://code.claude.com/docs/en/overview)

## Contributing

These are demonstration projects. For contributing to the core library, see the main [spring-ai-agent-utils](../spring-ai-agent-utils) project.

## License

Apache License 2.0
