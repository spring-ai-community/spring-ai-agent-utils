package org.springaicommunity.skills;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.util.StringUtils;

public class MyLoggingAdvisor implements BaseAdvisor {

	private final int order;

	public final boolean showSystemMessage;

	public final boolean showAvailableTools;

	private MyLoggingAdvisor(int order, boolean showSystemMessage, boolean showAvailableTools) {
		this.order = order;
		this.showSystemMessage = showSystemMessage;
		this.showAvailableTools = showAvailableTools;
	}

	@Override
	public int getOrder() {
		return this.order;
	}

	@Override
	public ChatClientRequest before(ChatClientRequest chatClientRequest, AdvisorChain advisorChain) {

		StringBuilder sb = new StringBuilder("\nUSER: ");

		if (this.showSystemMessage && chatClientRequest.prompt().getSystemMessage() != null) {
			sb.append("\n - SYSTEM: " + first(chatClientRequest.prompt().getSystemMessage().getText(), 60));
		}

		if (this.showAvailableTools) {
			Object tools = "No Tools";

			if (chatClientRequest.prompt().getOptions() instanceof ToolCallingChatOptions toolOptions) {
				tools = toolOptions.getToolCallbacks().stream().map(tc -> tc.getToolDefinition().name()).toList();
			}

			sb.append("\n - TOOLS: " + ModelOptionsUtils.toJsonString(tools));
		}

		Message lastMessage = chatClientRequest.prompt().getLastUserOrToolResponseMessage();

		if (lastMessage.getMessageType() == MessageType.TOOL) {
			ToolResponseMessage toolResponseMessage = (ToolResponseMessage) lastMessage;
			for (var toolResponse : toolResponseMessage.getResponses()) {
				var tr = toolResponse.name() + ": " + first(toolResponse.responseData(), 300);
				sb.append("\n - TOOL-RESPONSE: " + tr);
			}
		}
		else if (lastMessage.getMessageType() == MessageType.USER) {
			if (StringUtils.hasText(lastMessage.getText())) {
				sb.append("\n - TEXT: " + first(lastMessage.getText(), 300));
			}
		}

		System.out.println(sb.toString());

		return chatClientRequest;
	}

	@Override
	public ChatClientResponse after(ChatClientResponse chatClientResponse, AdvisorChain advisorChain) {
		StringBuilder sb = new StringBuilder("\nASSISTANT: ");

		if (chatClientResponse.chatResponse() == null || chatClientResponse.chatResponse().getResults() == null) {
			sb.append(" No chat response ");
			System.out.println(sb.toString());
			return chatClientResponse;
		}

		for (var generation : chatClientResponse.chatResponse().getResults()) {
			var message = generation.getOutput();
			if (message.getToolCalls() != null) {
				for (var toolCall : message.getToolCalls()) {
					sb.append("\n - TOOL-CALL: ")
						.append(toolCall.name())
						.append(" (")
						.append(toolCall.arguments())
						.append(")");
				}
			}

			if (message.getText() != null) {
				if (StringUtils.hasText(message.getText())) {
					sb.append("\n - TEXT: " + first(message.getText(), 200));
				}
			}
		}

		System.out.println(sb.toString());

		return chatClientResponse;
	}

	private String first(String text, int n) {
		if (text.length() <= n) {
			return text;
		}
		return text.substring(0, n) + "...";
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private int order = 0;

		private boolean showSystemMessage = true;

		private boolean showAvailableTools = true;

		public Builder order(int order) {
			this.order = order;
			return this;
		}

		public Builder showSystemMessage(boolean showSystemMessage) {
			this.showSystemMessage = showSystemMessage;
			return this;
		}

		public Builder showAvailableTools(boolean showAvailableTools) {
			this.showAvailableTools = showAvailableTools;
			return this;
		}

		public MyLoggingAdvisor build() {
			MyLoggingAdvisor advisor = new MyLoggingAdvisor(this.order, this.showSystemMessage,
					this.showAvailableTools);
			return advisor;
		}

	}

}