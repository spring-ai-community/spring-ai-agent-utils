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
- No external dependencies required
- Thread-safe for concurrent use

## Overview

The `GrepTool` is a Spring AI tool that brings powerful code search capabilities to AI agents. Unlike traditional grep that requires external tools, this is a pure Java implementation that works cross-platform without any setup.

**Why use GrepTool?**
- **Zero dependencies**: No need to install ripgrep or grep
- **AI-optimized**: Designed for agents to search codebases efficiently
- **Flexible filtering**: Combine regex, globs, and file types
- **Smart output**: Three modes optimized for different use cases

## Basic Usage

```java
// Default configuration
GrepTool grepTool = new GrepTool();

// Search for pattern in current directory
String result = grepTool.grep(
    "TODO",              // pattern
    null,                // path (uses current directory)
    null,                // glob
    OutputMode.files_with_matches,  // outputMode
    null, null, null,    // context lines (before, after, surrounding)
    null,                // showLineNumbers
    null,                // caseInsensitive
    null,                // type
    null, null, null     // headLimit, offset, multiline
);
```

### Custom Configuration

For large codebases or specific requirements, configure limits:

```java
GrepTool customGrepTool = new GrepTool(
    200000,  // maxOutputLength - Maximum output before truncation (default: 100000)
    50,      // maxDepth - Directory traversal depth (default: 100)
    5000     // maxLineLength - Max line length to process (default: 10000)
);
```

## Configuration Parameters

| Parameter | Default | Description |
|-----------|---------|-------------|
| `maxOutputLength` | 100,000 | Maximum output length before truncation |
| `maxDepth` | 100 | Maximum directory traversal depth (prevents infinite recursion) |
| `maxLineLength` | 10,000 | Maximum line length to process (longer lines are skipped) |

**When to adjust:**
- **maxOutputLength**: Increase for large search results (e.g., 200,000 for monorepos)
- **maxDepth**: Decrease for shallow searches (e.g., 10 for root-level only)
- **maxLineLength**: Increase for minified code or long lines (e.g., 50,000)

## Method Parameters

The `grep` method signature:

```java
String grep(
    String pattern,          // Required: Regex pattern to search
    String path,             // Optional: Directory/file to search (default: current dir)
    String glob,             // Optional: Glob pattern to filter files (e.g., "*.java")
    OutputMode outputMode,   // Optional: Output format (default: files_with_matches)
    Integer linesBefore,     // Optional: Context lines before match (-B)
    Integer linesAfter,      // Optional: Context lines after match (-A)
    Integer linesSurrounding,// Optional: Context lines before+after (-C)
    Boolean showLineNumbers, // Optional: Show line numbers in content mode (-n)
    Boolean caseInsensitive, // Optional: Case-insensitive search (-i)
    String type,             // Optional: File type filter (e.g., "java", "js")
    Integer headLimit,       // Optional: Limit output lines/entries
    Integer offset,          // Optional: Skip first N lines/entries
    Boolean multiline        // Optional: Enable multiline matching
)
```

## Output Modes

### 1. files_with_matches (Default)

Returns only file paths containing matches. Most efficient for finding files.

**Usage:**
```java
String result = grepTool.grep(
    "class UserService",
    "./src",
    null,
    OutputMode.files_with_matches,
    null, null, null, null, null, null, null, null, null
);
```

**Output:**
```
./src/main/java/com/example/UserService.java
./src/test/java/com/example/UserServiceTest.java
```

**Use when:**
- Finding which files contain a pattern
- Locating specific classes or functions
- Quick file discovery

### 2. count

Shows the number of matches per file.

**Usage:**
```java
String result = grepTool.grep(
    "TODO",
    "./src",
    null,
    OutputMode.count,
    null, null, null, null, null, null, null, null, null
);
```

**Output:**
```
./src/main/java/Controller.java: 5
./src/main/java/Service.java: 12
./src/main/java/Repository.java: 3
```

**Use when:**
- Counting occurrences (TODOs, FIXMEs, deprecations)
- Analyzing code patterns across files
- Finding files with most matches

### 3. content

Shows matching lines with optional context and line numbers.

**Usage:**
```java
String result = grepTool.grep(
    "public class.*",
    "./src",
    null,
    OutputMode.content,
    null, null, null,
    true,                // Show line numbers
    null, null, null, null, null
);
```

**Output:**
```
./src/main/java/UserService.java:
   15→public class UserService {

./src/main/java/OrderService.java:
   23→public class OrderService extends BaseService {
```

**Use when:**
- Examining actual matching code
- Understanding context around matches
- Code review or analysis

## Context Lines

Show surrounding lines for better context (similar to grep's -A, -B, -C options).

### Lines After Match (-A)

```java
String result = grepTool.grep(
    "@Override",
    "./src",
    null,
    OutputMode.content,
    null,                // linesBefore
    3,                   // linesAfter - show 3 lines after
    null,                // linesSurrounding
    true,                // showLineNumbers
    null, null, null, null, null
);
```

**Output:**
```
./src/Service.java:
   42→    @Override
   43→    public void process() {
   44→        // Implementation
   45→    }
```

### Lines Before Match (-B)

```java
String result = grepTool.grep(
    "throw new.*Exception",
    "./src",
    null,
    OutputMode.content,
    2,                   // linesBefore - show 2 lines before
    null,                // linesAfter
    null,                // linesSurrounding
    true,                // showLineNumbers
    null, null, null, null, null
);
```

**Output:**
```
./src/Service.java:
   38→    if (user == null) {
   39→        logger.error("User not found");
   40→        throw new UserNotFoundException("User not found");
```

### Lines Surrounding Match (-C)

```java
String result = grepTool.grep(
    "TODO",
    "./src",
    null,
    OutputMode.content,
    null, null,
    2,                   // linesSurrounding - 2 lines before AND after
    true,                // showLineNumbers
    null, null, null, null, null
);
```

**Output:**
```
./src/Service.java:
   15→    public void process() {
   16→        // Process data
   17→        // TODO: Add validation
   18→        save(data);
   19→    }
```

**Priority:** If multiple context options are provided, `linesSurrounding` takes precedence.

## File Filtering

### By File Type

Filter by programming language or file type:

```java
// Search only Java files
String result = grepTool.grep(
    "interface.*Repository",
    "./src",
    null,
    OutputMode.files_with_matches,
    null, null, null, null, null,
    "java",              // type filter
    null, null, null
);

// Search only TypeScript files
String result = grepTool.grep(
    "export.*Component",
    "./src",
    null, null, null, null, null, null, null,
    "ts",                // type filter
    null, null, null
);
```

**Supported file types:**
- `java` - .java files
- `js` - .js files
- `ts` - .ts, .tsx files
- `py` - .py files
- `rust` - .rs files
- `go` - .go files
- `cpp` - .cpp, .cc, .cxx, .h, .hpp files
- `xml` - .xml files
- `json` - .json files
- `yaml` - .yml, .yaml files
- `md` - .md, .markdown files
- `txt` - .txt files

### By Glob Pattern

Use glob patterns for custom file filtering:

```java
// Search only React components
String result = grepTool.grep(
    "useState",
    "./src",
    "**/*.tsx",          // glob pattern
    OutputMode.files_with_matches,
    null, null, null, null, null, null, null, null, null
);

// Search only test files
String result = grepTool.grep(
    "@Test",
    "./src",
    "**/*Test.java",     // glob pattern
    OutputMode.content,
    null, null, null, true, null, null, null, null, null
);

// Search multiple extensions
String result = grepTool.grep(
    "API_KEY",
    ".",
    "**/*.{java,properties,yml}",  // multiple extensions
    OutputMode.files_with_matches,
    null, null, null, null, null, null, null, null, null
);
```

**Glob patterns:**
- `*.java` - All .java files in directory
- `**/*.java` - All .java files recursively
- `src/**/*.test.js` - All .test.js files under src
- `**/*.{ts,tsx}` - All .ts and .tsx files
- `**/test-*.java` - Files starting with "test-"

### Combining Type and Glob

```java
// Type filter takes precedence over glob
String result = grepTool.grep(
    "pattern",
    "./src",
    "**/*.txt",          // glob
    OutputMode.files_with_matches,
    null, null, null, null, null,
    "java",              // type overrides glob
    null, null, null
);
// Only searches .java files (glob is ignored)
```

**Best practice:** Use `type` for standard languages, `glob` for custom patterns.

## Pattern Matching

### Basic Regex Patterns

```java
// Literal string
grepTool.grep("UserService", "./src", null, null, null, null, null, null, null, null, null, null, null);

// Word boundary
grepTool.grep("\\bclass\\b", "./src", null, null, null, null, null, null, null, null, null, null, null);

// Alternation
grepTool.grep("Error|Exception|Throwable", "./src", null, null, null, null, null, null, null, null, null, null, null);

// Character class
grepTool.grep("[A-Z][a-z]+Service", "./src", null, null, null, null, null, null, null, null, null, null, null);

// Quantifiers
grepTool.grep("TODO.*urgent", "./src", null, null, null, null, null, null, null, null, null, null, null);
```

### Common Search Patterns

**Find class definitions:**
```java
grepTool.grep("^public class \\w+", "./src", null, OutputMode.content, null, null, null, true, null, "java", null, null, null);
```

**Find method definitions:**
```java
grepTool.grep("^\\s+public \\w+ \\w+\\(", "./src", null, OutputMode.content, null, null, null, true, null, "java", null, null, null);
```

**Find TODO comments:**
```java
grepTool.grep("//\\s*TODO", "./src", null, OutputMode.count, null, null, null, null, null, null, null, null, null);
```

**Find imports:**
```java
grepTool.grep("^import .*springframework", "./src", null, OutputMode.files_with_matches, null, null, null, null, null, "java", null, null, null);
```

**Find API endpoints:**
```java
grepTool.grep("@(Get|Post|Put|Delete)Mapping", "./src", null, OutputMode.content, null, 2, null, true, null, "java", null, null, null);
```

**Find hardcoded credentials (security audit):**
```java
grepTool.grep("password\\s*=\\s*[\"']\\w+[\"']", ".", null, OutputMode.content, 1, 1, null, true, true, null, null, null, null);
```

### Case-Insensitive Search

```java
String result = grepTool.grep(
    "todo",              // lowercase pattern
    "./src",
    null, null, null, null, null, null,
    true,                // caseInsensitive = true
    null, null, null, null
);
// Matches: TODO, Todo, todo, tOdO, etc.
```

### Multiline Patterns

For patterns that span multiple lines:

```java
String result = grepTool.grep(
    "class.*\\{[\\s\\S]*?private",  // Match class with private field
    "./src",
    null,
    OutputMode.content,
    null, null, null, true, null, "java",
    null, null,
    true                 // multiline = true (. matches newlines)
);
```

**Note:** Multiline mode allows `.` to match newline characters.

## Limiting Results

### Head Limit

Limit the number of output lines or entries:

```java
// Limit to first 10 matching files
String result = grepTool.grep(
    "TODO",
    "./src",
    null,
    OutputMode.files_with_matches,
    null, null, null, null, null, null,
    10,                  // headLimit
    null, null
);

// Limit to first 20 matching lines
String result = grepTool.grep(
    "public.*method",
    "./src",
    null,
    OutputMode.content,
    null, null, null, true, null, null,
    20,                  // headLimit
    null, null
);
```

### Offset

Skip first N results:

```java
// Skip first 10 files, then show next 10
String result = grepTool.grep(
    "class",
    "./src",
    null,
    OutputMode.files_with_matches,
    null, null, null, null, null, null,
    10,                  // headLimit (show 10)
    10,                  // offset (skip first 10)
    null
);
```

**Use case:** Pagination through large result sets.

## Common Use Cases

### 1. Find All TODOs

```java
GrepTool grepTool = new GrepTool();

// Count TODOs per file
String counts = grepTool.grep(
    "TODO|FIXME",
    "./src",
    null,
    OutputMode.count,
    null, null, null, null,
    true,                // case insensitive
    null, null, null, null
);

// Show TODO comments with context
String todos = grepTool.grep(
    "TODO|FIXME",
    "./src",
    null,
    OutputMode.content,
    null, null,
    2,                   // 2 lines context
    true,                // line numbers
    true,                // case insensitive
    null, null, null, null
);
```

### 2. Find Class Definitions

```java
// Find all Spring Controllers
String controllers = grepTool.grep(
    "@RestController|@Controller",
    "./src",
    null,
    OutputMode.files_with_matches,
    null, null, null, null, null,
    "java",
    null, null, null
);

// Show controller methods with mappings
String mappings = grepTool.grep(
    "@(GetMapping|PostMapping|PutMapping|DeleteMapping)",
    "./src",
    null,
    OutputMode.content,
    null,
    3,                   // show method signature
    null, true, null, "java", null, null, null
);
```

### 3. Security Audit

```java
// Find potential security issues
String secrets = grepTool.grep(
    "password|secret|api[_-]?key|token",
    ".",
    "**/*.{java,properties,yml,yaml}",
    OutputMode.content,
    1, 1, null, true,
    true,                // case insensitive
    null, null, null, null
);

// Find SQL injection risks
String sqlInjection = grepTool.grep(
    "executeQuery.*\\+|createQuery.*\\+",
    "./src",
    null,
    OutputMode.content,
    2, 2, null, true, null,
    "java",
    null, null, null
);
```

### 4. Dependency Analysis

```java
// Find all Spring dependencies
String springImports = grepTool.grep(
    "^import.*springframework",
    "./src",
    null,
    OutputMode.count,
    null, null, null, null, null,
    "java",
    null, null, null
);

// Find deprecated API usage
String deprecated = grepTool.grep(
    "@Deprecated",
    "./src",
    null,
    OutputMode.content,
    null, 5, null, true, null,
    "java",
    null, null, null
);
```

### 5. Code Refactoring

```java
// Find all usages of old method name
String usages = grepTool.grep(
    "\\boldMethodName\\b",
    "./src",
    null,
    OutputMode.files_with_matches,
    null, null, null, null, null, null, null, null, null
);

// Find specific pattern to refactor
String pattern = grepTool.grep(
    "new ArrayList<>\\(\\)",
    "./src",
    null,
    OutputMode.content,
    1, 1, null, true, null,
    "java",
    null, null, null
);
```

## Spring Boot Integration

### Basic Configuration

```java
@Configuration
public class ToolsConfig {

    @Bean
    public GrepTool grepTool() {
        return new GrepTool();
    }
}
```

### Custom Configuration

```java
@Configuration
public class ToolsConfig {

    @Value("${grep.max-output-length:150000}")
    private int maxOutputLength;

    @Value("${grep.max-depth:50}")
    private int maxDepth;

    @Value("${grep.max-line-length:15000}")
    private int maxLineLength;

    @Bean
    public GrepTool grepTool() {
        return new GrepTool(maxOutputLength, maxDepth, maxLineLength);
    }
}
```

### ChatClient Integration

```java
@Configuration
public class AgentConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder chatClientBuilder) {
        return chatClientBuilder
            .defaultTools(new GrepTool())
            .defaultTools(new FileSystemTools())
            .build();
    }
}
```

**Usage with AI agent:**
```java
ChatClient chatClient = ...; // from bean

String response = chatClient.prompt()
    .user("Find all TODO comments in the src directory")
    .call()
    .content();
// AI automatically uses GrepTool to search and summarize
```

## Best Practices

### 1. Choose the Right Output Mode

```java
// ✅ GOOD: Use files_with_matches for file discovery
grepTool.grep("pattern", "./src", null, OutputMode.files_with_matches,
    null, null, null, null, null, null, null, null, null);

// ✅ GOOD: Use count for metrics
grepTool.grep("TODO", "./src", null, OutputMode.count,
    null, null, null, null, null, null, null, null, null);

// ✅ GOOD: Use content for code review
grepTool.grep("deprecated", "./src", null, OutputMode.content,
    null, null, 2, true, null, null, null, null, null);

// ❌ WASTEFUL: Using content mode when you only need file paths
grepTool.grep("pattern", "./src", null, OutputMode.content,
    null, null, null, null, null, null, null, null, null);
```

### 2. Use Type Filters for Standard Languages

```java
// ✅ GOOD: Use type filter for standard languages
grepTool.grep("pattern", "./src", null, null, null, null, null, null, null,
    "java", null, null, null);

// ❌ LESS EFFICIENT: Using glob when type filter exists
grepTool.grep("pattern", "./src", "**/*.java", null, null, null, null, null, null,
    null, null, null, null);
```

### 3. Add Context for Better Understanding

```java
// ✅ GOOD: Include context lines for AI understanding
grepTool.grep("@Transactional", "./src", null, OutputMode.content,
    null, 3, null, true, null, "java", null, null, null);

// ❌ MINIMAL: No context makes it hard to understand
grepTool.grep("@Transactional", "./src", null, OutputMode.content,
    null, null, null, true, null, "java", null, null, null);
```

### 4. Use Specific Patterns

```java
// ✅ GOOD: Specific pattern with word boundaries
grepTool.grep("\\bUserService\\b", "./src", null, null, null, null, null, null, null,
    null, null, null, null);

// ❌ TOO BROAD: May match UserServiceImpl, UserServiceTest, etc.
grepTool.grep("UserService", "./src", null, null, null, null, null, null, null,
    null, null, null, null);
```

### 5. Limit Results for Large Codebases

```java
// ✅ GOOD: Use headLimit for large result sets
grepTool.grep("class", "./src", null, OutputMode.files_with_matches,
    null, null, null, null, null, null, 100, null, null);

// ❌ WASTEFUL: No limit may return thousands of results
grepTool.grep("class", "./src", null, OutputMode.files_with_matches,
    null, null, null, null, null, null, null, null, null);
```

### 6. Escape Special Regex Characters

```java
// ✅ GOOD: Escape special characters
grepTool.grep("\\$\\{.*\\}", "./src", null, null, null, null, null, null, null,
    null, null, null, null);

// ❌ WRONG: Special characters not escaped
grepTool.grep("${.*}", "./src", null, null, null, null, null, null, null,
    null, null, null, null);
// { and } are regex special characters
```

## Error Handling

The tool handles errors gracefully:

**Empty pattern:**
```java
String result = grepTool.grep("", "./src", null, null, null, null, null, null, null, null, null, null, null);
// Returns empty result (no matches)
```

**Invalid regex:**
```java
String result = grepTool.grep("[invalid", "./src", null, null, null, null, null, null, null, null, null, null, null);
// Logs error and returns empty result
```

**Non-existent path:**
```java
String result = grepTool.grep("pattern", "/nonexistent/path", null, null, null, null, null, null, null, null, null, null, null);
// Returns empty result
```

**Output truncation:**
- If results exceed `maxOutputLength`, output is truncated with warning in logs
- Increase `maxOutputLength` in constructor for larger results

## Performance Tips

### 1. Use Narrow Search Paths

```java
// ✅ GOOD: Search specific directory
grepTool.grep("pattern", "./src/main/java", null, null, null, null, null, null, null, null, null, null, null);

// ❌ SLOW: Searching from root unnecessarily
grepTool.grep("pattern", ".", null, null, null, null, null, null, null, null, null, null, null);
```

### 2. Filter Early with Type/Glob

```java
// ✅ GOOD: Filter files before searching
grepTool.grep("pattern", "./src", null, null, null, null, null, null, null, "java", null, null, null);

// ❌ SLOW: Search all files then filter mentally
grepTool.grep("pattern", "./src", null, null, null, null, null, null, null, null, null, null, null);
```

### 3. Use files_with_matches When Possible

```java
// ✅ GOOD: Fast file discovery
grepTool.grep("pattern", "./src", null, OutputMode.files_with_matches, null, null, null, null, null, null, null, null, null);

// ❌ SLOWER: Content mode when you only need files
grepTool.grep("pattern", "./src", null, OutputMode.content, null, null, null, null, null, null, null, null, null);
```

### 4. Limit Results

```java
// ✅ GOOD: Stop after finding enough
grepTool.grep("pattern", "./src", null, null, null, null, null, null, null, null, 50, null, null);

// ❌ WASTEFUL: Process all matches
grepTool.grep("pattern", "./src", null, null, null, null, null, null, null, null, null, null, null);
```

### 5. Adjust Depth for Shallow Searches

```java
// ✅ GOOD: Limit depth for shallow searches
GrepTool shallowGrep = new GrepTool(100000, 3, 10000);
shallowGrep.grep("pattern", "./src", null, null, null, null, null, null, null, null, null, null, null);
```

## Troubleshooting

### No Results Found

**Problem:** Search returns empty results when you expect matches

**Solutions:**

1. **Check pattern syntax:**
   ```java
   // Test with simple literal pattern first
   grepTool.grep("UserService", "./src", null, null, null, null, null, null, null, null, null, null, null);
   ```

2. **Verify path exists:**
   ```java
   // Use absolute path to be sure
   grepTool.grep("pattern", "/full/path/to/src", null, null, null, null, null, null, null, null, null, null, null);
   ```

3. **Try case-insensitive search:**
   ```java
   grepTool.grep("pattern", "./src", null, null, null, null, null, null, true, null, null, null, null);
   ```

4. **Remove filters temporarily:**
   ```java
   // Remove type/glob filters
   grepTool.grep("pattern", "./src", null, null, null, null, null, null, null, null, null, null, null);
   ```

### Pattern Not Matching as Expected

**Problem:** Regex pattern doesn't match what you expect

**Solutions:**

1. **Escape special characters:**
   ```java
   // ✅ Correct: Escaped brackets
   grepTool.grep("List\\[String\\]", "./src", null, null, null, null, null, null, null, null, null, null, null);

   // ❌ Wrong: Unescaped brackets
   grepTool.grep("List[String]", "./src", null, null, null, null, null, null, null, null, null, null, null);
   ```

2. **Test pattern incrementally:**
   ```java
   // Start simple
   grepTool.grep("User", "./src", null, null, null, null, null, null, null, null, null, null, null);

   // Add complexity
   grepTool.grep("class.*User", "./src", null, null, null, null, null, null, null, null, null, null, null);

   // Full pattern
   grepTool.grep("^public class.*User.*\\{", "./src", null, null, null, null, null, null, null, null, null, null, null);
   ```

3. **Use multiline mode for cross-line patterns:**
   ```java
   grepTool.grep("class.*\\{[\\s\\S]*?method", "./src", null, null, null, null, null, null, null, null, null, null, true);
   ```

### Output Truncated

**Problem:** Results are cut off

**Solutions:**

1. **Increase maxOutputLength:**
   ```java
   GrepTool largeGrep = new GrepTool(500000, 100, 10000);
   ```

2. **Use headLimit to paginate:**
   ```java
   // First 100 results
   grepTool.grep("pattern", "./src", null, null, null, null, null, null, null, null, 100, 0, null);

   // Next 100 results
   grepTool.grep("pattern", "./src", null, null, null, null, null, null, null, null, 100, 100, null);
   ```

3. **Use count mode first:**
   ```java
   // See how many matches before getting content
   String count = grepTool.grep("pattern", "./src", null, OutputMode.count, null, null, null, null, null, null, null, null, null);
   ```

### Performance Issues

**Problem:** Search takes too long

**Solutions:**

1. **Narrow search path:**
   ```java
   // Instead of entire project
   grepTool.grep("pattern", ".", null, null, null, null, null, null, null, null, null, null, null);

   // Search specific directory
   grepTool.grep("pattern", "./src/main/java/com/example", null, null, null, null, null, null, null, null, null, null, null);
   ```

2. **Use type filters:**
   ```java
   grepTool.grep("pattern", "./src", null, null, null, null, null, null, null, "java", null, null, null);
   ```

3. **Reduce depth:**
   ```java
   GrepTool shallowGrep = new GrepTool(100000, 10, 10000);
   ```

4. **Use files_with_matches mode:**
   ```java
   grepTool.grep("pattern", "./src", null, OutputMode.files_with_matches, null, null, null, null, null, null, null, null, null);
   ```

### Lines Skipped

**Problem:** Warning about lines being skipped

**Cause:** Lines exceed `maxLineLength` (default: 10,000 characters)

**Solution:**
```java
// Increase maxLineLength for minified code
GrepTool longLineGrep = new GrepTool(100000, 100, 50000);
```

## Comparison with Traditional Grep

| Feature | GrepTool | Traditional grep |
|---------|----------|------------------|
| Installation | None required | Requires grep/ripgrep installation |
| Cross-platform | ✅ Pure Java | ⚠️ Different syntax on Windows |
| Regex flavor | Java regex | POSIX regex |
| File filtering | Type + Glob | Complex find commands |
| Output modes | 3 optimized modes | Manual formatting |
| AI integration | ✅ Designed for agents | Manual parsing needed |
| Dependencies | Zero | External binaries |
| Performance | Good for most use cases | Faster for huge codebases (native) |

**When to use GrepTool:**
- AI agent code search
- Cross-platform applications
- Avoiding external dependencies
- Integrated Spring AI workflows

**When traditional grep might be better:**
- Massive codebases (millions of files)
- Shell scripting outside Java
- Need specific grep/ripgrep features

## Advanced Examples

### Multi-Pattern Search with Post-Processing

```java
GrepTool grepTool = new GrepTool();

// Find all service classes
String services = grepTool.grep(
    "@Service|@Component",
    "./src",
    null,
    OutputMode.files_with_matches,
    null, null, null, null, null, "java", null, null, null
);

// Find all repositories
String repositories = grepTool.grep(
    "@Repository",
    "./src",
    null,
    OutputMode.files_with_matches,
    null, null, null, null, null, "java", null, null, null
);

// Combine results for architectural analysis
```

### Codebase Statistics

```java
// Count total classes
String classCount = grepTool.grep(
    "^(public |private |protected )?class \\w+",
    "./src",
    null,
    OutputMode.count,
    null, null, null, null, null, "java", null, null, null
);

// Count interfaces
String interfaceCount = grepTool.grep(
    "^(public |private )?interface \\w+",
    "./src",
    null,
    OutputMode.count,
    null, null, null, null, null, "java", null, null, null
);

// Count test methods
String testCount = grepTool.grep(
    "@Test",
    "./src/test",
    null,
    OutputMode.count,
    null, null, null, null, null, "java", null, null, null
);
```

### Security Scanning

```java
// Comprehensive security audit
GrepTool grepTool = new GrepTool();

// 1. Find hardcoded credentials
String credentials = grepTool.grep(
    "(password|secret|apikey|token)\\s*=\\s*[\"'][^\"']+[\"']",
    ".",
    "**/*.{java,properties,yml,yaml}",
    OutputMode.content,
    1, 1, null, true, true, null, null, null, null
);

// 2. Find SQL injection risks
String sqlRisks = grepTool.grep(
    "executeQuery.*\\+|createQuery.*\\+",
    "./src",
    null,
    OutputMode.content,
    2, 2, null, true, null, "java", null, null, null
);

// 3. Find XSS vulnerabilities
String xssRisks = grepTool.grep(
    "innerHTML|eval\\(|document\\.write",
    "./src",
    "**/*.{js,jsx,ts,tsx}",
    OutputMode.content,
    1, 1, null, true, null, null, null, null, null
);

// 4. Find unsafe deserialization
String deserializationRisks = grepTool.grep(
    "readObject|XMLDecoder|Yaml\\.load",
    "./src",
    null,
    OutputMode.content,
    2, 2, null, true, null, "java", null, null, null
);
```

## See Also

- [FileSystemTools](FileSystemTools.md) - For reading files found by grep
- [ShellTools](ShellTools.md) - For running system grep if needed
- [SkillsTool](SkillsTool.md) - For creating code analysis skills
