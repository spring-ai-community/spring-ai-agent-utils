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
package org.springaicommunity.agent.tools;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ExecBackend integration behavior of {@link ShellTools}: working-directory confinement
 * and per-instance background shell namespaces (regression tests for the historical
 * JVM-cwd execution and the process-global static shell registry).
 *
 * @author Christian Tzolov
 */
@DisabledOnOs(OS.WINDOWS)
class ShellToolsExecBackendTest {

	@TempDir
	Path tempDir;

	@Test
	void commandsRunInConfiguredWorkingDirectory() throws Exception {
		ShellTools shellTools = ShellTools.builder().workingDirectory(this.tempDir).build();

		String result = shellTools.bash("pwd && touch created-here.txt", null, null, null);

		// toRealPath(): macOS reports temp dirs via the /var symlink; pwd prints
		// /private/var
		assertThat(result).contains(this.tempDir.toRealPath().toString());
		assertThat(Files.exists(this.tempDir.resolve("created-here.txt"))).isTrue();
	}

	@Test
	void backgroundShellsAreScopedPerInstance() throws Exception {
		ShellTools sessionA = ShellTools.builder().workingDirectory(this.tempDir).build();
		ShellTools sessionB = ShellTools.builder().workingDirectory(this.tempDir).build();

		String started = sessionA.bash("sleep 30", null, null, true);
		String shellId = started.substring(started.indexOf("bash_id: ") + 9, started.indexOf("\n"));

		// Session B cannot see (or kill) session A's shell — the registry is per instance
		assertThat(sessionB.bashOutput(shellId, null)).contains("No background shell found");
		assertThat(sessionB.killShell(shellId)).contains("No background shell found");

		// Session A can
		assertThat(sessionA.bashOutput(shellId, null)).contains("Status: Running");
		assertThat(sessionA.killShell(shellId)).contains("Successfully killed shell");
	}

	@Test
	void blankCommandReturnsErrorStringInsteadOfThrowing() {
		ShellTools shellTools = ShellTools.builder().workingDirectory(this.tempDir).build();

		assertThat(shellTools.bash("   ", null, null, null)).isEqualTo("Error: command must not be blank");
		assertThat(shellTools.bash(null, null, null, null)).isEqualTo("Error: command must not be blank");
	}

	@Test
	void nonPositiveTimeoutIsClampedAndCommandStillRuns() {
		ShellTools shellTools = ShellTools.builder().workingDirectory(this.tempDir).build();

		String result = shellTools.bash("echo clamped-ok", 0L, null, null);

		assertThat(result).contains("clamped-ok").doesNotContain("timed out");
	}

	@Test
	void timeoutDiagnosticReportsEffectiveTimeout() {
		ShellTools shellTools = ShellTools.builder().workingDirectory(this.tempDir).build();

		String result = shellTools.bash("sleep 5", 200L, null, null);

		assertThat(result).contains("Command timed out after 200ms");
	}

	@Test
	void backgroundLaunchFailureHasSingleErrorPrefix() {
		ShellTools shellTools = ShellTools.builder()
			.execBackend(org.springaicommunity.agent.exec.LocalExecBackend.builder()
				.shellCommand("/no/such/shell", "-c")
				.build())
			.build();

		String result = shellTools.bash("echo hi", null, null, true);

		assertThat(result).startsWith("Error executing command: ");
		assertThat(result.indexOf("Error executing command:"))
			.isEqualTo(result.lastIndexOf("Error executing command:"));
	}

	@Test
	void syncRunIdsUseTheirOwnNamespaceAndAreNeverResolvable() {
		ShellTools shellTools = ShellTools.builder().workingDirectory(this.tempDir).build();

		String result = shellTools.bash("echo hi", null, null, null);
		String syncId = result.substring(result.indexOf("bash_id: ") + 9, result.indexOf("\n"));

		// Distinct namespace from backend handle ids ("shell_<n>") — collisions
		// impossible
		assertThat(syncId).startsWith("shell_run_");
		assertThat(shellTools.bashOutput(syncId, null)).contains("No background shell found");
	}

	@Test
	void execBackendAndWorkingDirectoryAreMutuallyExclusive() {
		assertThatThrownBy(() -> ShellTools.builder()
			.execBackend(org.springaicommunity.agent.exec.LocalExecBackend.builder().build())
			.workingDirectory(this.tempDir)
			.build()).isInstanceOf(IllegalStateException.class);
	}

}
