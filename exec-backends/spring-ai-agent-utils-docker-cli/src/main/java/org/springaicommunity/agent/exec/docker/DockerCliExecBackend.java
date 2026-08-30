/*
* Copyright 2026 - 2026 the original author or authors.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* https://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package org.springaicommunity.agent.exec.docker;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

import org.springaicommunity.agent.common.exec.ExecBackend;
import org.springaicommunity.agent.common.exec.ExecHandle;
import org.springaicommunity.agent.common.exec.ExecResult;
import org.springaicommunity.agent.common.exec.ExecSpec;
import org.springaicommunity.agent.common.workspace.Workspace;

/**
 * {@link ExecBackend} that runs commands inside a Docker container via the {@code docker}
 * CLI — no Java Docker client dependency, and the CLI resolves contexts, credential
 * helpers and non-standard daemon sockets exactly as the user's terminal does. Works with
 * any CLI-compatible runtime (Docker Desktop, Colima, podman via {@code podman-docker}).
 *
 * <p>
 * Two modes:
 * <ul>
 * <li><b>Managed container</b> — {@code image(...)} creates a long-lived container (kept
 * alive with a {@code sleep} entrypoint), optionally with a
 * {@code mount(hostDir, containerDir)} bind mount as the agent workspace.
 * {@link #close()} removes it; a JVM shutdown hook and a {@code docker} label
 * ({@value #CONTAINER_LABEL}) guard against orphans.</li>
 * <li><b>Attached container</b> — {@code containerId(...)} executes in an existing
 * running container whose lifecycle the caller owns.</li>
 * </ul>
 *
 * <p>
 * With a mount configured, {@link #workspace()} returns the {@link Workspace} that wires
 * the rest of the toolset: file/search tools operate host-side through the mount
 * (confined to it), while shell commands and path display use the in-container form.
 *
 * <p>
 * Execution details and caveats:
 * <ul>
 * <li>Commands run as {@code docker exec <container> <shell> -c <command>}; the client
 * process streams output and propagates the container command's exit code. Docker-level
 * failures surface as exit codes 125–127 with the CLI error on stderr.</li>
 * <li>Killing the client {@code docker exec} process does <em>not</em> kill the process
 * inside the container, so every command is wrapped to record its in-container PID under
 * {@code /tmp}; timeout and {@link ExecHandle#kill()} signal that PID (TERM, then KILL)
 * inside the container. Grandchildren the command itself detaches may survive.</li>
 * <li>The container needs a POSIX shell at the configured {@code shellCommand} (default
 * {@code /bin/sh}) and a writable {@code /tmp} for the PID files — on a read-only rootfs
 * the command still runs, but timeout/kill degrade to destroying only the client
 * process.</li>
 * </ul>
 *
 * @author Christian Tzolov
 */
public final class DockerCliExecBackend implements ExecBackend, AutoCloseable {

	/** Label set on managed containers, for identification and orphan cleanup. */
	public static final String CONTAINER_LABEL = "org.springaicommunity.agent.exec-backend";

	private static final String CONTAINER_LABEL_VALUE = "docker-cli";

	private static final AtomicLong HANDLE_SEQUENCE = new AtomicLong(System.currentTimeMillis());

	private static final long DOCKER_CONTROL_TIMEOUT_MILLIS = 30_000;

	private static final long CONTAINER_CREATE_TIMEOUT_MILLIS = 300_000; // image pull

	/**
	 * Wrapper executed as {@code <shell> -c WRAPPER <name> <pidfile> <shell...>
	 * <command>}: records the shell's own PID (kept by {@code exec}) so kill/timeout can
	 * signal the in-container process, then replaces itself with the real command shell.
	 */
	private static final String PID_WRAPPER = "echo $$ >\"$1\"; shift; exec \"$@\"";

	private final String dockerCommand;

	private final String containerId;

	private final boolean managedContainer;

	private final String containerWorkingDirectory;

	private final Map<String, String> environment;

	private final List<String> shellCommand;

	private final Path mountHostDirectory;

	private final String mountContainerDirectory;

	private final Thread shutdownHook;

	private volatile boolean closed;

	private DockerCliExecBackend(String dockerCommand, String containerId, boolean managedContainer,
			String containerWorkingDirectory, Map<String, String> environment, List<String> shellCommand,
			Path mountHostDirectory, String mountContainerDirectory) {
		this.dockerCommand = dockerCommand;
		this.containerId = containerId;
		this.managedContainer = managedContainer;
		this.containerWorkingDirectory = containerWorkingDirectory;
		this.environment = environment;
		this.shellCommand = shellCommand;
		this.mountHostDirectory = mountHostDirectory;
		this.mountContainerDirectory = mountContainerDirectory;
		if (managedContainer) {
			this.shutdownHook = new Thread(() -> removeContainer(), "docker-cli-exec-backend-cleanup");
			Runtime.getRuntime().addShutdownHook(this.shutdownHook);
		}
		else {
			this.shutdownHook = null;
		}
	}

	@Override
	public ExecResult run(ExecSpec spec) {
		if (this.closed) {
			return ExecResult.launchFailed("backend is closed");
		}
		String pidFile = pidFile("run_" + HANDLE_SEQUENCE.incrementAndGet());
		Process process;
		try {
			process = launch(spec, pidFile);
		}
		catch (IOException e) {
			return ExecResult.launchFailed(e.getMessage());
		}

		StringBuilder stdout = new StringBuilder();
		StringBuilder stderr = new StringBuilder();
		Thread stdoutReader = drain(process.getInputStream(), stdout);
		Thread stderrReader = drain(process.getErrorStream(), stderr);

		try {
			boolean completed = process.waitFor(spec.timeoutMillis(), TimeUnit.MILLISECONDS);
			if (!completed) {
				killInContainer(pidFile, process);
				return ExecResult.timedOut(snapshot(stdout), snapshot(stderr));
			}
			stdoutReader.join(1000);
			stderrReader.join(1000);
			return ExecResult.completed(process.exitValue(), snapshot(stdout), snapshot(stderr));
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			killInContainer(pidFile, process);
			return ExecResult.interrupted(snapshot(stdout), e.getMessage());
		}
	}

	@Override
	public ExecHandle start(ExecSpec spec) {
		if (this.closed) {
			throw new IllegalStateException("backend is closed");
		}
		String id = "docker_" + HANDLE_SEQUENCE.incrementAndGet();
		String pidFile = pidFile(id);
		try {
			return new DockerExecHandle(id, launch(spec, pidFile), pidFile);
		}
		catch (IOException e) {
			throw new IllegalStateException(e.getMessage(), e);
		}
	}

	/**
	 * The workspace formed by the configured bind mount: {@code root()} is the host
	 * directory (for the host-side file/search tools), {@code display(...)} rewrites host
	 * paths to their in-container form (for shell commands, skill base directories and
	 * environment prompts).
	 * @throws IllegalStateException when no mount is configured
	 */
	public Workspace workspace() {
		if (this.mountHostDirectory == null) {
			throw new IllegalStateException(
					"No workspace mount configured - use image(...) with mount(hostDir, containerDir)");
		}
		Path hostRoot = this.mountHostDirectory;
		String hostPrefix = hostRoot.toString();
		String containerPrefix = this.mountContainerDirectory;
		return new Workspace() {

			@Override
			public Path root() {
				return hostRoot;
			}

			@Override
			public String display(String hostPath) {
				if (hostPath.equals(hostPrefix)) {
					return containerPrefix;
				}
				if (hostPath.startsWith(hostPrefix + "/") || hostPath.startsWith(hostPrefix + "\\")) {
					return containerPrefix + "/" + hostPath.substring(hostPrefix.length() + 1).replace('\\', '/');
				}
				return hostPath;
			}

		};
	}

	/** The id of the container commands execute in. */
	public String containerId() {
		return this.containerId;
	}

	/**
	 * For a managed container, removes it ({@code docker rm -f}); for an attached
	 * container this is a no-op — the caller owns its lifecycle. Idempotent.
	 */
	@Override
	public void close() {
		if (this.closed) {
			return;
		}
		this.closed = true;
		if (this.managedContainer) {
			removeContainer();
			try {
				Runtime.getRuntime().removeShutdownHook(this.shutdownHook);
			}
			catch (IllegalStateException e) {
				// JVM already shutting down — the hook itself is running
			}
		}
	}

	private void removeContainer() {
		execLocal(List.of(this.dockerCommand, "rm", "-f", this.containerId), DOCKER_CONTROL_TIMEOUT_MILLIS);
	}

	private static String pidFile(String id) {
		return "/tmp/.agent-exec-" + id + ".pid";
	}

	private Process launch(ExecSpec spec, String pidFile) throws IOException {
		List<String> argv = new ArrayList<>();
		argv.add(this.dockerCommand);
		argv.add("exec");
		if (this.containerWorkingDirectory != null) {
			argv.add("-w");
			argv.add(this.containerWorkingDirectory);
		}
		Map<String, String> env = new LinkedHashMap<>(this.environment);
		env.putAll(spec.env());
		for (Map.Entry<String, String> entry : env.entrySet()) {
			argv.add("-e");
			argv.add(entry.getKey() + "=" + entry.getValue());
		}
		argv.add(this.containerId);
		// PID-recording wrapper, then the configured shell runs the actual command.
		// Everything is passed as argv elements - no host-side quoting is involved.
		argv.add(this.shellCommand.get(0));
		argv.add("-c");
		argv.add(PID_WRAPPER);
		argv.add("agent-exec"); // $0 of the wrapper shell
		argv.add(pidFile);
		argv.addAll(this.shellCommand);
		argv.add(spec.command());
		return new ProcessBuilder(argv).start();
	}

	/**
	 * Terminates the in-container process recorded in the pid file (TERM, short grace,
	 * then KILL), removes the pid file, and finally destroys the local client process.
	 */
	private void killInContainer(String pidFile, Process clientProcess) {
		signalPid(pidFile, "TERM", false);
		try {
			clientProcess.waitFor(2, TimeUnit.SECONDS);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		signalPid(pidFile, "KILL", true);
		clientProcess.destroy();
		try {
			if (!clientProcess.waitFor(5, TimeUnit.SECONDS)) {
				clientProcess.destroyForcibly();
			}
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			clientProcess.destroyForcibly();
		}
	}

	private void signalPid(String pidFile, String signal, boolean removePidFile) {
		String script = "pid=$(cat \"" + pidFile + "\" 2>/dev/null); [ -n \"$pid\" ] && kill -" + signal
				+ " $pid 2>/dev/null" + (removePidFile ? "; rm -f \"" + pidFile + "\"" : "") + "; true";
		execLocal(List.of(this.dockerCommand, "exec", this.containerId, this.shellCommand.get(0), "-c", script),
				DOCKER_CONTROL_TIMEOUT_MILLIS);
	}

	/**
	 * Runs a local control command (never model-authored), returning [exit, out, err].
	 */
	private static ControlResult execLocal(List<String> argv, long timeoutMillis) {
		try {
			Process process = new ProcessBuilder(argv).start();
			StringBuilder stdout = new StringBuilder();
			StringBuilder stderr = new StringBuilder();
			Thread outReader = drain(process.getInputStream(), stdout);
			Thread errReader = drain(process.getErrorStream(), stderr);
			if (!process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)) {
				process.destroyForcibly();
				return new ControlResult(-1, snapshot(stdout), "timed out: " + String.join(" ", argv));
			}
			outReader.join(1000);
			errReader.join(1000);
			return new ControlResult(process.exitValue(), snapshot(stdout), snapshot(stderr));
		}
		catch (IOException e) {
			return new ControlResult(-1, "", e.getMessage());
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return new ControlResult(-1, "", "interrupted");
		}
	}

	private record ControlResult(int exitCode, String stdout, String stderr) {
	}

	private static String snapshot(StringBuilder buffer) {
		synchronized (buffer) {
			return buffer.toString();
		}
	}

	private static Thread drain(InputStream stream, StringBuilder target) {
		Thread reader = new Thread(() -> {
			try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(stream))) {
				String line;
				while ((line = bufferedReader.readLine()) != null) {
					synchronized (target) {
						target.append(line).append("\n");
					}
				}
			}
			catch (IOException e) {
				// Process terminated or stream closed
			}
		});
		reader.setDaemon(true);
		reader.start();
		return reader;
	}

	/**
	 * Background handle over a {@code docker exec} client process. Output, liveness and
	 * exit code come from the client (docker propagates the container command's exit
	 * code); kill signals the recorded in-container PID first, so the process does not
	 * survive inside the container.
	 */
	private final class DockerExecHandle implements ExecHandle {

		private final String id;

		private final Process process;

		private final String pidFile;

		private final StringBuilder stdout = new StringBuilder();

		private final StringBuilder stderr = new StringBuilder();

		private int lastStdoutPosition = 0;

		private int lastStderrPosition = 0;

		DockerExecHandle(String id, Process process, String pidFile) {
			this.id = id;
			this.process = process;
			this.pidFile = pidFile;
			drain(process.getInputStream(), this.stdout);
			drain(process.getErrorStream(), this.stderr);
		}

		@Override
		public String id() {
			return this.id;
		}

		@Override
		public boolean isAlive() {
			return this.process.isAlive();
		}

		@Override
		public String newOutput(String filterRegex) {
			StringBuilder result = new StringBuilder();
			synchronized (this.stdout) {
				String newStdout = this.stdout.substring(this.lastStdoutPosition);
				newStdout = filter(newStdout, filterRegex);
				if (!newStdout.isEmpty()) {
					result.append("STDOUT:\n").append(newStdout);
				}
				this.lastStdoutPosition = this.stdout.length();
			}
			synchronized (this.stderr) {
				String newStderr = this.stderr.substring(this.lastStderrPosition);
				newStderr = filter(newStderr, filterRegex);
				if (!newStderr.isEmpty()) {
					if (result.length() > 0) {
						result.append("\n");
					}
					result.append("STDERR:\n").append(newStderr);
				}
				this.lastStderrPosition = this.stderr.length();
			}
			return result.toString();
		}

		@Override
		public int exitCode() {
			return this.process.exitValue();
		}

		@Override
		public void kill() {
			killInContainer(this.pidFile, this.process);
		}

	}

	private static String filter(String output, String filterRegex) {
		if (filterRegex == null || filterRegex.isEmpty() || output.isEmpty()) {
			return output;
		}
		Pattern pattern = Pattern.compile(filterRegex);
		StringBuilder filtered = new StringBuilder();
		for (String line : output.split("\n")) {
			if (pattern.matcher(line).find()) {
				filtered.append(line).append("\n");
			}
		}
		return filtered.toString();
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private String dockerCommand = "docker";

		private String containerId;

		private String image;

		private Path mountHostDirectory;

		private String mountContainerDirectory;

		private String containerWorkingDirectory;

		private final Map<String, String> environment = new HashMap<>();

		private List<String> shellCommand = List.of("/bin/sh", "-c");

		private Builder() {
		}

		/** Docker CLI executable. Default {@code docker}; e.g. {@code podman}. */
		public Builder dockerCommand(String dockerCommand) {
			this.dockerCommand = dockerCommand;
			return this;
		}

		/**
		 * Attach to an existing running container; the caller owns its lifecycle.
		 * Mutually exclusive with {@link #image(String)}.
		 */
		public Builder containerId(String containerId) {
			this.containerId = containerId;
			return this;
		}

		/**
		 * Create and manage a long-lived container from this image (removed on
		 * {@link DockerCliExecBackend#close()} or JVM shutdown). The image needs a POSIX
		 * shell and a {@code sleep} binary. Mutually exclusive with
		 * {@link #containerId(String)}.
		 */
		public Builder image(String image) {
			this.image = image;
			return this;
		}

		/**
		 * Bind-mounts a host directory into the managed container — the agent workspace.
		 * Also becomes the default working directory for commands, and enables
		 * {@link DockerCliExecBackend#workspace()}. Managed mode only.
		 */
		public Builder mount(Path hostDirectory, String containerDirectory) {
			this.mountHostDirectory = hostDirectory;
			this.mountContainerDirectory = containerDirectory;
			return this;
		}

		/** Working directory for executed commands. Default: the mount target, if any. */
		public Builder containerWorkingDirectory(String containerWorkingDirectory) {
			this.containerWorkingDirectory = containerWorkingDirectory;
			return this;
		}

		/** Environment variables set for every executed command. */
		public Builder environment(Map<String, String> environment) {
			if (environment != null) {
				this.environment.putAll(environment);
			}
			return this;
		}

		/** In-container shell. Default {@code /bin/sh -c}; must be POSIX-compatible. */
		public Builder shellCommand(String... shellCommand) {
			this.shellCommand = List.of(shellCommand);
			return this;
		}

		/**
		 * Builds the backend. In managed mode this creates and starts the container
		 * (pulling the image if needed) and fails with {@link IllegalStateException} on
		 * any docker error.
		 */
		public DockerCliExecBackend build() {
			if ((this.containerId == null) == (this.image == null)) {
				throw new IllegalStateException("Set exactly one of containerId (attach) or image (managed)");
			}
			if (this.shellCommand.size() < 2) {
				throw new IllegalStateException("shellCommand must be a shell plus its command flag, e.g. /bin/sh -c");
			}
			if (this.mountHostDirectory != null && this.image == null) {
				throw new IllegalStateException(
						"mount(...) requires image(...) - an attached container's mounts are fixed");
			}
			Path mountHost = (this.mountHostDirectory != null) ? this.mountHostDirectory.toAbsolutePath().normalize()
					: null;
			String workDir = this.containerWorkingDirectory;
			if (workDir == null && this.mountContainerDirectory != null) {
				workDir = this.mountContainerDirectory;
			}
			String container = this.containerId;
			boolean managed = false;
			if (this.image != null) {
				container = createContainer(mountHost, workDir);
				managed = true;
			}
			return new DockerCliExecBackend(this.dockerCommand, container, managed, workDir,
					Map.copyOf(this.environment), this.shellCommand, mountHost, this.mountContainerDirectory);
		}

		private String createContainer(Path mountHost, String workDir) {
			// Deterministic name so both failure branches can clean up: when 'docker
			// run -d' fails to *start* the container, the daemon may still have
			// *created* it, and its id is not reliably reported.
			String name = "agent-exec-" + Long.toUnsignedString(System.currentTimeMillis(), 36) + "-"
					+ HANDLE_SEQUENCE.incrementAndGet();
			List<String> argv = new ArrayList<>(List.of(this.dockerCommand, "run", "-d", "--name", name, "--label",
					CONTAINER_LABEL + "=" + CONTAINER_LABEL_VALUE));
			if (mountHost != null) {
				argv.add("-v");
				argv.add(mountHost + ":" + this.mountContainerDirectory);
			}
			if (workDir != null) {
				argv.add("-w");
				argv.add(workDir);
			}
			// Keep-alive entrypoint; ~68 years, portable across sh/busybox images
			argv.add("--entrypoint");
			argv.add("sleep");
			argv.add(this.image);
			argv.add("2147483647");
			ControlResult result = execLocal(argv, CONTAINER_CREATE_TIMEOUT_MILLIS);
			if (result.exitCode() != 0) {
				execLocal(List.of(this.dockerCommand, "rm", "-f", name), DOCKER_CONTROL_TIMEOUT_MILLIS);
				throw new IllegalStateException("Failed to create container from image '" + this.image + "': "
						+ (result.stderr().isBlank() ? result.stdout() : result.stderr()).trim());
			}
			String container = result.stdout().trim();
			// Fail fast on a container that died right after starting (e.g. an image
			// whose sleep rejects the keep-alive argument) instead of surfacing
			// confusing daemon errors on every later command.
			try {
				Thread.sleep(250);
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			ControlResult inspect = execLocal(
					List.of(this.dockerCommand, "inspect", "-f", "{{.State.Running}}", container),
					DOCKER_CONTROL_TIMEOUT_MILLIS);
			if (!"true".equals(inspect.stdout().trim())) {
				execLocal(List.of(this.dockerCommand, "rm", "-f", container), DOCKER_CONTROL_TIMEOUT_MILLIS);
				throw new IllegalStateException("Container from image '" + this.image
						+ "' is not running - the image must provide a POSIX shell and a 'sleep' binary "
						+ "(e.g. alpine, busybox, debian; distroless images do not qualify)");
			}
			return container;
		}

	}

}
