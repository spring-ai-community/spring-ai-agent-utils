# AskUserQuestionTool

A tool for asking users clarifying questions during AI agent execution. Enables AI agents to gather user preferences, clarify ambiguous instructions, and get decisions on implementation choices.

**Features:**
- Multiple-choice questions with 2-4 options per question
- Support for single-select and multi-select questions
- Free-text input support beyond predefined options
- 1-10 questions per interaction
- Custom `QuestionHandler` callbacks for UI integration
- Optional answer validation with configurable behavior
- Enhanced AI model integration with @JsonPropertyDescription annotations

## Overview

The `AskUserQuestionTool` is a Spring AI implementation of [Claude Code's AskUserQuestion tool](https://platform.claude.com/docs/en/agent-sdk/user-input#question-format).

The tool follows the question-answer workflow: AI generates questions with options, user provides answers, and AI continues with the collected input.

> **Improtant:** The AskUserQuestionTool can be used only with main Agents not sub-agents. Subagents cannot directly interact with users. When invoked via the Task tool, they operate in a completely isolated context.

## Basic Usage

```java
// The AI agent will call this tool automatically when it needs input
// For example, when asked: "Help me choose a database for my app"
AskUserQuestionTool askTool = AskUserQuestionTool.builder()
    .questionHandler(questions -> {
        // Display questions to user via your UI and collect answers
        return collectUserAnswers(questions);
    })
    .build();

ChatClient chatClient = chatClientBuilder
    .defaultTools(askTool)
    .build();
```

You must provide a `QuestionHandler` implementation via the `questionHandler()` builder method. For CLI applications, you can use the provided `CommandLineQuestionHandler` (see [Console-Based Implementation](#console-based-implementation)).

The `AskUserQuestionTool` class is thread-safe. However, the provided `QuestionHandler` must also be thread-safe if it maintains shared state.

All data structures are immutable with defensive copies. The `options` list is copied on Question construction and all returned collections cannot be modified.

## Question Format

The input Questions list cannot be null or empty and can contain 1-10 questions.
Each `Question` received from the AI consists of:

- `question` - The complete question text. Required, not blank, should end with "?"
- `header` - Short label for UI display. Required, max 12 characters
- `options` - List of the available `Options`. Each `Options` has:
    - `label` - Display text (e.g., "React", "Vue", "Angular")
    - `description` - Explanation of what this option means
- `multiSelect` - Flag indicating if multiple selections are allowed. Defaults to false if null.

**Example Questions:**

- Single-select question
    ```json
    {
        "question": "Which library should we use for date formatting?",
        "header": "Library",
        "options": [
            { "label": "Moment.js", "description": "Popular but large library" },
            { "label": "Day.js", "description": "Lightweight Moment.js alternative" },
            { "label": "date-fns", "description": "Modular and tree-shakeable" }
        ],
        "multiSelect": false
    }
    ```
- Multi-select question
    ```json
    {
        "question": "Which features do you want to enable?",
        "header": "Features",
        "options": [
            { "label": "Authentication", "description": "User login and registration" },
            { "label": "Database", "description": "PostgreSQL integration" },
            { "label": "Caching", "description": "Redis caching layer" }
        ],
        "multiSelect": true
    }
    ```

## Answer Format

The `QuestionHandler` receives a `List<Question>` and returns a `Map<String, String>` with answers:
- **Keys:** The question text (from `Question.question` field)
- **Values:** Selected option label(s)
  - Single-select: `"React"`
  - Multi-select: `"Authentication, Database"` (comma-separated)
  - Free-text: Any custom text the user provides

```java
// Example handler implementation
QuestionHandler handler = questions -> {
    Map<String, String> answers = new HashMap<>();
    for (Question q : questions) {
        String answer = promptUser(q);
        answers.put(q.question(), answer);
    }
    return answers;
};
```

### Error Handling

By default, the tool validates the answers returned by the `QuestionHandler`:

- The returned map is non-null and all questions have corresponding answers (keys match question text)
- No answer values are null (empty strings are acceptable)

If validation fails, an `InvalidUserAnswerException` is thrown with a descriptive error message.
This exception indicates user input errors and should be propagated to the user, not the AI agent.

> **Tip:** Configure Spring AI to handle this: `spring.ai.tools.throw-exception-on-error=org.springaicommunity.agent.tools.AskUserQuestionTool$InvalidUserAnswerException`

If the answer map contains keys that don't match any question, a warning is logged but execution continues. This allows flexibility while alerting developers to potential issues.

#### Disabling Answer Validation

You can disable answer validation by setting `answersValidation(false)` when building the tool:

```java
AskUserQuestionTool askTool = AskUserQuestionTool.builder()
    .questionHandler(questions -> collectUserAnswers(questions))
    .answersValidation(false)  // Disable validation
    .build();
```

This is useful when you want to allow partial answers or handle validation in your own custom logic.

## Implementation Example

### Console-Based Implementation

The library provides a ready-to-use `CommandLineQuestionHandler` for console/CLI applications:

```java
import org.springaicommunity.agent.utils.CommandLineQuestionHandler;

AskUserQuestionTool askTool = AskUserQuestionTool.builder()
    .questionHandler(new CommandLineQuestionHandler())
    .build();

ChatClient chatClient = chatClientBuilder
    .defaultTools(askTool)
    .build();
```

The `CommandLineQuestionHandler` displays questions with numbered options and supports:
- Single-select: Enter a number (e.g., `1`) or custom text
- Multi-select: Enter comma-separated numbers (e.g., `1,3`) or custom text
- Free-text input as an alternative to predefined options

### Web/GUI Implementation

```java
@RestController
public class WebQuestionHandler {

    private final AtomicReference<CompletableFuture<Map<String, String>>> pendingResponse
        = new AtomicReference<>();

    public AskUserQuestionTool createTool() {
        return AskUserQuestionTool.builder()
            .questionHandler(this::handleQuestions)
            .build();
    }

    private Map<String, String> handleQuestions(List<Question> questions) {
        // Store questions and create a future for the response
        CompletableFuture<Map<String, String>> future = new CompletableFuture<>();
        pendingResponse.set(future);

        // Notify frontend (WebSocket, SSE, or polling)
        sendQuestionsToFrontend(questions);

        try {
            // Wait for user to submit answers via /api/answers endpoint
            Map<String, String> answers = future.get(5, TimeUnit.MINUTES);
            return answers;
        } catch (Exception e) {
            throw new RuntimeException("Timeout waiting for user response", e);
        }
    }

    @PostMapping("/api/answers")
    public void submitAnswers(@RequestBody Map<String, String> answers) {
        CompletableFuture<Map<String, String>> future = pendingResponse.getAndSet(null);
        if (future != null) {
            future.complete(answers);
        }
    }
}
```

## Demo Application

See the [ask-user-question-demo](../../../examples/ask-user-question-demo) for a complete working example of a console-based AI chat application using the AskUserQuestionTool with `CommandLineQuestionHandler`. The demo shows how to:
- Use the provided `CommandLineQuestionHandler` for console interaction
- Handle both single-select and multi-select questions
- Configure the tool with answer validation options

## See Also

- [Claude Agent SDK - User Input](https://platform.claude.com/docs/en/agent-sdk/user-input#question-format)
- [Spring AI Documentation](https://docs.spring.io/spring-ai/reference/)
