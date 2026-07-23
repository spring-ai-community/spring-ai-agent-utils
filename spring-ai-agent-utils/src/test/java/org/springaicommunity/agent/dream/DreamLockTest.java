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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link DreamLock}.
 *
 * @author Christian Tzolov
 */
@DisplayName("DreamLock Tests")
class DreamLockTest {

	@TempDir
	Path tempDir;

	@Test
	@DisplayName("First acquisition succeeds")
	void firstAcquisitionSucceeds() {
		Optional<DreamLock> lock = DreamLock.tryAcquire(this.tempDir, Duration.ofMinutes(15));

		assertThat(lock).isPresent();
		assertThat(this.tempDir.resolve(".dream.lock")).exists();
	}

	@Test
	@DisplayName("Second acquisition fails while the first is held")
	void secondAcquisitionFailsWhileHeld() {
		Optional<DreamLock> first = DreamLock.tryAcquire(this.tempDir, Duration.ofMinutes(15));
		assertThat(first).isPresent();

		Optional<DreamLock> second = DreamLock.tryAcquire(this.tempDir, Duration.ofMinutes(15));

		assertThat(second).isEmpty();
	}

	@Test
	@DisplayName("Acquisition succeeds again after close() releases the lock")
	void reacquisitionSucceedsAfterClose() {
		Optional<DreamLock> first = DreamLock.tryAcquire(this.tempDir, Duration.ofMinutes(15));
		assertThat(first).isPresent();
		first.get().close();

		Optional<DreamLock> second = DreamLock.tryAcquire(this.tempDir, Duration.ofMinutes(15));

		assertThat(second).isPresent();
		assertThat(this.tempDir.resolve(".dream.lock")).exists();
	}

	@Test
	@DisplayName("close() deletes the lock file")
	void closeDeletesLockFile() {
		DreamLock lock = DreamLock.tryAcquire(this.tempDir, Duration.ofMinutes(15)).orElseThrow();

		lock.close();

		assertThat(this.tempDir.resolve(".dream.lock")).doesNotExist();
	}

	@Test
	@DisplayName("A lock older than staleAfter is reclaimed automatically")
	void staleLockIsReclaimed() throws Exception {
		Path lockFile = this.tempDir.resolve(".dream.lock");
		long ancientTimestamp = Instant.now().minus(Duration.ofHours(1)).toEpochMilli();
		Files.writeString(lockFile, Long.toString(ancientTimestamp), StandardOpenOption.CREATE_NEW);

		Optional<DreamLock> reclaimed = DreamLock.tryAcquire(this.tempDir, Duration.ofMinutes(15));

		assertThat(reclaimed).isPresent();
	}

	@Test
	@DisplayName("A lock younger than staleAfter is not reclaimed")
	void freshLockIsNotReclaimed() throws Exception {
		Path lockFile = this.tempDir.resolve(".dream.lock");
		Files.writeString(lockFile, Long.toString(Instant.now().toEpochMilli()), StandardOpenOption.CREATE_NEW);

		Optional<DreamLock> attempt = DreamLock.tryAcquire(this.tempDir, Duration.ofMinutes(15));

		assertThat(attempt).isEmpty();
	}

	@Test
	@DisplayName("An unreadable lock file is treated as stale and reclaimed")
	void unreadableLockFileIsReclaimed() throws Exception {
		Path lockFile = this.tempDir.resolve(".dream.lock");
		Files.writeString(lockFile, "not-a-timestamp", StandardOpenOption.CREATE_NEW);

		Optional<DreamLock> reclaimed = DreamLock.tryAcquire(this.tempDir, Duration.ofMinutes(15));

		assertThat(reclaimed).isPresent();
	}

}
