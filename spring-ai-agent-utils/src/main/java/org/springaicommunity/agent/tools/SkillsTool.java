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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.agent.common.workspace.Workspace;
import org.springaicommunity.agent.utils.Skills;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;

/**
 * @author Christian Tzolov
 */
public class SkillsTool {

	private static final Logger logger = LoggerFactory.getLogger(SkillsTool.class);

	private static final String TOOL_DESCRIPTION_TEMPLATE = """
			Execute a skill within the main conversation

			<skills_instructions>
			When users ask you to perform tasks, check if any of the available skills below can help complete the task more effectively. Skills provide specialized capabilities and domain knowledge.

			How to use skills:
			- Invoke skills using this tool with the skill name only (no arguments)
			- When you invoke a skill, you will see <command-message>The "{name}" skill is loading</command-message>
			- The skill's prompt will expand and provide detailed instructions on how to complete the task

			NOTE: Response always starts start with the base directory of the skill execution environment. You can use this to retrieve additional files of call shell commands.
			Skill description follows after the base directory line.

			Important:
			- Only use skills listed in <available_skills> below
			- Do not invoke a skill that is already running
			</skills_instructions>

			<available_skills>
			%s
			</available_skills>
			""";

	public static record SkillsInput(
			@ToolParam(description = "The skill name (no arguments). E.g., \"pdf\" or \"xlsx\"") String command) {
	}

	public static class SkillsFunction implements Function<SkillsInput, String> {

		private final Map<String, Skill> skillsMap;

		private final Workspace workspace;

		public SkillsFunction(Map<String, Skill> skillsMap) {
			this(skillsMap, null);
		}

		public SkillsFunction(Map<String, Skill> skillsMap, Workspace workspace) {
			this.skillsMap = skillsMap;
			this.workspace = workspace;
		}

		@Override
		public String apply(SkillsInput input) {
			return apply(input, null, List.of());
		}

		String apply(SkillsInput input, ToolContext toolContext, List<SkillLoadedListener> listeners) {
			Skill skill = this.skillsMap.get(input.command());

			if (skill != null) {
				String result;
				if (skill.basePath() == null || skill.basePath().isEmpty()) {
					result = skill.content();
				}
				else {
					String basePath = (this.workspace != null) ? this.workspace.display(skill.basePath())
							: skill.basePath();
					result = "Base directory for this skill: %s\n\n%s".formatted(basePath, skill.content());
				}
				for (SkillLoadedListener listener : listeners) {
					listener.onSkillLoaded(skill, toolContext);
				}
				return result;
			}

			return "Skill not found: " + input.command();
		}

	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private List<Skill> skills = new ArrayList<>();

		private List<SkillLoadedListener> skillLoadedListeners = new ArrayList<>();

		private String toolDescriptionTemplate = TOOL_DESCRIPTION_TEMPLATE;

		private Workspace workspace;

		protected Builder() {

		}

		public Builder toolDescriptionTemplate(String template) {
			this.toolDescriptionTemplate = template;
			return this;
		}

		/**
		 * Maps skill base-directory paths through {@link Workspace#display(String)}
		 * before they reach the model, so the announced base directory is valid in the
		 * environment where the agent's shell and file tools execute (e.g. an in-sandbox
		 * path instead of the host path the skills were loaded from).
		 * @param workspace the workspace whose display mapping to apply
		 * @return this builder
		 */
		public Builder workspace(Workspace workspace) {
			this.workspace = workspace;
			return this;
		}

		public Builder addSkillsResources(List<Resource> skillsResources) {
			this.skills.addAll(Skills.loadResources(skillsResources));
			return this;
		}

		public Builder addSkillsResource(Resource skillsResource) {
			this.skills.addAll(Skills.loadResource(skillsResource));
			return this;
		}

		public Builder addSkillsDirectory(String skillsRootDirectory) {
			this.addSkillsDirectories(List.of(skillsRootDirectory));
			return this;
		}

		public Builder addSkillsDirectories(List<String> skillsRootDirectories) {
			for (String skillsRootDirectory : skillsRootDirectories) {
				this.skills.addAll(Skills.loadDirectory(skillsRootDirectory));
			}
			return this;
		}

		/**
		 * Registers a skill programmatically, bypassing filesystem and classpath
		 * resource resolution. Useful for skills that are not packaged as SKILL.md files
		 * (for example generated at runtime or loaded from a database or configuration
		 * service). Programmatic skills have no base directory, so the skill content is
		 * returned to the model as is.
		 * @param name the skill name (the invocation key used by the model)
		 * @param description a short description of the skill
		 * @param content the skill instructions
		 * @return this builder
		 */
		public Builder addSkill(String name, String description, String content) {
			Assert.hasText(name, "name must not be empty");
			Assert.hasText(description, "description must not be empty");
			Assert.hasText(content, "content must not be empty");
			this.skills.add(new Skill("", Map.of("name", name, "description", description), content));
			return this;
		}

		/**
		 * Registers a listener to observe when skills are loaded and prepared for the
		 * model. Multiple listeners may be registered and are invoked in registration
		 * order.
		 * @param listener the listener to observe skill activation
		 * @return this builder
		 */
		public Builder onSkillLoaded(SkillLoadedListener listener) {
			Assert.notNull(listener, "listener must not be null");
			this.skillLoadedListeners.add(listener);
			return this;
		}

		public ToolCallback build() {
			Assert.notEmpty(this.skills, "At least one skill must be configured");

			List<Skill> uniqueSkills = uniqueByName(this.skills);

			String skillsXml = uniqueSkills.stream().map(s -> s.toXml()).collect(Collectors.joining("\n"));

			SkillsFunction delegate = new SkillsFunction(toSkillsMap(uniqueSkills), this.workspace);
			List<SkillLoadedListener> listenersSnapshot = List.copyOf(this.skillLoadedListeners);

			BiFunction<SkillsInput, ToolContext, String> adapter = (input, context) -> delegate.apply(input,
					context, listenersSnapshot);

			return FunctionToolCallback.builder("Skill", adapter)
				.description(this.toolDescriptionTemplate.formatted(skillsXml))
				.inputType(SkillsInput.class)
				.build();
		}

	}

	/**
	 * Represents a SKILL.md file with its location and parsed content.
	 */
	public static record Skill(String basePath, Map<String, Object> frontMatter, String content) {

		public String name() {
			return this.frontMatter().get("name").toString();
		}

		public String toXml() {
			String frontMatterXml = this.frontMatter()
				.entrySet()
				.stream()
				.map(e -> "  <%s>%s</%s>".formatted(e.getKey(), e.getValue(), e.getKey()))
				.collect(Collectors.joining("\n"));

			return "<skill>\n%s\n</skill>".formatted(frontMatterXml);
		}

	}

	/**
	 * Keeps the first skill registered under each name, in registration order, and logs
	 * a warning for each later duplicate. Duplicates are easy to get when a classpath
	 * location is aggregated across several JARs and directories; listing both would
	 * advertise a skill to the model that can never be invoked.
	 */
	private static List<Skill> uniqueByName(List<Skill> skills) {
		Map<String, Skill> unique = new LinkedHashMap<>();
		for (Skill skill : skills) {
			Skill existing = unique.putIfAbsent(skill.name(), skill);
			if (existing != null) {
				logger.warn("Ignoring duplicate skill '{}' from '{}': already registered from '{}'", skill.name(),
						skill.basePath(), existing.basePath());
			}
		}
		return new ArrayList<>(unique.values());
	}

	private static Map<String, Skill> toSkillsMap(List<Skill> skills) {

		Map<String, Skill> skillsMap = new HashMap<>();

		for (Skill skill : skills) {
			skillsMap.put(skill.name(), skill);
		}

		return skillsMap;
	}

}
