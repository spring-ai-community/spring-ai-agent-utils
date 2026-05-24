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
package org.springaicommunity.agent.aot;

import org.springaicommunity.agent.tools.SkillsTool;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

/**
 * {@link RuntimeHintsRegistrar} for GraalVM native image support.
 *
 * @author Christian Tzolov
 */
public class AgentUtilsRuntimeHints implements RuntimeHintsRegistrar {

	@Override
	public void registerHints(RuntimeHints hints, ClassLoader classLoader) {

		// Built-in library classpath resources
		hints.resources().registerPattern("agent/*.md");
		hints.resources().registerPattern("prompt/*.md");

		// User-provided skills — conventional classpath location
		hints.resources().registerPattern("META-INF/skills/**/*.md");

		// Reflection for SkillsTool inner types used via tool invocation
		hints.reflection().registerType(SkillsTool.SkillsInput.class, MemberCategory.values());
		hints.reflection().registerType(SkillsTool.SkillsFunction.class, MemberCategory.values());
	}

}
