package com.autoops.mcp;

import com.autoops.config.SshProperties;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/mcp")
@CrossOrigin(origins = "*")
public class McpServerController {

    private final SshProperties sshProperties;

    public McpServerController(SshProperties sshProperties) {
        this.sshProperties = sshProperties;
    }

    /**
     * MCP Server 信息端点
     */
    @GetMapping("/info")
    public Map<String, Object> getInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("name", "auto-ops-mcp-server");
        info.put("version", "1.0.0");
        info.put("description", "Auto Ops MCP Server - 提供 SSH 命令执行能力");
        
        Map<String, Object> capabilities = new HashMap<>();
        capabilities.put("tools", true);
        capabilities.put("resources", false);
        capabilities.put("prompts", false);
        info.put("capabilities", capabilities);
        
        return info;
    }

    /**
     * 列出可用的工具
     */
    @GetMapping("/tools")
    public Map<String, Object> listTools() {
        Map<String, Object> response = new HashMap<>();
        
        Map<String, Object> sshTool = new HashMap<>();
        sshTool.put("name", "ssh_execute_command");
        sshTool.put("description", "通过 SSH 在远程服务器上执行命令");
        
        Map<String, Object> inputSchema = new HashMap<>();
        inputSchema.put("type", "object");
        
        Map<String, Object> properties = new HashMap<>();
        Map<String, Object> commandProp = new HashMap<>();
        commandProp.put("type", "string");
        commandProp.put("description", "要执行的 shell 命令，例如: df -h, ps aux, uname -a");
        properties.put("command", commandProp);
        
        inputSchema.put("properties", properties);
        inputSchema.put("required", new String[]{"command"});
        
        sshTool.put("inputSchema", inputSchema);
        
        response.put("tools", new Object[]{sshTool});
        
        return response;
    }

    /**
     * 调用工具
     */
    @PostMapping("/tools/call")
    public Map<String, Object> callTool(@RequestBody ToolCallRequest request) {
        log.info("收到工具调用请求: {}", request.getName());
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            if ("ssh_execute_command".equals(request.getName())) {
                String command = (String) request.getArguments().get("command");
                
                if (command == null || command.trim().isEmpty()) {
                    response.put("content", new Object[]{
                        Map.of("type", "text", "text", "错误: 命令不能为空")
                    });
                    response.put("isError", true);
                    return response;
                }
                
                // 执行 SSH 命令
                String result = executeSshCommand(command);
                
                response.put("content", new Object[]{
                    Map.of("type", "text", "text", result)
                });
                response.put("isError", false);
                
            } else {
                response.put("content", new Object[]{
                    Map.of("type", "text", "text", "未知工具: " + request.getName())
                });
                response.put("isError", true);
            }
            
        } catch (Exception e) {
            log.error("工具执行失败: {}", e.getMessage(), e);
            response.put("content", new Object[]{
                Map.of("type", "text", "text", "执行失败: " + e.getMessage())
            });
            response.put("isError", true);
        }
        
        return response;
    }

    /**
     * 执行 SSH 命令
     */
    private String executeSshCommand(String command) throws Exception {
        Session session = null;
        ChannelExec channel = null;
        
        try {
            JSch jsch = new JSch();
            
            session = jsch.getSession(
                sshProperties.getUsername(),
                sshProperties.getHost(),
                sshProperties.getPort()
            );
            session.setPassword(sshProperties.getPassword());
            session.setConfig("StrictHostKeyChecking", "no");
            session.setTimeout(sshProperties.getTimeout());
            
            session.connect();
            log.info("SSH 连接成功: {}:{}", sshProperties.getHost(), sshProperties.getPort());
            
            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);
            
            InputStream outputStream = channel.getInputStream();
            InputStream errorStream = channel.getErrStream();
            
            channel.connect();
            
            StringBuilder output = new StringBuilder();
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(outputStream, StandardCharsets.UTF_8)
            );
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            
            StringBuilder errorOutput = new StringBuilder();
            BufferedReader errorReader = new BufferedReader(
                new InputStreamReader(errorStream, StandardCharsets.UTF_8)
            );
            while ((line = errorReader.readLine()) != null) {
                errorOutput.append(line).append("\n");
            }
            
            while (!channel.isClosed()) {
                Thread.sleep(100);
            }
            
            int exitStatus = channel.getExitStatus();
            
            StringBuilder result = new StringBuilder();
            result.append("命令: ").append(command).append("\n");
            result.append("退出码: ").append(exitStatus).append("\n\n");
            
            if (exitStatus == 0) {
                result.append("输出:\n");
                result.append(output.toString().trim());
            } else {
                result.append("错误:\n");
                result.append(errorOutput.toString().trim());
            }
            
            return result.toString();
            
        } finally {
            if (channel != null && channel.isConnected()) {
                channel.disconnect();
            }
            if (session != null && session.isConnected()) {
                session.disconnect();
                log.info("SSH 连接已关闭");
            }
        }
    }

    @Data
    public static class ToolCallRequest {
        private String name;
        private Map<String, Object> arguments;
    }
}
