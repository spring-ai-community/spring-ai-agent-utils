# Migration Guide: 0.7.x to 0.9.0

This release upgrades the Spring AI dependency from `2.0.0-M7` to `2.0.0-RC1` and Spring Framework from `7.0.1` to `7.0.7`. Several Spring AI APIs changed in ways that require updates to application code.

## Dependency Version

```xml
<dependency>
    <groupId>org.springaicommunity</groupId>
    <artifactId>spring-ai-agent-utils</artifactId>
    <version>0.9.0</version>
</dependency>
```

## Spring AI API Changes

### `ToolCallAdvisor` removed

`ToolCallAdvisor` is deprecated and no longer needs to be registered. Tool calling is now handled automatically by the framework when tools are present on the `ChatClient`.

**Before:**

```java
chatClientBuilder.defaultAdvisors(
    ToolCallAdvisor.builder().build(),
    MessageChatMemoryAdvisor.builder(...).build());
```

**After:**

```java
chatClientBuilder.defaultAdvisors(
    MessageChatMemoryAdvisor.builder(...).build());
```

If you were using `disableInternalConversationHistory()` or `conversationHistoryEnabled(false)`, that behaviour is now controlled by advisor ordering — see the [Memory advisor ordering](#memory-advisor-ordering) section below.

### `defaultToolCallbacks()` → `defaultTools()`

`ChatClient.Builder.defaultToolCallbacks()` is deprecated. Use `defaultTools()` instead. It accepts the same types: individual `ToolCallback` objects, `ToolCallbackProvider` instances, and annotated POJO classes.

**Before:**

```java
chatClientBuilder
    .defaultToolCallbacks(SkillsTool.builder().build())
    .defaultToolCallbacks(mcpToolCallbackProvider);
```

**After:**

```java
chatClientBuilder
    .defaultTools(SkillsTool.builder().build())
    .defaultTools(mcpToolCallbackProvider);
```

### OpenAI starter artifact renamed

The OpenAI SDK starter artifact has been renamed:

| Before | After |
|--------|-------|
| `spring-ai-starter-model-openai-sdk` | `spring-ai-starter-model-openai` |

```xml
<!-- After -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-openai</artifactId>
</dependency>
```

### OpenAI property prefix renamed

All `spring.ai.openai-sdk.*` properties are renamed to `spring.ai.openai.*`:

| Before | After |
|--------|-------|
| `spring.ai.openai-sdk.api-key` | `spring.ai.openai.api-key` |
| `spring.ai.openai-sdk.chat.options.model` | `spring.ai.openai.chat.options.model` |
| `spring.ai.openai-sdk.chat.options.temperature` | `spring.ai.openai.chat.options.temperature` |

### `ModelOptionsUtils.toJsonString()` removed

If you reference `ModelOptionsUtils.toJsonString()` in custom advisor or tool code, replace it with `JsonHelper`:

**Before:**

```java
import org.springframework.ai.model.ModelOptionsUtils;

String json = ModelOptionsUtils.toJsonString(object);
```

**After:**

```java
import org.springframework.ai.util.JsonHelper;

String json = new JsonHelper().toJson(object);
```

### `ToolCallingChatOptions.copy()` removed

`copy()` has been removed from the `ToolCallingChatOptions` interface. Use `mutate().build()` instead:

**Before:**

```java
ToolCallingChatOptions copy = toolOptions.copy();
```

**After:**

```java
ToolCallingChatOptions copy = toolOptions.mutate().build();
```

### `DefaultToolCallingChatOptions` public constructor removed

The no-arg public constructor is no longer available. Use the builder:

**Before:**

```java
new DefaultToolCallingChatOptions()
```

**After:**

```java
DefaultToolCallingChatOptions.builder().build()
```

## Memory Advisor Ordering

`MessageChatMemoryAdvisor` must be placed **outside** (before) the tool-calling loop so that memory is only read/written once per user turn rather than on every tool-call iteration. The advisor's default order (`Advisor.DEFAULT_CHAT_MEMORY_PRECEDENCE_ORDER = HIGHEST_PRECEDENCE + 200`) already satisfies this — do not override it with a higher value.

**Incorrect** (places memory inside the tool-call loop):

```java
MessageChatMemoryAdvisor.builder(memory)
    .order(Ordered.HIGHEST_PRECEDENCE + 1000)  // wrong — higher value = inner = inside tool loop
    .build()
```

**Correct** (use the default order):

```java
MessageChatMemoryAdvisor.builder(memory).build()
```

The auto-configured `ToolCallingAdvisor` sits at `HIGHEST_PRECEDENCE + 300`. Memory at the default `HIGHEST_PRECEDENCE + 200` is numerically lower and therefore outermost, wrapping all tool calls in a single memory read/write cycle.
