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
package org.springaicommunity.agent.tools.task.subagent.claude;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.agent.tools.task.subagent.SubagentDefinition;
import org.springaicommunity.agent.tools.task.subagent.SubagentReference;

import org.springframework.util.StringUtils;

/**
 * Claude-specific subagent definition parsed from markdown with YAML frontmatter.
 * Supports model, tools, disallowed tools, skills, and permission mode configuration.
 *
 * @author Christian Tzolov
 */
public class ClaudeSubagentDefinition implements SubagentDefinition {

	private static final Logger logger = LoggerFactory.getLogger(ClaudeSubagentDefinition.class);

	public static final String KIND = "CLAUDE";

	private static final String FRONTMATTER_NAME_KEY = "name";

	private static final String FRONTMATTER_DESCRIPTION_KEY = "description";

	private static final String FRONTMATTER_MODEL_KEY = "model";

	private static final String FRONTMATTER_TOOLS_KEY = "tools";

	private static final String FRONTMATTER_DISALLOWED_TOOLS_KEY = "disallowedTools";

	private static final String FRONTMATTER_SKILLS_KEY = "skills";

	private static final String FRONTMATTER_PERMISSION_MODE_KEY = "permissionMode";

	private final Map<String, Object> frontMatter;

	private final String content;

	private final SubagentReference reference;

	public ClaudeSubagentDefinition(SubagentReference reference, Map<String, Object> frontMatter, String content) {
		this.reference = reference;
		this.frontMatter = frontMatter;
		this.content = content;
	}

	@Override
	public String getName() {
		return this.frontMatter.get(FRONTMATTER_NAME_KEY).toString();
	}

	@Override
	public String getDescription() {
		return this.frontMatter.get(FRONTMATTER_DESCRIPTION_KEY).toString();
	}

	@Override
	public String getKind() {
		return KIND;
	}

	/**
	 * Model override for this agent. Defaults to main model if omitted
	 *
	 * https://platform.claude.com/docs/en/agent-sdk/subagents#agent-definition-configuration
	 * https://code.claude.com/docs/en/sub-agents#supported-frontmatter-fields
	 * @return model name or null
	 */
	public String getModel() {
		Object model = this.frontMatter.get(FRONTMATTER_MODEL_KEY);
		return model != null ? model.toString() : null;
	}

	/**
	 * Optional array of allowed tool names. If omitted, inherits all tools
	 *
	 * https://platform.claude.com/docs/en/agent-sdk/subagents#agent-definition-configuration
	 * https://code.claude.com/docs/en/sub-agents#supported-frontmatter-fields
	 * @return list of allowed tool names
	 */
	public List<String> tools() {
		if (!this.frontMatter.containsKey(FRONTMATTER_TOOLS_KEY)) {
			return List.of();
		}
		String[] toolNames = this.frontMatter.get(FRONTMATTER_TOOLS_KEY).toString().split(",");
		return Stream.of(toolNames).map(tn -> tn.trim()).filter(tn -> StringUtils.hasText(tn)).toList();
	}

	/**
	 * Tools to deny, removed from inherited or specified list
	 * @return list of disallowed tool names from the inherited or specified list
	 */
	public List<String> disallowedTools() {
		if (!this.frontMatter.containsKey(FRONTMATTER_DISALLOWED_TOOLS_KEY)) {
			return List.of();
		}
		String[] toolNames = this.frontMatter.get(FRONTMATTER_DISALLOWED_TOOLS_KEY).toString().split(",");
		return Stream.of(toolNames).map(tn -> tn.trim()).filter(tn -> StringUtils.hasText(tn)).toList();
	}

	/**
	 * Skills to load into the subagent’s context at startup. The full skill content is
	 * injected, not just made available for invocation. Subagents don’t inherit skills
	 * from the parent conversation
	 *
	 * https://code.claude.com/docs/en/sub-agents#supported-frontmatter-fields
	 * @return skill names
	 */
	public List<String> skills() {
		if (!this.frontMatter.containsKey(FRONTMATTER_SKILLS_KEY)) {
			return List.of();
		}
		String[] skillNames = this.frontMatter.get(FRONTMATTER_SKILLS_KEY).toString().split(",");
		return Stream.of(skillNames).map(tn -> tn.trim()).filter(tn -> StringUtils.hasText(tn)).toList();
	}

	/**
	 * Skills to load into the subagent’s context at startup. The full skill content is
	 * injected, not just made available for invocation. Subagents don’t inherit skills
	 * from the parent conversation
	 *
	 * https://code.claude.com/docs/en/sub-agents#supported-frontmatter-fields
	 * @return permission mode
	 */
	public String permissionMode() {
		if (!this.frontMatter.containsKey(FRONTMATTER_PERMISSION_MODE_KEY)) {
			return "default";
		}
		return this.frontMatter.get(FRONTMATTER_PERMISSION_MODE_KEY).toString();
	}

	@Override
	public SubagentReference getReference() {
		return this.reference;
	}

	public Map<String, Object> getFrontMatter() {
		return frontMatter;
	}

	public String getContent() {
		return content;
	}

}
