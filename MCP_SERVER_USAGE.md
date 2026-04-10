# MCP Server SSH 功能使用指南

## 📖 概述

本项目实现了基于 **MCP (Model Context Protocol)** 的 SSH 命令执行功能。通过标准化的 MCP 协议，AI 可以调用工具来执行远程服务器上的命令。

## 🏗️ 架构设计

```
┌─────────────┐         ┌──────────────┐         ┌─────────────┐
│   AI Model  │◄────────│ ChatService  │◄────────│  MCP Client │
│  (Qwen)     │         │              │         │             │
└─────────────┘         └──────────────┘         └──────┬──────┘
                                                         │
                                                         │ HTTP
                                                         ▼
                                                ┌────────────────┐
                                                │McpServerControl│
                                                │      ler       │
                                                └───────┬────────┘
                                                        │
                                                        │ JSch
                                                        ▼
                                                ┌────────────────┐
                                                │ Remote Server  │
                                                │   (via SSH)    │
                                                └────────────────┘
```

## 🔧 核心组件

### 1. McpServerController
**位置**: `com.autoops.mcp.McpServerController`

提供 MCP Server 的 HTTP API 端点：
- `GET /api/mcp/info` - 获取服务器信息
- `GET /api/mcp/tools` - 列出可用工具
- `POST /api/mcp/tools/call` - 调用工具

### 2. McpClient
**位置**: `com.autoops.mcp.McpClient`

MCP 客户端，用于调用 MCP Server 的工具：
- `getServerInfo()` - 获取服务器信息
- `listTools()` - 列出可用工具
- `callTool(toolName, arguments)` - 调用指定工具

### 3. ChatService
**位置**: `com.autoops.chat.service.ChatService`

集成 MCP 工具调用到聊天流程中，自动检测和执行为 `@tool{...}` 格式的工具调用。

## 📡 MCP API 端点

### 1. 获取服务器信息

**请求:**
```http
GET /api/mcp/info
```

**响应:**
```json
{
  "name": "auto-ops-mcp-server",
  "version": "1.0.0",
  "description": "Auto Ops MCP Server - 提供 SSH 命令执行能力",
  "capabilities": {
    "tools": true,
    "resources": false,
    "prompts": false
  }
}
```

### 2. 列出可用工具

**请求:**
```http
GET /api/mcp/tools
```

**响应:**
```json
{
  "tools": [
    {
      "name": "ssh_execute_command",
      "description": "通过 SSH 在远程服务器上执行命令",
      "inputSchema": {
        "type": "object",
        "properties": {
          "command": {
            "type": "string",
            "description": "要执行的 shell 命令，例如: df -h, ps aux, uname -a"
          }
        },
        "required": ["command"]
      }
    }
  ]
}
```

### 3. 调用工具

**请求:**
```http
POST /api/mcp/tools/call
Content-Type: application/json

{
  "name": "ssh_execute_command",
  "arguments": {
    "command": "df -h"
  }
}
```

**响应:**
```json
{
  "content": [
    {
      "type": "text",
      "text": "命令: df -h\n退出码: 0\n\n输出:\nFilesystem      Size  Used Avail Use% Mounted on\n/dev/sda1        50G   20G   30G  40% /"
    }
  ],
  "isError": false
}
```

## 💬 使用方式

### 方式一：直接调用 MCP API

你可以直接使用 HTTP 客户端调用 MCP Server：

```bash
# 1. 查看可用工具
curl http://localhost:8080/api/mcp/tools

# 2. 执行 SSH 命令
curl -X POST http://localhost:8080/api/mcp/tools/call \
  -H "Content-Type: application/json" \
  -d '{
    "name": "ssh_execute_command",
    "arguments": {
      "command": "uname -a"
    }
  }'
```

### 方式二：通过 AI 对话（推荐）

当用户与 AI 对话时，AI 可以生成工具调用标记，系统会自动执行：

**用户输入:**
```
帮我查看服务器的磁盘使用情况
```

**AI 响应（内部）:**
```
我将为您检查磁盘使用情况。

@tool{"name":"ssh_execute_command","arguments":{"command":"df -h"}}
```

**最终返回给用户:**
```
我将为您检查磁盘使用情况。

**工具调用:** `ssh_execute_command`

**执行结果:**
```
命令: df -h
退出码: 0

输出:
Filesystem      Size  Used Avail Use% Mounted on
/dev/sda1        50G   20G   30G  40% /
tmpfs           7.8G     0  7.8G   0% /dev/shm
```
```

## ⚙️ 配置说明

### SSH 服务器配置

在 `application.yml` 中配置：

```yaml
ssh:
  host: your-server-ip      # 服务器 IP
  port: 22                  # SSH 端口
  username: root            # 用户名
  password: your-password   # 密码
  timeout: 30000            # 超时时间（毫秒）
```

## 🎯 支持的命令类型

### ✅ 推荐的只读命令

- **系统信息**: `uname -a`, `hostname`, `whoami`
- **资源监控**: `df -h`, `free -h`, `top -bn1`, `uptime`
- **进程管理**: `ps aux`, `pgrep nginx`
- **服务状态**: `systemctl status nginx`, `service ssh status`
- **日志查看**: `tail -n 50 /var/log/syslog`, `journalctl -u nginx --no-pager -n 20`
- **文件操作**: `ls -la`, `pwd`, `find /var/log -name "*.log"`, `cat /etc/hostname`
- **网络信息**: `netstat -tulpn`, `ss -tulpn`, `ip addr show`

### ⚠️ 谨慎使用的命令

- **文件修改**: `echo "config" > /etc/config`, `sed -i 's/old/new/g' file`
- **服务管理**: `systemctl restart nginx`, `systemctl stop mysql`

### ❌ 禁止的命令（建议添加白名单）

- **删除操作**: `rm -rf /`, `dd if=/dev/zero of=/dev/sda`
- **格式化**: `mkfs.ext4 /dev/sda1`
- **危险操作**: `chmod 777 /`, `chown -R root:root /`

## 🔒 安全建议

### 1. 命令白名单机制

建议在 `McpServerController` 中添加命令验证：

```java
private static final Set<String> ALLOWED_COMMANDS = Set.of(
    "uname", "hostname", "df", "free", "top", "ps", 
    "systemctl", "service", "tail", "cat", "ls", "pwd"
);

private boolean isCommandAllowed(String command) {
    String cmd = command.trim().split("\\s+")[0];
    return ALLOWED_COMMANDS.contains(cmd);
}
```

### 2. 权限最小化

- 使用专门的运维账号，而非 root
- 配置 sudo 权限，限制可执行的命令
- 使用 SSH key 认证替代密码

### 3. 审计日志

所有命令执行都会记录到日志中：
```
2026-04-10 16:20:00 INFO  - 收到工具调用请求: ssh_execute_command
2026-04-10 16:20:00 INFO  - 执行 SSH 命令: df -h
2026-04-10 16:20:01 INFO  - SSH 连接成功: 192.168.1.100:22
2026-04-10 16:20:01 INFO  - 命令执行完成，退出码: 0
2026-04-10 16:20:01 INFO  - SSH 连接已关闭
```

### 4. 超时控制

设置合理的超时时间，防止长时间运行的命令阻塞：
```yaml
ssh:
  timeout: 30000  # 30秒超时
```

## 🧪 测试示例

### 测试 1: 查看系统信息

```bash
curl -X POST http://localhost:8080/api/mcp/tools/call \
  -H "Content-Type: application/json" \
  -d '{
    "name": "ssh_execute_command",
    "arguments": {
      "command": "uname -a"
    }
  }'
```

### 测试 2: 检查磁盘使用

```bash
curl -X POST http://localhost:8080/api/mcp/tools/call \
  -H "Content-Type: application/json" \
  -d '{
    "name": "ssh_execute_command",
    "arguments": {
      "command": "df -h"
    }
  }'
```

### 测试 3: 查看内存使用

```bash
curl -X POST http://localhost:8080/api/mcp/tools/call \
  -H "Content-Type: application/json" \
  -d '{
    "name": "ssh_execute_command",
    "arguments": {
      "command": "free -h"
    }
  }'
```

## 🚀 启动应用

```bash
# 编译
mvn clean package

# 运行
mvn spring-boot:run
```

应用将在 `http://localhost:8080` 启动。

## 📊 扩展开发

### 添加新的 MCP 工具

1. 在 `McpServerController.listTools()` 中添加新工具定义
2. 在 `McpServerController.callTool()` 中添加工具处理逻辑
3. 实现具体的业务逻辑

示例：添加文件读取工具

```java
// 在 listTools() 中添加
Map<String, Object> readFileTool = new HashMap<>();
readFileTool.put("name", "ssh_read_file");
readFileTool.put("description", "读取远程服务器上的文件内容");
// ... 添加 inputSchema

// 在 callTool() 中添加
if ("ssh_read_file".equals(request.getName())) {
    String filePath = (String) request.getArguments().get("path");
    String content = readFileViaSsh(filePath);
    // 返回结果
}
```

## 🆚 MCP vs 直接集成

| 特性 | MCP Server | 直接集成 |
|------|-----------|---------|
| **标准化** | ✅ 遵循 MCP 协议 | ❌ 自定义实现 |
| **可扩展性** | ✅ 易于添加新工具 | ⚠️ 需要修改代码 |
| **解耦** | ✅ 工具和业务分离 | ❌ 紧耦合 |
| **生态兼容** | ✅ 支持多个 AI 平台 | ❌ 仅当前项目 |
| **复杂度** | ⚠️ 稍复杂 | ✅ 简单直接 |
| **适用场景** | 多工具、多平台 | 单一功能 |

## 📝 总结

MCP Server 方案提供了：
- ✅ 标准化的工具调用接口
- ✅ 良好的可扩展性
- ✅ 与多个 AI 平台的兼容性
- ✅ 清晰的职责分离

适合需要：
- 管理多个工具的场景
- 与不同 AI 平台集成
- 长期维护和扩展的项目

---

**开发者**: 李富豪  
**创建日期**: 2026-04-10  
**版本**: 1.0.0
