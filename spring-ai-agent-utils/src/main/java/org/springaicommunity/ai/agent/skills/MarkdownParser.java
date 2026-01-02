/*
* Copyright 2025 - 2025 the original author or authors.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* https://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package org.springaicommunity.ai.agent.skills;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.springframework.core.io.DefaultResourceLoader;

/**
 * @author Christian Tzolov
 */
public class MarkdownParser {

	private Map<String, String> frontMatter;

	private String content;

	public MarkdownParser(String markdown) {
		parse(markdown);
	}

	private void parse(String markdown) {
		frontMatter = new HashMap<>();
		content = "";

		if (markdown == null || markdown.isEmpty()) {
			return;
		}

		// Check if document starts with front-matter delimiter (---)
		if (markdown.startsWith("---")) {
			// Find the closing delimiter
			int endIndex = markdown.indexOf("---", 3);

			if (endIndex != -1) {
				// Extract front-matter section
				String frontMatterSection = markdown.substring(3, endIndex).trim();
				parseFrontMatter(frontMatterSection);

				// Extract remaining content (skip the closing --- and any following
				// newlines)
				content = markdown.substring(endIndex + 3).trim();
			}
			else {
				// No closing delimiter found, treat entire document as content
				content = markdown;
			}
		}
		else {
			// No front-matter, entire document is content
			content = markdown;
		}
	}

	private void parseFrontMatter(String frontMatterSection) {
		String[] lines = frontMatterSection.split("\n");

		for (String line : lines) {
			line = line.trim();

			if (line.isEmpty()) {
				continue;
			}

			// Split on first colon
			int colonIndex = line.indexOf(':');
			if (colonIndex > 0) {
				String key = line.substring(0, colonIndex).trim();
				String value = line.substring(colonIndex + 1).trim();

				// Remove quotes if present
				value = removeQuotes(value);

				frontMatter.put(key, value);
			}
		}
	}

	private String removeQuotes(String value) {
		if (value.length() >= 2) {
			if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
				return value.substring(1, value.length() - 1);
			}
		}
		return value;
	}

	public Map<String, String> getFrontMatter() {
		return new HashMap<>(frontMatter);
	}

	public String getContent() {
		return content;
	}

	// Usage example
	public static void main(String[] args) throws IOException {
		String markdown1 = """
				---
				title: My Blog Post
				author: John Doe
				date: 2024-01-15
				tags: "java, markdown, parsing"
				---

				# Welcome to My Blog

				This is the **content** of my markdown document.

				- Item 1
				- Item 2
				""";

		String markdown = new DefaultResourceLoader()
			.getResource("file:/Users/christiantzolov/.claude/skills/ai-tuto/SKILL.md")
			.getContentAsString(StandardCharsets.UTF_8);

		MarkdownParser parser = new MarkdownParser(markdown);

		System.out.println("Front Matter:");
		parser.getFrontMatter().forEach((key, value) -> System.out.println("  " + key + ": " + value));

		System.out.println("\nContent:");
		System.out.println(parser.getContent());
	}

}
