# AskUserQuestionTool

A tool for asking users clarifying questions during AI agent execution. Enables AI agents to gather user preferences, clarify ambiguous instructions, and get decisions on implementation choices.

**Features:**
- Multiple-choice questions with 2-4 options per question
- Support for single-select and multi-select questions
- Free-text input support beyond predefined options
- 1-4 questions per interaction
- Immutable data structures with defensive copies
- Thread-safe implementation
- Custom function callbacks for UI integration

## Overview

The `AskUserQuestionTool` is a Spring AI implementation of [Claude Code's AskUserQuestion tool](https://platform.claude.com/docs/en/agent-sdk/user-input#question-format). It enables AI agents to:
- Request clarification when requirements are ambiguous
- Offer multiple approaches and let users choose
- Gather user preferences for subjective decisions
- Get approval or input on implementation choices

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

// The AI agent will call this tool automatically when it needs input
// For example, when asked: "Help me choose a database for my app"
```

## Question Format

Each question consists of:

| Field | Type | Description | Constraints |
|-------|------|-------------|-------------|
| `question` | String | The complete question text | Required, not blank, should end with "?" |
| `header` | String | Short label for UI display | Required, max 12 characters |
| `options` | List<Option> | Available choices | 2-4 options required |
| `multiSelect` | Boolean | Allow multiple selections | Defaults to false if null |

### Option Format

Each option has:
- `label` - Display text (e.g., "React", "Vue", "Angular")
- `description` - Explanation of what this option means

## Creating Questions

```java
// Single-select question
Question libraryChoice = new Question(
    "Which library should we use for date formatting?",
    "Library",
    List.of(
        new Option("Moment.js", "Popular but large library"),
        new Option("Day.js", "Lightweight Moment.js alternative"),
        new Option("date-fns", "Modular and tree-shakeable")
    ),
    false  // single-select
);

// Multi-select question
Question featureSelection = new Question(
    "Which features do you want to enable?",
    "Features",
    List.of(
        new Option("Authentication", "User login and registration"),
        new Option("Database", "PostgreSQL integration"),
        new Option("Caching", "Redis caching layer")
    ),
    true  // multi-select
);
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

## Validation Rules

The tool enforces strict validation:

### Questions List
- Must contain 1-4 questions
- Cannot be null or empty

### Question Fields
- `question` - Cannot be null or blank
- `header` - Cannot be null/blank, max 12 characters
- `options` - Must have 2-4 options
- `multiSelect` - Defaults to false if null

### Option Fields
- `label` - Cannot be null or blank
- `description` - Cannot be null or blank

**Validation Examples:**
```java
// ✅ Valid question
new Question(
    "Choose a framework?",
    "Framework",
    List.of(
        new Option("React", "Component-based UI"),
        new Option("Vue", "Progressive framework")
    ),
    false
);

// ❌ Invalid - header too long
new Question(
    "Choose a framework?",
    "FrameworkChoice",  // 15 chars > 12 max
    options,
    false
);

// ❌ Invalid - only 1 option
new Question(
    "Choose a framework?",
    "Framework",
    List.of(new Option("React", "Only option")),  // Need 2-4 options
    false
);

// ❌ Invalid - blank option label
new Option("", "Description");  // Label cannot be blank
```

## When to Use AskUserQuestionTool

### Use This Tool When:

1. **Multiple valid approaches exist** - Choice between frameworks, libraries, or patterns
2. **Subjective decisions required** - Styling, naming conventions, project structure
3. **Ambiguous requirements** - User request could be interpreted multiple ways
4. **Implementation preferences needed** - Testing strategy, error handling approach
5. **Configuration choices** - Deployment options, environment settings

### Skip This Tool When:

1. **Clear single solution** - Requirements are unambiguous
2. **Industry standard exists** - Follow established conventions
3. **Technical requirement** - One approach is technically superior
4. **User already specified** - Instructions are detailed enough

## Examples

### Example 1: Framework Selection

```java
// AI detects user needs help choosing a framework
Question frameworkQuestion = new Question(
    "Which frontend framework should we use?",
    "Framework",
    List.of(
        new Option("React (Recommended)", "Most popular, huge ecosystem"),
        new Option("Vue", "Gentle learning curve, progressive"),
        new Option("Svelte", "No virtual DOM, smaller bundles"),
        new Option("Angular", "Full-featured, enterprise-ready")
    ),
    false
);

// User selects: "React (Recommended)"
// AI continues with React setup
```

### Example 2: Multiple Configuration Choices

```java
// AI needs multiple decisions for project setup
List<Question> setupQuestions = List.of(
    new Question(
        "Which database do you prefer?",
        "Database",
        List.of(
            new Option("PostgreSQL", "Robust relational database"),
            new Option("MongoDB", "Flexible document database")
        ),
        false
    ),
    new Question(
        "Which features should we include?",
        "Features",
        List.of(
            new Option("Authentication", "User login system"),
            new Option("API Rate Limiting", "Protect against abuse"),
            new Option("Logging", "Application logs"),
            new Option("Caching", "Redis caching layer")
        ),
        true  // multi-select
    )
);

// User answers:
// - Database: "PostgreSQL"
// - Features: "Authentication, Logging, Caching"
```

### Example 3: Free-Text Input

```java
// User provides custom input not in options
Question customQuestion = new Question(
    "Which testing library do you prefer?",
    "Testing",
    List.of(
        new Option("Jest", "Popular JavaScript testing"),
        new Option("Vitest", "Fast Vite-native testing")
    ),
    false
);

// User types: "Playwright for E2E tests"
// AI adapts to use Playwright instead of predefined options
```

## Thread Safety

The `AskUserQuestionTool` class is thread-safe and can be used concurrently by multiple threads. However, the provided `questionAnswerFunction` must also be thread-safe if shared state is maintained.

**Thread-safe function example:**
```java
// Safe - no shared mutable state
AskUserQuestionTool tool = AskUserQuestionTool.builder()
    .questionAnswerFunction(questions -> {
        // Each invocation is independent
        Map<String, String> answers = promptUser(questions);
        return answers;
    })
    .build();
```

**Unsafe function example:**
```java
// Unsafe - shared mutable state without synchronization
private Map<String, String> sharedAnswers = new HashMap<>();

AskUserQuestionTool tool = AskUserQuestionTool.builder()
    .questionAnswerFunction(questions -> {
        // Multiple threads modifying sharedAnswers = data race
        sharedAnswers.put("lastQuestion", questions.get(0).question());
        return sharedAnswers;
    })
    .build();
```

## Immutability

All data structures are immutable with defensive copies:
- `options` list is copied on Question construction
- Returned collections cannot be modified

```java
List<Option> options = new ArrayList<>(List.of(
    new Option("A", "First"),
    new Option("B", "Second")
));
Question question = new Question("Choose?", "Choice", options, false);

// Modifying original list doesn't affect Question
options.clear();
assertThat(question.options()).hasSize(2);  // Still has 2 options

// Cannot modify returned collections
question.options().clear();  // Throws UnsupportedOperationException
```

## Integration with Chat Client

```java
@Configuration
public class AgentConfig {

    @Bean
    public AskUserQuestionTool askUserQuestionTool(QuestionHandlerService handler) {
        return AskUserQuestionTool.builder()
            .questionAnswerFunction(handler::handleQuestions)
            .build();
    }

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder,
                                  AskUserQuestionTool askUserQuestionTool) {
        return builder
            .defaultTools(askUserQuestionTool)
            .build();
    }
}
```

## API Reference

### AskUserQuestionTool

| Method | Description |
|--------|-------------|
| `builder()` | Create a new builder instance |
| `askUserQuestion(questions, answers)` | Tool method called by AI agent |

### Builder

| Method | Description |
|--------|-------------|
| `questionAnswerFunction(Function<List<Question>, Map<String, String>>)` | Set the function to handle questions |
| `build()` | Build the tool instance |

### Question

| Field | Type | Description |
|-------|------|-------------|
| `question` | `String` | The question text |
| `header` | `String` | Short label (max 12 chars) |
| `options` | `List<Option>` | Available choices (2-4) |
| `multiSelect` | `Boolean` | Allow multiple selections |

### Option

| Field | Type | Description |
|-------|------|-------------|
| `label` | `String` | Display text |
| `description` | `String` | Explanation of option |

## Best Practices

### 1. Clear Question Text
```java
// ✅ Good - specific and clear
"Which authentication method should we implement?"

// ❌ Bad - vague
"What about auth?"
```

### 2. Descriptive Options
```java
// ✅ Good - explains trade-offs
new Option("JWT", "Stateless, good for distributed systems")

// ❌ Bad - no context
new Option("JWT", "JWT tokens")
```

### 3. Concise Headers
```java
// ✅ Good - fits in 12 chars
"Auth Method"

// ❌ Bad - too long
"Authentication Method"
```

### 4. Logical Option Count
```java
// ✅ Good - reasonable choices
2-4 options per question

// ❌ Bad - too few or too many
1 option (no choice) or 5+ options (overwhelming)
```

### 5. Multi-Select for Non-Exclusive Choices
```java
// ✅ Good - features can be combined
new Question("Which features?", "Features", options, true)

// ✅ Good - only one can be chosen
new Question("Which database?", "Database", options, false)
```

## Error Handling

```java
try {
    String result = askTool.askUserQuestion(questions, null);
    // Result contains JSON with user answers
    logger.info("User responses: " + result);
} catch (IllegalArgumentException e) {
    // Validation error (null questions, wrong size, etc.)
    logger.error("Invalid questions: " + e.getMessage());
} catch (Exception e) {
    // Function execution error (timeout, user cancelled, etc.)
    logger.error("Failed to get user input: " + e.getMessage());
}
```

## Related Tools

- **[TodoWriteTool](TodoWriteTool.md)** - For tracking tasks and progress
- **[TaskTools](TaskTools.md)** - For delegating complex decisions to sub-agents

## See Also

- [Claude Agent SDK - User Input](https://platform.claude.com/docs/en/agent-sdk/user-input#question-format)
- [Spring AI Documentation](https://docs.spring.io/spring-ai/reference/)
