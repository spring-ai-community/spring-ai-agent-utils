# ToolRateLimitAdvisor

`ToolRateLimitAdvisor` limits how many tool calls can be executed in a single request loop.

## Features

- Shared per-request tool call counter across all tools.
- Configurable max calls per request.
- Configurable rate-limit response payload.

## Quick Start

```java
ToolRateLimitAdvisor advisor = ToolRateLimitAdvisor.builder()
    .maxToolCallsPerRequest(10)
    .build();
```
