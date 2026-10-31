package org.springaicommunity.agent.subagent.a2a;

import org.a2aproject.sdk.compat03.spec.AgentCapabilities_v0_3;
import org.a2aproject.sdk.compat03.spec.AgentCard_v0_3;
import org.a2aproject.sdk.compat03.spec.AgentSkill_v0_3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springaicommunity.agent.common.task.subagent.SubagentReference;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link A2ASubagentDefinition_v0_3}.
 *
 * @author a2ASubagentDefinitionV10Test
 */
@DisplayName("A2ASubagentDefinition_v0_3 Tests")
class A2ASubagentDefinition_v0_3Test {

    private static final String CARD_DESC = "Main agent description";

    private static final SubagentReference REF = new SubagentReference("http://localhost:8080/agent",
            A2ASubagentDefinition_v0_3.KIND, A2ASubagentDefinition_v0_3.PROTOCOL_VERSION);

    @Test
    @DisplayName("Should return only the card description when there are no skills")
    void shouldReturnOnlyCardDescriptionWhenNoSkills() {
        AgentCard_v0_3 card = baseCardBuilder().skills(List.of()).build();

        assertThat(new A2ASubagentDefinition_v0_3(REF, card).getDescription()).isEqualTo(CARD_DESC);
    }

    @Test
    @DisplayName("Should list skills indented under the card description")
    void shouldListSkillsIndented() {
        AgentSkill_v0_3 skill1 = new AgentSkill_v0_3.Builder()
                .id("s1")
                .name("Skill One")
                .description("Does something")
                .tags(List.of())
                .build();
        AgentSkill_v0_3 skill2 = new AgentSkill_v0_3.Builder()
                .id("s2")
                .name("Skill Two")
                .description("Does another thing")
                .tags(List.of())
                .build();
        AgentCard_v0_3 card = baseCardBuilder().skills(List.of(skill1, skill2)).build();

        assertThat(new A2ASubagentDefinition_v0_3(REF, card).getDescription()).isEqualTo("""
                Main agent description
                  Can help with:
                  - Skill One: Does something
                  - Skill Two: Does another thing""");
    }

    @Test
    @DisplayName("Should include examples when present")
    void shouldIncludeExamples() {
        AgentSkill_v0_3 skill = new AgentSkill_v0_3.Builder()
                .id("s1")
                .name("Calculator")
                .description("Performs math")
                .tags(List.of())
                .examples(List.of("1+1=2", "2*3=6"))
                .build();
        AgentCard_v0_3 card = baseCardBuilder().skills(List.of(skill)).build();

        assertThat(new A2ASubagentDefinition_v0_3(REF, card).getDescription()).isEqualTo("""
                Main agent description
                  Can help with:
                  - Calculator: Performs math
                    Examples: 1+1=2; 2*3=6""");
    }

    @Test
    @DisplayName("Should handle null examples without adding any")
    void shouldHandleNullExamples() {
        AgentSkill_v0_3 skill = new AgentSkill_v0_3.Builder()
                .id("s1")
                .name("Skill")
                .description("desc")
                .tags(List.of())
                .examples(null)
                .build();
        AgentCard_v0_3 card = baseCardBuilder().skills(List.of(skill)).build();

        assertThat(new A2ASubagentDefinition_v0_3(REF, card).getDescription()).doesNotContain("Examples:");
    }

    @Test
    @DisplayName("Should keep skills nested within the agent's Task tool registration")
    void shouldKeepSkillsNestedInRegistration() {
        AgentSkill_v0_3 skill = new AgentSkill_v0_3.Builder()
                .id("s1")
                .name("Search")
                .description("Find listings")
                .tags(List.of())
                .build();
        AgentCard_v0_3 card = baseCardBuilder().skills(List.of(skill)).build();

        String registration = new A2ASubagentDefinition_v0_3(REF, card).toSubagentRegistrations();

        // Only the first line starts at column 0, so skills cannot be mistaken for other
        // agents in the Task tool's agent list
        assertThat(registration.lines().skip(1)).allMatch(line -> line.startsWith("  "));
        // Not labelled "Skills", which would collide with Agent Skills of the Skill tool
        assertThat(registration).doesNotContainIgnoringCase("skills");
    }

    private static AgentCard_v0_3.Builder baseCardBuilder() {
        return new AgentCard_v0_3.Builder()
                .name("TestAgent")
                .description(CARD_DESC)
                .url("https://example.com/agent")
                .version("1.0.0")
                .protocolVersion("0.3")
                .preferredTransport("JSONRPC")
                .capabilities(new AgentCapabilities_v0_3.Builder().build())
                .defaultInputModes(List.of("text"))
                .defaultOutputModes(List.of("text"));
    }
}