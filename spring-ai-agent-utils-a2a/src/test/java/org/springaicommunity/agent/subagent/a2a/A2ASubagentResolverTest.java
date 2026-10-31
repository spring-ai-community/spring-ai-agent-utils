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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springaicommunity.agent.common.task.subagent.SubagentReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link A2ASubagentResolver} against a local HTTP server, verifying the agent
 * card URL that is actually requested.
 *
 * @author Christian Tzolov
 */
class A2ASubagentResolverTest {

    private static final String AGENT_CARD_V0_3 = """
            {"name": "test-agent", "description": "Test agent", "url": "http://localhost/agent",
             "version": "1.0.0", "protocolVersion": "0.3.0", "preferredTransport": "JSONRPC",
             "capabilities": {}, "defaultInputModes": ["text"], "defaultOutputModes": ["text"],
             "skills": []}
            """;

    private static final String AGENT_CARD_V1_0 = """
            {"name": "test-agent", "description": "Test agent", "version": "1.0.0",
             "supportedInterfaces": [{"url": "http://localhost/agent", "protocolBinding": "JSONRPC", "protocolVersion": "1.0"}],
             "capabilities": {}, "defaultInputModes": ["text"], "defaultOutputModes": ["text"],
             "skills": []}
            """;

    private final List<String> requestedPaths = new CopyOnWriteArrayList<>();

    private HttpServer server;

    private String rootUrl;

    private volatile String currentCard;

    @BeforeEach
    void startServer() throws IOException {
        this.server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        this.server.createContext("/", exchange -> {
            this.requestedPaths.add(exchange.getRequestURI().getPath());
            byte[] body = this.currentCard.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        this.server.start();
        this.rootUrl = "http://localhost:" + this.server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        this.server.stop(0);
    }

    @ParameterizedTest(name = "{0} + {1} -> {2}")
    @CsvSource({"'',          /.well-known/agent-card.json, /.well-known/agent-card.json",
            "/,          /.well-known/agent-card.json, /.well-known/agent-card.json",
            "/agent,     /.well-known/agent-card.json, /agent/.well-known/agent-card.json",
            "/agent/,    /.well-known/agent-card.json, /agent/.well-known/agent-card.json",
            "/a/b,       /.well-known/agent-card.json, /a/b/.well-known/agent-card.json",
            "/agent,     /custom/agent-card.json,      /agent/custom/agent-card.json",
            "/agent,     custom/agent.json,            /agent/custom/agent.json",
            "'',         custom/agent.json,            /custom/agent.json",
            "/my%20agent, /.well-known/agent-card.json, /my agent/.well-known/agent-card.json"})
    @DisplayName("Should request the agent card relative to the agent base URL")
    void shouldResolveAgentCardRelativeToBaseUrlForV0_3(String agentPath, String agentCardPath, String expectedPath) {
        this.currentCard = AGENT_CARD_V0_3;
        A2ASubagentResolver resolver = new A2ASubagentResolver(agentCardPath);

        A2ASubagentDefinition definition = resolver
                .resolve(new SubagentReference(this.rootUrl + agentPath, A2ASubagentDefinition.KIND));

        assertThat(this.requestedPaths).containsExactly(expectedPath);
        assertThat(definition.getName()).isEqualTo("test-agent");
        assertThat(definition).isInstanceOf(A2ASubagentDefinition_v0_3.class);
    }

    @ParameterizedTest(name = "{0} + {1} -> {2}")
    @CsvSource({"'',          /.well-known/agent-card.json, /.well-known/agent-card.json",
            "/,          /.well-known/agent-card.json, /.well-known/agent-card.json",
            "/agent,     /.well-known/agent-card.json, /agent/.well-known/agent-card.json",
            "/agent/,    /.well-known/agent-card.json, /agent/.well-known/agent-card.json",
            "/a/b,       /.well-known/agent-card.json, /a/b/.well-known/agent-card.json",
            "/agent,     /custom/agent-card.json,      /agent/custom/agent-card.json",
            "/agent,     custom/agent.json,            /agent/custom/agent.json",
            "'',         custom/agent.json,            /custom/agent.json",
            "/my%20agent, /.well-known/agent-card.json, /my agent/.well-known/agent-card.json"})
    @DisplayName("Should request the agent card relative to the agent base URL")
    void shouldResolveAgentCardRelativeToBaseUrlForV1_0(String agentPath, String agentCardPath, String expectedPath) {
        this.currentCard = AGENT_CARD_V1_0;
        A2ASubagentResolver resolver = new A2ASubagentResolver(agentCardPath);

        A2ASubagentDefinition definition = resolver
                .resolve(new SubagentReference(this.rootUrl + agentPath, A2ASubagentDefinition.KIND, A2ASubagentDefinition_v1_0.PROTOCOL_VERSION));

        assertThat(this.requestedPaths).containsExactly(expectedPath);
        assertThat(definition.getName()).isEqualTo("test-agent");
        assertThat(definition).isInstanceOf(A2ASubagentDefinition_v1_0.class);
    }
}
