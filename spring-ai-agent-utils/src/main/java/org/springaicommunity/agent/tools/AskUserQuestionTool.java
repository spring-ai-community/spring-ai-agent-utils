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

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.util.json.JsonParser;
import org.springframework.util.Assert;

/**
 * Tool for asking users clarifying questions during AI agent execution.
 *
 * <p>
 * This is a Spring AI implementation of Claude Code's AskUserQuestion tool, enabling AI
 * agents to gather user preferences, clarify ambiguous instructions, and get decisions on
 * implementation choices during execution.
 *
 * <p>
 * <strong>Thread Safety:</strong> This class is thread-safe. The tool can be safely used
 * by multiple threads concurrently. However, thread safety depends on the provided
 * {@code questionAnswerFunction} being thread-safe. If the function maintains mutable
 * shared state, the caller must ensure proper synchronization.
 *
 * @author Christian Tzolov
 * @see <a href=
 * "https://platform.claude.com/docs/en/agent-sdk/user-input#question-format"> Claude
 * Agent SDK - Question Format</a>
 */
public class AskUserQuestionTool {

	private static final Logger logger = LoggerFactory.getLogger(AskUserQuestionTool.class);

	/**
	 * Function that handles the user question workflow.
	 *
	 * <p>
	 * When invoked, this function receives a list of {@link Question} objects that the
	 * AI agent wants to ask the user.
	 *
	 * <p>
	 * The function should:
	 * <ol>
	 * <li>Present the questions to the user through your application's UI</li>
	 * <li>Collect the user's answers (option labels or free text)</li>
	 * <li>Return a Map containing the answers keyed by question text</li>
	 * </ol>
	 *
	 * <p>
	 * Answer format:
	 * <ul>
	 * <li>Keys: The question text (from {@link Question#question()} field)</li>
	 * <li>Values: The selected option's label (from {@link Option#label()} field)</li>
	 * <li>Multi-select: Join multiple labels with ", "</li>
	 * <li>Free text: Use the user's custom text directly</li>
	 * </ul>
	 *
	 * <p>
	 * <strong>Thread Safety:</strong> This function may be called concurrently by
	 * multiple threads. Implementations must be thread-safe or use appropriate
	 * synchronization if they maintain mutable shared state.
	 */
	private final Function<List<Question>, Map<String, String>> questionAnswerFunction;

	protected AskUserQuestionTool(Function<List<Question>, Map<String, String>> questionAnswerFunction) {
		this.questionAnswerFunction = questionAnswerFunction;
	}

	/**
	 * Represents a single question to ask the user
	 */
	public record Question(
			/**
			 * The complete question to ask the user. Should be clear, specific, and end
			 * with a question mark. Example: "Which library should we use for date
			 * formatting?"
			 */
			String question,

			/**
			 * Very short label displayed as a chip/tag (max 12 chars). Examples: "Auth
			 * method", "Library", "Approach"
			 */
			String header,

			/**
			 * The available choices for this question. Must have 2-4 options. Each option
			 * should be a distinct, mutually exclusive choice (unless multiSelect is
			 * enabled).
			 */
			List<Option> options,

			/**
			 * Set to true to allow the user to select multiple options instead of just
			 * one. Use when choices are not mutually exclusive. Defaults to false if
			 * null.
			 */
			Boolean multiSelect) {

		public Question {
			// Validate question text
			if (question == null || question.isBlank()) {
				throw new IllegalArgumentException("Question text cannot be null or blank");
			}

			// Validate header
			if (header == null || header.isBlank()) {
				throw new IllegalArgumentException("Header cannot be null or blank");
			}
			if (header.length() > 12) {
				throw new IllegalArgumentException("Header must be max 12 characters, got: " + header.length());
			}

			// Validate options
			if (options == null || options.size() < 2 || options.size() > 4) {
				throw new IllegalArgumentException("Options must have 2-4 items");
			}

			// Default multiSelect to false if null
			if (multiSelect == null) {
				multiSelect = false;
			}

			// Make defensive copy for immutability
			options = List.copyOf(options);
		}

		/**
		 * Represents a single option/choice for a question
		 */
		public record Option(
				/**
				 * The display text for this option that the user will see and select.
				 * Should be concise (1-5 words) and clearly describe the choice.
				 */
				String label,

				/**
				 * Explanation of what this option means or what will happen if chosen.
				 * Useful for providing context about trade-offs or implications.
				 */
				String description) {

			public Option {
				if (label == null || label.isBlank()) {
					throw new IllegalArgumentException("Option label cannot be null or blank");
				}
				if (description == null || description.isBlank()) {
					throw new IllegalArgumentException("Option description cannot be null or blank");
				}
			}
		}
	}

	@Tool(name = "AskUserQuestionTool",
			description = """
					Use this tool when you need to ask the user questions during execution. This allows you to:
					1. Gather user preferences or requirements
					2. Clarify ambiguous instructions
					3. Get decisions on implementation choices as you work
					4. Offer choices to the user about what direction to take.

					Usage notes:
					- Users will always be able to select "Other" to provide custom text input
					- Use multiSelect: true to allow multiple answers to be selected for a question
					- If you recommend a specific option, make that the first option in the list and add "(Recommended)" at the end of the label
					""")
	public String askUserQuestion(
			@ToolParam(description = "Questions to ask the user (1-4 questions)") List<Question> questions,
			@ToolParam(description = "User answers collected by the permission component",
					required = false) Map<String, String> answers) {

		// Validate the questions list
		validateQuestions(questions);

		logger.debug("Asking user {} question(s)", questions.size());
		if (logger.isTraceEnabled()) {
			questions.forEach(q -> logger.trace("Question: {}", q.question()));
		}

		Map<String, String> result = this.questionAnswerFunction.apply(questions);		

		if (logger.isDebugEnabled() && result != null) {
			logger.debug("Received {} answer(s) from user", result.size());
		}

		return "User has answered your questions: " + JsonParser.toJson(result);
	}

	/**
	 * Validates the questions list according to the following rules: - Questions list
	 * must contain 1-4 questions - Each question is validated by its compact constructor
	 * @param questions the questions list to validate
	 * @throws IllegalArgumentException if validation fails
	 */
	private void validateQuestions(List<Question> questions) {
		if (questions == null) {
			throw new IllegalArgumentException("Questions list cannot be null");
		}

		if (questions.isEmpty() || questions.size() > 4) {
			throw new IllegalArgumentException("Questions list must contain 1-4 questions, got: " + questions.size());
		}

		// Validate each question (this will trigger the compact constructor validation)
		for (int i = 0; i < questions.size(); i++) {
			Question question = questions.get(i);
			if (question == null) {
				throw new IllegalArgumentException("Question at index " + i + " is null");
			}
		}
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private Function<List<Question>, Map<String, String>> questionAnswerFunction;

		public Builder questionAnswerFunction(Function<List<Question>, Map<String, String>> questionAnswerFunction) {
			Assert.notNull(questionAnswerFunction, "questionAnswerFunction must not be null");
			this.questionAnswerFunction = questionAnswerFunction;
			return this;
		}

		public AskUserQuestionTool build() {
			Assert.notNull(questionAnswerFunction, "questionAnswerFunction must be provided");
			return new AskUserQuestionTool(questionAnswerFunction);
		}

	}

}
