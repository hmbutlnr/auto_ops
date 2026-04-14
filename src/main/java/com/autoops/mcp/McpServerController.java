package com.autoops.mcp;

import com.autoops.chat.service.ChatServiceWithFunctionCalling;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@RestController
@RequestMapping("/api/mcp")
@CrossOrigin(origins = "*")
public class McpServerController {

    private final SshProperties sshProperties;
    private final HostRepository hostRepository;
    private final ChatServiceWithFunctionCalling chatService;
    private static final Pattern IP_PATTERN = Pattern.compile(
        "(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)"
    );

    public McpServerController(SshProperties sshProperties, HostRepository hostRepository, 
                         ChatServiceWithFunctionCalling chatService) {
        this.sshProperties = sshProperties;
        this.hostRepository = hostRepository;
        this.chatService = chatService;
    }

    /**
     * 获取会话选中的主机，从 ChatService 中获取
     */
    private Host getSelectedHostFromSession(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            return null;
        }
        return chatService.getSelectedHost(sessionId);
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
        sshTool.put("description", "通过 SSH 在指定主机上执行 shell 命令");
        
        Map<String, Object> sshInputSchema = new HashMap<>();
        sshInputSchema.put("type", "object");
        
        Map<String, Object> sshProperties = new HashMap<>();
        Map<String, Object> commandProp = new HashMap<>();
        commandProp.put("type", "string");
        commandProp.put("description", "要执行的 shell 命令，例如: df -h, ps aux, uname -a, tail -n 50 /var/log/syslog");
        sshProperties.put("command", commandProp);
        
        Map<String, Object> hostIdProp = new HashMap<>();
        hostIdProp.put("type", "integer");
        hostIdProp.put("description", "可选：目标主机的 ID。如果指定，将在此主机上执行命令；如果不指定，将使用会话中已选择的主机");
        sshProperties.put("hostId", hostIdProp);
        
        sshInputSchema.put("properties", sshProperties);
        sshInputSchema.put("required", new String[]{"command"});
        
        sshTool.put("inputSchema", sshInputSchema);
        
        // 选择主机工具 - 触发前端弹窗让用户选择主机
        Map<String, Object> selectHostTool = new HashMap<>();
        selectHostTool.put("name", "select_host");
        selectHostTool.put("description", "当需要操作主机但不知道目标主机时，调用此工具触发前端弹出主机选择对话框。调用后请等待用户选择主机，不要继续执行其他操作。");
        
        Map<String, Object> selectHostInputSchema = new HashMap<>();
        selectHostInputSchema.put("type", "object");
        selectHostInputSchema.put("properties", new HashMap<>());
        
        selectHostTool.put("inputSchema", selectHostInputSchema);

        // 获取主机列表工具
        Map<String, Object> listHostsTool = new HashMap<>();
        listHostsTool.put("name", "list_hosts");
        listHostsTool.put("description", "获取所有可用的 SSH 主机列表，用于查看有哪些服务器可以连接。返回主机 ID、名称、地址、状态等信息。");
        
        Map<String, Object> listHostsInputSchema = new HashMap<>();
        listHostsInputSchema.put("type", "object");
        listHostsInputSchema.put("properties", new HashMap<>());
        listHostsTool.put("inputSchema", listHostsInputSchema);
        
        // 根据 IP 查询主机工具
        Map<String, Object> getHostByIpTool = new HashMap<>();
        getHostByIpTool.put("name", "get_host_by_ip");
        getHostByIpTool.put("description", "根据 IP 地址查询主机信息。如果用户提供了 IP 地址，可以使用此工具查询对应的主机详情（包括主机 ID）。");
        
        Map<String, Object> getHostByIpInputSchema = new HashMap<>();
        getHostByIpInputSchema.put("type", "object");
        
        Map<String, Object> getHostByIpProps = new HashMap<>();
        Map<String, Object> ipProp = new HashMap<>();
        ipProp.put("type", "string");
        ipProp.put("description", "主机的 IP 地址，例如: 192.168.1.100");
        getHostByIpProps.put("ip", ipProp);
        
        getHostByIpInputSchema.put("properties", getHostByIpProps);
        getHostByIpInputSchema.put("required", new String[]{"ip"});
        
        getHostByIpTool.put("inputSchema", getHostByIpInputSchema);
        
        // 根据主机名查询主机工具
        Map<String, Object> getHostByNameTool = new HashMap<>();
        getHostByNameTool.put("name", "get_host_by_name");
        getHostByNameTool.put("description", "根据主机名称查询主机信息。如果用户提到了主机名称，可以使用此工具查询对应的主机详情（包括主机 ID）。");
        
        Map<String, Object> getHostByNameInputSchema = new HashMap<>();
        getHostByNameInputSchema.put("type", "object");
        
        Map<String, Object> getHostByNameProps = new HashMap<>();
        Map<String, Object> nameProp = new HashMap<>();
        nameProp.put("type", "string");
        nameProp.put("description", "主机名称，例如: prod-server-01");
        getHostByNameProps.put("name", nameProp);
        
        getHostByNameInputSchema.put("properties", getHostByNameProps);
        getHostByNameInputSchema.put("required", new String[]{"name"});
        
        getHostByNameTool.put("inputSchema", getHostByNameInputSchema);
        
        response.put("tools", java.util.Arrays.asList(listHostsTool, selectHostTool, sshTool, getHostByIpTool, getHostByNameTool));
        
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
                // 需要选择主机，告诉前端弹窗，同时设置 needHostSelection=true
                response.put("content", new Object[]{
                    Map.of("type", "text", "text", "__SELECT_HOST_DIALOG__|请在下方选择要操作的主机")
                });
                response.put("isError", false);
                response.put("needHostSelection", true);
                response.put("showHostSelector", true);
                
            } else if ("get_host_by_ip".equals(request.getName())) {
                // 根据IP查询主机
                String ip = (String) request.getArguments().get("ip");
                String result = handleGetHostByIp(ip);
                response.put("content", new Object[]{
                    Map.of("type", "text", "text", result)
                });
                response.put("isError", false);
                
            } else if ("get_host_by_name".equals(request.getName())) {
                // 根据名称查询主机
                String name = (String) request.getArguments().get("name");
                String result = handleGetHostByName(name);
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
                
                // 优先使用参数中的 hostId，如果没有则尝试从会话中获取
                Number hostIdNum = (Number) request.getArguments().get("hostId");
                Long hostIdFromParam = hostIdNum != null ? hostIdNum.longValue() : null;
                Host selectedHost = null;
                
                // 尝试从参数或会话获取主机
                log.info("=== SSH 命令调试 ===");
                log.info("sessionId: [{}], hostIdFromParam: [{}]", sessionId, hostIdFromParam);
                
                if (hostIdFromParam != null) {
                    selectedHost = hostRepository.findById(hostIdFromParam);
                } else if (sessionId != null && !sessionId.isEmpty()) {
                    selectedHost = getSelectedHostFromSession(sessionId);
                    log.info("从 session 获取主机: {}", selectedHost);
                } else {
                    log.info("sessionId 为空，无法获取主机");
                }
                
                log.info("SSH 执行 - hostIdFromParam: {}, sessionId: {}, selectedHost: {}", 
                    hostIdFromParam, sessionId, selectedHost != null ? selectedHost.getName() : "null");
                
                log.info("getSelectedHostFromSession({}) 结果: {}", sessionId, getSelectedHostFromSession(sessionId));
                
                // 执行 SSH 命令
                if (selectedHost == null) {
                    // ��选中主机，返回特殊标记让前端知道需要弹出主机选择
                    response.put("content", new Object[]{
                        Map.of("type", "text", "text", "__NEED_HOST_SELECTION__")
                    });
                    response.put("isError", false);
                    response.put("needHostSelection", true);
                } else {
                    String result = executeSshCommand(selectedHost.getId(), command);
                    response.put("content", new Object[]{
                        Map.of("type", "text", "text", result)
                    });
                    response.put("isError", false);
                }
                
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
     * 处理根据IP查询主机
     */
    private String handleGetHostByIp(String ip) {
        try {
            if (ip == null || ip.trim().isEmpty()) {
                return "错误: 请提供有效的 IP 地址";
            }
            
            // 尝试解析消息中的IP（如果用户提供了其他文本）
            Matcher matcher = IP_PATTERN.matcher(ip);
            String targetIp = matcher.find() ? matcher.group() : ip.trim();
            
            final String finalIp = targetIp;
            Host host = hostRepository.findAll().stream()
                .filter(h -> h.getHostname().equals(finalIp))
                .findFirst()
                .orElse(null);
            
            if (host == null) {
                return "未找到 IP 为 " + finalIp + " 的主机。请先在主机管理中添加或检查 IP 是否正确。";
            }
            
            return formatHostInfo(host);
        } catch (Exception e) {
            log.error("根据 IP 查询主机失败: {}", e.getMessage());
            return "查询失败: " + e.getMessage();
        }
    }
    
    /**
     * 处理根据名称查询主机
     */
    private String handleGetHostByName(String name) {
        try {
            if (name == null || name.trim().isEmpty()) {
                return "错误: 请提供主机名称";
            }
            
            final String targetName = name.trim();
            Host host = hostRepository.findAll().stream()
                .filter(h -> h.getName().equalsIgnoreCase(targetName) || h.getName().contains(targetName))
                .findFirst()
                .orElse(null);
            
            if (host == null) {
                return "未找到名称为 " + targetName + " 的主机。请检查主机名称是否正确。";
            }
            
            return formatHostInfo(host);
        } catch (Exception e) {
            log.error("根据名称查询主机失败: {}", e.getMessage());
            return "查询失败: " + e.getMessage();
        }
    }
    
    /**
     * 格式化主机信息
     */
    private String formatHostInfo(Host host) {
        StringBuilder result = new StringBuilder();
        result.append("主机信息:\n");
        result.append("ID: ").append(host.getId()).append("\n");
        result.append("名称: ").append(host.getName()).append("\n");
        result.append("地址: ").append(host.getHostname()).append(":").append(host.getPort()).append("\n");
        result.append("用户: ").append(host.getUsername()).append("\n");
        result.append("状态: ").append(host.getStatus() != null ? host.getStatus() : "UNKNOWN").append("\n");
        if (host.getDescription() != null && !host.getDescription().isEmpty()) {
            result.append("描述: ").append(host.getDescription()).append("\n");
        }
        return result.toString();
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

            // 保存选中的主机到 ChatService
            if (sessionId != null && !sessionId.isEmpty()) {
                chatService.setSelectedHost(sessionId, host);
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
