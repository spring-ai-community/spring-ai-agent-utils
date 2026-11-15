# AgentToolset - Default Toolset Factory

`AgentToolset` builds the standard agent callbacks for an `ExecBackend` and `Workspace` pair, so each session does not repeat the same builder calls.

The default tier is `EXECUTE`:

| Tool | Wired from |
|------|------------|
| `Bash`, `BashOutput`, `KillShell` | `execBackend`, or the workspace root when no backend is set |
| `Read`, `Write`, `Edit` | `workspace` (allowed-directory confinement) |
| `Grep`, `Glob`, `ListDirectory` | `workspace` (working directory and confinement) |

Skills, web search, `TodoWrite` and `AskUserQuestion` stay opt-in: they need their own configuration (skill paths, an API key, a chat client, a question handler).

## Usage

```java
List<ToolCallback> tools = AgentToolset.builder()
    .execBackend(backend)
    .workspace(workspace)
    .listener(eventLogListener)          // optional; wraps every callback
    .build();
```

Omitting the backend and the workspace keeps each tool's own default: local shell execution and no directory confinement. When both are set, shell commands go through the backend and the file/search tools stay confined to the workspace. A custom backend keeps its own working directory — it is not also pointed at `workspace.root()`.

```java
// Plan mode: read-only file and search tools, no shell and no Write/Edit
List<ToolCallback> plan = AgentToolset.builder()
    .execBackend(backend)
    .workspace(workspace)
    .workspaceAccess(AgentToolset.WorkspaceAccess.READ)
    .build();

// Add a session tool, drop one built-in
List<ToolCallback> tools = AgentToolset.builder()
    .execBackend(backend)
    .workspace(workspace)
    .with(TodoWriteTool.builder().build())
    .without("Glob")
    .build();
```

`with(...)` accepts an `@Tool` object, a `ToolCallback`, or a `ToolCallbackProvider`. Extras are appended after the built-ins, then `without(...)` drops callbacks by tool name. An unknown name fails the build. `NONE` publishes no built-in tools, so the list is only what `with(...)` added.

## Permission tiers

`workspaceAccess` filters the default set. `READ` registers `Read` and not `Write` or `Edit` — the model is not offered the mutating file operations. `FileSystemTools` itself is unchanged; this is selection at assembly time, which is enough to stand up a read-only plan agent next to a read-write build agent ([#31](https://github.com/spring-ai-community/spring-ai-agent-utils/issues/31)).

| Tier | Callbacks |
|------|-----------|
| `NONE` | none |
| `READ` | `Read`, `Grep`, `Glob`, `ListDirectory` |
| `WRITE` | `READ` plus `Write`, `Edit` |
| `EXECUTE` | `WRITE` plus `Bash`, `BashOutput`, `KillShell` (the default) |

`execBackend` is consulted only at `EXECUTE`. Passing one at `READ` or `WRITE` is ignored so the same backend/workspace pair can be reused for a lower tier.

## System prompt

`MAIN_AGENT_SYSTEM_PROMPT_V2.md` is a [StringTemplate](https://www.stringtemplate.org/) rendered by Spring AI (`{if(FLAG)}` … `{endif}`). Sections that name Bash, Write, Edit, TodoWrite, WebFetch, or Task are included only when that tool was actually assembled. A read-only plan agent otherwise sees "use Bash" / "use Write" instructions and may call tools it was not given.

`AgentToolset.promptVariables(...)` builds the flag map from the callbacks (or from the same `@Tool` objects, `ToolCallback`s, and `ToolCallbackProvider`s accepted by `with(...)`). Every flag is always present, `true` or `false`. The default renderer throws if one is omitted.

| Flag | True when |
|------|-----------|
| `WORKSPACE_WRITE_AVAILABLE` | `Write` or `Edit` is present (`WRITE`, `EXECUTE`) |
| `WORKSPACE_EXECUTE_AVAILABLE` | `Bash` is present (`EXECUTE`) |
| `WORKSPACE_FULL_TOOLS_AVAILABLE` | `Read`, `Write`, `Edit`, and `Bash` are all present |
| `TODO_WRITE_AVAILABLE` | `TodoWrite` was added |
| `WEB_FETCH_AVAILABLE` | `WebFetch` was added |
| `TASK_AVAILABLE` | `Task` was added |
| `TASK_FILE_SEARCH_AVAILABLE` | `Task` registers an `Explore` subagent (`-Explore:` in the tool description) |

`without("Edit")` turns `WORKSPACE_FULL_TOOLS_AVAILABLE` off even on `EXECUTE`, because that section tells the model to call Edit. `without("Bash")` turns the execute and full-tool flags off.

```java
List<ToolCallback> tools = AgentToolset.builder()
    .workspace(workspace)
    .workspaceAccess(AgentToolset.WorkspaceAccess.READ)
    .build();

ChatClient chatClient = chatClientBuilder
    .defaultSystem(p -> p.text(systemPrompt)
        .param(AgentEnvironment.ENVIRONMENT_INFO_KEY, AgentEnvironment.info())
        .param(AgentEnvironment.GIT_STATUS_KEY, AgentEnvironment.gitStatus())
        .param(AgentEnvironment.AGENT_MODEL_KEY, agentModel)
        .param(AgentEnvironment.AGENT_MODEL_KNOWLEDGE_CUTOFF_KEY, agentModelKnowledgeCutoff)
        .params(AgentToolset.promptVariables(tools)))
    .defaultTools(tools)
    .build();
```

`builder.promptVariables()` is the same map `promptVariables(builder.build())` would return, including `with` / `without`.

## See also

- [Workspace & Exec SPI](WorkspaceAndExecSPI.md) — the two objects the factory binds
- [DockerCliExecBackend](DockerCliExecBackend.md) — the sandbox recipe
- [ToolCallListener](ToolCallListener.md) — what `listener(...)` attaches
