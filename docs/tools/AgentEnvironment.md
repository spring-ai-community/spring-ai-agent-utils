# AgentEnvironment - Dynamic Agent Context

Provide AI agents with runtime environment information and git repository context through dynamic system prompt parameters. The `AgentEnvironment` utility class automatically gathers environment metadata and git status to make agents more context-aware.

It is used in a combination with the agent system prompt: [MAIN_AGENT_SYSTEM_PROMPT_V2.md](https://github.com/spring-ai-community/spring-ai-agent-utils/blob/main/spring-ai-agent-utils/src/main/resources/prompt/MAIN_AGENT_SYSTEM_PROMPT_V2.md).

## System Prompt Template Placeholders

Use these placeholders in your system prompt markdown files:

| Placeholder | Replaced With | Source |
|-------------|---------------|--------|
| `{ENVIRONMENT_INFO}` | Environment information | `AgentEnvironment.info()` |
| `{GIT_STATUS}` | Git repository status | `AgentEnvironment.gitStatus()` |
| `{AGENT_MODEL}` | Model identifier | `agent.model` property |
| `{AGENT_MODEL_KNOWLEDGE_CUTOFF}` | Knowledge cutoff date | `agent.model.knowledge.cutoff` property |


## API Reference

### Constants

```java
// Parameter keys for system prompt placeholders
public static final String ENVIRONMENT_INFO_KEY = "ENVIRONMENT_INFO";
public static final String GIT_STATUS_KEY = "GIT_STATUS";
public static final String AGENT_MODEL_KEY = "AGENT_MODEL";
public static final String AGENT_MODEL_KNOWLEDGE_CUTOFF_KEY = "AGENT_MODEL_KNOWLEDGE_CUTOFF";
```

### Methods

#### `AgentEnvironment.info()`

Collects current environment information.

**Returns:** String containing:
- Working directory (absolute path)
- Git repository detection
- Platform name
- OS version
- Current date (ISO format)

**Example:**
```java
String envInfo = AgentEnvironment.info();
System.out.println(envInfo);
// Working directory: /Users/username/projects/myapp
// Is directory a git repo: Yes
// Platform: mac os x
// OS Version: Mac OS X 14.5.0
// Today's date: 2026-01-10
```

#### `AgentEnvironment.gitStatus()`

Collects git repository status snapshot.

**Returns:** String containing:
- Current branch name
- Main/master branch name (detected automatically)
- Working tree status (modified, added, deleted files)
- Recent 5 commits (one-line format)

**Returns empty string if:**
- Git is not installed or not in PATH
- Current directory is not a git repository

**Example:**
```java
String gitStatus = AgentEnvironment.gitStatus();
System.out.println(gitStatus);
// gitStatus: This is the git status at the start of the conversation...
// Current branch: feature-branch
// Main branch (you will usually use this for PRs): main
// Status:
// M README.md
// ...
```


## Basic Usage

### 1. Configure Agent Properties

Add agent configuration to your `application.properties`:

```properties
# AGENT CONFIGURATION

## Model info (Must match the configured model above)
agent.model=claude-sonnet-4-5-20250929
agent.model.knowledge.cutoff=2025-01

# For GPT models
# agent.model=gpt-5-mini-2025-08-07
# agent.model.knowledge.cutoff=2025-08-07

# For Gemini models
# agent.model=gemini-3.1-pro-preview
# agent.model.knowledge.cutoff=Unknown
```

### 2. Inject Configuration Values

Use `@Value` annotations to inject the configuration:

```java
@Value("${agent.model:Unknown}")
String agentModel;

@Value("${agent.model.knowledge.cutoff:Unknown}")
String agentModelKnowledgeCutoff;
```

### 3. Configure System Prompt with Parameters

Use the `AgentEnvironment` utility to provide dynamic context to your system prompt:

```java
import org.springaicommunity.agent.utils.AgentEnvironment;

ChatClient chatClient = chatClientBuilder
    .defaultSystem(p -> p.text(systemPrompt) // Load system prompt from classpath
        .param(AgentEnvironment.ENVIRONMENT_INFO_KEY, AgentEnvironment.info())
        .param(AgentEnvironment.GIT_STATUS_KEY, AgentEnvironment.gitStatus())
        .param(AgentEnvironment.AGENT_MODEL_KEY, agentModel)
        .param(AgentEnvironment.AGENT_MODEL_KNOWLEDGE_CUTOFF_KEY, agentModelKnowledgeCutoff))
    // ... rest of configuration
    .build();
```

### 4. Reference Parameters in System Prompt

Create a system prompt template that references the parameters:

**File:** `src/main/resources/prompt/MAIN_AGENT_SYSTEM_PROMPT_V2.md`

```markdown
Here is useful information about the environment you are running in:
<env>
{ENVIRONMENT_INFO}
</env>
You are powered by the model: {AGENT_MODEL}

Assistant knowledge cutoff is {AGENT_MODEL_KNOWLEDGE_CUTOFF}.

{GIT_STATUS}
```

## Environment Information Output

The `AgentEnvironment.info()` method returns a formatted string with:

```
Working directory: /Users/username/projects/myapp
Is directory a git repo: Yes
Platform: mac os x
OS Version: Mac OS X 14.5.0
Today's date: 2026-01-10
```

## Git Status Output

The `AgentEnvironment.gitStatus()` method returns a detailed git repository snapshot:

```
gitStatus: This is the git status at the start of the conversation. Note that this status is a snapshot in time, and will not update during the conversation.
Current branch: feature-branch

Main branch (you will usually use this for PRs): main

Status:
M README.md
M src/main/java/com/example/Application.java
?? src/main/java/com/example/NewFeature.java

Recent commits:
a1b2c3d Add new feature implementation
e4f5g6h Fix bug in authentication flow
h7i8j9k Update documentation
k0l1m2n Refactor service layer
n3o4p5q Initial commit
```

## Cross-Platform Support

The `AgentEnvironment` class handles platform differences automatically:

- **Windows**: Uses `cmd.exe /c git` for git commands
- **macOS/Linux**: Uses `git` directly
- **All platforms**: Sets `LC_ALL=C` and `LANG=C` for consistent git output

