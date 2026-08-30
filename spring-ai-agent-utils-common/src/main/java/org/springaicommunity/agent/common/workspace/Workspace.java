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
package org.springaicommunity.agent.common.workspace;

import java.nio.file.Path;

/**
 * The directory context an agent's tools operate in: a root directory plus a mapping from
 * host-side paths to the paths the <em>model</em> should see. For local execution the
 * mapping is the identity; for a sandboxed deployment (e.g. a container with a
 * bind-mounted workspace) an implementation translates host paths to their in-sandbox
 * form so the model is never shown a path it cannot use.
 *
 * <p>
 * Tool builders accept a workspace as one-call configuration via their {@code workspace}
 * option: the search tools (GrepTool, GlobTool, ListDirectoryTool) take the working
 * directory and the allowed-directory confinement together, FileSystemTools takes the
 * confinement, and ShellTools takes the working directory only (a shell cannot be
 * confined from Java — use a sandboxed exec backend for isolation). Path-publishing
 * components (SkillsTool, AgentEnvironment) use {@link #display} so system prompts and
 * tool responses describe the workspace rather than the host.
 *
 * @author Christian Tzolov
 */
@FunctionalInterface
public interface Workspace {

	/** The workspace root directory (host-side form). */
	Path root();

	/**
	 * How a host-side path should be presented to the model. Identity by default; sandbox
	 * implementations rewrite it to the in-sandbox form. Non-filesystem locations (e.g.
	 * classpath/JAR pseudo-paths) pass through unchanged.
	 */
	default String display(String hostPath) {
		return hostPath;
	}

	/** Convenience for {@link #display(String)}. */
	default String display(Path hostPath) {
		return display(hostPath.toString());
	}

	/** A local workspace rooted at the given directory, with identity path display. */
	static Workspace local(Path root) {
		if (root == null) {
			throw new IllegalArgumentException("root must not be null");
		}
		Path normalized = root.toAbsolutePath().normalize();
		return () -> normalized;
	}

}
