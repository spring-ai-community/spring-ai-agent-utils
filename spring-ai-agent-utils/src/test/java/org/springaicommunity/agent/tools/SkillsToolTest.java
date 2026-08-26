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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.UrlResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link SkillsTool}.
 *
 * @author Claude Code
 * @author kezhenxu94
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

	private static List<String> toolNames(ToolCallbackProvider provider) {
		return Arrays.stream(provider.getToolCallbacks()).map(tc -> tc.getToolDefinition().name()).toList();
	}

	private static ToolCallback toolNamed(ToolCallbackProvider provider, String name) {
		return Arrays.stream(provider.getToolCallbacks())
			.filter(tc -> name.equals(tc.getToolDefinition().name()))
			.findFirst()
			.orElseThrow(() -> new AssertionError("No tool callback named: " + name));
	}

	private static Path writeSkill(Path parentDir, String directoryName, String skillMd) throws IOException {
		Path skillDir = parentDir.resolve(directoryName);
		Files.createDirectories(skillDir);
		Files.writeString(skillDir.resolve("SKILL.md"), skillMd, StandardCharsets.UTF_8);
		return skillDir;
	}

	@Nested
	@DisplayName("Filesystem Skills")
	class FilesystemSkillsTests {

		@Test
		@DisplayName("should load skills from directory via addSkillsDirectory")
		void shouldLoadSkillsFromDirectory(@TempDir Path tempDir) throws IOException {
			writeSkill(tempDir, "my-skill", SKILL_MD_CONTENT);

			ToolCallbackProvider provider = SkillsTool.builder().addSkillsDirectory(tempDir.toString()).build();

			assertThat(toolNames(provider)).containsExactly("test-skill");
			assertThat(toolNamed(provider, "test-skill").getToolDefinition().description()).contains("A test skill");
		}

		@Test
		@DisplayName("should load skills from FileSystemResource via addSkillsResource")
		void shouldLoadSkillsFromFileSystemResource(@TempDir Path tempDir) throws IOException {
			writeSkill(tempDir, "my-skill", SKILL_MD_CONTENT);

			ToolCallbackProvider provider = SkillsTool.builder()
				.addSkillsResource(new FileSystemResource(tempDir.toFile()))
				.build();

			assertThat(toolNames(provider)).containsExactly("test-skill");
		}

		@Test
		@DisplayName("should create one tool callback per skill")
		void shouldCreateOneToolCallbackPerSkill(@TempDir Path tempDir) throws IOException {
			writeSkill(tempDir, "skill-one", SKILL_MD_CONTENT);
			writeSkill(tempDir, "skill-two", SKILL_MD_CONTENT_2);

			ToolCallbackProvider provider = SkillsTool.builder().addSkillsDirectory(tempDir.toString()).build();

			assertThat(toolNames(provider)).containsExactlyInAnyOrder("test-skill", "another-skill");
			assertThat(toolNamed(provider, "test-skill").getToolDefinition().description()).contains("A test skill")
				.doesNotContain("Another test skill");
			assertThat(toolNamed(provider, "another-skill").getToolDefinition().description())
				.contains("Another test skill")
				.doesNotContain("A test skill");
		}

		@Test
		@DisplayName("should expose extra front-matter fields in the tool description")
		void shouldExposeExtraFrontMatterFields(@TempDir Path tempDir) throws IOException {
			writeSkill(tempDir, "my-skill", """
					---
					name: rich-skill
					description: A rich skill
					allowed-tools: Read, Grep
					model: claude-sonnet-4-5-20250929
					---

					Content.
					""");

			ToolCallbackProvider provider = SkillsTool.builder().addSkillsDirectory(tempDir.toString()).build();

			assertThat(toolNamed(provider, "rich-skill").getToolDefinition().description()).contains("A rich skill")
				.contains("allowed-tools: Read, Grep")
				.contains("model: claude-sonnet-4-5-20250929");
		}

		@Test
		@DisplayName("should declare a no-argument input schema")
		void shouldDeclareNoArgumentInputSchema(@TempDir Path tempDir) throws IOException {
			writeSkill(tempDir, "my-skill", SKILL_MD_CONTENT);

			ToolCallbackProvider provider = SkillsTool.builder().addSkillsDirectory(tempDir.toString()).build();

			assertThat(toolNamed(provider, "test-skill").getToolDefinition().inputSchema())
				.contains("\"properties\" : { }");
		}

		@Test
		@DisplayName("should apply a custom tool description template per skill")
		void shouldApplyCustomToolDescriptionTemplate(@TempDir Path tempDir) throws IOException {
			writeSkill(tempDir, "my-skill", SKILL_MD_CONTENT);

			ToolCallbackProvider provider = SkillsTool.builder()
				.addSkillsDirectory(tempDir.toString())
				.toolDescriptionTemplate("Custom prefix: %s")
				.build();

			assertThat(toolNamed(provider, "test-skill").getToolDefinition().description())
				.isEqualTo("Custom prefix: A test skill");
		}

		@Test
		@DisplayName("should throw when directory does not exist")
		void shouldThrowWhenDirectoryDoesNotExist() {
			assertThatThrownBy(() -> SkillsTool.builder().addSkillsDirectory("/nonexistent/directory").build())
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

		@Test
		@DisplayName("should throw on duplicate skill names")
		void shouldThrowOnDuplicateSkillNames(@TempDir Path tempDir) throws IOException {
			writeSkill(tempDir, "first", SKILL_MD_CONTENT);
			writeSkill(tempDir, "second", SKILL_MD_CONTENT);

			assertThatThrownBy(() -> SkillsTool.builder().addSkillsDirectory(tempDir.toString()).build())
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Duplicate skill tool name 'test-skill'");
		}

		@Test
		@DisplayName("should support scoped skill names")
		void shouldSupportScopedSkillNames(@TempDir Path tempDir) throws IOException {
			writeSkill(tempDir, "project-a", """
					---
					name: project-a:pdf
					description: The project-a PDF skill
					---

					Project A content.
					""");
			writeSkill(tempDir, "project-b", """
					---
					name: project-b:pdf
					description: The project-b PDF skill
					---

					Project B content.
					""");

			ToolCallbackProvider provider = SkillsTool.builder().addSkillsDirectory(tempDir.toString()).build();

			assertThat(toolNames(provider)).containsExactlyInAnyOrder("project-a_pdf", "project-b_pdf");
			assertThat(toolNamed(provider, "project-a_pdf").call("{}")).contains("Project A content.");
			assertThat(toolNamed(provider, "project-b_pdf").call("{}")).contains("Project B content.");
		}

		@Test
		@DisplayName("should throw when scoped skill names collide after tool name derivation")
		void shouldThrowWhenDerivedToolNamesCollide(@TempDir Path tempDir) throws IOException {
			writeSkill(tempDir, "scoped", """
					---
					name: project-a:pdf
					description: A scoped skill
					---

					Content.
					""");
			writeSkill(tempDir, "flat", """
					---
					name: project-a_pdf
					description: A flat skill
					---

					Content.
					""");

			assertThatThrownBy(() -> SkillsTool.builder().addSkillsDirectory(tempDir.toString()).build())
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Duplicate skill tool name 'project-a_pdf'");
		}

		@Test
		@DisplayName("should throw on a skill name that is too long for a tool name")
		void shouldThrowOnTooLongSkillName(@TempDir Path tempDir) throws IOException {
			writeSkill(tempDir, "my-skill", """
					---
					name: %s
					description: A skill with a very long name
					---

					Content.
					""".formatted("x".repeat(65)));

			assertThatThrownBy(() -> SkillsTool.builder().addSkillsDirectory(tempDir.toString()).build())
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("is too long");
		}

		@Test
		@DisplayName("should throw when the name front-matter field is missing")
		void shouldThrowWhenNameIsMissing(@TempDir Path tempDir) throws IOException {
			writeSkill(tempDir, "my-skill", """
					---
					description: A skill without a name
					---

					Content.
					""");

			assertThatThrownBy(() -> SkillsTool.builder().addSkillsDirectory(tempDir.toString()).build())
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Missing required 'name' front-matter field in skill:");
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

			ToolCallbackProvider provider = SkillsTool.builder().addSkillsResource(jarResource).build();

			assertThat(toolNames(provider)).containsExactlyInAnyOrder("test-skill", "another-skill");
		}

		@Test
		@DisplayName("should load single skill from nested JAR path")
		void shouldLoadSingleSkillFromNestedJarPath() throws Exception {
			UrlResource jarResource = new UrlResource("jar:" + jarPath.toUri() + "!/skills/my-skill");

			ToolCallbackProvider provider = SkillsTool.builder().addSkillsResource(jarResource).build();

			assertThat(toolNames(provider)).containsExactly("test-skill");
		}

	}

	@Nested
	@DisplayName("Classpath JAR Integration")
	class ClasspathJarIntegrationTests {

		@Test
		@DisplayName("should load pdf skill from anthropics__skills__pdf JAR on classpath")
		void shouldLoadPdfSkillFromClasspathJar() {
			ClassPathResource resource = new ClassPathResource("META-INF/resources/skills/anthropics/skills");

			ToolCallbackProvider provider = SkillsTool.builder().addSkillsResource(resource).build();

			assertThat(toolNames(provider)).contains("pdf");

			String result = toolNamed(provider, "pdf").call("{}");
			assertThat(result).contains("Base directory for this skill:");
			assertThat(result).contains("PDF");
		}

		@Test
		@DisplayName("should load spring-boot skill from sivalabs skills JAR on classpath")
		void shouldLoadSpringBootSkillFromClasspathJar() {
			// sivalabs JAR uses META-INF/skills/ (not META-INF/resources/skills/)
			// and includes explicit directory entries, exercising a different code path
			ClassPathResource resource = new ClassPathResource("META-INF/skills/sivaprasadreddy/sivalabs-agent-skills");

			ToolCallbackProvider provider = SkillsTool.builder().addSkillsResource(resource).build();

			assertThat(toolNames(provider)).contains("spring-boot-skill");

			String result = toolNamed(provider, "spring-boot-skill").call("{}");
			assertThat(result).contains("Base directory for this skill:");
			assertThat(result).contains("Spring Boot");
		}

	}

	@Nested
	@DisplayName("SkillFunction")
	class SkillFunctionTests {

		@Test
		@DisplayName("should return content with base directory for filesystem skill")
		void shouldReturnContentWithBaseDirectory(@TempDir Path tempDir) throws IOException {
			Path skillDir = writeSkill(tempDir, "my-skill", SKILL_MD_CONTENT);

			ToolCallbackProvider provider = SkillsTool.builder().addSkillsDirectory(tempDir.toString()).build();

			String result = toolNamed(provider, "test-skill").call("{}");

			assertThat(result).contains("Base directory for this skill:");
			assertThat(result).contains(skillDir.toString());
			assertThat(result).contains("This is the test skill content.");
		}

		@Test
		@DisplayName("should not register a tool for an unknown skill")
		void shouldNotRegisterToolForUnknownSkill(@TempDir Path tempDir) throws IOException {
			writeSkill(tempDir, "my-skill", SKILL_MD_CONTENT);

			ToolCallbackProvider provider = SkillsTool.builder().addSkillsDirectory(tempDir.toString()).build();

			assertThat(toolNames(provider)).doesNotContain("nonexistent");
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

			ToolCallbackProvider provider = SkillsTool.builder().addSkillsResource(jarResource).build();

			String result = toolNamed(provider, "jar-skill").call("{}");

			assertThat(result).contains("Base directory for this skill: skills/jar-skill");
			assertThat(result).contains("JAR skill content.");
		}

	}

}
