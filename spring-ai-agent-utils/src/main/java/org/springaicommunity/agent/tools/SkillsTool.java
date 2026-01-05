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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springaicommunity.agent.utils.MarkdownParser;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.tool.function.FunctionToolCallback;
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
				var skillBaseDirectory = skill.path().getParent().toString();
				return "Base directory for this skill: %s\n\n%s".formatted(skillBaseDirectory, skill.content());
			}

			return "Skill not found: " + input.command();
		}

	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private List<Skill> skills = new ArrayList<>();

		private String toolDescriptionTemplate = TOOL_DESCRIPTION_TEMPLATE;

		private Builder() {

		}

		public Builder toolDescriptionTemplate(String template) {
			this.toolDescriptionTemplate = template;
			return this;
		}

		public Builder addSkillsDirectory(String skillsRootDirectory) {
			this.addSkillsDirectories(List.of(skillsRootDirectory));
			return this;
		}

		public Builder addSkillsDirectories(List<String> skillsRootDirectories) {
			for (String skillsRootDirectory : skillsRootDirectories) {
				try {
					this.skills.addAll(skills(skillsRootDirectory));
				}
				catch (IOException ex) {
					throw new RuntimeException("Failed to load skills from directory: " + skillsRootDirectory, ex);
				}
			}
			return this;
		}

		public ToolCallback build() {
			Assert.notEmpty(this.skills, "At least one skill must be configured");

			String skillsXml = this.skills.stream().map(s -> s.toXml()).collect(Collectors.joining("\n"));

			return FunctionToolCallback.builder("Skill", new SkillsFunction(toSkillsMap(this.skills)))
				.description(this.toolDescriptionTemplate.formatted(skillsXml))
				.inputType(SkillsInput.class)
				.build();
		}

	}

	/**
	 * Represents a SKILL.md file with its location and parsed content.
	 */
	private static record Skill(Path path, Map<String, Object> frontMatter, String content) {

		public String toXml() {
			String frontMatterXml = this.frontMatter()
				.entrySet()
				.stream()
				.map(e -> "  <%s>%s</%s>".formatted(e.getKey(), e.getValue(), e.getKey()))
				.collect(Collectors.joining("\n"));

			return "<skill>\n%s\n</skill>".formatted(frontMatterXml);
		}

	}

	private static Map<String, Skill> toSkillsMap(List<Skill> skills) {

		Map<String, Skill> skillsMap = new HashMap<>();

		for (Skill skillFile : skills) {
			skillsMap.put(skillFile.frontMatter().get("name").toString(), skillFile);
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
	private static List<Skill> skills(String rootDirectory) throws IOException {
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

}
