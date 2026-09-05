<div align="center">

# 🐰 UsagiAgent

**多智能体对话系统 · 本地部署 · RAG 知识库 · B 站视频分析**

一个围绕个人学习场景构建的多智能体对话应用：为不同科目创建专属智能体，绑定自己的知识库，
以流式对话获得**严格限定在知识库范围内**的回答；还能导入与其他 AI 的对话进行整理或接替任务。

![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-6DB33F?logo=spring&logoColor=white)
![React](https://img.shields.io/badge/React-18-61DAFB?logo=react&logoColor=white)
![TypeScript](https://img.shields.io/badge/TypeScript-5-3178C6?logo=typescript&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-17-4169E1?logo=postgresql&logoColor=white)
![pgvector](https://img.shields.io/badge/pgvector-向量检索-316192?logo=postgresql&logoColor=white)
![DeepSeek](https://img.shields.io/badge/LLM-DeepSeek-4D6BFE?logo=openai&logoColor=white)

</div>

---

## ✨ 核心特性

| 特性 | 说明 |
|---|---|
| 🤖 **多智能体管理** | 每个智能体独立配置名称、系统提示词、模型、可用工具与绑定知识库 |
| 💬 **流式对话** | SSE 逐 token 推送，支持多轮记忆，回答限定在绑定知识库范围内 |
| 📚 **知识库 RAG** | 上传 Markdown / PDF 自动入库，向量检索 + 限定范围问答 |
| 📄 **PDF 转 Markdown** | PDF 自动转换 → **大模型纠错** → 按标题切片向量化 |
| 🔗 **外部对话导入** | 粘贴文本 / 上传文件 / DeepSeek 分享链接，支持**整理摘要**与**接替任务** |
| 🎬 **B 站视频分析** | 发送视频链接，自动获取字幕/简介时间戳，生成**时间轴 + 重难点** |
| 🐰 **Usagi 视觉** | 智能体头像按 id 确定性分配 Usagi 素材（新建即随机、刷新不变） |

---

## 📦 技术栈

| 层 | 技术 |
|---|---|
| 前端 | Vite · React · TypeScript · Ant Design |
| 后端 | Spring Boot · Spring AI · MyBatis |
| 数据库 | PostgreSQL 17 · pgvector（向量检索） |
| 模型 | DeepSeek（默认；兼容旧数据的 glm-4.6） |

---

## 🚀 快速开始

### 0. 环境要求

| 组件 | 版本 | 用途 |
|---|---|---|
| JDK | 17+（推荐 21） | 后端编译运行 |
| Maven | 3.9+ | 后端构建 |
| Node.js | 20+（推荐 22） | 前端构建运行 |
| Docker | 20+ | 运行 PostgreSQL 数据库 |

### 1. 启动数据库（Docker）

```bash
docker run -d --name usagi-postgres \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=123456 \
  -e POSTGRES_DB=usagiAgent \
  -p 5432:5432 \
  --restart unless-stopped \
  pgvector/pgvector:pg17
```

> 镜像 `pgvector/pgvector:pg17` 内置 pgvector 扩展（知识库向量检索必需）；用户名/密码/库名可按需修改，需同步修改后端 `application.yaml`。

### 2. 初始化建表（首次部署执行一次）

```bash
docker cp usagiAgent/src/main/resources/db/jchatmind.sql usagi-postgres:/tmp/jchatmind.sql
docker exec usagi-postgres psql -U postgres -d usagiAgent -f /tmp/jchatmind.sql
```

建表后共 6 张表：`agent`、`chat_session`、`chat_message`、`knowledge_base`、`document`、`chunk_bge_m3`。

### 3. 配置 API Key（必做）

仓库不包含 `application.yaml`（真实 Key 不进入版本库），配置模板见
`usagiAgent/src/main/resources/application.example.yaml`，复制一份改名为 `application.yaml` 后填写：

```bash
cp usagiAgent/src/main/resources/application.example.yaml usagiAgent/src/main/resources/application.yaml
```

```yaml
spring:
  ai:
    deepseek:
      api-key: 你的DeepSeek_API_Key   # 到 https://platform.deepseek.com 申请
    zhipuai:
      api-key: 你的智谱_API_Key       # 到 https://open.bigmodel.cn 申请
```

> ⚠️ **`application.yaml` 已被 .gitignore 排除，请勿强行提交，避免 API Key 泄露。**

### 4. 配置 B 站视频分析（可选）

```yaml
# application.yaml 末尾追加
bilibili:
  cookie: ""   # 填登录 B 站后的 Cookie（SESSDATA=xxx 一段即可），可解锁视频 AI 字幕
```

- 不配置：仅能获取 up 主上传的 **CC 字幕** 与简介中的**时间戳目录**
- 配置后（推荐）：可获取绝大多数视频的 **AI 字幕**，分析成功率大幅提升
- 获取 SESSDATA：浏览器登录 B 站 → F12 → Application/存储 → Cookies → 复制 `SESSDATA` 的值

### 5. 启动后端

```bash
cd usagiAgent
mvn spring-boot:run
```

- 启动成功标志：日志出现 `Tomcat started on port 8080`
- 健康检查：访问 `http://localhost:8080/api/agents` 应返回 `{"code":200,"message":"success",...}`
- 开发模式下 devtools 自动监听代码变更并热重启

### 6. 启动前端

```bash
cd ui
npm install        # 首次运行安装依赖
npm run dev        # 开发模式（支持热更新）
```

访问 **http://localhost:5173**。后端地址默认 `http://localhost:8080/api`（配置在 `ui/src/api/http.ts`，部署时按需修改）。

生产构建：

```bash
npm run build        # 产物输出到 ui/dist
npm run preview      # 本地预览构建产物
```

---

## 🧩 功能使用

| 功能 | 入口 | 使用方式 |
|---|---|---|
| 智能体管理 | 左侧「智能体助手」 | 创建/编辑智能体：名称、系统提示词、模型、工具、知识库、对话参数 |
| 聊天 | 聊天页 | 选择智能体 → 输入问题 → SSE 流式回答，可连续多轮 |
| 知识库 | 左侧「知识库」 | 新建知识库 → 上传 Markdown / PDF → 自动转 Markdown、纠错、向量化入库 |
| 导入外部对话 | 左侧「导入外部对话」 | 粘贴文本 / 上传文件 / DeepSeek 分享链接 → 选择「整理」或「接替任务」 |
| B 站视频分析 | 聊天页（智能体勾选「B站视频分析」工具） | 发送 B 站视频链接 → 返回时间轴 + 重难点 Markdown |

---

## 📁 项目结构

```
├── ui/                          # 前端（Vite + React + TypeScript）
│   └── src/                     # 组件、hooks、API 封装
├── usagiAgent/                  # 后端（Spring Boot）
│   ├── src/main/java/com/buaa/usagi/
│   │   ├── agent/               # ReActAgent、ConversationParser、工具（含 BiliSubtitleTool）
│   │   ├── controller/          # REST 接口与 SSE
│   │   ├── service/             # 业务逻辑（PDF 转换、纠错、RAG、外部对话、B 站视频分析）
│   │   ├── mapper/              # MyBatis 数据访问（含向量查询）
│   │   └── model/               # 实体 / DTO / 请求响应
│   └── src/main/resources/
│       ├── application.yaml     # 核心配置（数据库、模型 API Key，不入库）
│       ├── application.example.yaml  # 配置模板（占位符）
│       └── db/jchatmind.sql     # 建表脚本（首次部署执行）
└── README.md                    # 本文档
```

---

## 🔌 主要 API

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/agents` | 智能体列表 |
| POST | `/api/agents` | 创建智能体 |
| GET | `/api/knowledge-bases` | 知识库列表 |
| POST | `/api/documents/upload` | 上传文档（md/pdf，multipart，单文件 ≤ 50MB） |
| POST | `/api/chat-sessions` | 创建会话 |
| POST | `/api/chat-messages` | 发送消息 |
| GET | `/sse/connect/{chatSessionId}` | SSE 流式回复 |
| POST | `/api/external-chat/fetch` | 从分享链接抓取对话（当前支持 DeepSeek） |
| POST | `/api/external-chat/process` | 处理外部对话（SUMMARIZE 整理 / TAKEOVER 接替） |

---

## 🧪 测试

```bash
# 后端全部测试（含上下文加载、对话解析、PDF 转换）
cd usagiAgent
mvn test

# 前端类型检查 + 构建
cd ui
npm run build
```

---

## ❓ 常见问题排查

| 现象 | 原因 | 解决 |
|---|---|---|
| 发消息提示 `chat_session` 外键约束错误（agent_id 不存在） | 页面持有刷新前的旧智能体 ID | 刷新页面，重新从数据库拉取智能体列表 |
| 聊天 / 导入对话 / PDF 纠错报「鉴权失败：API Key 未配置或无效」 | `application.yaml` 中 API Key 为占位符 | 填入真实 Key 后重启后端 |
| 上传 PDF 报「PDF 未提取到任何文本」 | 文件是扫描件/图片型 PDF（无文本层） | 需 OCR 工具转换，当前版本只支持含文本层的 PDF |
| 上传报「上传文件过大」 | 超过 50MB 限制 | 压缩文件或调整 `spring.servlet.multipart.max-file-size` |
| 前端页面打不开 | Vite dev server 未启动 | 执行 `npm run dev`，确认 5173 端口 |
| 后端报数据库连接失败 | Docker 容器未启动 | `docker start usagi-postgres` |
| B 站视频分析返回「没有可用的 CC 字幕，简介中也没有时间戳目录」 | 视频无 up 主上传字幕，且未配置 B 站 Cookie | 配置 `bilibili.cookie`（SESSDATA）后重启后端 |
| 修改后端代码后短暂无法访问 | devtools 热重启中 | 等待几秒自动恢复 |

---

## ⚠️ 当前版本已知限制

1. 仅接入云端 API 模型（DeepSeek），未支持 Ollama / vLLM 等本地模型
2. PDF 导入不支持扫描件（无 OCR）
3. 外部对话链接导入当前仅支持 DeepSeek 分享链接
4. 邮件通知为占位配置（`spring.mail`），不影响核心功能
5. B 站视频分析依赖 CC 字幕/AI 字幕（未配置 Cookie 时无字幕视频无法生成时间轴；后续可接入语音转写兜底）

---

<div align="center">

**UsagiAgent** · 为学习构建的智能体工作台 🐰

</div>
