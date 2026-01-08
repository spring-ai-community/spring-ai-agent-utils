# GlobTool

A pure Java implementation of glob pattern matching for finding files by name. Fast, cross-platform, and optimized for AI agents to discover files in codebases of any size.

**Features:**
- Fast file pattern matching with glob syntax
- Returns files sorted by modification time (most recent first)
- Supports simple patterns (`*.java`) and complex patterns (`**/*.tsx`)
- Automatic filtering of common directories (.git, node_modules, target, etc.)
- Configurable depth and result limits
- Pure Java implementation - no external dependencies
- Thread-safe for concurrent use

## Overview

The `GlobTool` is a Spring AI tool that enables AI agents to quickly find files by name patterns. Unlike grep which searches file contents, glob searches file names and paths, making it ideal for file discovery.

**Why use GlobTool?**
- **Fast file discovery**: Optimized for finding files by name patterns
- **AI-optimized**: Returns results sorted by modification time (recent files first)
- **Zero dependencies**: No need to install external tools
- **Smart filtering**: Automatically ignores build artifacts and version control directories

## Basic Usage

```java
// Build with default configuration
GlobTool globTool = GlobTool.builder().build();

// Find files by pattern
String files = globTool.glob(
    "*.java",           // pattern
    "./src"             // path (optional, defaults to current directory)
);
```

### Custom Configuration

For specific requirements, customize the builder:

```java
GlobTool customGlob = GlobTool.builder()
    .maxDepth(50)       // Maximum directory traversal depth (default: 100)
    .maxResults(500)    // Maximum number of results to return (default: 1000)
    .build();
```

## Configuration Parameters

| Parameter | Default | Description |
|-----------|---------|-------------|
| `maxDepth` | 100 | Maximum directory traversal depth (prevents infinite recursion) |
| `maxResults` | 1000 | Maximum number of results to return (limits output size) |

**When to adjust:**
- **maxDepth**: Decrease for shallow searches (e.g., 10 for root-level only)
- **maxResults**: Decrease for faster response (e.g., 100 for quick discovery)

## Method Parameters

The `glob` method signature:

```java
String glob(
    String pattern,  // Required: Glob pattern to match files
    String path      // Optional: Directory to search (default: current directory)
)
```

## Glob Patterns

### Simple Patterns

Match files in any directory (recursively):

```java
// Find all Java files
String javaFiles = globTool.glob("*.java", "./src");

// Find all TypeScript files
String tsFiles = globTool.glob("*.ts", "./src");

// Find all markdown files
String mdFiles = globTool.glob("*.md", ".");
```

**Output:**
```
./src/main/java/UserService.java
./src/main/java/OrderService.java
./src/test/java/UserServiceTest.java
```

### Complex Patterns

Use `**` for explicit recursive matching:

```java
// Find all Java files (explicit recursion)
String files = globTool.glob("**/*.java", "./src");

// Find all test files
String tests = globTool.glob("**/*Test.java", "./src");

// Find all React components
String components = globTool.glob("**/*Component.tsx", "./src");
```

### Path-Specific Patterns

Match files in specific subdirectories:

```java
// Find Java files only in controllers directory
String controllers = globTool.glob("**/controllers/*.java", "./src");

// Find config files in resources
String configs = globTool.glob("**/resources/*.yml", "./src");

// Find components in specific package
String userComponents = globTool.glob("**/user/**/*.tsx", "./src");
```

### Wildcard Patterns

```java
// Match all files (any extension)
String allFiles = globTool.glob("*", "./src");

// Match files starting with "Test"
String testFiles = globTool.glob("Test*.java", "./src");

// Match files ending with "Service"
String services = globTool.glob("**/*Service.java", "./src");
```

### Multiple Extensions

Note: Java's glob syntax doesn't support `{ext1,ext2}` syntax like bash. Use separate calls for multiple extensions:

```java
// Find TypeScript files
String tsFiles = globTool.glob("*.ts", "./src");

// Find TSX files separately
String tsxFiles = globTool.glob("*.tsx", "./src");
```

## Common Use Cases

### 1. Find All Source Files

```java
GlobTool globTool = GlobTool.builder().build();

// Find all Java source files
String javaSources = globTool.glob("**/*.java", "./src/main");

// Find all test files
String testFiles = globTool.glob("**/*Test.java", "./src/test");

// Find all configuration files
String configs = globTool.glob("**/*.yml", "./src/main/resources");
```

### 2. Find Components by Name

```java
// Find specific component
String userComponent = globTool.glob("**/UserComponent.tsx", "./src");

// Find all components
String allComponents = globTool.glob("**/*Component.tsx", "./src");

// Find all services
String services = globTool.glob("**/*Service.java", "./src");
```

### 3. Find Files by Directory

```java
// Find files in controllers
String controllers = globTool.glob("**/controllers/*.java", "./src");

// Find files in models
String models = globTool.glob("**/models/*.ts", "./src");

// Find files in utils
String utils = globTool.glob("**/utils/*", "./src");
```

### 4. Find Recently Modified Files

Since results are sorted by modification time (most recent first):

```java
// Find recent Java files (limited to 10)
GlobTool recentGlob = GlobTool.builder()
    .maxResults(10)
    .build();

String recentFiles = recentGlob.glob("**/*.java", "./src");
// Returns the 10 most recently modified Java files
```

### 5. Find Configuration Files

```java
// Find property files
String properties = globTool.glob("**/*.properties", ".");

// Find YAML configs
String yamlConfigs = globTool.glob("**/*.yml", ".");

// Find JSON configs
String jsonConfigs = globTool.glob("**/*.json", ".");
```

### 6. Find Documentation

```java
// Find all markdown files
String docs = globTool.glob("**/*.md", ".");

// Find README files
String readmes = globTool.glob("**/README.md", ".");

// Find files in docs directory
String docFiles = globTool.glob("docs/**/*", ".");
```

## Output Format

The tool returns a newline-separated list of file paths, sorted by modification time (most recent first):

```
./src/main/java/controllers/UserController.java
./src/main/java/services/UserService.java
./src/main/java/repositories/UserRepository.java
./src/test/java/controllers/UserControllerTest.java
```

**No matches:**
```
No files found matching pattern: *.xyz
```

**Error cases:**
```
Error: Path does not exist: /invalid/path
Error: Path is not a directory: /path/to/file.txt
```

## Filtered Directories

The tool automatically ignores common directories to improve performance:

- `.git` - Git version control
- `node_modules` - Node.js dependencies
- `target` - Maven build output
- `build` - Gradle build output
- `.idea` - IntelliJ IDEA settings
- `.vscode` - VS Code settings
- `dist` - Distribution builds
- `__pycache__` - Python cache

These directories are skipped during traversal, making searches faster and avoiding irrelevant files.

## Spring Boot Integration

### Basic Configuration

```java
@Configuration
public class ToolsConfig {

    @Bean
    public GlobTool globTool() {
        return GlobTool.builder().build();
    }
}
```

### Custom Configuration

```java
@Configuration
public class ToolsConfig {

    @Value("${glob.max-depth:50}")
    private int maxDepth;

    @Value("${glob.max-results:500}")
    private int maxResults;

    @Bean
    public GlobTool globTool() {
        return GlobTool.builder()
            .maxDepth(maxDepth)
            .maxResults(maxResults)
            .build();
    }
}
```

**application.properties:**
```properties
# GlobTool configuration
glob.max-depth=50
glob.max-results=500
```

### ChatClient Integration

```java
@Configuration
public class AgentConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder chatClientBuilder) {
        return chatClientBuilder
            .defaultTools(GlobTool.builder().build())
            .defaultTools(FileSystemTools.builder().build())
            .defaultTools(GrepTool.builder().build())
            .build();
    }
}
```

**Usage with AI agent:**
```java
ChatClient chatClient = ...; // from bean

String response = chatClient.prompt()
    .user("Find all TypeScript components in the src directory")
    .call()
    .content();
// AI automatically uses GlobTool to find files
```

## Best Practices

### 1. Use Specific Patterns

```java
// ✅ GOOD: Specific pattern
globTool.glob("**/*Service.java", "./src");

// ❌ TOO BROAD: May return too many files
globTool.glob("**/*", "./src");
```

### 2. Narrow the Search Path

```java
// ✅ GOOD: Search specific directory
globTool.glob("*.java", "./src/main/java/com/example/services");

// ❌ WASTEFUL: Search entire project
globTool.glob("*.java", ".");
```

### 3. Use Simple Patterns When Possible

```java
// ✅ GOOD: Simple pattern is faster
globTool.glob("*.java", "./src");

// ⚠️ UNNECESSARY: Explicit ** is redundant for simple extensions
globTool.glob("**/*.java", "./src");
// Both work the same, but simple pattern is clearer
```

### 4. Limit Results for Quick Discovery

```java
// ✅ GOOD: Limit results for faster response
GlobTool quickGlob = GlobTool.builder()
    .maxResults(50)
    .build();

quickGlob.glob("*.java", "./src");
```

### 5. Combine with GrepTool

Use GlobTool to find files, then GrepTool to search contents:

```java
// Find all service files
String serviceFiles = globTool.glob("**/*Service.java", "./src");

// Then search contents
String todoServices = grepTool.grep("TODO", "./src", "**/*Service.java",
    OutputMode.files_with_matches, null, null, null, null, null, null, null, null, null);
```

## Practical Examples

### Example 1: Find All Controllers

```java
GlobTool globTool = GlobTool.builder().build();

// Find REST controllers
String controllers = globTool.glob("**/*Controller.java", "./src");

// Find Spring MVC controllers
String mvcControllers = globTool.glob("**/controllers/*.java", "./src");
```

### Example 2: Find Configuration Files

```java
// Find all application configs
String configs = globTool.glob("application*.yml", "./src/main/resources");

// Find all property files
String properties = globTool.glob("**/*.properties", "./src/main/resources");
```

### Example 3: Find Test Files

```java
// Find all test files
String allTests = globTool.glob("**/*Test.java", "./src/test");

// Find integration tests
String integrationTests = globTool.glob("**/*IT.java", "./src/test");

// Find specific test
String userTests = globTool.glob("**/UserServiceTest.java", "./src/test");
```

### Example 4: Find Frontend Components

```java
// Find React components
String reactComponents = globTool.glob("**/*.tsx", "./src");

// Find Vue components
String vueComponents = globTool.glob("**/*.vue", "./src");

// Find pages
String pages = globTool.glob("**/pages/*.tsx", "./src");
```

### Example 5: Project Structure Analysis

```java
GlobTool globTool = GlobTool.builder().build();

// Count Java files
String javaFiles = globTool.glob("**/*.java", "./src");
int javaCount = javaFiles.split("\n").length;

// Count TypeScript files
String tsFiles = globTool.glob("**/*.ts", "./src");
int tsCount = tsFiles.split("\n").length;

// Find build files
String buildFiles = globTool.glob("**/pom.xml", ".");
String gradleFiles = globTool.glob("**/build.gradle", ".");
```

### Example 6: Find Recently Modified Files

```java
// Find 20 most recently modified Java files
GlobTool recentGlob = GlobTool.builder()
    .maxResults(20)
    .build();

String recentFiles = recentGlob.glob("**/*.java", "./src");
// Results are sorted by modification time (newest first)
```

## Advanced Configuration

### Shallow Search

For quick searches in shallow directory structures:

```java
GlobTool shallowGlob = GlobTool.builder()
    .maxDepth(3)        // Only search 3 levels deep
    .maxResults(100)    // Limit to 100 files
    .build();

String files = shallowGlob.glob("*.java", "./src");
```

### Large Codebase

For large codebases where you need more results:

```java
GlobTool largeGlob = GlobTool.builder()
    .maxDepth(200)      // Deep traversal
    .maxResults(5000)   // More results
    .build();

String files = largeGlob.glob("**/*.java", ".");
```

### Quick Discovery

For fast file discovery (find first matching files quickly):

```java
GlobTool quickGlob = GlobTool.builder()
    .maxResults(10)     // Stop after 10 files
    .build();

String files = quickGlob.glob("*Component.tsx", "./src");
```

## Performance Tips

### 1. Use Narrow Paths

```java
// ✅ GOOD: Narrow search path
globTool.glob("*.java", "./src/main/java/com/example");

// ❌ SLOW: Unnecessarily broad
globTool.glob("*.java", ".");
```

### 2. Limit Results

```java
// ✅ GOOD: Limit results when you don't need all matches
GlobTool limitedGlob = GlobTool.builder()
    .maxResults(50)
    .build();
```

### 3. Use Specific Patterns

```java
// ✅ GOOD: Specific pattern reduces matches
globTool.glob("**/UserService.java", "./src");

// ❌ SLOWER: Too broad
globTool.glob("**/*", "./src");
```

### 4. Reduce Depth for Flat Structures

```java
// ✅ GOOD: Shallow search for flat directories
GlobTool shallowGlob = GlobTool.builder()
    .maxDepth(2)
    .build();

shallowGlob.glob("*.md", "./docs");
```

## Troubleshooting

### No Files Found

**Problem:** Search returns "No files found" when you expect matches

**Solutions:**

1. **Check pattern syntax:**
   ```java
   // Simple extension pattern
   globTool.glob("*.java", "./src");

   // Explicit recursive pattern
   globTool.glob("**/*.java", "./src");
   ```

2. **Verify path exists:**
   ```java
   // Use absolute path to be sure
   globTool.glob("*.java", "/full/path/to/src");
   ```

3. **Check if files are in ignored directories:**
   - Files in `.git`, `node_modules`, `target`, etc. are automatically filtered
   - Search in more specific directories if needed

### Too Many Results

**Problem:** Returns more files than needed

**Solutions:**

1. **Use more specific pattern:**
   ```java
   // Instead of all files
   globTool.glob("**/*", "./src");

   // Be specific
   globTool.glob("**/*Service.java", "./src");
   ```

2. **Limit results:**
   ```java
   GlobTool limitedGlob = GlobTool.builder()
       .maxResults(100)
       .build();
   ```

3. **Narrow search path:**
   ```java
   // Instead of root
   globTool.glob("*.java", ".");

   // Search specific directory
   globTool.glob("*.java", "./src/main/java");
   ```

### Path Does Not Exist

**Problem:** Error message about non-existent path

**Solutions:**

1. **Check path spelling:**
   ```java
   // Verify the path exists
   globTool.glob("*.java", "./src");  // correct
   globTool.glob("*.java", "./scr");  // typo!
   ```

2. **Use relative path from current directory:**
   ```java
   // Relative to current working directory
   globTool.glob("*.java", "./src");
   ```

3. **Use absolute path:**
   ```java
   globTool.glob("*.java", "/absolute/path/to/src");
   ```

### Path Is Not a Directory

**Problem:** Provided path is a file, not a directory

**Solution:**
```java
// ❌ WRONG: File path
globTool.glob("*.java", "./src/Main.java");

// ✅ CORRECT: Directory path
globTool.glob("*.java", "./src");
```

### Results Not Sorted as Expected

**Note:** Results are automatically sorted by modification time (most recent first). If you need different sorting, process the results:

```java
String files = globTool.glob("*.java", "./src");
List<String> fileList = Arrays.asList(files.split("\n"));
Collections.sort(fileList);  // Sort alphabetically
```

## Comparison with GrepTool

| Feature | GlobTool | GrepTool |
|---------|----------|----------|
| **Search by** | File names/paths | File contents |
| **Pattern type** | Glob patterns | Regex patterns |
| **Use case** | Finding files by name | Searching code/text |
| **Speed** | Very fast | Slower (reads files) |
| **Output** | File paths | Files, counts, or content |
| **Best for** | File discovery | Code search |

**When to use GlobTool:**
- Finding files by name or extension
- Locating specific components/classes
- Quick file discovery
- Getting list of files to process

**When to use GrepTool:**
- Searching file contents
- Finding code patterns
- Text search within files
- Code analysis

**Use together:**
```java
// 1. Find files with GlobTool
String javaFiles = globTool.glob("**/*Service.java", "./src");

// 2. Search contents with GrepTool
String todosInServices = grepTool.grep("TODO", "./src", "**/*Service.java",
    OutputMode.content, null, null, 2, true, null, null, null, null, null);
```

## Common Patterns

### Find All Files of Type

```java
// Java files
globTool.glob("**/*.java", "./src");

// TypeScript files
globTool.glob("**/*.ts", "./src");

// Python files
globTool.glob("**/*.py", "./src");

// Configuration files
globTool.glob("**/*.yml", "./config");
```

### Find Files by Name

```java
// Exact name
globTool.glob("**/UserService.java", "./src");

// Files ending with pattern
globTool.glob("**/*Service.java", "./src");

// Files starting with pattern
globTool.glob("**/Test*.java", "./src");
```

### Find Files in Specific Directories

```java
// Controllers
globTool.glob("**/controllers/*.java", "./src");

// Services
globTool.glob("**/services/*.java", "./src");

// Models
globTool.glob("**/models/*.ts", "./src");

// Components
globTool.glob("**/components/*.tsx", "./src");
```

### Find Build and Config Files

```java
// Maven
globTool.glob("**/pom.xml", ".");

// Gradle
globTool.glob("**/build.gradle", ".");

// Package.json
globTool.glob("**/package.json", ".");

// Docker
globTool.glob("**/Dockerfile", ".");
```

## Integration Examples

### With FileSystemTools

```java
GlobTool globTool = GlobTool.builder().build();
FileSystemTools fileTools = FileSystemTools.builder().build();

// Find all config files
String configs = globTool.glob("**/*.yml", "./config");

// Read each config
for (String configPath : configs.split("\n")) {
    String content = fileTools.read(configPath, null, null, toolContext);
    // Process config content
}
```

### With GrepTool

```java
GlobTool globTool = GlobTool.builder().build();
GrepTool grepTool = GrepTool.builder().build();

// Find all service files
String serviceFiles = globTool.glob("**/*Service.java", "./src");

// Search for TODO comments only in services
String todos = grepTool.grep("TODO", "./src", "**/*Service.java",
    OutputMode.content, null, null, 2, true, null, null, null, null, null);
```

### In AI Agent Workflow

```java
ChatClient chatClient = chatClientBuilder
    .defaultTools(GlobTool.builder().build())
    .defaultTools(FileSystemTools.builder().build())
    .defaultTools(GrepTool.builder().build())
    .build();

// AI can now:
// 1. Use GlobTool to find files
// 2. Use FileSystemTools to read files
// 3. Use GrepTool to search contents

String response = chatClient.prompt()
    .user("Find all controllers and check if they have proper error handling")
    .call()
    .content();
```

## See Also

- [GrepTool](GrepTool.md) - For searching file contents
- [FileSystemTools](FileSystemTools.md) - For reading/writing files
- [ShellTools](ShellTools.md) - For running system commands
