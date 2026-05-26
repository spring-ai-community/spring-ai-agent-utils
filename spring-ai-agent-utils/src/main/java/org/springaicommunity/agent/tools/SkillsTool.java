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
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springaicommunity.agent.common.skill.SkillDescriptor;
import org.springaicommunity.agent.common.skill.SkillProvider;
import org.springaicommunity.agent.tools.skill.FileSystemSkillProvider;
import org.springaicommunity.agent.utils.Skills;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;

/**
 * @author Christian Tzolov
 */
public class SkillsTool {

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

		private final List<SkillProvider> providers;

		public SkillsFunction(List<SkillProvider> providers) {
			this.providers = providers;
		}

		@Override
		public String apply(SkillsInput input) {
			for (SkillProvider provider : this.providers) {
				String content = provider.getSkillContent(input.command());
				if (content != null) {
					return content;
				}
			}

			return "Skill not found: " + input.command();
		}

	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private List<Skill> skills = new ArrayList<>();

		private List<SkillProvider> providers = new ArrayList<>();

		private String toolDescriptionTemplate = TOOL_DESCRIPTION_TEMPLATE;

		protected Builder() {

		}

		public Builder toolDescriptionTemplate(String template) {
			this.toolDescriptionTemplate = template;
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
		 * Adds a custom {@link SkillProvider} for pluggable skill backends.
		 * @param provider the skill provider to add
		 * @return this builder
		 */
		public Builder addSkillProvider(SkillProvider provider) {
			this.providers.add(provider);
			return this;
		}

		public ToolCallback build() {
			// Wrap any statically-loaded filesystem skills into a provider
			if (!this.skills.isEmpty()) {
				this.providers.add(0, FileSystemSkillProvider.fromSkills(this.skills));
			}

			Assert.notEmpty(this.providers, "At least one skill or skill provider must be configured");

			List<SkillDescriptor> allDescriptors = this.providers.stream()
				.flatMap(p -> p.getSkillDescriptors().stream())
				.toList();

			String skillsXml = allDescriptors.stream()
				.map(SkillDescriptor::toXml)
				.collect(Collectors.joining("\n"));

			return FunctionToolCallback.builder("Skill", new SkillsFunction(this.providers))
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
}
