# Spring AI Agent Utils Test

Test fixtures for building and testing agent loops on top of [Spring AI Agent Utils](../spring-ai-agent-utils/README.md).

This module is a test dependency, not a runtime dependency: add it with `<scope>test</scope>` so it never lands on your application's runtime classpath.

## ScriptedChatModel

A scriptable `ChatModel` that replays a fixed sequence of assistant turns — text responses and tool-call requests — in order, so you can test a tool-calling agent loop without a real LLM.

Building a `ChatModel` test double for tool-calling flows is trap-prone: a stub that returns a plain `ChatOptions` from `getOptions()` produces an agent loop that looks correctly wired but silently never executes a tool, because `ToolCallingAdvisor` only runs tool execution when the model's options are `ToolCallingChatOptions`. `ScriptedChatModel` gets this right by construction.

```java
ChatModel model = ScriptedChatModel.builder()
    .toolCallTurn("Bash", "{\"command\":\"ls\"}")
    .textTurn("Done - the directory contains ...")
    .build();

String result = ChatClient.builder(model)
    .build()
    .prompt()
    .user("list the directory")
    .toolCallbacks(bashTool)
    .call()
    .content();
```

`ScriptedChatModel` also records every `Prompt` it receives (`getReceivedPrompts()`), useful for asserting on conversation state built up across a multi-turn tool-calling loop, and throws `IllegalStateException` if more turns are requested than were scripted.

## Installation

**Maven:**
```xml
<dependency>
    <groupId>org.springaicommunity</groupId>
    <artifactId>spring-ai-agent-utils-test</artifactId>
    <version>0.14.0-SNAPSHOT</version>
    <scope>test</scope>
</dependency>
```

## Requirements

- Java 17+
- Spring AI 2.0.0

## License

Apache License 2.0
