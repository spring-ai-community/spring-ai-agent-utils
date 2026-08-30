package org.springaicommunity.agent.subagent.a2a;

import io.a2a.A2A;
import io.a2a.spec.AgentCard;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.MockedStatic;
import org.springaicommunity.agent.common.task.subagent.SubagentReference;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

/**
 * Tests for {@link A2ASubagentResolver}.
 *
 * @author Caio Henrique Silva
 */
class A2ASubagentResolverTest {

    @ParameterizedTest
    @CsvSource({
            "http://localhost:8080,              /.well-known/agent-card.json, http://localhost:8080/.well-known/agent-card.json",
            "http://localhost:8080/dominio,      /.well-known/agent-card.json, http://localhost:8080/dominio/.well-known/agent-card.json",
            "http://localhost:8080/dominio/,     /.well-known/agent-card.json, http://localhost:8080/dominio/.well-known/agent-card.json",
            "https://api.example.com/v1,         /.well-known/agent-card.json, https://api.example.com/v1/.well-known/agent-card.json",
            "http://localhost:8080,              /custom/agent-card.json,      http://localhost:8080/custom/agent-card.json",
            "http://localhost:8080/dominio,      /custom/agent-card.json,      http://localhost:8080/dominio/custom/agent-card.json",
            "http://localhost:8080,              custom/agent.json,            http://localhost:8080/custom/agent.json",
            "http://localhost:8080/dominio,      custom/agent.json,            http://localhost:8080/dominio/custom/agent.json"
    })
    @DisplayName("Should build the correct well-known URL for various agent base URLs and card paths")
    void resolve_shouldCallA2AWithCorrectFullUri(String baseUrl, String agentCardPath, String expectedFullUri) {
        A2ASubagentResolver resolver = new A2ASubagentResolver(agentCardPath);
        SubagentReference ref = new SubagentReference(baseUrl, A2ASubagentDefinition.KIND);

        try (MockedStatic<A2A> a2aMock = mockStatic(A2A.class)) {
            a2aMock.when(() -> A2A.getAgentCard(anyString()))
                    .thenReturn(mock(AgentCard.class));

            resolver.resolve(ref);

            a2aMock.verify(() -> A2A.getAgentCard(expectedFullUri));
        }
    }
}