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
package org.springaicommunity.agent.tools.task.subagent.claude;

import org.junit.jupiter.api.Test;
import org.springaicommunity.agent.tools.task.subagent.Kind;
import org.springaicommunity.agent.tools.task.subagent.Subagent;
import org.springaicommunity.agent.tools.task.subagent.SubagentReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link ClaudeSubagentResolver}.
 *
 * @author Christian Tzolov
 */
class ClaudeSubagentResolverTest {

	private final ClaudeSubagentResolver resolver = new ClaudeSubagentResolver();

	@Test
	void shouldResolveOnlyClaudeSubagentKind() {
		SubagentReference claudeRef = new SubagentReference("file:test.md", Kind.CLAUDE_SUBAGENT.name(), null);
		SubagentReference a2aRef = new SubagentReference("http://example.com", Kind.A2A_SUBAGENT.name(), null);

		assertThat(resolver.canResolve(claudeRef)).isTrue();
		assertThat(resolver.canResolve(a2aRef)).isFalse();
	}

	@Test
	void shouldResolveFromClasspathResource() {
		SubagentReference ref = new SubagentReference("classpath:/agent/EXPLORE_SUBAGENT.md",
				Kind.CLAUDE_SUBAGENT.name(), null);

		Subagent subagent = resolver.resolve(ref);

		assertThat(subagent).isInstanceOf(ClaudeSubagent.class);
		assertThat(subagent.getName()).isEqualTo("Explore");
		assertThat(subagent.getDescription()).contains("exploring codebases");
		assertThat(subagent.getKind()).isEqualTo(Kind.CLAUDE_SUBAGENT.name());
	}

	@Test
	void shouldFailForNonExistentFile() {
		SubagentReference ref = new SubagentReference("file:/non/existent/file.md", Kind.CLAUDE_SUBAGENT.name(), null);

		assertThatThrownBy(() -> resolver.resolve(ref)).isInstanceOf(RuntimeException.class)
			.hasMessageContaining("Failed to read task file");
	}

	@Test
	void shouldRejectNullReference() {
		assertThatThrownBy(() -> resolver.canResolve(null)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> resolver.resolve(null)).isInstanceOf(IllegalArgumentException.class);
	}

}
