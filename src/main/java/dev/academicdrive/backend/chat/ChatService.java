package dev.academicdrive.backend.chat;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class ChatService {
    private final ChatClient chatClient;

    public ChatService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder
                .defaultSystem("You are Academic Drive's study assistant. Be accurate, clear, and concise.")
                .build();
    }

    public ChatResponse chat(ChatRequest request) {
        String reply = chatClient.prompt()
                .user(request.message())
                .call()
                .content();
        if (reply == null || reply.isBlank()) {
            throw new IllegalStateException("The AI provider returned an empty response.");
        }
        return new ChatResponse(reply);
    }
}
