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
package org.springaicommunity.agent.aot;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springaicommunity.agent.tools.SkillsTool;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link AgentUtilsRuntimeHints}.
 *
 * @author Christian Tzolov
 */
class AgentUtilsRuntimeHintsTests {

	private RuntimeHints hints;

	@BeforeEach
	void setUp() {
		this.hints = new RuntimeHints();
		new AgentUtilsRuntimeHints().registerHints(this.hints, getClass().getClassLoader());
	}

	@Test
	void builtInAgentResourcesAreRegistered() {
		assertThat(RuntimeHintsPredicates.resource().forResource("agent/GENERAL_PURPOSE_SUBAGENT.md"))
			.accepts(this.hints);
		assertThat(RuntimeHintsPredicates.resource().forResource("agent/BASH_SUBAGENT.md"))
			.accepts(this.hints);
		assertThat(RuntimeHintsPredicates.resource().forResource("agent/EXPLORE_SUBAGENT.md"))
			.accepts(this.hints);
		assertThat(RuntimeHintsPredicates.resource().forResource("agent/PLAN_SUBAGENT.md"))
			.accepts(this.hints);
	}

	@Test
	void builtInPromptResourcesAreRegistered() {
		assertThat(RuntimeHintsPredicates.resource().forResource("prompt/AUTO_MEMORY_TOOLS_SYSTEM_PROMPT.md"))
			.accepts(this.hints);
	}

	@Test
	void userSkillResourcePatternIsRegistered() {
		assertThat(RuntimeHintsPredicates.resource().forResource("META-INF/skills/my-skill/SKILL.md"))
			.accepts(this.hints);
		assertThat(RuntimeHintsPredicates.resource().forResource("META-INF/skills/nested/dir/SKILL.md"))
			.accepts(this.hints);
	}

	@Test
	void skillsInputReflectionIsRegistered() {
		assertThat(RuntimeHintsPredicates.reflection()
			.onType(SkillsTool.SkillsInput.class)
			.withMemberCategories(MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS, MemberCategory.INVOKE_PUBLIC_METHODS))
			.accepts(this.hints);
	}

	@Test
	void skillsFunctionReflectionIsRegistered() {
		assertThat(RuntimeHintsPredicates.reflection()
			.onType(SkillsTool.SkillsFunction.class)
			.withMemberCategories(MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS, MemberCategory.INVOKE_PUBLIC_METHODS))
			.accepts(this.hints);
	}

}
