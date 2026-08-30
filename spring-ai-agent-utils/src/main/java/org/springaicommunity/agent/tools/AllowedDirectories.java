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
package org.springaicommunity.agent.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Shared directory-confinement check for tools that accept model-supplied paths
 * (FileSystemTools, GrepTool, GlobTool, ListDirectoryTool). One implementation, one set
 * of semantics: when no allowed directories are configured, all paths are allowed; when
 * configured, a path must fall within at least one allowed directory.
 *
 * <p>
 * Extracted from {@code FileSystemTools.validateAllowedAccess} so the search tools get
 * the same containment as Read/Write/Edit instead of three copies of the check.
 *
 * @author Christian Tzolov
 */
final class AllowedDirectories {

	private final List<Path> allowedDirectories;

	AllowedDirectories(List<Path> allowedDirectories) {
		this.allowedDirectories = List.copyOf(allowedDirectories);
	}

	/** Whether any confinement is configured (empty = unrestricted). */
	boolean isRestricted() {
		return !this.allowedDirectories.isEmpty();
	}

	/**
	 * Validates that the given file path is within at least one configured allowed
	 * directory. When no allowed directories are configured, all paths are allowed. Uses
	 * three checks per directory candidate: (1) rejects raw {@code ..} path components to
	 * prevent symlink+{@code ..} escapes, (2) normalized path containment check to block
	 * remaining {@code ..} traversal, (3) real-path resolution walking up existing path
	 * components to catch symlink escapes including dangling symlinks (skipped when the
	 * candidate directory does not yet exist).
	 * @param filePath the path to validate
	 * @return an error string if access is denied, or {@code null} if access is allowed
	 */
	String validate(String filePath) {
		if (this.allowedDirectories.isEmpty()) {
			return null;
		}
		try {
			Path targetAbs = Paths.get(filePath).toAbsolutePath();

			// Check 1: reject '..' components in the raw absolute path.
			// Normalizing before this check would hide symlink+'..' bypass attempts
			// (e.g. /allowed/link/../outside normalizes to /allowed/outside but the OS
			// resolves 'link' as a symlink first, landing outside the allowed directory).
			for (Path component : targetAbs) {
				if ("..".equals(component.toString())) {
					return "Error: Access denied. Path is outside the allowed directories: " + filePath;
				}
			}

			Path target = targetAbs.normalize();

			for (Path allowedDir : this.allowedDirectories) {
				Path allowed = allowedDir.toAbsolutePath().normalize();
				Path realAllowed = Files.exists(allowed) ? allowed.toRealPath() : null;

				// Check 2: normalized path must start with this candidate (blocks
				// remaining traversal). A canonical path is also accepted when the
				// configured directory has a symlinked prefix (e.g. /tmp -> /private/tmp
				// on macOS), matching against the real form of the allowed directory.
				if (!target.startsWith(allowed) && (realAllowed == null || !target.startsWith(realAllowed))) {
					continue;
				}

				// Check 3: resolve symlinks in all existing path components to catch
				// symlink escapes. Skipped when the candidate directory does not yet
				// exist (checks 1+2 are sufficient then).
				if (realAllowed != null) {
					Path existing = target;
					while (existing != null && !Files.exists(existing, LinkOption.NOFOLLOW_LINKS)) {
						existing = existing.getParent();
					}
					if (existing != null) {
						Path realExisting;
						try {
							realExisting = existing.toRealPath();
						}
						catch (IOException e) {
							// toRealPath() throws on a dangling symlink (symlink exists
							// but target does not). We cannot verify where it points, so
							// deny access unconditionally.
							return "Error: Access denied. Cannot resolve path (possible dangling symlink): " + filePath;
						}
						if (!realExisting.startsWith(realAllowed)) {
							continue;
						}
					}
				}

				return null; // path is within this allowed directory
			}

			return "Error: Access denied. Path is outside the allowed directories: " + filePath;
		}
		catch (RuntimeException e) {
			return "Error: Invalid path: " + e.getMessage();
		}
		catch (IOException e) {
			return "Error validating path: " + e.getMessage();
		}
	}

	/**
	 * Builder-side collector shared by the tool builders (FileSystemTools, GrepTool,
	 * GlobTool, ListDirectoryTool) so the configuration logic — null handling, string
	 * conversion — lives in one place next to the validation it feeds.
	 */
	static final class Collector {

		private final java.util.List<Path> directories = new java.util.ArrayList<>();

		void add(Path directory) {
			if (directory != null) {
				this.directories.add(directory);
			}
		}

		void add(String directory) {
			if (directory != null) {
				this.directories.add(Paths.get(directory));
			}
		}

		void addAll(Path... directories) {
			for (Path directory : directories) {
				add(directory);
			}
		}

		java.util.List<Path> toList() {
			return java.util.List.copyOf(this.directories);
		}

	}

}
