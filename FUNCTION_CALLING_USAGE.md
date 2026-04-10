# Function Calling SSH 功能使用指南

## 🎯 概述

本项目实现了基于 **Function Calling** 的 AI 智能运维助手。AI 可以主动决定何时调用 SSH 工具来执行远程命令，实现真正的智能化运维对话。

## ✨ 核心优势

### 相比其他方案的优势

| 特性 | 直接集成 | MCP Server | **Function Calling** ✅ |
|------|---------|------------|----------------------|
| AI 主动性 | ❌ 被动检测 | ❌ 需要外部客户端 | ✅ AI 主动决定 |
| 用户体验 | ⚠️ 需确认 | ⚠️ 需安装客户端 | ✅ 完全自动化 |
| 实现复杂度 | 低 | 中 | 中 |
| 标准化 | ❌ | ✅ | ✅ |
| 多轮对话 | ❌ | ⚠️ | ✅ |
| 错误处理 | 简单 | 中等 | 完善 |

## 🏗️ 工作原理

```
用户: "帮我查看服务器的磁盘使用情况"
  ↓
┌─────────────────────────────────────┐
│ 第一轮：AI 分析并请求调用工具        │
│                                     │
│ AI: 我需要调用 ssh_execute_command  │
│     工具来检查磁盘                  │
│     tool_calls: [{                  │
│       name: "ssh_execute_command",  │
│       arguments: {command: "df -h"} │
│     }]                              │
└──────────────┬──────────────────────┘
               ↓
┌─────────────────────────────────────┐
│ 系统执行 SSH 命令                    │
│                                     │
│ 连接到服务器 → 执行 df -h           │
│ 返回结果                             │
└──────────────┬──────────────────────┘
               ↓
┌─────────────────────────────────────┐
│ 第二轮：AI 基于结果生成最终回复      │
│                                     │
│ AI: 根据检查结果，您的磁盘使用情况   │
│     如下：                           │
│     /dev/sda1 使用率 40%            │
│     空间充足，无需担心              │
└──────────────┬──────────────────────┘
               ↓
          返回给用户
```

## 🔧 核心组件

### 1. ChatServiceWithFunctionCalling
**位置**: `com.autoops.chat.service.ChatServiceWithFunctionCalling`

实现 Function Calling 的核心逻辑：
- 定义可用工具（SSH 执行命令）
- 第一轮调用：让 AI 决定是否调用工具
- 执行工具调用
- 第二轮调用：将工具结果返回给 AI，生成最终回复

### 2. SshService
**位置**: `com.autoops.service.SshService`

SSH 命令执行服务：
- 建立 SSH 连接
- 执行远程命令
- 返回命令输出和退出码

### 3. 工具定义

```json
{
  "type": "function",
  "function": {
    "name": "ssh_execute_command",
    "description": "通过 SSH 在远程服务器上执行 shell 命令。用于查看系统信息、检查资源使用、查看日志等运维操作。",
    "parameters": {
      "type": "object",
      "properties": {
        "command": {
          "type": "string",
          "description": "要执行的 shell 命令，例如: df -h, ps aux, uname -a, tail -n 50 /var/log/syslog"
        }
      },
      "required": ["command"]
    }
  }
}
```

## 💬 使用示例

### 示例 1: 查看系统信息

**用户输入:**
```
帮我查看服务器的系统信息
```

**内部流程:**
1. AI 分析后决定调用 `ssh_execute_command` 工具
2. 系统执行 `uname -a`
3. AI 基于结果生成友好回复

**最终回复:**
```
我已经为您查看了服务器的系统信息：

系统类型: Linux server 5.4.0-42-generic #46-Ubuntu SMP
架构: x86_64
主机名: server

这是一个 Ubuntu 20.04 系统，运行在 64 位架构上。
```

---

### 示例 2: 检查磁盘使用

**用户输入:**
```
检查磁盘使用情况，看看空间够不够
```

**内部流程:**
1. AI 调用 `ssh_execute_command(command="df -h")`
2. 获取磁盘使用信息
3. AI 分析并给出建议

**最终回复:**
```
根据您的磁盘使用情况：

文件系统      大小  已用  可用  使用率  挂载点
/dev/sda1      50G   20G   30G    40%    /
tmpfs         7.8G     0  7.8G     0%    /dev/shm

✅ 磁盘空间充足，当前使用率为 40%，还有 30G 可用空间。
建议：目前无需清理，但建议定期监控使用情况。
```

---

### 示例 3: 查看内存使用

**用户输入:**
```
内存使用怎么样？
```

**内部流程:**
1. AI 调用 `ssh_execute_command(command="free -h")`
2. 获取内存信息
3. AI 分析并总结

**最终回复:**
```
当前内存使用情况：

              total        used        free      shared  buff/cache   available
Mem:           7.8G        2.1G        3.2G        256M        2.5G        5.2G
Swap:          2.0G          0B        2.0G

✅ 内存使用正常：
- 总内存: 7.8GB
- 已使用: 2.1GB (27%)
- 可用: 5.2GB
- Swap 未使用

系统内存充足，运行状态良好。
```

---

### 示例 4: 查看进程

**用户输入:**
```
显示占用 CPU 最高的前 5 个进程
```

**内部流程:**
1. AI 调用 `ssh_execute_command(command="ps aux --sort=-%cpu | head -n 6")`
2. 获取进程列表
3. AI 格式化并解释

**最终回复:**
```
以下是占用 CPU 最高的前 5 个进程：

USER       PID %CPU %MEM    VSZ   RSS TTY      STAT START   TIME COMMAND
root         1  0.5  0.1 169316 11264 ?        Ss   Apr09   0:15 /sbin/init
nginx     1234  0.3  0.2 256780 18432 ?        S    Apr09   0:08 nginx: worker process
mysql     2345  0.2  1.5 1234560 123456 ?      Sl   Apr09   0:05 /usr/sbin/mysqld
root      3456  0.1  0.0  56789  4567 ?        Ss   Apr09   0:02 /usr/sbin/sshd

📊 分析：
- CPU 使用率都很低，最高仅 0.5%
- 主要进程：init、nginx、mysql、sshd
- 系统负载很低，运行正常
```

---

### 示例 5: 查看日志

**用户输入:**
```
查看 nginx 最近的错误日志
```

**内部流程:**
1. AI 调用 `ssh_execute_command(command="tail -n 20 /var/log/nginx/error.log")`
2. 获取日志内容
3. AI 分析是否有错误

**最终回复:**
```
我已查看 nginx 最近的错误日志（最后 20 行）：

```
2026/04/10 10:15:23 [error] 1234#0: *5678 open() "/var/www/html/favicon.ico" failed (2: No such file or directory)
2026/04/10 10:20:45 [warn] 1234#0: *5679 an upstream response is buffered to a temporary file
```

⚠️ 发现的问题：
1. 缺少 favicon.ico 文件（轻微问题，不影响功能）
2. 有上游响应缓冲警告（可能需要优化配置）

建议：
- 添加 favicon.ico 文件消除 404 错误
- 检查 nginx 缓冲区配置
```

---

### 示例 6: 综合检查

**用户输入:**
```
帮我做个服务器健康检查
```

**内部流程:**
AI 可能会多次调用工具：
1. `uname -a` - 系统信息
2. `uptime` - 运行时间
3. `df -h` - 磁盘使用
4. `free -h` - 内存使用
5. `top -bn1 | head -n 5` - 负载情况

**最终回复:**
```
🏥 服务器健康检查报告

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

✅ 系统信息
- 系统: Ubuntu 20.04 LTS
- 内核: 5.4.0-42-generic
- 架构: x86_64

✅ 运行时间
- 已运行: 15 days, 3 hours
- 负载平均: 0.15, 0.10, 0.08 (很低)

✅ 磁盘使用
- 根分区: 40% 使用 (20G/50G)
- 状态: 良好

✅ 内存使用
- 总内存: 7.8GB
- 使用率: 27%
- 状态: 充足

✅ 进程状态
- 关键服务运行正常
- 无异常高负载进程

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

🎉 总体评估: 服务器健康状况良好
所有指标都在正常范围内，无需立即干预。

💡 建议：
- 继续保持当前监控
- 定期备份重要数据
- 关注磁盘使用趋势
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

### DashScope API 配置

```yaml
dashscope:
  api-key: sk-your-api-key
  base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
  model: qwen-plus-latest   # 推荐使用支持 Function Calling 的模型
```

## 🔒 安全建议

### 1. 命令白名单（推荐）

虽然 AI 会生成合理的命令，但建议添加额外的验证层：

```java
private static final Set<String> ALLOWED_COMMANDS = Set.of(
    "uname", "hostname", "whoami",
    "df", "free", "top", "uptime",
    "ps", "pgrep",
    "systemctl", "service",
    "tail", "cat", "head", "grep",
    "ls", "pwd", "find",
    "netstat", "ss", "ip"
);

private boolean isCommandAllowed(String command) {
    String cmd = command.trim().split("\\s+")[0];
    return ALLOWED_COMMANDS.contains(cmd);
}
```

### 2. 危险命令过滤

```java
private static final List<String> DANGEROUS_PATTERNS = Arrays.asList(
    "rm -rf /",
    "dd if=",
    "mkfs",
    "chmod 777",
    "> /dev/sda"
);

private boolean isDangerous(String command) {
    return DANGEROUS_PATTERNS.stream()
        .anyMatch(pattern -> command.contains(pattern));
}
```

### 3. 权限最小化

- 使用专门的运维账号，而非 root
- 配置 sudo 权限，限制可执行的命令
- 使用 SSH key 认证替代密码

### 4. 审计日志

所有命令执行都会记录到日志：
```
2026-04-10 16:57:00 INFO  - 执行 SSH 命令: df -h
2026-04-10 16:57:00 INFO  - SSH连接成功: 192.168.1.100:22
2026-04-10 16:57:01 INFO  - 命令执行完成，退出码: 0
2026-04-10 16:57:01 INFO  - SSH连接已关闭
```

## 🚀 启动应用

```bash
# 编译
mvn clean package

# 运行
mvn spring-boot:run
```

应用将在 `http://localhost:8080` 启动。

## 📊 API 端点

### 发送聊天消息

**请求:**
```http
POST /api/chat/chat
Content-Type: application/json

{
  "sessionId": "optional-session-id",
  "message": "帮我查看服务器的磁盘使用情况"
}
```

**响应:**
```json
{
  "sessionId": "abc-123-def",
  "reply": "根据您的磁盘使用情况：\n\n文件系统      大小  已用  可用  使用率  挂载点\n/dev/sda1      50G   20G   30G    40%    /\n\n✅ 磁盘空间充足...",
  "success": true,
  "error": null
}
```

## 🎓 技术细节

### Function Calling 流程

1. **第一轮调用**
   ```java
   // 发送消息 + 工具定义给 AI
   Map<String, Object> response = callDashScopeWithTools(messages, tools);
   
   // AI 可能返回：
   // {
   //   "message": {
   //     "content": null,
   //     "tool_calls": [{
   //       "id": "call_xxx",
   //       "function": {
   //         "name": "ssh_execute_command",
   //         "arguments": "{\"command\":\"df -h\"}"
   //       }
   //     }]
   //   }
   // }
   ```

2. **执行工具**
   ```java
   // 解析工具调用
   String command = extractCommand(toolCall);
   
   // 执行 SSH 命令
   String result = sshService.executeCommand(command);
   ```

3. **第二轮调用**
   ```java
   // 将工具结果返回给 AI
   messages.add(buildToolMessage(toolCall, result));
   
   // AI 基于结果生成最终回复
   Map<String, Object> finalResponse = callDashScopeWithTools(messages, tools);
   ```

### 支持的模型

DashScope 以下模型支持 Function Calling：
- ✅ qwen-plus-latest
- ✅ qwen-max-latest
- ✅ qwen-turbo-latest

## 🆚 与其他方案对比

### vs 直接集成

| 方面 | 直接集成 | Function Calling |
|------|---------|------------------|
| AI 是否主动 | ❌ 被动检测代码块 | ✅ 主动决定调用 |
| 多轮对话 | ❌ 不支持 | ✅ 完整支持 |
| 参数传递 | ⚠️ 只能传命令字符串 | ✅ 结构化参数 |
| 错误恢复 | ⚠️ 困难 | ✅ AI 可以重试 |
| 扩展性 | ⚠️ 需修改正则 | ✅ 只需添加工具定义 |

### vs MCP Server

| 方面 | MCP Server | Function Calling |
|------|-----------|------------------|
| 部署方式 | 独立服务 | 集成在应用中 |
| 客户端 | 需要外部 MCP 客户端 | 内置，无需额外客户端 |
| 适用场景 | 开发者工具 | Web 应用 |
| 用户体验 | 需安装客户端 | 浏览器即可使用 |

## 📝 总结

Function Calling 方案提供了：
- ✅ **AI 主动性** - AI 智能决定何时调用工具
- ✅ **自然体验** - 用户像与真人对话一样
- ✅ **多轮交互** - 支持复杂的工具调用链
- ✅ **易于扩展** - 轻松添加新工具
- ✅ **标准化** - 遵循 OpenAI Function Calling 规范

这是目前**最优雅、最智能**的 AI + 工具调用方案！

---

**开发者**: 李富豪  
**创建日期**: 2026-04-10  
**版本**: 1.0.0
