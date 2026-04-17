# ToolCallPolicyAdvisor

`ToolCallPolicyAdvisor` is a Spring AI `ChatClient` advisor that lets you enforce policy on tool calls immediately before they execute.

It supports three policy actions:

- `ALLOW` - execute the tool call as-is.
- `DENY` - block the tool call and return a policy denial message to the model.
- `REWRITE` - replace tool arguments before executing the tool.

## Why use it

Use this advisor when your agent should not execute every model-proposed tool call blindly. Typical use cases:

- Allow-list and deny-list enforcement by tool name.
- Argument sanitization (strip unsafe fields, inject defaults).
- Guardrails for sensitive tools (shell, file write, network operations).
- Deterministic policy handling independent of model behavior.

## Quick Start

```java
ToolCallPolicyAdvisor advisor = ToolCallPolicyAdvisor.builder()
    .policy((toolName, arguments) -> {
        if ("Shell".equals(toolName)) {
            return ToolCallDecision.deny("Shell access is disabled in this environment");
        }
        return ToolCallDecision.allow();
    })
    .build();
```

Register it before `ToolCallingAdvisor`:

```java
ChatClient chatClient = ChatClient.builder(chatModel)
    .defaultAdvisors(
        ToolCallPolicyAdvisor.builder().build(),
        ToolCallAdvisor.builder().build())
    .build();
```

## Rewriting arguments

```java
ToolCallPolicyAdvisor advisor = ToolCallPolicyAdvisor.builder()
    .policy((toolName, arguments) -> {
        if ("EditFile".equals(toolName)) {
            String rewritten = "{\"path\":\"README.md\",\"content\":\"safe\"}";
            return ToolCallDecision.rewrite(rewritten, "Path restricted by policy");
        }
        return ToolCallDecision.allow();
    })
    .build();
```

## Builder Options

| Builder method | Type | Default | Description |
|---|---|---|---|
| `order(int)` | `int` | `HIGHEST_PRECEDENCE + 250` | Advisor order. Runs before default `ToolCallingAdvisor` (`+300`). |
| `policy(ToolCallPolicy)` | functional interface | allow all | Policy invoked per tool call with `(toolName, arguments)`. |
| `deniedCallResponse(BiFunction<String, ToolCallDecision, String>)` | function | built-in message | Custom response payload returned when policy action is `DENY`. |

## Decision API

Use `ToolCallDecision` helpers inside your policy:

- `ToolCallDecision.allow()`
- `ToolCallDecision.deny("reason")`
- `ToolCallDecision.rewrite(rewrittenArguments)`
- `ToolCallDecision.rewrite(rewrittenArguments, "reason")`

`ToolCallDecision.rewrite(...)` requires non-empty rewritten arguments.
