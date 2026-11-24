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
package org.springaicommunity.agent.utils;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springaicommunity.agent.tools.AskUserQuestionTool;
import org.springaicommunity.agent.tools.AskUserQuestionTool.Question;
import org.springaicommunity.agent.tools.AskUserQuestionTool.Question.Option;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.http.codec.ServerSentEvent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HeartbeatsIntegrationTest {

	@Test
	void streamsChatClientContentAsDataWithOneModelCall() {
		ChatModel model = mock(ChatModel.class);
		when(model.getOptions()).thenReturn(ChatOptions.builder().build());
		ChatResponse response = new ChatResponse(List.of(new Generation(new AssistantMessage("hello"))));
		when(model.stream(any(Prompt.class))).thenReturn(Flux.just(response));
		ChatClient client = ChatClient.builder(model).build();

		Flux<ServerSentEvent<String>> events = Heartbeats.sse(client.prompt().user("hi").stream().content());
		StepVerifier.create(events)
			.assertNext(event -> assertThat(event.data()).isEqualTo("hello"))
			.expectComplete()
			.verify(Duration.ofSeconds(5));
		verify(model).stream(any(Prompt.class));
	}

	@Test
	void stopsBeatingWhenAQuestionHandlerReceivesAnAnswer() {
		Sinks.One<Question> pending = Sinks.one();
		CompletableFuture<Map<String, String>> answer = new CompletableFuture<>();
		AskUserQuestionTool tool = AskUserQuestionTool.builder().questionHandler(questions -> {
			pending.tryEmitValue(questions.get(0));
			return answer.orTimeout(5, TimeUnit.SECONDS).join();
		}).build();
		Question question = new Question("Continue?", "Confirm",
				List.of(new Option("Yes", "Proceed"), new Option("No", "Stop")), false);
		Mono<String> toolResult = Mono.fromCallable(() -> tool.askUserQuestion(List.of(question), Map.of()))
			.subscribeOn(Schedulers.boundedElastic());
		Flux<String> interaction = pending.asMono()
			.flatMapMany(q -> Flux.just(q.question())
				.concatWith(Heartbeats.until(Mono.fromFuture(answer, true), Duration.ofMinutes(1), "beat")));

		StepVerifier.create(interaction.mergeWith(toolResult))
			.expectNext("Continue?", "beat")
			.then(() -> answer.complete(Map.of("Continue?", "Yes")))
			.assertNext(result -> assertThat(result).contains("Continue?", "Yes"))
			.expectComplete()
			.verify(Duration.ofSeconds(5));
	}

}
