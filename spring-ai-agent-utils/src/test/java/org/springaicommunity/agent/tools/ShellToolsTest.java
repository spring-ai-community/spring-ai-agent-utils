/*
 * Copyright 2025 - 2025 the original author or authors.
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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.springaicommunity.agent.tools.ShellTools;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tests for {@link ShellTools}.
 *
 * @author Christian Tzolov
 */
@DisplayName("ShellTools Tests")
class ShellToolsTest {

	private ShellTools shellTools;

	@BeforeEach
	void setUp() {
		this.shellTools = new ShellTools();
	}

	@Nested
	@DisplayName("Bash Tool - Basic Command Execution")
	class BasicCommandExecutionTests {

		@Test
		@DisplayName("Should execute simple echo command successfully")
		void shouldExecuteSimpleEchoCommand() {
			String result = shellTools.bash("echo 'Hello World'", null, null, null, null);

			assertThat(result).contains("bash_id: shell_");
			assertThat(result).contains("Hello World");
			assertThat(result).doesNotContain("Exit code:");
		}

		@Test
		@DisplayName("Should execute pwd command successfully")
		void shouldExecutePwdCommand() {
			String result = shellTools.bash("pwd", null, null, null, null);

			assertThat(result).contains("bash_id: shell_");
			assertThat(result).contains("/");
			assertThat(result).doesNotContain("Exit code:");
		}

		@Test
		@DisplayName("Should execute ls command successfully")
		@DisabledOnOs(OS.WINDOWS)
		void shouldExecuteLsCommand() {
			String result = shellTools.bash("ls /tmp", null, null, null, null);

			assertThat(result).contains("bash_id: shell_");
			assertThat(result).doesNotContain("Exit code:");
		}

		@Test
		@DisplayName("Should handle commands with multiple lines of output")
		@DisabledOnOs(OS.WINDOWS)
		void shouldHandleMultipleLinesOfOutput() {
			String result = shellTools.bash("echo 'Line 1'; echo 'Line 2'; echo 'Line 3'", null,
					null, null, null);

			assertThat(result).contains("bash_id: shell_");
			assertThat(result).contains("Line 1");
			assertThat(result).contains("Line 2");
			assertThat(result).contains("Line 3");
		}

	}

	@Nested
	@DisplayName("Bash Tool - Exit Codes and Error Handling")
	class ExitCodeAndErrorHandlingTests {

		@Test
		@DisplayName("Should capture non-zero exit code")
		@DisabledOnOs(OS.WINDOWS)
		void shouldCaptureNonZeroExitCode() {
			String result = shellTools.bash("exit 1", null, null, null, null);

			assertThat(result).contains("bash_id: shell_");
			assertThat(result).contains("Exit code: 1");
		}

		@Test
		@DisplayName("Should capture stderr output")
		@DisabledOnOs(OS.WINDOWS)
		void shouldCaptureStderrOutput() {
			String result = shellTools.bash("echo 'Error message' >&2", null, null, null, null);

			assertThat(result).contains("bash_id: shell_");
			assertThat(result).contains("STDERR:");
			assertThat(result).contains("Error message");
		}

		@Test
		@DisplayName("Should capture both stdout and stderr")
		@DisabledOnOs(OS.WINDOWS)
		void shouldCaptureBothStdoutAndStderr() {
			String result = shellTools.bash("echo 'Output'; echo 'Error' >&2", null, null, null,
					null);

			assertThat(result).contains("bash_id: shell_");
			assertThat(result).contains("Output");
			assertThat(result).contains("STDERR:");
			assertThat(result).contains("Error");
		}

		@Test
		@DisplayName("Should handle invalid command gracefully")
		@DisabledOnOs(OS.WINDOWS)
		void shouldHandleInvalidCommand() {
			String result = shellTools.bash("invalidcommandthatdoesnotexist", null, null, null,
					null);

			assertThat(result).contains("bash_id: shell_");
			assertThat(result).containsAnyOf("STDERR:", "Exit code:");
		}

	}

	@Nested
	@DisplayName("Bash Tool - Timeout Handling")
	class TimeoutHandlingTests {

		@Test
		@DisplayName("Should timeout long-running command")
		@DisabledOnOs(OS.WINDOWS)
		void shouldTimeoutLongRunningCommand() {
			String result = shellTools.bash("sleep 10", 1000L, null, null, null);

			assertThat(result).contains("bash_id: shell_");
			assertThat(result).contains("Command timed out after 1000ms");
		}

		@Test
		@DisplayName("Should use default timeout when not specified")
		@DisabledOnOs(OS.WINDOWS)
		void shouldUseDefaultTimeout() {
			// This command should complete well within the default 120 second timeout
			String result = shellTools.bash("echo 'Fast command'", null, null, null, null);

			assertThat(result).contains("bash_id: shell_");
			assertThat(result).contains("Fast command");
			assertThat(result).doesNotContain("timed out");
		}

		@Test
		@DisplayName("Should enforce maximum timeout limit")
		@DisabledOnOs(OS.WINDOWS)
		void shouldEnforceMaximumTimeoutLimit() {
			// Request timeout greater than 600000ms, should be capped at 600000ms
			String result = shellTools.bash("echo 'Test'", 999999999L, null, null, null);

			assertThat(result).contains("bash_id: shell_");
			assertThat(result).contains("Test");
		}

	}

	@Nested
	@DisplayName("Bash Tool - Background Execution")
	class BackgroundExecutionTests {

		@Test
		@DisplayName("Should start background process successfully")
		@DisabledOnOs(OS.WINDOWS)
		void shouldStartBackgroundProcess() {
			String result = shellTools.bash("echo 'Background task'; sleep 1; echo 'Done'", null,
					null, true, null);

			assertThat(result).contains("bash_id: shell_");
			assertThat(result).contains("Background shell started with ID:");
			assertThat(result).contains("Use BashOutput tool");

			// Extract shell_id from result
			String shellId = extractShellId(result);
			assertThat(shellId).isNotNull();

			// Clean up
			shellTools.killShell(shellId, null);
		}

		@Test
		@DisplayName("Should execute background process and retrieve output")
		@DisabledOnOs(OS.WINDOWS)
		void shouldExecuteBackgroundProcessAndRetrieveOutput() throws InterruptedException {
			String startResult = shellTools.bash("echo 'Start'; sleep 1; echo 'End'", null, null,
					true, null);

			String shellId = extractShellId(startResult);

			// Wait a bit for process to produce output
			Thread.sleep(1500);
			String output = shellTools.bashOutput(shellId, null, null);
			assertThat(output).contains("Start");

			// Clean up
			shellTools.killShell(shellId, null);
		}

		@Test
		@DisplayName("Should track background process completion")
		@DisabledOnOs(OS.WINDOWS)
		void shouldTrackBackgroundProcessCompletion() throws InterruptedException {
			String startResult = shellTools.bash("echo 'Quick task'", null, null, true, null);

			String shellId = extractShellId(startResult);

			// Wait for process to complete
			Thread.sleep(1000);
			String output = shellTools.bashOutput(shellId, null, null);
			assertThat(output).contains("Status: Completed");

			// Clean up
			shellTools.killShell(shellId, null);
		}

	}

	@Nested
	@DisplayName("BashOutput Tool - Output Retrieval")
	class BashOutputRetrievalTests {

		@Test
		@DisplayName("Should retrieve output from background process")
		@DisabledOnOs(OS.WINDOWS)
		void shouldRetrieveOutputFromBackgroundProcess() throws InterruptedException {
			String startResult = shellTools.bash("echo 'Test output'", null, null, true, null);

			String shellId = extractShellId(startResult);

			Thread.sleep(1000);
			String output = shellTools.bashOutput(shellId, null, null);
			assertThat(output).contains("Shell ID: " + shellId);
			assertThat(output).contains("Test output");

			// Clean up
			shellTools.killShell(shellId, null);
		}

		@Test
		@DisplayName("Should return error for non-existent shell ID")
		void shouldReturnErrorForNonExistentShellId() {
			String output = shellTools.bashOutput("shell_nonexistent", null, null);

			assertThat(output).contains("Error: No background shell found with ID: shell_nonexistent");
		}

		@Test
		@DisplayName("Should show only new output on subsequent calls")
		@DisabledOnOs(OS.WINDOWS)
		void shouldShowOnlyNewOutputOnSubsequentCalls() throws InterruptedException {
			String startResult = shellTools.bash(
					"echo 'First'; sleep 1; echo 'Second'; sleep 1; echo 'Third'", null, null, true, null);

			String shellId = extractShellId(startResult);

			// Get first output
			Thread.sleep(500);
			String firstOutput = shellTools.bashOutput(shellId, null, null);
			assertThat(firstOutput).contains("First");

			// Wait and get second output
			Thread.sleep(1500);
			String secondOutput = shellTools.bashOutput(shellId, null, null);
			// Second call should show new output
			assertThat(secondOutput).containsAnyOf("Second", "Third", "No new output");

			// Clean up
			shellTools.killShell(shellId, null);
		}

		@Test
		@DisplayName("Should handle no new output gracefully")
		@DisabledOnOs(OS.WINDOWS)
		void shouldHandleNoNewOutputGracefully() throws InterruptedException {
			String startResult = shellTools.bash("echo 'Done'", null, null, true, null);

			String shellId = extractShellId(startResult);

			// Get first output
			Thread.sleep(1000);
			String firstOutput = shellTools.bashOutput(shellId, null, null);
			assertThat(firstOutput).contains("Done");

			// Try to get output again - should show no new output
			String secondOutput = shellTools.bashOutput(shellId, null, null);
			assertThat(secondOutput).contains("No new output since last check");

			// Clean up
			shellTools.killShell(shellId, null);
		}

	}

	@Nested
	@DisplayName("BashOutput Tool - Filtering")
	class BashOutputFilteringTests {

		@Test
		@DisplayName("Should filter output with regex pattern")
		@DisabledOnOs(OS.WINDOWS)
		void shouldFilterOutputWithRegexPattern() throws InterruptedException {
			String startResult = shellTools.bash("echo 'ERROR: Something failed'; echo 'INFO: All good'; echo 'ERROR: Another problem'",
					null, null, true, null);

			String shellId = extractShellId(startResult);

			Thread.sleep(1000);
			String output = shellTools.bashOutput(shellId, "ERROR", null);
			assertThat(output).contains("ERROR");
			// Filtered output should not contain INFO lines

			// Clean up
			shellTools.killShell(shellId, null);
		}

		@Test
		@DisplayName("Should handle empty filter result")
		@DisabledOnOs(OS.WINDOWS)
		void shouldHandleEmptyFilterResult() throws InterruptedException {
			String startResult = shellTools.bash("echo 'No errors here'", null, null, true, null);

			String shellId = extractShellId(startResult);

			Thread.sleep(1000);
			String output = shellTools.bashOutput(shellId, "ERROR", null);
			// Should show shell info but no matching output
			assertThat(output).contains("Shell ID: " + shellId);

			// Clean up
			shellTools.killShell(shellId, null);
		}

	}

	@Nested
	@DisplayName("KillShell Tool - Process Termination")
	class ProcessTerminationTests {

		@Test
		@DisplayName("Should kill running background process")
		@DisabledOnOs(OS.WINDOWS)
		void shouldKillRunningBackgroundProcess() {
			String startResult = shellTools.bash("sleep 100", null, null, true, null);

			String shellId = extractShellId(startResult);

			// Kill the process
			String killResult = shellTools.killShell(shellId, null);

			assertThat(killResult).contains("Successfully killed shell: " + shellId);
		}

		@Test
		@DisplayName("Should handle killing already terminated process")
		@DisabledOnOs(OS.WINDOWS)
		void shouldHandleKillingAlreadyTerminatedProcess() throws InterruptedException {
			String startResult = shellTools.bash("echo 'Quick task'", null, null, true, null);

			String shellId = extractShellId(startResult);

			// Wait for process to complete naturally
			Thread.sleep(1000);
			String output = shellTools.bashOutput(shellId, null, null);
			assertThat(output).contains("Status: Completed");

			// Try to kill already completed process
			String killResult = shellTools.killShell(shellId, null);

			assertThat(killResult).contains("was already terminated");
		}

		@Test
		@DisplayName("Should return error for non-existent shell ID")
		void shouldReturnErrorForNonExistentShellIdWhenKilling() {
			String killResult = shellTools.killShell("shell_nonexistent", null);

			assertThat(killResult).contains("Error: No background shell found with ID: shell_nonexistent");
		}

		@Test
		@DisplayName("Should remove shell from background processes after kill")
		@DisabledOnOs(OS.WINDOWS)
		void shouldRemoveShellFromBackgroundProcessesAfterKill() {
			String startResult = shellTools.bash("sleep 100", null, null, true, null);

			String shellId = extractShellId(startResult);

			// Kill the process
			shellTools.killShell(shellId, null);

			// Try to access killed process
			String output = shellTools.bashOutput(shellId, null, null);
			assertThat(output).contains("Error: No background shell found with ID: " + shellId);
		}

	}

	@Nested
	@DisplayName("Cross-Platform Compatibility")
	class CrossPlatformCompatibilityTests {

		@Test
		@DisplayName("Should use bash on Unix-like systems")
		@DisabledOnOs(OS.WINDOWS)
		void shouldUseBashOnUnixLikeSystems() {
			String result = shellTools.bash("echo $SHELL", null, null, null, null);

			assertThat(result).contains("bash_id: shell_");
		}

		@Test
		@DisplayName("Should use cmd.exe on Windows")
		@EnabledOnOs(OS.WINDOWS)
		void shouldUseCmdOnWindows() {
			String result = shellTools.bash("echo %COMSPEC%", null, null, null, null);

			assertThat(result).contains("bash_id: shell_");
			assertThat(result).containsIgnoringCase("cmd");
		}

	}

	@Nested
	@DisplayName("Output Truncation")
	class OutputTruncationTests {

		@Test
		@DisplayName("Should truncate very long output")
		@DisabledOnOs(OS.WINDOWS)
		void shouldTruncateVeryLongOutput() {
			// Generate output longer than 30000 characters
			StringBuilder longCommand = new StringBuilder("for i in {1..2000}; do echo 'This is a long line of text that will be repeated many times'; done");

			String result = shellTools.bash(longCommand.toString(), null, null, null, null);

			assertThat(result).contains("bash_id: shell_");
			// Output should be truncated
			if (result.length() > 30000) {
				assertThat(result).contains("(output truncated)");
			}
		}

	}

	@Nested
	@DisplayName("Shell ID Generation")
	class ShellIdGenerationTests {

		@Test
		@DisplayName("Should generate unique shell IDs")
		void shouldGenerateUniqueShellIds() {
			String result1 = shellTools.bash("echo 'First'", null, null, null, null);
			String result2 = shellTools.bash("echo 'Second'", null, null, null, null);

			String shellId1 = extractShellId(result1);
			String shellId2 = extractShellId(result2);

			assertThat(shellId1).isNotEqualTo(shellId2);
		}

		@Test
		@DisplayName("Shell ID should follow expected pattern")
		void shellIdShouldFollowExpectedPattern() {
			String result = shellTools.bash("echo 'Test'", null, null, null, null);

			String shellId = extractShellId(result);
			assertThat(shellId).matches("shell_\\d+");
		}

	}

	/**
	 * Helper method to extract shell_id from command output
	 */
	private String extractShellId(String output) {
		Pattern pattern = Pattern.compile("bash_id: (shell_\\d+)");
		Matcher matcher = pattern.matcher(output);
		if (matcher.find()) {
			return matcher.group(1);
		}
		return null;
	}

}
