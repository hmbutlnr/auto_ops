package com.autoops.mcp;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class McpClient {

    private final RestTemplate restTemplate;
    private final String mcpServerUrl;

    public McpClient() {
        this.restTemplate = new RestTemplate();
        this.mcpServerUrl = "http://localhost:8080/api/mcp";
    }

    /**
     * 获取 MCP Server 信息
     */
    public Map<String, Object> getServerInfo() {
        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(
                mcpServerUrl + "/info", 
                Map.class
            );
            return response.getBody();
        } catch (Exception e) {
            log.error("获取 MCP Server 信息失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 列出可用工具
     */
    public List<Map<String, Object>> listTools() {
        try {
            log.info("正在从 MCP Server 获取工具列表...");
            ResponseEntity<Map> response = restTemplate.getForEntity(
                mcpServerUrl + "/tools", 
                Map.class
            );
            Map<String, Object> body = response.getBody();
            log.info("MCP Server 返回: {}", body);
            if (body != null && body.containsKey("tools")) {
                Object toolsObj = body.get("tools");
                log.info("tools 字段类型: {}, 值: {}", toolsObj != null ? toolsObj.getClass() : "null", toolsObj);
                return (List<Map<String, Object>>) body.get("tools");
            }
        } catch (Exception e) {
            log.error("获取工具列表失败: {}", e.getMessage());
        }
        return List.of();
    }

    /**
     * 调用工具
     */
    public ToolCallResult callTool(String toolName, Map<String, Object> arguments) {
        return callTool(toolName, arguments, null);
    }

    /**
     * 调用工具（带会话ID）
     */
    public ToolCallResult callTool(String toolName, Map<String, Object> arguments, String sessionId) {
        try {
            log.info("McpClient.callTool - toolName: {}, arguments: {}, sessionId: {}", toolName, arguments, sessionId);
            
            // 构建请求
            ToolCallRequest request = new ToolCallRequest();
            request.setName(toolName);
            request.setArguments(arguments);
            request.setSessionId(sessionId);
            
            // 设置请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<ToolCallRequest> entity = new HttpEntity<>(request, headers);
            
            // 调用工具
            ResponseEntity<Map> response = restTemplate.postForEntity(
                mcpServerUrl + "/tools/call",
                entity,
                Map.class
            );
            
            log.info("McpClient.callTool 响应: {}", response.getBody());
            
            Map<String, Object> result = response.getBody();
            ToolCallResult toolResult = new ToolCallResult();
            
            if (result != null) {
                Object contentObj = result.get("content");
                String content = contentObj instanceof List ? contentObj.toString() : (String) contentObj;
                
                Boolean isError = (Boolean) result.get("isError");
                Boolean needHostSelection = (Boolean) result.get("needHostSelection");
                Boolean showHostSelector = (Boolean) result.get("showHostSelector");
                
                // 检查 content 中是否包含特殊标记
                boolean hasDialogMarker = content != null && content.toString().contains("__SELECT_HOST_DIALOG__");
                
                log.info("MCP返回: isError={}, needHostSelection={}, showHostSelector={}, content={}", 
                    isError, needHostSelection, showHostSelector, content);
                
                toolResult.setNeedHostSelection(
                    Boolean.TRUE.equals(needHostSelection) || 
                    Boolean.TRUE.equals(showHostSelector) || 
                    hasDialogMarker
                );
                toolResult.setError(Boolean.TRUE.equals(isError));
                
                if (Boolean.TRUE.equals(isError)) {
                    log.warn("工具执行出错: {}", result.get("content"));
                    toolResult.setContent(extractTextFromContent(result.get("content")));
                    return toolResult;
                }
                
                // 提取文本内容
                toolResult.setContent(extractTextFromContent(result.get("content")));
            } else {
                toolResult.setContent("工具调用无返回结果");
            }
            
            return toolResult;
            
        } catch (Exception e) {
            log.error("调用工具失败: {}", e.getMessage(), e);
            ToolCallResult toolResult = new ToolCallResult();
            toolResult.setError(true);
            toolResult.setContent("工具调用失败: " + e.getMessage());
            return toolResult;
        }
    }

    /**
     * 从 content 中提取文本
     */
    @SuppressWarnings("unchecked")
    private String extractTextFromContent(Object content) {
        if (content instanceof List) {
            List<Map<String, Object>> contentList = (List<Map<String, Object>>) content;
            if (!contentList.isEmpty()) {
                Map<String, Object> firstItem = contentList.get(0);
                if ("text".equals(firstItem.get("type"))) {
                    return (String) firstItem.get("text");
                }
            }
        }
        return content != null ? content.toString() : "无内容";
    }

    @Data
    public static class ToolCallRequest {
        private String name;
        private Map<String, Object> arguments;
        private String sessionId;
    }
    
    @Data
    public static class ToolCallResult {
        private String content;
        private boolean error;
        private boolean needHostSelection;
    }
}
