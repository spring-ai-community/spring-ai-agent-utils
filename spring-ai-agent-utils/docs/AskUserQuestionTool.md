# AskUserQuestionTool

A tool for asking users clarifying questions during AI agent execution. Enables AI agents to gather user preferences, clarify ambiguous instructions, and get decisions on implementation choices.

**Features:**
- Multiple-choice questions with 2-4 options per question
- Support for single-select and multi-select questions
- Free-text input support beyond predefined options
- 1-10 questions per interaction
- Custom function callbacks for UI integration
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
    .questionAnswerFunction(questions -> {
        // Display questions to user via your UI
        Map<String, String> answers = collectUserAnswers(questions);
        return answers;
    })
    .build();

ChatClient chatClient = chatClientBuilder
    .defaultTools(askTool)
    .build();
```

You have to provide a custom `questionAnswerFunction(Function<List<Question>, Map<String, String>>)` function to handle the AI questions.

The `AskUserQuestionTool` class is thread-safe and can be used concurrently by multiple threads. However, the provided `questionAnswerFunction` must also be thread-safe if shared state is maintained.

All data structures are immutable with defensive copies. The `options` list is copied on Question construction and all returned collections cannot be modified.

## Question Format

The input Questions list cannot be null or empty and can contain 1-10 questions.
Each [Question](https://github.com/spring-ai-community/spring-ai-agent-utils/blob/main/spring-ai-agent-utils/src/main/java/org/springaicommunity/agent/tools/AskUserQuestionTool.java#L90C16-L90C24) received from the AI consists of:

- `question` - The complete question text. Required, not blank, should end with "?"
- `header` - Short label for UI display. Required, max 12 characters
- `options` - List of the available [Options](https://github.com/spring-ai-community/spring-ai-agent-utils/blob/main/spring-ai-agent-utils/src/main/java/org/springaicommunity/agent/tools/AskUserQuestionTool.java#L118). Each [Options](https://github.com/spring-ai-community/spring-ai-agent-utils/blob/main/spring-ai-agent-utils/src/main/java/org/springaicommunity/agent/tools/AskUserQuestionTool.java#L118) has:
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

The `questionAnswerFunction` receives a `List<Question>` and should return a `Map<String, String>` with answers:
- **Keys:** The question text (from `Question.question` field)
- **Values:** Selected option label(s)
  - Single-select: `"React"`
  - Multi-select: `"Authentication, Database"` (comma-separated)
  - Free-text: Any custom text the user provides

```java
// Example function implementation
Function<List<Question>, Map<String, String>> handler = questions -> {
    Map<String, String> answers = new HashMap<>();
    for (Question q : questions) {
        // Collect user input for each question
        String answer = promptUser(q);
        answers.put(q.question(), answer);
    }
    return answers;
};

// Example answer map
Map<String, String> answers = Map.of(
    "Which library should we use?", "Day.js",
    "Which features do you want?", "Authentication, Database"
);
```

### Error Handling

By default, the tool automatically validates the answers returned by `questionAnswerFunction`:

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
    .questionAnswerFunction(questions -> collectUserAnswers(questions))
    .answersValidation(false)  // Disable validation
    .build();
```

This is useful when you want to allow partial answers or handle validation in your own custom logic.

## Implementation Example

### Console-Based Implementation

```java
public class ConsoleQuestionHandler {

    public static AskUserQuestionTool createTool() {
        return AskUserQuestionTool.builder()
            .questionAnswerFunction(ConsoleQuestionHandler::handleQuestions)
            .build();
    }

    private static Map<String, String> handleQuestions(List<Question> questions) {
        Map<String, String> answers = new HashMap<>();
        Scanner scanner = new Scanner(System.in);

        for (Question q : questions) {
            System.out.println("\n" + q.header() + ": " + q.question());

            List<Option> options = q.options();
            for (int i = 0; i < options.size(); i++) {
                Option opt = options.get(i);
                System.out.printf("  %d. %s - %s%n", i + 1, opt.label(), opt.description());
            }

            if (q.multiSelect()) {
                System.out.println("  (Enter numbers separated by commas, or type custom text)");
            } else {
                System.out.println("  (Enter a number, or type custom text)");
            }

            String response = scanner.nextLine().trim();
            answers.put(q.question(), parseResponse(response, options));
        }

        return answers;
    }

    private static String parseResponse(String response, List<Option> options) {
        try {
            // Try parsing as option number(s)
            String[] parts = response.split(",");
            List<String> labels = new ArrayList<>();
            for (String part : parts) {
                int index = Integer.parseInt(part.trim()) - 1;
                if (index >= 0 && index < options.size()) {
                    labels.add(options.get(index).label());
                }
            }
            return labels.isEmpty() ? response : String.join(", ", labels);
        } catch (NumberFormatException e) {
            // Not a number, use as free text
            return response;
        }
    }
}
```

### Web/GUI Implementation

```java
@RestController
public class WebQuestionHandler {

    private final AtomicReference<CompletableFuture<Map<String, String>>> pendingResponse
        = new AtomicReference<>();

    public AskUserQuestionTool createTool() {
        return AskUserQuestionTool.builder()
            .questionAnswerFunction(this::handleQuestions)
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

See the [ask-user-question-demo](../../../examples/ask-user-question-demo) for a complete working example of a console-based AI chat application using the AskUserQuestionTool. The demo shows how to:
- Implement a custom question handler for console interaction
- Parse user responses (numeric selections or free text)
- Handle both single-select and multi-select questions
- Configure the tool with answer validation options

## See Also

- [Claude Agent SDK - User Input](https://platform.claude.com/docs/en/agent-sdk/user-input#question-format)
- [Spring AI Documentation](https://docs.spring.io/spring-ai/reference/)
