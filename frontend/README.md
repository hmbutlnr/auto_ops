# Auto Ops Frontend

基于 Vue 3 + Vite 构建的前端应用,与后端 API 分离。

## 技术栈

- **Vue 3** - 渐进式 JavaScript 框架
- **Vite** - 下一代前端构建工具
- **Axios** - HTTP 客户端

## 项目结构

```
frontend/
├── src/
│   ├── api/
│   │   └── chat.js          # API 请求模块
│   ├── components/
│   │   ├── ChatBox.vue      # 聊天框组件
│   │   └── MessageItem.vue  # 消息项组件
│   ├── App.vue              # 根组件
│   └── main.js              # 入口文件
├── index.html               # HTML 模板
├── vite.config.js           # Vite 配置
├── package.json             # 依赖配置
└── README.md                # 说明文档
```

## 开发

### 安装依赖

```bash
npm install
```

### 启动开发服务器

```bash
npm run dev
```

前端将在 http://localhost:3000 运行,并自动代理 API 请求到后端

### 启动后端服务

在后端项目根目录执行:

```bash
mvn spring-boot:run
```

后端 API 将在 http://localhost:8080 运行

## 构建

### 生产环境构建

```bash
npm run build
```

构建产物将输出到 `dist/` 目录

### 预览生产构建

```bash
npm run preview
```

## 配置

### 开发环境

开发环境下,Vite 会自动将 `/api` 请求代理到 `http://localhost:8080`,配置在 `vite.config.js` 中。

### 生产环境

生产环境部署时,需要配置实际的 API 地址。可以:

1. 修改 `vite.config.js` 中的代理配置
2. 使用环境变量配置 API 地址
3. 通过 Nginx 等反向代理配置

## 功能

- 聊天对话界面
- 新建会话
- 实时消息收发
- 与 AI 助手交互
- 响应式设计

## 组件说明

### ChatBox.vue

主要的聊天界面组件,包含:
- 消息列表显示
- 输入框和发送按钮
- 新建对话功能

### MessageItem.vue

消息项组件,负责渲染单条消息:
- 支持用户和助手两种角色
- 自动格式化换行符
