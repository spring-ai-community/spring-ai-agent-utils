/*
 * Copyright 2026 - 2026 the original author or authors.
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
package org.springaicommunity.agent.common.skill;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Describes a skill's identity and metadata for tool registration.
 *
 * <p>
 * Skill descriptors are used at build time to generate the tool description XML that
 * tells the LLM which skills are available. The actual skill content is fetched lazily
 * via {@link SkillProvider#getSkillContent(String)}.
 *
 * @author Daniel Volovik
 */
public interface SkillDescriptor {

	/** Returns the unique name of this skill. */
	String getName();

	/** Returns the metadata (front-matter) for this skill. */
	Map<String, Object> getMetadata();

	/** Formats this skill's metadata as an XML block for the tool description. */
	default String toXml() {
		String frontMatterXml = getMetadata().entrySet()
			.stream()
			.map(e -> "  <%s>%s</%s>".formatted(e.getKey(), e.getValue(), e.getKey()))
			.collect(Collectors.joining("\n"));

		return "<skill>\n%s\n</skill>".formatted(frontMatterXml);
	}

}