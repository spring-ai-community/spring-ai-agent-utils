# InterruptAdvisor - Cancelling In-Flight Turns

`InterruptAdvisor` adds cooperative cancellation to the agentic tool-calling loop. Once `ChatClient.call()` enters the loop, it normally runs to completion — a UI "stop" button, a session-level interrupt event, or a budget guard has no seam to abort it. This advisor is that seam.

## How it works

The advisor polls an application-supplied `BooleanSupplier` in its `before()` hook. Its default order (`HIGHEST_PRECEDENCE + 400`) places it just *inside* the auto-registered tool-calling advisor (`HIGHEST_PRECEDENCE + 300`), so the check re-runs on **every tool-call round**: an interrupt raised while tools execute lands before the next model request, not only between turns.

When the signal reports true, the turn unwinds with a `TurnInterruptedException` — the caller catches it and decides what an interrupted turn means (discard, partial save, quiet end-of-turn).

## Usage

```java
AtomicBoolean interrupted = new AtomicBoolean();

ChatClient chatClient = chatClientBuilder
    .defaultAdvisors(memoryAdvisor,
            InterruptAdvisor.builder().interruptSignal(interrupted::get).build())
    .defaultTools(tools)
    .build();

// From another thread (REST handler, UI event, watchdog):
interrupted.set(true);

// The worker loop:
try {
    String content = chatClient.prompt().user(text).call().content();
}
catch (TurnInterruptedException ex) {
    interrupted.set(false);      // re-arm for the next turn
    // e.g. end the turn quietly with stop_reason "end_turn"
}
```

The signal isn't limited to a flag — any cheap, thread-safe check works:

```java
// Wall-clock guard: cancel turns that run past a deadline
Instant deadline = Instant.now().plus(Duration.ofMinutes(5));
InterruptAdvisor.builder().interruptSignal(() -> Instant.now().isAfter(deadline)).build();
```

## Streaming

With `stream()` the check runs inside the reactive pipeline, so the `TurnInterruptedException` arrives as the Flux's **error signal** rather than a synchronous throw:

```java
chatClient.prompt().user(text).stream().content()
    .onErrorResume(TurnInterruptedException.class, ex -> Flux.empty())
    .subscribe(...);
```

## Configuration

| Builder option | Default | Notes |
|---|---|---|
| `interruptSignal(BooleanSupplier)` | required | Polled before each model request; must be cheap and thread-safe |
| `order(int)` | `HIGHEST_PRECEDENCE + 400` | Orders below the tool-calling advisor's `+300` check only once per turn |

## See also

- [ToolCallListener](ToolCallListener.md) — observe the tool calls of the same loop
