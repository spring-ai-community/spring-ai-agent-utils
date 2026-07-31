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
package org.springaicommunity.agent.dream;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * A file-based lock at {@code <memoriesDir>/.dream.lock} that prevents two dream cycles
 * from running concurrently against the same memory store.
 *
 * <p>
 * Acquisition is atomic ({@code CREATE_NEW}), so it is safe across threads and processes
 * sharing the same filesystem. A lock older than the configured staleness threshold is
 * reclaimed automatically — this covers the case where a previous dream cycle crashed
 * without releasing the lock.
 *
 * @author Christian Tzolov
 */
public final class DreamLock implements AutoCloseable {

	static final String LOCK_FILE_NAME = ".dream.lock";

	private final Path lockFile;

	private DreamLock(Path lockFile) {
		this.lockFile = lockFile;
	}

	/**
	 * Attempts to acquire the dream lock for {@code memoriesDir}.
	 * @param memoriesDir the memories root directory to lock
	 * @param staleAfter a lock older than this age is treated as abandoned and reclaimed
	 * @return the acquired lock, or {@link Optional#empty()} if another dream cycle
	 * already holds it
	 */
	public static Optional<DreamLock> tryAcquire(Path memoriesDir, Duration staleAfter) {
		Path lockFile = memoriesDir.resolve(LOCK_FILE_NAME);

		if (Files.exists(lockFile) && isStale(lockFile, staleAfter)) {
			try {
				Files.deleteIfExists(lockFile);
			}
			catch (IOException ignored) {
				// Best-effort reclamation — if the delete fails, the create attempt
				// below will simply fail too and the caller treats it as "locked".
			}
		}

		try {
			Files.writeString(lockFile, Long.toString(Instant.now().toEpochMilli()), StandardOpenOption.CREATE_NEW);
			return Optional.of(new DreamLock(lockFile));
		}
		catch (FileAlreadyExistsException e) {
			return Optional.empty();
		}
		catch (IOException e) {
			throw new UncheckedIOException("Failed to acquire dream lock at " + lockFile, e);
		}
	}

	private static boolean isStale(Path lockFile, Duration staleAfter) {
		try {
			long acquiredAtMillis = Long.parseLong(Files.readString(lockFile).trim());
			return Instant.now().toEpochMilli() - acquiredAtMillis > staleAfter.toMillis();
		}
		catch (IOException | NumberFormatException e) {
			// Unreadable lock file — treat as stale so a corrupted lock can't strand
			// the feature forever.
			return true;
		}
	}

	/**
	 * Releases the lock by deleting the lock file.
	 */
	@Override
	public void close() {
		try {
			Files.deleteIfExists(this.lockFile);
		}
		catch (IOException e) {
			throw new UncheckedIOException("Failed to release dream lock at " + this.lockFile, e);
		}
	}

}
