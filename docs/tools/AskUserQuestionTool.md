# AskUserQuestionTool

A tool for asking users clarifying questions during AI agent execution. Enables AI agents to gather user preferences, clarify ambiguous instructions, and get decisions on implementation choices.

**Features:**
- Multiple-choice questions with 2-4 options per question
- Support for single-select and multi-select questions
- Free-text input support beyond predefined options
- 1-10 questions per interaction
- Custom `QuestionHandler` callbacks for UI integration
- Optional answer validation with configurable behavior
- Optional heartbeat while waiting for human input, to keep interactive events from stalling in proxy buffers
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

## Keeping the Connection Alive (Heartbeats)

The blocking pattern above has a nasty failure mode in real deployments: SSE responses are frequently buffered by CDNs and reverse proxies (nginx without `X-Accel-Buffering: no`, Cloudflare, AWS ALB, ...). If the frame carrying the interactive event sits in a proxy buffer:

- the client never receives the event and never learns the request id,
- the client therefore never submits an answer,
- the future blocks until its timeout — from the user's perspective the turn is **deadlocked**.

The established mitigation is to publish a heartbeat immediately after the interactive event, plus periodic heartbeats while the future is pending, which push the buffered frames through the proxy. The `heartbeat()` builder option makes that a framework concern instead of application boilerplate:

```java
@RestController
public class WebQuestionHandler {

    private final SseEmitter emitter = ...;

    public AskUserQuestionTool createTool() {
        return AskUserQuestionTool.builder()
            .questionHandler(this::handleQuestions)
            .heartbeat(Duration.ofSeconds(15), () -> {
                try {
                    this.emitter.send(SseEmitter.event().comment("heartbeat"));
                }
                catch (IOException ex) {
                    // transport is gone; there is nothing left to flush
                }
            })
            .build();
    }

    // handleQuestions / submitAnswers stay exactly as before
}
```

The heartbeat is **transport-agnostic**: the framework only invokes the callback; the application decides what to do with the signal — flush an SSE comment frame, send a WebSocket ping, emit a metric, write a log line.

Semantics:

- One beat fires immediately when the wait begins, then one every `interval` until the handler returns.
- Beats are emitted on a dedicated daemon thread, so a slow beat callback can never block the wait, and a pending wait never holds back JVM shutdown.
- The handler's result — or exception — passes through untouched, and beats stop as soon as it completes, successfully or not.
- A failing periodic beat is logged and heartbeats continue — a broken beat transport is exactly the condition heartbeats exist to surface. The immediate beat runs on the calling thread, and its exceptions propagate.

> **Tip:** the immediate beat fires just before the handler is invoked, so an event published *inside* the handler is flushed by the next periodic beat. If you need zero added latency, invoke the beat callback yourself right after publishing.

## Demo Application

See the [ask-user-question-demo](https://github.com/spring-ai-community/spring-ai-agent-utils/tree/main/examples/ask-user-question-demo) for a complete working example of a console-based AI chat application using the AskUserQuestionTool with `CommandLineQuestionHandler`. The demo shows how to:
- Use the provided `CommandLineQuestionHandler` for console interaction
- Handle both single-select and multi-select questions
- Configure the tool with answer validation options

## See Also

- [Claude Agent SDK - User Input](https://platform.claude.com/docs/en/agent-sdk/user-input#question-format)
- [Spring AI Documentation](https://docs.spring.io/spring-ai/reference/)
