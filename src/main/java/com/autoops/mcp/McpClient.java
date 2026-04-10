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
            ResponseEntity<Map> response = restTemplate.getForEntity(
                mcpServerUrl + "/tools", 
                Map.class
            );
            Map<String, Object> body = response.getBody();
            if (body != null && body.containsKey("tools")) {
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
    public String callTool(String toolName, Map<String, Object> arguments) {
        try {
            // 构建请求
            ToolCallRequest request = new ToolCallRequest();
            request.setName(toolName);
            request.setArguments(arguments);
            
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
            
            Map<String, Object> result = response.getBody();
            if (result != null) {
                Boolean isError = (Boolean) result.get("isError");
                if (Boolean.TRUE.equals(isError)) {
                    log.warn("工具执行出错: {}", result.get("content"));
                    return extractTextFromContent(result.get("content"));
                }
                
                // 提取文本内容
                return extractTextFromContent(result.get("content"));
            }
            
            return "工具调用无返回结果";
            
        } catch (Exception e) {
            log.error("调用工具失败: {}", e.getMessage(), e);
            return "工具调用失败: " + e.getMessage();
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
    }
}
