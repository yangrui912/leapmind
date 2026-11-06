# LeapMind 教育网站 —— 模块详细任务文档

> **文档目的**：为一个月的开发提供各模块的详细技术说明，包含需求分析、实现方案、子模块拆分（前后端）、接口契约、数据关系、验收标准。本文档不进行人员分工，只描述每个模块"要做什么、怎么做、和其他模块怎么配合"。
>
> **技术栈**：React + TypeScript（前端） · Spring Boot（主后端，业务逻辑与API网关） + Python FastAPI（AI与数据分析微服务） · MySQL + Redis（数据层） · 大模型API调用（AI能力，统一封装在Python层，Java通过内部HTTP调用）

---

## 目录

- [一、整体架构与模块总览](#一整体架构与模块总览)
- [二、共享数据层设计](#二共享数据层设计)
- [三、模块详情](#三模块详情)
  - [M1 做题板块](#m1-做题板块)
  - [M2 拍照答疑+讲题](#m2-拍照答疑讲题)
  - [M3 薄弱点列表](#m3-薄弱点列表)
  - [M4 即时讲课](#m4-即时讲课)
  - [M5 AI备课](#m5-ai备课)
  - [M6 上下文记忆（全局）](#m6-上下文记忆全局)
  - [M7 即时交互式问答（优化）](#m7-即时交互式问答优化)
  - [M8 虚拟AI教师（优化）](#m8-虚拟ai教师优化)
- [四、附录：接口契约汇总](#四附录接口契约汇总)

---

## 一、整体架构与模块总览

### 1.1 技术架构

```
┌─────────────────────────────────────────────────────────────┐
│                            前端                             │
│  做题页面 │ 讲题页面 │ 讲课页面 │ 备课页面 │ 答疑页面 │ ...    │
└──────────────────────────┬──────────────────────────────────┘
                           │ HTTP + SSE/WebSocket
┌──────────────────────────▼──────────────────────────────────┐
│                  Java Spring Boot (主后端)                    │
│                                                              │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────────┐   │
│  │ 做题服务 │ │ 讲题服务 │ │ 讲课服务 │ │ 交互问答服务 │   │
│  └──────────┘ └──────────┘ └──────────┘ └──────────────┘   │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────────┐   │
│  │ 备课管理 │ │ 弱点API  │ │ 事件采集 │ │ AI教师管理   │   │
│  └──────────┘ └──────────┘ └──────────┘ └──────────────┘   │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐   │
│  │      SSE/WebSocket流式基础设施 · 认证鉴权 · 限流熔断   │   │
│  └──────────────────────────────────────────────────────┘   │
└──────────────────────────┬──────────────────────────────────┘
                           │ 内部 HTTP (REST)
┌──────────────────────────▼──────────────────────────────────┐
│                Python FastAPI (AI与数据微服务)                │
│                                                              │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────────┐   │
│  │ AI调用层 │ │ 弱点分析 │ │ 用户画像 │ │ 文档解析     │   │
│  │(统一封装)│ │ 引擎     │ │ 引擎     │ │ (PDF/Word等) │   │
│  └──────────┘ └──────────┘ └──────────┘ └──────────────┘   │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────────┐   │
│  │ Prompt  │ │ 备课生成 │ │ 讲课生成 │ │ 情感/NLP    │   │
│  │ 工程    │ │ 引擎     │ │ 引擎     │ │ 分析        │   │
│  └──────────┘ └──────────┘ └──────────┘ └──────────────┘   │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐   │
│  │  大模型API调用(多模型) │ Prompt管理 │ 流式回调 │ 重试  │   │
│  └──────────────────────────────────────────────────────┘   │
└──────────────────────────┬──────────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────────────┐
│              MySQL (主数据库)  +  Redis (缓存/会话/排行)      │
└─────────────────────────────────────────────────────────────┘
```

### 1.1.1 Java 与 Python 技术分工

> **核心原则**：Java 负责业务逻辑、API网关、流式基础设施和事务管理；Python 负责AI调用、数据分析、文档解析和NLP任务。两者通过内部 HTTP REST 通信。

#### 为什么这样分工？

| 维度 | Java (Spring Boot) | Python (FastAPI) |
|------|-------------------|-----------------|
| **擅长领域** | REST API、事务管理、高并发、WebSocket/SSE基础设施 | 数据分析、AI/ML、NLP、文档解析、算法原型 |
| **典型场景** | CRUD业务、用户认证、排行榜、会话管理、限流熔断 | 薄弱点计算、用户画像、AI prompt工程、OCR后处理 |
| **生态优势** | MyBatis-Plus（ORM）、Spring WebFlux（SSE）、Micrometer（监控） | numpy/pandas（数据分析）、LangChain（AI编排）、PyPDF2/python-docx（文档解析） |
| **不适合做的事** | 复杂数据分析算法、AI prompt迭代调优、文档格式解析 | 高并发事务型CRUD、WebSocket长连接管理、细粒度权限 |

#### 模块技术分工速查表

| 模块 | Java 负责 | Python 负责 | 通信方式 |
|------|----------|------------|---------|
| **M1 做题** | 全部（题库CRUD、答题提交、积分排行、统计查询） | — | — |
| **M2 拍照答疑+讲题** | OCR图片接收、SSE流式推送、讲题内容管理、题库匹配 | OCR后处理去噪、AI答疑生成、AI讲题生成、Prompt构建 | Java → Python HTTP |
| **M3 薄弱点列表** | 薄弱点查询API、推荐练习API、改善追踪API | 薄弱度计算引擎、趋势分析、知识图谱算法、学习建议生成 | Python定时计算 → MySQL；Java读表提供API |
| **M4 即时讲课** | 文件接收上传、SSE流式推送、讲课内容CRUD、回放管理 | 文档解析(PDF/Word/PPT/图片)、AI讲课内容生成、PPT结构生成 | Java → Python HTTP |
| **M5 AI备课** | 备课内容CRUD、PPT模板管理、PPTX导出、风格配置管理 | AI备课内容生成、AI动态PPT编排、教学大纲生成 | Java → Python HTTP |
| **M6 上下文记忆** | 事件采集API、画像查询API、复习提醒API | 用户画像计算引擎、学习风格推断、遗忘曲线管理、画像摘要生成 | Python定时计算 → MySQL；Java读表提供API |
| **M7 交互式问答** | 全部（SSE/WebSocket基础设施、会话管理、限流熔断、监控日志、降级策略） | AI对话生成（被Java调用）、上下文摘要压缩 | Java → Python HTTP（仅AI生成部分） |
| **M8 虚拟AI教师** | TTS API编排、教师形象配置管理、音频缓存管理 | 文本情感分析、表情/动作序列生成、口型序列计算 | Java → Python HTTP（仅NLP分析部分） |

#### Java 与 Python 内部通信规范

```
Java Spring Boot                         Python FastAPI
     │                                        │
     │  POST /api/internal/ai/generate        │
     │──────────────────────────────────────→│
     │  {module, scene, prompt, params}       │
     │                                        │──→ 大模型API
     │                                        │←── 大模型响应
     │  SSE/JSON 响应（流式或非流式）           │
     │←──────────────────────────────────────│
     │                                        │
```

- Python FastAPI 运行在内网（`http://localhost:8001`），不对外暴露
- Java 通过 `PythonAIClient`（内部HTTP客户端）调用Python服务
- 流式场景：Python返回SSE，Java透传（或聚合后）给前端
- 非流式场景：Python返回JSON结果，Java进行业务处理后返回前端

### 1.2 模块总览与依赖关系

```
                    ┌──────────────────┐
                    │   M1 做题板块     │  ← 核心数据源
                    │  (新开发·最重要)   │
                    └───┬──────┬───────┘
                        │      │
              ┌─────────┘      └──────────┐
              ▼                           ▼
     ┌──────────────────┐       ┌──────────────────┐
     │ M2 拍照答疑+讲题  │       │  M3 薄弱点列表     │
     │    (新开发)       │       │    (新开发)        │
     └────────┬─────────┘       └────────┬─────────┘
              │                          │
              │         ┌────────────────┘
              │         │
              ▼         ▼
     ┌──────────────────┐       ┌──────────────────┐
     │  M4 即时讲课      │       │  M5 AI备课        │
     │    (新开发)       │◄──────│    (新开发)       │
     └────────┬─────────┘       └────────┬─────────┘
              │                          │
              └──────────┬───────────────┘
                         │
                         ▼
                ┌──────────────────┐
                │ M6 上下文记忆     │  ← 全局用户画像
                │    (新开发)       │
                └──────────────────┘

     ┌──────────────────────────────────────────────┐
     │  M7 即时交互式问答（优化）                      │
     │  服务于 M1/M2/M4/M5 所有实时问答场景            │
     └──────────────────────────────────────────────┘

     ┌──────────────────────────────────────────────┐
     │  M8 虚拟AI教师（优化）                          │
     │  服务于 M4 讲课、M7 问答时的形象展示             │
     └──────────────────────────────────────────────┘
```

### 1.3 数据流向核心逻辑

```
用户做题(M1) → 产生答题记录 → 错题自动归档(M1) → 讲题服务生成讲解(M2)
                                    ↓
                              答题数据分析 → 识别薄弱点(M3)
                                    ↓
                              更新用户画像(M6) → 优化AI回答
                                    ↓
                              生成针对性讲课(M4) + 备课(M5)
```

### 1.4 Python AI 微服务项目结构建议

```
python-ai-service/
├── main.py                          # FastAPI 应用入口
├── config.py                        # 配置管理（模型参数、API密钥等）
├── requirements.txt                 # 依赖：fastapi, uvicorn, openai, numpy, pandas 等
│
├── api/
│   ├── __init__.py
│   ├── ai_generate.py               # /api/internal/ai/generate（非流式）
│   ├── ai_generate_stream.py        # /api/internal/ai/generate/stream（流式SSE）
│   ├── ocr_process.py               # /api/internal/ai/ocr/process
│   ├── document_parse.py            # /api/internal/ai/parse-document
│   ├── weakness_analysis.py         # /api/internal/ai/analyze-weakness
│   ├── user_profile.py              # /api/internal/ai/build-profile
│   ├── lesson_prep.py               # /api/internal/ai/generate-lesson-prep
│   ├── lecture_generate.py          # /api/internal/ai/generate-lecture
│   └── sentiment_analysis.py        # /api/internal/ai/analyze-sentiment
│
├── services/
│   ├── __init__.py
│   ├── ai_client.py                 # 大模型API调用封装（多模型路由、重试、流式）
│   ├── prompt_manager.py            # Prompt模板管理与构建
│   ├── context_compressor.py        # 对话上下文压缩
│   ├── ocr_processor.py             # OCR后处理（去噪、结构化）
│   ├── document_parser.py           # 文档解析器（PDF/Word/PPT/图片）
│   ├── weakness_engine.py           # 薄弱点计算引擎
│   ├── profile_engine.py            # 用户画像计算引擎
│   ├── lesson_generator.py          # 备课内容生成器
│   ├── lecture_generator.py         # 讲课内容生成器
│   └── nlp_analyzer.py              # NLP分析（情感、关键词提取）
│
├── models/
│   ├── __init__.py
│   ├── schemas.py                   # Pydantic数据模型
│   └── db_models.py                 # 数据库模型（SQLAlchemy，只读MySQL）
│
└── utils/
    ├── __init__.py
    ├── db_helper.py                 # 数据库连接工具
    └── cache_helper.py              # Redis缓存工具
```

> **注意**：Python服务只做AI相关计算，不直接暴露给前端。所有前端请求通过Java → Java内部HTTP调用Python → Python返回结果 → Java处理并返回前端。

---

## 二、共享数据层设计

### 2.1 核心共享表

以下是多模块共用的核心数据库表。每个模块可能还有自己专有的表（在各模块章节详述）。

#### 用户相关

```sql
-- 用户基础表（已存在，需确认字段）
users: id, username, password_hash, nickname, avatar_url, grade, role, created_at, updated_at

-- 用户画像表（M6负责构建，M2/M3/M4/M5/M7消费）
user_profiles:
  id, user_id (FK→users),
  strengths_json TEXT,           -- 擅长知识点列表 JSON: [{"kp_id":1,"level":0.85},...]
  weakness_json TEXT,            -- 薄弱知识点列表 JSON
  learning_style VARCHAR(50),    -- 学习风格：visual/auditory/reading/kinesthetic
  confusion_history_json TEXT,   -- 历史困惑点记录 JSON
  avg_accuracy DECIMAL(5,2),     -- 平均正确率
  total_questions INT,           -- 总做题数
  updated_at DATETIME,
  UNIQUE KEY uk_user (user_id)
```

#### 题库相关

```sql
-- 题库表（M1主要读写，M2/M3/M4读取）
questions:
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  subject VARCHAR(50) NOT NULL,      -- 科目：math/chinese/english/physics/chemistry/biology
  grade VARCHAR(20) NOT NULL,        -- 年级：grade_7/grade_8/grade_9/grade_10/grade_11/grade_12
  chapter VARCHAR(100),              -- 章节
  type VARCHAR(30) NOT NULL,         -- 题型：single_choice/multi_choice/fill_blank/short_answer/essay
  difficulty TINYINT NOT NULL,       -- 难度 1-5
  content_json TEXT NOT NULL,        -- 题目内容JSON：{"stem":"...","options":["A...","B...",...],"images":["url1"]}
  answer_json TEXT NOT NULL,         -- 答案JSON：{"correct":"A","explanation":"...","steps":["步骤1","步骤2"]}
  source VARCHAR(50) DEFAULT 'manual', -- 来源：manual/ai_generated/imported
  status TINYINT DEFAULT 1,          -- 1:启用 0:禁用
  created_at DATETIME,
  updated_at DATETIME,
  INDEX idx_subject_grade (subject, grade),
  INDEX idx_difficulty (difficulty),
  INDEX idx_chapter (chapter)
```

```sql
-- 知识点表（题库标签、薄弱点分析共用）
knowledge_points:
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  subject VARCHAR(50) NOT NULL,
  grade VARCHAR(20) NOT NULL,
  name VARCHAR(200) NOT NULL,         -- 知识点名称，如"勾股定理"
  parent_id BIGINT DEFAULT NULL,      -- 父知识点ID（构建知识树）
  description TEXT,                   -- 知识点描述
  level TINYINT DEFAULT 1,            -- 层级：1/2/3（一级/二级/三级）
  INDEX idx_subject_grade (subject, grade),
  INDEX idx_parent (parent_id)
```

```sql
-- 题目-知识点关联表
question_kp_relations:
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  question_id BIGINT NOT NULL,
  kp_id BIGINT NOT NULL,
  weight DECIMAL(3,2) DEFAULT 1.00,   -- 该知识点在题中的权重
  UNIQUE KEY uk_q_kp (question_id, kp_id)
```

#### 答题记录相关（M1核心产出）

```sql
-- 答题记录表（M1写入，M3/M6读取分析）
user_answers:
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  question_id BIGINT NOT NULL,
  user_answer_json TEXT NOT NULL,      -- 用户提交的答案JSON
  is_correct TINYINT(1) NOT NULL,      -- 是否答对
  time_spent INT,                      -- 作答耗时（秒）
  attempt_count INT DEFAULT 1,         -- 第几次尝试
  source_scene VARCHAR(30),            -- 场景：free_practice/homework/wrong_redo/exam
  created_at DATETIME,
  INDEX idx_user_time (user_id, created_at DESC),
  INDEX idx_user_correct (user_id, is_correct),
  INDEX idx_question (question_id)
```

```sql
-- 错题本表（M1自动归档，M2读取）
wrong_question_book:
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  question_id BIGINT NOT NULL,
  answer_record_id BIGINT,             -- 关联的答题记录
  wrong_reason_tag VARCHAR(50),        -- 错误原因标签：concept_unclear/careless/formula_wrong/method_wrong
  is_key_focus TINYINT(1) DEFAULT 0,   -- 是否标记为重点复习
  review_count INT DEFAULT 0,          -- 复习次数
  last_review_at DATETIME,
  status VARCHAR(20) DEFAULT 'unresolved', -- unresolved/reviewing/resolved
  created_at DATETIME,
  UNIQUE KEY uk_user_question (user_id, question_id)
```

#### 讲课/备课内容相关

```sql
-- 教学内容表（M4讲课、M5备课共用）
teaching_contents:
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  type VARCHAR(30) NOT NULL,           -- lesson/lesson_plan/ppt/courseware
  title VARCHAR(500),
  source_type VARCHAR(30),             -- from_file/from_text/from_weakpoint/from_lesson_plan
  source_content_json TEXT,            -- 原始输入内容JSON（文本或文件信息）
  generated_content_json LONGTEXT,     -- AI生成的完整内容JSON
  ppt_structure_json LONGTEXT,         -- PPT结构JSON（幻灯片页列表）
  status VARCHAR(20) DEFAULT 'draft',  -- draft/published/archived
  created_at DATETIME,
  updated_at DATETIME,
  INDEX idx_user_type (user_id, type),
  INDEX idx_user_time (user_id, created_at DESC)
```

#### 对话相关

```sql
-- 对话会话表（M7管理）
conversation_sessions:
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  scene_type VARCHAR(30),              -- doing_exercise/explaining/teaching/qa
  context_json TEXT,                   -- 当前上下文快照JSON
  status VARCHAR(20) DEFAULT 'active', -- active/paused/ended
  created_at DATETIME,
  updated_at DATETIME

-- 对话消息表
conversation_messages:
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  session_id BIGINT NOT NULL,
  role VARCHAR(20) NOT NULL,           -- user/assistant/system
  content TEXT NOT NULL,
  message_type VARCHAR(30),            -- text/image/voice/action
  metadata_json TEXT,                  -- 附加信息JSON：token消耗、响应耗时等
  created_at DATETIME,
  INDEX idx_session_time (session_id, created_at)
```

#### 积分排行相关（M1游戏激励）

```sql
-- 用户积分表
user_points:
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  total_points INT DEFAULT 0,
  daily_points INT DEFAULT 0,          -- 当日积分（每日重置）
  streak_days INT DEFAULT 0,           -- 连续学习天数
  last_study_date DATE,
  UNIQUE KEY uk_user (user_id)

-- 积分明细表
point_records:
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  action_type VARCHAR(30),             -- answer_correct/daily_checkin/streak_bonus/challenge_win
  points INT,
  description VARCHAR(200),
  created_at DATETIME,
  INDEX idx_user_time (user_id, created_at DESC)
```

### 2.2 Redis 缓存规范

| Key Pattern | 用途 | TTL | 使用者 |
|---|---|---|---|
| `user:session:{sessionId}` | 用户会话状态 | 30min | M7 |
| `questions:list:{subject}:{grade}:{chapter}:{difficulty}` | 题库列表缓存 | 10min | M1 |
| `ranking:daily:{date}` | 每日排行榜 | 5min | M1 |
| `ranking:weekly:{week}` | 周排行榜 | 1h | M1 |
| `user:weakpoints:{userId}` | 用户薄弱点缓存 | 30min | M3 |
| `user:profile:{userId}` | 用户画像缓存 | 30min | M6 |
| `teaching:content:{contentId}` | 教学内容缓存 | 1h | M4/M5 |
| `ai:ratelimit:{userId}` | AI调用频率限制 | 1min | M7 |
| `tts:audio:{hash}` | TTS音频缓存 | 24h | M8 |

### 2.3 AI调用层统一规范（Java + Python 双层架构）

所有模块调用大模型都通过统一的AI服务体系，Java层负责编排调度，Python层负责实际的AI调用和Prompt工程：

#### Java层：AI调用客户端

```java
// Java端AI调用入口（Spring Boot中，内部调用Python FastAPI）
public interface AIService {
    // 非流式调用 → 内部调用 Python POST /api/internal/ai/generate
    AICallResult call(AICallRequest request);

    // 流式调用 → 内部调用 Python POST /api/internal/ai/generate/stream
    // 返回Flux，用于SSE推送给前端（透传或聚合Python的SSE响应）
    Flux<String> callStream(AICallRequest request);

    // 获取该次调用的token消耗
    TokenUsage getTokenUsage(String callId);
}

// 请求参数
class AICallRequest {
    String moduleName;          // 调用模块：exercise/explain/lesson/prep/qa
    String sceneType;           // 场景：answer_question/explain_wrong/generate_lesson/create_ppt
    String prompt;              // 完整prompt（Java层拼接好用户画像等上下文后传入）
    String modelName;           // 模型选择，默认从配置读取
    Integer maxTokens;          // 最大输出token
    Double temperature;         // 温度参数
    Map<String, Object> extra;  // 扩展参数
}
```

#### Python层：AI调用引擎（核心）

```python
# Python FastAPI 服务 —— 统一AI调用与Prompt管理中心
# 运行在 http://localhost:8001，仅内网访问

# POST /api/internal/ai/generate          — 非流式AI调用
# POST /api/internal/ai/generate/stream   — 流式AI调用(SSE)
# POST /api/internal/ai/ocr/process       — OCR后处理与文本结构化
# POST /api/internal/ai/parse-document    — 文档解析(PDF/Word/PPT/图片)
# POST /api/internal/ai/analyze-weakness  — 薄弱点分析计算
# POST /api/internal/ai/build-profile     — 用户画像构建
# POST /api/internal/ai/analyze-sentiment — 文本情感分析(表情/动作驱动)

class AIPromptManager:
    """Prompt管理器 —— 所有模块的Prompt模板集中管理"""
    
    def build_prompt(self, module: str, scene: str, context: dict) -> str:
        """根据模块和场景构建完整prompt，注入用户画像等上下文"""
        pass
    
    def compress_context(self, messages: list, max_tokens: int) -> list:
        """上下文压缩 —— 历史消息超限时AI自动摘要而非截断"""
        pass

class AIModelRouter:
    """模型路由器 —— 根据场景自动选择最合适的模型"""
    
    def route(self, scene: str) -> str:
        """数学题→推理模型，简单问答→快速模型，备课→长文本模型"""
        pass
```

#### 调用流程

```
前端 → Java(Spring Boot) → Python(FastAPI) → 大模型API
  ↑                          │                     │
  └── SSE/JSON响应 ──────────┴── 流式/非流式响应 ──┘
```

1. Java层接收前端请求，从M6获取用户画像 → 拼入context参数
2. Java通过内部HTTP调用Python服务，传入 (module, scene, context, params)
3. Python层进行Prompt构建（注入画像、学习风格等） → 调用大模型API
4. Python返回结果（流式SSE或JSON）给Java
5. Java进行业务后处理（如保存到数据库） → 返回给前端

---

## 三、模块详情

---

### M1 做题板块

#### 1. 模块概述

做题板块是 **整个平台最核心的数据生产模块**。用户在此完成各类练习，系统记录每一次答题行为（对错、耗时、知识点击中），这些数据是讲题(M2)、薄弱点分析(M3)、上下文记忆(M6)等下游模块的基础。

#### 2. 业务场景

| 场景 | 说明 | 触发入口 |
|------|------|---------|
| 随时做 | 用户自主选题练习 | 首页"开始做题"、题库浏览页 |
| 课后题 | 学完某个知识点的配套练习 | 讲课结束页"来做练习" |
| 错题重做 | 从错题本中选择历史错题重新作答 | 错题本页"重做"按钮 |
| 挑战模式 | 限时答题PK/排行榜 | 游戏激励入口 |

#### 3. 与其他模块的关系

```
M1(做题) ──→ 答题记录(user_answers表) ──→ M3(薄弱点分析) 读取分析
    │
    ├──→ 错题数据(wrong_question_book表) ──→ M2(讲题) 读取错题生成讲解
    │
    ├──→ 答题行为数据 ──→ M6(上下文记忆) 写入用户画像
    │
    ├──→ 积分数据(user_points表) ──→ 排行榜展示
    │
    └──→ 做错题时可触发 ──→ M7(即时交互) 追问"这题为什么错？"
```

**具体接口契约：**

M1提供给其他模块的数据通过 **共享数据库表** 获取（而非实时接口调用），原因是：
- 答题记录写入频繁，异步消费更合理
- M3薄弱点分析和M6用户画像更新不需要每次答题都实时触发，可定时批量计算

但M1提供以下查询接口供同步场景使用：

```
GET /api/practice/user-stats?userId={userId}
→ 返回：用户做题统计摘要
{
  "totalQuestions": 256,
  "correctRate": 0.72,
  "avgTimeSpent": 45,         // 秒
  "todayQuestions": 15,
  "todayCorrectRate": 0.8,
  "streakDays": 7
}
```

```
GET /api/wrong-questions?userId={userId}&status=unresolved&limit=20
→ 返回：待复习错题列表（M2讲题模块调用）
{
  "total": 35,
  "items": [{
    "id": 123,
    "questionId": 456,
    "questionContent": {...},     // 题目完整内容
    "userAnswer": {...},          // 用户当时的答案
    "correctAnswer": "...",
    "wrongReasonTag": "concept_unclear",
    "knowledgePoints": [          // 关联知识点
      {"id": 10, "name": "勾股定理", "weight": 1.0}
    ],
    "createdAt": "2026-07-05T10:30:00"
  }]
}
```

#### 4. 技术栈

| 层 | 技术 | 语言 |
|----|------|------|
| 前端 | React + TypeScript，状态管理 Zustand，UI组件 Ant Design，图表 ECharts | TypeScript |
| 后端 | Spring Boot，MyBatis-Plus，MySQL | **Java** |
| 缓存 | Redis（题库列表、排行榜） | — |
| AI | 题目知识点自动归类、难度校验 → 调用 Python AI 服务 | Java → Python |

> **为什么用Java？** 做题板块是典型的CRUD+事务密集型业务。Spring Boot + MyBatis-Plus 对数据库事务管理、多条件组合查询、缓存策略（Redis Sorted Set排行榜）有最成熟的生态支持。Python在此场景无优势。

#### 5. 子模块拆分

##### 5.1 后端

###### 5.1.1 题库管理与检索服务

- **功能**：题库的CRUD、多条件筛选查询、分页
- **核心逻辑**：
  - 支持按 科目 + 年级 + 章节 + 题型 + 难度 组合筛选
  - 支持关键词搜索题目内容
  - 题目按难度排序、随机打散（避免用户感知到难度梯度）
  - 支持批量导入题目（Excel/CSV）
- **涉及数据表**：`questions`, `knowledge_points`, `question_kp_relations`
- **接口**：

```
GET  /api/questions             查询题目列表（分页+筛选）
POST /api/questions             新增题目（管理端）
PUT  /api/questions/{id}        更新题目
DELETE /api/questions/{id}      删除题目
POST /api/questions/batch-import  批量导入题目
GET  /api/questions/{id}        获取单题详情
```

- **查询参数示例**：
```
GET /api/questions?subject=math&grade=grade_8&chapter=勾股定理&type=single_choice&difficulty=3&page=1&size=20
```

###### 5.1.2 练习模式与题目分发服务

- **功能**：根据练习场景（随时做/课后题/错题重做）生成题目序列
- **核心逻辑**：
  - **随时做**：用户选择科目+范围 → 按难度递增 + 随机编排返回题目列表
  - **课后题**：接收知识点ID列表 → 查询关联题目 → 按难度排序返回
  - **错题重做**：从 `wrong_question_book` 中获取用户错题 → 排除已解决的 → 返回
  - 题目去重：同一用户在短时间内（7天）不会重复遇到做对的题
- **涉及数据表**：`questions`, `question_kp_relations`, `wrong_question_book`, `user_answers`
- **接口**：

```
POST /api/practice/generate-session
→ 请求：
{
  "userId": 1001,
  "sceneType": "free_practice",       // free_practice / homework / wrong_redo
  "subject": "math",
  "grade": "grade_8",
  "knowledgePointIds": [10, 11],      // homework场景必传
  "questionCount": 10,
  "difficultyRange": {"min": 1, "max": 5}
}
→ 返回：
{
  "sessionId": "sess_abc123",
  "questions": [
    {
      "questionId": 456,
      "type": "single_choice",
      "difficulty": 2,
      "content": {
        "stem": "在直角三角形ABC中，∠C=90°，AC=3，BC=4，则AB=?",
        "options": ["A. 5", "B. 6", "C. 7", "D. 8"],
        "images": []
      },
      "knowledgePoints": [{"id": 10, "name": "勾股定理"}]
    },
    // ...更多题目
  ],
  "totalCount": 10
}
```

###### 5.1.3 答题提交与评判服务

- **功能**：接收用户答案，判断正确性，记录答题行为
- **核心逻辑**：
  - 对比用户答案与正确答案（不同题型比较逻辑不同）
  - 选择题：直接匹配选项
  - 填空题：模糊匹配 + 关键字匹配
  - 简答题：调用AI进行语义相似度判断（非精确匹配）
  - 客观题即时返回结果，主观题异步AI评判后通过通知更新
- **涉及数据表**：`user_answers`, `wrong_question_book`, `user_points`, `point_records`
- **接口**：

```
POST /api/practice/submit-answer
→ 请求：
{
  "userId": 1001,
  "sessionId": "sess_abc123",
  "questionId": 456,
  "answer": {"selected": "A"},        // 答案格式因题型而异
  "timeSpent": 35,                    // 作答耗时（秒）
  "attemptCount": 1
}
→ 返回：
{
  "recordId": 789,
  "isCorrect": true,
  "correctAnswer": {"selected": "A"},
  "explanation": "根据勾股定理，a²+b²=c²，3²+4²=25，所以c=5。故选A。",
  "knowledgePoints": [{"id": 10, "name": "勾股定理"}],
  "pointsEarned": 10,                 // 本次获得的积分
  "totalPoints": 1250,                // 用户总积分
  "nextQuestionId": 457               // 下一题ID（会话未结束时返回）
}
```

###### 5.1.4 错题本管理服务

- **功能**：自动归档错题、手动标记重点、错题状态管理、复习记录
- **核心逻辑**：
  - 答错时自动插入 `wrong_question_book` 表
  - 同一道题多次答对后自动标记为 `resolved`
  - 支持用户手动标记/取消"重点复习"
  - 记录每次复习时间，支持间隔重复提醒
- **涉及数据表**：`wrong_question_book`
- **接口**：

```
GET    /api/wrong-questions                   查询错题本列表（支持筛选）
PUT    /api/wrong-questions/{id}/focus        标记/取消重点复习
DELETE /api/wrong-questions/{id}              删除错题记录
POST   /api/wrong-questions/batch-redo       生成错题重做练习会话
```

###### 5.1.5 练习统计服务

- **功能**：统计用户的做题数据，生成图表数据给前端
- **核心逻辑**：
  - 正确率统计（总体/按科目/按知识点/按时间段）
  - 用时分析（平均用时趋势）
  - 错误分布（按知识点/按题型/按错误原因标签）
  - 每日做题量趋势
- **涉及数据表**：`user_answers`, `wrong_question_book`
- **接口**：

```
GET /api/practice/statistics?userId={userId}&period=week
→ 返回：
{
  "summary": {
    "totalQuestions": 256,
    "correctRate": 0.72,
    "avgTimeSpent": 45,
    "streakDays": 7
  },
  "dailyTrend": [
    {"date": "2026-07-02", "count": 20, "correctRate": 0.7},
    {"date": "2026-07-03", "count": 25, "correctRate": 0.76}
  ],
  "byKnowledgePoint": [
    {"kpId": 10, "kpName": "勾股定理", "total": 30, "correct": 21, "rate": 0.7},
    {"kpId": 11, "kpName": "相似三角形", "total": 20, "correct": 15, "rate": 0.75}
  ],
  "wrongReasons": [
    {"reason": "concept_unclear", "count": 15},
    {"reason": "careless", "count": 10}
  ]
}
```

###### 5.1.6 游戏激励与排行榜服务

- **功能**：积分获取、升级、排行榜
- **核心逻辑**：
  - 积分规则：答对+10，连续答对额外加分，每日签到+5，连续7天+30
  - 排行榜：日榜/周榜/月榜，按总积分或正确率排名
  - 使用Redis Sorted Set做排行榜，定时同步到MySQL
- **涉及数据表**：`user_points`, `point_records`
- **Redis**：`ranking:daily:{date}` (Sorted Set), `ranking:weekly:{week}` (Sorted Set)
- **接口**：

```
GET  /api/ranking/daily?limit=20            每日排行榜
GET  /api/ranking/weekly?limit=20           周排行榜
GET  /api/ranking/user-position?userId={}   用户在排行榜中的位置
GET  /api/points/history?userId={}&page=1   积分明细
POST /api/points/checkin                    每日签到
```

##### 5.2 前端

###### 5.2.1 题库浏览与筛选页

- **组件**：SubjectFilter, GradeFilter, ChapterTree, DifficultySlider, QuestionList, Pagination
- **交互**：
  - 左侧筛选面板（科目 → 年级 → 章节级联 → 题型多选 → 难度滑块）
  - 右侧题目列表（卡片或表格形式，显示题干摘要、难度、题型标签）
  - 点击题目可预览完整内容
  - 底部"开始练习"按钮，进入做题页
- **状态管理**：筛选条件、题目列表、总数、当前页

###### 5.2.2 做题页（核心页面）

- **组件**：QuestionCard, OptionList, ProgressBar, Timer, SubmitButton, HintButton, QuestionNav
- **交互**：
  - 顶部进度条（当前第X题/共Y题）
  - 题目卡片（题干 + 选项 + 图片）
  - 点击选项高亮，点击提交确认
  - 作答计时（可隐藏/显示）
  - 提交后弹出结果面板（对/错 ✓/✗ + 解析 + 知识点标签）
  - "下一题"按钮 / "去问AI"按钮（跳转讲题/答疑）
  - 侧边题目导航（可跳转未答题）
  - 全部完成后的练习结束页（统计摘要 + 错题入口 + "再练一组"）
- **关键状态**：
  - sessionId, currentIndex, answers[], questions[]
  - isSubmitted当前题, showResult当前题, timeSpent
- **注意**：页面刷新后应从session恢复状态

###### 5.2.3 错题本页

- **组件**：WrongQuestionList, WrongQuestionCard, FilterBar, BatchActions
- **交互**：
  - 错题列表（题目摘要、错误原因标签、错题时间、复习状态）
  - 筛选：按科目、知识点、状态（未解决/已解决）、时间范围
  - 单题操作：查看解析、重做、删除、标记重点
  - 批量操作：选中多题 → "批量重做"
  - 点击重做 → 跳到做题页，使用错题重做模式
- **状态管理**：错题列表、筛选条件、选中项

###### 5.2.4 练习统计页

- **组件**：StatSummaryCards, TrendChart, KpRadarChart, WrongReasonPieChart
- **交互**：
  - 顶部摘要卡片：总做题数、正确率、平均用时、连续天数
  - 趋势折线图：近7/30天做题量+正确率趋势
  - 知识点雷达图：各知识点掌握度
  - 饼图：错误原因分布
  - 时间切换：近一周/近一月/全部
- **图表库**：ECharts

###### 5.2.5 排行榜页

- **组件**：RankingTabs, RankingList, RankingCard, UserRankPosition
- **交互**：
  - Tab切换：日榜 / 周榜 / 月榜
  - 排行榜列表（排名、头像、昵称、积分）
  - 当前用户排名高亮展示
  - Top 3特殊样式（金/银/铜奖牌图标）

#### 6. 完成效果与验收标准

| 验收项 | 标准 |
|--------|------|
| 用户能完成完整做题流程 | 选题→答题→提交→看结果→下一题，全流程无阻断 |
| 错题自动归档 | 答错后错题本立即可见该题 |
| 错题重做 | 从错题本发起重做，能正常生成题目列表 |
| 统计图表正确 | 统计数据与实际答题记录一致 |
| 排行榜刷新 | 获得积分后排行榜在5分钟内反映变化 |
| 数据可追踪 | 每条答题记录落库，包含完整字段 |
| 做题中途刷新恢复 | 刷新页面后回到当前做题会话，进度不丢失 |

---

### M2 拍照答疑+讲题

#### 1. 模块概述

拍照答疑：用户拍摄/上传题目图片 → OCR识别 → AI解析题目 → 返回解答和讲解。
讲题：基于用户做错的题（来自M1错题本）或拍照上传的题，AI生成结构化的逐步讲解内容，帮助用户真正理解解题思路。

#### 2. 与其他模块的关系

```
M1(错题本) ──→ M2(讲题) 获取要讲解的题目
M2 ←──→ M7(即时交互) 讲题中追问走M7的对话链路
M2 ──→ M6(上下文记忆) 讲题中的困惑点写入用户画像
M2 ──→ M3(薄弱点) 讲题反馈的数据辅助薄弱点分析
M2 ──→ teaching_contents表 讲解内容保存供复用
```

**接口契约：**

M2查询M1错题本：
```
GET /api/wrong-questions?userId={userId}&status=unresolved&limit=50
（M1提供，M2调用，详见M1章节）
```

M2提供给M6的数据（通过共享表）：
- 讲题过程中用户的追问记录 → `conversation_messages` 表
- M6从中提取困惑点label，写入 `user_profiles.confusion_history_json`

#### 3. 技术栈

| 层 | 技术 | 语言 |
|----|------|------|
| 前端 | React + TypeScript，富文本/Markdown渲染（react-markdown + KaTeX数学公式），摄像头调用（MediaDevices API） | TypeScript |
| 后端(API层) | Spring Boot，图片接收上传与存储，SSE流式推送基础设施，讲题内容管理，题库匹配检索 | **Java** |
| 后端(AI/OCR层) | FastAPI，OCR文本后处理（去噪、题干/选项/公式结构识别），AI答疑与讲题Prompt构建，大模型调用与流式回调 | **Python** |
| 存储 | MySQL（讲解记录），MinIO/OSS（用户上传图片） | — |
| 实时交互 | SSE流式返回（Java接收Python的AI生成流 → 透传给前端） | — |

> **为什么混合？** 图片上传存储和SSE基础设施是Java强项。但OCR后处理（文本去噪、公式识别）和个性化AI讲解Prompt构建（"因为你XXX错了所以重点讲XXX"）在Python生态中更灵活——LangChain的Prompt模板管理、str.format_map的上下文注入都比Java字符串拼接更可靠。

#### 4. 子模块拆分

##### 4.1 后端

###### 4.1.1 拍照上传与OCR识别服务

- **功能**：接收图片，调用OCR API识别题目文本，返回结构化题目信息
- **核心逻辑**：
  - 图片上传 → 存储到MinIO/OSS → 返回图片URL
  - 调用OCR API识别图片中的文字（支持手写体和印刷体混合）
  - OCR结果后处理：去除噪声文字、识别题干/选项结构、识别数学公式
  - 如果OCR识别不清，可用AI进行二次纠错和补全
- **涉及数据表**：`ocr_records`（新建，记录OCR识别历史）
- **接口**：

```
POST /api/ocr/recognize-question
→ 请求（multipart/form-data）：
  userId: 1001
  image: [图片文件]
  subject: "math"                    // 可选，辅助OCR识别
→ 返回：
{
  "ocrRecordId": 501,
  "imageUrl": "https://oss.xxx.com/ocr/xxx.jpg",
  "recognizedText": "在直角三角形ABC中，∠C=90°，AC=3，BC=4，求AB的长。\nA. 5  B. 6  C. 7  D. 8",
  "structuredQuestion": {
    "stem": "在直角三角形ABC中，∠C=90°，AC=3，BC=4，求AB的长。",
    "options": ["5", "6", "7", "8"],
    "type": "single_choice",
    "subject": "math"
  },
  "confidence": 0.95                  // 识别置信度
}
```

###### 4.1.2 拍照答疑生成服务

- **功能**：根据OCR识别的题目，调用AI生成解答和讲解
- **核心逻辑**：
  - 构建Prompt：题目内容 + 科目 + 年级 + 用户知识水平（从M6用户画像获取）
  - 调用AI生成：答案 + 逐步解析 + 涉及的知识点 + 易错点提醒
  - 流式返回给前端（SSE），用户可边生成边看
  - 如果题库中有完全匹配的题，优先返回已有的解析（减少AI调用）
- **涉及数据表**：`questions`（匹配已有题目），`teaching_contents`（保存生成的解答）
- **接口**：

```
POST /api/explain/photo-qa
→ 请求：
{
  "userId": 1001,
  "ocrRecordId": 501,
  "question": {
    "stem": "在直角三角形ABC中...",
    "options": ["5", "6", "7", "8"],
    "subject": "math",
    "type": "single_choice"
  },
  "userProfile": {                   // 可选，从M6获取
    "grade": "grade_8",
    "weakPoints": [{"kpId": 10, "kpName": "勾股定理"}]
  }
}
→ 返回（SSE流式）：
data: {"type":"answer","content":"正确答案是A. 5\n\n"}
data: {"type":"step","content":"**第一步：识别题目类型**\n这..."}
data: {"type":"step","content":"**第二步：套用公式**\n..."}
data: {"type":"knowledge","content":"[{\"id\":10,\"name\":\"勾股定理\"}]"}
data: {"type":"done","contentId":999}
```

###### 4.1.3 讲题生成服务

- **功能**：基于错题（M1提供）生成结构化讲题内容
- **核心逻辑**：
  - 接收：题目内容 + 用户错误答案 + 错误原因标签 + 知识点
  - Prompt特别针对用户的错误原因进行讲解（不是泛泛讲题，而是"因为你XXX错了，所以重点讲XXX"）
  - 生成结构化讲题：①题目分析 ②解题步骤（分3-5步） ③每个步骤的详细解释 ④核心知识点梳理 ⑤易错点提醒 ⑥同类题推荐
  - 讲题内容持久化，支持回放
- **涉及数据表**：`teaching_contents`
- **接口**：

```
POST /api/explain/generate
→ 请求：
{
  "userId": 1001,
  "wrongQuestionId": 123,            // 来自M1错题本
  "userAnswer": {"selected": "B"},
  "correctAnswer": {"selected": "A"},
  "wrongReasonTag": "concept_unclear",
  "knowledgePoints": [{"id": 10, "name": "勾股定理"}]
}
→ 返回（SSE流式）：
data: {"type":"overview","content":"这道题考察的是**勾股定理**..."}
data: {"type":"step","stepNumber":1,"title":"审题分析","content":"..."}
data: {"type":"step","stepNumber":2,"title":"公式套用","content":"..."}
data: {"type":"step","stepNumber":3,"title":"计算求解","content":"..."}
data: {"type":"tip","content":"⚠️ 易错提醒：注意区分斜边和直角边，斜边一定是最长边"}
data: {"type":"similar","content":"[推荐同类题ID: 457, 458, 459]"}
data: {"type":"done","explainId":888}
```

###### 4.1.4 讲题回放与内容管理服务

- **功能**：历史讲解内容查询、回放、删除
- **涉及数据表**：`teaching_contents`
- **接口**：

```
GET    /api/explain/history?userId={userId}&page=1&size=20   讲解历史列表
GET    /api/explain/{explainId}                               获取单次讲解详情
DELETE /api/explain/{explainId}                               删除讲解记录
```

###### 4.1.5 题库匹配服务

- **功能**：OCR/拍照识别后，先在题库中查找是否有相同/类似题目，有则直接复用已有解析，减少AI调用
- **核心逻辑**：
  - 基于题目的文本特征（关键词、数字、公式结构）在题库中快速检索
  - 使用MySQL全文索引或简单的相似度计算
  - 匹配度 > 0.9 → 直接返回已有解析
  - 匹配度 0.6-0.9 → 返回候选列表，让用户确认
  - 匹配度 < 0.6 → 走AI生成
- **接口**：

```
POST /api/questions/match
→ 请求：
{
  "stem": "在直角三角形ABC中...",
  "subject": "math",
  "type": "single_choice"
}
→ 返回：
{
  "matched": true,
  "matchDegree": 0.95,
  "questionId": 456,
  "existingExplanation": {...}       // 已有解析内容
}
```

##### 4.2 前端

###### 4.2.1 拍照答疑页

- **组件**：CameraCapture, ImageUploader, OCRResultCard, QAResultPanel
- **交互**：
  - 拍照入口：调用设备摄像头拍照 or 从相册选择
  - 图片预览 + 裁剪区域选择
  - 上传 → loading → OCR识别结果展示
  - 如有匹配的题库题目，优先展示
  - AI解答流式输出（逐段显示，打字机效果）
  - 解答完成后，底部显示关联知识点和同类题推荐
  - "添加到错题本"按钮（如答错过类似题）
  - "继续追问"输入框（调用M7即时交互）
- **状态管理**：图片、OCR结果、流式内容、加载状态

###### 4.2.2 讲题页

- **组件**：ExplainStepPanel, StepProgress, KnowledgeTag, ExplainContent, VoicePlayButton, AskMoreButton
- **交互**：
  - 顶部：题目卡片（折叠显示原题）
  - 主体：逐步讲解内容，步骤条导航，点击步骤可跳转
  - 每步支持语音朗读（TTS，对接M8）
  - 知识点标签可点击跳转到知识图谱
  - 关键公式/概念高亮展示
  - 底部："继续追问"输入框、"同类题练习"按钮
  - 讲解完成后的反馈按钮（"懂了" / "还是不太懂"）→ 反馈给M6
- **状态管理**：讲解内容、当前步骤、语音播放状态、用户反馈

###### 4.2.3 讲题历史页

- **组件**：ExplainHistoryList, ExplainCard
- **交互**：
  - 讲题历史列表（题目、时间、知识点标签）
  - 点击进入讲题回放模式（复用讲题页组件，从历史数据渲染）

#### 5. 完成效果与验收标准

| 验收项 | 标准 |
|--------|------|
| 拍照识别准确率 | 印刷体题目识别率 > 95%，支持数学公式 |
| AI解答质量 | 解答步骤清晰、知识点标注正确、结论正确 |
| 流式讲题体验 | SSE流式返回，首字延迟 < 2秒 |
| 错题讲解个性化 | 讲解针对用户具体错误原因，而非通用答案 |
| 讲解可回放 | 历史讲解可重新查看，内容完整 |
| 题库匹配优先 | 相同题目命中后不重复调用AI |

---

### M3 薄弱点列表

#### 1. 模块概述

基于用户做题数据(M1)、讲题反馈(M2)、提问记录(M7)，自动分析识别用户的知识薄弱点，并以列表 + 知识图谱形式展示，同时提供针对性练习推荐。

#### 2. 与其他模块的关系

```
M1(答题记录) ──┐
M2(讲题反馈) ──┼──→ M3(薄弱点分析) ──→ user_weak_points表
M7(提问记录) ──┘         │
                          ├──→ M4(即时讲课) 接收薄弱点列表 → 生成针对性讲课
                          ├──→ M5(AI备课) 提供薄弱点数据 → 备课参考
                          ├──→ M6(上下文记忆) 薄弱点写入用户画像
                          └──→ M1(做题) 基于薄弱点推荐练习题目
```

**接口契约：**

M3 提供给M4/M5的查询接口：
```
GET /api/weak-points?userId={userId}&topN=10
→ 返回：
{
  "userId": 1001,
  "weakPoints": [
    {
      "kpId": 10,
      "kpName": "勾股定理",
      "weaknessScore": 0.72,        // 薄弱程度 0-1，越高越薄弱
      "errorCount": 12,              // 该知识点的错题数
      "totalAttempts": 30,           // 该知识点总答题数
      "errorRate": 0.4,              // 错误率
      "trend": "declining",          // 趋势：improving/stable/declining
      "lastErrorAt": "2026-07-07",
      "recommendedQuestions": [456, 457, 458],  // 推荐练习
      "subWeakPoints": [             // 子知识点薄弱点
        {"kpId": 101, "kpName": "勾股定理逆定理", "weaknessScore": 0.85}
      ]
    },
    // ...更多薄弱点，按weaknessScore降序
  ],
  "updatedAt": "2026-07-08T10:00:00",
  "overallAssessment": "几何模块整体偏弱，建议优先巩固勾股定理，再进入相似三角形学习"
}
```

M3 提供给M1的推荐练习接口：
```
GET /api/weak-points/recommend-questions?userId={userId}&kpId=10&count=10
→ 返回：
{
  "kpName": "勾股定理",
  "questions": [{...}, {...}]        // 推荐题目列表，从易到难
}
```

#### 3. 技术栈

| 层 | 技术 | 语言 |
|----|------|------|
| 前端 | React + TypeScript，ECharts知识图谱可视化（力导向图） | TypeScript |
| 后端(API层) | Spring Boot，薄弱点查询API、推荐练习API、改善追踪API（读MySQL提供REST接口） | **Java** |
| 后端(计算引擎) | FastAPI + numpy + pandas + scikit-learn，薄弱度加权计算、趋势分析（对比近7天vs前7天）、知识图谱构建（networkx）、学习建议生成（调用AI） | **Python** |
| 执行调度 | Spring Task / XXL-Job（定时触发Python计算任务）+ 事件驱动增量更新 | Java |
| AI | Python AI服务（薄弱点综合分析 + 个性化学习建议生成） | Python |

> **为什么Python核心？** 薄弱点分析本质是数据统计+算法：
> - `weaknessScore = w1×errorRate + w2×(1-recentCorrectRate) + w3×confusionFrequency` 这类加权公式用numpy/pandas一行代码解决
> - 趋势判断需要比较时间序列数据（近7天 vs 前7天），pandas的rolling和shift操作是为此设计的
> - 知识图谱构建（节点掌握度计算、前置/后置知识点关联）用networkx比Java手写图算法简洁得多
> - 如果用Java写同样的逻辑，代码量至少3-5倍，且难以调试和调优算法参数

#### 4. 子模块拆分

##### 4.1 后端

###### 4.1.1 薄弱点计算引擎

- **功能**：基于多源数据计算用户各知识点的薄弱程度（Python实现）
- **核心算法**（综合加权，numpy/pandas实现）：
  - `weaknessScore = w1 × errorRate + w2 × (1 - recentCorrectRate) + w3 × confusionFrequency`
  - 其中：
    - `errorRate`：该知识点历史错误率
    - `recentCorrectRate`：最近10次该知识点的正确率（更反映当前状态）
    - `confusionFrequency`：用户在提问中提及该知识点的频率（来自M7对话记录）
  - 趋势判断：比较近7天 vs 前7天的错误率变化
- **数据来源**：
  - `user_answers` 表（M1提供）
  - `wrong_question_book` 表（M1提供）
  - `conversation_messages` 表（M7提供，分析用户提问中涉及的知识点）
  - `user_profiles.confusion_history_json`（M6提供）
- **执行方式**：
  - 定时任务：每天晚上凌晨2点全量重算
  - 实时触发：用户完成一组练习后，增量更新相关知识点
- **涉及数据表**：读取 `user_answers`, `wrong_question_book`, `conversation_messages`；写入 `user_weak_points`

```sql
-- 薄弱点结果表
user_weak_points:
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  kp_id BIGINT NOT NULL,
  weakness_score DECIMAL(5,3),       -- 薄弱分数 0-1
  error_count INT,                    -- 错题数
  total_attempts INT,                 -- 总答题数
  error_rate DECIMAL(5,3),            -- 错误率
  confusion_count INT DEFAULT 0,      -- 提问困惑次数
  trend VARCHAR(20),                  -- improving/stable/declining
  last_error_at DATETIME,
  calculated_at DATETIME,
  UNIQUE KEY uk_user_kp (user_id, kp_id)
```

###### 4.1.2 薄弱点查询与展示服务

- **功能**：提供薄弱点列表、详情、知识图谱数据
- **接口**：

```
GET    /api/weak-points                         查询薄弱点列表
GET    /api/weak-points/{kpId}/detail           查看某个薄弱知识点详情（含关联题目、学习建议）
GET    /api/weak-points/knowledge-graph         获取知识图谱数据（薄弱点 + 关联关系）
```

###### 4.1.3 针对性练习推荐服务

- **功能**：根据薄弱点推荐适合的练习题目
- **核心逻辑**：
  - 从薄弱知识点关联的题目中，按难度从低到高选取
  - 排除用户7天内做过的题
  - 优先推荐用户之前做错但已标记"已解决"的题（验证是否真正掌握）
- **接口**：

```
GET  /api/weak-points/recommend-questions?userId={userId}&kpId={kpId}&count=10
POST /api/weak-points/generate-practice-plan   为薄弱点生成练习计划
→ 请求：{"userId": 1001, "kpIds": [10, 11]}
→ 返回：练习计划（题目序列 + 建议完成时间）
```

###### 4.1.4 薄弱点改善追踪服务

- **功能**：追踪薄弱点的改善情况，生成改善报告
- **接口**：

```
GET /api/weak-points/improvement-report?userId={userId}&period=month
→ 返回：
{
  "period": "2026-07",
  "overallImprovement": 0.15,         // 整体改善度（薄弱分下降比例）
  "improvedKps": [{"kpId": 10, "kpName": "勾股定理", "before": 0.72, "after": 0.45}],
  "worsenedKps": [],
  "suggestion": "过去一个月勾股定理有明显进步，建议开始攻克相似三角形"
}
```

##### 4.2 前端

###### 4.2.1 薄弱点列表页

- **组件**：WeakPointList, WeakPointCard, SortBar, FilterBar
- **交互**：
  - 薄弱点卡片列表（知识点名称、薄弱等级进度条、错题数、趋势箭头）
  - 排序：按薄弱程度 / 按最近错题时间
  - 筛选：按科目
  - 点击卡片展开子知识点
  - 点击"去练习"跳转做题页（携带知识点ID参数）

###### 4.2.2 薄弱点详情页

- **组件**：WeakPointDetail, ErrorQuestionPreview, PracticeButton, KpKnowledgeGraph
- **交互**：
  - 知识点薄弱情况概述
  - 错误题目列表（点击跳转讲题）
  - 趋势折线图（薄弱分变化曲线）
  - 关联知识图谱（展示该知识点与前置/后置知识点的关系）
  - "针对性练习"按钮 → 跳转做题页

###### 4.2.3 知识图谱页

- **组件**：KnowledgeGraphCanvas, KpNode, KpEdge
- **交互**：
  - 以用户薄弱知识点为中心的力导向图
  - 节点颜色表示掌握程度（绿=掌握、黄=一般、红=薄弱、灰=未学习）
  - 点击节点查看详情
  - 双击节点跳转"去练习"
- **图表库**：ECharts力导向图 或 自研Canvas图形

#### 5. 完成效果与验收标准

| 验收项 | 标准 |
|--------|------|
| 薄弱点识别准确 | Top5薄弱点与用户实际错题分布一致 |
| 实时/准实时更新 | 完成一组练习后5分钟内薄弱点数据更新 |
| 知识图谱正确 | 知识点关联关系、掌握度颜色正确 |
| 推荐练习相关性 | 推荐题目与薄弱知识点匹配度 > 90% |
| 改善追踪可用 | 可查看月度改善情况，数据前后可比 |

---

### M4 即时讲课

#### 1. 模块概述

用户上传知识点文件（PDF/Word/PPT/图片）或输入一段文字描述，AI自动解析内容并生成结构化的讲课内容（PPT/图文/文章），然后进入讲课演示流程。这是平台"AI教学"的核心体验模块。

#### 2. 与其他模块的关系

```
M1(用户知识水平) ──┐
M3(薄弱点列表)  ──┼──→ M4(即时讲课) 生成针对性讲课内容
M5(AI备课)      ──┘
         │
         ├──→ teaching_contents表 保存讲课内容
         ├──→ M7(即时交互) 讲课中的追问走M7链路
         ├──→ M8(虚拟AI教师) 讲课时调用虚拟教师形象+TTS
         └──→ M6(上下文记忆) 讲课中的互动数据写入用户画像
```

**接口契约：**

M4调用M3获取薄弱点：
```
GET /api/weak-points?userId={userId}&topN=5
（M3提供，返回用户Top5薄弱点，M4据此调整讲课重点）
```

M4调用M5获取备课内容：
```
GET /api/lesson-prep/contents?userId={userId}&status=published
（M5提供，返回已发布的备课内容，M4可直接转换讲课或复用PPT结构）
```

M4调用M8进行TTS讲课：
```
POST /api/virtual-teacher/lecture
→ 请求：
{
  "content": "今天我们学习勾股定理...",
  "voiceType": "male_teacher",       // 音色
  "speed": 1.0,                      // 语速
  "emotion": "enthusiastic"          // 情绪
}
→ 返回（SSE流式）：
data: {"type":"audio","chunk":"base64..."}
data: {"type":"mouth_shape","phoneme":"a","duration":200}
data: {"type":"expression","value":"explaining"}
```

#### 3. 技术栈

| 层 | 技术 | 语言 |
|----|------|------|
| 前端 | React + TypeScript，PPT渲染组件（自研slide组件或PPTX.js），文件上传 | TypeScript |
| 后端(API层) | Spring Boot，文件接收与存储、SSE流式推送、讲课内容CRUD、回放管理、PPT样式配置 | **Java** |
| 后端(解析/AI层) | FastAPI + PyPDF2 + python-docx + python-pptx + Pillow，文档解析（5种格式→结构化文本），AI讲课内容生成（大纲+逐页PPT结构），PPT结构JSON编排 | **Python** |
| 存储 | MySQL（讲课记录），MinIO/OSS（上传文件和生成的PPT/图片） | — |
| 实时 | SSE流式返回讲课生成内容（Python生成→Java透传→前端） | — |

> **为什么混合？** 文档解析是典型Python强项——PyPDF2解析PDF、python-docx解析Word、python-pptx解析PPT，Java的Apache POI/PDFBox在中文支持、表格提取、公式识别方面远不如Python生态。AI讲课内容生成涉及复杂的Prompt工程（"在薄弱知识点处放慢节奏，增加互动问题"），用Python的LangChain或直接Prompt模板管理比Java灵活得多。Java负责文件上传、SSE基础设施、CRUD管理这些它最擅长的事。

#### 4. 子模块拆分

##### 4.1 后端

###### 4.1.1 文件解析服务

- **功能**：解析用户上传的文件，提取文本和结构（Python微服务实现）
- **支持格式**：
  - PDF：提取文本、标题层级
  - Word (.docx)：提取文本、标题、表格
  - PPT (.pptx)：提取每页文本和结构
  - 图片：OCR识别文字内容
  - 纯文本：直接使用
- **核心逻辑**：
  - 文件上传 → 校验格式和大小（最大50MB） → 存储到MinIO/OSS
  - 根据文件类型选择解析器 → 提取结构化文本
  - 输出统一格式：`{title, sections: [{heading, content, level}]}`
- **接口**：

```
POST /api/lecture/parse-file
→ 请求（multipart/form-data）：
  userId: 1001
  file: [文件]
→ 返回：
{
  "fileId": "file_abc",
  "fileUrl": "https://oss.xxx.com/uploads/xxx.pdf",
  "parsedContent": {
    "title": "勾股定理",
    "sections": [
      {"heading": "一、勾股定理的定义", "level": 1, "content": "..."},
      {"heading": "二、勾股定理的证明", "level": 1, "content": "..."},
      {"heading": "2.1 面积法证明", "level": 2, "content": "..."}
    ],
    "estimatedDuration": 15         // 估算讲课时长（分钟）
  }
}
```

###### 4.1.2 讲课内容生成服务

- **功能**：根据解析后的内容，调用AI生成讲课内容
- **核心逻辑**：
  - Prompt构建 = 原始内容 + 用户知识水平（M1/M3/M6提供） + 讲课风格 + 页码控制
  - AI生成结构：每页包含标题、正文、配图建议、讲解提示、互动问题
  - 考虑用户薄弱点：在薄弱知识点处放慢节奏，增加示例和互动问题
  - 生成结果保存为结构化JSON（方便前端渲染为PPT样式）
- **PPT结构JSON格式**：

```json
{
  "title": "勾股定理",
  "totalPages": 12,
  "estimatedDuration": 15,
  "slides": [
    {
      "pageNum": 1,
      "type": "cover",
      "content": {"title": "勾股定理", "subtitle": "初中数学·八年级", "imageSuggestion": "直角三角形图"}
    },
    {
      "pageNum": 2,
      "type": "content",
      "content": {
        "title": "一、什么是勾股定理",
        "body": ["直角三角形两直角边的平方和等于斜边的平方", "公式：a² + b² = c²"],
        "formula": "a^2 + b^2 = c^2",
        "imageSuggestion": "标注了abc的直角三角形",
        "highlightPoints": ["c是斜边，是最长边", "a和b是直角边"]
      },
      "interaction": {"type": "question", "text": "如果一个直角三角形直角边分别是6和8，斜边是多少？"}
    }
    // ...更多页
  ],
  "knowledgePoints": [{"id": 10, "name": "勾股定理"}]
}
```

- **接口**：

```
POST /api/lecture/generate
→ 请求：
{
  "userId": 1001,
  "sourceType": "file",              // file / text / from_weakpoint / from_lesson_prep
  "sourceId": "file_abc",            // 文件ID或文本内容
  "textContent": "讲解勾股定理...",   // sourceType=text时使用
  "weakPointIds": [10],              // 可选，要重点讲的薄弱知识点
  "style": "interactive",            // 讲课风格：concise/detailed/interactive/storytelling
  "duration": 15,                    // 期望时长（分钟）
  "grade": "grade_8"
}
→ 返回（SSE流式）：
data: {"type":"outline","content":"已为你规划12页讲课内容，预计15分钟..."}
data: {"type":"slide","pageNum":1,"slide":{...}}
data: {"type":"slide","pageNum":2,"slide":{...}}
data: {"type":"done","lectureId":999,"totalPages":12}
```

###### 4.1.3 讲课内容管理服务

- **功能**：讲课内容的CRUD、状态管理
- **接口**：

```
GET    /api/lecture/contents                  讲课内容列表
GET    /api/lecture/contents/{lectureId}       获取讲课内容详情（含完整PPT结构）
PUT    /api/lecture/contents/{lectureId}       编辑讲课内容（修改文字、调序、增删页）
DELETE /api/lecture/contents/{lectureId}       删除讲课内容
POST   /api/lecture/contents/{lectureId}/publish  发布讲课内容（供其他人使用）
```

###### 4.1.4 讲课回放服务

- **功能**：保存讲课演示过程（每页停留时间、互动记录），支持回放
- **接口**：

```
POST /api/lecture/{lectureId}/record-progress   记录讲课进度（翻页、互动等）
GET  /api/lecture/{lectureId}/playback          获取讲课回放数据
```

##### 4.2 前端

###### 4.2.1 讲课创建页

- **组件**：UploadZone, TextInputArea, StyleSelector, DurationPicker, WeakPointSelector
- **交互**：
  - 上传文件区域（拖拽或点击，支持多格式）
  - 或文本输入框（直接输入你要讲的内容）
  - 讲课风格选择（简洁/详细/互动式/故事化）
  - 期望时长滑块（5/10/15/20/30分钟）
  - 关联薄弱点（自动选择Top5，可手动调整）
  - "开始生成"按钮 → 进入生成等待页

###### 4.2.2 讲课内容生成等待页

- **组件**：GenerationProgress, OutlinePreview, SlidePreviewList
- **交互**：
  - 生成进度指示（大纲 → 逐页生成）
  - 实时展示已生成的幻灯片缩略图
  - 全部生成完成后，"开始讲课"按钮

###### 4.2.3 讲课演示页（核心页面）

- **组件**：SlideRenderer, SlideNavigator, ProgressBar, AskButton, VoiceControl, TeacherAvatar
- **交互**：
  - 主体：当前幻灯片展示（支持标题、正文、公式、图片、高亮标注）
  - 翻页：点击/键盘左右键/手势滑动
  - 右侧或底部：页码导航缩略图
  - 顶部进度条（当前页/总页）
  - 底部工具栏：
    - 语音开关（TTS朗读当前页，对接M8）
    - AI教师形象（可选显示/隐藏，对接M8）
    - "提问"按钮（点击打开对话面板，对接M7）
    - 全屏模式
  - 互动题弹出（如第X页的提问，用户输入答案后AI给出反馈）
  - 讲课结束时展示总结页 + "做配套练习"按钮（跳转M1做题）

###### 4.2.4 讲课历史与内容管理页

- **组件**：LectureList, LectureCard, ActionMenu
- **交互**：
  - 讲课内容列表（标题、封面、时长、创建时间）
  - 操作：开始讲课、编辑内容、删除、分享

#### 5. 完成效果与验收标准

| 验收项 | 标准 |
|--------|------|
| 文件解析可用 | PDF/Word/PPT/图片/文本5种输入方式均可正常解析 |
| 生成内容质量 | 结构合理（有引入→讲解→示例→互动→总结），知识点准确 |
| 讲课流程完整 | 生成→预览→讲课演示→翻页→互动→结束，全流程无阻断 |
| 薄弱点关联 | 传入薄弱知识点时，相关内容页有明显侧重讲解 |
| PPT视觉效果 | 幻灯片展示排版清晰，公式正确渲染，章节导航可用 |
| 内容可复用 | 保存后可从历史中再次打开讲课 |

---

### M5 AI备课

#### 1. 模块概述

AI备课帮助用户（教师/学生/家长）快速生成教学大纲和PPT课件。分为三个层次：
- **基础功能**：AI生成教学展示内容 → 前端渲染为原始PPT
- **高级功能1**：AI动态生成PPT（根据知识点和教学目标自动编排PPT页）
- **高级功能2**：用户可自定义PPT风格（配色、字体、模板）

备课产出的内容可供即时讲课(M4)直接使用。

#### 2. 与其他模块的关系

```
M3(薄弱点) ──→ M5(AI备课) 根据薄弱点调整备课重点
          │
          ├──→ teaching_contents表 保存备课内容
          ├──→ M4(即时讲课) 提供备课内容、PPT模板
          └──→ M6(上下文记忆) 教学风格偏好写入用户画像
```

**接口契约：**

M5提供给M4的内容查询：
```
GET /api/lesson-prep/contents?userId={userId}&status=published&type=ppt
→ 返回：
{
  "total": 5,
  "items": [
    {
      "prepId": 301,
      "title": "一元二次方程",
      "type": "ppt",
      "subject": "math",
      "grade": "grade_9",
      "slideCount": 15,
      "styleTemplate": "default",
      "knowledgePoints": [{"id": 20, "name": "一元二次方程"}],
      "createdAt": "2026-07-05",
      "pptStructure": {...}          // 完整PPT结构JSON
    }
  ]
}
```

#### 3. 技术栈

| 层 | 技术 | 语言 |
|----|------|------|
| 前端 | React + TypeScript，PPT渲染组件，风格编辑器（颜色选择器、字体选择器、模板预览） | TypeScript |
| 后端(API层) | Spring Boot，备课内容CRUD、PPT模板管理、风格配置管理、PPTX导出（Apache POI） | **Java** |
| 后端(AI层) | FastAPI + LangChain（可选），AI备课内容生成（教学大纲→课时教案），AI动态PPT编排（智能决定每页内容量、图文布局、分页策略），教学风格适配 | **Python** |
| 导出 | Apache POI 生成真正PPTX文件下载（Java层） | Java |
| 存储 | MySQL（备课内容、模板），MinIO/OSS（导出的PPT文件） | — |

> **为什么Python核心？** AI备课是纯AI生成任务——输入知识点+教学目标→输出结构化教案+PPT。这涉及复杂的Prompt链（先出大纲→再生成每课时→再编排PPT页），Python的Prompt模板管理、链式调用、输出格式校验都比Java灵活。Java只做模板CRUD管理和最终的PPTX文件导出（Apache POI是Java生态最成熟的PPTX库，Python的python-pptx也可以但POI功能更全）。

#### 4. 子模块拆分

##### 4.1 后端

###### 4.1.1 AI备课内容生成服务

- **功能**：根据用户指定的知识点/章节/教学目标，生成结构化的备课内容
- **核心逻辑**：
  - 输入：知识点ID列表 + 教学目标 + 年级 + 课时数 + 风格偏好
  - AI生成：教学大纲 → 每课时的详细内容（教学目标、重难点、教学过程、板书设计、课后作业）
  - 考虑学生薄弱点（如有传入）
- **接口**：

```
POST /api/lesson-prep/generate
→ 请求：
{
  "userId": 1001,
  "title": "一元二次方程",
  "subject": "math",
  "grade": "grade_9",
  "knowledgePointIds": [20, 21, 22],
  "teachingGoals": ["理解一元二次方程的定义", "掌握求根公式", "能解决实际应用题"],
  "totalHours": 3,
  "weakPointIds": [20],              // 可选，学生薄弱知识点
  "style": "standard"                // standard/detailed/interactive
}
→ 返回（SSE流式）：
data: {"type":"outline","content":{"title":"一元二次方程·教案","totalHours":3,"sections":[...]}}
data: {"type":"section","index":1,"title":"第一课时：定义与标准形式","content":{...}}
data: {"type":"section","index":2,"title":"第二课时：求根公式","content":{...}}
data: {"type":"done","prepId":301}
```

###### 4.1.2 PPT生成服务（基础功能 + 高级功能1）

- **功能**：将备课内容自动编排成PPT幻灯片结构
- **核心逻辑**：
  - 基础功能：根据备课大纲生成固定的PPT模板结构（封面→教学目标→内容页×N→总结→课后练习）
  - 高级功能1：AI动态决定每页的内容量、图文布局、分页策略，而非机械地按章节切页
  - 每页幻灯片包含：标题、正文区、配图提示、公式区、互动问题
- **接口**：

```
POST /api/lesson-prep/generate-ppt
→ 请求：
{
  "prepId": 301,                     // 基于已有备课内容生成PPT
  "templateStyle": "default",        // PPT模板风格
  "maxSlides": 20
}
→ 返回：
{
  "pptId": 501,
  "slides": [{...}]                  // 同M4的PPT结构JSON格式
}
```

###### 4.1.3 PPT风格管理服务（高级功能2）

- **功能**：管理PPT风格模板，用户可自定义配色、字体、布局
- **涉及数据表**：`ppt_templates`（新建）

```sql
ppt_templates:
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT,                    -- NULL=系统默认模板
  name VARCHAR(100),
  config_json TEXT,                  -- 配色、字体、布局等配置JSON
  preview_image_url VARCHAR(500),
  is_system TINYINT(1) DEFAULT 0,
  created_at DATETIME
```

- **接口**：

```
GET    /api/lesson-prep/templates                   获取可用模板列表
POST   /api/lesson-prep/templates                   创建自定义模板
PUT    /api/lesson-prep/templates/{templateId}       更新模板
DELETE /api/lesson-prep/templates/{templateId}       删除自定义模板
POST   /api/lesson-prep/templates/{templateId}/apply 将模板应用到指定PPT
```

- **风格配置JSON示例**：

```json
{
  "colorScheme": {
    "primary": "#2563EB",
    "secondary": "#7C3AED",
    "background": "#FFFFFF",
    "text": "#1F2937",
    "accent": "#F59E0B"
  },
  "fontFamily": {
    "title": "PingFang SC",
    "body": "PingFang SC"
  },
  "layout": {
    "titleAlign": "left",
    "contentColumns": 2,
    "showPageNumber": true
  }
}
```

###### 4.1.4 备课内容管理服务

- **功能**：备课内容的CRUD、发布、导出
- **接口**：

```
GET    /api/lesson-prep/contents                    备课内容列表
GET    /api/lesson-prep/contents/{prepId}            获取备课详情
PUT    /api/lesson-prep/contents/{prepId}            更新备课内容
DELETE /api/lesson-prep/contents/{prepId}            删除备课内容
POST   /api/lesson-prep/contents/{prepId}/export-ppt 导出为PPTX文件下载
```

##### 4.2 前端

###### 4.2.1 备课创建页

- **组件**：KnowledgePointSelector, GoalEditor, HoursPlanner, StyleSelector
- **交互**：
  - 输入标题 + 选择科目 + 年级
  - 知识点选择器（从知识树中选择要讲的知识点）
  - 教学目标编辑器（文本列表，支持AI自动生成）
  - 课时数设置
  - 薄弱点导入（从M3获取，可选）
  - "生成备课内容"按钮

###### 4.2.2 备课内容编辑页

- **组件**：OutlineEditor, SectionEditor, RichTextEditor
- **交互**：
  - 左侧大纲树（课时 → 教学环节）
  - 右侧内容编辑器（富文本，支持修改AI生成的内容）
  - 每个环节可展开编辑：教学目标、重难点、教学过程、板书、课后作业
  - "生成PPT"按钮

###### 4.2.3 PPT预览与编辑页

- **组件**：SlideThumbnailList, SlideCanvas, StylePanel
- **交互**：
  - 左侧：幻灯片缩略图列表（可拖拽调整顺序、增删页）
  - 中间：当前幻灯片Canvas预览
  - 右侧：样式面板（模板选择、颜色、字体配置）
  - 点击缩略图切换当前编辑页
  - "保存" / "导出PPTX" / "开始讲课"（跳转M4）按钮

###### 4.2.4 备课内容列表页

- **组件**：PrepContentList, PrepContentCard
- **交互**：
  - 备课内容卡片列表（标题、科目、课时数、PPT页数、创建时间）
  - 操作：编辑、预览PPT、导出、删除、"用于讲课"（跳转M4）

#### 5. 完成效果与验收标准

| 验收项 | 标准 |
|--------|------|
| 备课生成可用 | 输入知识点 → 生成完整教案（含目标、过程、作业） |
| PPT生成可用 | 备课内容 → 可用的PPT结构（≥8页） |
| PPT可编辑 | 用户可增删改幻灯片页、调整顺序 |
| 风格自定义 | 用户可自定义配色和字体，预览生效 |
| 导出功能 | 可导出为标准PPTX文件 |
| 与M4联动 | 备课PPT可在即时讲课中直接使用 |

---

### M6 上下文记忆（全局）

#### 1. 模块概述

构建全局的"用户画像"系统，记录用户在各模块中的行为特征——擅长什么、困惑什么、学习风格偏好、遗忘曲线等。这些画像数据在每次AI交互（讲题、讲课、答疑、备课）时作为上下文注入Prompt，让AI的回答更个性化。

#### 2. 与其他模块的关系

```
M1(答题行为) ──┐
M2(讲题反馈) ──┤
M3(薄弱点)   ──┼──→ M6(用户画像) ──→ user_profiles表
M4(讲课互动) ──┤
M7(对话记录) ──┘
                    │
                    └──→ 提供给 M2/M4/M5/M7 作为AI调用的上下文参数
```

**接口契约：**

M6提供给所有AI调用场景的用户画像获取接口：

```
GET /api/user-profile/{userId}
→ 返回：
{
  "userId": 1001,
  "grade": "grade_8",
  "strengths": [
    {"kpId": 15, "kpName": "一元一次方程", "masteryLevel": 0.9},
    {"kpId": 18, "kpName": "不等式", "masteryLevel": 0.85}
  ],
  "weaknesses": [
    {"kpId": 10, "kpName": "勾股定理", "masteryLevel": 0.35},
    {"kpId": 11, "kpName": "相似三角形", "masteryLevel": 0.4}
  ],
  "learningStyle": "visual",
  "confusionTopics": [               // 最近困惑的话题
    {"kpId": 10, "kpName": "勾股定理", "detail": "分不清斜边和直角边", "frequency": 3}
  ],
  "summaryProfile": "该学生代数能力强，几何偏弱。视觉型学习者，喜欢图表和图示讲解。做题速度快但容易粗心。",
  "preferredExplanationStyle": "step_by_step",
  "recentFocusAreas": ["勾股定理", "二次根式"],
  "learningPace": "moderate",         // slow/moderate/fast
  "lastUpdated": "2026-07-08T02:00:00"
}
```

M6接收各模块的数据写入：
```
POST /api/user-profile/{userId}/record-event
→ 请求：
{
  "userId": 1001,
  "eventType": "answer_question",     // answer_question / ask_doubt / feedback / lecture_interact
  "sourceModule": "M1",               // M1/M2/M4/M5/M7
  "data": {
    "kpId": 10,                       // 相关知识点
    "isCorrect": false,               // 事件相关数据
    "timeSpent": 45,
    "confusionTag": "concept_unclear"
  }
}
→ 返回：{"acknowledged": true}
```

#### 3. 怎么完成（建议）

这部分建议按”先能用、再优化”的方式推进，MVP优先做 3 件事：

1. **先把用户行为事件收上来（Java实现）**  
   先从 M1（答题）、M2（讲题反馈）、M4（讲课互动）、M7（对话记录）四个入口采集数据。每次事件至少记录：用户ID、知识点ID、行为类型、是否正确、耗时、时间戳。使用Java的事件采集API（`POST /api/user-profile/{userId}/record-event`）高性能写入MySQL。

2. **先做一个简化版用户画像（Python实现）**  
   不要一开始做太复杂的心理模型，先建立这几个核心字段即可（Python定时任务每天凌晨计算）：
   - 擅长知识点：pandas筛选 `(correctRate > 0.8) & (totalAttempts > 10)`
   - 困惑知识点：`weaknessScore = w1×errorRate + w2×confusionFrequency`
   - 学习风格：基于行为特征向量→简单规则推断 visual/auditory/reading/kinesthetic
   - 学习节奏：按答题速度分位数分档 fast/moderate/slow
   - 最近关注点：最近7天高频出现的前3个知识点

3. **把画像注入给 AI 调用（Java+Python协作）**  
   Java层在调用AI前先查用户画像（`GET /api/user-profile/{userId}/summary`），作为context参数传给Python AI服务。Python层在Prompt构建时将画像注入——这样系统就能做到”同一道题，不同学生讲法不同”。

#### 4. 技术栈

| 层 | 技术 | 语言 |
|----|------|------|
| 前端 | React + TypeScript，学习档案展示页（雷达图、知识树、时间线） | TypeScript |
| 后端(API层) | Spring Boot，事件采集API、画像查询API、复习提醒API、遗忘曲线调度 | **Java** |
| 后端(画像引擎) | FastAPI + numpy + scikit-learn，多源数据汇总、擅长/薄弱点识别（正确率阈值判定）、学习风格推断（行为聚类）、困惑点提取（对话记录NLP分析）、画像摘要生成（调用AI） | **Python** |
| 缓存 | Redis（用户画像缓存 `user:profile:{userId}`，TTL 30min） | — |
| AI | Python AI服务（画像自然语言摘要生成 + 个性化学习建议） | Python |

> **为什么Python核心？** 用户画像本质是推荐系统+用户建模：
> - 擅长点识别：多维度阈值判定（正确率>80% AND 答题数>10 AND 近期趋势=improving），用pandas的条件筛选一行代码
> - 困惑点提取：从对话记录中NLP分析（"为什么""不懂""什么意思"等模式匹配），Python的re+nltk/jieba比Java更自然
> - 学习风格推断：4维特征向量→行为聚类→visual/auditory/reading/kinesthetic分类，scikit-learn的KMeans现成可用
> - 遗忘曲线：间隔重复算法（1天/3天/7天/30天），用Python的datetime+timedelta计算远比Java的Calendar简洁
>
> Java只负责事件采集API（高性能写入）和对外查询接口，画像计算全在Python完成。

#### 5. 子模块拆分

##### 4.1 后端

###### 4.1.1 用户画像计算引擎

- **功能**：汇总多源数据，构建和更新用户画像
- **核心逻辑**：
  - 数据收集：读取 `user_answers`（M1）、`wrong_question_book`（M1）、`conversation_messages`（M7）、`user_weak_points`（M3）
  - 擅长点识别：正确率 > 80% 且答题数 > 10 → 判定为掌握
  - 困惑点识别：从对话记录中提取"为什么""不懂""什么意思"等模式 → 标记困惑topic
  - 学习风格推断：从用户行为推断（偏好看图片 → visual / 偏好听讲 → auditory / 偏好看文字 → reading）
  - 画像摘要生成：调用AI对以上数据生成一段自然语言描述
- **执行方式**：每天凌晨全量更新 + 事件驱动增量更新
- **涉及数据表**：写入 `user_profiles`

###### 4.1.2 事件采集服务

- **功能**：接收各模块上报的用户行为事件，存入事件日志
- **涉及数据表**：`user_events`（新建）

```sql
user_events:
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  event_type VARCHAR(50) NOT NULL,
  source_module VARCHAR(10),
  event_data_json TEXT,              -- 事件详情JSON
  created_at DATETIME,
  INDEX idx_user_time (user_id, created_at DESC)
```

- **接口**：

```
POST /api/user-profile/{userId}/record-event   记录事件（见上文）
POST /api/user-profile/{userId}/batch-events   批量记录事件
```

###### 4.1.3 画像查询服务

- **功能**：提供给其他模块查询用户画像
- **接口**：

```
GET  /api/user-profile/{userId}                  获取完整用户画像
GET  /api/user-profile/{userId}/summary           获取画像摘要（轻量，适合注入Prompt）
GET  /api/user-profile/{userId}/knowledge-status  获取知识掌握状态（知识点→掌握度映射）
```

###### 4.1.4 遗忘曲线管理服务

- **功能**：基于艾宾浩斯遗忘曲线，记录用户每个知识点的复习时机
- **核心逻辑**：
  - 用户学习/复习某个知识点后，按间隔（1天/3天/7天/30天）计算下次复习时间
  - 到期提醒用户复习
- **涉及数据表**：`review_schedules`（新建）

```sql
review_schedules:
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  kp_id BIGINT NOT NULL,
  last_review_at DATETIME,
  next_review_at DATETIME,
  review_stage TINYINT,              -- 当前复习阶段：1/2/3/4/5
  created_at DATETIME
```

- **接口**：

```
GET  /api/user-profile/{userId}/review-reminders     获取待复习提醒
POST /api/user-profile/{userId}/mark-reviewed        标记已复习
```

##### 4.2 前端

###### 4.2.1 用户学习档案页

- **组件**：ProfileSummary, KnowledgeRadar, StrengthsList, WeaknessesList, LearningTimeline, ReviewReminderCard
- **交互**：
  - 用户画像摘要（自然语言描述）
  - 知识掌握雷达图（按学科展示各模块掌握度）
  - 擅长/薄弱知识点标签云
  - 学习历史时间线（关键事件：完成练习、完成讲课、提问等）
  - 待复习提醒卡片（点击跳转做题/讲题）

###### 4.2.2 知识点掌握度详情页

- **组件**：KnowledgeTree, MasteryProgress, ReviewSchedule
- **交互**：
  - 以知识树形式展示所有知识点
  - 每个节点显示掌握度进度条
  - 点击查看详细复习计划和历史

#### 5. 完成效果与验收标准

| 验收项 | 标准 |
|--------|------|
| 用户画像可读 | 画像摘要自然语言描述准确反映用户实际状态 |
| 画像数据可注入AI | 各模块AI调用时能获取到用户画像并产生差异化回答 |
| 事件采集完整 | M1/M2/M4/M5/M7的关键事件均能被M6采集 |
| 实时性 | 关键事件（答题/提问）发生后5分钟内画像更新 |
| 遗忘提醒 | 到达复习时间点时用户可见提醒 |

---

### M7 即时交互式问答（优化）

#### 1. 模块概述

已有旧链路的即时对话功能，本阶段重点是：
1. **性能优化**：降低延迟、提升并发、减少无效请求
2. **体验优化**：流式响应、打断、断线恢复
3. **新链路开拓**：WebSocket替代HTTP轮询、支持多模态输入（语音、图片）
4. **监控体系**：性能日志、失败追踪、降级策略

#### 2. 与其他模块的关系

```
M7是横向服务层，被多个模块集成调用：
  M1(做题) → "这题为什么选B？"
  M2(讲题) → 讲题过程中的追问
  M4(讲课) → 讲课中的互动提问
  M5(备课) → 备课过程中的咨询

M7 ──→ conversation_messages 表 → M3/M6消费对话数据进行薄弱点和画像分析
```

**接口契约：**

统一对话接口（各模块通过此接口发起对话）：
```
POST /api/conversation/ask
→ 请求：
{
  "userId": 1001,
  "sessionId": "sess_abc",           // 可选，无则新建会话
  "question": "为什么不是选B？",
  "sceneType": "explaining",         // doing_exercise / explaining / teaching / lesson_prep / general_qa
  "context": {                       // 场景上下文
    "questionId": 456,               // 如果在做题场景
    "currentSlide": 3,               // 如果在讲课场景
    "relatedKpId": 10
  },
  "inputType": "text",               // text / voice / image
  "attachmentUrls": []               // 图片或语音附件URL
}
→ 返回（SSE流式，核心链路）：
event: message
data: {"type":"thinking","content":""}

event: message
data: {"type":"content","chunk":"这道","index":0}
data: {"type":"content","chunk":"题考","index":1}
data: {"type":"content","chunk":"察的","index":2}

event: interrupt
data: {"type":"interrupted","message":"用户已打断"}

event: message
data: {"type":"done","callId":"call_xyz","tokenUsage":{"input":150,"output":80}}
```

#### 3. 技术栈

| 层 | 技术 | 语言 |
|----|------|------|
| 前端 | React + TypeScript，EventSource（SSE）或 WebSocket 客户端 | TypeScript |
| 后端(基础设施) | Spring Boot，Spring WebFlux（SSE流式响应），WebSocket双向通道，Redis Pub/Sub（会话状态），会话管理，限流熔断（Resilience4j），失败重试与降级 | **Java** |
| 后端(AI生成) | 实际AI对话生成调用 Python AI服务（`POST /api/internal/ai/generate/stream`），Java层负责接收流式结果并透传给前端 | Java → Python |
| 监控 | Micrometer + Prometheus（性能指标），ai_call_logs表（每次调用的延迟、token、成功率） | Java |
| 降级 | Redis缓存常见问题答案（按question hash做key），兜底模板化回答 | Java |

> **为什么Java主导？** M7的核心不是AI生成本身，而是围绕AI对话的**基础设施**：
> - Spring WebFlux的`Flux<ServerSentEvent>`是业界最成熟的SSE实现之一
> - WebSocket连接管理（心跳、断线重连、多节点同步）Java生态更可靠
> - 限流熔断（Resilience4j/Sentinel）、性能监控（Micrometer→Prometheus）是Spring Boot的看家本领
> - 会话状态管理（Redis Pub/Sub多节点同步）Spring Session生态成熟
>
> AI生成部分仍调用Python服务（Prompt构建+大模型调用），但会话管理、流式分发、降级策略这些"壳"全在Java。

#### 4. 子模块拆分

##### 4.1 后端

###### 4.1.1 对话请求优化服务

- **功能**：优化对话请求链路，减少无效调用
- **优化点**：
  - **请求合并**：同一会话短时间内（200ms内）重复问题不发两次AI请求
  - **上下文压缩**：历史消息超过限制时，AI自动摘要而非截断（保留语义）
  - **相似问题缓存**：Redis缓存高频问题的答案（按question文本hash做key，TTL 1h）
  - **预加载**：在做题/讲课场景中，预判用户可能问的问题提前生成（如知识点解释）
- **接口优化**：同上统一对话接口

###### 4.1.2 流式响应与打断服务

- **功能**：支持SSE流式输出 + 用户打断生成
- **核心逻辑**：
  - 使用Spring WebFlux的 `Flux<ServerSentEvent>` 实现SSE
  - 打断机制：前端发 `POST /api/conversation/interrupt` → 后端取消当前AI调用（Mono.cancel）→ 发送interrupt事件
  - 打断后的上下文保留，用户可以继续追问
- **接口**：

```
POST /api/conversation/ask          对话请求（SSE流式返回，见上文）
POST /api/conversation/interrupt?sessionId={sessionId}  打断当前生成
```

###### 4.1.3 会话状态管理服务

- **功能**：管理会话的创建、上下文存储、状态恢复
- **核心逻辑**：
  - 每个会话存储在Redis中（key: `user:session:{sessionId}`），含最新20条消息的摘要
  - 会话超时30分钟自动结束（可配置）
  - 页面刷新/网络中断后，通过sessionId恢复上下文
- **涉及数据表**：`conversation_sessions`, `conversation_messages`
- **接口**：

```
POST /api/conversation/sessions                      创建新会话
GET   /api/conversation/sessions?userId={userId}      获取用户会话列表
GET   /api/conversation/sessions/{sessionId}          获取会话详情（含历史消息）
DELETE /api/conversation/sessions/{sessionId}         删除会话
```

###### 4.1.4 性能监控与日志服务

- **功能**：记录每次AI调用的性能指标，提供监控面板数据
- **监控指标**：
  - 请求量（QPS）、平均响应延迟（P50/P95/P99）
  - 首字延迟（TTFB）、完整响应时间
  - Token消耗（input/output）
  - 失败率、错误类型分布
  - 打断率
- **实现**：
  - 在统一AI调用层埋点
  - 指标通过Micrometer暴露给Prometheus
  - 或简单方案：写入 `ai_call_logs` 表定时统计

```sql
ai_call_logs:
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  call_id VARCHAR(50),
  module_name VARCHAR(30),
  scene_type VARCHAR(30),
  user_id BIGINT,
  session_id BIGINT,
  prompt_length INT,
  response_length INT,
  latency_ms INT,
  ttfb_ms INT,                       -- Time To First Byte
  token_input INT,
  token_output INT,
  success TINYINT(1),
  error_msg TEXT,
  interrupted TINYINT(1) DEFAULT 0,
  created_at DATETIME,
  INDEX idx_module_time (module_name, created_at),
  INDEX idx_user_time (user_id, created_at)
```

- **接口**：

```
GET /api/conversation/stats/overview                  总体性能概览
GET /api/conversation/stats/module?moduleName=M1      按模块查看性能
```

###### 4.1.5 失败重试与降级服务

- **功能**：AI调用失败时的自动重试和降级策略
- **核心逻辑**：
  - 失败自动重试：最多3次，指数退避（1s/3s/9s）
  - 超时熔断：单次请求超过30秒自动中断
  - 降级策略：返回模板化兜底回答 → "抱歉，我暂时无法回答这个问题，请稍后再试或换个问法。"
  - 限流：单用户每分钟最多10次AI调用（Redis计数器）
- **接口**：无额外接口，集成在统一AI调用层

###### 4.1.6 新链路（WebSocket）

- **功能**：提供WebSocket通道作为SSE的补充/替代
- **优势**：双向通信，更低的延迟，天然支持打断
- **实现**：
  - WebSocket连接：`ws://host/api/conversation/ws?userId={userId}&sessionId={sessionId}`
  - 前端发消息 → 后端推流式回复 → 前端发interrupt → 后端停止
  - WebSocket心跳：每15秒ping/pong
- **注意**：如果SSE已经能满足需求，WebSocket可作为后续版本的优化项

##### 4.2 前端

###### 4.2.1 对话组件（可嵌入各模块）

- **组件**：ChatPanel（可嵌入做题页/讲题页/讲课页）, MessageBubble, StreamingText, InterruptButton
- **交互**：
  - 流式消息：AI回复逐字显示（打字机效果 + 闪烁光标）
  - "打断"按钮：AI正在生成时显示，点击后立刻停止并保留已生成内容
  - 上下文提示条：显示"当前正在讲解：勾股定理"，让用户知道AI知道当前上下文
  - 错误重试：失败时显示重试按钮
  - 语音输入按钮（调用浏览器SpeechRecognition API）
  - 图片上传按钮（拍照提问）
- **复用方式**：ChatPanel是一个独立React组件，各模块通过props传入 `sceneType` 和 `context` 即可嵌入

###### 4.2.2 对话状态恢复

- **功能**：页面刷新或网络中断后，自动恢复对话上下文
- **实现**：
  - 会话ID存在localStorage
  - 页面加载时调 `GET /api/conversation/sessions/{sessionId}` 恢复历史消息
  - 如果有未完成的AI回复，显示"回复生成中断，是否继续？"

#### 5. 完成效果与验收标准

| 验收项 | 标准 |
|--------|------|
| 流式响应 | SSE流式，首字延迟 < 2s |
| 打断可用 | 点击打断后500ms内停止生成 |
| 上下文保持 | 多轮对话中AI记住前文，能指代消解 |
| 断线恢复 | 刷新页面后对话历史完整恢复 |
| 降级不中断 | AI服务异常时返回兜底回答，不白屏 |
| 性能可监控 | 可查看各模块AI调用的延迟、成功率、token消耗 |
| 限流生效 | 超频用户收到"请稍后再试"提示 |

---

### M8 虚拟AI教师（优化）

#### 1. 模块概述

已有基础AI教师建模，本阶段重点优化：
1. **形象优化**：更好的2D/Live2D或3D建模效果
2. **表情与动作**：根据讲课内容自动切换表情和手势
3. **语音合成（TTS）优化**：更自然的语音，支持多种音色
4. **口型同步**：语音与口型联动
5. **状态管理**：待机/讲课/答疑/鼓励等不同状态的动作

#### 2. 与其他模块的关系

```
M8服务于：
  M4(即时讲课) → 讲课时的虚拟教师展示 + TTS朗读
  M7(即时交互) → 答疑时教师的表情和简短视频反馈
  M2(讲题)     → 讲题时可选显示教师形象

依赖：
  统一AI调用层 → TTS语音合成（调用TTS API）
```

**接口契约：**

M8提供给M4/M7/M2的教师驱动接口：

```
POST /api/virtual-teacher/speak
→ 请求：
{
  "text": "今天我们学习勾股定理",
  "voiceType": "male_teacher",
  "speed": 1.0,
  "emotion": "enthusiastic",         // neutral/happy/encouraging/serious/concerned
  "returnAudio": true,               // 是否返回音频
  "returnMouthShapes": true          // 是否返回口型数据
}
→ 返回：
{
  "audioUrl": "https://oss.xxx.com/tts/xxx.mp3",     // 或SSE流式返回音频base64
  "duration": 3500,                  // 音频时长ms
  "mouthShapes": [                   // 口型序列（用于前端口型同步）
    {"phoneme": "a", "startMs": 0, "endMs": 200},
    {"phoneme": "i", "startMs": 200, "endMs": 350}
  ],
  "expressionSequence": [            // 表情序列
    {"expression": "happy", "startMs": 0, "endMs": 1500},
    {"expression": "explaining", "startMs": 1500, "endMs": 3500}
  ]
}

POST /api/virtual-teacher/set-state
→ 请求：
{
  "teacherId": "teacher_001",
  "state": "lecturing",              // idle/listening/lecturing/answering/encouraging
  "params": {
    "expression": "neutral",
    "gesture": "pointing_board"
  }
}
→ 返回：{"success": true}
```

#### 3. 技术栈

| 层 | 技术 | 语言 |
|----|------|------|
| 前端 | React + Three.js（3D）/ Live2D Web SDK（2D）/ Canvas动画 | TypeScript |
| 后端(API/TTS层) | Spring Boot，TTS API编排（阿里云/腾讯云/Azure TTS），音频缓存管理，教师形象配置CRUD | **Java** |
| 后端(NLP层) | FastAPI + jieba/snownlp/transformers，文本情感分析 → 表情序列映射，内容语义分析 → 手势指令生成，口型序列（phoneme→时间轴）计算 | **Python** |
| 存储 | MinIO/OSS（TTS音频缓存、模型资源文件） | — |
| 缓存 | Redis（TTS音频缓存 `tts:audio:{hash}` → URL，TTL 24h） | — |

> **为什么混合？**
> - **Java做TTS编排**：调用第三方TTS API是典型的API编排+缓存管理，Spring Boot的RestTemplate/WebClient + Redis缓存策略非常成熟。音频文件的存储管理（MinIO/OSS）也适合在Java层统一处理。
> - **Python做NLP分析**：文本情感分析（"这道题非常重要！"→serious表情）、语义理解（讲解内容→pointing_board手势）、中文分词和关键词提取，这些NLP任务在Python生态中有现成的库（snownlp情感分析、jieba分词），Java生态几乎没有同等级别的中文NLP工具。

#### 4. 子模块拆分

##### 4.1 后端

###### 4.1.1 TTS语音合成服务

- **功能**：将文本转为语音，支持多种音色和语速
- **核心逻辑**：
  - 调用第三方TTS API（如微软Azure TTS、阿里云TTS、腾讯云TTS）
  - 音频缓存：相同文本+音色+语速 → Redis查缓存 → 命中直接返回URL，未命中调API
  - 支持流式TTS（边生成边播放，减少等待）
- **接口**：

```
POST /api/virtual-teacher/tts
→ 请求：{"text":"...", "voiceType":"male_teacher", "speed":1.0}
→ 返回（SSE流式）：
data: {"type":"audio","chunk":"base64..."}    // 流式音频片段
data: {"type":"done","audioUrl":"https://..."}
```

###### 4.1.2 表情与动作驱动服务

- **功能**：根据文本内容的情感分析，自动生成表情和动作指令
- **核心逻辑**：
  - 对讲课/对话文本做情感分析（调用AI或简单规则）
  - 映射：积极内容 → smiling表情，提问 → curious表情，强调 → serious表情 + pointing手势
  - 生成表情序列和口型序列
- **接口**：

```
POST /api/virtual-teacher/generate-expressions
→ 请求：{"text":"这道题非常重要，一定要注意！", "emotion":"serious"}
→ 返回：
{
  "expressions": [
    {"expression": "serious", "startMs": 0, "endMs": 3000}
  ],
  "gestures": [
    {"gesture": "emphasizing", "startMs": 500, "endMs": 2500}
  ]
}
```

###### 4.1.3 教师形象配置服务

- **功能**：管理可选AI教师形象、用户选择偏好
- **涉及数据表**：`teacher_avatars`（新建）

```sql
teacher_avatars:
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(100),                 -- 教师形象名称
  type VARCHAR(20),                  -- 2d / 3d / live2d
  avatar_url VARCHAR(500),           -- 预览图
  model_file_url VARCHAR(500),       -- 模型文件URL
  voice_type VARCHAR(50),            -- 默认音色
  is_active TINYINT(1) DEFAULT 1
```

- **接口**：

```
GET  /api/virtual-teacher/avatars                    可选教师形象列表
PUT  /api/virtual-teacher/user-preference            设置用户偏好的教师形象和音色
GET  /api/virtual-teacher/user-preference            获取用户当前偏好
```

##### 4.2 前端

###### 4.2.1 虚拟教师渲染组件

- **组件**：TeacherCanvas（Three.js Canvas 或 Live2D Canvas），TeacherStateIndicator
- **实现方案选择**：
  - **方案A（推荐·性价比高）**：Live2D —— 2D骨骼动画，表情变化流畅，体积小（5-10MB），对设备要求低
  - **方案B（效果好·资源消耗大）**：Three.js 3D模型 —— 3D渲染，可360度旋转，但开发成本高、体积大（50MB+）
  - **方案C（最简单）**：CSS + PNG序列帧动画 —— 预设几个关键帧表情切换
- **交互**：
  - Canvas渲染教师形象（支持最小尺寸 200×200px，可全屏）
  - 根据后端返回的口型序列 + 表情序列，驱动口型和表情变化
  - 状态指示器（小圆点+文字：讲课中 / 聆听中 / 思考中）

###### 4.2.2 教师形象选择页

- **组件**：AvatarSelector, AvatarPreview
- **交互**：
  - 教师形象卡片列表（预览图 + 名称 + 音色）
  - 点击预览 → 右侧3D/2D预览（可旋转、可试听语音）
  - 点击"选择" → 保存用户偏好

###### 4.2.3 嵌入讲课页的教师组件

- **组件**：InlineTeacher（精简版TeacherCanvas + 语音播放控制）
- **交互**：
  - 讲课时教师形象在侧边栏或底部（可最小化/展开）
  - 自动朗读当前幻灯片内容（调用TTS → 播放音频 + 触发表情口型动画）
  - 语音控制：播放/暂停/跳过/语速调节
  - 点击教师形象可唤起对话面板（对接M7）

#### 5. 完成效果与验收标准

| 验收项 | 标准 |
|--------|------|
| 形象可用 | 教师形象渲染流畅，≥30fps（普通设备） |
| TTS可用 | 文字→语音生成，延迟 < 2秒（有缓存时 < 500ms） |
| 口型同步 | 口型与语音基本同步（偏差 < 200ms） |
| 表情变化 | 讲课中表情能根据内容自动切换（至少3种表情） |
| 讲课嵌入 | 讲课页(M4)中可正常显示教师形象并朗读 |
| 音色可选 | 至少2种音色可选 |

---

## 四、附录：接口契约汇总

### 4.1 模块间数据依赖速查表

| 消费者 | 依赖提供者 | 获取方式 | 语言 | 数据内容 |
|--------|-----------|---------|------|---------|
| M2讲题 | M1做题 | 读表 `wrong_question_book` + `user_answers` | Java | 错题列表、用户错误答案 |
| M3薄弱点 | M1做题 | 读表 `user_answers`（Python计算引擎直连MySQL） | Python | 答题记录 |
| M3薄弱点 | M2讲题 | 读表 `teaching_contents`（讲题反馈） | Python | 用户"还是不懂"标记 |
| M3薄弱点 | M7交互 | 读表 `conversation_messages`（Python NLP分析） | Python | 用户提问中涉及的知识点 |
| M4讲课 | M3薄弱点 | GET `/api/weak-points`（Java API） | Java | 薄弱点列表 |
| M4讲课 | M5备课 | GET `/api/lesson-prep/contents`（Java API） | Java | 备课PPT结构 |
| M5备课 | M3薄弱点 | GET `/api/weak-points`（Java API） | Java | 薄弱点列表 |
| M6画像 | M1/M2/M3/M4/M7 | POST `/api/user-profile/{userId}/record-event`（Java API） | Java | 各模块行为事件 |
| M2/M4/M5/M7 | M6画像 | GET `/api/user-profile/{userId}`（Java API） | Java | 用户画像 |
| 所有AI调用 | M6画像 | GET `/api/user-profile/{userId}/summary` → 传入Python AI | Java→Python | 画像摘要（注入Prompt） |
| M2/M4/M5/M7 | Python AI服务 | `POST /api/internal/ai/*`（内部HTTP） | Java→Python | AI生成能力 |
| M2讲课/M4讲课 | M7交互 | POST `/api/conversation/ask`（Java API→Python AI） | Java→Python | 讲课中追问 |
| M8教师 | Python NLP | `POST /api/internal/ai/analyze-sentiment`（内部HTTP） | Java→Python | 情感分析→表情/动作 |

### 4.2 各模块对外提供的核心接口一览

#### Java Spring Boot 对外接口（前端调用）

| 模块 | 接口路径 | 方法 | 语言 | 主要消费者 |
|------|---------|------|------|-----------|
| M1 | `/api/questions` | GET | Java | 题库查询 |
| M1 | `/api/practice/generate-session` | POST | Java | 生成练习 |
| M1 | `/api/practice/submit-answer` | POST | Java | 提交答案 |
| M1 | `/api/wrong-questions` | GET | Java | M2获取错题 |
| M1 | `/api/practice/statistics` | GET | Java | 统计展示 |
| M1 | `/api/ranking/daily` | GET | Java | 排行榜 |
| M2 | `/api/ocr/recognize-question` | POST | Java→Python | 拍照识别 |
| M2 | `/api/explain/photo-qa` | POST | Java→Python | 拍照答疑(SSE) |
| M2 | `/api/explain/generate` | POST | Java→Python | 生成讲题(SSE) |
| M2 | `/api/explain/history` | GET | Java | 讲题历史 |
| M3 | `/api/weak-points` | GET | Java(读表) | M4/M5获取薄弱点 |
| M3 | `/api/weak-points/recommend-questions` | GET | Java | M1获取推荐练习 |
| M3 | `/api/weak-points/knowledge-graph` | GET | Java(读表) | 知识图谱 |
| M4 | `/api/lecture/parse-file` | POST | Java→Python | 文件解析 |
| M4 | `/api/lecture/generate` | POST | Java→Python | 生成讲课(SSE) |
| M4 | `/api/lecture/contents` | GET | Java | 讲课内容列表 |
| M5 | `/api/lesson-prep/generate` | POST | Java→Python | 生成备课(SSE) |
| M5 | `/api/lesson-prep/generate-ppt` | POST | Java→Python | 生成PPT |
| M5 | `/api/lesson-prep/contents` | GET | Java | M4获取备课内容 |
| M5 | `/api/lesson-prep/templates` | GET | Java | PPT模板列表 |
| M6 | `/api/user-profile/{userId}` | GET | Java(读表) | 各模块获取画像 |
| M6 | `/api/user-profile/{userId}/record-event` | POST | Java | 各模块上报事件 |
| M7 | `/api/conversation/ask` | POST | Java→Python | 各模块发起对话(SSE) |
| M7 | `/api/conversation/interrupt` | POST | Java | 打断生成 |
| M7 | `/api/conversation/sessions` | GET | Java | 会话管理 |
| M8 | `/api/virtual-teacher/speak` | POST | Java→Python | TTS+表情 |
| M8 | `/api/virtual-teacher/avatars` | GET | Java | 教师形象列表 |

#### Python FastAPI 内部接口（仅Java后端调用，不对外暴露）

| 服务 | 接口路径 | 方法 | 说明 |
|------|---------|------|------|
| AI调用 | `/api/internal/ai/generate` | POST | 非流式AI调用（统一入口） |
| AI调用 | `/api/internal/ai/generate/stream` | POST | 流式AI调用（SSE，Java透传） |
| OCR处理 | `/api/internal/ai/ocr/process` | POST | OCR文本后处理与结构化 |
| 文档解析 | `/api/internal/ai/parse-document` | POST | PDF/Word/PPT/图片→结构化文本 |
| 薄弱点分析 | `/api/internal/ai/analyze-weakness` | POST | 用户薄弱点计算（定时或事件触发） |
| 用户画像 | `/api/internal/ai/build-profile` | POST | 用户画像构建与更新 |
| 备课生成 | `/api/internal/ai/generate-lesson-prep` | POST | AI备课内容生成 |
| 讲课生成 | `/api/internal/ai/generate-lecture` | POST | AI讲课内容+PPT结构生成 |
| 情感分析 | `/api/internal/ai/analyze-sentiment` | POST | 文本情感分析→表情/动作序列 |
| 上下文压缩 | `/api/internal/ai/compress-context` | POST | 对话历史摘要压缩 |

### 4.3 开发建议顺序

基于模块间依赖关系和技术栈差异，建议按以下顺序推进：

```
第0批（基础设施·优先搭建）：
  └─ Python AI微服务框架搭建（FastAPI + AI调用层 + Prompt管理）
  └─ Java与Python内部通信链路打通（PythonAIClient + 流式透传验证）

第1批（并行启动）：
  └─ M1 做题板块（纯Java，核心数据源，优先）
  └─ M7 即时交互优化（Java为主+Python AI生成，底层服务）

第2批（依赖M1+M7+Python服务完成）：
  └─ M2 拍照答疑+讲题（Java API + Python OCR/AI）
  └─ M3 薄弱点列表（Java API + Python计算引擎）
  └─ M6 上下文记忆（Java事件采集 + Python画像引擎）

第3批（依赖M3+M6完成）：
  └─ M5 AI备课（Java管理 + Python AI生成）
  └─ M8 虚拟AI教师优化（Java TTS编排 + Python NLP分析）

第4批（依赖M3+M5+M8）：
  └─ M4 即时讲课（聚合模块，依赖最多：Java API + Python文档解析/AI生成）
```

> **注意**：由于引入了Python微服务，第0批（基础设施搭建）是必须优先完成的。Python服务的内网API需要在M2/M3/M4/M5/M6开发前就稳定下来。

---

> **文档版本**：v2.0（新增Java+Python双技术栈架构）
> **最后更新**：2026-07-16
