package com.autoops.chat.model;

import com.autoops.host.model.Host;
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
    private Map<String, Object> lastToolCall;
    private List<Map<String, Object>> messagesWithToolCalls;
    private Host selectedHost;

    public ChatSession(String sessionId) {
        this.sessionId = sessionId;
        this.messages = new ArrayList<>();
        this.messagesWithToolCalls = new ArrayList<>();
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
    
    public void setLastToolCall(Map<String, Object> toolCall) {
        this.lastToolCall = toolCall;
    }
    
    public void addMessagesWithToolCalls(List<Map<String, Object>> msgs) {
        this.messagesWithToolCalls.addAll(msgs);
    }
    
    public List<Map<String, Object>> getMessagesWithToolCalls() {
        return this.messagesWithToolCalls;
    }
    
    public void setSelectedHost(Host host) {
        this.selectedHost = host;
    }
}