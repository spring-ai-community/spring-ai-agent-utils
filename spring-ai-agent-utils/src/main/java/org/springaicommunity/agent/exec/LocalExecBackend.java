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
package org.springaicommunity.agent.exec;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

import org.springaicommunity.agent.common.exec.ExecBackend;
import org.springaicommunity.agent.common.exec.ExecHandle;
import org.springaicommunity.agent.common.exec.ExecResult;
import org.springaicommunity.agent.common.exec.ExecSpec;

/**
 * {@link ExecBackend} that runs commands on the host JVM via {@link ProcessBuilder}.
 * Compared to the historical inline execution in {@code ShellTools}, this implementation
 * supports a working directory, a clean-environment mode (child processes no longer
 * inherit the full JVM environment, including its secrets, unless you opt in), and
 * per-instance background handles instead of a process-global registry.
 *
 * @author Christian Tzolov
 */
public final class LocalExecBackend implements ExecBackend {

	private static final AtomicLong HANDLE_SEQUENCE = new AtomicLong(System.currentTimeMillis());

	private final Path workingDirectory;

	private final boolean cleanEnvironment;

	private final Map<String, String> environment;

	private final List<String> shellCommand;

	private LocalExecBackend(Path workingDirectory, boolean cleanEnvironment, Map<String, String> environment,
			List<String> shellCommand) {
		this.workingDirectory = workingDirectory;
		this.cleanEnvironment = cleanEnvironment;
		this.environment = environment;
		this.shellCommand = shellCommand;
	}

	@Override
	public ExecResult run(ExecSpec spec) {
		Process process;
		try {
			process = launch(spec);
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
				destroy(process);
				return ExecResult.timedOut(snapshot(stdout), snapshot(stderr));
			}
			stdoutReader.join(1000);
			stderrReader.join(1000);
			return ExecResult.completed(process.exitValue(), snapshot(stdout), snapshot(stderr));
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			destroy(process);
			return ExecResult.interrupted(snapshot(stdout), e.getMessage());
		}
	}

	@Override
	public ExecHandle start(ExecSpec spec) {
		String id = "shell_" + HANDLE_SEQUENCE.incrementAndGet();
		try {
			return new LocalExecHandle(id, launch(spec));
		}
		catch (IOException e) {
			throw new IllegalStateException(e.getMessage(), e);
		}
	}

	/** Reads a drain buffer under the same lock its reader thread appends with. */
	private static String snapshot(StringBuilder buffer) {
		synchronized (buffer) {
			return buffer.toString();
		}
	}

	private Process launch(ExecSpec spec) throws IOException {
		List<String> command = new ArrayList<>(this.shellCommand);
		command.add(spec.command());
		ProcessBuilder processBuilder = new ProcessBuilder(command);
		processBuilder.redirectErrorStream(false);
		if (this.workingDirectory != null) {
			processBuilder.directory(this.workingDirectory.toFile());
		}
		if (this.cleanEnvironment) {
			processBuilder.environment().clear();
		}
		processBuilder.environment().putAll(this.environment);
		processBuilder.environment().putAll(spec.env());
		return processBuilder.start();
	}

	private static Thread drain(java.io.InputStream stream, StringBuilder target) {
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

	private static void destroy(Process process) {
		process.destroy();
		try {
			if (!process.waitFor(5, TimeUnit.SECONDS)) {
				process.destroyForcibly();
			}
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			process.destroyForcibly();
		}
	}

	/** Background handle over a host process, with cursor-based incremental output. */
	private static final class LocalExecHandle implements ExecHandle {

		private final String id;

		private final Process process;

		private final StringBuilder stdout = new StringBuilder();

		private final StringBuilder stderr = new StringBuilder();

		private int lastStdoutPosition = 0;

		private int lastStderrPosition = 0;

		LocalExecHandle(String id, Process process) {
			this.id = id;
			this.process = process;
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
			destroy(this.process);
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

	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private Path workingDirectory;

		private boolean cleanEnvironment = false;

		private final Map<String, String> environment = new HashMap<>();

		private List<String> shellCommand;

		private Builder() {
		}

		/**
		 * Working directory for launched processes. Default: the JVM's current directory.
		 */
		public Builder workingDirectory(Path workingDirectory) {
			this.workingDirectory = workingDirectory;
			return this;
		}

		public Builder workingDirectory(String workingDirectory) {
			return workingDirectory(workingDirectory != null ? Paths.get(workingDirectory) : null);
		}

		/**
		 * When true, child processes start from an empty environment instead of
		 * inheriting the JVM's (recommended when commands are model-authored — the JVM
		 * environment often carries secrets). Default: false, preserving historical
		 * behavior.
		 */
		public Builder cleanEnvironment(boolean cleanEnvironment) {
			this.cleanEnvironment = cleanEnvironment;
			return this;
		}

		/** Environment variables to set for every launched process. */
		public Builder environment(Map<String, String> environment) {
			if (environment != null) {
				this.environment.putAll(environment);
			}
			return this;
		}

		/**
		 * Overrides shell selection. Default: {@code cmd.exe /c} on Windows,
		 * {@code /bin/bash -c} elsewhere.
		 */
		public Builder shellCommand(String... shellCommand) {
			this.shellCommand = List.of(shellCommand);
			return this;
		}

		public LocalExecBackend build() {
			List<String> shell = this.shellCommand;
			if (shell == null) {
				String os = System.getProperty("os.name").toLowerCase();
				shell = os.contains("win") ? List.of("cmd.exe", "/c") : List.of("/bin/bash", "-c");
			}
			return new LocalExecBackend(this.workingDirectory, this.cleanEnvironment, Map.copyOf(this.environment),
					shell);
		}

	}

}
