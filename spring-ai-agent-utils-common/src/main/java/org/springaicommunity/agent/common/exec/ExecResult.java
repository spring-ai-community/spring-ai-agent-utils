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
package org.springaicommunity.agent.common.exec;

/**
 * The outcome of a synchronous command execution. The {@link Status} discriminates the
 * four possible outcomes explicitly — no sentinel exit codes: {@code exitCode} is only
 * meaningful when the status is {@link Status#COMPLETED}. Backends report failures as
 * results rather than throwing, and messages carry no user-facing prefixes (the tool
 * layer owns presentation).
 *
 * @param status how the execution ended
 * @param exitCode the process exit code; meaningful only for {@link Status#COMPLETED}
 * @param stdout captured standard output (possibly partial for non-completed statuses)
 * @param stderr captured standard error, or the failure reason for
 * {@link Status#LAUNCH_FAILED} / {@link Status#INTERRUPTED}
 * @author Christian Tzolov
 */
public record ExecResult(Status status, int exitCode, String stdout, String stderr) {

	/** How a synchronous execution ended. */
	public enum Status {

		/** The process ran to completion; {@code exitCode} is its exit code. */
		COMPLETED,

		/** The process was killed after exceeding the spec's timeout. */
		TIMED_OUT,

		/**
		 * The command could not be launched at all; {@code stderr} carries the reason.
		 */
		LAUNCH_FAILED,

		/** The executing thread was interrupted; {@code stderr} carries the reason. */
		INTERRUPTED

	}

	public ExecResult {
		stdout = (stdout != null) ? stdout : "";
		stderr = (stderr != null) ? stderr : "";
	}

	public static ExecResult completed(int exitCode, String stdout, String stderr) {
		return new ExecResult(Status.COMPLETED, exitCode, stdout, stderr);
	}

	public static ExecResult timedOut(String stdout, String stderr) {
		return new ExecResult(Status.TIMED_OUT, -1, stdout, stderr);
	}

	public static ExecResult launchFailed(String reason) {
		return new ExecResult(Status.LAUNCH_FAILED, -1, "", reason);
	}

	public static ExecResult interrupted(String stdout, String reason) {
		return new ExecResult(Status.INTERRUPTED, -1, stdout, reason);
	}

}
