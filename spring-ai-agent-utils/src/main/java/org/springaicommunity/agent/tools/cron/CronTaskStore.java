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
package org.springaicommunity.agent.tools.cron;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads and writes durable cron tasks to an NDJSON file on disk.
 *
 * <p>
 * Each durable task is stored as a single JSON line. On save the file is written
 * atomically (write to temp file then rename) to avoid corruption. On load, malformed
 * lines are silently skipped.
 * </p>
 *
 * @author Christian Tzolov
 */
public class CronTaskStore {

	private static final Logger logger = LoggerFactory.getLogger(CronTaskStore.class);

	private final Path file;

	public CronTaskStore(Path file) {
		this.file = file;
	}

	/**
	 * Load all durable tasks from the persistence file. Returns an empty list if the file
	 * does not exist or is unreadable.
	 */
	public List<CronTask> load() {
		if (!Files.exists(file)) {
			return List.of();
		}
		try {
			List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
			List<CronTask> tasks = new ArrayList<>();
			for (String line : lines) {
				line = line.trim();
				if (line.isEmpty()) {
					continue;
				}
				try {
					tasks.add(parseLine(line));
				}
				catch (Exception e) {
					logger.warn("Skipping malformed cron task line: {}", line, e);
				}
			}
			return List.copyOf(tasks);
		}
		catch (IOException e) {
			logger.warn("Failed to read durable tasks file: {}", file, e);
			return List.of();
		}
	}

	/**
	 * Persist all durable tasks to the file atomically.
	 */
	public void save(List<CronTask> tasks) {
		Path parent = file.getParent();
		if (parent != null) {
			try {
				Files.createDirectories(parent);
			}
			catch (IOException e) {
				logger.warn("Failed to create parent directories for {}", file, e);
				return;
			}
		}
		Path tmp = file.resolveSibling(file.getFileName().toString() + ".tmp");
		try {
			String content = tasks.stream().map(this::formatLine).collect(Collectors.joining("\n"));
			if (!content.isEmpty()) {
				content += "\n";
			}
			Files.writeString(tmp, content, StandardCharsets.UTF_8);
			Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		}
		catch (IOException e) {
			logger.warn("Failed to save durable tasks to {}", file, e);
			try {
				Files.deleteIfExists(tmp);
			}
			catch (IOException ignored) {
			}
		}
	}

	/**
	 * Remove the persistence file entirely (used when all durable tasks are deleted).
	 */
	public void delete() {
		try {
			Files.deleteIfExists(file);
		}
		catch (IOException e) {
			logger.warn("Failed to delete durable tasks file: {}", file, e);
		}
	}

	private CronTask parseLine(String line) {
		// Simple manual JSON parsing to avoid extra dependencies.
		// Format: {"id":"...","cron":"...","prompt":"...","recurring":bool,"durable":bool,"nextFireTime":"..."}
		// Validate the line looks like a JSON object
		String trimmed = line.trim();
		if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
			throw new IllegalArgumentException("Line does not look like a JSON object");
		}
		String id = extractString(line, "id");
		if (id == null || id.isEmpty()) {
			throw new IllegalArgumentException("Missing required field: id");
		}
		String cron = extractString(line, "cron");
		if (cron == null || cron.isEmpty()) {
			throw new IllegalArgumentException("Missing required field: cron");
		}
		String prompt = extractString(line, "prompt");
		boolean recurring = extractBoolean(line, "recurring");
		boolean durable = extractBoolean(line, "durable");
		String nextFireTimeStr = extractString(line, "nextFireTime");
		Instant nextFireTime = nextFireTimeStr != null ? Instant.parse(nextFireTimeStr) : null;
		String createdAtStr = extractString(line, "createdAt");
		Instant createdAt = createdAtStr != null ? Instant.parse(createdAtStr) : Instant.now();
		return new CronTask(id, cron, prompt, recurring, durable, nextFireTime, createdAt);
	}

	private String formatLine(CronTask task) {
		StringBuilder sb = new StringBuilder();
		sb.append("{\"id\":\"").append(escape(task.id())).append("\"");
		sb.append(",\"cron\":\"").append(escape(task.cron())).append("\"");
		sb.append(",\"prompt\":\"").append(escape(task.prompt())).append("\"");
		sb.append(",\"recurring\":").append(task.recurring());
		sb.append(",\"durable\":").append(task.durable());
		if (task.nextFireTime() != null) {
			sb.append(",\"nextFireTime\":\"").append(task.nextFireTime().toString()).append("\"");
		}
		if (task.createdAt() != null) {
			sb.append(",\"createdAt\":\"").append(task.createdAt().toString()).append("\"");
		}
		sb.append("}");
		return sb.toString();
	}

	private String extractString(String json, String key) {
		String prefix = "\"" + key + "\":\"";
		int start = json.indexOf(prefix);
		if (start == -1) {
			return null;
		}
		start += prefix.length();
		StringBuilder value = new StringBuilder();
		for (int i = start; i < json.length(); i++) {
			char c = json.charAt(i);
			if (c == '\\' && i + 1 < json.length()) {
				value.append(json.charAt(i + 1));
				i++;
			}
			else if (c == '"') {
				break;
			}
			else {
				value.append(c);
			}
		}
		return value.toString();
	}

	private boolean extractBoolean(String json, String key) {
		String prefix = "\"" + key + "\":";
		int start = json.indexOf(prefix);
		if (start == -1) {
			return false;
		}
		start += prefix.length();
		// Skip whitespace
		while (start < json.length() && Character.isWhitespace(json.charAt(start))) {
			start++;
		}
		if (start >= json.length()) {
			return false;
		}
		return json.startsWith("true", start);
	}

	private String escape(String s) {
		if (s == null) {
			return "";
		}
		return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
	}

}
