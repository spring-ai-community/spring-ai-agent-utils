# DiffTool

Pure Java unified-diff tool for comparing two text blocks or two files on disk. Uses Eugene W. Myers' O(ND) difference algorithm and emits output compatible with the `patch` utility and `git apply`.

**Features:**
- Unified-diff output with configurable surrounding context
- String-to-string and file-to-file comparison
- Whitespace-insensitive modes (`STRICT`, `IGNORE_TRAILING`, `IGNORE_ALL`)
- Lightweight summary mode returning insertion, deletion, and hunk counts
- Correct handling of missing trailing newlines (`\ No newline at end of file`)
- Zero runtime dependencies beyond Spring AI core and the JDK; stateless and thread-safe

## Available Tools

### 1. diff - Compare two strings

The primary tool for comparing two text blocks and producing a unified diff.

**Parameters:**
- `before` (required) - Original text (the 'before' side)
- `after` (required) - Modified text (the 'after' side)
- `beforeLabel` (optional) - Label emitted on the `---` header line (default: `before`)
- `afterLabel` (optional) - Label emitted on the `+++` header line (default: `after`)
- `contextLines` (optional) - Unchanged context lines around each change (default: 3, max: 10)
- `whitespaceMode` (optional) - Whitespace handling: `STRICT`, `IGNORE_TRAILING`, or `IGNORE_ALL` (default: `STRICT`)

**Basic Usage:**

```java
DiffTool diffTool = DiffTool.builder().build();

// Basic comparison
String result = diffTool.diff(
    "hello\nworld\n",   // before
    "hello\njava\n",    // after
    null,               // beforeLabel (uses "before")
    null,               // afterLabel (uses "after")
    null,               // contextLines (default 3)
    null                // whitespaceMode (default STRICT)
);

// With custom labels
String result = diffTool.diff(
    originalConfig,
    updatedConfig,
    "a/config.yml",
    "b/config.yml",
    null,
    null
);

// Whitespace-insensitive comparison
String result = diffTool.diff(
    before,
    after,
    null,
    null,
    5,                              // 5 context lines
    DiffTool.WhitespaceMode.IGNORE_ALL
);
```

**Output Format:**
```
--- a/config.yml
+++ b/config.yml
@@ -1,3 +1,3 @@
 hello
-world
+java
```

**Important Notes:**
- Output follows the standard unified-diff format and is compatible with `patch` and `git apply`
- Returns an empty string when inputs are equivalent under the selected whitespace mode
- Output uses the line terminator dominant in `before` (or in `after` when `before` has no lines)
- Appends `\ No newline at end of file` marker when either side is missing a trailing newline
- `contextLines` is clamped to the range `[0, 10]`

### 2. DiffFiles - Compare two files

Produces a unified diff between the current contents of two files on disk.

**Parameters:**
- `beforePath` (required) - Path to the original file
- `afterPath` (required) - Path to the modified file
- `contextLines` (optional) - Unchanged context lines around each change (default: 3, max: 10)
- `whitespaceMode` (optional) - Whitespace handling: `STRICT`, `IGNORE_TRAILING`, or `IGNORE_ALL` (default: `STRICT`)

**Usage:**

```java
DiffTool diffTool = DiffTool.builder().build();

// Compare two files
String result = diffTool.diffFiles(
    "src/main/resources/application.yml",    // beforePath
    "src/main/resources/application-new.yml", // afterPath
    null,                                     // contextLines (default 3)
    null                                      // whitespaceMode (default STRICT)
);

// With extra context and whitespace-insensitive
String result = diffTool.diffFiles(
    "old.txt",
    "new.txt",
    7,
    DiffTool.WhitespaceMode.IGNORE_TRAILING
);
```

**Output Format:**
```
--- a/src/main/resources/application.yml
+++ b/src/main/resources/application-new.yml
@@ -10,7 +10,7 @@
 spring:
   datasource:
-    url: jdbc:h2:mem:test
+    url: jdbc:postgresql://localhost/app
```

**Key Features:**
- **Automatic labels**: Header lines use `a/<beforePath>` and `b/<afterPath>`
- **Path resolution**: Relative paths are resolved against the JVM working directory
- **Error handling**: Returns `Error reading files: <message>` if either file cannot be read

**Important Notes:**
- Both paths must be non-blank
- Files are read with the platform default charset via `Files.readString`
- Uses the same diff engine as the `diff` tool

### 3. DiffSummarize - Aggregate change counts

Returns aggregate counts without emitting the full unified-diff body.

**Parameters:**
- `before` (required) - Original text (the 'before' side)
- `after` (required) - Modified text (the 'after' side)
- `contextLines` (optional) - Unchanged context lines used when grouping hunks (default: 3, max: 10)
- `whitespaceMode` (optional) - Whitespace handling: `STRICT`, `IGNORE_TRAILING`, or `IGNORE_ALL` (default: `STRICT`)

**Usage:**

```java
DiffTool diffTool = DiffTool.builder().build();

// Get change counts for two strings
DiffTool.DiffSummary summary = diffTool.summarize(
    before,
    after,
    null,
    null
);

System.out.println("Insertions: " + summary.insertions());
System.out.println("Deletions:  " + summary.deletions());
System.out.println("Hunks:      " + summary.hunks());
```

**Output Format:**
```
DiffSummary[insertions=4, deletions=2, hunks=1]
```

**Key Features:**
- **Lightweight**: Skips unified-diff rendering — only the edit script and hunk grouping are computed
- **Same engine**: Counts match exactly what the `diff` tool would emit
- **Useful for gating**: Cheaply check whether changes exist or exceed a threshold

**Example Workflow:**

```java
DiffTool diffTool = DiffTool.builder().build();

// 1. Summarize first to decide whether to render
DiffTool.DiffSummary summary = diffTool.summarize(before, after, null, null);

if (summary.insertions() == 0 && summary.deletions() == 0) {
    System.out.println("No changes.");
    return;
}

// 2. Render the full diff only when needed
String unified = diffTool.diff(before, after, "a/file", "b/file", null, null);
System.out.println(unified);
```

**Whitespace Modes:**

| Mode | Behavior |
|------|----------|
| `STRICT` | Compare lines byte-for-byte |
| `IGNORE_TRAILING` | Ignore trailing whitespace (see `String#stripTrailing()`) |
| `IGNORE_ALL` | Collapse all whitespace runs to a single space, then trim |

**Configuration & Limits:**

| Setting | Default | Maximum | Description |
|---------|---------|---------|-------------|
| `contextLines` | 3 | 10 | Unchanged lines shown around each change region |
| `whitespaceMode` | `STRICT` | — | Line-equality policy |

**Best Practices:**

1. **Use git-style labels**: Emit `a/<path>` and `b/<path>` for easy consumption by `git apply`
   ```java
   diffTool.diff(before, after, "a/pom.xml", "b/pom.xml", null, null);
   ```

2. **Summarize before rendering**: Skip the full diff when there are no changes
   ```java
   if (diffTool.summarize(before, after, null, null).hunks() == 0) return;
   ```

3. **Tune context for readability**: Increase `contextLines` for reviews, keep the default for patches
   ```java
   diffTool.diff(before, after, null, null, 7, null);
   ```

4. **Choose the right whitespace mode**: Use `IGNORE_TRAILING` for cross-platform text files; reserve `IGNORE_ALL` for formatting-only diffs
   ```java
   diffTool.diff(before, after, null, null, null, DiffTool.WhitespaceMode.IGNORE_TRAILING);
   ```

5. **Prefer `diffFiles` for disk content**: Avoids manual `Files.readString` and produces git-style labels automatically
   ```java
   diffTool.diffFiles("old.yml", "new.yml", null, null);
   ```