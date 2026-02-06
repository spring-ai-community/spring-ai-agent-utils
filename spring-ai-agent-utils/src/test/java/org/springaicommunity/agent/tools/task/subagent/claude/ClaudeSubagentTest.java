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

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springaicommunity.agent.common.task.subagent.SubagentReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ClaudeSubagentDefinition}.
 *
 * @author Christian Tzolov
 */
class ClaudeSubagentTest {

	@Test
	void shouldParseBasicFrontmatter() {
		Map<String, Object> frontMatter = Map.of("name", "test-agent", "description", "Test description");
		SubagentReference ref = new SubagentReference("file:test.md", ClaudeSubagentDefinition.KIND, null);

		ClaudeSubagentDefinition subagent = new ClaudeSubagentDefinition(ref, frontMatter, "System prompt content");

		assertThat(subagent.getName()).isEqualTo("test-agent");
		assertThat(subagent.getDescription()).isEqualTo("Test description");
		assertThat(subagent.getKind()).isEqualTo(ClaudeSubagentDefinition.KIND);
		assertThat(subagent.getContent()).isEqualTo("System prompt content");
	}

	@Test
	void shouldParseToolsFromFrontmatter() {
		Map<String, Object> frontMatter = Map.of("name", "test", "description", "desc", "tools", "Read, Grep, Glob");
		SubagentReference ref = new SubagentReference("file:test.md", ClaudeSubagentDefinition.KIND, null);

		ClaudeSubagentDefinition subagent = new ClaudeSubagentDefinition(ref, frontMatter, "");

		assertThat(subagent.tools()).containsExactly("Read", "Grep", "Glob");
	}

	@Test
	void shouldParseDisallowedToolsFromFrontmatter() {
		Map<String, Object> frontMatter = Map.of("name", "test", "description", "desc", "disallowedTools",
				"Bash, Shell");
		SubagentReference ref = new SubagentReference("file:test.md", ClaudeSubagentDefinition.KIND, null);

		ClaudeSubagentDefinition subagent = new ClaudeSubagentDefinition(ref, frontMatter, "");

		assertThat(subagent.disallowedTools()).containsExactly("Bash", "Shell");
	}

	@Test
	void shouldReturnEmptyListWhenNoToolsSpecified() {
		Map<String, Object> frontMatter = Map.of("name", "test", "description", "desc");
		SubagentReference ref = new SubagentReference("file:test.md", ClaudeSubagentDefinition.KIND, null);

		ClaudeSubagentDefinition subagent = new ClaudeSubagentDefinition(ref, frontMatter, "");

		assertThat(subagent.tools()).isEmpty();
		assertThat(subagent.disallowedTools()).isEmpty();
	}

	@Test
	void shouldParseModelFromFrontmatter() {
		Map<String, Object> frontMatter = Map.of("name", "test", "description", "desc", "model", "opus");
		SubagentReference ref = new SubagentReference("file:test.md", ClaudeSubagentDefinition.KIND, null);

		ClaudeSubagentDefinition subagent = new ClaudeSubagentDefinition(ref, frontMatter, "");

		assertThat(subagent.getModel()).isEqualTo("opus");
	}

	@Test
	void shouldReturnDefaultPermissionMode() {
		Map<String, Object> frontMatter = Map.of("name", "test", "description", "desc");
		SubagentReference ref = new SubagentReference("file:test.md", ClaudeSubagentDefinition.KIND, null);

		ClaudeSubagentDefinition subagent = new ClaudeSubagentDefinition(ref, frontMatter, "");

		assertThat(subagent.permissionMode()).isEqualTo("default");
	}

}
