package com.autoops.chat.service;

import com.autoops.chat.config.DashScopeConfig;
import com.autoops.chat.model.ChatMessage;
import com.autoops.chat.model.ChatSession;
import com.autoops.host.model.Host;
import com.autoops.host.repository.HostRepository;
import com.autoops.mcp.McpClient;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class ChatServiceWithFunctionCalling {

    private final DashScopeConfig config;
    private final McpClient mcpClient;
    private final HostRepository hostRepository;
    private final Map<String, ChatSession> sessions = new ConcurrentHashMap<>();
    private RestTemplate restTemplate;
    private ObjectMapper objectMapper;
    private static final Pattern IP_PATTERN = Pattern.compile(
        "(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)"
    );

    public ChatServiceWithFunctionCalling(DashScopeConfig config, McpClient mcpClient, HostRepository hostRepository) {
        this.config = config;
        this.mcpClient = mcpClient;
        this.hostRepository = hostRepository;
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
    
    public Host getSelectedHost(String sessionId) {
        ChatSession session = sessions.get(sessionId);
        return session != null ? session.getSelectedHost() : null;
    }
    
    public void clearSelectedHost(String sessionId) {
        ChatSession session = sessions.get(sessionId);
        if (session != null) {
            session.setSelectedHost(null);
        }
    }
    
    public void setSelectedHost(String sessionId, Host host) {
        ChatSession session = sessions.computeIfAbsent(sessionId, ChatSession::new);
        session.setSelectedHost(host);
    }

/**
 * 处理聊天请求，智能体模式：AI可以多轮对话并多次调用工具
 * @return 返回结果，包含 "__HOST_SELECTION_DIALOG:" 前缀表示需要前端弹出选择主机
 */
public String chat(String sessionId, String userMessage, Host preSelectedHost) {
    ChatSession session = sessions.computeIfAbsent(sessionId, ChatSession::new);
    session.addUserMessage(userMessage);

    try {
        Host host = session.getSelectedHost();
        if (host == null && preSelectedHost != null) {
            host = preSelectedHost;
            session.setSelectedHost(host);
        }
        
        // // 尝试从消息中提取IP自动识别主机
        // if (host == null) {
        //     String ipFromMessage = extractIpFromMessage(userMessage);
        //     if (ipFromMessage != null) {
        //         host = findHostByIp(ipFromMessage);
        //         if (host != null) {
        //             session.setSelectedHost(host);
        //             log.info("从用户消息中识别并自动选择主机: {} ({})", host.getName(), host.getHostname());
        //         }
        //     }
        // }
        
        // 构建动态 system prompt
        List<Map<String, Object>> messages = buildMessages(session);
        addSystemPrompt(messages, host);
        
        // 获取 MCP 工具定义
        Map<String, Object> tools = buildToolsDefinitionFromMcp();
        
        // AI 多轮对话循环
        int maxIterations = 10;
        int iteration = 0;
        String finalReply = null;
        
        while (iteration < maxIterations) {
            iteration++;
            log.info("AI 对话轮次: {}, 已调用工具次数: {}", iteration, iteration - 1);
            
            // 调用 AI
            ResponseEntity<Map> response = callDashScopeWithTools(messages, tools);
            Map<String, Object> responseBody = response.getBody();
            
            // 检查 AI 是否调用了工具
            List<Map<String, Object>> toolCalls = extractToolCalls(responseBody);
            
            if (toolCalls == null || toolCalls.isEmpty()) {
                // AI 没有调用工具，返回最终回复
                finalReply = extractContent(responseBody);
                session.addAssistantMessage(finalReply);
                log.info("AI 最终回复: {}", finalReply);
                break;
            }
            
            // AI 调用了一个或多个工具
            log.info("AI 调用了 {} 个工具", toolCalls.size());
            
            for (Map<String, Object> toolCall : toolCalls) {
                String toolName = getToolName(toolCall);
                log.info("执行工具: {}", toolName);
                
                // 执行工具获取结果
                String toolResult = executeToolCallViaMcp(sessionId, toolCall, host);
                
                // 记录工具调用结果
                String toolResultMsg = "【工具调用】" + toolName + "\n结果: " + toolResult;
                session.addAssistantMessage(toolResultMsg);
                
                // 添加工具结果到消息��史，供下一轮 AI 参考
                Map<String, Object> toolMessage = new HashMap<>();
                toolMessage.put("role", "tool");
                toolMessage.put("content", toolResult);
                messages.add(toolMessage);
                
                log.info("工具执行完成，返回结果给 AI 进行下一轮分析");
            }
            
            // 如果 AI 调用了 select_host 工具，弹窗让用户选择，不需要继续循环
            boolean needHostSelection = toolCalls.stream()
                .anyMatch(tc -> "select_host".equals(getToolName(tc)));;
            
            if (needHostSelection) {
                finalReply = "__HOST_SELECTION_DIALOG:|请在下方选择要操作的主机";
                break;
            }
        }
        
        if (iteration >= maxIterations) {
            log.warn("达到最大对话轮次限制");   
            return "对话已达到最大轮次限制，请重新开始。";
        }
        
        return finalReply;
        
    } catch (Exception e) {
        log.error("聊天处理失败: {}", e.getMessage(), e);
        return "处理失败: " + e.getMessage();
    }
}

/**
 * 添加动态 system prompt
 */
private void addSystemPrompt(List<Map<String, Object>> messages, Host host) {
    Map<String, Object> systemPrompt = new HashMap<>();
    systemPrompt.put("role", "system");
    
    StringBuilder content = new StringBuilder();
    content.append("你是一个智能运维助手，可以帮助用户管理服务器。\n");
    
    if (host != null) {
        content.append(String.format("当前已选择主机: %s (IP: %s, 端口: %d, 用户名: %s)\n",
            host.getName(), host.getHostname(), host.getPort(), host.getUsername()));
        content.append("可以使用 ssh_execute_command 工具在此主机上执行命令。\n");
    } else {
        content.append("可用的 MCP 工具:\n");
        content.append("- list_hosts: 查看所有主机列表\n");
        content.append("- select_host: 选择要操作的主机（需要用户交互选择）\n");
        content.append("- get_host_by_ip: 根据 IP 查询主机信息\n");
        content.append("- get_host_by_name: 根据名称查询主机信息\n");
        content.append("- ssh_execute_command: 在主机上执行 SSH 命令\n");
    }
    
    content.append("\n注意: 如果需要操作主机但没有选择主机，请先调用 select_host 工具。\n");
    
    systemPrompt.put("content", content.toString());
    messages.add(0, systemPrompt);
}
    
    /**
     * 获取主机列表信息（用于传给AI作为参考）
     */
    private String getHostListInfo() {
        try {
            List<Host> hosts = hostRepository.findAll();
            if (hosts == null || hosts.isEmpty()) {
                return "当前没有配置任何主机";
            }
            StringBuilder sb = new StringBuilder();
            for (Host h : hosts) {
                sb.append(String.format("- %s (IP: %s, 端口: %d)\n", 
                    h.getName(), h.getHostname(), h.getPort()));
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("获取主机列表失败: {}", e.getMessage());
            return "无法获取主机列表";
        }
    }
    
    /**
     * 根据IP查找主机
     */
    private Host findHostByIp(String ip) {
        try {
            List<Host> hosts = hostRepository.findAll();
            for (Host h : hosts) {
                if (ip.equals(h.getHostname())) {
                    return h;
                }
            }
        } catch (Exception e) {
            log.error("查找主机失败: {}", e.getMessage());
        }
        return null;
    }
    
    /**
     * 主机选择后继续处理 - 直接使用已选主机，不触发 list_hosts
     */
    public String continueAfterHostSelection(String sessionId, String hostIpMessage, Host selectedHost) {
        ChatSession session = sessions.get(sessionId);
        if (session == null) {
            session = new ChatSession(sessionId);
            sessions.put(sessionId, session);
        }
        
        // 保存选中的主机到 session
        session.setSelectedHost(selectedHost);
        
        // 添加用户消息（不需要包含主机选择信息，session已存储）
        session.addUserMessage("继续对话");
        
        try {
            // 直接调用 chat，使用已保存的主机信息
            return chat(sessionId, "继续对话", selectedHost);
            
        } catch (Exception e) {
            log.error("主机选择后继续处理失败: {}", e.getMessage(), e);
            return "处理主机选择结果失败: " + e.getMessage();
        }
    }
    
    /**
     * 从工具结果中提取主机列表
     */
    private String extractHostListFromResult(String result) {
        if (result == null || result.isEmpty()) {
            return "当前没有配置任何主机";
        }
        return result;
    }
    
    /**
     * 获取工具名称
     */
    @SuppressWarnings("unchecked")
    private String getToolName(Map<String, Object> toolCall) {
        if (toolCall == null) return null;
        Map<String, Object> function = (Map<String, Object>) toolCall.get("function");
        if (function == null) return null;
        return (String) function.get("name");
    }
    
    /**
     * 从用户消息中检测 IP 地址并匹配主机
     */
    private Host detectHostFromMessage(String message) {
        if (message == null || message.isEmpty()) {
            return null;
        }
        
        Matcher matcher = IP_PATTERN.matcher(message);
        while (matcher.find()) {
            String ip = matcher.group();
            log.info("检测到 IP: {}", ip);
            
            // 从数据库查找匹配的主机
            List<Host> hosts = hostRepository.findAll();
            for (Host host : hosts) {
                if (ip.equals(host.getHostname())) {
                    log.info("匹配到主机: {} ({})", host.getName(), host.getHostname());
                    return host;
                }
            }
        }
        return null;
    }
    
    /**
     * 从用户消息中提取第一个 IP 地址
     */
    private String extractIpFromMessage(String message) {
        if (message == null || message.isEmpty()) {
            return null;
        }
        Matcher matcher = IP_PATTERN.matcher(message);
        if (matcher.find()) {
            return matcher.group();
        }
        return null;
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
     * 从 MCP Server 获取工具定义并转换为 DashScope 格式
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> buildToolsDefinitionFromMcp() {
        List<Map<String, Object>> tools = new ArrayList<>();
        
        try {
            // 从 MCP Server 获取工具列表
            List<Map<String, Object>> mcpTools = mcpClient.listTools();
            log.info("从 MCP 获取的工具列表: {}", mcpTools);
            
            if (mcpTools == null || mcpTools.isEmpty()) {
                log.warn("MCP 未返回任何工具!");
            }
            
            for (Map<String, Object> mcpTool : mcpTools) {
                Map<String, Object> tool = new HashMap<>();
                tool.put("type", "function");
                
                Map<String, Object> function = new HashMap<>();
                function.put("name", mcpTool.get("name"));
                function.put("description", mcpTool.get("description"));
                
                // 转换 inputSchema 为 parameters
                Map<String, Object> inputSchema = (Map<String, Object>) mcpTool.get("inputSchema");
                if (inputSchema != null) {
                    function.put("parameters", inputSchema);
                } else {
                    Map<String, Object> emptyParams = new HashMap<>();
                    emptyParams.put("type", "object");
                    emptyParams.put("properties", new HashMap<>());
                    function.put("parameters", emptyParams);
                }
                
                tool.put("function", function);
                tools.add(tool);
            }
            
            log.info("从 MCP Server 获取到 {} 个工具", tools.size());
            log.info("转换后的工具定义: {}", tools);
            
        } catch (Exception e) {
            log.error("从 MCP Server 获取工具定义失败: {}", e.getMessage(), e);
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("tools", tools);
        log.info("返回的工具 JSON: {}", result);
        return result;
    }

    /**
     * 调用 DashScope API（带工具定义）
     */
    private ResponseEntity<Map> callDashScopeWithTools(List<Map<String, Object>> messages, 
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
            
            return response;
            
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
     * 通过 MCP 客户端执行工具调用
     */
    @SuppressWarnings("unchecked")
    private String executeToolCallViaMcp(String sessionId, Map<String, Object> toolCall, Host selectedHost) {
        try {
            Map<String, Object> function = (Map<String, Object>) toolCall.get("function");
            String functionName = (String) function.get("name");
            String argumentsStr = (String) function.get("arguments");

            log.info("执行工具: {}, 参数: {}, sessionId: {}, selectedHost: {}", functionName, argumentsStr, sessionId, selectedHost != null ? selectedHost.getName() : "null");

            // 解析参数
            Map<String, Object> arguments = objectMapper.readValue(argumentsStr, Map.class);
            
            // 如果有选中的主机，添加 hostId 到参数中
            if (selectedHost != null && "ssh_execute_command".equals(functionName)) {
                arguments.put("hostId", selectedHost.getId());
                log.info("添加 hostId 到 SSH 命令参数: {}", selectedHost.getId());
            }

            // 通过 MCP 客户端调用工具，传入 sessionId
            McpClient.ToolCallResult result = mcpClient.callTool(functionName, arguments, sessionId);
            
            log.info("工具执行结果: isNeedHostSelection={}, content={}", result.isNeedHostSelection(), result.getContent());
            
            // 检查是否需要弹出主机选择对话框
            if (result.isNeedHostSelection()) {
                // 返回特殊标记让前端知道需要选择主机
                log.info("需要弹出主机选择对话框, functionName={}", functionName);
                return "__HOST_SELECTION_DIALOG:|请先选择要操作的主机，然后告诉我您想执行什么命令。";
            }
            
            return result.getContent();

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
