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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.springaicommunity.agent.tools.SkillsTool.Skill;

import org.springframework.core.io.Resource;

/**
 * @author Christian Tzolov
 */

public class Skills {

	/**
	 * Loads skills from the given resources, which can be directories containing
	 * SKILL.md files.
	 * @param skillsResources the resources to load skills from
	 * @return a list of Skill objects containing the path, front-matter, and content of
	 * each SKILL.md file found in the resources
	 */
	public static List<Skill> loadResources(List<Resource> skillsResources) {
		List<Skill> skills = new ArrayList<>();
		for (Resource skillsResource : skillsResources) {
			skills.addAll(loadResource(skillsResource));
		}
		return skills;
	}

	/**
	 * Loads skills from the given resource, which can be a directory containing SKILL.md
	 * files.
	 * @param skillsResource the resource to load skills from
	 * @return a list of Skill objects containing the path, front-matter, and content of
	 * each SKILL.md file found in the resource
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
				throw new RuntimeException("Failed to load skills from directory: " + skillsResource, ex);
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
	 * @return a list of Skill objects containing the path, front-matter, and content of
	 * each SKILL.md file
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
						skills.add(new Skill(path, parser.getFrontMatter(), parser.getContent()));
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

}
