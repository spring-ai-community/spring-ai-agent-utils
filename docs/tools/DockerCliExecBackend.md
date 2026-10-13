# DockerCliExecBackend - Sandboxed Command Execution

`DockerCliExecBackend` is an `ExecBackend` implementation (module `spring-ai-agent-utils-docker-cli`) that runs model-authored shell commands inside a Docker container instead of on the host JVM. It drives Docker through the `docker` CLI — no Java Docker client dependency — so contexts, credential helpers and non-standard daemon sockets are resolved exactly as in your terminal, and any CLI-compatible runtime works (Docker Desktop, Colima, podman via `podman-docker`).

```xml
<dependency>
    <groupId>org.springaicommunity</groupId>
    <artifactId>spring-ai-agent-utils-docker-cli</artifactId>
</dependency>
```

## The full sandbox recipe

The headline use case: one managed container per agent/session, a bind-mounted host directory as the workspace, and the whole toolset wired from two objects:

```java
Path hostWorkspace = Files.createTempDirectory("agent-session");

try (DockerCliExecBackend backend = DockerCliExecBackend.builder()
        .image("eclipse-temurin:17-jdk-alpine")       // needs a POSIX shell + sleep
        .mount(hostWorkspace, "/workspace")           // the agent workspace
        .build()) {

    Workspace workspace = backend.workspace();        // host root + /workspace display mapping

    // Shell commands execute inside the container
    ShellTools shell = ShellTools.builder().execBackend(backend).build();

    // File/search tools operate host-side through the mount, confined to it
    FileSystemTools files = FileSystemTools.builder().workspace(workspace).build();
    GrepTool grep = GrepTool.builder().workspace(workspace).build();
    GlobTool glob = GlobTool.builder().workspace(workspace).build();

    // Skill base directories and environment prompts show container paths
    ToolCallbackProvider skills = SkillsTool.builder()
        .addSkillsDirectory(hostWorkspace.resolve("skills").toString())
        .workspace(workspace)
        .build();
    String envInfo = AgentEnvironment.info(workspace);
    String gitStatus = AgentEnvironment.gitStatus(backend);   // git runs in the container
}
```

Every path the model sees is a container path; every command the model writes runs in the container; every file the model touches stays inside the mount. No tool code changes.

## Two modes

**Managed container** — `image(...)` creates a long-lived container (kept alive with a `sleep` entrypoint) and owns its lifecycle:

```java
DockerCliExecBackend backend = DockerCliExecBackend.builder()
    .image("alpine:3.20")
    .mount(hostDir, "/workspace")                 // optional; enables workspace()
    .containerWorkingDirectory("/workspace")      // default: the mount target
    .environment(Map.of("LANG", "C"))             // env vars for every command
    .shellCommand("/bin/bash", "-c")              // default: /bin/sh -c
    .dockerCommand("podman")                      // default: docker
    .build();
...
backend.close();                                  // docker rm -f
```

`close()` removes the container. A JVM shutdown hook does the same if `close()` never runs, and managed containers carry the label `org.springaicommunity.agent.exec-backend=docker-cli`, so stragglers from crashed JVMs can be swept with:

```bash
docker rm -f $(docker ps -aq --filter label=org.springaicommunity.agent.exec-backend=docker-cli)
```

**Attached container** — `containerId(...)` executes in an existing running container whose lifecycle you own (Kubernetes sidecar, compose service, testcontainer). `close()` is a no-op; `mount(...)` is not available (an existing container's mounts are fixed), so `workspace()` requires managed mode.

## Semantics and caveats

- **Exit codes and streams** — `docker exec` propagates the container command's exit code and separate stdout/stderr, so `ExecResult` looks exactly as with `LocalExecBackend`. Docker-level failures (container not running, daemon down) surface as exit codes 125–127 with the CLI error on stderr.
- **Timeout and kill reach into the container.** Killing the client `docker exec` process does not kill the in-container process (a Docker limitation), so every command is wrapped to record its in-container PID under `/tmp`; timeouts and `KillShell` signal that PID (TERM, then KILL after a grace period) inside the container. Processes the command itself detaches into the background may survive — same caveat as the local backend.
- **Background shells** (`BashOutput`/`KillShell`) get handle ids in the `docker_<n>` namespace and stream incremental output with the same cursor semantics as local shells.
- **Image requirements** — a POSIX shell at the configured `shellCommand` path and a `sleep` binary (managed mode keep-alive); `alpine`, `busybox`, `debian`, `eclipse-temurin` images all qualify, distroless images do not. `build()` fails fast with a clear message when the managed container cannot start or dies immediately. The in-container kill mechanism also needs a writable `/tmp` for its PID files — on a read-only rootfs commands still run, but timeout/kill degrade to destroying only the client `docker exec` process.
- **Runtime requirement** — the `docker` CLI must be on the JVM's PATH. If the JVM itself runs in a container, mount the Docker socket and install the CLI, or attach to a pre-created container instead.

## See also

- [Workspace & Exec SPI](WorkspaceAndExecSPI.md) — overview of the two seams this backend implements
- [ShellTools](ShellTools.md#execution-backend--working-directory) — the `ExecBackend` seam this plugs into
- [AgentEnvironment](AgentEnvironment.md) — workspace/backend-aware environment prompts
- [FileSystemTools](FileSystemTools.md) / [GrepTool](GrepTool.md) / [GlobTool](GlobTool.md) — workspace confinement for the host-side file tools
