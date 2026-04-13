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
    private final Map<String, Long> sessionSelectedHosts = new java.util.concurrent.ConcurrentHashMap<>();

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
            
            // 检查是否是主机选择结果
            boolean isHostSelectionResult = message != null && 
                (message.contains("主机 IP:") || message.contains("选择的主机 IP:"));
            
            if (isHostSelectionResult) {
                // 提取主机IP，查询主机信息
                String hostIp = message.replace("用户选择的主机 IP:", "").trim();
                Host host = hostRepository.findAll().stream()
                    .filter(h -> h.getHostname().equals(hostIp))
                    .findFirst()
                    .orElse(null);
                
                // 主机选择后继续处理
                String reply = chatService.continueAfterHostSelection(sessionId, message, host);
                
                // 保存主机选择到 Controller 的 sessionSelectedHosts
                if (host != null) {
                    sessionSelectedHosts.put(sessionId, host.getId());
                }
                
                return new ChatResponse(sessionId, reply, true, null, false, null, host);
            }
            
            // 检查是否有提前选择的主机
            Long preSelectedHostId = sessionSelectedHosts.get(sessionId);
            Host preSelectedHost = null;
            if (preSelectedHostId != null) {
                preSelectedHost = hostRepository.findById(preSelectedHostId);
            }
            
            // 传递已选主机给 chat 服务
            String reply = chatService.chat(sessionId, message, preSelectedHost);
            
            boolean needsHostSelection = false;
            
            if (reply != null && (reply.startsWith("__SELECTED_HOST:") || reply.startsWith("__HOST_SELECTION_DIALOG:"))) {
                int pipeIndex = reply.indexOf("|");
                if (pipeIndex > 0) {
                    needsHostSelection = true;
                    reply = reply.substring(pipeIndex + 1);
                }
            }
            
            return new ChatResponse(sessionId, reply, true, null, needsHostSelection, null, null);
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
        Long hostId = sessionSelectedHosts.get(sessionId);
        if (hostId != null) {
            Host host = hostRepository.findById(hostId);
            if (host != null) {
                response.put("success", true);
                response.put("host", host);
            } else {
                response.put("success", false);
                response.put("error", "主机不存在");
            }
        } else {
            response.put("success", true);
            response.put("host", null);
        }
        return response;
    }

    @DeleteMapping("/session/{sessionId}/selected-host")
    public Map<String, Object> clearSelectedHost(@PathVariable String sessionId) {
        Map<String, Object> response = new HashMap<>();
        sessionSelectedHosts.remove(sessionId);
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
            sessionSelectedHosts.put(sessionId, hostId);
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
