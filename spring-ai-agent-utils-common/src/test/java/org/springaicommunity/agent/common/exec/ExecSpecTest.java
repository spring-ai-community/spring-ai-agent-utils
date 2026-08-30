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
package org.springaicommunity.agent.common.exec;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Timeout policy is owned by the spec so every backend behaves identically.
 *
 * @author Christian Tzolov
 */
class ExecSpecTest {

	@Test
	void nonPositiveTimeoutBecomesDefault() {
		assertEquals(ExecSpec.DEFAULT_TIMEOUT_MILLIS, new ExecSpec("echo hi", 0, Map.of()).timeoutMillis());
		assertEquals(ExecSpec.DEFAULT_TIMEOUT_MILLIS, new ExecSpec("echo hi", -5, Map.of()).timeoutMillis());
	}

	@Test
	void oversizedTimeoutIsClampedToMax() {
		assertEquals(ExecSpec.MAX_TIMEOUT_MILLIS,
				new ExecSpec("echo hi", ExecSpec.MAX_TIMEOUT_MILLIS + 1, Map.of()).timeoutMillis());
	}

	@Test
	void positiveTimeoutIsKept() {
		assertEquals(1, new ExecSpec("echo hi", 1, Map.of()).timeoutMillis());
	}

	@Test
	void blankCommandIsRejected() {
		assertThrows(IllegalArgumentException.class, () -> new ExecSpec("  ", 1000, Map.of()));
	}

	@Test
	void nullEnvBecomesEmpty() {
		assertTrue(new ExecSpec("echo hi", 1000, null).env().isEmpty());
	}

}
