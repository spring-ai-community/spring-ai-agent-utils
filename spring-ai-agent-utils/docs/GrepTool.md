# GrepTool

A pure Java implementation of grep functionality that doesn't require external ripgrep installation. Provides powerful search capabilities with regex patterns, glob filtering, and multiple output modes.

**Features:**
- Full Java regex support for pattern matching
- File type filtering (java, js, ts, py, rust, go, etc.)
- Glob pattern matching (`*.java`, `**/*.tsx`)
- Multiple output modes: `files_with_matches`, `count`, `content`
- Context lines (before/after matching lines)
- Case-insensitive search
- Multiline pattern matching
- Configurable limits and depth

## Basic Usage

```java
// Default configuration
GrepTool grepTool = new GrepTool();

// Custom configuration
GrepTool customGrepTool = new GrepTool(
    200000,  // maxOutputLength - Maximum output before truncation (default: 100000)
    50,      // maxDepth - Directory traversal depth (default: 100)
    5000     // maxLineLength - Max line length to process (default: 10000)
);
```

## Configuration Parameters

| Parameter | Default | Description |
|-----------|---------|-------------|
| `maxOutputLength` | 100000 | Maximum output length before truncation |
| `maxDepth` | 100 | Maximum directory traversal depth (prevents infinite recursion) |
| `maxLineLength` | 10000 | Maximum line length to process (longer lines are skipped) |

## Search Examples

```java
// Search for pattern in current directory
String result = grepTool.grep("TODO", null, null, null, null, null, null, null, null, null, null, null, null);

// Search Java files only
String result = grepTool.grep(
    "public class.*",        // pattern
    "./src",                  // path
    null,                     // glob
    OutputMode.files_with_matches,  // outputMode
    null, null, null, null,  // context options
    null,                     // caseInsensitive
    "java",                   // type
    null, null, null          // limit options
);

// Search with glob pattern and show content
String result = grepTool.grep(
    "Error|Exception",        // pattern
    ".",                      // path
    "**/*.log",              // glob
    OutputMode.content,       // outputMode
    2, null, null,           // 2 lines before context
    true,                     // showLineNumbers
    true,                     // caseInsensitive
    null,                     // type
    100, null, null          // limit to 100 lines
);
```

## Output Modes

- `files_with_matches` (default) - Shows only file paths containing matches
- `count` - Shows match count per file
- `content` - Shows matching lines with optional context and line numbers
