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
package org.springaicommunity.agent.tools;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springaicommunity.agent.common.skill.SkillDescriptor;
import org.springaicommunity.agent.common.skill.SkillProvider;

import org.springframework.ai.tool.ToolCallback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link SkillsTool} with custom {@link SkillProvider} implementations.
 *
 * @author Daniel Volovik
 */
@DisplayName("SkillsTool Provider Tests")
class SkillsToolProviderTest {

	@Test
	@DisplayName("should build with custom provider")
	void shouldBuildWithCustomProvider() {
		SkillProvider provider = new StubSkillProvider("my-skill", "A custom skill", "Custom skill content");

		ToolCallback callback = SkillsTool.builder().addSkillProvider(provider).build();

		assertThat(callback).isNotNull();
		assertThat(callback.getToolDefinition().description()).contains("my-skill");
	}

	@Test
	@DisplayName("should resolve content from custom provider")
	void shouldResolveContentFromCustomProvider() {
		SkillProvider provider = new StubSkillProvider("my-skill", "A custom skill", "Custom skill content");

		ToolCallback callback = SkillsTool.builder().addSkillProvider(provider).build();

		String result = callback.call("{\"command\":\"my-skill\"}");
		assertThat(result).contains("Custom skill content");
	}

	@Test
	@DisplayName("should return not found for unknown skill from provider")
	void shouldReturnNotFoundForUnknownSkill() {
		SkillProvider provider = new StubSkillProvider("my-skill", "A custom skill", "Custom skill content");

		ToolCallback callback = SkillsTool.builder().addSkillProvider(provider).build();

		String result = callback.call("{\"command\":\"nonexistent\"}");
		assertThat(result).contains("Skill not found: nonexistent");
	}

	@Test
	@DisplayName("should combine filesystem skills with custom provider")
	void shouldCombineFilesystemWithCustomProvider(@TempDir Path tempDir) throws IOException {
		Path skillDir = tempDir.resolve("fs-skill");
		Files.createDirectories(skillDir);
		Files.writeString(skillDir.resolve("SKILL.md"),
				"---\nname: fs-skill\ndescription: Filesystem skill\n---\n\nFS content.", StandardCharsets.UTF_8);

		SkillProvider customProvider = new StubSkillProvider("custom-skill", "Custom skill", "Custom content");

		ToolCallback callback = SkillsTool.builder()
			.addSkillsDirectory(tempDir.toString())
			.addSkillProvider(customProvider)
			.build();

		assertThat(callback).isNotNull();
		String description = callback.getToolDefinition().description();
		assertThat(description).contains("fs-skill");
		assertThat(description).contains("custom-skill");

		// Both skills should be resolvable
		assertThat(callback.call("{\"command\":\"fs-skill\"}")).contains("FS content.");
		assertThat(callback.call("{\"command\":\"custom-skill\"}")).contains("Custom content");
	}

	@Test
	@DisplayName("should call provider on each invocation for dynamic content")
	void shouldCallProviderDynamicallyOnEachInvocation() {
		AtomicInteger counter = new AtomicInteger(0);
		SkillProvider dynamicProvider = new SkillProvider() {
			@Override
			public List<SkillDescriptor> getSkillDescriptors() {
				return List.of(new SimpleDescriptor("counter-skill", "Counts invocations"));
			}

			@Override
			public String getSkillContent(String skillName) {
				if ("counter-skill".equals(skillName)) {
					return "Invocation #" + counter.incrementAndGet();
				}
				return null;
			}
		};

		ToolCallback callback = SkillsTool.builder().addSkillProvider(dynamicProvider).build();

		assertThat(callback.call("{\"command\":\"counter-skill\"}")).contains("Invocation #1");
		assertThat(callback.call("{\"command\":\"counter-skill\"}")).contains("Invocation #2");
		assertThat(callback.call("{\"command\":\"counter-skill\"}")).contains("Invocation #3");
	}

	@Test
	@DisplayName("should throw when no skills or providers configured")
	void shouldThrowWhenNoSkillsOrProviders() {
		assertThatThrownBy(() -> SkillsTool.builder().build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("At least one skill or skill provider must be configured");
	}

	@Test
	@DisplayName("should resolve from first matching provider")
	void shouldResolveFromFirstMatchingProvider() {
		SkillProvider provider1 = new StubSkillProvider("shared-skill", "From provider 1", "Content from provider 1");
		SkillProvider provider2 = new StubSkillProvider("shared-skill", "From provider 2", "Content from provider 2");

		ToolCallback callback = SkillsTool.builder()
			.addSkillProvider(provider1)
			.addSkillProvider(provider2)
			.build();

		// First provider wins
		assertThat(callback.call("{\"command\":\"shared-skill\"}")).contains("Content from provider 1");
	}

	/**
	 * Stub SkillProvider for testing.
	 */
	private static class StubSkillProvider implements SkillProvider {

		private final String name;

		private final String description;

		private final String content;

		StubSkillProvider(String name, String description, String content) {
			this.name = name;
			this.description = description;
			this.content = content;
		}

		@Override
		public List<SkillDescriptor> getSkillDescriptors() {
			return List.of(new SimpleDescriptor(this.name, this.description));
		}

		@Override
		public String getSkillContent(String skillName) {
			if (this.name.equals(skillName)) {
				return this.content;
			}
			return null;
		}

	}

	private record SimpleDescriptor(String name, String description) implements SkillDescriptor {

		@Override
		public String getName() {
			return this.name;
		}

		@Override
		public Map<String, Object> getMetadata() {
			return Map.of("name", this.name, "description", this.description);
		}

	}

}