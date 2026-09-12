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

	private static Path createSkillJar(Path jarPath, String skillName) throws IOException {
		// A real MANIFEST.MF is required: both the classpath*: resolution strategy and
		// the manual JAR scan fallback in Skills discover JAR roots by enumerating
		// ClassLoader.getResources("META-INF/MANIFEST.MF") across the classpath, which
		// every Maven/Gradle-built JAR has but a hand-built one won't unless added here.
		Manifest manifest = new Manifest();
		manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
		try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(jarPath), manifest)) {
			jos.putNextEntry(new JarEntry("META-INF/skills/"));
			jos.closeEntry();
			jos.putNextEntry(new JarEntry("META-INF/skills/" + skillName + "/"));
			jos.closeEntry();
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
