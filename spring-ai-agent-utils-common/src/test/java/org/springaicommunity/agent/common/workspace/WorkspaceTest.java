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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The local workspace normalizes its root and displays paths verbatim; custom
 * implementations override display to map host paths into their sandbox form. Fixture
 * paths are derived from a temp directory so the assertions hold on every platform.
 *
 * @author Christian Tzolov
 */
class WorkspaceTest {

	@TempDir
	Path tempDir;

	@Test
	void localWorkspaceNormalizesRoot() {
		Path dotted = this.tempDir.resolve("sub").resolve("..");

		assertEquals(this.tempDir, Workspace.local(dotted).root());
	}

	@Test
	void localWorkspaceDisplayIsIdentity() {
		Workspace workspace = Workspace.local(this.tempDir);

		Path file = this.tempDir.resolve("README.md");
		assertEquals(file.toString(), workspace.display(file));
		assertEquals("classpath:skills/pdf", workspace.display("classpath:skills/pdf"));
	}

	@Test
	void localWorkspaceRejectsNullRoot() {
		assertThrows(IllegalArgumentException.class, () -> Workspace.local(null));
	}

	@Test
	void customDisplayMapsHostPathsIntoSandboxForm() {
		String hostRoot = this.tempDir.toString();
		Workspace sandbox = new Workspace() {

			@Override
			public Path root() {
				return WorkspaceTest.this.tempDir;
			}

			@Override
			public String display(String hostPath) {
				return hostPath.replace(hostRoot, "/workspace");
			}

		};

		assertEquals("/workspace/skills/pdf", sandbox.display(hostRoot + "/skills/pdf"));
	}

}
