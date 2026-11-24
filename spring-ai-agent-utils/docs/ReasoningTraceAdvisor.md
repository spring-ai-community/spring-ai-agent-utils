# ReasoningTraceAdvisor

`ReasoningTraceAdvisor` emits per-iteration observability traces with timing data.

## Features

- Captures per-iteration duration.
- Captures user input and assistant output excerpts.
- Captures tool calls returned by the model.
- Sends traces to a pluggable sink (`Consumer<ReasoningTraceEntry>`).

## Quick Start

```java
ReasoningTraceAdvisor advisor = ReasoningTraceAdvisor.builder()
    .traceSink(trace -> logger.info("{}", trace))
    .includeToolArguments(false)
    .maxTextLength(2000)
    .build();
```
