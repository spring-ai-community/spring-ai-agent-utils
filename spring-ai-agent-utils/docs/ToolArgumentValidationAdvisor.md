# ToolArgumentValidationAdvisor

`ToolArgumentValidationAdvisor` enforces argument payload size limits for every tool call.

## Features

- Validates UTF-8 argument size before tool execution.
- Blocks oversized payloads with configurable error messaging.

## Quick Start

```java
ToolArgumentValidationAdvisor advisor = ToolArgumentValidationAdvisor.builder()
    .maxArgumentBytes(65_536)
    .build();
```
