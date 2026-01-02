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
package org.springaicommunity.ai.agent.skills;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * @author Christian Tzolov
 */

public class SkillsUtils {

	/**
	 * Represents a SKILL.md file with its location and parsed content.
	 */
	public static class Skill {

		private final Path path;

		private final Map<String, String> frontMatter;

		private final String content;

		public Skill(Path path, Map<String, String> frontMatter, String content) {
			this.path = path;
			this.frontMatter = frontMatter;
			this.content = content;
		}

		public Path getPath() {
			return this.path;
		}

		public Map<String, String> getFrontMatter() {
			return this.frontMatter;
		}

		public String getContent() {
			return this.content;
		}

		public String getFrontMatterValue(String key) {
			return this.frontMatter.get(key);
		}

		@Override
		public String toString() {
			return "Skill{path=" + this.path + ", frontMatter=" + this.frontMatter + ", contentLength="
					+ (this.content != null ? this.content.length() : 0) + "}";
		}

	}

	public static Map<String, Skill> skillsMap(String rootDirectory) throws IOException {
		List<Skill> skillFiles = skillsList(rootDirectory);
		Map<String, Skill> skillsMap = new HashMap<>();

		for (Skill skillFile : skillFiles) {
			skillsMap.put(skillFile.getFrontMatterValue("name"), skillFile);
		}

		return skillsMap;
	}

	/**
	 * Recursively finds all SKILL.md files in the given root directory and returns their
	 * parsed contents.
	 * @param rootDirectory the root directory to search for SKILL.md files
	 * @return a list of SkillFile objects containing the path, front-matter, and content
	 * of each SKILL.md file
	 * @throws IOException if an I/O error occurs while reading the directory or files
	 */
	public static List<Skill> skillsList(String rootDirectory) throws IOException {
		Path rootPath = Paths.get(rootDirectory);

		if (!Files.exists(rootPath)) {
			throw new IOException("Root directory does not exist: " + rootDirectory);
		}

		if (!Files.isDirectory(rootPath)) {
			throw new IOException("Path is not a directory: " + rootDirectory);
		}

		List<Skill> skillFiles = new ArrayList<>();

		try (Stream<Path> paths = Files.walk(rootPath)) {
			paths.filter(Files::isRegularFile)
				.filter(path -> path.getFileName().toString().equals("SKILL.md"))
				.forEach(path -> {
					try {
						String markdown = Files.readString(path, StandardCharsets.UTF_8);
						MarkdownParser parser = new MarkdownParser(markdown);
						skillFiles.add(new Skill(path, parser.getFrontMatter(), parser.getContent()));
					}
					catch (IOException e) {
						throw new RuntimeException("Failed to read SKILL.md file: " + path, e);
					}
				});
		}

		return skillFiles;
	}

	/**
	 * Recursively finds all SKILL.md files in the given root directory and returns their
	 * parsed contents. This version uses a Path parameter.
	 * @param rootDirectory the root directory path to search for SKILL.md files
	 * @return a list of SkillFile objects containing the path, front-matter, and content
	 * of each SKILL.md file
	 * @throws IOException if an I/O error occurs while reading the directory or files
	 */
	public static List<Skill> skillsList(Path rootDirectory) throws IOException {
		return skillsList(rootDirectory.toString());
	}
}
