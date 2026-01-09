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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springaicommunity.agent.tools.AskUserQuestionTool.Question;
import org.springaicommunity.agent.tools.AskUserQuestionTool.Question.Option;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link AskUserQuestionTool}.
 *
 * @author Christian Tzolov
 */
@DisplayName("AskUserQuestionTool Tests")
class AskUserQuestionToolTest {

	private AtomicReference<List<Question>> capturedInput;

	private AskUserQuestionTool tool;

	@BeforeEach
	void setUp() {
		this.capturedInput = new AtomicReference<>();
		this.tool = AskUserQuestionTool.builder()
			.questionAnswerFunction(questions -> {
				this.capturedInput.set(questions);
				// Simulate user providing answers
				Map<String, String> answers = new HashMap<>();
				for (Question q : questions) {
					// Select the first option by default
					answers.put(q.question(), q.options().get(0).label());
				}
				return answers;
			})
			.build();
	}

	@Nested
	@DisplayName("Builder Tests")
	class BuilderTests {

		@Test
		@DisplayName("Should create tool with valid function")
		void shouldCreateToolWithValidFunction() {
			AskUserQuestionTool customTool = AskUserQuestionTool.builder()
				.questionAnswerFunction(questions -> Map.of())
				.build();
			assertThat(customTool).isNotNull();
		}

		@Test
		@DisplayName("Should reject null function in builder")
		void shouldRejectNullFunctionInBuilder() {
			assertThatThrownBy(() -> AskUserQuestionTool.builder().questionAnswerFunction(null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("questionAnswerFunction must not be null");
		}

		@Test
		@DisplayName("Should reject build without function")
		void shouldRejectBuildWithoutFunction() {
			assertThatThrownBy(() -> AskUserQuestionTool.builder().build())
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("questionAnswerFunction must be provided");
		}

	}

	@Nested
	@DisplayName("Valid Question Tests")
	class ValidQuestionTests {

		@Test
		@DisplayName("Should accept single valid question")
		void shouldAcceptSingleValidQuestion() {
			List<Option> options = List.of(new Option("Option 1", "First option"),
					new Option("Option 2", "Second option"));
			List<Question> questions = List.of(new Question("Which option?", "Choice", options, false));

			String result = AskUserQuestionToolTest.this.tool.askUserQuestion(questions, null);

			assertThat(result).isNotNull();
			assertThat(result).contains("Which option?");
		}

		@Test
		@DisplayName("Should accept four questions (maximum)")
		void shouldAcceptFourQuestions() {
			List<Question> questions = List.of(
					new Question("Question 1?", "Q1", List.of(new Option("A", "Answer A"), new Option("B", "Answer B")),
							false),
					new Question("Question 2?", "Q2", List.of(new Option("C", "Answer C"), new Option("D", "Answer D")),
							false),
					new Question("Question 3?", "Q3", List.of(new Option("E", "Answer E"), new Option("F", "Answer F")),
							false),
					new Question("Question 4?", "Q4", List.of(new Option("G", "Answer G"), new Option("H", "Answer H")),
							false));

			String result = AskUserQuestionToolTest.this.tool.askUserQuestion(questions, null);

			assertThat(result).isNotNull();
			assertThat(result).contains("Question 1?")
				.contains("Question 2?")
				.contains("Question 3?")
				.contains("Question 4?");
		}

		@Test
		@DisplayName("Should default multiSelect to false when null")
		void shouldDefaultMultiSelectToFalseWhenNull() {
			List<Option> options = List.of(new Option("A", "First"), new Option("B", "Second"));
			Question question = new Question("Choose?", "Select", options, null);

			assertThat(question.multiSelect()).isFalse();
		}

		@Test
		@DisplayName("Should accept question with header at max length")
		void shouldAcceptQuestionWithHeaderAtMaxLength() {
			List<Option> options = List.of(new Option("A", "First"), new Option("B", "Second"));
			List<Question> questions = List.of(new Question("Question?", "12Characters", options, false));

			String result = AskUserQuestionToolTest.this.tool.askUserQuestion(questions, null);

			assertThat(result).isNotNull();
			assertThat(AskUserQuestionToolTest.this.capturedInput.get().get(0).header()).hasSize(12);
		}

	}

	@Nested
	@DisplayName("Question Validation Tests")
	class QuestionValidationTests {

		@Test
		@DisplayName("Should reject null questions list")
		void shouldRejectNullQuestionsList() {
			assertThatThrownBy(() -> AskUserQuestionToolTest.this.tool.askUserQuestion(null, null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Questions list cannot be null");
		}

		@Test
		@DisplayName("Should reject empty questions list")
		void shouldRejectEmptyQuestionsList() {
			assertThatThrownBy(() -> AskUserQuestionToolTest.this.tool.askUserQuestion(List.of(), null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Questions list must contain 1-4 questions");
		}

		@Test
		@DisplayName("Should reject more than 4 questions")
		void shouldRejectMoreThanFourQuestions() {
			List<Question> questions = List.of(
					new Question("Q1?", "Q1", List.of(new Option("A", "Answer A"), new Option("B", "Answer B")),
							false),
					new Question("Q2?", "Q2", List.of(new Option("C", "Answer C"), new Option("D", "Answer D")),
							false),
					new Question("Q3?", "Q3", List.of(new Option("E", "Answer E"), new Option("F", "Answer F")),
							false),
					new Question("Q4?", "Q4", List.of(new Option("G", "Answer G"), new Option("H", "Answer H")),
							false),
					new Question("Q5?", "Q5", List.of(new Option("I", "Answer I"), new Option("J", "Answer J")),
							false));

			assertThatThrownBy(() -> AskUserQuestionToolTest.this.tool.askUserQuestion(questions, null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Questions list must contain 1-4 questions")
				.hasMessageContaining("got: 5");
		}

		@Test
		@DisplayName("Should reject null question in list")
		void shouldRejectNullQuestionInList() {
			List<Question> questions = new ArrayList<>();
			questions.add(null);

			assertThatThrownBy(() -> AskUserQuestionToolTest.this.tool.askUserQuestion(questions, null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Question at index 0 is null");
		}

		@Test
		@DisplayName("Should reject question with null text")
		void shouldRejectQuestionWithNullText() {
			List<Option> options = List.of(new Option("A", "First"), new Option("B", "Second"));

			assertThatThrownBy(() -> new Question(null, "Header", options, false))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Question text cannot be null or blank");
		}

		@Test
		@DisplayName("Should reject question with blank text")
		void shouldRejectQuestionWithBlankText() {
			List<Option> options = List.of(new Option("A", "First"), new Option("B", "Second"));

			assertThatThrownBy(() -> new Question("   ", "Header", options, false))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Question text cannot be null or blank");
		}

		@Test
		@DisplayName("Should reject question with null header")
		void shouldRejectQuestionWithNullHeader() {
			List<Option> options = List.of(new Option("A", "First"), new Option("B", "Second"));

			assertThatThrownBy(() -> new Question("Question?", null, options, false))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Header cannot be null or blank");
		}

		@Test
		@DisplayName("Should reject question with blank header")
		void shouldRejectQuestionWithBlankHeader() {
			List<Option> options = List.of(new Option("A", "First"), new Option("B", "Second"));

			assertThatThrownBy(() -> new Question("Question?", "   ", options, false))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Header cannot be null or blank");
		}

		@Test
		@DisplayName("Should reject question with header exceeding 12 characters")
		void shouldRejectQuestionWithHeaderExceeding12Characters() {
			List<Option> options = List.of(new Option("A", "First"), new Option("B", "Second"));

			assertThatThrownBy(() -> new Question("Question?", "ThisIsTooLong", options, false))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Header must be max 12 characters")
				.hasMessageContaining("got: 13");
		}

		@Test
		@DisplayName("Should reject question with null options")
		void shouldRejectQuestionWithNullOptions() {
			assertThatThrownBy(() -> new Question("Question?", "Header", null, false))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Options must have 2-4 items");
		}

		@Test
		@DisplayName("Should reject question with only one option")
		void shouldRejectQuestionWithOnlyOneOption() {
			List<Option> options = List.of(new Option("Only", "Single option"));

			assertThatThrownBy(() -> new Question("Question?", "Header", options, false))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Options must have 2-4 items");
		}

		@Test
		@DisplayName("Should reject question with more than 4 options")
		void shouldRejectQuestionWithMoreThanFourOptions() {
			List<Option> options = List.of(new Option("A", "First"), new Option("B", "Second"),
					new Option("C", "Third"), new Option("D", "Fourth"), new Option("E", "Fifth"));

			assertThatThrownBy(() -> new Question("Question?", "Header", options, false))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Options must have 2-4 items");
		}

	}

	@Nested
	@DisplayName("Option Validation Tests")
	class OptionValidationTests {

		@Test
		@DisplayName("Should reject option with null label")
		void shouldRejectOptionWithNullLabel() {
			assertThatThrownBy(() -> new Option(null, "Description"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Option label cannot be null or blank");
		}

		@Test
		@DisplayName("Should reject option with blank label")
		void shouldRejectOptionWithBlankLabel() {
			assertThatThrownBy(() -> new Option("   ", "Description"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Option label cannot be null or blank");
		}

		@Test
		@DisplayName("Should reject option with null description")
		void shouldRejectOptionWithNullDescription() {
			assertThatThrownBy(() -> new Option("Label", null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Option description cannot be null or blank");
		}

		@Test
		@DisplayName("Should reject option with blank description")
		void shouldRejectOptionWithBlankDescription() {
			assertThatThrownBy(() -> new Option("Label", "   "))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Option description cannot be null or blank");
		}

	}


	@Nested
	@DisplayName("Question Record Tests")
	class QuestionRecordTests {

		@Test
		@DisplayName("Should create immutable copy of options list")
		void shouldCreateImmutableCopyOfOptionsList() {
			List<Option> options = new ArrayList<>(List.of(new Option("A", "First"), new Option("B", "Second")));

			Question question = new Question("Question?", "Header", options, false);

			// Modify original list
			options.clear();

			// Question should still have the options
			assertThat(question.options()).hasSize(2);
		}

		@Test
		@DisplayName("Should prevent modification of returned options list")
		void shouldPreventModificationOfReturnedOptionsList() {
			List<Option> options = List.of(new Option("A", "First"), new Option("B", "Second"));

			Question question = new Question("Question?", "Header", options, false);

			assertThatThrownBy(() -> question.options().clear()).isInstanceOf(UnsupportedOperationException.class);
		}

	}


	@Nested
	@DisplayName("Function Invocation Tests")
	class FunctionInvocationTests {

		@Test
		@DisplayName("Should invoke function with questions")
		void shouldInvokeFunctionWithQuestions() {
			List<Option> options = List.of(new Option("A", "First"), new Option("B", "Second"));
			List<Question> questions = List.of(new Question("Question?", "Header", options, false));

			AskUserQuestionToolTest.this.tool.askUserQuestion(questions, null);

			assertThat(AskUserQuestionToolTest.this.capturedInput.get()).isNotNull();
			assertThat(AskUserQuestionToolTest.this.capturedInput.get()).hasSize(1);
		}

		@Test
		@DisplayName("Should invoke function and return result")
		void shouldInvokeFunctionAndReturnResult() {
			List<Option> options = List.of(new Option("A", "First"), new Option("B", "Second"));
			List<Question> questions = List.of(new Question("Question?", "Header", options, false));

			String result = AskUserQuestionToolTest.this.tool.askUserQuestion(questions, null);

			assertThat(result).isNotNull();
			assertThat(result).contains("User has answered your questions");
			assertThat(AskUserQuestionToolTest.this.capturedInput.get()).hasSize(1);
		}

	}

	@Nested
	@DisplayName("Integration Tests")
	class IntegrationTests {

		@Test
		@DisplayName("Should handle single-select question workflow")
		void shouldHandleSingleSelectQuestionWorkflow() {
			AtomicReference<Map<String, String>> capturedAnswers = new AtomicReference<>();
			AskUserQuestionTool customTool = AskUserQuestionTool.builder()
				.questionAnswerFunction(questions -> {
					Map<String, String> answers = new HashMap<>();
					answers.put("Which library should we use?", "React");
					capturedAnswers.set(answers);
					return answers;
				})
				.build();

			List<Option> options = List.of(new Option("React", "React library"),
					new Option("Vue", "Vue.js framework"), new Option("Angular", "Angular framework"));
			List<Question> questions = List
				.of(new Question("Which library should we use?", "Library", options, false));

			String result = customTool.askUserQuestion(questions, null);

			assertThat(result).contains("React");
			assertThat(capturedAnswers.get()).containsEntry("Which library should we use?", "React");
		}

		@Test
		@DisplayName("Should handle multi-select question workflow")
		void shouldHandleMultiSelectQuestionWorkflow() {
			AtomicReference<Map<String, String>> capturedAnswers = new AtomicReference<>();
			AskUserQuestionTool customTool = AskUserQuestionTool.builder()
				.questionAnswerFunction(questions -> {
					Map<String, String> answers = new HashMap<>();
					// Join multiple selections with ", "
					answers.put("Which features?", "Authentication, Database");
					capturedAnswers.set(answers);
					return answers;
				})
				.build();

			List<Option> options = List.of(new Option("Authentication", "User auth"),
					new Option("Database", "Database integration"), new Option("API", "REST API"));
			List<Question> questions = List.of(new Question("Which features?", "Features", options, true));

			String result = customTool.askUserQuestion(questions, null);

			assertThat(result).contains("Authentication, Database");
			assertThat(capturedAnswers.get()).containsEntry("Which features?", "Authentication, Database");
		}

		@Test
		@DisplayName("Should handle free-text input")
		void shouldHandleFreeTextInput() {
			AtomicReference<Map<String, String>> capturedAnswers = new AtomicReference<>();
			AskUserQuestionTool customTool = AskUserQuestionTool.builder()
				.questionAnswerFunction(questions -> {
					Map<String, String> answers = new HashMap<>();
					// User provides custom text instead of selecting an option
					answers.put("Which database?", "PostgreSQL");
					capturedAnswers.set(answers);
					return answers;
				})
				.build();

			List<Option> options = List.of(new Option("MySQL", "MySQL database"),
					new Option("MongoDB", "MongoDB database"));
			List<Question> questions = List.of(new Question("Which database?", "Database", options, false));

			String result = customTool.askUserQuestion(questions, null);

			assertThat(result).contains("PostgreSQL");
			assertThat(capturedAnswers.get()).containsEntry("Which database?", "PostgreSQL");
		}

		@Test
		@DisplayName("Should handle multiple questions with mixed types")
		void shouldHandleMultipleQuestionsWithMixedTypes() {
			AtomicReference<Map<String, String>> capturedAnswers = new AtomicReference<>();
			AskUserQuestionTool customTool = AskUserQuestionTool.builder()
				.questionAnswerFunction(questions -> {
					Map<String, String> answers = new HashMap<>();
					answers.put("Which format?", "JSON");
					answers.put("Which methods?", "GET, POST");
					capturedAnswers.set(answers);
					return answers;
				})
				.build();

			List<Question> questions = List.of(
					new Question("Which format?", "Format",
							List.of(new Option("JSON", "JSON format"), new Option("XML", "XML format")), false),
					new Question("Which methods?", "Methods",
							List.of(new Option("GET", "GET method"), new Option("POST", "POST method"),
									new Option("PUT", "PUT method")),
							true));

			String result = customTool.askUserQuestion(questions, null);

			assertThat(result).contains("JSON").contains("GET, POST");
			assertThat(capturedAnswers.get()).containsEntry("Which format?", "JSON");
			assertThat(capturedAnswers.get()).containsEntry("Which methods?", "GET, POST");
		}

	}

	@Nested
	@DisplayName("Thread Safety Tests")
	class ThreadSafetyTests {

		@Test
		@DisplayName("Should handle concurrent invocations safely")
		void shouldHandleConcurrentInvocationsSafely() throws InterruptedException {
			AtomicInteger callCount = new AtomicInteger(0);
			AskUserQuestionTool threadSafeTool = AskUserQuestionTool.builder()
				.questionAnswerFunction(questions -> {
					callCount.incrementAndGet();
					Map<String, String> answers = new HashMap<>();
					for (Question q : questions) {
						answers.put(q.question(), q.options().get(0).label());
					}
					return answers;
				})
				.build();

			int threadCount = 10;
			ExecutorService executor = Executors.newFixedThreadPool(threadCount);
			CountDownLatch latch = new CountDownLatch(threadCount);

			List<Option> options = List.of(new Option("A", "First"), new Option("B", "Second"));
			List<Question> questions = List.of(new Question("Question?", "Header", options, false));

			for (int i = 0; i < threadCount; i++) {
				executor.submit(() -> {
					try {
						String result = threadSafeTool.askUserQuestion(questions, null);
						assertThat(result).isNotNull();
						assertThat(result).contains("User has answered your questions");
					}
					finally {
						latch.countDown();
					}
				});
			}

			latch.await(5, TimeUnit.SECONDS);
			executor.shutdown();

			assertThat(callCount.get()).isEqualTo(threadCount);
		}

	}

}
