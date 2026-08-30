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
 * A handle to a background command started via {@link ExecBackend#start}. Handles are
 * owned by the caller (not a process-global registry), so separate tool instances have
 * separate shell namespaces.
 *
 * @author Christian Tzolov
 */
public interface ExecHandle {

	/** Stable identifier for this background command. */
	String id();

	/** Whether the underlying process is still running. */
	boolean isAlive();

	/**
	 * Returns output produced since the previous call (cursor semantics). When
	 * {@code filterRegex} is non-null, only lines matching it are returned; filtered-out
	 * lines are consumed and not returned by later calls.
	 */
	String newOutput(String filterRegex);

	/** The process exit code; only valid once {@link #isAlive()} is false. */
	int exitCode();

	/** Terminates the process (graceful first, forcibly after a short grace period). */
	void kill();

}
