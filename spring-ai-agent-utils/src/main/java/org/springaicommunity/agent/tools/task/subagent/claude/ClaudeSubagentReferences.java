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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.springaicommunity.agent.common.task.subagent.SubagentReference;

import org.springframework.core.io.Resource;

/**
 * Factory methods for creating Claude subagent references from directories or resources.
 *
 * @author Christian Tzolov
 */
public class ClaudeSubagentReferences {

	public static List<SubagentReference> fromRootDirectories(List<String> taskRootDirectories) {

		List<SubagentReference> subagentReferences = new ArrayList<>();

		for (String taskRootDirectory : taskRootDirectories) {
			subagentReferences.addAll(fromRootDirectory(taskRootDirectory));
		}

		return subagentReferences;
	}

	public static List<SubagentReference> fromRootDirectory(String rootDirectory) {

		Path rootPath = Paths.get(rootDirectory);

		if (!Files.exists(rootPath)) {
			throw new RuntimeException("Root directory does not exist: " + rootDirectory);
		}

		if (!Files.isDirectory(rootPath)) {
			throw new RuntimeException("Path is not a directory: " + rootDirectory);
		}

		List<SubagentReference> subagentReferences = new ArrayList<>();

		try {
			try (Stream<Path> paths = Files.walk(rootPath)) {
				paths.filter(Files::isRegularFile)
					.filter(path -> path.getFileName().toString().endsWith(".md"))
					.forEach(path -> {
						subagentReferences.add(new SubagentReference(path.toAbsolutePath().toString(),
								ClaudeSubagentDefinition.KIND, null));
					});
			}
		}
		catch (IOException ex) {
			throw new RuntimeException("Failed to read tasks from directory: " + rootDirectory, ex);
		}

		return subagentReferences;
	}

	public static List<SubagentReference> fromResources(Resource... resources) {
		return Arrays.stream(resources).map(ClaudeSubagentReferences::fromResource).flatMap(List::stream).toList();
	}

	public static List<SubagentReference> fromResources(List<Resource> resources) {
		return resources.stream().map(ClaudeSubagentReferences::fromResource).flatMap(List::stream).toList();
	}

	public static List<SubagentReference> fromResource(Resource agentRootPath) {
		try {
			String path = agentRootPath.getFile().toPath().toAbsolutePath().toString();
			if (agentRootPath.getFile().isDirectory()) {
				return fromRootDirectory(path);
			}

			return List.of(new SubagentReference(path, ClaudeSubagentDefinition.KIND, null));
		}
		catch (IOException ex) {
			throw new RuntimeException("Failed to load tasks from directory: " + agentRootPath, ex);
		}
	}

}