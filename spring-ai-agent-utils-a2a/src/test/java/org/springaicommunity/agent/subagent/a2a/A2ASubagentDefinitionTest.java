package org.springaicommunity.agent.subagent.a2a;

import io.a2a.spec.AgentCapabilities;
import io.a2a.spec.AgentCard;
import io.a2a.spec.AgentSkill;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springaicommunity.agent.common.task.subagent.SubagentReference;

import java.util.List;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

/**
 * Tests for {@link A2ASubagentDefinition}.
 *
 * @author Caio Henrique Silva
 */
@DisplayName("A2ASubagentDefinition Tests")
class A2ASubagentDefinitionTest {

    private static final String CARD_DESC = "Main agent description";
    private static final SubagentReference REF = new SubagentReference("ref1", "A2A");

    @Test
    @DisplayName("Should return only the card description when there are no skills")
    void shouldReturnOnlyCardDescriptionWhenNoSkills() {
        AgentCard card = baseCardBuilder().skills(List.of()).build();
        A2ASubagentDefinition def = new A2ASubagentDefinition(REF, card);
        assertThat(def.getDescription()).isEqualTo(CARD_DESC);
    }

    @Test
    @DisplayName("Should include correctly formatted skills")
    void shouldIncludeFormattedSkills() {
        AgentSkill skill1 = new AgentSkill.Builder()
                .id("s1").name("Skill One").description("Does something").tags(List.of()).build();
        AgentSkill skill2 = new AgentSkill.Builder()
                .id("s2").name("Skill Two").description("Does another thing").tags(List.of()).build();

        AgentCard card = baseCardBuilder().skills(List.of(skill1, skill2)).build();
        A2ASubagentDefinition a2ASubagentDefinition = new A2ASubagentDefinition(REF, card);

        String desc = a2ASubagentDefinition.getDescription();
        assertThat(desc).contains(CARD_DESC, "Capabilities:", "- Skill One: Does something",
                "- Skill Two: Does another thing");
    }

    @Test
    @DisplayName("Should include examples when present")
    void shouldIncludeExamples() {
        AgentSkill skill = new AgentSkill.Builder()
                .id("s1").name("Calculator").description("Performs math").tags(List.of())
                .examples(List.of("1+1=2", "2*3=6"))
                .build();

        AgentCard card = baseCardBuilder().skills(List.of(skill)).build();
        A2ASubagentDefinition a2ASubagentDefinition = new A2ASubagentDefinition(REF, card);

        String desc = a2ASubagentDefinition.getDescription();
        assertThat(desc).contains("  - Example: 1+1=2", "  - Example: 2*3=6");
    }

    @Test
    @DisplayName("Should handle null examples (without adding any)")
    void shouldHandleNullExamples() {
        AgentSkill skill = new AgentSkill.Builder()
                .id("s1").name("Skill").description("desc").tags(List.of())
                .examples(null)
                .build();

        AgentCard card = baseCardBuilder().skills(List.of(skill)).build();
        A2ASubagentDefinition a2ASubagentDefinition = new A2ASubagentDefinition(REF, card);

        assertThat(a2ASubagentDefinition.getDescription()).doesNotContain("Example:");
    }

    private static AgentCard.Builder baseCardBuilder() {
        return new AgentCard.Builder()
                .name("TestAgent")
                .description(CARD_DESC)
                .url("https://example.com/agent")
                .version("1.0.0")
                .capabilities(new AgentCapabilities(true, true,
                        true, List.of()))
                .defaultInputModes(List.of("text"))
                .defaultOutputModes(List.of("text"));
    }
}