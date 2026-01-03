# FileSystemTools

A comprehensive file manipulation toolkit providing read, write, and edit operations for working with files in the local filesystem.

**Features:**
- Read files with line range support and pagination
- Write new files or overwrite existing ones
- Precise string replacement editing
- Line number formatting for easy reference
- Long line truncation (2000 chars max)
- Automatic parent directory creation
- UTF-8 encoding support
- Replace all or single occurrence editing

## Available Tools

### 1. Read - Read File Contents

Reads a file from the local filesystem with optional line range support for handling large files.

**Parameters:**
- `filePath` (required) - The absolute path to the file to read
- `offset` (optional) - The line number to start reading from (1-indexed)
- `limit` (optional) - The number of lines to read (default: 2000)

**Basic Usage:**

```java
FileSystemTools fileTools = new FileSystemTools();

// Read entire file (up to 2000 lines)
String content = fileTools.read(
    "/path/to/file.txt",
    null,                    // offset (start from beginning)
    null,                    // limit (read up to 2000 lines)
    toolContext
);

// Read specific line range
String content = fileTools.read(
    "/path/to/large-file.log",
    100,                     // Start from line 100
    50,                      // Read 50 lines
    toolContext
);

// Read from line 500 onwards
String content = fileTools.read(
    "/path/to/file.java",
    500,                     // Start from line 500
    2000,                    // Read up to 2000 lines
    toolContext
);
```

**Output Format:**
```
File: /path/to/file.txt
Showing lines 1-10 of 150

     1→First line of content
     2→Second line of content
     3→Third line of content
     ...
    10→Tenth line of content
```

**Key Features:**
- **Line numbers**: Results formatted with `cat -n` style line numbers (right-aligned, 6 chars, arrow separator)
- **Line truncation**: Lines longer than 2000 characters are truncated with "... (line truncated)" suffix
- **Pagination**: Read large files in chunks using offset and limit
- **Empty file detection**: Returns "File is empty" message for empty files
- **Error handling**: Clear error messages for non-existent files or directories

**Important Notes:**
- File path must be absolute, not relative
- Default limit is 2000 lines - recommended to read full file when possible
- Line numbers are 1-indexed (first line is line 1)
- Cannot read directories (use Bash tool with `ls` command)
- Supports reading various file types (text, images, PDFs, Jupyter notebooks with appropriate handling)

### 2. Write - Create or Overwrite Files

Writes content to a file, creating new files or overwriting existing ones.

**Parameters:**
- `filePath` (required) - The absolute path to the file to write (must be absolute)
- `content` (required) - The content to write to the file

**Basic Usage:**

```java
// Create a new file
String result = fileTools.write(
    "/path/to/new-file.txt",
    "This is the file content\nWith multiple lines",
    toolContext
);
// Returns: "Successfully created file: /path/to/new-file.txt (45 bytes)"

// Overwrite an existing file
String result = fileTools.write(
    "/path/to/existing-file.txt",
    "New content replacing old content",
    toolContext
);
// Returns: "Successfully overwrote file: /path/to/existing-file.txt (33 bytes)"

// Create file with parent directories
String result = fileTools.write(
    "/path/to/new/directory/file.txt",
    "Content",
    toolContext
);
// Automatically creates /path/to/new/directory/ if it doesn't exist
```

**Important Notes:**
- **MUST read first**: If overwriting an existing file, you MUST use the Read tool first
- **Prefer Edit**: ALWAYS prefer editing existing files instead of writing new ones
- **No emojis**: Avoid writing emojis unless explicitly requested by the user
- **No proactive docs**: Never create documentation files (*.md, README) unless explicitly requested
- **Parent directories**: Automatically creates parent directories if they don't exist
- **Complete replacement**: Overwrites the entire file content (does not append)

**When to Use:**
- Creating new files explicitly requested by the user
- Generating configuration files, scripts, or source files
- Writing output from data transformations

**When NOT to Use:**
- Modifying existing files (use Edit instead)
- Making small changes to code (use Edit instead)

### 3. Edit - Precise String Replacement

Performs exact string replacements in files with safety checks to prevent unintended changes.

**Parameters:**
- `filePath` (required) - The absolute path to the file to modify
- `old_string` (required) - The exact text to replace
- `new_string` (required) - The text to replace it with (must be different from old_string)
- `replace_all` (optional) - Replace all occurrences (default: false)

**Basic Usage:**

```java
// Single replacement (default)
String result = fileTools.edit(
    "/path/to/file.java",
    "public void oldMethod() {",       // old_string
    "public void newMethod() {",       // new_string
    null,                               // replace_all (false)
    toolContext
);

// Replace all occurrences (useful for variable renaming)
String result = fileTools.edit(
    "/path/to/file.java",
    "oldVariableName",                 // old_string
    "newVariableName",                 // new_string
    true,                              // replace_all
    toolContext
);

// Multi-line replacement
String result = fileTools.edit(
    "/path/to/config.yml",
    "database:\n  host: localhost\n  port: 5432",
    "database:\n  host: prod-server\n  port: 3306",
    null,
    toolContext
);
```

**Output Format:**
```
The file /path/to/file.java has been updated. Here's the result of running `cat -n` on a snippet of the edited file:
    15→    private static final String NAME = "newValue";
    16→
    17→    public void newMethod() {
    18→        // method implementation
    19→    }
```

**Safety Features:**

1. **Uniqueness Check**: If `old_string` appears multiple times and `replace_all=false`, the edit fails with an error:
   ```
   Error: old_string appears 5 times in the file. Either provide a larger string
   with more surrounding context to make it unique or use replace_all=true to
   change all instances.
   ```

2. **Existence Check**: Returns error if `old_string` is not found in the file

3. **Different Strings**: Validates that `old_string` and `new_string` are different

4. **Must Read First**: Should read the file first to see exact content and indentation

**Indentation Handling:**

When copying text from Read tool output, preserve exact indentation AFTER the line number prefix:

```
Read output:
    42→    public void method() {

Correct old_string:
"    public void method() {"

Incorrect old_string (includes line number):
"42→    public void method() {"
```

**Best Practices:**

1. **Read before editing**: Always use Read tool first to see exact content
   ```java
   String content = fileTools.read("/path/to/file.java", null, null, toolContext);
   // Now edit based on what you see
   ```

2. **Include context for uniqueness**: If replacement string appears multiple times, include surrounding lines
   ```java
   // Instead of just "foo"
   fileTools.edit(filePath, "bar\nfoo\nbaz", "bar\nnewValue\nbaz", null, toolContext);
   ```

3. **Use replace_all for renaming**: When renaming variables or constants throughout a file
   ```java
   fileTools.edit(filePath, "OLD_CONSTANT", "NEW_CONSTANT", true, toolContext);
   ```

4. **Preserve indentation**: Copy exact whitespace from Read output (spaces/tabs)

5. **Multi-line edits**: Use `\n` for newlines in both old_string and new_string

**Example Workflow:**

```java
FileSystemTools fileTools = new FileSystemTools();

// 1. Read the file to see current content
String content = fileTools.read(
    "/src/main/java/Example.java",
    null,
    null,
    toolContext
);

// 2. Identify exact string to replace (preserving indentation)
String oldCode = """
    public void calculate() {
        return value * 2;
    }""";

String newCode = """
    public void calculate() {
        return value * 3;
    }""";

// 3. Perform the edit
String result = fileTools.edit(
    "/src/main/java/Example.java",
    oldCode,
    newCode,
    null,
    toolContext
);

// Output shows snippet with context around the edit
```

**Common Use Cases:**

| Task | Configuration | Example |
|------|--------------|---------|
| Update single method | `replace_all=false` | Modify one method implementation |
| Rename variable | `replace_all=true` | Change `oldName` to `newName` everywhere |
| Update configuration | `replace_all=false` | Change one config value |
| Fix typos | `replace_all=true` | Fix misspelled word throughout file |
| Refactor imports | `replace_all=false` | Update specific import statement |

**Error Messages:**

| Error | Meaning | Solution |
|-------|---------|----------|
| "File does not exist" | File path is invalid | Check file path is correct |
| "old_string not found" | Text doesn't exist in file | Verify exact string including whitespace |
| "appears N times" | Multiple matches found | Add more context or use `replace_all=true` |
| "must be different" | old_string equals new_string | Ensure you're actually changing the content |
