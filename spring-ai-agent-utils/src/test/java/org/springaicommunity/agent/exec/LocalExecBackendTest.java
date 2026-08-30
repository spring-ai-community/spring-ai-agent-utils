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

import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.springaicommunity.agent.common.exec.ExecBackend;
import org.springaicommunity.agent.common.exec.ExecHandle;
import org.springaicommunity.agent.common.exec.ExecResult;
import org.springaicommunity.agent.common.exec.ExecSpec;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 */
@DisabledOnOs(OS.WINDOWS)
class LocalExecBackendTest {

	@TempDir
	Path tempDir;

	@Test
	void runsInConfiguredWorkingDirectory() throws Exception {
		ExecBackend backend = LocalExecBackend.builder().workingDirectory(this.tempDir).build();
		ExecResult result = backend.run(ExecSpec.of("pwd"));

		assertThat(result.exitCode()).isZero();
		// toRealPath(): on macOS the temp dir is reported via the /var symlink while pwd
		// prints the physical /private/var path
		assertThat(result.stdout().trim()).isEqualTo(this.tempDir.toRealPath().toString());
	}

	@Test
	void defaultsToJvmWorkingDirectory() {
		ExecBackend backend = LocalExecBackend.builder().build();
		ExecResult result = backend.run(ExecSpec.of("pwd"));

		assertThat(result.exitCode()).isZero();
		assertThat(result.stdout().trim()).isEqualTo(System.getProperty("user.dir"));
	}

	@Test
	void cleanEnvironmentHidesJvmVariables() {
		ExecBackend backend = LocalExecBackend.builder()
			.workingDirectory(this.tempDir)
			.cleanEnvironment(true)
			.environment(Map.of("SAFE_VAR", "visible"))
			.build();
		ExecResult result = backend.run(ExecSpec.of("echo HOME=[$HOME] SAFE_VAR=[$SAFE_VAR]"));

		assertThat(result.stdout()).contains("HOME=[]").contains("SAFE_VAR=[visible]");
	}

	@Test
	void inheritsJvmEnvironmentByDefault() {
		ExecBackend backend = LocalExecBackend.builder().workingDirectory(this.tempDir).build();
		ExecResult result = backend.run(ExecSpec.of("echo HOME=[$HOME]"));

		assertThat(result.stdout()).doesNotContain("HOME=[]");
	}

	@Test
	void perSpecEnvironmentIsApplied() {
		ExecBackend backend = LocalExecBackend.builder().workingDirectory(this.tempDir).build();
		ExecResult result = backend
			.run(new ExecSpec("echo VALUE=[$SPEC_VAR]", 10_000, Map.of("SPEC_VAR", "from-spec")));

		assertThat(result.stdout()).contains("VALUE=[from-spec]");
	}

	@Test
	void timesOutAndReportsIt() {
		ExecBackend backend = LocalExecBackend.builder().workingDirectory(this.tempDir).build();
		ExecResult result = backend.run(new ExecSpec("sleep 5", 300, Map.of()));

		assertThat(result.status()).isEqualTo(ExecResult.Status.TIMED_OUT);
	}

	@Test
	void reportsLaunchFailureWithoutThrowing() {
		ExecBackend backend = LocalExecBackend.builder()
			.workingDirectory(this.tempDir)
			.shellCommand("/no/such/shell", "-c")
			.build();
		ExecResult result = backend.run(ExecSpec.of("echo hi"));

		assertThat(result.status()).isEqualTo(ExecResult.Status.LAUNCH_FAILED);
		assertThat(result.stderr()).isNotBlank();
	}

	@Test
	void backgroundHandleStreamsIncrementalOutputAndKills() throws Exception {
		ExecBackend backend = LocalExecBackend.builder().workingDirectory(this.tempDir).build();
		ExecHandle handle = backend.start(ExecSpec.of("echo first; sleep 3; echo second"));

		assertThat(handle.id()).startsWith("shell_");
		Thread.sleep(500);
		assertThat(handle.newOutput(null)).contains("first");
		// Cursor semantics: already-consumed output is not repeated
		assertThat(handle.newOutput(null)).doesNotContain("first");

		handle.kill();
		Thread.sleep(200);
		assertThat(handle.isAlive()).isFalse();
	}

	@Test
	void handleIdsAreUniqueAcrossBackends() {
		ExecBackend backend1 = LocalExecBackend.builder().workingDirectory(this.tempDir).build();
		ExecBackend backend2 = LocalExecBackend.builder().workingDirectory(this.tempDir).build();
		ExecHandle handle1 = backend1.start(ExecSpec.of("true"));
		ExecHandle handle2 = backend2.start(ExecSpec.of("true"));

		assertThat(handle1.id()).isNotEqualTo(handle2.id());
	}

}
