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

/**
 * Execution backend SPI: where model-authored shell commands run. Tools submit an
 * {@link org.springaicommunity.agent.common.exec.ExecSpec} and consume an
 * {@link org.springaicommunity.agent.common.exec.ExecResult} or
 * {@link org.springaicommunity.agent.common.exec.ExecHandle}; the
 * {@link org.springaicommunity.agent.common.exec.ExecBackend} implementation decides the
 * shell, working directory, and environment — host JVM by default, container or remote
 * worker in sandboxed deployments.
 */
package org.springaicommunity.agent.common.exec;
