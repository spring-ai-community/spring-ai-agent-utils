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
package org.springaicommunity.agent.tools;

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.UrlResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link SkillsTool}.
 *
 * @author Claude Code
 */
@DisplayName("SkillsTool Tests")
class SkillsToolTest {

	private static final String SKILL_MD_CONTENT = """
			---
			name: test-skill
			description: A test skill
			---

			This is the test skill content.
			""";

	private static final String SKILL_MD_CONTENT_2 = """
			---
			name: another-skill
			description: Another test skill
			---

			This is another skill content.
			""";

	@Nested
	@DisplayName("Filesystem Skills")
	class FilesystemSkillsTests {

		@Test
		@DisplayName("should load skills from directory via addSkillsDirectory")
		void shouldLoadSkillsFromDirectory(@TempDir Path tempDir) throws IOException {
			Path skillDir = tempDir.resolve("my-skill");
			Files.createDirectories(skillDir);
			Files.writeString(skillDir.resolve("SKILL.md"), SKILL_MD_CONTENT, StandardCharsets.UTF_8);

			ToolCallback callback = SkillsTool.builder()
				.addSkillsDirectory(tempDir.toString())
				.build();

			assertThat(callback).isNotNull();
			assertThat(callback.getToolDefinition().name()).isEqualTo("Skill");
			assertThat(callback.getToolDefinition().description()).contains("\"test-skill\"");
		}

		@Test
		@DisplayName("should load skills from FileSystemResource via addSkillsResource")
		void shouldLoadSkillsFromFileSystemResource(@TempDir Path tempDir) throws IOException {
			Path skillDir = tempDir.resolve("my-skill");
			Files.createDirectories(skillDir);
			Files.writeString(skillDir.resolve("SKILL.md"), SKILL_MD_CONTENT, StandardCharsets.UTF_8);

			ToolCallback callback = SkillsTool.builder()
				.addSkillsResource(new FileSystemResource(tempDir.toFile()))
				.build();

			assertThat(callback).isNotNull();
			assertThat(callback.getToolDefinition().description()).contains("\"test-skill\"");
		}

		@Test
		@DisplayName("should load multiple skills from nested directories")
		void shouldLoadMultipleSkillsFromNestedDirectories(@TempDir Path tempDir) throws IOException {
			Path skillDir1 = tempDir.resolve("skill-one");
			Files.createDirectories(skillDir1);
			Files.writeString(skillDir1.resolve("SKILL.md"), SKILL_MD_CONTENT, StandardCharsets.UTF_8);

			Path skillDir2 = tempDir.resolve("skill-two");
			Files.createDirectories(skillDir2);
			Files.writeString(skillDir2.resolve("SKILL.md"), SKILL_MD_CONTENT_2, StandardCharsets.UTF_8);

			ToolCallback callback = SkillsTool.builder()
				.addSkillsDirectory(tempDir.toString())
				.build();

			assertThat(callback).isNotNull();
			String description = callback.getToolDefinition().description();
			assertThat(description).contains("\"test-skill\"");
			assertThat(description).contains("\"another-skill\"");
		}

		@Test
		@DisplayName("should throw when directory does not exist")
		void shouldThrowWhenDirectoryDoesNotExist() {
			assertThatThrownBy(
					() -> SkillsTool.builder().addSkillsDirectory("/nonexistent/directory").build())
				.isInstanceOf(RuntimeException.class)
				.hasMessageContaining("Root directory does not exist: /nonexistent/directory");
		}

		@Test
		@DisplayName("should throw when no skills are configured")
		void shouldThrowWhenNoSkillsConfigured(@TempDir Path tempDir) {
			assertThatThrownBy(() -> SkillsTool.builder().addSkillsDirectory(tempDir.toString()).build())
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("At least one skill must be configured");
		}

	}

	@Nested
	@DisplayName("JAR Classpath Skills")
	class JarClasspathSkillsTests {

		private static Path jarPath;

		@TempDir
		static Path jarTempDir;

		@BeforeAll
		static void createTestJar() throws IOException {
			jarPath = jarTempDir.resolve("test-skills.jar");

			try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jarPath.toFile()))) {
				// Add directory entries
				jos.putNextEntry(new JarEntry("skills/"));
				jos.closeEntry();
				jos.putNextEntry(new JarEntry("skills/my-skill/"));
				jos.closeEntry();

				// Add SKILL.md
				jos.putNextEntry(new JarEntry("skills/my-skill/SKILL.md"));
				jos.write(SKILL_MD_CONTENT.getBytes(StandardCharsets.UTF_8));
				jos.closeEntry();

				// Add second skill
				jos.putNextEntry(new JarEntry("skills/another-skill/"));
				jos.closeEntry();
				jos.putNextEntry(new JarEntry("skills/another-skill/SKILL.md"));
				jos.write(SKILL_MD_CONTENT_2.getBytes(StandardCharsets.UTF_8));
				jos.closeEntry();
			}
		}

		@Test
		@DisplayName("should load skills from JAR resource")
		void shouldLoadSkillsFromJarResource() throws Exception {
			UrlResource jarResource = new UrlResource("jar:" + jarPath.toUri() + "!/skills");

			ToolCallback callback = SkillsTool.builder().addSkillsResource(jarResource).build();

			assertThat(callback).isNotNull();
			String description = callback.getToolDefinition().description();
			assertThat(description).contains("\"test-skill\"");
			assertThat(description).contains("\"another-skill\"");
		}

		@Test
		@DisplayName("should load single skill from nested JAR path")
		void shouldLoadSingleSkillFromNestedJarPath() throws Exception {
			UrlResource jarResource = new UrlResource(
					"jar:" + jarPath.toUri() + "!/skills/my-skill");

			ToolCallback callback = SkillsTool.builder().addSkillsResource(jarResource).build();

			assertThat(callback).isNotNull();
			String description = callback.getToolDefinition().description();
			assertThat(description).contains("\"test-skill\"");
		}

	}

	@Nested
	@DisplayName("Classpath JAR Integration")
	class ClasspathJarIntegrationTests {

		@Test
		@DisplayName("should load pdf skill from anthropics__skills__pdf JAR on classpath")
		void shouldLoadPdfSkillFromClasspathJar() {
			ClassPathResource resource = new ClassPathResource(
					"META-INF/resources/skills/anthropics/skills");

			ToolCallback callback = SkillsTool.builder().addSkillsResource(resource).build();

			assertThat(callback).isNotNull();
			assertThat(callback.getToolDefinition().description()).contains("\"pdf\"");

			String result = callback.call("{\"command\":\"pdf\"}");
			assertThat(result).contains("Base directory for this skill:");
			assertThat(result).contains("PDF");
		}

		@Test
		@DisplayName("should load spring-boot skill from sivalabs skills JAR on classpath")
		void shouldLoadSpringBootSkillFromClasspathJar() {
			// sivalabs JAR uses META-INF/skills/ (not META-INF/resources/skills/)
			// and includes explicit directory entries, exercising a different code path
			ClassPathResource resource = new ClassPathResource(
					"META-INF/skills/sivaprasadreddy/sivalabs-agent-skills");

			ToolCallback callback = SkillsTool.builder().addSkillsResource(resource).build();

			assertThat(callback).isNotNull();
			assertThat(callback.getToolDefinition().description()).contains("\"spring-boot-skill\"");

			String result = callback.call("{\"command\":\"spring-boot-skill\"}");
			assertThat(result).contains("Base directory for this skill:");
			assertThat(result).contains("Spring Boot");
		}

		@Test
		@DisplayName("should load skills from multiple JARs with same directory name")
		void shouldLoadSkillsFromMultipleJars(@TempDir Path tempDir) throws Exception {
			// Create JAR 1 with skill1
			Path jar1 = tempDir.resolve("jar1.jar");
			createJar(jar1, "META-INF/myskills/skill1/SKILL.md", """
					---
					name: skill1
					description: Skill 1
					---
					Skill 1 content.
					""");

			// Create JAR 2 with skill2
			Path jar2 = tempDir.resolve("jar2.jar");
			createJar(jar2, "META-INF/myskills/skill2/SKILL.md", """
					---
					name: skill2
					description: Skill 2
					---
					Skill 2 content.
					""");

			// Create a custom ClassLoader that includes both JARs
			URL[] urls = { jar1.toUri().toURL(), jar2.toUri().toURL() };
			try (URLClassLoader classLoader = new URLClassLoader(urls, null)) {

				ClassPathResource resource = new ClassPathResource("META-INF/myskills", classLoader);

				ToolCallback callback = SkillsTool.builder().addSkillsResource(resource).build();

				String description = callback.getToolDefinition().description();
				assertThat(description).contains("\"skill1\"");
				assertThat(description).as("Should contain skill2").contains("\"skill2\"");
			}
		}

		private void createJar(Path jarPath, String entryPath, String content) throws IOException {
			try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jarPath.toFile()))) {
				String[] parts = entryPath.split("/");
				StringBuilder currentPath = new StringBuilder();
				for (int i = 0; i < parts.length - 1; i++) {
					currentPath.append(parts[i]).append("/");
					jos.putNextEntry(new JarEntry(currentPath.toString()));
					jos.closeEntry();
				}
				jos.putNextEntry(new JarEntry(entryPath));
				jos.write(content.getBytes(StandardCharsets.UTF_8));
				jos.closeEntry();
			}
		}

	}

	@Nested
	@DisplayName("Tool Description")
	class ToolDescriptionTests {

		@Test
		@DisplayName("description should not use XML tags that look like tool names")
		void descriptionShouldNotUseXmlTags(@TempDir Path tempDir) throws IOException {
			Path skillDir = tempDir.resolve("my-skill");
			Files.createDirectories(skillDir);
			Files.writeString(skillDir.resolve("SKILL.md"), SKILL_MD_CONTENT, StandardCharsets.UTF_8);

			ToolCallback callback = SkillsTool.builder()
				.addSkillsDirectory(tempDir.toString())
				.build();

			String description = callback.getToolDefinition().description();

			// Should NOT contain XML like <name>test-skill</name> which confuses the LLM
			assertThat(description).doesNotContain("<name>");
			assertThat(description).doesNotContain("</name>");

			// Should contain the skill name in a command-list format
			assertThat(description).contains("\"test-skill\"");

			// Should explain how to call it
			assertThat(description).contains("Skill({\"command\":");

			// Should clarify these are NOT tool names
			assertThat(description).contains("NOT");
		}

	}

	@Nested
	@DisplayName("SkillsFunction")
	class SkillsFunctionTests {

		@Test
		@DisplayName("should return content with base directory for filesystem skill")
		void shouldReturnContentWithBaseDirectory(@TempDir Path tempDir) throws IOException {
			Path skillDir = tempDir.resolve("my-skill");
			Files.createDirectories(skillDir);
			Files.writeString(skillDir.resolve("SKILL.md"), SKILL_MD_CONTENT, StandardCharsets.UTF_8);

			ToolCallback callback = SkillsTool.builder()
				.addSkillsDirectory(tempDir.toString())
				.build();

			String result = callback.call("{\"command\":\"test-skill\"}");

			assertThat(result).contains("Base directory for this skill:");
			assertThat(result).contains(skillDir.toString());
			assertThat(result).contains("This is the test skill content.");
		}

		@Test
		@DisplayName("should return not found message for unknown skill")
		void shouldReturnNotFoundForUnknownSkill(@TempDir Path tempDir) throws IOException {
			Path skillDir = tempDir.resolve("my-skill");
			Files.createDirectories(skillDir);
			Files.writeString(skillDir.resolve("SKILL.md"), SKILL_MD_CONTENT, StandardCharsets.UTF_8);

			ToolCallback callback = SkillsTool.builder()
				.addSkillsDirectory(tempDir.toString())
				.build();

			String result = callback.call("{\"command\":\"nonexistent\"}");

			assertThat(result).contains("Skill not found: nonexistent");
		}

		@Test
		@DisplayName("should return JAR base path for JAR-loaded skill")
		void shouldReturnJarBasePathForJarSkill(@TempDir Path jarTempDir) throws Exception {
			Path jarPath = jarTempDir.resolve("test.jar");

			try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jarPath.toFile()))) {
				jos.putNextEntry(new JarEntry("skills/"));
				jos.closeEntry();
				jos.putNextEntry(new JarEntry("skills/jar-skill/"));
				jos.closeEntry();
				jos.putNextEntry(new JarEntry("skills/jar-skill/SKILL.md"));
				jos.write(("---\nname: jar-skill\ndescription: JAR skill\n---\n\nJAR skill content.")
					.getBytes(StandardCharsets.UTF_8));
				jos.closeEntry();
			}

			UrlResource jarResource = new UrlResource("jar:" + jarPath.toUri() + "!/skills");

			ToolCallback callback = SkillsTool.builder().addSkillsResource(jarResource).build();

			String result = callback.call("{\"command\":\"jar-skill\"}");

			assertThat(result).contains("Base directory for this skill: skills/jar-skill");
			assertThat(result).contains("JAR skill content.");
		}

	}

}
