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

import java.util.List;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.util.Assert;

/**
 * Attaches a {@link ToolCallListener} to {@link ToolCallback}s by decoration. The
 * decorator delegates {@code getToolDefinition()}/{@code getToolMetadata()} untouched, so
 * it composes with any callback source ({@code ToolCallbacks.from(...)},
 * {@code FunctionToolCallback}, the skills tool, ...).
 *
 * <pre>{@code
 * List<ToolCallback> observed = ToolCallListeners.wrapAll(callbacks, listener);
 * }</pre>
 *
 * @author Christian Tzolov
 * @see ToolCallListener
 */
public final class ToolCallListeners {

	private ToolCallListeners() {
	}

	/** Decorates one callback so the listener observes each invocation. */
	public static ToolCallback wrap(ToolCallback callback, ToolCallListener listener) {
		Assert.notNull(callback, "callback must not be null");
		Assert.notNull(listener, "listener must not be null");
		return new ListeningToolCallback(callback, listener);
	}

	/** Decorates every callback in the list with the same listener. */
	public static List<ToolCallback> wrapAll(List<ToolCallback> callbacks, ToolCallListener listener) {
		Assert.notNull(callbacks, "callbacks must not be null");
		return callbacks.stream().map(callback -> wrap(callback, listener)).toList();
	}

	private static final class ListeningToolCallback implements ToolCallback {

		private final ToolCallback delegate;

		private final ToolCallListener listener;

		ListeningToolCallback(ToolCallback delegate, ToolCallListener listener) {
			this.delegate = delegate;
			this.listener = listener;
		}

		@Override
		public ToolDefinition getToolDefinition() {
			return this.delegate.getToolDefinition();
		}

		@Override
		public ToolMetadata getToolMetadata() {
			return this.delegate.getToolMetadata();
		}

		@Override
		public String call(String toolInput) {
			return call(toolInput, null);
		}

		@Override
		public String call(String toolInput, ToolContext toolContext) {
			String toolName = getToolDefinition().name();
			Object context = this.listener.beforeCall(toolName, toolInput);
			try {
				String result = this.delegate.call(toolInput, toolContext);
				this.listener.afterCall(context, toolName, toolInput, result);
				return result;
			}
			catch (RuntimeException ex) {
				String reported = this.listener.onError(context, toolName, toolInput, ex);
				if (reported != null) {
					return reported;
				}
				throw ex;
			}
		}

	}

}
