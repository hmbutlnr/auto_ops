package com.autoops.chat.controller;

import com.autoops.chat.dto.ChatRequest;
import com.autoops.chat.dto.ChatResponse;
import com.autoops.chat.model.ChatSession;
import com.autoops.chat.service.ChatServiceWithFunctionCalling;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "*")
public class ChatController {

    private final ChatServiceWithFunctionCalling chatService;

    public ChatController(ChatServiceWithFunctionCalling chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/chat")
    public ChatResponse chat(@RequestBody ChatRequest request) {
        try {
            String sessionId = request.getSessionId();
            if (sessionId == null || sessionId.isEmpty()) {
                sessionId = UUID.randomUUID().toString();
                chatService.createSession(sessionId);
            }
            String reply = chatService.chat(sessionId, request.getMessage());
            return new ChatResponse(sessionId, reply, true, null);
        } catch (Exception e) {
            return new ChatResponse(null, null, false, e.getMessage());
        }
    }

    @GetMapping("/session/{sessionId}")
    public ChatSession getSession(@PathVariable String sessionId) {
        return chatService.getSession(sessionId);
    }

    @PostMapping("/new-session")
    public String createNewSession() {
        String sessionId = UUID.randomUUID().toString();
        chatService.createSession(sessionId);
        return sessionId;
    }
}