/*
 * Copyright 2026 - 2026 the original author or authors.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at https://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.springaicommunity.agent.tools;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.util.JsonHelper;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class ContextWebSearchToolTests {

	private static final String KEY = "synthetic-context-secret";
	private static final JsonHelper JSON = new JsonHelper();
	private static final String ROW = """
		{"title":"Spring AI","url":"https://docs.spring.io/spring-ai/reference/", "description":"Unverified search snippet", "highlights":{"code":"SUCCESS","highlights":["  Relevant source passage  ","Second passage"]}}
		""";

	@Test
	void shouldDispatchThroughNativeToolCallbackWithHighlightsAndFilters() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(request -> {
			assertThat(request.getURI().toString()).isEqualTo("https://api.context.dev/v1/web/search");
			assertThat(request.getMethod()).isEqualTo(HttpMethod.POST);
			assertThat(request.getHeaders().getFirst("Authorization")).isEqualTo("Bearer " + KEY);
			String payload = ((org.springframework.mock.http.client.MockClientHttpRequest) request).getBodyAsString();
			assertThat(JSON.fromJson(payload, Map.class)).isEqualTo(JSON.fromJson("""
				{"query":"Spring AI","numResults":3,"includeDomains":["spring.io"],"excludeDomains":["example.com"],"highlightsOptions":{"enabled":true,"maxCharacters":1000},"timeoutOpts":{"milliseconds":25000,"behavior":"fail"}}
				""", Map.class));
		})
			.andRespond(withSuccess("{\"results\":[" + ROW + "]}", MediaType.APPLICATION_JSON));
		var tool = new ContextWebSearchTool(KEY, 3, builder);
		var callback = ToolCallbacks.from(tool)[0];
		assertThat(callback.getToolDefinition().name()).isEqualTo("WebSearch");
		String result = callback.call("""
			{"query":"Spring AI","allowedDomains":["spring.io"],"blockedDomains":["example.com"]}
			""");
		assertThat(result).contains("Relevant source passage", "Second passage", "https://docs.spring.io/")
			.doesNotContain(KEY, "Unverified search snippet");
		server.verify();
	}

	@Test
	void shouldDistinguishEmptySuccessAndUnavailableHighlights() {
		for (String response : List.of("{\"results\":[]}", "{\"partial\":false,\"results\":[]}")) {
			assertThat(call(response)).isEqualTo("[]");
		}
		for (String highlights : List.of("null", "{\"code\":\"TIMEOUT\",\"highlights\":null}",
			"{\"code\":\"SUCCESS\",\"highlights\":[]}")) {
			String row = "{\"title\":\"Unavailable\",\"url\":\"https://example.com\",\"highlights\":" + highlights + "}";
			assertSafeFailure("{\"results\":[" + row + "]}");
			assertThat(call("{\"results\":[" + row + "," + ROW + "]}")).contains("Relevant source passage")
				.doesNotContain("Unavailable");
		}
	}

	@Test
	void shouldRejectMalformedPartialOrResultsWithoutLeakingPayloads() {
		for (String response : List.of("{", "{\"secret\":\"" + KEY + "\"}", "{\"results\":null}",
			"{\"results\":{}}", "{\"results\":[null]}", "{\"results\":[{\"title\":42}]}",
			"{\"results\":[{\"title\":\"x\",\"url\":\"javascript:alert(1)\"}]}",
			"{\"results\":[{\"title\":\"x\",\"url\":\"https://user:secret@example.com\"}]}")) {
			assertSafeFailure(response);
		}
		for (String partial : List.of("true", "null", "0", "\"false\"")) {
			assertSafeFailure("{\"partial\":" + partial + ",\"results\":[" + ROW + "]}");
		}
	}

	@Test
	void shouldRejectInvalidHighlightsAndBoundResultCount() {
		for (String h : List.of("{\"code\":\"SUCCESS\",\"highlights\":[42]}",
			"{\"code\":\"SUCCESS\",\"highlights\":null}", "{\"code\":\"UNKNOWN\",\"highlights\":[]}")) {
			assertSafeFailure("{\"results\":[{\"title\":\"x\",\"url\":\"https://example.com\",\"highlights\":" + h + "}]}");
		}
		String result = call("{\"results\":[" + ROW + "," + ROW + "]}");
		assertThat(JSON.fromJson(result, List.class)).hasSize(1);
	}

	@Test
	void shouldRejectHttpAndTransportErrorsWithoutRawExceptionCauses() {
		for (int code : List.of(302, 401, 402, 403, 429, 500)) {
			RestClient.Builder builder = RestClient.builder();
			MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
			server.expect(anything()).andRespond(withStatus(HttpStatus.valueOf(code)).body(KEY));
			assertSafeThrowable(() -> ToolCallbacks.from(new ContextWebSearchTool(KEY, 1, builder))[0]
				.call("{\"query\":\"test\"}"));
			server.verify();
		}
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(anything()).andRespond(withException(new IOException(KEY + " \\u0073ecret")));
		assertSafeThrowable(() -> ToolCallbacks.from(new ContextWebSearchTool(KEY, 1, builder))[0]
			.call("{\"query\":\"test\"}"));
		server.verify();
	}

	@Test
	void shouldBoundResponseAndValidateBeforeDispatch() {
		assertSafeFailure(" ".repeat(5 * 1024 * 1024 + 1));
		var tool = ContextWebSearchTool.builder(KEY).build();
		for (String query : List.of("", " ", "x".repeat(501))) {
			assertThatThrownBy(() -> tool.webSearch(query, null, null)).isInstanceOf(IllegalArgumentException.class);
		}
		assertThatThrownBy(() -> ContextWebSearchTool.builder(" ")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> ContextWebSearchTool.builder(KEY).resultCount(0)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> ContextWebSearchTool.builder(KEY).resultCount(101)).isInstanceOf(IllegalArgumentException.class);
		assertThat(ContextWebSearchTool.builder(KEY).resultCount(100).build()).isNotNull();
	}

	@Test
	void shouldValidateDomainListsAndInterruptBeforeDispatch() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		var tool = new ContextWebSearchTool(KEY, 1, builder);
		for (List<String> domains : List.of(java.util.Arrays.asList((String) null), List.of(""), List.of("  "),
			java.util.Collections.nCopies(101, "example.com"))) {
			assertThatThrownBy(() -> tool.webSearch("test", domains, null)).isInstanceOf(IllegalArgumentException.class);
			assertThatThrownBy(() -> tool.webSearch("test", null, domains)).isInstanceOf(IllegalArgumentException.class);
		}
		try {
			Thread.currentThread().interrupt();
			assertSafeThrowable(() -> tool.webSearch("test", null, null));
			assertThat(Thread.currentThread().isInterrupted()).isTrue();
		}
		finally {
			Thread.interrupted();
		}
		server.verify();
		for (String key : List.of(KEY + "\n", KEY + "\r", KEY + "\u0000", KEY + "é")) {
			assertThatThrownBy(() -> ContextWebSearchTool.builder(key)).isInstanceOf(IllegalArgumentException.class)
				.hasMessageNotContaining(KEY);
		}
	}

	@Test
	void shouldRejectAdvertisedOversizedResponseAndMixedMalformedRows() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(anything()).andRespond(withSuccess("{\"results\":[]}", MediaType.APPLICATION_JSON)
			.header("Content-Length", "5242881"));
		assertSafeThrowable(() -> new ContextWebSearchTool(KEY, 1, builder).webSearch("test", null, null));
		server.verify();
		assertSafeFailure("{\"results\":[null," + ROW + "]}");
	}

	private String call(String response) {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(anything()).andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
		String result = new ContextWebSearchTool(KEY, 1, builder).webSearch("test", null, null);
		server.verify();
		return result;
	}

	private void assertSafeFailure(String response) {
		assertSafeThrowable(() -> call(response));
	}

	private void assertSafeThrowable(Runnable action) {
		assertThatThrownBy(action::run).satisfies(error -> {
			StringWriter trace = new StringWriter();
			error.printStackTrace(new PrintWriter(trace));
			assertThat(trace.toString()).doesNotContain(KEY, "\\u0073ecret");
		});
	}

}
