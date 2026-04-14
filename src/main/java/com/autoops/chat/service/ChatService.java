package com.autoops.chat.service;

import com.autoops.chat.config.DashScopeConfig;
import com.autoops.chat.model.ChatMessage;
import com.autoops.chat.model.ChatSession;
import com.autoops.mcp.McpClient;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ChatService {

    private final DashScopeConfig config;
    private final McpClient mcpClient;
    private final Map<String, ChatSession> sessions = new ConcurrentHashMap<>();
    private RestTemplate restTemplate;
    // 匹配工具调用标记的正则表达式
    private static final Pattern TOOL_CALL_PATTERN = Pattern.compile("@tool\\{(.*?)\\}");

    public ChatService(DashScopeConfig config, McpClient mcpClient) {
        this.config = config;
        this.mcpClient = mcpClient;
    }

    @PostConstruct
    public void init() {
        restTemplate = new RestTemplate();
    }

    public ChatSession createSession(String sessionId) {
        ChatSession session = new ChatSession(sessionId);
        sessions.put(sessionId, session);
        return session;
    }

    public ChatSession getSession(String sessionId) {
        return sessions.get(sessionId);
    }

    public String chat(String sessionId, String userMessage) {
        ChatSession session = sessions.computeIfAbsent(sessionId, ChatSession::new);
        session.addUserMessage(userMessage);

        List<Map<String, String>> messages = new ArrayList<>();
        for (ChatMessage msg : session.getMessages()) {
            Map<String, String> message = new HashMap<>();
            message.put("role", msg.getRole());
            message.put("content", msg.getContent());
            messages.add(message);
        }

        try {
            // 构建请求体
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", config.getModel());
            requestBody.put("messages", messages);
            
            // 设置请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + config.getApiKey());
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            
            // 发送请求到 DashScope API
            String url = config.getBaseUrl() + "/chat/completions";
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
            
            // 解析响应
            Map<String, Object> responseBody = response.getBody();
            if (responseBody != null && responseBody.containsKey("choices")) {
                List<Map<String, Object>> choices = (List<Map<String, Object>>) responseBody.get("choices");
                if (choices != null && !choices.isEmpty()) {
                    Map<String, Object> choice = choices.get(0);
                    Map<String, Object> message = (Map<String, Object>) choice.get("message");
                    String assistantReply = (String) message.get("content");
                    
                    // 检查是否包含 MCP 工具调用，如果有则执行
                    String finalReply = processAndExecuteTools(assistantReply);
                    
                    session.addAssistantMessage(finalReply);
                    return finalReply;
                }
            }
            
            return "无法解析AI响应";
        } catch (Exception e) {
            return "调用AI服务失败: " + e.getMessage();
        }
    }

    /**
     * 处理 AI 响应并执行其中的 MCP 工具调用
     * @param aiResponse AI 的原始响应
     * @return 处理后的响应（包含工具执行结果）
     */
    private String processAndExecuteTools(String aiResponse) {
        Matcher matcher = TOOL_CALL_PATTERN.matcher(aiResponse);
        StringBuffer result = new StringBuffer();
        boolean hasToolCalls = false;
        
        while (matcher.find()) {
            hasToolCalls = true;
            String toolCallJson = matcher.group(1).trim();
            
            try {
                // 解析工具调用 JSON
                Map<String, Object> toolCall = parseToolCall(toolCallJson);
                String toolName = (String) toolCall.get("name");
                Map<String, Object> arguments = (Map<String, Object>) toolCall.get("arguments");
                
                // 调用 MCP 工具
                McpClient.ToolCallResult toolResult = mcpClient.callTool(toolName, arguments);
                String executionResult = toolResult.getContent();
                
                // 将工具调用和结果添加到响应中
                matcher.appendReplacement(result, 
                    Matcher.quoteReplacement("**工具调用:** `" + toolName + "`\n\n**执行结果:**\n```\n" + executionResult + "\n```"));
            } catch (Exception e) {
                matcher.appendReplacement(result, 
                    Matcher.quoteReplacement("**工具执行失败:** " + e.getMessage()));
            }
        }
        
        if (hasToolCalls) {
            matcher.appendTail(result);
            return result.toString();
        }
        
        // 如果没有找到工具调用，返回原始响应
        return aiResponse;
    }

    /**
     * 解析工具调用 JSON
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseToolCall(String json) {
        // 简化的 JSON 解析，实际项目中建议使用 Jackson
        Map<String, Object> result = new HashMap<>();
        
        // 提取 name
        int nameStart = json.indexOf("\"name\":\"") + 8;
        int nameEnd = json.indexOf("\"", nameStart);
        String name = json.substring(nameStart, nameEnd);
        result.put("name", name);
        
        // 提取 arguments
        int argsStart = json.indexOf("\"arguments\":{") + 13;
        int argsEnd = json.lastIndexOf("}");
        String argsStr = json.substring(argsStart, argsEnd);
        
        Map<String, Object> arguments = new HashMap<>();
        // 简单解析 command 参数
        if (argsStr.contains("\"command\":\"")) {
            int cmdStart = argsStr.indexOf("\"command\":\"") + 11;
            int cmdEnd = argsStr.indexOf("\"", cmdStart);
            String command = argsStr.substring(cmdStart, cmdEnd);
            arguments.put("command", command);
        }
        
        result.put("arguments", arguments);
        return result;
    }
}
