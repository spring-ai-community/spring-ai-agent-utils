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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springaicommunity.agent.common.workspace.Workspace;
import org.springaicommunity.agent.utils.Skills;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;

/**
 * Registers every loaded skill as its own {@link ToolCallback}, named after the skill.
 * Skill selection is therefore plain tool selection: the model calls the skill's tool
 * (without arguments) instead of picking a name out of a catalog embedded in a single
 * tool description.
 *
 * @author Christian Tzolov
 * @author kezhenxu94
 */
public class SkillsTool {

	/**
	 * Characters that the model providers do not accept in a tool name. Scoped skill
	 * names (e.g. {@code project-a:pdf}) remain valid skill names - the illegal
	 * characters are replaced when deriving the tool name.
	 */
	private static final Pattern ILLEGAL_TOOL_NAME_CHARS = Pattern.compile("[^a-zA-Z0-9_-]");

	/**
	 * Maximum tool name length accepted by the model providers.
	 */
	private static final int MAX_TOOL_NAME_LENGTH = 64;

	private static final String TOOL_DESCRIPTION_TEMPLATE = """
			%s

			Returns the instructions to follow for this skill, preceded by a "Base directory for this skill: <path>" line - use that path to read the skill's other files or run its scripts.
			""";

	/**
	 * Returns the instructions of a single skill. Takes no input.
	 */
	public static class SkillFunction implements Supplier<String> {

		private final Skill skill;

		private final Workspace workspace;

		public SkillFunction(Skill skill) {
			this(skill, null);
		}

		public SkillFunction(Skill skill, Workspace workspace) {
			this.skill = skill;
			this.workspace = workspace;
		}

		@Override
		public String get() {
			String basePath = (this.workspace != null) ? this.workspace.display(this.skill.basePath())
					: this.skill.basePath();
			return "Base directory for this skill: %s\n\n%s".formatted(basePath, this.skill.content());
		}

	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private List<Skill> skills = new ArrayList<>();

		private String toolDescriptionTemplate = TOOL_DESCRIPTION_TEMPLATE;

		private Workspace workspace;

		protected Builder() {

		}

		/**
		 * @param template description template applied to every skill tool. Must contain
		 * a single {@code %s} placeholder, replaced by the skill's rendered front-matter.
		 */
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
		 * @return one {@link ToolCallback} per configured skill, named after the skill.
		 */
		public ToolCallbackProvider build() {
			Assert.notEmpty(this.skills, "At least one skill must be configured");

			Map<String, Skill> skillsByToolName = new LinkedHashMap<>();

			for (Skill skill : this.skills) {
				String toolName = skill.toolName();

				Skill previous = skillsByToolName.put(toolName, skill);

				if (previous != null) {
					throw new IllegalArgumentException(
							"Duplicate skill tool name '%s' derived from skill '%s' in '%s' and skill '%s' in '%s'. Skill names must be unique."
								.formatted(toolName, previous.name(), previous.basePath(), skill.name(),
										skill.basePath()));
				}
			}

			return ToolCallbackProvider.from(skillsByToolName.entrySet()
				.stream()
				.map(e -> this.toToolCallback(e.getKey(), e.getValue()))
				.toList());
		}

		private ToolCallback toToolCallback(String toolName, Skill skill) {
			return FunctionToolCallback.builder(toolName, new SkillFunction(skill, this.workspace))
				.description(this.toolDescriptionTemplate.formatted(skill.toDescription()))
				.build();
		}

	}

	/**
	 * Represents a SKILL.md file with its location and parsed content.
	 */
	public static record Skill(String basePath, Map<String, Object> frontMatter, String content) {

		public String name() {
			Object name = this.frontMatter().get("name");
			Assert.notNull(name, "Missing required 'name' front-matter field in skill: " + this.basePath());
			return name.toString();
		}

		/**
		 * The name of the tool registered for this skill. Scoped skill names (e.g.
		 * {@code project-a:pdf}) are supported: characters that the model providers do
		 * not allow in a tool name are replaced by {@code _} (e.g.
		 * {@code project-a_pdf}).
		 */
		public String toolName() {
			String name = this.name();
			String toolName = ILLEGAL_TOOL_NAME_CHARS.matcher(name).replaceAll("_");

			Assert.isTrue(toolName.length() <= MAX_TOOL_NAME_LENGTH,
					"Skill name '%s' in '%s' is too long: a skill name is used as the tool name and must be at most %d characters."
						.formatted(name, this.basePath(), MAX_TOOL_NAME_LENGTH));

			return toolName;
		}

		/**
		 * Renders the skill front-matter as the plain-text description of the skill tool:
		 * the {@code description} field followed by any remaining fields (e.g.
		 * {@code allowed-tools}, {@code model}) as {@code key: value} lines.
		 */
		public String toDescription() {
			String description = String.valueOf(this.frontMatter().getOrDefault("description", ""));

			String remainingFrontMatter = this.frontMatter()
				.entrySet()
				.stream()
				.filter(e -> !"name".equals(e.getKey()) && !"description".equals(e.getKey()))
				.map(e -> "%s: %s".formatted(e.getKey(), e.getValue()))
				.collect(Collectors.joining("\n"));

			return remainingFrontMatter.isEmpty() ? description : description + "\n\n" + remainingFrontMatter;
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

}
