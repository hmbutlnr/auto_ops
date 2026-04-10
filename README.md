# Auto Ops - AI 智能运维助手

## 📖 项目简介

Auto Ops 是一个基于 Spring Boot 和 AI 的智能运维助手，支持通过自然语言对话来执行远程服务器的 SSH 命令，实现智能化的服务器管理和监控。

## 🎯 核心功能

- ✅ **AI 对话交互** - 使用自然语言与 AI 对话
- ✅ **智能工具调用** - AI 主动决定何时执行 SSH 命令（Function Calling）
- ✅ **远程服务器管理** - 通过 SSH 执行远程命令
- ✅ **多轮对话支持** - 上下文感知，支持复杂任务
- ✅ **实时结果反馈** - 命令执行结果即时返回

## 🏗️ 技术架构

### 技术栈

- **后端框架**: Spring Boot 3.2.0
- **Java 版本**: Java 17
- **AI 服务**: 阿里云 DashScope (通义千问)
- **SSH 客户端**: JSch 0.1.55
- **构建工具**: Maven

### 架构图

```
┌──────────────┐
│   用户界面    │  ← Web 浏览器 / API 客户端
│  (Frontend)  │
└──────┬───────┘
       │ HTTP
       ▼
┌──────────────────┐
│ ChatController   │  ← REST API 端点
└──────┬───────────┘
       │
       ▼
┌──────────────────────────┐
│ChatServiceWithFunction   │  ← Function Calling 核心逻辑
│     Calling              │
└──────┬───────────────────┘
       │
       ├──────────────────────┐
       │                      │
       ▼                      ▼
┌──────────────┐    ┌─────────────────┐
│ DashScope API│    │   SshService    │
│  (AI Model)  │    │  (JSch SSH)     │
└──────────────┘    └────────┬────────┘
                             │
                             │ SSH
                             ▼
                    ┌─────────────────┐
                    │ Remote Server   │
                    │  (目标服务器)    │
                    └─────────────────┘
```

## 📁 项目结构

```
auto-ops/
├── src/main/java/com/autoops/
│   ├── AutoOpsApplication.java          # 应用入口
│   ├── config/
│   │   ├── DashScopeConfig.java         # DashScope 配置
│   │   └── SshProperties.java           # SSH 配置
│   ├── chat/
│   │   ├── controller/
│   │   │   └── ChatController.java      # 聊天 API 控制器
│   │   ├── service/
│   │   │   └── ChatServiceWithFunctionCalling.java  # Function Calling 服务
│   │   ├── model/
│   │   │   ├── ChatMessage.java         # 聊天消息模型
│   │   │   └── ChatSession.java         # 会话模型
│   │   └── dto/
│   │       ├── ChatRequest.java         # 请求 DTO
│   │       └── ChatResponse.java        # 响应 DTO
│   ├── service/
│   │   └── SshService.java              # SSH 命令执行服务
│   └── mcp/
│       ├── McpServerController.java     # MCP Server API（可选）
│       └── McpClient.java               # MCP 客户端（可选）
├── src/main/resources/
│   ├── application.yml                  # 应用配置
│   └── static/
│       └── index.html                   # 前端页面
├── pom.xml                              # Maven 配置
├── FUNCTION_CALLING_USAGE.md            # Function Calling 使用指南
├── MCP_SERVER_USAGE.md                  # MCP Server 使用指南
├── COMPARISON.md                        # 方案对比
└── README.md                            # 本文件
```

## 🚀 快速开始

### 1. 环境要求

- Java 17+
- Maven 3.6+
- 阿里云 DashScope API Key

### 2. 配置

编辑 `src/main/resources/application.yml`：

```yaml
server:
  port: 8080

# 阿里千问配置
dashscope:
  api-key: sk-your-api-key-here
  base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
  model: qwen-plus-latest

# SSH服务器配置
ssh:
  host: your-server-ip
  port: 22
  username: root
  password: your-password
  timeout: 30000
```

### 3. 编译和运行

```bash
# 编译
mvn clean package

# 运行
mvn spring-boot:run
```

应用将在 http://localhost:8080 启动。

### 4. 测试

访问 http://localhost:8080 或在终端测试：

```bash
curl -X POST http://localhost:8080/api/chat/chat \
  -H "Content-Type: application/json" \
  -d '{
    "message": "帮我查看服务器的磁盘使用情况"
  }'
```

## 💬 使用示例

### 示例 1: 查看系统信息

```
用户: 帮我查看服务器的系统信息

AI: 我已经为您查看了服务器的系统信息：

系统类型: Linux server 5.4.0-42-generic #46-Ubuntu SMP
架构: x86_64
主机名: server

这是一个 Ubuntu 20.04 系统，运行在 64 位架构上。
```

### 示例 2: 检查资源使用

```
用户: 检查服务器的内存和磁盘使用情况

AI: 我已为您检查了资源使用情况：

📊 内存使用:
- 总内存: 7.8GB
- 已使用: 2.1GB (27%)
- 可用: 5.2GB

💾 磁盘使用:
- 根分区: 40% 使用 (20G/50G)
- 可用空间: 30G

✅ 系统资源充足，运行状态良好。
```

### 示例 3: 查看日志

```
用户: 查看 nginx 最近的错误日志

AI: 我已查看 nginx 最近的错误日志（最后 20 行）：

[发现 2 个警告]
1. 缺少 favicon.ico 文件
2. 上游响应缓冲警告

建议：添加 favicon.ico 文件，检查 nginx 缓冲区配置。
```

## 🔧 API 文档

### 发送聊天消息

**端点:** `POST /api/chat/chat`

**请求体:**
```json
{
  "sessionId": "optional-session-id",
  "message": "帮我查看服务器的系统信息"
}
```

**响应:**
```json
{
  "sessionId": "abc-123-def",
  "reply": "系统信息如下：...",
  "success": true,
  "error": null
}
```

### 创建新会话

**端点:** `POST /api/chat/new-session`

**响应:**
```
session-id-uuid
```

### 获取会话历史

**端点:** `GET /api/chat/session/{sessionId}`

**响应:**
```json
{
  "sessionId": "abc-123",
  "messages": [
    {
      "role": "user",
      "content": "你好",
      "timestamp": "2026-04-10T16:00:00"
    },
    {
      "role": "assistant",
      "content": "你好！有什么可以帮助您的？",
      "timestamp": "2026-04-10T16:00:01"
    }
  ]
}
```

## 🔒 安全考虑

### 1. 命令白名单

建议在 `SshService` 中添加命令验证：

```java
private static final Set<String> ALLOWED_COMMANDS = Set.of(
    "uname", "hostname", "df", "free", "top", "ps",
    "systemctl", "service", "tail", "cat", "ls", "pwd"
);
```

### 2. 权限控制

- 使用最小权限的 SSH 账号
- 避免使用 root 用户
- 配置 sudo 权限限制

### 3. 审计日志

所有命令执行都会记录到应用日志中，便于追溯。

### 4. 超时控制

设置合理的 SSH 超时时间，防止长时间运行的命令阻塞。

## 📊 性能指标

- **响应时间**: ~2-5 秒（取决于 AI 响应和 SSH 连接）
- **并发能力**: 支持多个会话同时对话
- **内存占用**: ~200-300 MB
- **CPU 使用**: 低（主要在 SSH 命令执行时）

## 🛠️ 扩展开发

### 添加新的工具

1. 在 `ChatServiceWithFunctionCalling.buildToolsDefinition()` 中添加工具定义
2. 在 `executeToolCall()` 中添加工具处理逻辑
3. 实现具体的业务逻辑

示例：添加文件读取工具

```java
// 工具定义
Map<String, Object> readFileTool = new HashMap<>();
readFileTool.put("name", "ssh_read_file");
readFileTool.put("description", "读取远程服务器上的文件内容");
// ... 添加参数定义

// 工具执行
if ("ssh_read_file".equals(functionName)) {
    String filePath = (String) arguments.get("path");
    return readFileViaSsh(filePath);
}
```

### 添加更多 SSH 功能

- 文件上传/下载
- 批量命令执行
- 实时监控
- 日志分析

## 📝 相关文档

- [Function Calling 使用指南](FUNCTION_CALLING_USAGE.md) - 详细的 Function Calling 使用说明
- [MCP Server 使用指南](MCP_SERVER_USAGE.md) - MCP Server 方案说明
- [方案对比](COMPARISON.md) - 不同方案的对比分析

## 🤝 贡献指南

欢迎提交 Issue 和 Pull Request！

## 📄 许可证

MIT License

## 👥 作者

**李富豪**

- 创建日期: 2026-04-10
- 版本: 1.0.0

## 🙏 致谢

- [Spring Boot](https://spring.io/projects/spring-boot)
- [阿里云 DashScope](https://dashscope.aliyun.com/)
- [JSch](http://www.jcraft.com/jsch/)

---

**注意**: 本项目仅供学习和研究使用，生产环境请做好充分的安全评估和测试。
