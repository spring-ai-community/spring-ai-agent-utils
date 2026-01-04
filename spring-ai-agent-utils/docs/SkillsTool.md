# SkillsTool - Agent Skills System

Extend AI agent capabilities with reusable, composable knowledge modules defined in Markdown with YAML front-matter. Based on [Claude Code's Agent Skills](https://code.claude.com/docs/en/skills#agent-skills), skills enable AI agents to perform specialized tasks through semantic matching.

**Features:**
- Define skills as Markdown files with YAML frontmatter
- Automatic skill invocation through semantic matching
- Support for reference files and helper scripts
- Progressive disclosure of detailed information
- Project-wide or user-wide skill scopes
- Customizable tool descriptions and templates

## Overview

Skills are markdown files that teach the AI agent how to perform specific tasks. Unlike traditional tools that execute code, skills provide **knowledge and instructions** to the AI, enabling it to handle specialized domains effectively.

**How Skills Work:**
1. **Discovery**: At startup, SkillsTool loads skill names and descriptions
2. **Semantic Matching**: When a user request matches a skill's description, the AI invokes it
3. **Execution**: The full skill content is loaded and the AI follows its instructions

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

## Basic Usage

### Create a Skill

**File:** `.claude/skills/my-skill/SKILL.md`

```markdown
---
name: my-skill
description: What this skill does and when to use it. Include specific
  capabilities and trigger keywords users would naturally say.
---

# My Skill

## Instructions
Provide clear, step-by-step guidance for the AI agent.

## Examples
Show concrete examples of using this skill.
```

### Register SkillsTool

```java
ChatClient chatClient = chatClientBuilder
    .defaultToolCallbacks(SkillsTool.builder()
        .addSkillsDirectory(".claude/skills")
        .build())
    .build();

// AI automatically invokes skills based on semantic matching
String response = chatClient.prompt()
    .user("Help me with PDF processing")
    .call()
    .content();
// If a "pdf" skill exists with matching description, it's automatically invoked
```

## Skill File Structure

Every skill requires a `SKILL.md` file. Supporting files are optional:

```
.claude/skills/
└── my-skill/
    ├── SKILL.md          # Required: Skill definition
    ├── reference.md      # Optional: Detailed documentation
    ├── examples.md       # Optional: Usage examples
    ├── scripts/          # Optional: Helper scripts
    │   └── process.py
    └── pyproject.toml    # Optional: Python dependencies
```

### SKILL.md Format

```markdown
---
name: my-skill
description: Comprehensive description including what the skill does,
  when to use it, and trigger keywords users might mention.
allowed-tools: Read, Grep, Bash
model: claude-sonnet-4-5-20250929
---

# Skill Title

## Overview
Brief introduction to what this skill does.

## Instructions
Step-by-step guidance for the AI agent.

## Examples
Concrete examples demonstrating skill usage.

## Additional Resources
- For complete details, see [reference.md](reference.md)
- For usage examples, see [examples.md](examples.md)
```

## Frontmatter Fields

| Field | Required | Description |
|-------|----------|-------------|
| `name` | Yes | Skill identifier: lowercase letters, numbers, hyphens only (max 64 chars) |
| `description` | Yes | What it does + when to use it (max 1024 chars). Used for semantic matching |
| `allowed-tools` | No | Comma-separated tools the agent can use without asking permission |
| `model` | No | Specific model to use when this skill is active |

### Name Field

```yaml
# ✅ GOOD
name: pdf-processor
name: data-analysis
name: api-documentation

# ❌ BAD
name: PDF Processor      # No spaces or capitals
name: data_analysis      # Use hyphens, not underscores
name: my-super-long-skill-name-that-exceeds-the-limit  # Too long
```

### Description Field

The description is **critical** for semantic matching. Include:
- What the skill does
- When to use it
- Trigger keywords users might say
- Specific capabilities

```yaml
# ✅ GOOD
description: Extract text and tables from PDF files, fill forms, merge documents.
  Use when working with PDF files or when the user mentions PDFs, forms,
  or document extraction.

# ❌ BAD
description: Helps with documents  # Too vague
description: PDF tool              # Lacks context and triggers
```

### Allowed-Tools Field

Comma-separated list of tools the AI can use when this skill is active:

```yaml
allowed-tools: Read, Grep, Bash, Write
```

**Without this field:** AI must ask user permission before using tools
**With this field:** AI can use listed tools automatically

### Model Field

Override the default model for this specific skill:

```yaml
model: claude-sonnet-4-5-20250929
model: claude-opus-4-5-20251101
```

## Skill Locations

Where you store a skill determines its scope:

| Location | Path | Scope |
|----------|------|-------|
| **Personal** | `~/.claude/skills/` | User, across all projects |
| **Project** | `.claude/skills/` | Team in this repository |

**Tip:** Use project skills (`.claude/skills/`) for team collaboration by committing them to version control.

### Loading Multiple Directories

```java
SkillsTool skillsTool = SkillsTool.builder()
    .addSkillsDirectory(".claude/skills")           // Project skills
    .addSkillsDirectory(System.getenv("HOME") + "/.claude/skills")  // Personal skills
    .build();
```

Alternative using `addSkillsDirectories`:

```java
SkillsTool skillsTool = SkillsTool.builder()
    .addSkillsDirectories(List.of(
        ".claude/skills",
        System.getenv("HOME") + "/.claude/skills"
    ))
    .build();
```

## Builder Configuration

### Basic Builder

```java
SkillsTool.builder()
    .addSkillsDirectory(".claude/skills")
    .build();
```

### Custom Tool Description Template

```java
String customTemplate = """
    Execute a custom skill...

    <available_skills>
    %s
    </available_skills>
    """;

SkillsTool.builder()
    .addSkillsDirectory(".claude/skills")
    .toolDescriptionTemplate(customTemplate)
    .build();
```

### Multiple Skill Directories

```java
SkillsTool.builder()
    .addSkillsDirectory(".claude/skills")
    .addSkillsDirectory("~/.claude/skills")
    .addSkillsDirectory("/shared/team-skills")
    .build();
```

## Best Practices

### 1. Write Effective Descriptions

**Include specific capabilities:**

```yaml
# ✅ GOOD
description: Process Excel files: read sheets, analyze data, create charts,
  generate reports. Use when working with .xlsx, .xls files, spreadsheets,
  or when user mentions Excel, data analysis, or pivot tables.
```

**Include trigger keywords:**

```yaml
# ✅ GOOD
description: Analyze SQL databases, write optimized queries, explain execution
  plans. Use when user mentions: databases, SQL, MySQL, PostgreSQL, queries,
  tables, indexes, or performance tuning.
```

**Too vague (avoid):**

```yaml
# ❌ BAD
description: Database helper
description: Works with files
```

### 2. Keep Skills Focused

**SKILL.md should be:**
- Under 500 lines
- Focused on one domain/task
- Quick to read and understand

**Use supporting files for details:**

```markdown
## Quick Start
[Essential 50-100 line instructions here]

## Additional Resources
- For complete API reference, see [reference.md](reference.md)
- For 20+ examples, see [examples.md](examples.md)
- For advanced techniques, see [advanced.md](advanced.md)
```

### 3. Progressive Disclosure

Structure skills to provide information when needed:

```markdown
# PDF Processing Skill

## Basic Operations
Quick instructions for common PDF tasks...

## Advanced Features
For complex operations like form filling, see [advanced.md](advanced.md)

## API Reference
For complete API documentation, see [reference.md](reference.md)
```

The AI loads supporting files **only when needed**, preserving context.

### 4. Provide Clear Examples

```markdown
## Examples

### Extract Text from PDF
\```bash
python scripts/extract_text.py input.pdf output.txt
\```

### Merge Multiple PDFs
\```bash
python scripts/merge.py file1.pdf file2.pdf output.pdf
\```

### Fill PDF Form
\```python
from pdf_processor import fill_form

fill_form('template.pdf', {
    'name': 'John Doe',
    'date': '2025-01-04'
}, 'filled.pdf')
\```
```

### 5. Tool Access for Skills

Skills often need to read files or run scripts. Register necessary tools:

```java
ChatClient chatClient = chatClientBuilder
    .defaultToolCallbacks(SkillsTool.builder()
        .addSkillsDirectory(".claude/skills")
        .build())

    // Required for skills to load reference files
    .defaultTools(new FileSystemTools())

    // Required for skills to execute scripts
    .defaultTools(new ShellTools())

    .build();
```

**Without FileSystemTools:** Skills can't read `reference.md`, `examples.md`
**Without ShellTools:** Skills can't execute scripts in `scripts/` directory

## How Skills Are Invoked

### Three-Step Process

1. **Discovery** (at startup):
   - SkillsTool scans `.claude/skills/` directories
   - Loads `name` and `description` from each `SKILL.md`
   - Creates lightweight skill registry

2. **Activation** (on user request):
   - User: "Help me extract data from this PDF"
   - AI matches request to skill descriptions
   - AI invokes skill: `Skill(command="pdf-processor")`

3. **Execution**:
   - SkillsTool loads full `SKILL.md` content
   - Returns content + base directory to AI
   - AI follows skill instructions

### Invocation Example

```java
// User request
"I need to process a PDF file and extract tables"

// AI recognizes "pdf" skill matches
// AI calls: Skill(command="pdf")

// SkillsTool returns:
"Base directory for this skill: /project/.claude/skills/pdf

# PDF Processing Skill
[full SKILL.md content here...]
"

// AI follows instructions from skill
```

## Complete Example

### Example Skill: API Documentation Generator

**File:** `.claude/skills/api-docs/SKILL.md`

```markdown
---
name: api-docs
description: Generate API documentation from source code. Analyze REST endpoints,
  GraphQL schemas, or OpenAPI specs. Use when user mentions: API docs, documentation
  generation, endpoint documentation, REST API, GraphQL, OpenAPI, Swagger.
allowed-tools: Read, Grep, Bash, Write
model: claude-sonnet-4-5-20250929
---

# API Documentation Generator

## Overview
This skill helps generate comprehensive API documentation from source code,
focusing on REST endpoints, request/response formats, and usage examples.

## Instructions

### Step 1: Analyze Source Code
1. Use Grep to find controller/route files
2. Read files to understand endpoint structure
3. Identify request/response types

### Step 2: Extract Information
For each endpoint, document:
- HTTP method and path
- Request parameters (query, path, body)
- Response format
- Error codes
- Authentication requirements

### Step 3: Generate Documentation
Create markdown documentation with:
- Endpoint overview table
- Detailed endpoint descriptions
- Code examples in multiple languages
- Response schemas

## Example

### Finding Spring Boot Endpoints
\```bash
grep -r "@GetMapping\\|@PostMapping\\|@PutMapping\\|@DeleteMapping" src/
\```

### Documenting an Endpoint
\```markdown
## GET /api/users/{id}

Retrieve user by ID.

**Parameters:**
- `id` (path, required): User ID

**Response:** `200 OK`
\```json
{
  "id": 1,
  "name": "John Doe",
  "email": "john@example.com"
}
\```

**Errors:**
- `404`: User not found
\```

## Additional Resources
- For OpenAPI integration, see [openapi.md](openapi.md)
- For GraphQL schemas, see [graphql.md](graphql.md)
```

### Registration

```java
@Configuration
public class SkillsConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder chatClientBuilder) {
        return chatClientBuilder
            .defaultToolCallbacks(SkillsTool.builder()
                .addSkillsDirectory(".claude/skills")
                .build())
            .defaultTools(new FileSystemTools())
            .defaultTools(new ShellTools())
            .defaultTools(new GrepTool())
            .build();
    }
}
```

### Usage

```java
ChatClient chatClient = ...; // from bean

String response = chatClient.prompt()
    .user("Generate API documentation for my Spring Boot application")
    .call()
    .content();

// AI automatically:
// 1. Recognizes "API documentation" matches api-docs skill
// 2. Invokes the skill
// 3. Follows skill instructions to generate docs
```

## Advanced Usage

### Custom Skill with Scripts

**Directory structure:**

```
.claude/skills/data-analysis/
├── SKILL.md
├── scripts/
│   ├── analyze.py
│   ├── visualize.py
│   └── report.py
└── pyproject.toml
```

**SKILL.md:**

```markdown
---
name: data-analysis
description: Analyze CSV/Excel data, create visualizations, generate statistical
  reports. Use when user mentions: data analysis, CSV, Excel, statistics,
  charts, graphs, visualization.
allowed-tools: Read, Bash, Write
---

# Data Analysis Skill

## Setup
First, install dependencies:
\```bash
cd .claude/skills/data-analysis
pip install -r requirements.txt
\```

## Usage

### Analyze CSV
\```bash
python scripts/analyze.py data.csv --output report.txt
\```

### Create Visualization
\```bash
python scripts/visualize.py data.csv --type bar --output chart.png
\```

## Examples
[Detailed examples here...]
```

### Namespace Collision Handling

If multiple skills have the same name:

```java
// Use fully qualified names: directory:skill-name
Skill(command="project-a:pdf")
Skill(command="project-b:pdf")
```

## Spring Boot Integration

### Configuration Properties

```properties
# application.properties
skills.directory=.claude/skills
skills.user.directory=${user.home}/.claude/skills
```

### Configuration Class

```java
@Configuration
public class SkillsConfiguration {

    @Value("${skills.directory}")
    private String projectSkillsDir;

    @Value("${skills.user.directory}")
    private String userSkillsDir;

    @Bean
    public SkillsTool skillsTool() {
        return SkillsTool.builder()
            .addSkillsDirectory(projectSkillsDir)
            .addSkillsDirectory(userSkillsDir)
            .build();
    }

    @Bean
    public ChatClient chatClient(
            ChatClient.Builder chatClientBuilder,
            SkillsTool skillsTool) {

        return chatClientBuilder
            .defaultToolCallbacks(skillsTool)
            .defaultTools(new FileSystemTools())
            .defaultTools(new ShellTools())
            .build();
    }
}
```

## Troubleshooting

### Skill Not Found

**Problem:** AI doesn't invoke skill even though it should match

**Solutions:**
1. Check skill description includes trigger keywords
2. Verify `SKILL.md` exists and has frontmatter
3. Ensure skill directory is registered
4. Check logs for skill loading errors

```java
// Enable debug logging
logging.level.org.springaicommunity.agent.tools.SkillsTool=DEBUG
```

### Skill Can't Access Files

**Problem:** Skill tries to read `reference.md` but fails

**Solution:** Register FileSystemTools
```java
ChatClient chatClient = chatClientBuilder
    .defaultToolCallbacks(skillsTool)
    .defaultTools(new FileSystemTools())  // Add this
    .build();
```

### Skill Can't Run Scripts

**Problem:** Skill can't execute Python/shell scripts

**Solution:** Register ShellTools
```java
ChatClient chatClient = chatClientBuilder
    .defaultToolCallbacks(skillsTool)
    .defaultTools(new ShellTools())  // Add this
    .build();
```

### Multiple Skills with Same Name

**Problem:** Naming collision between skills

**Solution:** Use fully qualified names
```markdown
---
name: project-name:skill-name
---
```

## Limitations

- Skill descriptions limited to 1024 characters
- Skill names limited to 64 characters
- Only `SKILL.md` files are recognized (exact name)
- YAML frontmatter must be valid
- Semantic matching depends on AI's understanding

## Best Practices Summary

1. **Descriptive names**: Use clear, hyphenated names
2. **Rich descriptions**: Include capabilities and trigger keywords
3. **Keep focused**: One skill = one domain/task
4. **Progressive disclosure**: Use supporting files for details
5. **Provide examples**: Show concrete usage patterns
6. **Register tools**: FileSystemTools for files, ShellTools for scripts
7. **Version control**: Commit project skills to git
8. **Test thoroughly**: Verify AI invokes skill correctly
9. **Document well**: Clear instructions for AI to follow
10. **Maintain skills**: Update as requirements change

## See Also

- [FileSystemTools](FileSystemTools.md) - For reading reference files
- [ShellTools](ShellTools.md) - For executing skill scripts
- [Claude Code Skills Documentation](https://code.claude.com/docs/en/skills#agent-skills) - Official reference
- [Claude Code Skills Article](https://mikhail.io/2025/10/claude-code-skills/) - Implementation patterns
