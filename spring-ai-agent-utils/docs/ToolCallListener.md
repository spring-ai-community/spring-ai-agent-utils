# ToolCallListener - Observing Tool Invocations

`ToolCallListener` + `ToolCallListeners` let an application observe every tool invocation without hand-rolling a `ToolCallback` decorator: audit/event logs (e.g. `tool_use`/`tool_result` event pairs), SSE progress streaming to a UI, per-call metrics.

## Usage

```java
ToolCallListener listener = new ToolCallListener() {

    @Override
    public Object beforeCall(String toolName, String toolInput) {
        // Returned value is the correlation context for this invocation
        return eventLog.append("agent.tool_use", Map.of("name", toolName, "input", toolInput)).id();
    }

    @Override
    public void afterCall(Object context, String toolName, String toolInput, String result) {
        eventLog.append("agent.tool_result",
                Map.of("tool_use_id", context, "name", toolName, "content", result, "is_error", false));
    }

    @Override
    public String onError(Object context, String toolName, String toolInput, RuntimeException ex) {
        String message = "Tool '" + toolName + "' failed: " + ex.getMessage();
        eventLog.append("agent.tool_result",
                Map.of("tool_use_id", context, "name", toolName, "content", message, "is_error", true));
        return message;   // reported to the model so the loop can adapt; return null to rethrow
    }
};

List<ToolCallback> observed = ToolCallListeners.wrapAll(callbacks, listener);

ChatClient chatClient = chatClientBuilder.defaultTools(observed).build();
```

## Semantics

- **Correlation context** — `beforeCall` may return any opaque object; the decorator hands it back to `afterCall`/`onError` for the same invocation. Use it for use/result correlation ids or duration measurement (store a start timestamp). All listener methods have no-op defaults.
- **Error policy is yours** — `onError` returning a non-null string reports the failure to the model as the tool result (report-and-continue, letting the agent adapt); returning `null` propagates the exception (fail-fast).
- **Transparent decoration** — `getToolDefinition()`/`getToolMetadata()` are delegated untouched, so wrapping composes with `ToolCallbacks.from(...)`, `FunctionToolCallback`, the skills tool, and any other callback source.
- **Listener methods must not throw** — the decorator deliberately doesn't guard listener calls (observability failures stay loud instead of leaving silent audit gaps). A throwing `beforeCall` prevents the tool from running; a throwing `afterCall` discards a result whose side effects have already happened (and the model may retry the tool); a throwing `onError` replaces the original tool exception. Handle your own failures inside the listener.

## See also

- [InterruptAdvisor](InterruptAdvisor.md) — cancel the loop these calls run in
