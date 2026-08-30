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

import java.util.Map;

/**
 * A shell command to execute: the command line as the model authored it, a wall-clock
 * timeout in milliseconds (applied to synchronous runs), and extra environment variables
 * to set for this invocation. Shell selection and working directory are backend concerns,
 * not part of the spec.
 *
 * <p>
 * Timeout policy is owned here so every backend behaves the same: non-positive values are
 * replaced by {@link #DEFAULT_TIMEOUT_MILLIS} and values above
 * {@link #MAX_TIMEOUT_MILLIS} are clamped to it. Read {@link #timeoutMillis()} for the
 * effective value.
 *
 * @param command the shell command line to execute
 * @param timeoutMillis effective wall-clock timeout for synchronous execution
 * @param env extra environment variables for this invocation (never null)
 * @author Christian Tzolov
 */
public record ExecSpec(String command, long timeoutMillis, Map<String, String> env) {

	/** Default timeout for synchronous commands: 2 minutes. */
	public static final long DEFAULT_TIMEOUT_MILLIS = 120_000;

	/** Upper bound for synchronous command timeouts: 10 minutes. */
	public static final long MAX_TIMEOUT_MILLIS = 600_000;

	public ExecSpec {
		if (command == null || command.isBlank()) {
			throw new IllegalArgumentException("command must not be blank");
		}
		if (timeoutMillis <= 0) {
			timeoutMillis = DEFAULT_TIMEOUT_MILLIS;
		}
		timeoutMillis = Math.min(timeoutMillis, MAX_TIMEOUT_MILLIS);
		env = (env != null) ? Map.copyOf(env) : Map.of();
	}

	/** Creates a spec with the default timeout and no extra environment. */
	public static ExecSpec of(String command) {
		return new ExecSpec(command, DEFAULT_TIMEOUT_MILLIS, Map.of());
	}

}
