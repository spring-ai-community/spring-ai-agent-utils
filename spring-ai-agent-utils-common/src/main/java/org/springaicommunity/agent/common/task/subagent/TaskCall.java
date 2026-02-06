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
package org.springaicommunity.agent.common.task.subagent;

import org.springframework.ai.tool.annotation.ToolParam;

public record TaskCall( // @formatter:off
		@ToolParam(description = "A short (3-5 word) description of the task") String description,
		@ToolParam(description = "The task for the agent to perform") String prompt,
		@ToolParam(description = "The type of specialized agent to use for this task") String subagent_type,
		@ToolParam(description = "Optional model to use for this agent. If not specified, inherits from parent. Prefer small models for quick, straightforward tasks to minimize cost and latency.", required = false) String model,
		@ToolParam(description = "Optional agent ID to resume from. If provided, the agent will continue from the previous execution transcript.", required = false) String resume,
		@ToolParam(description = "Set to true to run this agent in the background. Use TaskOutput to read the output later.", required = false) Boolean run_in_background ) { // @formatter:on
}