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
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springaicommunity.agent.common.workspace.Workspace;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.execution.ToolExecutionException;
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
			assertThat(callback.getToolDefinition().description()).contains("test-skill");
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
			assertThat(callback.getToolDefinition().description()).contains("test-skill");
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
			assertThat(description).contains("test-skill");
			assertThat(description).contains("another-skill");
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
			assertThat(description).contains("test-skill");
			assertThat(description).contains("another-skill");
		}

		@Test
		@DisplayName("should load single skill from nested JAR path")
		void shouldLoadSingleSkillFromNestedJarPath() throws Exception {
			UrlResource jarResource = new UrlResource(
					"jar:" + jarPath.toUri() + "!/skills/my-skill");

			ToolCallback callback = SkillsTool.builder().addSkillsResource(jarResource).build();

			assertThat(callback).isNotNull();
			String description = callback.getToolDefinition().description();
			assertThat(description).contains("test-skill");
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
			assertThat(callback.getToolDefinition().description()).contains("pdf");

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
			assertThat(callback.getToolDefinition().description()).contains("spring-boot-skill");

			String result = callback.call("{\"command\":\"spring-boot-skill\"}");
			assertThat(result).contains("Base directory for this skill:");
			assertThat(result).contains("Spring Boot");
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

	@Nested
	@DisplayName("Programmatic Skills")
	class ProgrammaticSkillsTests {

		@Test
		@DisplayName("should register a skill programmatically via addSkill")
		void shouldRegisterSkillProgrammatically() {
			ToolCallback callback = SkillsTool.builder()
				.addSkill("my-skill", "A programmatic skill", "This is the skill content.")
				.build();

			assertThat(callback).isNotNull();
			assertThat(callback.getToolDefinition().description()).contains("my-skill");
			assertThat(callback.getToolDefinition().description()).contains("A programmatic skill");
		}

		@Test
		@DisplayName("should return content without base directory for programmatic skill")
		void shouldReturnContentWithoutBaseDirectory() {
			SkillsTool.Skill skill = new SkillsTool.Skill("",
					Map.of("name", "my-skill", "description", "A programmatic skill"),
					"This is the skill content.");

			SkillsTool.SkillsFunction function = new SkillsTool.SkillsFunction(Map.of("my-skill", skill));

			String result = function.apply(new SkillsTool.SkillsInput("my-skill"));

			assertThat(result).isEqualTo("This is the skill content.");
			assertThat(result).doesNotContain("Base directory for this skill:");
		}

		@Test
		@DisplayName("should keep the first skill when a name is registered twice")
		void shouldKeepFirstSkillForDuplicateName() {
			ToolCallback callback = SkillsTool.builder()
				.addSkill("my-skill", "First registration", "First content.")
				.addSkill("my-skill", "Second registration", "Second content.")
				.build();

			String description = callback.getToolDefinition().description();
			assertThat(description).contains("First registration").doesNotContain("Second registration");
			assertThat(description.split("<name>my-skill</name>", -1)).hasSize(2);
			assertThat(callback.call("{\"command\":\"my-skill\"}")).isEqualTo("\"First content.\"");
		}

		@Test
		@DisplayName("should combine programmatic and resource skills in one tool")
		void shouldCombineProgrammaticAndResourceSkills() {
			ToolCallback callback = SkillsTool.builder()
				.addSkillsResource(new ClassPathResource("META-INF/skills"))
				.addSkill("my-skill", "A programmatic skill", "This is the skill content.")
				.build();

			assertThat(callback.getToolDefinition().description()).contains("my-skill", "spring-boot-skill");

			assertThat(callback.call("{\"command\":\"my-skill\"}")).isEqualTo("\"This is the skill content.\"");
			assertThat(callback.call("{\"command\":\"spring-boot-skill\"}"))
				.startsWith("\"Base directory for this skill: META-INF/skills/");
		}

	}

	@Nested
	@DisplayName("Skill Observation Tests")
	class SkillObservationTests {

		@Test
		@DisplayName("should notify listener when skill is loaded with ToolContext")
		void shouldNotifyListenerWhenSkillIsLoadedWithToolContext() {
			AtomicReference<SkillsTool.Skill> loadedSkill = new AtomicReference<>();
			AtomicReference<ToolContext> loadedContext = new AtomicReference<>();

			ToolCallback callback = SkillsTool.builder()
				.addSkill("my-skill", "My description", "My instructions content")
				.onSkillLoaded((skill, toolContext) -> {
					loadedSkill.set(skill);
					loadedContext.set(toolContext);
				})
				.build();

			ToolContext toolContext = new ToolContext(Map.of("sessionKey", "sessionVal"));
			String result = callback.call("{\"command\":\"my-skill\"}", toolContext);

			assertThat(result).isEqualTo("\"My instructions content\"");
			assertThat(loadedSkill.get()).isNotNull();
			assertThat(loadedSkill.get().name()).isEqualTo("my-skill");
			assertThat(loadedSkill.get().content()).isEqualTo("My instructions content");
			assertThat(loadedSkill.get().frontMatter()).containsEntry("description", "My description");
			assertThat(loadedContext.get()).isSameAs(toolContext);
		}

		@Test
		@DisplayName("should pass null ToolContext when called without context")
		void shouldPassNullToolContextWhenCalledWithoutContext() {
			AtomicReference<ToolContext> loadedContext = new AtomicReference<>();
			AtomicReference<SkillsTool.Skill> loadedSkill = new AtomicReference<>();

			ToolCallback callback = SkillsTool.builder()
				.addSkill("my-skill", "Desc", "Content")
				.onSkillLoaded((skill, toolContext) -> {
					loadedSkill.set(skill);
					loadedContext.set(toolContext);
				})
				.build();

			callback.call("{\"command\":\"my-skill\"}");

			assertThat(loadedSkill.get()).isNotNull();
			assertThat(loadedContext.get()).isNull();
		}

		@Test
		@DisplayName("should not notify listener when skill is not found")
		void shouldNotNotifyListenerWhenSkillIsNotFound() {
			AtomicInteger calls = new AtomicInteger();

			ToolCallback callback = SkillsTool.builder()
				.addSkill("my-skill", "Desc", "Content")
				.onSkillLoaded((skill, toolContext) -> calls.incrementAndGet())
				.build();

			String result = callback.call("{\"command\":\"unknown-skill\"}");

			assertThat(result).isEqualTo("\"Skill not found: unknown-skill\"");
			assertThat(calls.get()).isZero();
		}

		@Test
		@DisplayName("should support multiple listeners")
		void shouldSupportMultipleListeners() {
			AtomicInteger firstCalls = new AtomicInteger();
			AtomicInteger secondCalls = new AtomicInteger();

			ToolCallback callback = SkillsTool.builder()
				.addSkill("my-skill", "Desc", "Content")
				.onSkillLoaded((skill, toolContext) -> firstCalls.incrementAndGet())
				.onSkillLoaded((skill, toolContext) -> secondCalls.incrementAndGet())
				.build();

			callback.call("{\"command\":\"my-skill\"}");

			assertThat(firstCalls.get()).isEqualTo(1);
			assertThat(secondCalls.get()).isEqualTo(1);
		}

		@Test
		@DisplayName("should preserve SkillsFunction implements Function type contract")
		void shouldPreserveSkillsFunctionImplementsFunctionContract() {
			SkillsTool.Skill skill = new SkillsTool.Skill("", Map.of("name", "skill1"), "content");
			SkillsTool.SkillsFunction function = new SkillsTool.SkillsFunction(Map.of("skill1", skill));

			// Verify assignment to java.util.function.Function (prevents IncompatibleClassChangeError)
			Function<SkillsTool.SkillsInput, String> fn = function;
			String result = fn.apply(new SkillsTool.SkillsInput("skill1"));
			assertThat(result).isEqualTo("content");
		}

		@Test
		@DisplayName("should snapshot listeners at build time and not share mutable builder list")
		void shouldSnapshotListenersAtBuildTime() {
			AtomicInteger calls = new AtomicInteger();
			SkillsTool.Builder builder = SkillsTool.builder().addSkill("my-skill", "Desc", "Content");

			ToolCallback callback = builder.build();

			// Adding listener to builder after build() must not affect the already-built callback
			builder.onSkillLoaded((skill, toolContext) -> calls.incrementAndGet());

			callback.call("{\"command\":\"my-skill\"}");
			assertThat(calls.get()).isZero();
		}

		@Test
		@DisplayName("should not notify listener if preparing result fails (e.g. workspace throws)")
		void shouldNotNotifyListenerIfPreparingResultFails(@TempDir Path tempDir) throws IOException {
			Files.writeString(tempDir.resolve("SKILL.md"), SKILL_MD_CONTENT, StandardCharsets.UTF_8);
			AtomicInteger calls = new AtomicInteger();
			IllegalStateException failure = new IllegalStateException("workspace translation failed");
			Workspace failingWorkspace = new Workspace() {
				@Override
				public Path root() {
					return tempDir;
				}

				@Override
				public String display(String path) {
					throw failure;
				}
			};

			ToolCallback callback = SkillsTool.builder()
				.addSkillsDirectory(tempDir.toString())
				.workspace(failingWorkspace)
				.onSkillLoaded((skill, toolContext) -> calls.incrementAndGet())
				.build();

			assertThatThrownBy(() -> callback.call("{\"command\":\"test-skill\"}"))
				.isInstanceOf(ToolExecutionException.class)
				.hasCause(failure);

			assertThat(calls.get()).isZero();
		}

		@Test
		@DisplayName("should propagate exception when listener throws")
		void shouldPropagateExceptionWhenListenerThrows() {
			ToolCallback callback = SkillsTool.builder()
				.addSkill("my-skill", "Desc", "Content")
				.onSkillLoaded((skill, toolContext) -> {
					throw new RuntimeException("listener failed");
				})
				.build();

			assertThatThrownBy(() -> callback.call("{\"command\":\"my-skill\"}"))
				.isInstanceOf(RuntimeException.class)
				.hasMessage("listener failed");
		}

	}
}
