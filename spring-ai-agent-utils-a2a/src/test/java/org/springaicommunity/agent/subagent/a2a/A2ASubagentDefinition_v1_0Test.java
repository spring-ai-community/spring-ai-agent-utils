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
package org.springaicommunity.agent.subagent.a2a;

import java.util.List;

import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentSkill;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springaicommunity.agent.common.task.subagent.SubagentReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link A2ASubagentDefinition_v1_0}.
 *
 * @author a2ASubagentDefinitionV10Test
 */
@DisplayName("A2ASubagentDefinition_v1_0 Tests")
class A2ASubagentDefinition_v1_0Test {

	private static final String CARD_DESC = "Main agent description";

	private static final SubagentReference REF = new SubagentReference("http://localhost:8080/agent",
			A2ASubagentDefinition_v1_0.KIND, A2ASubagentDefinition_v1_0.PROTOCOL_VERSION);

	@Test
	@DisplayName("Should return only the card description when there are no skills")
	void shouldReturnOnlyCardDescriptionWhenNoSkills() {
		AgentCard card = baseCardBuilder().skills(List.of()).build();

		assertThat(new A2ASubagentDefinition_v1_0(REF, card).getDescription()).isEqualTo(CARD_DESC);
	}

	@Test
	@DisplayName("Should list skills indented under the card description")
	void shouldListSkillsIndented() {
		AgentSkill skill1 = AgentSkill.builder().id("s1")
			.name("Skill One")
			.description("Does something")
			.tags(List.of())
			.build();
		AgentSkill skill2 = AgentSkill.builder().id("s2")
			.name("Skill Two")
			.description("Does another thing")
			.tags(List.of())
			.build();
		AgentCard card = baseCardBuilder().skills(List.of(skill1, skill2)).build();

		assertThat(new A2ASubagentDefinition_v1_0(REF, card).getDescription()).isEqualTo("""
				Main agent description
				  Can help with:
				  - Skill One: Does something
				  - Skill Two: Does another thing""");
	}

	@Test
	@DisplayName("Should include examples when present")
	void shouldIncludeExamples() {
		AgentSkill skill = AgentSkill.builder().id("s1")
			.name("Calculator")
			.description("Performs math")
			.tags(List.of())
			.examples(List.of("1+1=2", "2*3=6"))
			.build();
		AgentCard card = baseCardBuilder().skills(List.of(skill)).build();

		assertThat(new A2ASubagentDefinition_v1_0(REF, card).getDescription()).isEqualTo("""
				Main agent description
				  Can help with:
				  - Calculator: Performs math
				    Examples: 1+1=2; 2*3=6""");
	}

	@Test
	@DisplayName("Should handle null examples without adding any")
	void shouldHandleNullExamples() {
		AgentSkill skill = AgentSkill.builder().id("s1")
			.name("Skill")
			.description("desc")
			.tags(List.of())
			.examples(null)
			.build();
		AgentCard card = baseCardBuilder().skills(List.of(skill)).build();

		assertThat(new A2ASubagentDefinition_v1_0(REF, card).getDescription()).doesNotContain("Examples:");
	}

	@Test
	@DisplayName("Should keep skills nested within the agent's Task tool registration")
	void shouldKeepSkillsNestedInRegistration() {
		AgentSkill skill = AgentSkill.builder().id("s1")
			.name("Search")
			.description("Find listings")
			.tags(List.of())
			.build();
		AgentCard card = baseCardBuilder().skills(List.of(skill)).build();

		String registration = new A2ASubagentDefinition_v1_0(REF, card).toSubagentRegistrations();

		// Only the first line starts at column 0, so skills cannot be mistaken for other
		// agents in the Task tool's agent list
		assertThat(registration.lines().skip(1)).allMatch(line -> line.startsWith("  "));
		// Not labelled "Skills", which would collide with Agent Skills of the Skill tool
		assertThat(registration).doesNotContainIgnoringCase("skills");
	}

	private static AgentCard.Builder baseCardBuilder() {
		return AgentCard.builder().name("TestAgent")
			.description(CARD_DESC)
			.url("https://example.com/agent")
			.version("1.0.0")
			.capabilities(new AgentCapabilities(true, true, true, List.of()))
			.defaultInputModes(List.of("text"))
			.defaultOutputModes(List.of("text"))
			.supportedInterfaces(List.of());
	}

}
