# ToolFilterAdvisor

`ToolFilterAdvisor` filters available tool callbacks by include/exclude glob patterns.

## Features

- Include patterns (allow-list) for tool names.
- Exclude patterns (deny-list) for tool names.
- Uses simple wildcard matching (`*`, `?`).

## Quick Start

```java
ToolFilterAdvisor advisor = ToolFilterAdvisor.builder()
    .includePatterns(List.of("*File", "Web*"))
    .excludePatterns(List.of("Write*"))
    .build();
```
