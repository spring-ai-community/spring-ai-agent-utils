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

/**
 * Observes individual tool invocations when attached via
 * {@link ToolCallListeners#wrap(org.springframework.ai.tool.ToolCallback, ToolCallListener)}.
 * Typical uses: an audit/event log of {@code tool_use}/{@code tool_result} pairs, SSE
 * progress streaming, per-call metrics.
 *
 * <p>
 * {@link #beforeCall} may return an opaque correlation context that is handed back to
 * {@link #afterCall}/{@link #onError} for the same invocation — enough to correlate
 * use/result pairs (e.g. an event id) or measure durations, without the decorator
 * imposing a schema. All default implementations are no-ops.
 *
 * <p>
 * <b>Listener methods must not throw.</b> The decorator deliberately does not guard
 * listener calls — observability failures stay loud instead of leaving silent gaps in an
 * audit trail — which gives listener exceptions sharp edges: a throwing
 * {@link #beforeCall} prevents the tool from running; a throwing {@link #afterCall}
 * discards a tool result that was <em>successfully produced</em> (its side effects have
 * already happened, and the model may retry the tool); a throwing {@link #onError}
 * replaces the original tool exception. Catch and handle your own failures inside the
 * listener.
 *
 * @author Christian Tzolov
 */
public interface ToolCallListener {

	/**
	 * Called before the tool executes.
	 * @param toolName the tool's definition name
	 * @param toolInput the raw JSON input the model produced
	 * @return an opaque correlation context passed to {@link #afterCall}/{@link #onError}
	 * for this invocation; may be null
	 */
	default Object beforeCall(String toolName, String toolInput) {
		return null;
	}

	/**
	 * Called after the tool returned normally.
	 * @param context the value returned by {@link #beforeCall} (may be null)
	 * @param toolName the tool's definition name
	 * @param toolInput the raw JSON input
	 * @param result the tool's result string
	 */
	default void afterCall(Object context, String toolName, String toolInput, String result) {
	}

	/**
	 * Called when the tool threw. Return a non-null string to report the failure to the
	 * model as the tool result — letting the agent loop adapt instead of dying — or null
	 * to propagate the exception to the caller.
	 * @param context the value returned by {@link #beforeCall} (may be null)
	 * @param toolName the tool's definition name
	 * @param toolInput the raw JSON input
	 * @param exception the failure
	 * @return the error string to return to the model, or null to rethrow
	 */
	default String onError(Object context, String toolName, String toolInput, RuntimeException exception) {
		return null;
	}

}
