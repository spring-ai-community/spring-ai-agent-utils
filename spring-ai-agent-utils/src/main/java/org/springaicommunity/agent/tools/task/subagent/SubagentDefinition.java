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
package org.springaicommunity.agent.tools.task.subagent;

/**
 * Defines a subagent's identity and configuration metadata.
 *
 * @author Christian Tzolov
 */
public interface SubagentDefinition {

	/** Returns the unique name of this subagent. */
	String getName();

	/** Returns the description of this subagent's capabilities. */
	String getDescription();

	/** Returns the kind/type identifier (e.g., "CLAUDE"). */
	String getKind();

	/** Returns the reference used to resolve this definition. */
	SubagentReference getReference();

	/** Formats this subagent for registration display. */
	default public String toSubagentRegistrations() {
		return "-%s: /%s".formatted(getName(), getDescription());
	}

}
