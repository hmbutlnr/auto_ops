package com.autoops.chat.service;

import com.autoops.chat.config.DashScopeConfig;
import com.autoops.chat.model.ChatMessage;
import com.autoops.chat.model.ChatSession;
import com.autoops.service.SshService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class ChatServiceWithFunctionCalling {

    private final DashScopeConfig config;
    private final SshService sshService;
    private final Map<String, ChatSession> sessions = new ConcurrentHashMap<>();
    private RestTemplate restTemplate;
    private ObjectMapper objectMapper;

    public ChatServiceWithFunctionCalling(DashScopeConfig config, SshService sshService) {
        this.config = config;
        this.sshService = sshService;
    }

    @PostConstruct
    public void init() {
        restTemplate = new RestTemplate();
        objectMapper = new ObjectMapper();
    }

    public ChatSession createSession(String sessionId) {
        ChatSession session = new ChatSession(sessionId);
        sessions.put(sessionId, session);
        return session;
    }

    public ChatSession getSession(String sessionId) {
        return sessions.get(sessionId);
    }

    /**
     * 处理聊天请求，支持 Function Calling
     */
    public String chat(String sessionId, String userMessage) {
        ChatSession session = sessions.computeIfAbsent(sessionId, ChatSession::new);
        session.addUserMessage(userMessage);

        try {
            // 第一轮：调用 AI，传入工具定义
            List<Map<String, Object>> messages = buildMessages(session);
            Map<String, Object> tools = buildToolsDefinition();
            
            Map<String, Object> firstResponse = callDashScopeWithTools(messages, tools);
            
            // 检查是否有工具调用
            List<Map<String, Object>> toolCalls = extractToolCalls(firstResponse);
            
            if (toolCalls != null && !toolCalls.isEmpty()) {
                log.info("检测到 {} 个工具调用", toolCalls.size());
                
                // 执行工具调用
                for (Map<String, Object> toolCall : toolCalls) {
                    String result = executeToolCall(toolCall);
                    
                    // 添加工具调用和结果到消息历史
                    messages.add(buildAssistantMessageWithToolCall(toolCall));
                    messages.add(buildToolMessage(toolCall, result));
                }
                
                // 第二轮：将工具结果返回给 AI，生成最终回复
                Map<String, Object> finalResponse = callDashScopeWithTools(messages, tools);
                String finalReply = extractContent(finalResponse);
                
                session.addAssistantMessage(finalReply);
                return finalReply;
            } else {
                // 没有工具调用，直接返回 AI 的回复
                String reply = extractContent(firstResponse);
                session.addAssistantMessage(reply);
                return reply;
            }
            
        } catch (Exception e) {
            log.error("聊天处理失败: {}", e.getMessage(), e);
            return "处理失败: " + e.getMessage();
        }
    }

    /**
     * 构建消息列表
     */
    private List<Map<String, Object>> buildMessages(ChatSession session) {
        List<Map<String, Object>> messages = new ArrayList<>();
        for (ChatMessage msg : session.getMessages()) {
            Map<String, Object> message = new HashMap<>();
            message.put("role", msg.getRole());
            message.put("content", msg.getContent());
            messages.add(message);
        }
        return messages;
    }

    /**
     * 构建工具定义
     */
    private Map<String, Object> buildToolsDefinition() {
        List<Map<String, Object>> tools = new ArrayList<>();
        
        // SSH 执行命令工具
        Map<String, Object> sshTool = new HashMap<>();
        sshTool.put("type", "function");
        
        Map<String, Object> function = new HashMap<>();
        function.put("name", "ssh_execute_command");
        function.put("description", "通过 SSH 在远程服务器上执行 shell 命令。用于查看系统信息、检查资源使用、查看日志等运维操作。");
        
        // 参数定义
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("type", "object");
        
        Map<String, Object> properties = new HashMap<>();
        Map<String, Object> commandProp = new HashMap<>();
        commandProp.put("type", "string");
        commandProp.put("description", "要执行的 shell 命令，例如: df -h, ps aux, uname -a, tail -n 50 /var/log/syslog");
        properties.put("command", commandProp);
        
        parameters.put("properties", properties);
        parameters.put("required", Arrays.asList("command"));
        
        function.put("parameters", parameters);
        sshTool.put("function", function);
        
        tools.add(sshTool);
        
        Map<String, Object> result = new HashMap<>();
        result.put("tools", tools);
        return result;
    }

    /**
     * 调用 DashScope API（带工具定义）
     */
    private Map<String, Object> callDashScopeWithTools(List<Map<String, Object>> messages, 
                                                        Map<String, Object> tools) {
        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", config.getModel());
            requestBody.put("messages", messages);
            requestBody.putAll(tools);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + config.getApiKey());
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            
            String url = config.getBaseUrl() + "/chat/completions";
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
            
            return response.getBody();
            
        } catch (Exception e) {
            log.error("调用 DashScope API 失败: {}", e.getMessage(), e);
            throw new RuntimeException("AI 调用失败: " + e.getMessage(), e);
        }
    }

    /**
     * 提取工具调用
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractToolCalls(Map<String, Object> response) {
        try {
            if (response == null || !response.containsKey("choices")) {
                return null;
            }
            
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            if (choices == null || choices.isEmpty()) {
                return null;
            }
            
            Map<String, Object> choice = choices.get(0);
            Map<String, Object> message = (Map<String, Object>) choice.get("message");
            
            if (message == null || !message.containsKey("tool_calls")) {
                return null;
            }
            
            return (List<Map<String, Object>>) message.get("tool_calls");
            
        } catch (Exception e) {
            log.error("提取工具调用失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 执行工具调用
     */
    @SuppressWarnings("unchecked")
    private String executeToolCall(Map<String, Object> toolCall) {
        try {
            Map<String, Object> function = (Map<String, Object>) toolCall.get("function");
            String functionName = (String) function.get("name");
            String argumentsStr = (String) function.get("arguments");
            
            log.info("执行工具: {}, 参数: {}", functionName, argumentsStr);
            
            // 解析参数
            Map<String, Object> arguments = objectMapper.readValue(argumentsStr, Map.class);
            
            if ("ssh_execute_command".equals(functionName)) {
                String command = (String) arguments.get("command");
                return sshService.executeCommand(command);
            }
            
            return "未知工具: " + functionName;
            
        } catch (Exception e) {
            log.error("执行工具失败: {}", e.getMessage(), e);
            return "工具执行失败: " + e.getMessage();
        }
    }

    /**
     * 构建包含工具调用的助手消息
     */
    private Map<String, Object> buildAssistantMessageWithToolCall(Map<String, Object> toolCall) {
        Map<String, Object> message = new HashMap<>();
        message.put("role", "assistant");
        message.put("content", null);
        message.put("tool_calls", Arrays.asList(toolCall));
        return message;
    }

    /**
     * 构建工具响应消息
     */
    private Map<String, Object> buildToolMessage(Map<String, Object> toolCall, String result) {
        Map<String, Object> message = new HashMap<>();
        message.put("role", "tool");
        message.put("tool_call_id", toolCall.get("id"));
        message.put("content", result);
        return message;
    }

    /**
     * 提取 AI 回复内容
     */
    @SuppressWarnings("unchecked")
    private String extractContent(Map<String, Object> response) {
        try {
            if (response == null || !response.containsKey("choices")) {
                return "无响应";
            }
            
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            if (choices == null || choices.isEmpty()) {
                return "无响应";
            }
            
            Map<String, Object> choice = choices.get(0);
            Map<String, Object> message = (Map<String, Object>) choice.get("message");
            
            if (message == null) {
                return "无响应";
            }
            
            String content = (String) message.get("content");
            return content != null ? content : "无内容";
            
        } catch (Exception e) {
            log.error("提取内容失败: {}", e.getMessage());
            return "解析响应失败";
        }
    }
}
