# Workspace & Exec SPI - Sandboxing Overview

The agent tools are decoupled from the host machine through two small SPIs in `spring-ai-agent-utils-common`. Together they answer the two questions any sandboxed agent deployment has to answer:

| Seam | Package | Question it answers |
|------|---------|---------------------|
| **`ExecBackend`** | `org.springaicommunity.agent.common.exec` | *Where do shell commands run?* |
| **`Workspace`** | `org.springaicommunity.agent.common.workspace` | *Where do files live, and how are paths shown to the model?* |

Tools describe **what** to do; the backend and workspace decide **where and how**. Swapping the host for a container changes configuration, never tool code.

## ExecBackend — where commands run

```java
public interface ExecBackend {
    ExecResult run(ExecSpec spec);      // synchronous, honors the spec's timeout
    ExecHandle start(ExecSpec spec);    // background: poll output, kill
}
```

- **`ExecSpec`** — the command line as the model authored it, a wall-clock timeout (policy owned by the spec: default 2 min, capped at 10 min), and per-invocation environment variables. Shell selection and working directory are deliberately *backend* concerns.
- **`ExecResult`** — status (`COMPLETED`, `TIMED_OUT`, `LAUNCH_FAILED`, `INTERRUPTED`), exit code, separate stdout/stderr. Failures are reported, never thrown.
- **`ExecHandle`** — a background command: `isAlive()`, cursor-based `newOutput(filter)` (each call returns only output produced since the last one), `exitCode()`, `kill()` (graceful, then forced).

**Who uses it:** [ShellTools](ShellTools.md) routes `Bash`/`BashOutput`/`KillShell` through the configured backend, and [AgentEnvironment](AgentEnvironment.md) runs its git-status commands through it, so a sandboxed agent reports the sandbox's view of the repository.

**Implementations:**

| Implementation | Module | Runs commands |
|---|---|---|
| `LocalExecBackend` (default) | `spring-ai-agent-utils` | On the host JVM, with working directory, clean-environment mode and shell selection |
| [`DockerCliExecBackend`](DockerCliExecBackend.md) | `spring-ai-agent-utils-docker-cli` | Inside a Docker container, via the `docker` CLI |

Custom implementations are a small class: implement `run` (and `start` if you support background shells — throwing `UnsupportedOperationException` there is acceptable for remote/queued executors). An adapter over [agent-sandbox](https://github.com/spring-ai-community/agent-sandbox)'s `Sandbox.exec()` is straightforward for the synchronous path — the shapes are compatible.

## Workspace — where files live, and what the model sees

```java
@FunctionalInterface
public interface Workspace {
    Path root();                                     // host-side root directory
    default String display(String hostPath) { ... }  // host path -> model-visible path
    static Workspace local(Path root) { ... }        // identity display
}
```

A workspace is a root directory plus a *display mapping*. For local execution the mapping is the identity; for a container with a bind-mounted workspace it rewrites host paths to their in-container form, so the model is never shown a path its shell cannot use.

**Who uses it** — one `workspace(...)` call per tool builder, with per-tool semantics:

| Tool | `workspace(...)` effect |
|---|---|
| [GrepTool](GrepTool.md) / [GlobTool](GlobTool.md) / ListDirectoryTool | Working directory **and** allowed-directory confinement |
| [FileSystemTools](FileSystemTools.md) | Allowed-directory confinement |
| [ShellTools](ShellTools.md) | Working directory only (a shell can't be confined from Java — that's what a sandboxed `ExecBackend` is for) |
| [SkillsTool](SkillsTool.md) | Announces skill base directories via `display(...)` |
| [AgentEnvironment](AgentEnvironment.md) | Environment-info block describes the workspace, not the host |

## How the two compose

The file/search tools run **host-side**, confined to the workspace root; shell commands run **in the backend**. With a bind mount connecting the two, both operate on the same files and the model sees one consistent world:

```java
try (DockerCliExecBackend backend = DockerCliExecBackend.builder()
        .image("alpine:3.20")
        .mount(hostDir, "/workspace")
        .build()) {

    Workspace workspace = backend.workspace();   // hostDir root, /workspace display

    ShellTools shell = ShellTools.builder().execBackend(backend).build();
    FileSystemTools files = FileSystemTools.builder().workspace(workspace).build();
    GrepTool grep = GrepTool.builder().workspace(workspace).build();
    String envInfo = AgentEnvironment.info(workspace);
}
```

See [DockerCliExecBackend](DockerCliExecBackend.md) for the complete recipe and its operational caveats.

## Module map

| Module | Contents |
|---|---|
| `spring-ai-agent-utils-common` | The SPIs: `ExecBackend`, `ExecSpec`, `ExecResult`, `ExecHandle`, `Workspace` — the `exec` and `workspace` packages are plain JDK, so backend implementations need no Spring on their classpath |
| `spring-ai-agent-utils` | The tools plus `LocalExecBackend` (host default) |
| `exec-backends/spring-ai-agent-utils-docker-cli` | `DockerCliExecBackend` (Docker sandbox) |

The `exec-backends/` folder is the home for further backend implementations as they land.
