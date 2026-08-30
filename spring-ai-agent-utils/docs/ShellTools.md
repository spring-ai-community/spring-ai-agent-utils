# ShellTools

A comprehensive shell execution toolkit that provides both synchronous and background command execution with full process management capabilities.

**Features:**
- Synchronous command execution with configurable timeout
- Background process execution for long-running commands
- Real-time output monitoring for background processes
- Separate stdout and stderr capture
- Process lifecycle management (kill, status checks)
- Regex filtering for output
- Cross-platform support (Windows/Unix)
- Automatic output truncation for large outputs
- Exit code reporting

## Available Tools

### 1. Bash - Execute Shell Commands

The primary tool for executing shell commands either synchronously or in the background.

**Parameters:**
- `command` (required) - The shell command to execute
- `timeout` (optional) - Timeout in milliseconds (max 600000ms / 10 minutes, default: 120000ms / 2 minutes)
- `description` (optional) - Clear description of what the command does (5-10 words)
- `runInBackground` (optional) - Set to `true` to run command in background

**Basic Usage:**

```java
ShellTools shellTools = ShellTools.builder().build();

// Synchronous execution
String result = shellTools.bash(
    "ls -la",           // command
    null,               // timeout (uses default 2 minutes)
    "List all files",   // description
    null               // runInBackground (false)
);

// With custom timeout
String result = shellTools.bash(
    "npm install",
    300000L,            // 5 minute timeout
    "Install dependencies",
    null
);

// Background execution
String result = shellTools.bash(
    "npm run dev",
    null,
    "Start dev server",
    true               // Run in background
);
// Returns: "bash_id: shell_1234567890"
```

**Output Format:**
```
bash_id: shell_1234567890

[command output]

STDERR:
[error output if any]

Exit code: [exit code if non-zero]
```

**Important Notes:**
- Commands timeout after 2 minutes by default (configurable up to 10 minutes)
- Output is truncated if it exceeds 30,000 characters
- Background commands return immediately with a `bash_id` for monitoring
- Use Unix shell (`/bin/bash`) on Linux/Mac, CMD on Windows
- Don't use `&` when using `runInBackground=true` (handled automatically)

### 2. BashOutput - Monitor Background Processes

Retrieves output from running or completed background shell processes.

**Parameters:**
- `bash_id` (required) - The ID of the background shell (from Bash tool)
- `filter` (optional) - Regex pattern to filter output lines

**Usage:**

```java
// Start background process
String startResult = shellTools.bash(
    "mvn clean install",
    null,
    "Build project",
    true
);
// Extract bash_id from result (e.g., "shell_1234567890")

// Monitor output
String output = shellTools.bashOutput(
    "shell_1234567890",  // bash_id
    null                 // no filter
);

// Filter output with regex (only show ERROR lines)
String filteredOutput = shellTools.bashOutput(
    "shell_1234567890",
    ".*ERROR.*"         // regex filter
);
```

**Output Format:**
```
Shell ID: shell_1234567890
Status: Running (or Completed)
Exit code: 0

New output:
STDOUT:
[new stdout content since last check]

STDERR:
[new stderr content since last check]
```

**Key Features:**
- **Incremental output**: Only returns NEW output since last check
- **Regex filtering**: Filter output lines matching a pattern (filtered lines are consumed)
- **Status tracking**: Shows if process is running or completed
- **Exit code**: Available when process completes

**Important Notes:**
- Only shows output generated since the last `BashOutput` call
- Filtered lines are marked as "read" and won't appear in subsequent calls
- Returns "No new output since last check" if nothing new is available

### 3. KillShell - Terminate Background Processes

Gracefully terminates a running background shell process.

**Parameters:**
- `bash_id` (required) - The ID of the background shell to kill

**Usage:**

```java
// Kill a background process
String result = shellTools.killShell(
    "shell_1234567890"
);
// Returns: "Successfully killed shell: shell_1234567890"
```

**Termination Process:**
1. Attempts graceful shutdown with `destroy()`
2. Waits up to 5 seconds for process to terminate
3. Forces termination with `destroyForcibly()` if needed
4. Removes process from active shell tracking

**Example Workflow:**

```java
ShellTools shellTools = ShellTools.builder().build();

// 1. Start a long-running background task
String start = shellTools.bash(
    "python train_model.py",
    null,
    "Train ML model",
    true
);
// Returns: "bash_id: shell_1234567890 ..."

// 2. Periodically check progress
String output1 = shellTools.bashOutput("shell_1234567890", ".*epoch.*");
// Shows only lines containing "epoch"

Thread.sleep(5000);

String output2 = shellTools.bashOutput("shell_1234567890", null);
// Shows all new output since last check

// 3. Kill if needed
String killResult = shellTools.killShell("shell_1234567890");
// Returns: "Successfully killed shell: shell_1234567890"
```

**Cross-Platform Support:**

| Platform | Shell Used | Example |
|----------|------------|---------|
| Windows | `cmd.exe /c` | `cmd.exe /c dir` |
| Linux/Mac | `/bin/bash -c` | `/bin/bash -c ls -la` |

**Configuration & Limits:**

| Setting | Default | Maximum | Description |
|---------|---------|---------|-------------|
| Timeout | 120000ms (2 min) | 600000ms (10 min) | Command execution timeout |
| Output Length | N/A | 30000 chars | Output truncated if exceeded |

**Best Practices:**

1. **Use descriptive names**: Provide clear descriptions for better logging
   ```java
   shellTools.bash("git status", null, "Check git status", null);
   ```

2. **Background for long tasks**: Use background execution for commands that take > 30 seconds
   ```java
   shellTools.bash("npm run test", null, "Run test suite", true);
   ```

3. **Monitor background processes**: Regularly check output of background processes
   ```java
   String output = shellTools.bashOutput(bashId, null);
   ```

4. **Filter wisely**: Use regex filters to focus on relevant output
   ```java
   // Only show test failures
   shellTools.bashOutput(bashId, ".*(FAIL|ERROR).*");
   ```

5. **Clean up**: Kill background processes when no longer needed
   ```java
   shellTools.killShell(bashId);
   ```

## Execution backend & working directory

`ShellTools` executes commands through a pluggable `ExecBackend` (SPI in
`spring-ai-agent-utils-common`, package `org.springaicommunity.agent.common.exec`).
The default `LocalExecBackend` runs processes on the host JVM; alternative
implementations can execute inside a container or on a remote worker without any
tool changes — see [DockerCliExecBackend](DockerCliExecBackend.md) for the
Docker-based sandbox backend.

```java
// Confine local execution to a workspace directory (per session/agent)
ShellTools tools = ShellTools.builder()
    .workingDirectory(Path.of("/workspace/session-42"))
    .build();

// Equivalent, sharing one Workspace definition with the file/search tools
ShellTools tools = ShellTools.builder()
    .workspace(Workspace.local(Path.of("/workspace/session-42")))
    .build();

// Or supply a fully configured backend
ShellTools tools = ShellTools.builder()
    .execBackend(LocalExecBackend.builder()
        .workingDirectory(workDir)
        .cleanEnvironment(true)                 // child processes do NOT inherit the JVM env
        .environment(Map.of("LANG", "C"))
        .build())
    .build();
```

Security notes:

- **Working directory** — without one, commands run in the JVM's current directory.
  Always set a per-session working directory when commands are model-authored.
- **Environment** — by default child processes inherit the full JVM environment
  (historical behavior). Enable `cleanEnvironment(true)` so secrets in host
  environment variables are not visible to model-authored commands.
- **Shell namespaces** — background shells (`BashOutput`/`KillShell`) are tracked
  per `ShellTools` instance, not globally. Agents holding *different* instances have
  separate shell namespaces; agents that *share* an instance (for example Claude
  subagents, which reuse the single `ShellTools` created by `ClaudeSubagentType`)
  share a namespace and can see each other's shells. Migration note: the registry
  was previously JVM-global — if you relied on reading or killing a shell from a
  different `ShellTools` instance, share one instance between those agents
  explicitly.
