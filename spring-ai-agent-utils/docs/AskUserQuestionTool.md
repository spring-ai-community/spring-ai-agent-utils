# AskUserQuestionTool

A tool for asking users clarifying questions during AI agent execution. Enables AI agents to gather user preferences, clarify ambiguous instructions, and get decisions on implementation choices.

**Features:**
- Multiple-choice questions with 2-4 options per question
- Support for single-select and multi-select questions
- Free-text input support beyond predefined options
- 1-4 questions per interaction
- Custom function callbacks for UI integration

## Overview

The `AskUserQuestionTool` is a Spring AI implementation of [Claude Code's AskUserQuestion tool](https://platform.claude.com/docs/en/agent-sdk/user-input#question-format).

The tool follows the question-answer workflow: AI generates questions with options, user provides answers, and AI continues with the collected input.

## Basic Usage

```java
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
// The AI agent will call this tool automatically when it needs input
// For example, when asked: "Help me choose a database for my app"
```

You have to provide a `questionAnswerFunction(Function<List<Question>, Map<String, String>>)` function to handle the AI questions.

The `AskUserQuestionTool` class is thread-safe and can be used concurrently by multiple threads. However, the provided `questionAnswerFunction` must also be thread-safe if shared state is maintained.

All data structures are immutable with defensive copies:
- `options` list is copied on Question construction
- Returned collections cannot be modified

## Question Format

The input  Questions list cannot be null or empty and can contain 1-4 questions.
Each [Question](https://github.com/spring-ai-community/spring-ai-agent-utils/blob/main/spring-ai-agent-utils/src/main/java/org/springaicommunity/agent/tools/AskUserQuestionTool.java#L90C16-L90C24) received from the AI consists of:

- `question` - The complete question text. Required, not blank, should end with "?"
- `header` - Short label for UI display. Required, max 12 characters
- `options` - List of the available [Options](https://github.com/spring-ai-community/spring-ai-agent-utils/blob/main/spring-ai-agent-utils/src/main/java/org/springaicommunity/agent/tools/AskUserQuestionTool.java#L118).
- `multiSelect` - Flag indicating if multiple selections are allowed. Defaults to false if null.

Each [Options](https://github.com/spring-ai-community/spring-ai-agent-utils/blob/main/spring-ai-agent-utils/src/main/java/org/springaicommunity/agent/tools/AskUserQuestionTool.java#L118) has:
- `label` - Display text (e.g., "React", "Vue", "Angular")
- `description` - Explanation of what this option means

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

### Answer Error Handling

The `questionAnswerFunction` should handle errors appropriately:

**Return Value Requirements:**
- Must return a non-null `Map<String, String>`
- Map keys should match the `question` text from each `Question` object
- Missing answers for questions may cause the AI agent to behave unexpectedly
- Empty strings are valid answers, but `null` values should be avoided

**Exception Handling:**
- If the function throws an exception, the tool execution will fail
- The AI agent may retry or handle the failure based on the Spring AI configuration
- For timeout scenarios (e.g., user doesn't respond), consider throwing a descriptive exception or implementing a retry mechanism

**Example with error handling:**
```java
AskUserQuestionTool tool = AskUserQuestionTool.builder()
    .questionAnswerFunction(questions -> {
        try {
            Map<String, String> answers = promptUser(questions);

            // Validate all questions have answers
            for (Question q : questions) {
                if (!answers.containsKey(q.question())) {
                    throw new IllegalStateException(
                        "Missing answer for question: " + q.question()
                    );
                }
            }

            return answers;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("User input interrupted", e);
        } catch (TimeoutException e) {
            throw new RuntimeException("User did not respond in time", e);
        }
    })
    .build();
```

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

## See Also

- [Claude Agent SDK - User Input](https://platform.claude.com/docs/en/agent-sdk/user-input#question-format)
- [Spring AI Documentation](https://docs.spring.io/spring-ai/reference/)
