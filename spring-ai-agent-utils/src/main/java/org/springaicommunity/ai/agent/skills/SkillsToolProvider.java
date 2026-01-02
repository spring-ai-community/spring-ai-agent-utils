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
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springaicommunity.ai.agent.skills.SkillsUtils.Skill;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.tool.function.FunctionToolCallback;

/**
 * @author Christian Tzolov
 */

public class SkillsToolProvider {

	private static String DESCRIPTION = """
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

			""";

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

	public static ToolCallback create(String skillsDirectory) {
		Map<String, Skill> skillsMap;
		try {
			skillsMap = SkillsUtils.skillsMap(skillsDirectory);
		}
		catch (IOException ex) {
			throw new RuntimeException("Failed to load skills from directory: " + skillsDirectory, ex);
		}
		return create(skillsMap);
	}

	public static ToolCallback create(Map<String, Skill> skillsMap) {

		var skillsXml = skillsMap.values()
			.stream()
			.map(skill -> "<skill>" + skillFrontMatterToXML(skill) + "</skill>")
			.collect(Collectors.joining("\n"));

		return FunctionToolCallback.builder("Skill", new SkillsFunction(skillsMap))
			.description(DESCRIPTION + "\n<available_skills>" + skillsXml + "</available_skills>")
			.inputType(SkillsInput.class)
			.build();
	}

	private static String skillFrontMatterToXML(Skill skill) {
		return skill.getFrontMatter()
			.entrySet()
			.stream()
			.map(e -> "<" + e.getKey() + ">" + e.getValue() + "</" + e.getKey() + ">")
			.collect(Collectors.joining("\n"));
	}

}
