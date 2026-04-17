# ToolTimeoutAdvisor

`ToolTimeoutAdvisor` enforces timeout limits on tool execution.

## Features

- Global default timeout.
- Per-tool timeout overrides.
- Configurable timeout response payload.

## Quick Start

```java
ToolTimeoutAdvisor advisor = ToolTimeoutAdvisor.builder()
    .defaultTimeout(Duration.ofSeconds(30))
    .perToolTimeouts(Map.of("Shell", Duration.ofSeconds(10)))
    .build();
```
