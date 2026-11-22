# ContextWebSearchTool

An optional `WebSearch` tool using [Context.dev web search](https://docs.context.dev/api-reference/web-scraping/search). It accepts the same query and domain-filter arguments as `BraveWebSearchTool` and returns JSON results with `title`, `url`, and `description`.

Register **Context or Brave**, not both: they expose the same `WebSearch` tool name. Existing examples and Claude subagent defaults continue to use Brave.

```java
import java.util.List;

import org.springaicommunity.agent.tools.ContextWebSearchTool;

var webSearch = ContextWebSearchTool.builder(System.getenv("CONTEXT_DEV_API_KEY"))
    .resultCount(5)
    .build();

// Register explicitly with your existing ChatClient builder.
var chatClient = chatClientBuilder.defaultTools(webSearch).build();

// Or invoke the tool directly.
String results = webSearch.webSearch("Spring AI tools", List.of("spring.io"), null);
```

A Context API key and account credits are required. The result count defaults to 10 (range 1–100). Optional allowed and blocked domains are sent as native Context filters; each list accepts up to 100 domains. Queries must contain 1–500 characters.

Descriptions use successful, nonempty query-relevant Highlights by default. Results without usable Highlights are omitted; if none are usable, the tool fails explicitly. An actual empty search result remains an empty JSON array. Incomplete responses and provider failures raise a sanitized exception rather than appearing as empty results. Search Highlights are discovery evidence, not a guarantee of a fresh direct page read.

The tool uses a fixed Context endpoint, Bearer-header authentication, a 30-second request/read deadline, and no redirects. It does not request zero data retention or alter the host application's logging, caching, tracing, or privacy settings.
