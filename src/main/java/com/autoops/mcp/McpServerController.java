package com.autoops.mcp;

import com.autoops.config.SshProperties;
import com.autoops.host.model.Host;
import com.autoops.host.repository.HostRepository;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
@RequestMapping("/api/mcp")
@CrossOrigin(origins = "*")
public class McpServerController {

    private final SshProperties sshProperties;
    private final HostRepository hostRepository;
    
    // 存储每个会话选中的主机: sessionId -> hostId
    private final Map<String, Long> sessionSelectedHosts = new ConcurrentHashMap<>();

    public McpServerController(SshProperties sshProperties, HostRepository hostRepository) {
        this.sshProperties = sshProperties;
        this.hostRepository = hostRepository;
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
        
        // SSH 执行命令工具
        Map<String, Object> sshTool = new HashMap<>();
        sshTool.put("name", "ssh_execute_command");
        sshTool.put("description", "通过 SSH 在指定主机上执行 shell 命令。此工具仅在用户明确要求执行命令或操作（如查看进程、磁盘空间、内存、日志等）时才使用。" +
            "对于查询类问题（如'有没有xxx主机'、'主机状态'、'有哪些主机'），请直接回答，不需要调用此工具。");
        
        Map<String, Object> sshInputSchema = new HashMap<>();
        sshInputSchema.put("type", "object");
        
        Map<String, Object> sshProperties = new HashMap<>();
        Map<String, Object> commandProp = new HashMap<>();
        commandProp.put("type", "string");
        commandProp.put("description", "要执行的 shell 命令，例如: df -h, ps aux, uname -a, tail -n 50 /var/log/syslog");
        sshProperties.put("command", commandProp);
        
        Map<String, Object> hostIdProp = new HashMap<>();
        hostIdProp.put("type", "integer");
        hostIdProp.put("description", "可选：目标主机的 ID。如果指定，将在此主机上执行命令；如果不指定，将使用之前选择的主机或默认主机");
        sshProperties.put("hostId", hostIdProp);
        
        sshInputSchema.put("properties", sshProperties);
        sshInputSchema.put("required", new String[]{"command"});
        
        sshTool.put("inputSchema", sshInputSchema);
        
        // 获取主机列表工具
        Map<String, Object> listHostsTool = new HashMap<>();
        listHostsTool.put("name", "list_hosts");
        listHostsTool.put("description", "获取所有可用的 SSH 主机列表，用于查看有哪些服务器可以连接。返回主机 ID、名称、地址、状态等信息。当用户想要操作服务器但不知道有哪些可用时，应该先调用此工具。");
        
        Map<String, Object> listHostsInputSchema = new HashMap<>();
        listHostsInputSchema.put("type", "object");
        listHostsInputSchema.put("properties", new HashMap<>());
        listHostsTool.put("inputSchema", listHostsInputSchema);
        
        // 选择主机工具
        Map<String, Object> selectHostTool = new HashMap<>();
        selectHostTool.put("name", "select_host");
        selectHostTool.put("description", "选择一个主机作为当前操作目标。在执行 SSH 命令前，必须先选择要操作的主机。用户可以通过主机 ID 来选择，主机 ID 可以通过 list_hosts 工具获取。");
        
        Map<String, Object> selectHostInputSchema = new HashMap<>();
        selectHostInputSchema.put("type", "object");
        
        Map<String, Object> selectHostProps = new HashMap<>();
        Map<String, Object> selectHostIdProp = new HashMap<>();
        selectHostIdProp.put("type", "integer");
        selectHostIdProp.put("description", "要选择的主机 ID，可以通过 list_hosts 工具获取可用主机的 ID");
        selectHostProps.put("hostId", selectHostIdProp);
        
        selectHostInputSchema.put("properties", selectHostProps);
        selectHostInputSchema.put("required", new String[]{"hostId"});
        
        selectHostTool.put("inputSchema", selectHostInputSchema);
        
        response.put("tools", java.util.Arrays.asList(listHostsTool, selectHostTool, sshTool));
        
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
            if ("list_hosts".equals(request.getName())) {
                // 获取主机列表
                String result = handleListHosts();
                response.put("content", new Object[]{
                    Map.of("type", "text", "text", result)
                });
                response.put("isError", false);
                
            } else if ("select_host".equals(request.getName())) {
                // 选择主机
                String sessionId = request.getSessionId();
                Number hostIdNum = (Number) request.getArguments().get("hostId");
                Long hostId = hostIdNum != null ? hostIdNum.longValue() : null;
                
                String result = handleSelectHost(sessionId, hostId);
                response.put("content", new Object[]{
                    Map.of("type", "text", "text", result)
                });
                response.put("isError", false);
                
            } else if ("ssh_execute_command".equals(request.getName())) {
                String command = (String) request.getArguments().get("command");
                String sessionId = request.getSessionId();
                
                if (command == null || command.trim().isEmpty()) {
                    response.put("content", new Object[]{
                        Map.of("type", "text", "text", "错误: 命令不能为空")
                    });
                    response.put("isError", true);
                    return response;
                }
                
                // 优先使用参数中的 hostId，如果没有则使用会话中选中的主机
                Number hostIdNum = (Number) request.getArguments().get("hostId");
                Long hostIdFromParam = hostIdNum != null ? hostIdNum.longValue() : null;
                Long selectedHostId = hostIdFromParam != null ? hostIdFromParam : sessionSelectedHosts.get(sessionId);
                
                log.info("SSH 执行 - hostIdFromParam: {}, sessionId: {}, selectedHostId: {}", hostIdFromParam, sessionId, selectedHostId);
                
                // 执行 SSH 命令
                String result = executeSshCommand(selectedHostId, command);
                
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
     * 处理获取主机列表
     */
    private String handleListHosts() {
        try {
            List<Host> hosts = hostRepository.findAll();
            log.info("handleListHosts - 主机数量: {}", hosts.size());
            
            if (hosts == null || hosts.isEmpty()) {
                return "当前没有配置任何主机。请先在主机管理中添加主机。";
            }

            StringBuilder result = new StringBuilder("可用主机列表:\n\n");
            for (Host host : hosts) {
                result.append("ID: ").append(host.getId()).append("\n");
                result.append("名称: ").append(host.getName()).append("\n");
                result.append("地址: ").append(host.getHostname()).append(":").append(host.getPort()).append("\n");
                result.append("用户: ").append(host.getUsername()).append("\n");
                result.append("状态: ").append(host.getStatus() != null ? host.getStatus() : "UNKNOWN").append("\n");
                if (host.getDescription() != null && !host.getDescription().isEmpty()) {
                    result.append("描述: ").append(host.getDescription()).append("\n");
                }
                result.append("---\n");
            }
            log.info("handleListHosts 返回: {}", result.toString());
            return result.toString();
        } catch (Exception e) {
            log.error("获取主机列表失败: {}", e.getMessage());
            return "获取主机列表失败: " + e.getMessage();
        }
    }

    /**
     * 处理选择主机
     */
    private String handleSelectHost(String sessionId, Long hostId) {
        try {
            if (hostId == null) {
                return "错误: 请提供有效的主机ID";
            }

            Host host = hostRepository.findById(hostId);
            if (host == null) {
                return "错误: 主机不存在 (ID: " + hostId + ")。请使用 list_hosts 查看可用主机。";
            }

            // 保存选中的主机到会话
            if (sessionId != null && !sessionId.isEmpty()) {
                sessionSelectedHosts.put(sessionId, hostId);
                return "已选择主机: " + host.getName() + " (" + host.getHostname() + ":" + host.getPort() + ")。现在可以在此主机上执行命令了。";
            } else {
                return "错误: 缺少会话ID，无法选择主机";
            }
        } catch (Exception e) {
            log.error("选择主机失败: {}", e.getMessage());
            return "选择主机失败: " + e.getMessage();
        }
    }

    /**
     * 执行 SSH 命令（使用默认配置的主机）
     */
    private String executeSshCommand(String command) throws Exception {
        return executeSshCommand(null, command);
    }
        
    /**
     * 执行 SSH 命令（支持指定主机ID）
     */
    private String executeSshCommand(Long hostId, String command) throws Exception {
        Session session = null;
        ChannelExec channel = null;
        
        String targetHost;
        int targetPort;
        String targetUsername;
        String targetPassword;
        
        // 如果指定了hostId，从数据库获取主机信息
        if (hostId != null) {
            Host host = hostRepository.findById(hostId);
            if (host == null) {
                return "错误: 主机不存在 (ID: " + hostId + ")。请使用 list_hosts 查看可用主机。";
            }
            targetHost = host.getHostname();
            targetPort = host.getPort();
            targetUsername = host.getUsername();
            targetPassword = host.getPassword();
            log.info("在指定主机上执行命令: {} ({}:{}), 用户: {}", host.getName(), targetHost, targetPort, targetUsername);
        } else {
            // 使用默认配置
            targetHost = sshProperties.getHost();
            targetPort = sshProperties.getPort();
            targetUsername = sshProperties.getUsername();
            targetPassword = sshProperties.getPassword();
            log.info("在默认主机上执行命令: {}:{}, 用户: {}", targetHost, targetPort, targetUsername);
            return "错误: 未选择主机，无法执行命令。请先使用 list_hosts 查看可用主机，然后使用 select_host 选择主机。";
        }
        
        try {
            JSch jsch = new JSch();
            
            session = jsch.getSession(targetUsername, targetHost, targetPort);
            session.setPassword(targetPassword);
            session.setConfig("StrictHostKeyChecking", "no");
            session.setTimeout(sshProperties.getTimeout());
            
            session.connect();
            log.info("SSH 连接成功: {}:{}", targetHost, targetPort);
            
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
            if (hostId != null) {
                Host host = hostRepository.findById(hostId);
                result.append("主机: ").append(host != null ? host.getName() : "未知").append("\n");
            }
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
        private String sessionId;
    }
}
