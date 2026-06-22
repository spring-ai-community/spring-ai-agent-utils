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

import java.util.List;

/**
 * Service provider interface for pluggable skill backends.
 *
 * <p>
 * Implementations provide skill discovery and content retrieval from any backing store
 * (filesystem, database, API, etc.). The {@link #getSkillDescriptors()} method is called
 * at build time to enumerate available skills for the tool description, while
 * {@link #getSkillContent(String)} is called at invocation time when the LLM requests a
 * specific skill.
 *
 * @author Daniel Volovik
 * @see SkillDescriptor
 */
public interface SkillProvider {

	/** Returns descriptors for all skills this provider can serve. */
	List<SkillDescriptor> getSkillDescriptors();

	/**
	 * Resolves the full content for a skill by name.
	 * @param skillName the name of the skill to retrieve
	 * @return the skill content, or {@code null} if this provider does not have the
	 * requested skill
	 */
	String getSkillContent(String skillName);

}