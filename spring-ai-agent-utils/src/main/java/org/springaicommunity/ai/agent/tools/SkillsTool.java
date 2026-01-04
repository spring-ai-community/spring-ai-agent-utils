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
package org.springaicommunity.ai.agent.tools;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springaicommunity.ai.agent.utils.SkillsUtils;
import org.springaicommunity.ai.agent.utils.SkillsUtils.Skill;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.util.Assert;

/**
 * @author Christian Tzolov
 */

public class SkillsTool {

	private static String buildDescription(String availableSkillsXml) {
		return """
				Execute a skill within the main conversation

				<skills_instructions>
				When users ask you to perform tasks, check if any of the available skills below can help complete the task more effectively. Skills provide specialized capabilities and domain knowledge.

				How to use skills:
				- Invoke skills using this tool with the skill name only (no arguments)
				- When you invoke a skill, you will see <command-message>The "{name}" skill is loading</command-message>
				- The skill's prompt will expand and provide detailed instructions on how to complete the task
				- Examples:
				  - `command: "pdf"` - invoke the pdf skill
				  - `command: "xlsx"` - invoke the xlsx skill
				  - `command: "ms-office-suite:pdf"` - invoke using fully qualified name

				Important:
				- Only use skills listed in <available_skills> below
				- Do not invoke a skill that is already running
				- Do not use this tool for built-in CLI commands (like /help, /clear, etc.)
				</skills_instructions>

				<available_skills>
				%s
				</available_skills>
				"""
			.formatted(availableSkillsXml);
	}

	public static record SkillsInput(
			@ToolParam(description = "The skill name (no arguments). E.g., \"pdf\" or \"xlsx\"") String command) {
	}

	public static class SkillsFunction implements Function<SkillsInput, String> {

		private Map<String, Skill> skillsMap;

		public SkillsFunction(Map<String, Skill> skillsMap) {
			this.skillsMap = skillsMap;
		}

		@Override
		public String apply(SkillsInput input) {
			Skill skill = this.skillsMap.get(input.command());

			if (skill != null) {
				var skillBaseDirectory = skill.getPath().getParent().toString();
				return "Base directory for this skill: %s\n\n%s".formatted(skillBaseDirectory, skill.getContent());
			}

			return "Skill not found: " + input.command();
		}

	}

	private static String skillToXml(Skill skill) {
		String frontMatterXml = skill.getFrontMatter()
			.entrySet()
			.stream()
			.map(e -> "  <%s>%s</%s>".formatted(e.getKey(), e.getValue(), e.getKey()))
			.collect(Collectors.joining("\n"));

		return "<skill>\n%s\n</skill>".formatted(frontMatterXml);
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private Map<String, Skill> skillsMap = new HashMap<>();

		private Builder() {

		}

		public Builder skillsMap(Map<String, Skill> skillsMap) {
			Assert.notNull(skillsMap, "skills map can't be null");
			this.skillsMap.putAll(skillsMap);
			return this;
		}

		public Builder skillsRootDirectory(String skillsRootDirectory) {
			try {
				this.skillsMap.putAll(SkillsUtils.skillsMap(skillsRootDirectory));
			}
			catch (IOException ex) {
				throw new RuntimeException("Failed to load skills from directory: " + skillsRootDirectory, ex);
			}

			return this;
		}

		public Builder skillsRootDirectories(List<String> skillsRootDirectories) {
			try {
				this.skillsMap.putAll(SkillsUtils.skillsMap(skillsRootDirectories));
			}
			catch (IOException ex) {
				throw new RuntimeException("Failed to load skills from directories: " + skillsRootDirectories, ex);
			}

			return this;
		}

		public ToolCallback build() {
			Assert.notEmpty(this.skillsMap, "At least one skill must be configured");

			String skillsXml = skillsMap.values()
				.stream()
				.map(SkillsTool::skillToXml)
				.collect(Collectors.joining("\n"));

			return FunctionToolCallback.builder("Skill", new SkillsFunction(skillsMap))
				.description(buildDescription(skillsXml))
				.inputType(SkillsInput.class)
				.build();
		}

	}

}
