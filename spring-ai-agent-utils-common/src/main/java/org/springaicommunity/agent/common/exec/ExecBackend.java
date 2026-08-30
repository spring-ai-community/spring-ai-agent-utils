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
 * Where model-authored shell commands run. Tools describe <em>what</em> to execute (an
 * {@link ExecSpec}); the backend decides <em>where and how</em> — which shell, which
 * working directory, which environment variables. The default implementation runs
 * processes on the host JVM; alternative implementations can execute inside a container
 * or on a remote worker without any tool changes.
 *
 * @author Christian Tzolov
 */
public interface ExecBackend {

	/**
	 * Executes the command synchronously, honoring the spec's timeout. Failures are
	 * reported via {@link ExecResult.Status}, never thrown.
	 */
	ExecResult run(ExecSpec spec);

	/**
	 * Starts the command in the background and returns a handle to poll or kill it.
	 * Throws {@link IllegalStateException} with the (unprefixed) failure reason when the
	 * command cannot be launched. Handle ids must be unique within the backend.
	 */
	ExecHandle start(ExecSpec spec);

}
