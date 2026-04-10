package com.autoops.chat.model;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Data
public class ChatSession {
    private String sessionId;
    private List<ChatMessage> messages;
    private LocalDateTime createdAt;
    private LocalDateTime lastActiveAt;

    public ChatSession(String sessionId) {
        this.sessionId = sessionId;
        this.messages = new ArrayList<>();
        this.createdAt = LocalDateTime.now();
        this.lastActiveAt = LocalDateTime.now();
    }

    public void addUserMessage(String content) {
        messages.add(new ChatMessage("user", content));
        lastActiveAt = LocalDateTime.now();
    }

    public void addAssistantMessage(String content) {
        messages.add(new ChatMessage("assistant", content));
        lastActiveAt = LocalDateTime.now();
    }

    public void addSystemMessage(String content) {
        messages.add(new ChatMessage("system", content));
    }
}