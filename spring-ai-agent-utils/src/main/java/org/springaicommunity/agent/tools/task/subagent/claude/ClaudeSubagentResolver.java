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
package org.springaicommunity.agent.tools.task.subagent.claude;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springaicommunity.agent.tools.task.subagent.SubagentDefinition;
import org.springaicommunity.agent.tools.task.subagent.SubagentReference;
import org.springaicommunity.agent.tools.task.subagent.SubagentResolver;
import org.springaicommunity.agent.utils.MarkdownParser;

import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.util.Assert;

/**
 * Resolves Claude subagent references by parsing markdown files with YAML frontmatter.
 *
 * @author Christian Tzolov
 */
public class ClaudeSubagentResolver implements SubagentResolver {

	@Override
	public boolean canResolve(SubagentReference subagentRef) {
		Assert.notNull(subagentRef, "SubagentReference must not be null");
		return subagentRef.kind().equals(ClaudeSubagentDefinition.KIND);
	}

	@Override
	public SubagentDefinition resolve(SubagentReference subagentRef) {
		Assert.notNull(subagentRef, "SubagentReference must not be null");
		Assert.isTrue(subagentRef.kind().equals(ClaudeSubagentDefinition.KIND),
				"ClaudeSubagentResolver can resolve only subagents of kind: " + ClaudeSubagentDefinition.KIND);

		try {
			String uri = (subagentRef.uri().startsWith("/"))? "file:" + subagentRef.uri() : subagentRef.uri();
			var resource = new DefaultResourceLoader().getResource(uri);
			String markdown = resource.getContentAsString(StandardCharsets.UTF_8);
			MarkdownParser parser = new MarkdownParser(markdown);

			return new ClaudeSubagentDefinition(subagentRef, parser.getFrontMatter(), parser.getContent());
		}
		catch (IOException e) {
			throw new RuntimeException("Failed to read task file: " + subagentRef.uri(), e);
		}
	}

}
