# UsagiAgent 多智能体应用 —— 部署运行说明

UsagiAgent 是一个本地部署的多智能体对话应用：支持智能体管理、流式聊天、知识库问答（RAG）、外部对话导入（整理 / 接替任务）、PDF 知识导入（自动转 Markdown 并经大模型纠错）等功能。

- 前端：Vite + React + Ant Design（`ui/`）
- 后端：Spring Boot + Spring AI + MyBatis（`usagiAgent/`）
- 数据库：PostgreSQL 17 + pgvector（Docker 容器）

---

## 一、目录结构

```
项目根目录/
├── ui/                        # 前端（Vite + React + TypeScript）
│   └── src/                   # 组件、hooks、API 封装
├── usagiAgent/                # 后端（Spring Boot）
│   ├── src/main/java/com/buaa/usagi/
│   │   ├── agent/             # ReActAgent、ConversationParser、工具
│   │   ├── controller/        # REST 接口
│   │   ├── service/           # 业务逻辑（PDF 转换、Markdown 纠错、外部对话等）
│   │   ├── mapper/            # MyBatis 数据访问
│   │   └── model/             # 实体 / DTO / VO / 请求响应
│   └── src/main/resources/
│       ├── application.yaml   # 核心配置（数据库、模型 API Key）
│       ├── db/jchatmind.sql   # 建表脚本（首次部署执行）
│       └── mapper/            # MyBatis XML
└── README.md                  # 本文档
```

---

## 二、环境要求

| 组件 | 版本要求 | 说明 |
|---|---|---|
| JDK | 17+（推荐 21） | 后端编译运行 |
| Maven | 3.9+ | 后端构建 |
| Node.js | 20+（推荐 22） | 前端构建运行 |
| Docker | 20+ | 运行 PostgreSQL 数据库 |

---

## 三、数据库部署（Docker + PostgreSQL + pgvector）

### 1. 启动数据库容器

```bash
docker run -d --name usagi-postgres \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=123456 \
  -e POSTGRES_DB=usagiAgent \
  -p 5432:5432 \
  --restart unless-stopped \
  pgvector/pgvector:pg17
```

- 镜像 `pgvector/pgvector:pg17` 内置 pgvector 扩展（知识库向量检索必需）
- 用户名、密码、数据库名可按需修改，同时需要同步修改后端的 `application.yaml`
- 查看运行状态：`docker ps | grep usagi-postgres`

### 2. 建表（首次部署执行一次）

建表脚本已随项目提供：`usagiAgent/src/main/resources/db/jchatmind.sql`（含 vector 扩展与全部数据表）：

```bash
docker cp usagiAgent/src/main/resources/db/jchatmind.sql usagi-postgres:/tmp/jchatmind.sql
docker exec usagi-postgres psql -U postgres -d usagiAgent -f /tmp/jchatmind.sql
```

建表后共有 6 张表：`agent`、`chat_session`、`chat_message`、`knowledge_base`、`document`、`chunk_bge_m3`。

---

## 四、后端部署运行

### 1. 配置 API Key（必做）

编辑 `usagiAgent/src/main/resources/application.yaml`（仓库中不包含该文件，配置模板见同目录 `application.example.yaml`，复制一份改名为 `application.yaml` 后填写）：

```yaml
spring:
  ai:
    deepseek:
      api-key: 你的DeepSeek_API_Key   # 到 https://platform.deepseek.com 申请
    zhipuai:
      api-key: 你的智谱_API_Key       # 到 https://open.bigmodel.cn 申请
```

- **DeepSeek**：默认对话、外部对话整理/接替、PDF 纠错、B 站视频时间轴提炼使用
- **智谱（glm-4.6）**：创建智能体时可选的第二个模型
- 未配置 Key 时，界面管理功能可用，但所有调用大模型的环节会提示"API Key 未配置或无效"

### 1.1 配置 B 站视频分析（可选）

编辑 `usagiAgent/src/main/resources/application.yaml`：

```yaml
# B 站视频分析（可选配置）
bilibili:
  cookie: ""   # 填登录 B 站后的 Cookie（SESSDATA=xxx 一段即可），可解锁视频 AI 字幕
```

- 不配置时，仅能获取 up 主上传的 **CC 字幕** 与简介中的**时间戳目录**
- 配置后（推荐），可获取绝大多数视频的 **AI 字幕**，分析成功率大幅提升
- 获取 SESSDATA：浏览器登录 B 站 → F12 → Application/存储 → Cookies → 复制 `SESSDATA` 的值

### 2. 启动后端

```bash
cd usagiAgent
mvn spring-boot:run
```

- 启动成功标志：日志出现 `Tomcat started on port 8080`
- 健康检查：访问 `http://localhost:8080/api/agents` 应返回 `{"code":200,"message":"success",...}`
- 开发模式下 devtools 会自动监听代码变更并热重启

### 3. 打包（生产部署可选）

```bash
mvn clean package
java -jar target/usagi-0.0.1-SNAPSHOT.jar
```

---

## 五、前端部署运行

```bash
cd ui

# 首次运行安装依赖
npm install

# 开发模式（推荐，支持热更新）
npm run dev
```

- 访问地址：**http://localhost:5173**
- 后端地址默认 `http://localhost:8080/api`（配置在 `ui/src/api/http.ts`，部署时按需修改）

生产构建：

```bash
npm run build        # 产物输出到 ui/dist
npm run preview      # 本地预览构建产物
```

---

## 六、功能清单

| 功能 | 入口 | 说明 |
|---|---|---|
| 智能体管理 | 左侧「智能体助手」 | 创建/编辑智能体：名称、系统提示词、模型（deepseek-chat / glm-4.6）、工具、知识库、对话参数 |
| 聊天 | 聊天页 | SSE 流式回复，支持多轮记忆 |
| 知识库 | 左侧「知识库」 | 上传 **Markdown** 或 **PDF** 文档；PDF 自动转 Markdown → 大模型纠错 → 向量化入库 |
| 导入外部对话 | 左侧「导入外部对话」 | 三种来源：粘贴文本、上传 .txt/.md 文件、DeepSeek 分享链接；两种处理：整理对话（结构化摘要）、接替任务（导入会话继续推进） |
| B 站视频分析 | 聊天页（智能体勾选「B站视频分析」工具） | 给智能体发送 B 站视频链接，自动获取字幕/简介时间戳并生成**时间轴 + 重难点**（Markdown） |

---

## 七、主要 API

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

## 八、常见问题排查

| 现象 | 原因 | 解决 |
|---|---|---|
| 发消息提示 `chat_session` 外键约束错误（agent_id 不存在） | 页面持有刷新前的旧智能体 ID | 刷新页面，重新从数据库拉取智能体列表 |
| 聊天 / 导入对话 / PDF 纠错报「鉴权失败：API Key 未配置或无效」 | application.yaml 中 API Key 为占位符 | 填入真实 Key 后重启后端 |
| 上传 PDF 报「PDF 未提取到任何文本」 | 文件是扫描件/图片型 PDF（无文本层） | 需 OCR 工具转换，当前版本只支持含文本层的 PDF |
| 上传报「上传文件过大」 | 超过 50MB 限制 | 压缩文件或调整 `spring.servlet.multipart.max-file-size` |
| 前端页面打不开 | Vite dev server 未启动 | 执行 `npm run dev`，确认 5173 端口 |
| 后端报数据库连接失败 | Docker 容器未启动 | `docker start usagi-postgres` |
| B 站视频分析返回「没有可用的 CC 字幕，简介中也没有时间戳目录」 | 视频无 up 主上传字幕，且未配置 B 站 Cookie | 在 `application.yaml` 配置 `bilibili.cookie`（SESSDATA）后重启后端，即可获取 AI 字幕 |
| 修改后端代码后短暂无法访问 | devtools 热重启中 | 等待几秒自动恢复 |

---

## 九、测试

```bash
# 后端全部测试（含上下文加载、对话解析、PDF 转换）
cd usagiAgent
mvn test

# 前端类型检查 + 构建
cd ui
npm run build
```

---

## 十、当前版本已知限制

1. 仅接入云端 API 模型（DeepSeek、智谱），未支持 Ollama / vLLM 等本地模型
2. PDF 导入不支持扫描件（无 OCR）
3. 外部对话链接导入当前仅支持 DeepSeek 分享链接
4. 邮件通知为占位配置（`application.yaml` 中 `spring.mail`），不影响核心功能
5. B 站视频分析依赖 CC 字幕/AI 字幕（未配置 Cookie 时无字幕视频无法生成时间轴；无字幕且无简介时间戳的视频暂不支持，后续可接入语音转写兜底）
