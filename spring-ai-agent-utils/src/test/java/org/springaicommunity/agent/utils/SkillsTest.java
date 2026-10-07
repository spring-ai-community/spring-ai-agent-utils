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
package org.springaicommunity.agent.utils;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.springaicommunity.agent.tools.SkillsTool.Skill;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.UrlResource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link Skills}.
 */
@DisplayName("Skills Tests")
class SkillsTest {

	@Test
	@DisplayName("loadResource aggregates skills across multiple JARs sharing the same classpath prefix")
	void loadResourceAggregatesSkillsAcrossMultipleJarsSharingSamePrefix(@TempDir Path tempDir) throws IOException {
		// Two "SkillsJars" that both publish under the shared META-INF/skills root, as
		// documented for classpath JAR skill sharing.
		Path jarA = createSkillJar(tempDir.resolve("skills-a.jar"), "skill-a");
		Path jarB = createSkillJar(tempDir.resolve("skills-b.jar"), "skill-b");

		ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
		// Bootstrap classloader as parent (not the real test classpath): this project's
		// own test dependencies already include a real SkillsJar under META-INF/skills,
		// which would otherwise mix into the search results below.
		try (URLClassLoader multiJarClassLoader = new URLClassLoader(
				new URL[] { jarA.toUri().toURL(), jarB.toUri().toURL() }, null)) {
			Thread.currentThread().setContextClassLoader(multiJarClassLoader);

			List<Skill> skills = Skills.loadResource(new ClassPathResource("META-INF/skills", multiJarClassLoader));

			// Before the fix, ClassPathResource#getURL() resolved to only the first
			// matching classpath entry, so only one of the two JARs' skills would be
			// found here.
			assertThat(skills).hasSize(2);
			assertThat(skills.stream().map(skill -> skill.frontMatter().get("name")))
				.containsExactlyInAnyOrder("skill-a", "skill-b");
		}
		finally {
			Thread.currentThread().setContextClassLoader(originalClassLoader);
		}
	}

	@Test
	@DisplayName("loadResource aggregates skills when a filesystem directory precedes a JAR on the classpath")
	void loadResourceAggregatesSkillsWhenDirectoryPrecedesJar(@TempDir Path tempDir) throws IOException {
		// The application's own classes directory comes before dependency JARs on a
		// typical classpath, so ClassPathResource#getFile() resolves to the directory.
		Path classesDir = tempDir.resolve("classes");
		Path localSkill = classesDir.resolve("META-INF/skills/local-skill/SKILL.md");
		Files.createDirectories(localSkill.getParent());
		Files.writeString(localSkill, """
				---
				name: local-skill
				description: Test skill local-skill
				---

				local-skill content.
				""");
		Path jar = createSkillJar(tempDir.resolve("skills-a.jar"), "skill-a");

		try (URLClassLoader classLoader = new URLClassLoader(
				new URL[] { classesDir.toUri().toURL(), jar.toUri().toURL() }, null)) {

			List<Skill> skills = Skills.loadResource(new ClassPathResource("META-INF/skills", classLoader));

			assertThat(skills.stream().map(skill -> skill.frontMatter().get("name")))
				.containsExactlyInAnyOrder("local-skill", "skill-a");
			// Filesystem skills keep a plain directory base path, not a file: URL
			assertThat(skills).filteredOn(skill -> "local-skill".equals(skill.frontMatter().get("name")))
				.singleElement()
				.extracting(Skill::basePath)
				.isEqualTo(localSkill.getParent().toAbsolutePath().toString());
		}
	}

	@Test
	@DisplayName("loadResource scans the ClassPathResource's own class loader")
	void loadResourceUsesResourceClassLoader(@TempDir Path tempDir) throws IOException {
		Path jar = createSkillJar(tempDir.resolve("skills-a.jar"), "skill-a");

		// The thread context class loader is left untouched: the skills are only
		// visible through the class loader the resource was created with.
		try (URLClassLoader classLoader = new URLClassLoader(new URL[] { jar.toUri().toURL() }, null)) {

			List<Skill> skills = Skills.loadResource(new ClassPathResource("META-INF/skills", classLoader));

			assertThat(skills.stream().map(skill -> skill.frontMatter().get("name"))).containsExactly("skill-a");
		}
	}

	@Test
	@DisplayName("loadResource releases JAR handles for a direct JAR URL")
	void loadResourceReleasesDirectJarHandle(@TempDir Path tempDir) throws IOException {
		Path jar = createSkillJar(tempDir.resolve("direct.jar"), "direct-skill");
		UrlResource resource = new UrlResource("jar:" + jar.toUri() + "!/META-INF/skills/");

		List<Skill> skills = Skills.loadResource(resource);

		assertThat(skills.stream().map(skill -> skill.frontMatter().get("name")))
			.containsExactly("direct-skill");
		Files.delete(jar);
		assertThat(jar).doesNotExist();
	}

	@Test
	@DisplayName("loadResource releases JAR handles when directory entries are absent")
	void loadResourceReleasesFallbackJarHandle(@TempDir Path tempDir) throws IOException {
		Path jar = createSkillJar(tempDir.resolve("fallback.jar"), "fallback-skill", false);

		try (URLClassLoader classLoader = new URLClassLoader(new URL[] { jar.toUri().toURL() }, null)) {
			List<Skill> skills = Skills.loadResource(new ClassPathResource("META-INF/skills", classLoader));

			assertThat(skills.stream().map(skill -> skill.frontMatter().get("name")))
				.containsExactly("fallback-skill");
		}
		Files.delete(jar);
		assertThat(jar).doesNotExist();
	}

	private static Path createSkillJar(Path jarPath, String skillName) throws IOException {
		return createSkillJar(jarPath, skillName, true);
	}

	private static Path createSkillJar(Path jarPath, String skillName, boolean directoryEntries) throws IOException {
		// A real MANIFEST.MF is required: both the classpath*: resolution strategy and
		// the manual JAR scan fallback in Skills discover JAR roots by enumerating
		// ClassLoader.getResources("META-INF/MANIFEST.MF") across the classpath, which
		// every Maven/Gradle-built JAR has but a hand-built one won't unless added here.
		Manifest manifest = new Manifest();
		manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
		try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(jarPath), manifest)) {
			if (directoryEntries) {
				jos.putNextEntry(new JarEntry("META-INF/skills/"));
				jos.closeEntry();
				jos.putNextEntry(new JarEntry("META-INF/skills/" + skillName + "/"));
				jos.closeEntry();
			}
			jos.putNextEntry(new JarEntry("META-INF/skills/" + skillName + "/SKILL.md"));
			jos.write(("""
					---
					name: %s
					description: Test skill %s
					---

					%s content.
					""".formatted(skillName, skillName, skillName)).getBytes(StandardCharsets.UTF_8));
			jos.closeEntry();
		}
		return jarPath;
	}

}
