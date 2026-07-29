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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springaicommunity.agent.common.skill.SkillDescriptor;
import org.springaicommunity.agent.common.skill.SkillProvider;
import org.springaicommunity.agent.tools.SkillsTool.Skill;
import org.springaicommunity.agent.utils.Skills;

import org.springframework.core.io.Resource;

/**
 * A {@link SkillProvider} implementation that loads skills from the filesystem, classpath
 * resources, or JAR files using the existing {@link Skills} utility.
 *
 * @author Daniel Volovik
 */
public class FileSystemSkillProvider implements SkillProvider {

	private final Map<String, Skill> skillsMap;

	private FileSystemSkillProvider(List<Skill> skills) {
		this.skillsMap = new HashMap<>();
		for (Skill skill : skills) {
			this.skillsMap.put(skill.name(), skill);
		}
	}

	/**
	 * Creates a provider from pre-loaded {@link Skill} objects.
	 * @param skills the skills to serve
	 * @return a new provider instance
	 */
	public static FileSystemSkillProvider fromSkills(List<Skill> skills) {
		return new FileSystemSkillProvider(skills);
	}

	/**
	 * Creates a provider that loads skills from a filesystem directory.
	 * @param directory the root directory to scan for SKILL.md files
	 * @return a new provider instance
	 */
	public static FileSystemSkillProvider fromDirectory(String directory) {
		return new FileSystemSkillProvider(Skills.loadDirectory(directory));
	}

	/**
	 * Creates a provider that loads skills from Spring {@link Resource} references.
	 * @param resources the resources to load skills from
	 * @return a new provider instance
	 */
	public static FileSystemSkillProvider fromResources(List<Resource> resources) {
		return new FileSystemSkillProvider(Skills.loadResources(resources));
	}

	/**
	 * Creates a provider that loads skills from a single Spring {@link Resource}.
	 * @param resource the resource to load skills from
	 * @return a new provider instance
	 */
	public static FileSystemSkillProvider fromResource(Resource resource) {
		return new FileSystemSkillProvider(Skills.loadResource(resource));
	}

	@Override
	public List<SkillDescriptor> getSkillDescriptors() {
		return this.skillsMap.values()
			.stream()
			.map(skill -> (SkillDescriptor) new FileSystemSkillDescriptor(skill.name(), skill.frontMatter()))
			.toList();
	}

	@Override
	public String getSkillContent(String skillName) {
		Skill skill = this.skillsMap.get(skillName);
		if (skill == null) {
			return null;
		}
		return "Base directory for this skill: %s\n\n%s".formatted(skill.basePath(), skill.content());
	}

	private record FileSystemSkillDescriptor(String name, Map<String, Object> metadata)
			implements SkillDescriptor {

		@Override
		public String getName() {
			return this.name;
		}

		@Override
		public Map<String, Object> getMetadata() {
			return this.metadata;
		}

	}

}