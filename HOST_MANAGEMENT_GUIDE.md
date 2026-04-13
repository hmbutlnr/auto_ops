# 主机管理模块使用说明

## 功能概述

主机管理模块允许你管理和监控多个 SSH 主机,包括:
- 添加、编辑、删除主机
- 测试主机连接状态
- 批量刷新所有主机状态
- 查看主机详细信息

## 技术架构

### 后端
- **数据模型**: `Host` - 主机信息实体
- **Repository**: `HostRepository` - 数据存储层(内存存储)
- **Service**: `HostService` - 业务逻辑层
- **Controller**: `HostController` - REST API 接口

### 前端
- **框架**: Vue 3 + Vite
- **路由**: Vue Router
- **API**: Axios
- **组件**:
  - `HostManagement.vue` - 主机管理主页面
  - 路由导航集成

## API 接口

### 1. 获取所有主机
```
GET /api/host
```

### 2. 获取单个主机
```
GET /api/host/{id}
```

### 3. 创建主机
```
POST /api/host
Content-Type: application/json

{
  "name": "生产服务器1",
  "hostname": "192.168.1.100",
  "port": 22,
  "username": "root",
  "password": "password",
  "description": "生产环境服务器"
}
```

### 4. 更新主机
```
PUT /api/host/{id}
Content-Type: application/json

{
  "name": "生产服务器1",
  "hostname": "192.168.1.100",
  "port": 22,
  "username": "root",
  "password": "newpassword",
  "description": "更新后的描述"
}
```

### 5. 删除主机
```
DELETE /api/host/{id}
```

### 6. 测试主机连接
```
POST /api/host/{id}/test
```

### 7. 批量刷新主机状态
```
POST /api/host/refresh-status
```

## 使用步骤

### 1. 启动后端服务
```bash
cd d:\IDEAWorkSpace2\auto_ops
mvn spring-boot:run
```

后端服务将在 http://localhost:8080 启动

### 2. 启动前端开发服务器
```bash
cd d:\IDEAWorkSpace2\auto_ops\frontend
npm run dev
```

前端服务将在 http://localhost:3000 启动

### 3. 访问主机管理页面
在浏览器中打开 http://localhost:3000/hosts

### 4. 添加主机
- 点击"添加主机"按钮
- 填写主机信息:
  - **主机名称**: 自定义的主机标识名称
  - **主机地址**: IP 地址或域名
  - **端口**: SSH 端口(默认 22)
  - **用户名**: SSH 登录用户名
  - **密码**: SSH 登录密码
  - **描述**: 可选的主机描述信息
- 点击"确定"保存

### 5. 测试连接
- 在主机卡片上点击"测试连接"按钮
- 系统会尝试通过 SSH 连接到该主机
- 连接结果会显示在弹窗中
- 主机状态会自动更新(在线/离线)

### 6. 批量刷新状态
- 点击页面顶部的"刷新状态"按钮
- 系统会依次测试所有主机的连接状态
- 所有主机的状态会实时更新

### 7. 编辑主机
- 点击主机卡片上的"编辑"按钮
- 修改主机信息
- 密码字段留空表示不修改密码
- 点击"确定"保存

### 8. 删除主机
- 点击主机卡片上的"删除"按钮
- 确认删除操作
- 主机将从列表中移除

## 主机状态说明

- **在线 (ONLINE)**: SSH 连接测试成功
- **离线 (OFFLINE)**: SSH 连接测试失败
- **未知 (UNKNOWN)**: 尚未进行过连接测试

## 注意事项

1. **安全性**: 当前使用内存存储,重启后数据会丢失。生产环境建议集成数据库。
2. **密码加密**: 当前密码明文存储,生产环境应该加密存储。
3. **连接超时**: SSH 连接超时设置为 5 秒,可根据网络情况调整。
4. **并发测试**: 批量刷新状态时会依次测试,避免同时发起大量 SSH 连接。

## 后续优化建议

1. 集成 MySQL/PostgreSQL 数据库持久化存储
2. 实现密码加密存储(如 AES 加密)
3. 支持 SSH 密钥认证方式
4. 添加主机分组功能
5. 实现定时自动检测主机状态
6. 添加主机监控指标(CPU、内存、磁盘等)
7. 支持批量导入/导出主机配置
8. 添加操作日志记录
