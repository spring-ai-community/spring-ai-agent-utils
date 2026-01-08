# TodoWriteTool

A structured task list management tool for AI coding sessions. Helps AI agents track progress, organize complex tasks, and provide visibility into task execution.

**Features:**
- Create and manage todo lists with pending, in_progress, and completed states
- Validates exactly one task is in_progress at a time
- Requires both imperative and active forms for clear task descriptions
- Real-time task state management during execution
- Custom consumer callbacks for UI integration

## Overview

The `TodoWriteTool` is a Spring AI implementation of Claude Code's TodoWrite tool. It enables AI agents to:
- Break down complex tasks into manageable steps
- Track progress systematically
- Demonstrate thoroughness to users
- Prevent missing requirements or steps

The tool enforces validation rules to ensure task lists remain organized and actionable.

## Basic Usage

```java
TodoWriteTool todoTool = TodoWriteTool.builder().build();

// Create a todo list
Todos todos = new Todos(List.of(
    new TodoItem("Read configuration file", Status.completed, "Reading configuration file"),
    new TodoItem("Parse settings", Status.in_progress, "Parsing settings"),
    new TodoItem("Validate configuration", Status.pending, "Validating configuration")
));

String result = todoTool.todoWrite(todos);
// Returns: "Todos have been modified successfully. Ensure that you continue to use the todo list..."
```

## Task States

Each task can be in one of three states:

| State | Description | Rules |
|-------|-------------|-------|
| `pending` | Task not yet started | Multiple tasks can be pending |
| `in_progress` | Currently working on | **Exactly ONE** task must be in_progress at a time |
| `completed` | Task finished successfully | Multiple tasks can be completed |

**Critical Rule:** Only ONE task can be `in_progress` at any given time. The tool validates this constraint.

## Task Structure

Each task requires two forms of description:

### Content (Imperative Form)
The "what needs to be done" - uses imperative mood (commands)

**Examples:**
- ✅ "Read configuration file"
- ✅ "Parse settings"
- ✅ "Run tests"
- ❌ "Reading file" (this is active form, not content)

### Active Form (Present Continuous)
The "what is happening" - uses present continuous tense

**Examples:**
- ✅ "Reading configuration file"
- ✅ "Parsing settings"
- ✅ "Running tests"
- ❌ "Read file" (this is imperative, not active form)

## When to Use TodoWriteTool

### Use This Tool When:

1. **Complex multi-step tasks** - Task requires 3+ distinct steps
2. **Non-trivial operations** - Careful planning or multiple operations needed
3. **User explicitly requests** - User asks for todo list tracking
4. **Multiple tasks provided** - User provides numbered or comma-separated list
5. **After receiving instructions** - Capture user requirements immediately
6. **Starting work** - Mark task as in_progress BEFORE beginning
7. **Completing work** - Mark completed and add follow-up tasks if needed

### Skip This Tool When:

1. **Single straightforward task** - Only one simple operation needed
2. **Trivial tasks** - Tracking provides no organizational benefit
3. **Less than 3 steps** - Can be completed in 1-2 trivial steps
4. **Conversational/informational** - No actual coding task to complete

## Examples

### Example 1: Multi-Step Feature Implementation

```java
// User: "Add a dark mode toggle to settings, then run tests"

Todos darkModeImplementation = new Todos(List.of(
    new TodoItem("Create dark mode toggle component", Status.in_progress,
                 "Creating dark mode toggle component"),
    new TodoItem("Add dark mode state management", Status.pending,
                 "Adding dark mode state management"),
    new TodoItem("Implement dark theme styles", Status.pending,
                 "Implementing dark theme styles"),
    new TodoItem("Update components for theme switching", Status.pending,
                 "Updating components for theme switching"),
    new TodoItem("Run tests and fix failures", Status.pending,
                 "Running tests and fixing failures")
));

todoTool.todoWrite(darkModeImplementation);
```

**Why use TodoWriteTool?**
- Multi-step feature (UI + state + styling + testing)
- User explicitly requested tests afterward
- Helps track progress through all implementation phases

### Example 2: Codebase-Wide Refactoring

```java
// User: "Rename getCwd to getCurrentWorkingDirectory across the project"
// After search: Found 15 instances across 8 files

Todos refactoring = new Todos(List.of(
    new TodoItem("Rename getCwd in src/utils/path.js", Status.completed,
                 "Renaming getCwd in src/utils/path.js"),
    new TodoItem("Rename getCwd in src/services/file.js", Status.in_progress,
                 "Renaming getCwd in src/services/file.js"),
    new TodoItem("Rename getCwd in src/components/FileExplorer.jsx", Status.pending,
                 "Renaming getCwd in src/components/FileExplorer.jsx"),
    // ... more files
    new TodoItem("Run tests to verify refactoring", Status.pending,
                 "Running tests to verify refactoring")
));

todoTool.todoWrite(refactoring);
```

**Why use TodoWriteTool?**
- Multiple files affected (8 files, 15 instances)
- Systematic tracking prevents missing occurrences
- Ensures code consistency across refactoring

### Example 3: Performance Optimization

```java
// User: "Optimize my React app, it's rendering slowly"
// After analysis: Identified 5 performance issues

Todos optimization = new Todos(List.of(
    new TodoItem("Implement memoization in ProductList", Status.completed,
                 "Implementing memoization in ProductList"),
    new TodoItem("Add virtualization to Dashboard lists", Status.completed,
                 "Adding virtualization to Dashboard lists"),
    new TodoItem("Optimize image loading in Gallery", Status.in_progress,
                 "Optimizing image loading in Gallery"),
    new TodoItem("Fix state update loops in ShoppingCart", Status.pending,
                 "Fixing state update loops in ShoppingCart"),
    new TodoItem("Review bundle size and code splitting", Status.pending,
                 "Reviewing bundle size and code splitting")
));

todoTool.todoWrite(optimization);
```

**Why use TodoWriteTool?**
- Multiple optimization opportunities identified
- Non-trivial task requiring analysis first
- Systematic approach ensures all bottlenecks addressed

## Task Management Best Practices

### 1. Update in Real-Time

Mark tasks as completed IMMEDIATELY after finishing, don't batch:

```java
// ❌ BAD: Batching completions
// Complete 3 tasks, then update all at once

// ✅ GOOD: Update after each completion
Todos step1 = new Todos(List.of(
    new TodoItem("Task 1", Status.completed, "Doing task 1"),
    new TodoItem("Task 2", Status.in_progress, "Doing task 2"),
    new TodoItem("Task 3", Status.pending, "Doing task 3")
));
todoTool.todoWrite(step1);

// After completing task 2
Todos step2 = new Todos(List.of(
    new TodoItem("Task 1", Status.completed, "Doing task 1"),
    new TodoItem("Task 2", Status.completed, "Doing task 2"),
    new TodoItem("Task 3", Status.in_progress, "Doing task 3")
));
todoTool.todoWrite(step2);
```

### 2. Only One Task In Progress

```java
// ❌ BAD: Multiple tasks in_progress
Todos invalid = new Todos(List.of(
    new TodoItem("Task 1", Status.in_progress, "Doing task 1"),
    new TodoItem("Task 2", Status.in_progress, "Doing task 2")
));
// Throws: IllegalArgumentException - Only ONE task can be in_progress

// ✅ GOOD: One in_progress, others pending
Todos valid = new Todos(List.of(
    new TodoItem("Task 1", Status.in_progress, "Doing task 1"),
    new TodoItem("Task 2", Status.pending, "Doing task 2")
));
```

### 3. Complete Before Starting New

```java
// ❌ BAD: Starting new without completing current
// Task 1 is in_progress, directly move to Task 2

// ✅ GOOD: Complete current, then start next
Todos complete = new Todos(List.of(
    new TodoItem("Task 1", Status.completed, "Doing task 1"),
    new TodoItem("Task 2", Status.in_progress, "Doing task 2")
));
```

### 4. Remove Irrelevant Tasks

```java
// User changes requirements - some tasks no longer needed
Todos updated = new Todos(List.of(
    new TodoItem("Task 1", Status.completed, "Doing task 1"),
    new TodoItem("Task 2", Status.in_progress, "Doing task 2")
    // Task 3 removed entirely - not just marked completed
));
```

## Task Completion Rules

### Only Mark Completed When FULLY Done

**Never mark completed if:**
- Tests are failing
- Implementation is partial
- Unresolved errors encountered
- Couldn't find necessary files/dependencies

```java
// ❌ BAD: Tests failed but marked completed
Todos invalid = new Todos(List.of(
    new TodoItem("Run tests", Status.completed, "Running tests")
));
// But 5 tests failed!

// ✅ GOOD: Keep in_progress if issues remain
Todos valid = new Todos(List.of(
    new TodoItem("Run tests and fix failures", Status.in_progress,
                 "Running tests and fixing failures")
));
// Continue fixing until all pass
```

### Handle Blockers

When blocked, create new task for resolution:

```java
// Original plan
Todos original = new Todos(List.of(
    new TodoItem("Install dependencies", Status.completed, "Installing dependencies"),
    new TodoItem("Run build", Status.in_progress, "Running build"),
    new TodoItem("Deploy", Status.pending, "Deploying")
));

// Build fails due to missing config
Todos blocked = new Todos(List.of(
    new TodoItem("Install dependencies", Status.completed, "Installing dependencies"),
    new TodoItem("Run build", Status.in_progress, "Running build"),
    new TodoItem("Create missing config file", Status.pending, "Creating missing config file"),
    new TodoItem("Deploy", Status.pending, "Deploying")
));
```

## Validation Rules

The tool validates the following constraints:

### 1. Exactly One In-Progress Task

```java
// Throws IllegalArgumentException
Todos invalid = new Todos(List.of(
    new TodoItem("Task 1", Status.in_progress, "Doing task 1"),
    new TodoItem("Task 2", Status.in_progress, "Doing task 2")
));
// Error: Only ONE task can be in_progress at a time. Found 2 in_progress tasks.
```

### 2. Non-Empty Content

```java
// Throws IllegalArgumentException
Todos invalid = new Todos(List.of(
    new TodoItem("", Status.pending, "Doing something")
));
// Error: Task at index 0 has empty or blank content.
```

### 3. Non-Empty Active Form

```java
// Throws IllegalArgumentException
Todos invalid = new Todos(List.of(
    new TodoItem("Do something", Status.pending, "")
));
// Error: Task at index 0 has empty or blank activeForm.
```

### 4. Valid Status

```java
// Throws IllegalArgumentException
Todos invalid = new Todos(List.of(
    new TodoItem("Do something", null, "Doing something")
));
// Error: Task at index 0 has null status.
```

## Custom Consumer Integration

Provide custom consumer for UI integration or logging:

```java
// Custom consumer for UI updates
TodoWriteTool todoTool = TodoWriteTool.builder()
    .todoListConsumer(todos -> {
        System.out.println("=== Updated Todo List ===");
        for (int i = 0; i < todos.todos().size(); i++) {
            var item = todos.todos().get(i);
            String status = switch(item.status()) {
                case pending -> "[ ]";
                case in_progress -> "[→]";
                case completed -> "[✓]";
            };
            System.out.println(status + " " + item.content());
        }
    })
    .build();

// Use the tool
todoTool.todoWrite(new Todos(List.of(
    new TodoItem("First task", Status.completed, "Doing first task"),
    new TodoItem("Second task", Status.in_progress, "Doing second task"),
    new TodoItem("Third task", Status.pending, "Doing third task")
)));

// Output:
// === Updated Todo List ===
// [✓] First task
// [→] Second task
// [ ] Third task
```

## Spring Boot Integration

### Basic Configuration

```java
@Configuration
public class ToolsConfig {

    @Bean
    public TodoWriteTool todoWriteTool() {
        return TodoWriteTool.builder().build();
    }
}
```

### With Custom Consumer

```java
@Configuration
public class ToolsConfig {

    @Bean
    public TodoWriteTool todoWriteTool(TodoProgressService progressService) {
        return TodoWriteTool.builder()
            .todoListConsumer(todos -> {
                progressService.updateProgress(todos);
                logger.info("Todo list updated: {} tasks", todos.todos().size());
            })
            .build();
    }
}
```

### ChatClient Integration

```java
ChatClient chatClient = chatClientBuilder
    .defaultTools(TodoWriteTool.builder()
        .todoListConsumer(todos -> {
            // Custom handling of todo updates
            eventPublisher.publishEvent(new TodoUpdatedEvent(todos));
        })
        .build())
    .build();

String response = chatClient.prompt()
    .user("Implement user authentication with email verification")
    .call()
    .content();
// AI automatically uses TodoWriteTool to track implementation steps
```

## Advanced Examples

### Dynamic Task Addition

```java
// Initial plan
Todos initial = new Todos(List.of(
    new TodoItem("Analyze codebase", Status.completed, "Analyzing codebase"),
    new TodoItem("Identify issues", Status.in_progress, "Identifying issues")
));
todoTool.todoWrite(initial);

// After analysis, discovered 3 issues - add new tasks
Todos expanded = new Todos(List.of(
    new TodoItem("Analyze codebase", Status.completed, "Analyzing codebase"),
    new TodoItem("Identify issues", Status.completed, "Identifying issues"),
    new TodoItem("Fix memory leak in UserService", Status.in_progress,
                 "Fixing memory leak in UserService"),
    new TodoItem("Optimize database queries in OrderRepository", Status.pending,
                 "Optimizing database queries in OrderRepository"),
    new TodoItem("Remove unused dependencies", Status.pending,
                 "Removing unused dependencies")
));
todoTool.todoWrite(expanded);
```

### Nested Task Breakdown

```java
// High-level tasks first
Todos highlevel = new Todos(List.of(
    new TodoItem("Set up authentication system", Status.in_progress,
                 "Setting up authentication system"),
    new TodoItem("Implement authorization", Status.pending,
                 "Implementing authorization"),
    new TodoItem("Add session management", Status.pending,
                 "Adding session management")
));
todoTool.todoWrite(highlevel);

// Break down current task into subtasks
Todos detailed = new Todos(List.of(
    new TodoItem("Create User entity with password hashing", Status.completed,
                 "Creating User entity with password hashing"),
    new TodoItem("Implement login endpoint", Status.in_progress,
                 "Implementing login endpoint"),
    new TodoItem("Add JWT token generation", Status.pending,
                 "Adding JWT token generation"),
    new TodoItem("Create authentication filter", Status.pending,
                 "Creating authentication filter"),
    new TodoItem("Implement authorization", Status.pending,
                 "Implementing authorization"),
    new TodoItem("Add session management", Status.pending,
                 "Adding session management")
));
todoTool.todoWrite(detailed);
```

## Best Practices Summary

1. **Use for complexity**: 3+ steps, multi-file changes, or complex operations
2. **Skip for simplicity**: Single straightforward tasks or informational queries
3. **Two forms required**: Imperative content + present continuous activeForm
4. **One in progress**: Always exactly ONE task in_progress at a time
5. **Update immediately**: Mark completed right after finishing, not batched
6. **Complete fully**: Only mark completed when 100% done (tests pass, no errors)
7. **Handle blockers**: Add new tasks for discovered issues instead of abandoning
8. **Remove irrelevant**: Delete tasks that are no longer needed
9. **Break down**: Split large tasks into specific, actionable subtasks
10. **Be specific**: Clear, descriptive task names with measurable outcomes

## Common Mistakes

### ❌ Batch Completions
```java
// Complete tasks 1, 2, 3, then update all at once
// This reduces visibility into progress
```

### ❌ Multiple In-Progress
```java
new Todos(List.of(
    new TodoItem("Task 1", Status.in_progress, "..."),
    new TodoItem("Task 2", Status.in_progress, "...")  // ERROR
));
```

### ❌ Wrong Tense Forms
```java
new TodoItem("Running tests", "Run tests")  // Backwards!
// Should be: ("Run tests", "Running tests")
```

### ❌ Marking Incomplete Work
```java
new TodoItem("Run tests", Status.completed, "...")
// But 5 tests failed! Should be in_progress
```

### ❌ Using for Trivial Tasks
```java
// User: "Add a comment to this function"
new Todos(List.of(
    new TodoItem("Add comment", Status.in_progress, "Adding comment")
));
// Overkill - just add the comment directly
```

## See Also

- [FileSystemTools](FileSystemTools.md) - For file operations
- [ShellTools](ShellTools.md) - For shell command execution
- [SmartWebFetchTool](SmartWebFetchTool.md) - For web content fetching
