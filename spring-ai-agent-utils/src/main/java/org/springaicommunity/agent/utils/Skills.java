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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

import org.springaicommunity.agent.tools.SkillsTool.Skill;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

/**
 * @author Christian Tzolov
 */

public class Skills {

	/**
	 * Loads skills from the given resources, which can be directories or classpath
	 * locations containing SKILL.md files.
	 * @param skillsResources the resources to load skills from
	 * @return a list of Skill objects containing the basePath, front-matter, and content
	 * of each SKILL.md file found in the resources
	 */
	public static List<Skill> loadResources(List<Resource> skillsResources) {
		List<Skill> skills = new ArrayList<>();
		for (Resource skillsResource : skillsResources) {
			skills.addAll(loadResource(skillsResource));
		}
		return skills;
	}

	/**
	 * Loads skills from the given resources. Supports filesystem directories, JAR-based
	 * classpath resources, and {@link ClassPathResource} references to directories inside
	 * JARs.
	 * @param skillsResources the resources to load skills from
	 * @return a list of Skill objects
	 * @throws RuntimeException if an I/O error occurs while reading the resource
	 */
	public static List<Skill> loadResource(Resource... skillsResources) {

		List<Skill> skills = new ArrayList<>();

		for (Resource skillsResource : skillsResources) {
			try {
				String path = skillsResource.getFile().toPath().toAbsolutePath().toString();
				skills.addAll(loadDirectory(path));
			}
			catch (IOException ex) {
				try {
					skills.addAll(loadJarResource(skillsResource));
				}
				catch (IOException jarEx) {
					throw new RuntimeException("Failed to load skills from resource: " + skillsResource, jarEx);
				}
			}
		}
		return skills;
	}

	public static List<Skill> loadDirectories(List<String> rootDirectories) {
		List<Skill> skills = new ArrayList<>();
		for (String rootDirectory : rootDirectories) {
			skills.addAll(loadDirectory(rootDirectory));
		}
		return skills;
	}

	/**
	 * Recursively finds all SKILL.md files in the given root directory and returns their
	 * parsed contents.
	 * @param rootDirectory the root directory to search for SKILL.md files
	 * @return a list of Skill objects containing the basePath, front-matter, and content
	 * of each SKILL.md file
	 * @throws RuntimeException if an I/O error occurs while reading the directory or
	 * files
	 */
	public static List<Skill> loadDirectory(String rootDirectory) {

		Path rootPath = Paths.get(rootDirectory);

		if (!Files.exists(rootPath)) {
			throw new RuntimeException("Root directory does not exist: " + rootDirectory);
		}

		if (!Files.isDirectory(rootPath)) {
			throw new RuntimeException("Path is not a directory: " + rootDirectory);
		}

		List<Skill> skills = new ArrayList<>();

		try (Stream<Path> paths = Files.walk(rootPath)) {
			paths.filter(Files::isRegularFile)
				.filter(path -> path.getFileName().toString().equals("SKILL.md"))
				.forEach(path -> {
					try {
						String markdown = Files.readString(path, StandardCharsets.UTF_8);
						MarkdownParser parser = new MarkdownParser(markdown);
						skills.add(new Skill(path.getParent().toString(), parser.getFrontMatter(),
								parser.getContent()));
					}
					catch (IOException e) {
						throw new RuntimeException("Failed to read SKILL.md file: " + path, e);
					}
				});
		}
		catch (IOException e) {
			throw new RuntimeException("Failed to walk root directory: " + rootDirectory, e);
		}

		return skills;
	}

	/**
	 * Loads skills from a non-filesystem resource. Handles two cases:
	 * <ul>
	 * <li>Resources with resolvable {@code jar:} URLs (e.g.,
	 * {@link org.springframework.core.io.UrlResource}) — uses
	 * {@link JarURLConnection}</li>
	 * <li>{@link ClassPathResource} where the directory lacks an explicit JAR entry —
	 * uses Spring's {@link ResourcePatternResolver} with a manual JAR scan fallback</li>
	 * </ul>
	 * @param resource the resource pointing to a skills directory
	 * @return a list of Skill objects parsed from SKILL.md files
	 * @throws IOException if an I/O error occurs while reading
	 */
	private static List<Skill> loadJarResource(Resource resource) throws IOException {
		URL resourceUrl;
		try {
			resourceUrl = resource.getURL();
		}
		catch (FileNotFoundException ex) {
			// ClassPathResource for a JAR directory without an explicit directory entry
			// cannot resolve to a URL. Fall back to classpath scanning.
			if (resource instanceof ClassPathResource classPathResource) {
				return loadFromClasspath(classPathResource.getPath());
			}
			throw ex;
		}

		String protocol = resourceUrl.getProtocol();

		if (!"jar".equals(protocol)) {
			throw new IOException("Unsupported resource protocol for JAR loading: " + protocol);
		}

		JarURLConnection jarConnection = (JarURLConnection) resourceUrl.openConnection();
		String entryPrefix = jarConnection.getEntryName();
		if (!entryPrefix.endsWith("/")) {
			entryPrefix = entryPrefix + "/";
		}
		return scanJarForSkills(jarConnection.getJarFile(), entryPrefix);
	}

	/**
	 * Discovers SKILL.md files under the given classpath prefix using Spring's
	 * {@link ResourcePatternResolver}. Falls back to manual JAR scanning for JARs that
	 * lack explicit directory entries (a known limitation of
	 * {@link PathMatchingResourcePatternResolver} — see Spring Framework issue #16711).
	 * @param classpathPrefix the classpath prefix to scan (e.g.,
	 * "META-INF/resources/skills")
	 * @return a list of Skill objects parsed from discovered SKILL.md files
	 * @throws IOException if an I/O error occurs during scanning or reading
	 */
	private static List<Skill> loadFromClasspath(String classpathPrefix) throws IOException {
		// Primary: Spring's ResourcePatternResolver — works for well-formed JARs with
		// explicit directory entries and for resources on the filesystem.
		ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
		Resource[] resources = resolver.getResources("classpath*:" + classpathPrefix + "/**/SKILL.md");

		if (resources.length > 0) {
			List<Skill> skills = new ArrayList<>();
			for (Resource skillResource : resources) {
				try (InputStream is = skillResource.getInputStream()) {
					String basePath = deriveBasePathFromUrl(skillResource.getURL());
					skills.add(parseSkill(is, basePath));
				}
			}
			return skills;
		}

		// Fallback: Manual JAR scanning for JARs without directory entries.
		// Uses the same strategy as Spring's own
		// PathMatchingResourcePatternResolver.addAllClassLoaderJarRoots().
		return scanClasspathJarsForSkills(classpathPrefix);
	}

	/**
	 * Scans all classpath JARs for SKILL.md files under the given prefix. Discovers JARs
	 * via {@code ClassLoader.getResources("META-INF/MANIFEST.MF")} — a technique used by
	 * Spring internally when standard classpath resolution is insufficient.
	 */
	private static List<Skill> scanClasspathJarsForSkills(String classpathPrefix) throws IOException {
		String prefix = classpathPrefix.endsWith("/") ? classpathPrefix : classpathPrefix + "/";

		ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
		if (classLoader == null) {
			classLoader = Skills.class.getClassLoader();
		}

		List<Skill> skills = new ArrayList<>();

		Enumeration<URL> manifests = classLoader.getResources("META-INF/MANIFEST.MF");
		while (manifests.hasMoreElements()) {
			URL manifestUrl = manifests.nextElement();
			if (!"jar".equals(manifestUrl.getProtocol())) {
				continue;
			}

			JarURLConnection jarConnection = (JarURLConnection) manifestUrl.openConnection();
			skills.addAll(scanJarForSkills(jarConnection.getJarFile(), prefix));
		}

		return skills;
	}

	/**
	 * Scans a single JAR file for SKILL.md entries under the given prefix.
	 * @param jarFile the JAR to scan
	 * @param entryPrefix the entry prefix to match (must end with '/')
	 * @return a list of Skill objects found in this JAR
	 */
	private static List<Skill> scanJarForSkills(JarFile jarFile, String entryPrefix) throws IOException {
		List<Skill> skills = new ArrayList<>();
		Enumeration<JarEntry> entries = jarFile.entries();

		while (entries.hasMoreElements()) {
			JarEntry entry = entries.nextElement();
			String entryName = entry.getName();

			if (!entry.isDirectory() && entryName.startsWith(entryPrefix)
					&& entryName.endsWith("/SKILL.md")) {
				try (InputStream is = jarFile.getInputStream(entry)) {
					skills.add(parseSkill(is, entryName));
				}
			}
		}
		return skills;
	}

	/**
	 * Parses a SKILL.md file from an input stream into a {@link Skill}.
	 * @param is the input stream containing the SKILL.md markdown content
	 * @param entryPath the JAR entry path — used to derive the base directory
	 */
	private static Skill parseSkill(InputStream is, String entryPath) throws IOException {
		String markdown = new String(is.readAllBytes(), StandardCharsets.UTF_8);
		MarkdownParser parser = new MarkdownParser(markdown);
		String basePath = entryPath.endsWith("/SKILL.md")
				? entryPath.substring(0, entryPath.lastIndexOf('/'))
				: entryPath;
		return new Skill(basePath, parser.getFrontMatter(), parser.getContent());
	}

	/**
	 * Derives the JAR-internal base path from a resource URL by stripping the SKILL.md
	 * filename and the {@code jar:file:...!/} prefix.
	 */
	private static String deriveBasePathFromUrl(URL skillUrl) {
		String urlStr = skillUrl.toString();
		String basePath = urlStr.substring(0, urlStr.lastIndexOf("/SKILL.md"));
		if (basePath.contains("!/")) {
			basePath = basePath.substring(basePath.indexOf("!/") + 2);
		}
		return basePath;
	}

}
