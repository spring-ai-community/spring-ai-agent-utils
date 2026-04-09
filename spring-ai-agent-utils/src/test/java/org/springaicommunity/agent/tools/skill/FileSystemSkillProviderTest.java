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
package org.springaicommunity.agent.tools.skill;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springaicommunity.agent.common.skill.SkillDescriptor;

import org.springframework.core.io.FileSystemResource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link FileSystemSkillProvider}.
 *
 * @author Daniel Volovik
 */
@DisplayName("FileSystemSkillProvider Tests")
class FileSystemSkillProviderTest {

	private static final String SKILL_MD = """
			---
			name: test-skill
			description: A test skill
			---

			This is the test skill content.
			""";

	private static final String SKILL_MD_2 = """
			---
			name: another-skill
			description: Another test skill
			---

			This is another skill content.
			""";

	@Test
	@DisplayName("should load skills from directory and return descriptors")
	void shouldLoadFromDirectory(@TempDir Path tempDir) throws IOException {
		Path skillDir = tempDir.resolve("my-skill");
		Files.createDirectories(skillDir);
		Files.writeString(skillDir.resolve("SKILL.md"), SKILL_MD, StandardCharsets.UTF_8);

		FileSystemSkillProvider provider = FileSystemSkillProvider.fromDirectory(tempDir.toString());

		List<SkillDescriptor> descriptors = provider.getSkillDescriptors();
		assertThat(descriptors).hasSize(1);
		assertThat(descriptors.get(0).getName()).isEqualTo("test-skill");
		assertThat(descriptors.get(0).getMetadata()).containsEntry("name", "test-skill");
		assertThat(descriptors.get(0).getMetadata()).containsEntry("description", "A test skill");
	}

	@Test
	@DisplayName("should return content with base directory prefix")
	void shouldReturnContentWithBaseDirectory(@TempDir Path tempDir) throws IOException {
		Path skillDir = tempDir.resolve("my-skill");
		Files.createDirectories(skillDir);
		Files.writeString(skillDir.resolve("SKILL.md"), SKILL_MD, StandardCharsets.UTF_8);

		FileSystemSkillProvider provider = FileSystemSkillProvider.fromDirectory(tempDir.toString());

		String content = provider.getSkillContent("test-skill");
		assertThat(content).contains("Base directory for this skill:");
		assertThat(content).contains(skillDir.toString());
		assertThat(content).contains("This is the test skill content.");
	}

	@Test
	@DisplayName("should return null for unknown skill")
	void shouldReturnNullForUnknownSkill(@TempDir Path tempDir) throws IOException {
		Path skillDir = tempDir.resolve("my-skill");
		Files.createDirectories(skillDir);
		Files.writeString(skillDir.resolve("SKILL.md"), SKILL_MD, StandardCharsets.UTF_8);

		FileSystemSkillProvider provider = FileSystemSkillProvider.fromDirectory(tempDir.toString());

		assertThat(provider.getSkillContent("nonexistent")).isNull();
	}

	@Test
	@DisplayName("should load multiple skills")
	void shouldLoadMultipleSkills(@TempDir Path tempDir) throws IOException {
		Path skillDir1 = tempDir.resolve("skill-one");
		Files.createDirectories(skillDir1);
		Files.writeString(skillDir1.resolve("SKILL.md"), SKILL_MD, StandardCharsets.UTF_8);

		Path skillDir2 = tempDir.resolve("skill-two");
		Files.createDirectories(skillDir2);
		Files.writeString(skillDir2.resolve("SKILL.md"), SKILL_MD_2, StandardCharsets.UTF_8);

		FileSystemSkillProvider provider = FileSystemSkillProvider.fromDirectory(tempDir.toString());

		List<SkillDescriptor> descriptors = provider.getSkillDescriptors();
		assertThat(descriptors).hasSize(2);
		assertThat(descriptors).extracting(SkillDescriptor::getName)
			.containsExactlyInAnyOrder("test-skill", "another-skill");
	}

	@Test
	@DisplayName("should load skills from resource")
	void shouldLoadFromResource(@TempDir Path tempDir) throws IOException {
		Path skillDir = tempDir.resolve("my-skill");
		Files.createDirectories(skillDir);
		Files.writeString(skillDir.resolve("SKILL.md"), SKILL_MD, StandardCharsets.UTF_8);

		FileSystemSkillProvider provider = FileSystemSkillProvider
			.fromResource(new FileSystemResource(tempDir.toFile()));

		assertThat(provider.getSkillDescriptors()).hasSize(1);
		assertThat(provider.getSkillContent("test-skill")).contains("This is the test skill content.");
	}

	@Test
	@DisplayName("toXml should format metadata correctly")
	void shouldFormatXmlCorrectly(@TempDir Path tempDir) throws IOException {
		Path skillDir = tempDir.resolve("my-skill");
		Files.createDirectories(skillDir);
		Files.writeString(skillDir.resolve("SKILL.md"), SKILL_MD, StandardCharsets.UTF_8);

		FileSystemSkillProvider provider = FileSystemSkillProvider.fromDirectory(tempDir.toString());

		SkillDescriptor descriptor = provider.getSkillDescriptors().get(0);
		String xml = descriptor.toXml();
		assertThat(xml).contains("<skill>");
		assertThat(xml).contains("</skill>");
		assertThat(xml).contains("<name>test-skill</name>");
		assertThat(xml).contains("<description>A test skill</description>");
	}

}