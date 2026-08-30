# Migration Guide: 0.11.0 to 0.12.0

This release introduces the sandboxing foundation: the `ExecBackend` and `Workspace` SPIs, directory confinement for the search tools, and the `spring-ai-agent-utils-docker-cli` module. **There are no breaking API changes** — all existing code compiles and runs unchanged. There is **one behavioral change** to review (background shells) and a few output-level changes worth knowing about.

## Dependency Version

```xml
<dependency>
    <groupId>org.springaicommunity</groupId>
    <artifactId>spring-ai-agent-utils</artifactId>
    <version>0.12.0</version>
</dependency>
```

The Spring AI dependency is unchanged (2.0.1).

## Behavioral change: background shells are per-instance

In 0.11.0 the background-shell registry was JVM-global: any `ShellTools` instance could read (`BashOutput`) or kill (`KillShell`) a shell started through any other instance. In 0.12.0 each `ShellTools` instance owns its background shells — separate instances have separate shell namespaces.

This is the right default (separate agents/sessions no longer see each other's shells), but if you relied on cross-instance visibility, those lookups now return `Error: No background shell found with ID ...` with no compile-time signal.

**Before (0.11.0 — worked by accident of the global registry):**

```java
ShellTools agentA = ShellTools.builder().build();
ShellTools agentB = ShellTools.builder().build();
// agentB could read/kill shells started by agentA
```

**After (0.12.0 — share one instance where shared visibility is intended):**

```java
ShellTools shared = ShellTools.builder().build();
// register the same instance with both agents
```

Note: Claude subagents already share the single `ShellTools` created by `ClaudeSubagentType`, so they keep a shared namespace without changes.

## Deprecations

Move off the deprecated no-argument constructors to the builders — they now carry the configuration that matters (execution backend, working directory, confinement):

```java
// Before
ShellTools shell = new ShellTools();
GrepTool grep = new GrepTool();

// After
ShellTools shell = ShellTools.builder().workingDirectory(workDir).build();
GrepTool grep = GrepTool.builder().workingDirectory(workDir).build();
```

## Output-level changes (no code impact)

These change what the model (or a log reader) sees, not any API:

- **`AgentEnvironment.gitStatus()`** no longer leaks git error text into the rendered block. Previously, in a repository with no local `main`/`master` and no `origin/HEAD` symref, the "Main branch" line could contain a literal `fatal: ...` message; failed git commands now contribute empty strings and the main-branch detection falls back to `main`. Git commands also run through the `ExecBackend` SPI (platform shell) instead of a private `ProcessBuilder`.
- **`FileSystemTools.read`** header says `Showing lines 1-N of at least M` when the line limit truncates the read, instead of implying the file was fully counted.
- **`Bash` with a blank command** returns `Error: command must not be blank` instead of spawning a shell.
- Synchronous `Bash` run ids moved to their own `shell_run_<n>` namespace so they can never collide with background shell ids.

## New in 0.12.0 (opt-in, no migration required)

- **`ExecBackend` SPI** (`org.springaicommunity.agent.common.exec`) — pluggable command execution; `ShellTools.builder().execBackend(...)` and `AgentEnvironment.gitStatus(ExecBackend)`. `LocalExecBackend` (the default) adds `workingDirectory`, `cleanEnvironment` and `shellCommand` options.
- **`Workspace` SPI** (`org.springaicommunity.agent.common.workspace`) — root directory + host-to-model path display; one-call `workspace(...)` option on the tool builders, `SkillsTool` base-path mapping, `AgentEnvironment.info(Workspace)`.
- **Directory confinement for search tools** — `allowedDirectory(...)` on `GrepTool`, `GlobTool` and `ListDirectoryTool`, sharing the FileSystemTools jail semantics. Note: when confinement is configured, directory traversal does **not** follow symbolic links (unconfined behavior is unchanged).
- **`spring-ai-agent-utils-docker-cli`** — a Docker `ExecBackend` running commands in a sandbox container (new `exec-backends/` module group, managed by the BOM).

See [Workspace & Exec SPI](WorkspaceAndExecSPI.md) for the overview and [DockerCliExecBackend](DockerCliExecBackend.md) for the full sandbox recipe.
