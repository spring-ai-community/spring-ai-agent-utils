# ToolApprovalAdvisor

`ToolApprovalAdvisor` adds a human-in-the-loop approval gate before tool execution.

## Features

- Blocks tool execution until `CompletableFuture` approval resolves.
- Supports approve, deny, and approve-with-rewritten-arguments flows.
- Supports optional approval timeout.

## Quick Start

```java
ToolApprovalAdvisor advisor = ToolApprovalAdvisor.builder()
    .approvalManager(request -> {
        // Integrate with your UI/workflow queue here
        return CompletableFuture.completedFuture(
            ToolApprovalAdvisor.ToolApprovalDecision.approve());
    })
    .build();
```

## Decision Types

- `ToolApprovalDecision.approve()`
- `ToolApprovalDecision.approveWithRewrite(String rewrittenArguments)`
- `ToolApprovalDecision.deny(String reason)`
