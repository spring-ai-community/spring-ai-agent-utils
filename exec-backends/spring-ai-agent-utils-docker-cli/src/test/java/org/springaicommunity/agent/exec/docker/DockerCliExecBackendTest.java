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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springaicommunity.agent.common.exec.ExecHandle;
import org.springaicommunity.agent.common.exec.ExecResult;
import org.springaicommunity.agent.common.exec.ExecSpec;
import org.springaicommunity.agent.common.workspace.Workspace;
import org.springaicommunity.agent.tools.ShellTools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for {@link DockerCliExecBackend} against a real Docker daemon. The
 * whole suite is skipped when the {@code docker} CLI or daemon is unavailable (JUnit
 * assumption), so builds without Docker stay green.
 *
 * @author Christian Tzolov
 */
class DockerCliExecBackendTest {

	private static final String IMAGE = "alpine:3.20";

	private static final String CONTAINER_WORKSPACE = "/workspace";

	@TempDir
	static Path hostWorkspace;

	private static DockerCliExecBackend backend;

	@BeforeAll
	static void createSharedContainer() {
		Assumptions.assumeTrue(dockerAvailable(), "Docker CLI/daemon not available - skipping Docker backend tests");
		backend = DockerCliExecBackend.builder().image(IMAGE).mount(hostWorkspace, CONTAINER_WORKSPACE).build();
	}

	@AfterAll
	static void removeSharedContainer() {
		if (backend != null) {
			backend.close();
		}
	}

	private static boolean dockerAvailable() {
		try {
			Process process = new ProcessBuilder("docker", "version", "--format", "{{.Server.Version}}").start();
			return process.waitFor(15, TimeUnit.SECONDS) && process.exitValue() == 0;
		}
		catch (IOException e) {
			return false;
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return false;
		}
	}

	@Test
	void commandsRunInsideTheContainerNotOnTheHost() {
		ExecResult result = backend.run(ExecSpec.of("cat /etc/os-release"));

		assertThat(result.status()).isEqualTo(ExecResult.Status.COMPLETED);
		assertThat(result.exitCode()).isZero();
		assertThat(result.stdout()).contains("Alpine");
	}

	@Test
	void exitCodeAndStderrPropagateFromTheContainer() {
		ExecResult result = backend.run(ExecSpec.of("echo boom >&2; exit 7"));

		assertThat(result.status()).isEqualTo(ExecResult.Status.COMPLETED);
		assertThat(result.exitCode()).isEqualTo(7);
		assertThat(result.stderr()).contains("boom");
	}

	@Test
	void perSpecEnvironmentVariablesAreApplied() {
		ExecResult result = backend
			.run(new ExecSpec("printf %s \"$GREETING\"", 0, Map.of("GREETING", "hola contenedor")));

		assertThat(result.exitCode()).isZero();
		assertThat(result.stdout().trim()).isEqualTo("hola contenedor");
	}

	@Test
	void workingDirectoryDefaultsToTheMountTarget() {
		ExecResult result = backend.run(ExecSpec.of("pwd"));

		assertThat(result.stdout().trim()).isEqualTo(CONTAINER_WORKSPACE);
	}

	@Test
	void bindMountRoundTripsBetweenHostAndContainer() throws Exception {
		Files.writeString(hostWorkspace.resolve("from-host.txt"), "written on host\n");

		ExecResult read = backend.run(ExecSpec.of("cat /workspace/from-host.txt"));
		assertThat(read.stdout()).contains("written on host");

		ExecResult write = backend.run(ExecSpec.of("printf 'written in container' > /workspace/from-container.txt"));
		assertThat(write.exitCode()).isZero();
		assertThat(Files.readString(hostWorkspace.resolve("from-container.txt"))).isEqualTo("written in container");
	}

	@Test
	void timeoutKillsTheProcessInsideTheContainer() {
		ExecResult result = backend.run(new ExecSpec("sleep 600", 1500, Map.of()));

		assertThat(result.status()).isEqualTo(ExecResult.Status.TIMED_OUT);
		ExecResult ps = backend.run(ExecSpec.of("ps"));
		assertThat(ps.stdout()).doesNotContain("sleep 600");
	}

	@Test
	void backgroundHandleStreamsOutputAndKillReachesIntoTheContainer() throws Exception {
		ExecHandle handle = backend
			.start(ExecSpec.of("i=0; while true; do i=$((i+1)); echo tick-$i-end; sleep 1; done"));

		assertThat(handle.id()).startsWith("docker_");
		String first = awaitOutput(handle, "tick-1-end");
		assertThat(first).contains("tick-1-end");

		handle.kill();
		long deadline = System.currentTimeMillis() + 5000;
		while (handle.isAlive() && System.currentTimeMillis() < deadline) {
			Thread.sleep(100);
		}
		assertThat(handle.isAlive()).isFalse();

		ExecResult ps = backend.run(ExecSpec.of("ps"));
		assertThat(ps.stdout()).doesNotContain("tick-");

		// Cursor semantics: consumed output is not returned again
		assertThat(handle.newOutput(null)).doesNotContain("tick-1-end");
	}

	@Test
	void workspaceMapsHostPathsToContainerPaths() {
		Workspace workspace = backend.workspace();

		assertThat(workspace.root()).isEqualTo(hostWorkspace.toAbsolutePath().normalize());
		assertThat(workspace.display(hostWorkspace.resolve("skills").resolve("pdf")))
			.isEqualTo("/workspace/skills/pdf");
		assertThat(workspace.display(hostWorkspace)).isEqualTo(CONTAINER_WORKSPACE);
		assertThat(workspace.display("/somewhere/else")).isEqualTo("/somewhere/else");
	}

	@Test
	void shellToolsRunEndToEndOverTheDockerBackend() {
		ShellTools shellTools = ShellTools.builder().execBackend(backend).build();

		String result = shellTools.bash("echo running in $(pwd) on $(. /etc/os-release; echo $ID)", null, null, null);

		assertThat(result).contains("running in /workspace on alpine");
	}

	@Test
	void attachModeExecutesInAnExistingContainer() {
		try (DockerCliExecBackend attached = DockerCliExecBackend.builder()
			.containerId(backend.containerId())
			.containerWorkingDirectory("/tmp")
			.build()) {

			ExecResult result = attached.run(ExecSpec.of("pwd"));
			assertThat(result.stdout().trim()).isEqualTo("/tmp");
		}

		// close() of an attached backend must not remove the caller-owned container
		assertThat(backend.run(ExecSpec.of("echo still-alive")).stdout()).contains("still-alive");
	}

	@Test
	void closeRemovesTheManagedContainer() {
		DockerCliExecBackend own = DockerCliExecBackend.builder().image(IMAGE).build();
		String containerId = own.containerId();

		own.close();

		ExecResult result = own.run(ExecSpec.of("echo hi"));
		assertThat(result.status()).isEqualTo(ExecResult.Status.LAUNCH_FAILED);
		assertThat(containerGone(containerId)).isTrue();
	}

	@Test
	void missingDockerCliReportsLaunchFailure() {
		DockerCliExecBackend broken = DockerCliExecBackend.builder()
			.dockerCommand("definitely-not-a-docker-cli")
			.containerId("irrelevant")
			.build();

		ExecResult result = broken.run(ExecSpec.of("echo hi"));

		assertThat(result.status()).isEqualTo(ExecResult.Status.LAUNCH_FAILED);
	}

	@Test
	void missingContainerSurfacesTheDockerError() {
		try (DockerCliExecBackend gone = DockerCliExecBackend.builder().containerId("no-such-container-xyz").build()) {

			ExecResult result = gone.run(ExecSpec.of("echo hi"));

			assertThat(result.status()).isEqualTo(ExecResult.Status.COMPLETED);
			assertThat(result.exitCode()).isNotZero();
			assertThat(result.stderr()).isNotBlank();
		}
	}

	@Test
	void imageWithoutSleepFailsAtBuildNotAtFirstCommand() {
		// hello-world has no shell and no sleep binary - the managed container cannot
		// be kept alive, and build() must say so instead of leaving every later
		// command to fail with a confusing daemon error
		assertThatThrownBy(() -> DockerCliExecBackend.builder().image("hello-world").build())
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("hello-world");

		// The failed build must not leave a 'Created' container corpse behind
		assertThat(noContainersFromImage("hello-world")).isTrue();
	}

	private static boolean noContainersFromImage(String image) {
		try {
			Process process = new ProcessBuilder("docker", "ps", "-a", "-q", "--filter", "ancestor=" + image).start();
			String output = new String(process.getInputStream().readAllBytes());
			process.waitFor(10, TimeUnit.SECONDS);
			return output.isBlank();
		}
		catch (IOException e) {
			return false;
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return false;
		}
	}

	@Test
	void builderRejectsInvalidConfigurations() {
		assertThatThrownBy(() -> DockerCliExecBackend.builder().build()).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("exactly one");
		assertThatThrownBy(() -> DockerCliExecBackend.builder().containerId("c").image("i").build())
			.isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(
				() -> DockerCliExecBackend.builder().containerId("c").mount(hostWorkspace, "/workspace").build())
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("mount");
		assertThatThrownBy(() -> DockerCliExecBackend.builder().containerId("c").build().workspace())
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("mount");
	}

	private static String awaitOutput(ExecHandle handle, String expected) throws InterruptedException {
		StringBuilder seen = new StringBuilder();
		long deadline = System.currentTimeMillis() + 10_000;
		while (System.currentTimeMillis() < deadline) {
			seen.append(handle.newOutput(null));
			if (seen.toString().contains(expected)) {
				return seen.toString();
			}
			Thread.sleep(200);
		}
		return seen.toString();
	}

	private static boolean containerGone(String containerId) {
		try {
			Process process = new ProcessBuilder("docker", "ps", "-a", "-q", "--filter", "id=" + containerId).start();
			String output = new String(process.getInputStream().readAllBytes());
			process.waitFor(10, TimeUnit.SECONDS);
			return output.isBlank();
		}
		catch (IOException e) {
			return false;
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return false;
		}
	}

}
