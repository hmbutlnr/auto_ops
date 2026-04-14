package com.autoops.chat.controller;

import com.autoops.chat.dto.ChatRequest;
import com.autoops.chat.dto.ChatResponse;
import com.autoops.chat.model.ChatSession;
import com.autoops.chat.service.ChatServiceWithFunctionCalling;
import com.autoops.host.model.Host;
import com.autoops.host.repository.HostRepository;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "*")
public class ChatController {

    private final ChatServiceWithFunctionCalling chatService;
    private final HostRepository hostRepository;

    public ChatController(ChatServiceWithFunctionCalling chatService, HostRepository hostRepository) {
        this.chatService = chatService;
        this.hostRepository = hostRepository;
    }

    @PostMapping("/chat")
    public ChatResponse chat(@RequestBody ChatRequest request) {
        try {
            String sessionId = request.getSessionId();
            if (sessionId == null || sessionId.isEmpty()) {
                sessionId = UUID.randomUUID().toString();
                chatService.createSession(sessionId);
            }
            
            String message = request.getMessage();
            String reply = chatService.chat(sessionId, message, null);
            
            // 检查是否需要弹出主机选择对话框（通过检查特殊标记）
            boolean needsHostSelection = reply != null && reply.contains("__HOST_SELECTION_DIALOG:");
            
            if (needsHostSelection) {
                int pipeIndex = reply.indexOf("|");
                if (pipeIndex > 0) {
                    reply = reply.substring(pipeIndex + 1);
                }
            }
            
            // 获取当前会话选中的主机
            Host selectedHost = chatService.getSelectedHost(sessionId);
            return new ChatResponse(sessionId, reply, true, null, needsHostSelection, null, selectedHost);
        } catch (Exception e) {
            return new ChatResponse(null, null, false, e.getMessage(), false, null, null);
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

    @GetMapping("/session/{sessionId}/selected-host")
    public Map<String, Object> getSelectedHost(@PathVariable String sessionId) {
        Map<String, Object> response = new HashMap<>();
        Host host = chatService.getSelectedHost(sessionId);
        if (host != null) {
            response.put("success", true);
            response.put("host", host);
        } else {
            response.put("success", true);
            response.put("host", null);
        }
        return response;
    }

    @DeleteMapping("/session/{sessionId}/selected-host")
    public Map<String, Object> clearSelectedHost(@PathVariable String sessionId) {
        Map<String, Object> response = new HashMap<>();
        chatService.clearSelectedHost(sessionId);
        response.put("success", true);
        response.put("message", "已清除选中主机");
        return response;
    }

    @PostMapping("/session/{sessionId}/select-host")
    public Map<String, Object> selectHost(@PathVariable String sessionId, @RequestBody Map<String, Object> body) {
        Map<String, Object> response = new HashMap<>();
        try {
            Number hostIdNum = (Number) body.get("hostId");
            if (hostIdNum == null) {
                response.put("success", false);
                response.put("error", "hostId 不能为空");
                return response;
            }
            Long hostId = hostIdNum.longValue();
            Host host = hostRepository.findById(hostId);
            if (host == null) {
                response.put("success", false);
                response.put("error", "主机不存在");
                return response;
            }
            chatService.setSelectedHost(sessionId, host);
            response.put("success", true);
            response.put("host", host);
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
        }
        return response;
    }
}
