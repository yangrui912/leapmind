# LeapMind 阳睿开发指南

> 本指南为阳睿在 LeapMind 项目中的专属开发手册，涵盖 Git 工作流、OpenCode AI 开发环境配置、以及全部任务的分步实现方案。按照本指南操作即可完成所有开发工作。

---

## 目录

- [第一章 环境准备与 Git 工作流](#第一章-环境准备与-git-工作流)
- [第二章 OpenCode AI 开发环境](#第二章-opencode-ai-开发环境)
- [第三章 M2 Python AI 后端实现](#第三章-m2-python-ai-后端实现)
- [第四章 M2 前端实现（3 页）](#第四章-m2-前端实现3-页)
- [第五章 M5 前端实现（4 页）](#第五章-m5-前端实现4-页)
- [第六章 M3 前端实现（3 页）](#第六章-m3-前端实现3-页)
- [第七章 依赖协作与验收标准](#第七章-依赖协作与验收标准)

---

## 第一章 环境准备与 Git 工作流

### 1.1 你的任务总览

| 类别 | 模块 | 具体内容 | 数量 |
|------|------|---------|------|
| Python AI 后端 | M2 | OCR 后处理 + 答疑/讲题 Prompt 构建 | 3 个接口 |
| 前端主攻 | M2 | 拍照答疑页 + 讲题页 + 讲题历史页 | 3 页 |
| 前端主攻 | M5 | 备课创建页 + 备课内容编辑页 + PPT 预览编辑页 + 备课内容列表页 | 4 页 |
| 前端兼顾 | M3 | 薄弱点列表页 + 薄弱点详情页 + 知识图谱页 | 3 页 |

### 1.2 克隆仓库到本地

你已经将项目 fork 到了自己的仓库 `github.com/yangrui912/leapmind`。现在需要克隆到本地：

```bash
# 1. 克隆你的 fork 仓库（默认只克隆 main 分支）
git clone https://github.com/yangrui912/leapmind.git
cd leapmind

# 2. 关联上游原始仓库（用于同步主仓库的更新）
git remote add upstream https://github.com/dgut-hsd/leapmind.git

# 3. 验证远程仓库配置
git remote -v
# 应该看到：
# origin    https://github.com/yangrui912/leapmind.git (fetch)
# origin    https://github.com/yangrui912/leapmind.git (push)
# upstream  https://github.com/dgut-hsd/leapmind.git (fetch)
# upstream  https://github.com/dgut-hsd/leapmind.git (push)
```

### 1.3 分支结构与策略

仓库共有 11 个分支，结构如下：

```
main        ← 正式服代码（生产环境）
  ↑
develop     ← 测试服代码（每周五通过 PR 汇总合并）
  ↑
frontend    ← 前端开发集成分支（你的前端任务基于此分支）
backend-M2  ← M2 后端开发分支（你的 Python AI 后端任务基于此分支）
backend-M1~M8 ← 各模块后端开发分支
```

**分支说明：**

| 分支 | 用途 | 说明 |
|------|------|------|
| `main` | 正式服代码 | 生产环境，只从 develop 合并，不直接开发 |
| `develop` | 测试服代码 | 测试环境，**每周五通过 PR 汇总合并**各开发分支的代码 |
| `frontend` | 前端开发分支 | 你的所有前端任务（M2/M5/M3 页面）都基于此分支 |
| `backend-M2` | M2 后端开发分支 | 你的 Python AI 后端任务基于此分支 |

**你只需要关注以下两个基础分支：**

| 你的任务 | 基础分支 | 说明 |
|---------|---------|------|
| M2 Python AI 后端 | `backend-M2` | Python AI 服务的开发分支 |
| M2/M5/M3 前端页面 | `frontend` | 前端开发集成分支 |

```bash
# 获取所有远程分支信息（只下载元数据，不切换）
git fetch --all

# ⚠️ 重要：因为你的 fork 仓库(origin)和团队上游仓库(upstream)都有同名分支
# （如 origin/backend-M2 和 upstream/backend-M2 同时存在），
# 直接执行 git checkout backend-M2 会报错：
#   fatal: 'backend-M2' matched multiple (2) remote tracking branches
# 解决方法：首次切换时必须用 --track 明确指定跟踪上游仓库！

# 切换到 frontend 分支（前端开发用）——首次切换
git checkout -b frontend --track upstream/frontend

# 或者切换到 backend-M2 分支（Python AI 后端开发用）——首次切换
git checkout -b backend-M2 --track upstream/backend-M2
```

> **重要说明：**
> - **首次切换**某分支时，必须用 `git checkout -b <分支名> --track upstream/<分支名>`，明确告诉 Git 跟踪上游仓库。
> - **后续切换**（本地已有该分支后），直接 `git checkout <分支名>` 即可，不会再报错。
> - `git fetch --all` 只下载元数据，不会创建本地分支。`--track` 参数会在创建本地分支的同时设置跟踪关系。

### 1.4 日常工作流（Fork → Branch → PR）

> **开发规范：每次开发前必须先拉取最新代码！** 这不是可选项，是团队协作的强制要求。不同成员可能已经推送了新代码，如果你不拉取最新代码就开始开发，后期合并时会产生大量冲突。

每次开发新功能时，严格遵循以下步骤：

```bash
# === 第 1 步：拉取最新代码（每次开发必须执行！）===
# 切换到基础分支（首次切换用 git checkout -b <分支名> --track upstream/<分支名>）
# 后续切换（本地已有该分支）直接用 git checkout <分支名> 即可
git checkout frontend      # 前端开发用（或 backend-M2 后端开发用）

# 拉取上游最新代码
git fetch upstream
git merge upstream/frontend   # 或 upstream/backend-M2

# 推送到你自己的 fork（保持 fork 同步）
git push origin frontend      # 或 origin/backend-M2

# === 第 2 步：创建功能分支 ===
# 从基础分支创建新的功能分支
git checkout -b feature/m2-frontend-photo-qa frontend
# 命名规则：feature/<模块>-<功能描述>

# === 第 3 步：开发代码 ===
# 在功能分支上进行开发...
# 使用 OpenCode 进行 AI 辅助开发（见第二章）

# === 第 4 步：提交代码 ===
git add .
git commit -m "feat(m2-frontend): create photo Q&A page with camera capture"

# === 第 5 步：推送到你的 fork ===
git push origin feature/m2-frontend-photo-qa

# === 第 6 步：在 GitHub 上创建 PR ===
# 访问 https://github.com/yangrui912/leapmind
# 点击 "Compare & pull request"
# 确认：base repository = dgut-hsd/leapmind, base = frontend (或 backend-M2)
#       head repository = yangrui912/leapmind, compare = feature/m2-frontend-photo-qa
# 填写 PR 标题和描述，点击 "Create pull request"
```

### 1.5 周五代码汇总规范

> **每周五需要汇总一次代码到 `develop` 分支上，以 Pull Request 的形式提交。**

团队约定每周五将各开发分支（`frontend`、`backend-M2` 等）的代码汇总合并到 `develop` 测试服分支，供周末测试使用。

```bash
# === 周五汇总操作（以前端为例）===

# 1. 确保你的 frontend 分支是最新的
git checkout frontend          # 后端用：git checkout backend-M2
git fetch upstream
git merge upstream/frontend    # 后端用：git merge upstream/backend-M2
git push origin frontend       # 后端用：git push origin backend-M2

# 2. 在 GitHub 上创建 PR：frontend → develop
#    base: dgut-hsd/leapmind:develop
#    head: yangrui912/leapmind:frontend
#    标题：weekly: merge frontend into develop (W30)
#    描述：本周前端开发汇总，包含以下功能：
#         - M2 拍照答疑页
#         - M3 知识图谱页

# 3. 通知团队 reviewer 进行 Code Review
# 4. Review 通过后合并到 develop
# 5. develop 上的代码会部署到测试服供测试
```

**分支层级与汇总流程：**

```
你的 feature 分支        →  PR 合并到基础分支（frontend / backend-M2）
frontend / backend-M2    →  每周五 PR 汇总合并到 develop（测试服）
develop                  →  测试通过后 PR 合并到 main（正式服）
```

| 场景 | 源分支 | 目标分支 | 频率 | 说明 |
|------|--------|---------|------|------|
| 日常开发 | `feature/xxx` | `frontend` 或 `backend-M2` | 随时 | 功能开发完成即提 PR |
| 周五汇总 | `frontend` / `backend-M2` | `develop` | 每周五一次 | 汇总本周代码到测试服 |
| 正式发布 | `develop` | `main` | 测试通过后 | 发布到生产环境 |

### 1.6 你的分支规划

| 开发任务 | 基础分支 | 功能分支名 | PR 目标 |
|---------|---------|-----------|---------|
| M2 OCR 后处理 | `backend-M2` | `feature/m2-ocr-processor` | `backend-M2` |
| M2 答疑/讲题 Prompt | `backend-M2` | `feature/m2-prompt-manager` | `backend-M2` |
| M2 拍照答疑页 | `frontend` | `feature/m2-frontend-photo-qa` | `frontend` |
| M2 讲题页 | `frontend` | `feature/m2-frontend-explain` | `frontend` |
| M2 讲题历史页 | `frontend` | `feature/m2-frontend-explain-history` | `frontend` |
| M3 薄弱点列表页 | `frontend` | `feature/m3-frontend-weakpoint-list` | `frontend` |
| M3 薄弱点详情页 | `frontend` | `feature/m3-frontend-weakpoint-detail` | `frontend` |
| M3 知识图谱页 | `frontend` | `feature/m3-frontend-knowledge-graph` | `frontend` |
| M5 备课创建页 | `frontend` | `feature/m5-frontend-prep-create` | `frontend` |
| M5 备课编辑页 | `frontend` | `feature/m5-frontend-prep-edit` | `frontend` |
| M5 PPT 编辑页 | `frontend` | `feature/m5-frontend-ppt-editor` | `frontend` |
| M5 备课列表页 | `frontend` | `feature/m5-frontend-prep-list` | `frontend` |

### 1.7 Commit 规范

使用 Conventional Commits 格式：

```
<type>(<scope>): <description>
```

| type | 含义 | 示例 |
|------|------|------|
| `feat` | 新功能 | `feat(m2-backend): implement OCR text denoising` |
| `fix` | 修复 | `fix(m2-frontend): fix SSE connection timeout` |
| `refactor` | 重构 | `refactor(m3-frontend): extract weak point card component` |
| `docs` | 文档 | `docs: update README for M5 module` |
| `chore` | 构建/配置 | `chore: add @dnd-kit dependency` |

### 1.8 处理 PR Review 反馈

```bash
# 如果 reviewer 提了修改意见，在同一分支上继续修改
git checkout feature/m2-frontend-photo-qa

# 修改代码后提交
git add .
git commit -m "fix(m2-frontend): address review feedback on camera component"

# 推送（PR 会自动更新）
git push origin feature/m2-frontend-photo-qa
```

### 1.9 解决冲突

```bash
# 如果 PR 有冲突，先同步上游（以前端为例）
git checkout frontend
git fetch upstream
git merge upstream/frontend
git push origin frontend

# 回到功能分支，rebase 到最新 frontend
git checkout feature/m2-frontend-photo-qa
git rebase frontend

# 此时如果出现冲突，Git 会暂停 rebase 并提示冲突的文件
# 可以使用 git status 查看冲突文件列表
git status

# 在代码编辑器中手动解决冲突（删除冲突标记 <<<<<<<, =======, >>>>>>>）
# 解决完毕后，继续执行以下命令：

git add .  # 将解决冲突后的文件标记为已解决
git rebase --continue  # 继续 rebase 流程
# 如果想中止 rebase，可以执行：git rebase --abort

# 强制推送（rebase 后本地分支历史已改变，需要强制推送，推荐使用 --force-with-lease 以防覆盖他人提交）
git push origin feature/m2-frontend-photo-qa --force-with-lease
```

### 1.10 M2 小组长 PR 审查工作流程（你作为 M2 组长的职责）

> **你是 M2 模块的小组长**，需要负责审查 M2 团队成员提交的 PR，审查通过后将代码合并到对应的基础分支（`backend-M2` 或 `frontend`），然后在周五汇总时统一推送到 `develop` 测试服分支。

#### 整体职责

```
M2 成员提交 PR → 你审查代码 + 评论 → 审查通过 → 合并到基础分支（backend-M2/frontend）
                                              → 周五汇总时 PR 合并到 develop
```

#### 第 1 步：查看 M2 成员的 PR

```bash
# 方法一：在 GitHub 网页上查看
# 访问 https://github.com/dgut-hsd/leapmind/pulls
# 筛选条件：label=M2 或在标题中搜索 m2

# 方法二：使用 GitHub CLI 命令行查看（更高效）
# 安装 GitHub CLI（如果尚未安装）
# macOS: brew install gh
# Linux: sudo apt install gh
# Windows: choco install gh

# 登录
gh auth login

# 查看 M2 相关的 PR
gh pr list --repo dgut-hsd/leapmind --search "m2 in:title" --state open

# 查看某个 PR 的详情
gh pr view <PR编号> --repo dgut-hsd/leapmind

# 查看 PR 的代码变更
gh pr diff <PR编号> --repo dgut-hsd/leapmind
```

#### 第 2 步：审查 PR 代码

审查 PR 时需要关注以下要点：

| 审查维度 | 检查内容 | 不通过的情况 |
|---------|---------|-------------|
| 代码规范 | 命名规范、缩进、注释是否完整 | 变量名随意、无注释、缩进混乱 |
| 功能完整性 | 是否实现了 PR 描述中的全部功能 | 功能缺失、接口未实现、边界条件未处理 |
| 接口契约 | API 入参/返回值是否与文档定义一致 | 字段名不一致、类型不匹配、缺少必填字段 |
| 错误处理 | 是否有 try-catch、参数校验、错误提示 | 无错误处理、异常直接暴露给用户 |
| 安全性 | 是否有 SQL 注入、XSS、敏感信息泄露 | 硬编码密钥、未校验用户输入 |
| 代码冲突 | 是否与最新基础分支有冲突 | PR 显示 "This branch has conflicts" |

#### 第 3 步：在 PR 上发表评论

**情况 A：审查不通过，需要修改**

在 GitHub PR 页面的 Files changed 标签页中，逐行评论需要修改的地方：

```bash
# 使用 GitHub CLI 添加评论
gh pr comment <PR编号> --repo dgut-hsd/leapmind --body "代码审查反馈：

## 需要修改的问题
1. **ocr_processor.py 第 35 行**：正则表达式缺少对中文逗号的处理，建议补充 `，` 到过滤字符列表
2. **schemas.py**：`OCRProcessResponse` 缺少 `ocrRecordId` 字段，根据接口文档需要返回此字段
3. **前端 CameraCapture.tsx**：缺少摄像头权限被拒绝时的错误提示

## 建议
- 代码整体结构清晰，功能基本完整
- 修复以上 3 个问题后重新提交，我来再次审查

请修改后 push 到同一分支，PR 会自动更新。"
```

**情况 B：审查通过，可以合并**

```bash
# 添加 Approve 评论
gh pr review <PR编号> --repo dgut-hsd/leapmind --approve --body "代码审查通过 ✅

- 功能完整，接口契约符合文档定义
- 代码规范良好，注释清晰
- 错误处理完善

准备合并到 backend-M2 分支。"
```

#### 第 4 步：合并 PR 到基础分支

审查通过后，将 PR 合并到对应的基础分支：

```bash
# 方法一：在 GitHub 网页上点击 "Merge pull request" 按钮

# 方法二：使用 GitHub CLI 合并（推荐）
# 合并到 backend-M2（后端 PR）
gh pr merge <PR编号> --repo dgut-hsd/leapmind --merge --base backend-M2

# 合并到 frontend（前端 PR）
gh pr merge <PR编号> --repo dgut-hsd/leapmind --merge --base frontend

# 合并方式说明：
# --merge   创建合并提交（保留分支历史，推荐）
# --squash  压缩为一个提交（历史更干净）
# --rebase  rebase 合并（线性历史）
```

#### 第 5 步：周五汇总到 develop 分支

每周五，你需要将本周 M2 模块的所有已合并代码从基础分支汇总到 `develop` 测试服分支：

```bash
# === 后端汇总：backend-M2 → develop ===
# 1. 确保本地 backend-M2 最新
git checkout backend-M2
git fetch upstream
git merge upstream/backend-M2
git push origin backend-M2

# 2. 在 GitHub 上创建 PR：backend-M2 → develop
gh pr create \
  --repo dgut-hsd/leapmind \
  --base develop \
  --head backend-M2 \
  --title "weekly: merge backend-M2 into develop (W30)" \
  --body "## 本周 M2 后端汇总
### 合并的 PR
- #45 feat(m2-backend): implement OCR text denoising
- #47 feat(m2-backend): implement photo Q&A prompt manager
- #49 feat(m2-backend): implement explain wrong prompt

### 变更内容
- 新增 OCR 后处理服务（ocr_processor.py）
- 新增答疑/讲题 Prompt 模板（prompt_manager.py）
- 新增 SSE 流式生成接口（ai_generate_stream.py）"

# 3. 通知测试团队，Review 通过后合并到 develop
```

```bash
# === 前端汇总：frontend → develop ===
git checkout frontend
git fetch upstream
git merge upstream/frontend
git push origin frontend

gh pr create \
  --repo dgut-hsd/leapmind \
  --base develop \
  --head frontend \
  --title "weekly: merge frontend into develop (W30)" \
  --body "## 本周前端汇总
### 合并的 PR
- #46 feat(m2-frontend): create photo Q&A page
- #48 feat(m3-frontend): create weak point list page
- #50 feat(m5-frontend): create lesson prep create page

### 变更内容
- M2 拍照答疑页（CameraCapture + QAResultPanel）
- M3 薄弱点列表页（WeakPointCard + 排序筛选）
- M5 备课创建页（KnowledgePointSelector + StyleSelector）"
```

#### M2 小组长日常工作清单

| 频率 | 工作内容 |
|------|---------|
| 每天 | 查看 M2 成员是否有新 PR 提交，及时审查 |
| PR 提交后 24h 内 | 完成代码审查并发表评论（通过或需修改） |
| 审查通过后 | 合并 PR 到基础分支（`backend-M2` 或 `frontend`） |
| 每周五 | 将 `backend-M2` 和 `frontend` 的代码汇总 PR 到 `develop` |
| 随时 | 协调 M2 成员之间的代码冲突，指导开发规范 |

#### 审查 PR 时的沟通原则

1. **具体明确**：评论指出具体文件和行号，不要笼统说"代码有问题"
2. **对事不对人**：评论针对代码质量，不针对开发者个人
3. **给出建议**：不仅指出问题，还给出修改建议或参考代码
4. **及时响应**：PR 提交后 24 小时内完成审查，避免阻塞团队成员开发
5. **区分优先级**：`[必须修改]` 标记阻塞问题，`[建议优化]` 标记非阻塞建议

---

## 第二章 OpenCode AI 开发环境

### 2.1 安装 OpenCode

```bash
# 方式一：安装脚本（推荐）
curl -fsSL https://opencode.ai/install | bash

# 方式二：npm
npm install -g opencode-ai

# 方式三：Homebrew（macOS/Linux）
brew install anomalyco/tap/opencode

# 验证安装
opencode --version
```

### 2.2 配置 LLM Provider

OpenCode 需要配置一个大模型 API Key。你可以选择 OpenCode Zen（官方推荐）或其他提供商。

```bash
# 启动 OpenCode
opencode

# 在 TUI 中执行 /connect 命令
/connect
# 选择 opencode，然后访问 https://opencode.ai/auth 获取 API Key
# 或者选择其他提供商（如 anthropic、openai）
```

也可以通过环境变量配置：

```bash
# 添加到 ~/.bashrc 或 ~/.zshrc
export ANTHROPIC_API_KEY="your-api-key"
# 或
export OPENAI_API_KEY="your-api-key"
```

### 2.3 项目初始化

```bash
# 进入项目目录
cd /path/to/leapmind

# 启动 OpenCode
opencode

# 在 TUI 中执行 /init 命令
/init
# OpenCode 会扫描项目结构，自动生成 AGENTS.md 文件
```

### 2.4 AGENTS.md 内容建议

`/init` 生成的文件可能不够详细，建议手动补充以下内容：

```markdown
# LeapMind Project

## Overview
LeapMind 是一个 AI 互动教育平台，支持拍照答疑、AI 讲题、薄弱点分析、智能备课等功能。

## Tech Stack
- Frontend: React + TypeScript + Vite (端口 5173)
- Java Backend: Spring Boot 3 + MyBatis-Plus (端口 8080)
- Python AI Service: FastAPI (端口 8000，内部调用)
- Database: MySQL 8.0 + Redis

## Project Structure
- aitutor-frontend/          # React + Vite 前端
- aitutor-backend-java/      # Spring Boot Java 后端
- aitutor-backend-python/    # FastAPI Python AI 微服务

## Code Standards
- Frontend: TypeScript strict mode, ESLint + Prettier, 函数组件 + Hooks
- Python: PEP 8, type hints, Pydantic models for all API schemas
- Commit: Conventional Commits format

## Key Conventions
- Python AI 服务仅内部调用，不直接暴露给前端
- Java 通过 HTTP 调用 Python，前端不直接访问 Python
- 流式场景：Python 返回 SSE，Java 透传给前端
- API 路由：Java 用 /api/ 前缀，Python 用 /api/internal/ai/ 前缀
- 前端 SSE 使用 @microsoft/fetch-event-source 库
```

### 2.5 项目级配置 opencode.json

在项目根目录创建 `opencode.json`：

```json
{
  "$schema": "https://opencode.ai/config.json",
  "model": "anthropic/claude-sonnet-4-5",
  "instructions": ["AGENTS.md"],
  "permission": {
    "edit": "ask",
    "bash": "ask"
  },
  "watcher": {
    "ignore": ["node_modules/**", "dist/**", ".git/**", "target/**", "__pycache__/**"]
  }
}
```

### 2.6 日常使用 OpenCode

OpenCode 有两种工作模式，通过 **Tab 键** 切换：

| 模式 | 用途 | 说明 |
|------|------|------|
| Plan 模式 | 分析需求、制定计划 | 不会修改任何文件，只给出建议 |
| Build 模式 | 实际编写代码 | 确认计划后切换到此模式执行 |

**典型工作流程：**

```
# 1. 在 OpenCode TUI 中，按 Tab 切换到 Plan 模式
<TAB>

# 2. 描述你的需求（使用 @ 引用文件）
"请帮我实现 OCR 后处理服务，参考 @aitutor-backend-python/ 下的现有代码结构。
需要实现文本去噪、题干/选项结构识别、数学公式识别功能。"

# 3. 审查 OpenCode 给出的计划，可以追问或补充
"请在计划中加入低置信度时的 AI 二次纠错逻辑"

# 4. 确认计划后，按 Tab 切换到 Build 模式
<TAB>

# 5. 让它开始实现
"计划没问题，请开始实现。"

# 6. 如果不满意，使用 /undo 撤销
/undo
```

**其他常用命令：**

| 命令 | 作用 |
|------|------|
| `/init` | 初始化项目，生成 AGENTS.md |
| `/undo` | 撤销上次修改（可多次执行） |
| `/redo` | 恢复撤销的修改 |
| `/share` | 生成对话分享链接 |
| `@` | 模糊搜索引用项目文件 |

### 2.7 针对每个任务的 OpenCode Prompt 模板

#### M2 OCR 后处理

```
请帮我实现 OCR 后处理服务。
参考 @aitutor-backend-python/ 下的现有代码结构和路由注册方式。

需要实现以下功能：
1. 文本去噪：去除多余空白、OCR 乱码字符
2. 结构识别：识别题干、选项（A/B/C/D）、填空位置、题型判断
3. 数学公式识别：识别 ∠、°、√、∑ 等数学符号
4. 置信度评估：根据识别完整度计算置信度
5. AI 二次纠错：置信度 < 0.7 时调用大模型纠错

接口：POST /api/internal/ai/ocr/process
输入：{ rawText, subject, imageUrl }
输出：{ recognizedText, structuredQuestion: { stem, options, type, subject }, confidence }

请在 services/ocr_processor.py 中实现核心逻辑，
在 api/ocr_process.py 中实现路由，
在 models/schemas.py 中定义 Pydantic 数据模型。
```

#### M2 前端页面

```
请帮我创建 M2 拍照答疑页面。
参考 @aitutor-frontend/src/ 下的现有组件和路由结构。

需要实现以下组件：
1. CameraCapture - 调用摄像头拍照，使用 navigator.mediaDevices.getUserMedia()
2. ImageUploader - 从相册选择图片上传
3. OCRResultCard - 展示 OCR 识别结果，支持编辑修正
4. QAResultPanel - SSE 流式展示 AI 答疑内容，打字机效果

技术要求：
- React + TypeScript
- 使用 @microsoft/fetch-event-source 处理 SSE 流式请求
- 使用 react-markdown + rehype-katex 渲染数学公式
- 状态管理：image, ocrResult, streamingContent, loading

页面路由：/m2/photo-qa
```

#### M5 前端页面

```
请帮我创建 M5 PPT 预览与编辑页面。
参考 @aitutor-frontend/src/ 下的现有组件结构。

需要实现以下组件：
1. SlideThumbnailList - 左侧幻灯片缩略图列表，支持拖拽排序（@dnd-kit/core）
2. SlideCanvas - 中间 16:9 Canvas 预览区域
3. StylePanel - 右侧样式面板（模板选择、配色、字体）
4. SlideRenderer - 共享幻灯片渲染组件（preview/edit/present 三种模式）

功能要求：
- 支持增删改幻灯片页
- 拖拽调整页面顺序
- 公式渲染（KaTeX）
- 导出 PPTX 按钮
- "开始讲课"按钮跳转 M4

页面路由：/m5/ppt-editor/:pptId
```

#### M3 知识图谱

```
请帮我创建 M3 知识图谱页面。
参考 @aitutor-frontend/src/ 下的现有组件和样式规范。

需要实现：
1. KnowledgeGraphCanvas - ECharts 力导向图
   - 节点颜色映射掌握度：>0.8 绿色 / >0.5 黄色 / >0 红色 / 0 灰色
   - 薄弱点节点放大显示
   - 单击节点跳转薄弱点详情页
   - 双击节点跳转做题页
   - 支持缩放和拖拽
2. 图例说明
3. 节点详情弹窗

数据接口：GET /api/weak-points/knowledge-graph
返回：{ nodes: [{id, name, mastery, isWeakPoint}], links: [{source, target}] }

页面路由：/m3/knowledge-graph
```

---

## 第三章 M2 Python AI 后端实现

> **接口对接速查（开始开发前先确认）**
>
> | 方向 | 接口 | 对接人 | 你要做什么 |
> |------|------|--------|-----------|
> | **你提供给别人** | `POST /api/internal/ai/ocr/process` | **王圳**、**孔维诚** | 主动发接口文档给他们（入参/返回值/SSE 事件格式） |
> | **你提供给别人** | `POST /api/internal/ai/generate/stream`（答疑） | **王圳**、**孔维诚** | 同上 |
> | **你提供给别人** | `POST /api/internal/ai/generate/stream`（讲题） | **王圳**、**孔维诚** | 同上 |
> | **你依赖别人** | `GET /api/user-profile/{userId}/summary`（画像） | **张梓鸿**（M6 Java） | 找他要接口文档，用于注入 Prompt 实现个性化 |
> | **你依赖别人** | `GET /api/wrong-questions`（错题列表） | **杜恩泽**（M1 Java） | 找他要接口文档，用于讲题时获取错题 |
> | **前置条件** | Python AI 微服务框架 | 确认是否已搭建 | 如未搭建需先完成 FastAPI 框架 |
>
> ⚡ **行动顺序**：先搭好 Python 框架 → 写好 3 个接口 → 把接口文档发给王圳和孔维诚 → 同时找张梓鸿、杜恩泽要画像和错题本接口文档。

### 3.1 Python AI 微服务项目结构

项目的 Python 服务在 `aitutor-backend-python/` 目录下。根据任务文档，目录结构如下：

```
aitutor-backend-python/
├── run.py                          # 启动入口
├── main.py                         # FastAPI 应用入口
├── config.py                       # 配置管理
├── .env.example                    # 环境变量模板
├── api/                            # API 路由层
│   ├── __init__.py
│   ├── ai_generate.py              # /api/internal/ai/generate（非流式）
│   ├── ai_generate_stream.py       # /api/internal/ai/generate/stream（流式SSE）
│   └── ocr_process.py              # /api/internal/ai/ocr/process ★你负责
├── services/                       # 业务逻辑层
│   ├── __init__.py
│   ├── ai_client.py                # 大模型 API 调用封装
│   ├── prompt_manager.py           # Prompt 模板管理 ★你负责（答疑+讲题）
│   └── ocr_processor.py            # OCR 后处理 ★你负责
├── models/                         # 数据模型层
│   ├── __init__.py
│   └── schemas.py                  # Pydantic 数据模型
└── utils/                          # 工具层
    ├── __init__.py
    ├── db_helper.py                # 数据库连接
    └── cache_helper.py             # Redis 缓存
```

### 3.2 开发前准备

```bash
# 切换到 backend-M2 分支并创建功能分支
# ⚠️ 首次切换 backend-M2 时，因为 origin 和 upstream 都有同名分支，直接 checkout 会报错
# 必须用 --track 指定上游仓库：
git checkout -b backend-M2 --track upstream/backend-M2   # 首次切换
# 后续切换直接用：git checkout backend-M2

# 同步上游最新代码
git fetch upstream
git merge upstream/backend-M2
git push origin backend-M2

git checkout -b feature/m2-ocr-processor backend-M2

# 安装依赖
cd aitutor-backend-python
cp .env.example .env
# 编辑 .env，填入至少一个 AI Provider 的 API Key
pip install uv
uv sync

# 启动服务确认能跑通
python run.py
# 访问 http://localhost:8000/docs 查看 API 文档
```

### 3.3 任务 A：OCR 后处理服务

#### 功能需求

图片上传后，Java 层调用 OCR API 识别文字，**Python 负责 OCR 结果的后处理**：
- 去除噪声文字
- 识别题干/选项结构
- 识别数学公式
- 低置信度时用 AI 二次纠错

#### Step 1：定义 Pydantic 数据模型

在 `models/schemas.py` 中添加：

```python
from pydantic import BaseModel
from typing import Optional, List
from enum import Enum


class QuestionType(str, Enum):
    """题目类型"""
    SINGLE_CHOICE = "single_choice"
    MULTI_CHOICE = "multi_choice"
    FILL_BLANK = "fill_blank"
    SHORT_ANSWER = "short_answer"
    ESSAY = "essay"


class StructuredQuestion(BaseModel):
    """结构化题目"""
    stem: str                                    # 题干
    options: Optional[List[str]] = None          # 选项列表
    type: QuestionType                           # 题型
    subject: str                                 # 科目


class OCRProcessRequest(BaseModel):
    """OCR 后处理请求"""
    rawText: str                                 # OCR 原始识别文本
    subject: Optional[str] = None                # 科目（可选）
    imageUrl: Optional[str] = None               # 原始图片 URL（可选）


class OCRProcessResponse(BaseModel):
    """OCR 后处理响应"""
    recognizedText: str                          # 清洗后的识别文本
    structuredQuestion: StructuredQuestion        # 结构化题目
    confidence: float                            # 置信度 0-1
```

#### Step 2：实现 OCR 后处理器

在 `services/ocr_processor.py` 中实现：

```python
import re
import json
from typing import Optional
from models.schemas import StructuredQuestion, QuestionType


class OCRProcessor:
    """OCR 后处理器"""

    def __init__(self, ai_client=None):
        self.ai_client = ai_client  # 可选，用于低置信度时二次纠错

    def process(self, raw_text: str, subject: str = None) -> dict:
        """OCR 后处理主流程"""
        # 1. 文本去噪
        cleaned_text = self._denoise(raw_text)

        # 2. 结构识别
        structured = self._recognize_structure(cleaned_text, subject)

        # 3. 置信度评估
        confidence = self._evaluate_confidence(cleaned_text, structured)

        # 4. 低置信度时 AI 二次纠错
        if confidence < 0.7 and self.ai_client:
            structured = self._ai_correction(cleaned_text, subject)
            confidence = max(confidence, 0.85)

        return {
            "recognizedText": cleaned_text,
            "structuredQuestion": structured,
            "confidence": confidence
        }

    def _denoise(self, text: str) -> str:
        """去除 OCR 噪声：多余空白、乱码字符"""
        # 去除多余空白
        text = re.sub(r'\s+', ' ', text).strip()
        # 去除常见 OCR 噪声字符（保留中文、英文、数字、常见标点和数学符号）
        text = re.sub(
            r'[^\w\s\u4e00-\u9fff（）。，；：、""''！？'
            r'\-\+\*\/\=\<\>∠°√∑∏∫π∞≠≤≥≈±×÷]',
            '',
            text
        )
        return text

    def _recognize_structure(self, text: str, subject: str) -> StructuredQuestion:
        """识别题干、选项、题型"""
        # 识别选项模式：A. xxx B. xxx 或 A) xxx B) xxx
        option_pattern = r'[A-D][\.\)]\s*([^\n]+?)(?=[A-D][\.\)]|$)'
        options = re.findall(option_pattern, text)

        if options:
            # 有选项 → 选择题
            stem = re.split(r'[A-D][\.\)]', text)[0].strip()
            q_type = QuestionType.SINGLE_CHOICE
        elif '____' in text or '（  ）' in text or '（  ）' in text:
            # 有填空符 → 填空题
            stem = text
            q_type = QuestionType.FILL_BLANK
        else:
            # 默认简答题
            stem = text
            q_type = QuestionType.SHORT_ANSWER

        return StructuredQuestion(
            stem=stem,
            options=options if options else None,
            type=q_type,
            subject=subject or "unknown"
        )

    def _evaluate_confidence(self, text: str, structured: StructuredQuestion) -> float:
        """评估识别置信度"""
        confidence = 0.5  # 基础分

        # 题干长度合理
        if 5 <= len(structured.stem) <= 500:
            confidence += 0.2

        # 选项完整（选择题且4个选项）
        if structured.options and len(structured.options) == 4:
            confidence += 0.15

        # 包含数学符号（数学科目）
        if structured.subject == "math" and re.search(r'[∠°√∑∏∫=<>]', text):
            confidence += 0.1

        # 无明显乱码
        if not re.search(r'[^\w\s\u4e00-\u9fff（）。，；：、""''！？\-\+\*\/\=\<\>∠°√∑∏∫π]', text):
            confidence += 0.05

        return min(confidence, 1.0)

    def _ai_correction(self, text: str, subject: str) -> StructuredQuestion:
        """AI 二次纠错：调用大模型重新识别"""
        prompt = f"""请将以下 OCR 识别文本整理为结构化题目。

OCR 文本：{text}
科目：{subject or '未知'}

请返回 JSON 格式：
{{
  "stem": "题干内容",
  "options": ["选项A内容", "选项B内容", "选项C内容", "选项D内容"],
  "type": "single_choice|multi_choice|fill_blank|short_answer|essay",
  "subject": "{subject or 'unknown'}"
}}

注意：
1. 修正 OCR 可能的识别错误
2. 数学公式用标准符号表示
3. 如果没有选项，options 返回 null
"""
        # 调用 AI 大模型
        response = self.ai_client.generate(prompt)
        data = json.loads(response)
        return StructuredQuestion(**data)
```

#### Step 3：实现 API 路由

在 `api/ocr_process.py` 中实现：

```python
from fastapi import APIRouter
from services.ocr_processor import OCRProcessor
from models.schemas import OCRProcessRequest, OCRProcessResponse

router = APIRouter()

# 初始化处理器（ai_client 可选，后续注入）
ocr_processor = OCRProcessor()


@router.post("/api/internal/ai/ocr/process", response_model=OCRProcessResponse)
async def process_ocr(request: OCRProcessRequest):
    """OCR 后处理接口"""
    result = ocr_processor.process(request.rawText, request.subject)
    return result
```

#### Step 4：在 main.py 中注册路由

```python
from fastapi import FastAPI
from api.ocr_process import router as ocr_router
# ... 其他路由导入

app = FastAPI(title="LeapMind AI Service")

# 注册路由
app.include_router(ocr_router)
# ... 其他路由注册
```

### 3.4 任务 B：AI 答疑 Prompt 构建

#### 功能需求

根据 OCR 识别的题目，调用 AI 生成解答和讲解：
- 构建 Prompt：题目内容 + 科目 + 年级 + 用户知识水平
- AI 生成：答案 + 逐步解析 + 涉及知识点 + 易错点提醒
- 流式返回（SSE），用户可边生成边看

#### Step 1：实现答疑 Prompt 模板

在 `services/prompt_manager.py` 中添加：

```python
class PromptManager:
    """AI Prompt 模板管理器"""

    def build_photo_qa_prompt(self, question: dict, user_profile: dict = None) -> str:
        """构建拍照答疑 Prompt

        Args:
            question: 题目信息 {stem, options, type, subject}
            user_profile: 用户画像 {grade, weakPoints}
        """
        prompt = f"""你是一位经验丰富的教师，请解答以下题目并提供详细解析。

## 题目信息
科目：{question.get('subject', '未知')}
题干：{question['stem']}
"""
        # 添加选项
        if question.get('options'):
            prompt += "选项：\n"
            for i, opt in enumerate(question['options']):
                prompt += f"  {chr(65 + i)}. {opt}\n"

        # 注入用户画像（个性化）
        if user_profile:
            grade = user_profile.get('grade', '未知年级')
            weak_points = user_profile.get('weakPoints', [])
            prompt += f"\n## 学生信息\n年级：{grade}\n"
            if weak_points:
                weak_names = [wp.get('kpName', '') for wp in weak_points]
                prompt += f"薄弱知识点：{', '.join(weak_names)}\n"
                prompt += "（请在解析中重点关注上述薄弱知识点的讲解）\n"

        prompt += """
## 输出格式要求
请按以下结构输出，每部分用换行分隔：

### 答案
直接给出正确答案

### 解析步骤
逐步解析，每步标注步骤名

### 涉及知识点
列出本题涉及的知识点

### 易错点提醒
列出本题的常见错误点
"""
        return prompt
```

#### Step 2：实现 SSE 流式生成接口

在 `api/ai_generate_stream.py` 中（如果已有此文件则补充，否则新建）：

```python
from fastapi import APIRouter
from fastapi.responses import StreamingResponse
from services.prompt_manager import PromptManager
from services.ai_client import AIClient
import json

router = APIRouter()
prompt_manager = PromptManager()
ai_client = AIClient()


@router.post("/api/internal/ai/generate/stream")
async def generate_stream(request: dict):
    """AI 流式生成接口（SSE）

    支持场景：
    - module=explain, scene=photo_qa → 拍照答疑
    - module=explain, scene=explain_wrong → 错题讲题
    - module=lesson_prep, scene=generate → 备课生成
    """
    module = request.get("module")
    scene = request.get("scene")
    params = request.get("params", {})

    # 根据 scene 构建 Prompt
    if module == "explain" and scene == "photo_qa":
        prompt = prompt_manager.build_photo_qa_prompt(
            params.get("question", {}),
            params.get("userProfile")
        )
    elif module == "explain" and scene == "explain_wrong":
        prompt = prompt_manager.build_explain_wrong_prompt(
            params.get("question", {}),
            params.get("userAnswer", {}),
            params.get("wrongReasonTag", ""),
            params.get("knowledgePoints", []),
            params.get("userProfile")
        )
    else:
        prompt = request.get("prompt", "")

    async def event_stream():
        """SSE 事件流"""
        async for chunk in ai_client.stream_generate(prompt):
            yield f"data: {json.dumps(chunk, ensure_ascii=False)}\n\n"

    return StreamingResponse(
        event_stream(),
        media_type="text/event-stream"
    )
```

### 3.5 任务 C：AI 讲题 Prompt 构建

#### 功能需求

基于错题生成结构化讲题内容：
- **个性化 Prompt**：针对用户错误原因讲解（"因为你 XXX 错了，所以重点讲 XXX"）
- 生成结构：题目分析 → 解题步骤（3-5步） → 核心知识点 → 易错点 → 同类题推荐

#### 实现讲题 Prompt 模板

在 `services/prompt_manager.py` 中添加：

```python
class PromptManager:
    # 错误原因映射表
    WRONG_REASON_MAP = {
        "concept_unclear": "概念理解不清",
        "careless": "粗心大意",
        "formula_wrong": "公式记错或用错",
        "method_wrong": "解题方法选择错误"
    }

    def build_explain_wrong_prompt(
        self,
        question: dict,
        user_answer: dict,
        wrong_reason_tag: str,
        knowledge_points: list,
        user_profile: dict = None
    ) -> str:
        """构建错题讲题 Prompt（个性化）

        Args:
            question: 题目 {stem, options, correctAnswer}
            user_answer: 用户答案 {selected}
            wrong_reason_tag: 错误原因标签
            knowledge_points: 涉及知识点 [{id, name}]
            user_profile: 用户画像
        """
        wrong_reason = self.WRONG_REASON_MAP.get(wrong_reason_tag, "未知原因")
        kp_names = [kp.get('name', '') for kp in knowledge_points]

        prompt = f"""你是一位经验丰富的教师，学生做错了一道题，请根据学生的具体错误原因进行针对性讲解。

## 题目信息
题干：{question['stem']}
"""
        # 添加选项
        if question.get('options'):
            for i, opt in enumerate(question['options']):
                prompt += f"  {chr(65 + i)}. {opt}\n"

        prompt += f"正确答案：{question.get('correctAnswer', '未知')}\n"
        prompt += f"学生选择：{user_answer.get('selected', '未知')}\n"

        prompt += f"""
## 错误分析
错误原因：{wrong_reason}
涉及知识点：{', '.join(kp_names)}

## 讲解要求
因为学生「{wrong_reason}」，所以请重点讲解以下内容：
- 概念理解不清 → 从基础概念重新梳理，举生活实例帮助理解
- 粗心大意 → 指出容易看错的细节，强调审题技巧
- 公式记错或用错 → 对比正确公式与错误用法，说明公式推导过程
- 解题方法选择错误 → 分析为什么应该用这种方法，其他方法为什么不合适

## 输出格式
请依次输出以下 JSON 对象，每个对象一行：

1. {{"type":"overview","content":"题目整体分析"}}
2. {{"type":"step","stepNumber":1,"title":"审题分析","content":"..."}}
3. {{"type":"step","stepNumber":2,"title":"解题过程","content":"..."}}
4. {{"type":"step","stepNumber":3,"title":"得出答案","content":"..."}}
5. {{"type":"tip","content":"易错点提醒"}}
6. {{"type":"similar","content":"同类题推荐"}}
"""
        # 注入用户画像
        if user_profile and user_profile.get('weakPoints'):
            weak_names = [wp.get('kpName', '') for wp in user_profile['weakPoints']]
            prompt += f"\n注意：学生当前薄弱知识点为 {', '.join(weak_names)}，请在讲解中额外关注。\n"

        return prompt
```

### 3.6 AI Client 流式调用封装

在 `services/ai_client.py` 中（如果已有则补充，否则新建）：

```python
import json
from typing import AsyncGenerator
from openai import AsyncOpenAI


class AIClient:
    """大模型 API 调用封装"""

    def __init__(self):
        # 根据实际情况配置 API Key 和 Base URL
        self.client = AsyncOpenAI(
            api_key="your-api-key",  # 从环境变量读取
            base_url="https://api.openai.com/v1"  # 或其他兼容 API
        )

    async def stream_generate(self, prompt: str) -> AsyncGenerator[dict, None]:
        """流式调用大模型，逐块返回结构化内容"""
        response = await self.client.chat.completions.create(
            model="gpt-4o",  # 或其他模型
            messages=[{"role": "user", "content": prompt}],
            stream=True,
            temperature=0.7
        )

        buffer = ""
        async for chunk in response:
            delta = chunk.choices[0].delta.content
            if delta:
                buffer += delta
                # 尝试解析完整的 JSON 行
                while "\n" in buffer:
                    line, buffer = buffer.split("\n", 1)
                    line = line.strip()
                    if line.startswith("{") and line.endswith("}"):
                        try:
                            yield json.loads(line)
                        except json.JSONDecodeError:
                            continue

    async def generate(self, prompt: str) -> str:
        """非流式调用大模型"""
        response = await self.client.chat.completions.create(
            model="gpt-4o",
            messages=[{"role": "user", "content": prompt}],
            temperature=0.3
        )
        return response.choices[0].message.content
```

### 3.7 依赖配置

确保 `requirements.txt`（或 `pyproject.toml`）包含以下依赖：

```
fastapi
uvicorn
openai
pydantic
python-dotenv
# 根据项目实际情况补充其他依赖
```

### 3.8 测试要点

| 测试项 | 预期结果 |
|--------|---------|
| 印刷体题目识别 | 题干和选项正确分离，置信度 > 0.8 |
| 手写体+印刷体混合 | 基本结构识别正确，可能触发 AI 纠错 |
| 数学公式（∠C=90°, a²+b²=c²） | 数学符号保留正确 |
| 选择题结构识别 | 4 个选项正确分割 |
| 填空题识别 | 识别 ____ 或 （  ） 标记 |
| 答疑 Prompt 输出 | 包含答案、解析步骤、知识点、易错点 |
| 讲题个性化输出 | 针对错误原因给出针对性讲解 |
| SSE 流式返回 | 首字延迟 < 2 秒，逐步返回内容 |

---

## 第四章 M2 前端实现（3 页）

> **接口对接速查（开始开发前先确认）**
>
> | 页面 | 需要的接口 | 对接人 | 你要做什么 |
> |------|-----------|--------|-----------|
> | 拍照答疑页 | `POST /api/ocr/recognize-question` | **王圳**（M2 Java） | 找他要接口文档 |
> | 拍照答疑页 | `POST /api/explain/photo-qa` (SSE) | **王圳** / **孔维诚** | 找他们要 SSE 接口文档 |
> | 讲题页 | `GET /api/wrong-questions` | **杜恩泽**（M1 Java） | 找他要错题本接口文档 |
> | 讲题页 | `POST /api/explain/generate` (SSE) | **王圳**（M2 Java） | 找他要讲题生成接口文档 |
> | 讲题页 | `POST /api/conversation/ask` (SSE) | **周贺浚**（M7 Java 接口人） | 找他要 M7 对话接口文档（追问功能） |
> | 讲题页 | M7 ChatPanel 组件 | **孔维诚** | 找他要组件 props 定义（sceneType、context 等） |
> | 讲题历史页 | `GET /api/explain/history` | **王圳**（M2 Java） | 找他要历史查询接口文档 |
>
> ⚡ **行动顺序**：优先找**王圳**要 M2 Java 侧 4 个接口 → 找**杜恩泽**要错题本接口 → 找**周贺浚**要 M7 对话接口 → 找**孔维诚**要 ChatPanel 组件 props。接口未就绪时先用 Mock 数据开发。

### 4.1 前端项目结构

前端代码在 `aitutor-frontend/` 目录下。建议按以下结构组织你的代码：

```
aitutor-frontend/src/
├── api/                    # API 请求封装
│   ├── ocr.ts              # OCR 相关接口
│   ├── explain.ts          # 讲题相关接口
│   ├── weakPoints.ts       # 薄弱点相关接口
│   └── lessonPrep.ts       # 备课相关接口
├── components/
│   ├── m2/                 # M2 专属组件
│   │   ├── CameraCapture.tsx
│   │   ├── ImageUploader.tsx
│   │   ├── OCRResultCard.tsx
│   │   ├── QAResultPanel.tsx
│   │   ├── ExplainStepPanel.tsx
│   │   ├── StepProgress.tsx
│   │   ├── ExplainContent.tsx
│   │   ├── VoicePlayButton.tsx
│   │   ├── AskMoreButton.tsx
│   │   ├── ExplainCard.tsx
│   │   └── ExplainHistoryList.tsx
│   ├── m3/                 # M3 专属组件
│   └── m5/                 # M5 专属组件
├── pages/
│   ├── m2/
│   │   ├── PhotoQAPage.tsx
│   │   ├── ExplainPage.tsx
│   │   └── ExplainHistoryPage.tsx
│   ├── m3/
│   └── m5/
├── types/                  # TypeScript 类型定义
│   ├── m2.ts
│   ├── m3.ts
│   └── m5.ts
└── hooks/                  # 自定义 Hooks
```

### 4.2 前端开发准备

```bash
# 切换到 frontend 分支（每次开发前必须拉取最新代码！）
# 首次切换用：git checkout -b frontend --track upstream/frontend
# 后续切换直接用：git checkout frontend
git checkout frontend
git fetch upstream
git merge upstream/frontend
git push origin frontend

# 创建功能分支
git checkout -b feature/m2-frontend-photo-qa frontend

# 安装依赖
cd aitutor-frontend
npm install

# 安装你需要的额外依赖
npm install @microsoft/fetch-event-source react-markdown remark-math rehype-katex katex

# 启动开发服务器
npm run dev
# 访问 http://localhost:5173
```

### 4.3 页面一：拍照答疑页

**路由**: `/m2/photo-qa`

#### Step 1：定义类型

在 `types/m2.ts` 中：

```typescript
// OCR 识别结果
interface OCRResult {
  ocrRecordId: number;
  imageUrl: string;
  recognizedText: string;
  structuredQuestion: {
    stem: string;
    options: string[];
    type: 'single_choice' | 'multi_choice' | 'fill_blank' | 'short_answer' | 'essay';
    subject: string;
  };
  confidence: number;
}

// 答疑 SSE 流式数据块
interface QASSEChunk {
  type: 'answer' | 'step' | 'knowledge' | 'done';
  content: string;
  contentId?: number;
}
```

#### Step 2：实现 API 封装

在 `api/ocr.ts` 中：

```typescript
import { fetchEventSource } from '@microsoft/fetch-event-source';

const API_BASE = '/api';

// OCR 识别题目
export async function recognizeQuestion(
  image: File,
  subject?: string
): Promise<OCRResult> {
  const formData = new FormData();
  formData.append('image', image);
  if (subject) formData.append('subject', subject);

  const res = await fetch(`${API_BASE}/ocr/recognize-question`, {
    method: 'POST',
    body: formData,
  });
  return res.json();
}

// 拍照答疑（SSE 流式）
export async function photoQA(
  ocrRecordId: number,
  question: any,
  onMessage: (chunk: QASSEChunk) => void,
  onError?: (err: Error) => void
) {
  await fetchEventSource(`${API_BASE}/explain/photo-qa`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ ocrRecordId, question }),
    onmessage: (ev) => {
      const data: QASSEChunk = JSON.parse(ev.data);
      onMessage(data);
    },
    onerror: (err) => {
      onError?.(err);
      throw err; // 停止重试
    },
  });
}
```

#### Step 3：实现拍照组件

在 `components/m2/CameraCapture.tsx` 中：

```typescript
import { useState, useRef, useCallback } from 'react';

interface CameraCaptureProps {
  onCapture: (image: File) => void;
}

export function CameraCapture({ onCapture }: CameraCaptureProps) {
  const videoRef = useRef<HTMLVideoElement>(null);
  const [streaming, setStreaming] = useState(false);
  const streamRef = useRef<MediaStream | null>(null);

  // 开启摄像头
  const startCamera = useCallback(async () => {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        video: { facingMode: 'environment' } // 后置摄像头
      });
      streamRef.current = stream;
      if (videoRef.current) {
        videoRef.current.srcObject = stream;
      }
      setStreaming(true);
    } catch (err) {
      console.error('摄像头访问失败:', err);
    }
  }, []);

  // 拍照
  const capture = useCallback(() => {
    if (!videoRef.current) return;
    const canvas = document.createElement('canvas');
    canvas.width = videoRef.current.videoWidth;
    canvas.height = videoRef.current.videoHeight;
    const ctx = canvas.getContext('2d');
    ctx?.drawImage(videoRef.current, 0, 0);
    canvas.toBlob((blob) => {
      if (blob) {
        const file = new File([blob], 'capture.jpg', { type: 'image/jpeg' });
        onCapture(file);
      }
    }, 'image/jpeg', 0.9);
  }, [onCapture]);

  // 关闭摄像头
  const stopCamera = useCallback(() => {
    streamRef.current?.getTracks().forEach(t => t.stop());
    setStreaming(false);
  }, []);

  return (
    <div className="camera-container">
      <video ref={videoRef} autoPlay playsInline />
      {!streaming ? (
        <button onClick={startCamera}>开启摄像头</button>
      ) : (
        <>
          <button onClick={capture}>拍照</button>
          <button onClick={stopCamera}>关闭</button>
        </>
      )}
    </div>
  );
}
```

#### Step 4：实现答疑结果面板

在 `components/m2/QAResultPanel.tsx` 中：

```typescript
import { useState } from 'react';
import ReactMarkdown from 'react-markdown';
import remarkMath from 'remark-math';
import rehypeKatex from 'rehype-katex';
import { photoQA } from '../../api/ocr';

interface QAResultPanelProps {
  ocrRecordId: number;
  question: any;
}

export function QAResultPanel({ ocrRecordId, question }: QAResultPanelProps) {
  const [content, setContent] = useState<string>('');
  const [loading, setLoading] = useState(false);
  const [done, setDone] = useState(false);

  const handleGenerate = async () => {
    setLoading(true);
    setContent('');
    setDone(false);

    await photoQA(
      ocrRecordId,
      question,
      (chunk) => {
        if (chunk.type === 'done') {
          setDone(true);
          setLoading(false);
        } else {
          // 流式追加内容（打字机效果）
          setContent((prev) => prev + chunk.content + '\n\n');
        }
      },
      (err) => {
        console.error('SSE error:', err);
        setLoading(false);
      }
    );
  };

  return (
    <div className="qa-result-panel">
      <button onClick={handleGenerate} disabled={loading}>
        {loading ? 'AI 思考中...' : '开始答疑'}
      </button>

      {content && (
        <div className="qa-content">
          <ReactMarkdown
            remarkPlugins={[remarkMath]}
            rehypePlugins={[rehypeKatex]}
          >
            {content}
          </ReactMarkdown>
        </div>
      )}
    </div>
  );
}
```

#### Step 5：组装页面

在 `pages/m2/PhotoQAPage.tsx` 中：

```typescript
import { useState } from 'react';
import { CameraCapture } from '../../components/m2/CameraCapture';
import { ImageUploader } from '../../components/m2/ImageUploader';
import { OCRResultCard } from '../../components/m2/OCRResultCard';
import { QAResultPanel } from '../../components/m2/QAResultPanel';
import { recognizeQuestion } from '../../api/ocr';
import type { OCRResult } from '../../types/m2';

export default function PhotoQAPage() {
  const [ocrResult, setOcrResult] = useState<OCRResult | null>(null);
  const [loading, setLoading] = useState(false);

  const handleImage = async (image: File) => {
    setLoading(true);
    try {
      const result = await recognizeQuestion(image);
      setOcrResult(result);
    } catch (err) {
      console.error('OCR failed:', err);
    }
    setLoading(false);
  };

  return (
    <div className="photo-qa-page">
      <h1>拍照答疑</h1>

      {/* 拍照 / 上传区域 */}
      {!ocrResult && (
        <>
          <CameraCapture onCapture={handleImage} />
          <ImageUploader onUpload={handleImage} />
          {loading && <p>识别中...</p>}
        </>
      )}

      {/* OCR 结果展示 */}
      {ocrResult && (
        <>
          <OCRResultCard
            result={ocrResult}
            onEdit={(text) => setOcrResult({
              ...ocrResult,
              structuredQuestion: { ...ocrResult.structuredQuestion, stem: text }
            })}
          />
          {/* AI 答疑 */}
          <QAResultPanel
            ocrRecordId={ocrResult.ocrRecordId}
            question={ocrResult.structuredQuestion}
          />
        </>
      )}
    </div>
  );
}
```

### 4.4 页面二：讲题页

**路由**: `/m2/explain/:wrongQuestionId`

#### 组件清单与实现要点

| 组件 | 文件路径 | 实现要点 |
|------|---------|---------|
| StepProgress | `components/m2/StepProgress.tsx` | 水平步骤条，当前步骤高亮，已完成步骤打勾，可点击跳转 |
| ExplainContent | `components/m2/ExplainContent.tsx` | Markdown + KaTeX 渲染，每步独立卡片 |
| VoicePlayButton | `components/m2/VoicePlayButton.tsx` | 对接 M8 TTS 接口，播放/暂停控制 |
| AskMoreButton | `components/m2/AskMoreButton.tsx` | 展开追问面板（复用 M7 ChatPanel 组件） |

#### 讲题 API 封装

在 `api/explain.ts` 中：

```typescript
import { fetchEventSource } from '@microsoft/fetch-event-source';

export interface ExplainSSEChunk {
  type: 'overview' | 'step' | 'tip' | 'similar' | 'done';
  content: string;
  stepNumber?: number;
  title?: string;
  explainId?: number;
}

export async function generateExplain(
  params: {
    wrongQuestionId: number;
    userAnswer: any;
    correctAnswer: any;
    wrongReasonTag: string;
    knowledgePoints: any[];
  },
  onMessage: (chunk: ExplainSSEChunk) => void
) {
  await fetchEventSource('/api/explain/generate', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(params),
    onmessage: (ev) => {
      onMessage(JSON.parse(ev.data));
    },
  });
}

// 获取讲题历史
export async function getExplainHistory(page: number, size: number) {
  const res = await fetch(`/api/explain/history?page=${page}&size=${size}`);
  return res.json();
}

// 获取讲题详情（回放）
export async function getExplainDetail(explainId: number) {
  const res = await fetch(`/api/explain/${explainId}`);
  return res.json();
}

// 删除讲题记录
export async function deleteExplain(explainId: number) {
  await fetch(`/api/explain/${explainId}`, { method: 'DELETE' });
}
```

#### 语音朗读按钮实现

```typescript
// components/m2/VoicePlayButton.tsx
import { useState } from 'react';

interface VoicePlayButtonProps {
  text: string;  // 当前步骤的文本内容
}

export function VoicePlayButton({ text }: VoicePlayButtonProps) {
  const [playing, setPlaying] = useState(false);
  const audioRef = useRef<HTMLAudioElement>(null);

  const handlePlay = async () => {
    if (playing) {
      audioRef.current?.pause();
      setPlaying(false);
      return;
    }

    // 调用 M8 TTS 接口
    const res = await fetch('/api/virtual-teacher/tts', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ text, voiceType: 'default' }),
    });
    const blob = await res.blob();
    const url = URL.createObjectURL(blob);

    if (audioRef.current) {
      audioRef.current.src = url;
      audioRef.current.play();
      setPlaying(true);
    }
  };

  return (
    <>
      <button onClick={handlePlay}>
        {playing ? '暂停朗读' : '语音朗读'}
      </button>
      <audio
        ref={audioRef}
        onEnded={() => setPlaying(false)}
      />
    </>
  );
}
```

### 4.5 页面三：讲题历史页

**路由**: `/m2/explain-history`

#### 实现要点

```typescript
// pages/m2/ExplainHistoryPage.tsx
import { useState, useEffect } from 'react';
import { getExplainHistory, deleteExplain } from '../../api/explain';

export default function ExplainHistoryPage() {
  const [items, setItems] = useState([]);
  const [page, setPage] = useState(1);
  const [total, setTotal] = useState(0);

  useEffect(() => {
    loadData(page);
  }, [page]);

  const loadData = async (p: number) => {
    const data = await getExplainHistory(p, 10);
    setItems(data.items);
    setTotal(data.total);
  };

  const handleDelete = async (id: number) => {
    await deleteExplain(id);
    loadData(page); // 刷新列表
  };

  return (
    <div>
      <h1>讲题历史</h1>
      {items.length === 0 ? (
        <p>暂无讲题记录</p>
      ) : (
        items.map(item => (
          <div key={item.id} className="explain-card">
            <h3>{item.questionSummary}</h3>
            <p>{item.createdAt}</p>
            <div className="tags">
              {item.knowledgePoints?.map(kp => (
                <span key={kp.id} className="tag">{kp.name}</span>
              ))}
            </div>
            <button onClick={() => navigate(`/m2/explain/replay/${item.id}`)}>
              查看回放
            </button>
            <button onClick={() => handleDelete(item.id)}>删除</button>
          </div>
        ))
      )}
      {/* 分页 */}
      <div className="pagination">
        <button disabled={page === 1} onClick={() => setPage(p => p - 1)}>上一页</button>
        <span>{page} / {Math.ceil(total / 10)}</span>
        <button disabled={page * 10 >= total} onClick={() => setPage(p => p + 1)}>下一页</button>
      </div>
    </div>
  );
}
```

---

## 第五章 M5 前端实现（4 页）

> **接口对接速查（开始开发前先确认）**
>
> | 页面 | 需要的接口 | 对接人 | 你要做什么 |
> |------|-----------|--------|-----------|
> | 备课创建页 | `POST /api/lesson-prep/generate` (SSE) | **王佳雯**（M5 Java） | 找她要备课生成接口文档✅ |
> | 备课创建页 | `GET /api/weak-points` | **曾俊桥**（M3 Java） | 找他要薄弱点接口文档（用于针对性生成）✅ |
> | 备课内容编辑页 | `GET /api/lesson-prep/contents` | **苏杰**（M5 Java） | 找他要备课内容 CRUD 接口文档✅ |
> | 备课内容编辑页 | `POST /api/lesson-prep/generate-ppt` | **王佳雯**（M5 Java） | 找她要 PPT 生成接口文档 |
> | PPT 预览编辑页 | `GET /api/lesson-prep/templates` | **苏杰**（M5 Java） | 找他要 PPT 模板列表接口文档✅ |
> | PPT 预览编辑页 | PPTX 导出 | **王佳雯**（M5 Java） | 找她要 PPTX 导出接口文档 |
> | PPT 预览编辑页 | SlideRenderer 组件 | **孔维诚**（M4 前端） | 与他协商 SlideRenderer 组件接口定义（M4/M5 共用） |
> | 备课内容列表页 | `GET /api/lesson-prep/contents` | **苏杰**（M5 Java） | 同备课编辑页，复用同一接口✅ |
>
> ⚡ **行动顺序**：找**苏杰**要备课内容 CRUD + PPT 模板接口 → 找**王佳雯**要备课生成(SSE) + PPT 生成 + PPTX 导出接口 → 找**曾俊桥**要薄弱点查询接口 → 尽早与**孔维诚**协商 SlideRenderer 组件接口。接口未就绪时先用 Mock 数据开发。

### 5.1 页面一：备课创建页

**路由**: `/m5/lesson-prep/create`

#### 组件清单

| 组件 | 功能 |
|------|------|
| KnowledgePointSelector | 从知识树选择知识点，支持搜索和多选 |
| GoalEditor | 教学目标编辑，支持增删改，AI 自动生成建议 |
| HoursPlanner | 课时数设置 |
| StyleSelector | 风格选择（standard/detailed/interactive），薄弱点导入 |

#### 类型定义

在 `types/m5.ts` 中：

```typescript
interface LessonPrepCreateRequest {
  title: string;
  subject: string;
  grade: string;
  knowledgePointIds: number[];
  teachingGoals: string[];
  totalHours: number;
  weakPointIds?: number[];
  style: 'standard' | 'detailed' | 'interactive';
}

interface LessonPrepSSEChunk {
  type: 'outline' | 'section' | 'done';
  content: any;
  index?: number;
  prepId?: number;
}
```

#### API 封装

在 `api/lessonPrep.ts` 中：

```typescript
import { fetchEventSource } from '@microsoft/fetch-event-source';

// 生成备课内容（SSE 流式）
export async function generateLessonPrep(
  params: LessonPrepCreateRequest,
  onMessage: (chunk: LessonPrepSSEChunk) => void
) {
  await fetchEventSource('/api/lesson-prep/generate', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(params),
    onmessage: (ev) => {
      onMessage(JSON.parse(ev.data));
    },
  });
}

// 获取备课列表
export async function getLessonPrepContents(params: {
  status?: string;
  page?: number;
  size?: number;
}) {
  const res = await fetch(`/api/lesson-prep/contents?${new URLSearchParams(params as any)}`);
  return res.json();
}

// 获取备课详情
export async function getLessonPrepDetail(prepId: number) {
  const res = await fetch(`/api/lesson-prep/contents/${prepId}`);
  return res.json();
}

// 保存备课内容
export async function saveLessonPrep(prepId: number, data: any) {
  await fetch(`/api/lesson-prep/contents/${prepId}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data),
  });
}

// 导出 PPT
export async function exportPpt(prepId: number) {
  const res = await fetch(`/api/lesson-prep/contents/${prepId}/export-ppt`, {
    method: 'POST',
  });
  const blob = await res.blob();
  // 触发下载
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `lesson-prep-${prepId}.pptx`;
  a.click();
  URL.revokeObjectURL(url);
}
```

### 5.2 页面二：备课内容编辑页

**路由**: `/m5/lesson-prep/edit/:prepId`

#### 实现要点

- **左侧大纲树**（OutlineEditor）：课时 → 教学环节，支持展开/折叠、拖拽排序
- **右侧内容编辑器**（SectionEditor）：富文本编辑，每环节含教学目标、重难点、教学过程、板书、课后作业
- **富文本编辑器**（RichTextEditor）：推荐使用 `@tiptap/react`

```bash
# 安装富文本编辑器依赖
npm install @tiptap/react @tiptap/starter-kit @tiptap/extension-image @tiptap/extension-link
```

### 5.3 页面三：PPT 预览与编辑页

**路由**: `/m5/ppt-editor/:pptId`

#### 组件清单

| 组件 | 功能 |
|------|------|
| SlideThumbnailList | 左侧缩略图列表，拖拽排序（@dnd-kit/core），增删页 |
| SlideCanvas | 中间 16:9 Canvas 预览，支持点击编辑内容块 |
| StylePanel | 右侧样式面板：模板选择、配色、字体、布局 |
| SlideRenderer | 共享渲染组件（与 M4 共用），支持 preview/edit/present 模式 |

#### 安装拖拽依赖

```bash
npm install @dnd-kit/core @dnd-kit/sortable @dnd-kit/utilities
```

#### PPT 结构类型

```typescript
interface PPTSlide {
  id: string;
  title: string;
  contentBlocks: ContentBlock[];
  layout: 'title' | 'content' | 'split' | 'image-text';
  notes?: string;
}

interface ContentBlock {
  type: 'text' | 'image' | 'formula' | 'interactive';
  content: string;
  position?: { x: number; y: number; w: number; h: number };
}
```

#### SlideRenderer 共享组件

```typescript
// components/shared/SlideRenderer/index.tsx
interface SlideRendererProps {
  slide: PPTSlide;
  styleConfig?: StyleConfig;
  mode: 'preview' | 'edit' | 'present';
  onSlideChange?: (slide: PPTSlide) => void;
}

export function SlideRenderer({ slide, styleConfig, mode, onSlideChange }: SlideRendererProps) {
  return (
    <div
      className="slide-container"
      style={{
        aspectRatio: '16/9',
        background: styleConfig?.backgroundColor || '#ffffff',
        position: 'relative',
      }}
    >
      <h2>{slide.title}</h2>
      {slide.contentBlocks.map((block, i) => (
        <div key={i} className={`content-block ${block.type}`}>
          {block.type === 'text' && <p>{block.content}</p>}
          {block.type === 'formula' && (
            <span className="katex-formula">{block.content}</span>
          )}
          {block.type === 'image' && <img src={block.content} alt="" />}
        </div>
      ))}
    </div>
  );
}
```

> **注意**：SlideRenderer 组件需要与 M4 前端负责人（孔维诚）协商接口定义，确保两边共用。

### 5.4 页面四：备课内容列表页

**路由**: `/m5/lesson-prep`

#### 实现要点

- 卡片网格布局展示备课内容
- 筛选（按状态、按科目）
- "新建备课"按钮 → 跳转创建页
- 每张卡片操作菜单：编辑、预览 PPT、导出、删除、"用于讲课"（跳转 M4）

```typescript
// pages/m5/LessonPrepListPage.tsx
import { useState, useEffect } from 'react';
import { getLessonPrepContents } from '../../api/lessonPrep';

export default function LessonPrepListPage() {
  const [items, setItems] = useState([]);
  const [page, setPage] = useState(1);

  useEffect(() => {
    getLessonPrepContents({ page, size: 12 }).then(data => setItems(data.items));
  }, [page]);

  return (
    <div className="lesson-prep-list">
      <div className="header">
        <h1>我的备课</h1>
        <button onClick={() => navigate('/m5/lesson-prep/create')}>新建备课</button>
      </div>

      <div className="card-grid">
        {items.map(item => (
          <div key={item.id} className="prep-card">
            <h3>{item.title}</h3>
            <p>科目：{item.subject} | 课时：{item.totalHours}</p>
            <p>PPT 页数：{item.pptSlideCount || 0}</p>
            <span>{new Date(item.createdAt).toLocaleDateString()}</span>
            <div className="actions">
              <button onClick={() => navigate(`/m5/lesson-prep/edit/${item.id}`)}>编辑</button>
              <button onClick={() => navigate(`/m5/ppt-editor/${item.pptId}`)}>PPT</button>
              <button onClick={() => navigate(`/m4/teaching/${item.id}`)}>讲课</button>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
```

---

## 第六章 M3 前端实现（3 页）

> **接口对接速查（开始开发前先确认）**
>
> | 页面 | 需要的接口 | 对接人 | 你要做什么 |
> |------|-----------|--------|-----------|
> | 薄弱点列表页 | `GET /api/weak-points` | **曾俊桥**（M3 Java） | 找他要薄弱点列表接口文档 |
> | 薄弱点详情页 | `GET /api/weak-points/recommend-questions` | **曾俊桥**（M3 Java） | 找他要推荐练习接口文档 |
> | 知识图谱页 | `GET /api/weak-points/knowledge-graph` | **曾俊桥**（M3 Java） | 找他要知识图谱接口文档 |
>
> ⚡ **行动顺序**：M3 全部 3 个接口都找**曾俊桥**要。如他尚未给出，可先找 M3 接口人**刘金华**确认接口进度。接口未就绪时先用 Mock 数据开发，M3 页面依赖较少，可优先开发知识图谱页（纯数据可视化）。

### 6.1 页面一：薄弱点列表页

**路由**: `/m3/weak-points`

#### 类型定义

在 `types/m3.ts` 中：

```typescript
interface WeakPoint {
  kpId: number;
  kpName: string;
  weaknessScore: number;       // 0-1，越高越薄弱
  errorCount: number;
  totalAttempts: number;
  errorRate: number;
  trend: 'improving' | 'stable' | 'declining';
  lastErrorAt: string;
  subWeakPoints?: { kpId: number; kpName: string; weaknessScore: number }[];
}

interface WeakPointListResponse {
  userId: number;
  weakPoints: WeakPoint[];
  updatedAt: string;
  overallAssessment: string;
}
```

#### API 封装

在 `api/weakPoints.ts` 中：

```typescript
export async function getWeakPoints(topN: number = 10): Promise<WeakPointListResponse> {
  const res = await fetch(`/api/weak-points?topN=${topN}`);
  return res.json();
}

export async function getWeakPointDetail(kpId: number) {
  const res = await fetch(`/api/weak-points/${kpId}/detail`);
  return res.json();
}

export async function getKnowledgeGraph() {
  const res = await fetch('/api/weak-points/knowledge-graph');
  return res.json();
}
```

#### 薄弱点卡片实现

```typescript
// components/m3/WeakPointCard.tsx
import type { WeakPoint } from '../../types/m3';

const TREND_CONFIG = {
  improving: { icon: '↑', color: '#52c41a', label: '改善中' },
  stable:    { icon: '→', color: '#8c8c8c', label: '稳定' },
  declining: { icon: '↓', color: '#ff4d4f', label: '退步' },
};

export function WeakPointCard({ wp }: { wp: WeakPoint }) {
  const trend = TREND_CONFIG[wp.trend];
  // 薄弱等级颜色：>0.7 红 / >0.4 黄 / <0.4 绿
  const levelColor = wp.weaknessScore > 0.7 ? '#ff4d4f' :
                      wp.weaknessScore > 0.4 ? '#faad14' : '#52c41a';

  return (
    <div className="weak-point-card">
      <div className="header">
        <h3>{wp.kpName}</h3>
        <span style={{ color: trend.color }}>{trend.icon} {trend.label}</span>
      </div>
      {/* 薄弱等级进度条 */}
      <div className="progress-bar">
        <div
          className="progress-fill"
          style={{ width: `${wp.weaknessScore * 100}%`, background: levelColor }}
        />
      </div>
      <div className="stats">
        <span>错题：{wp.errorCount}/{wp.totalAttempts}</span>
        <span>错误率：{(wp.errorRate * 100).toFixed(0)}%</span>
      </div>
      <button onClick={() => navigate(`/m3/weak-points/${wp.kpId}`)}>查看详情</button>
      <button onClick={() => navigate(`/m1/practice?kpId=${wp.kpId}`)}>去练习</button>
    </div>
  );
}
```

### 6.2 页面二：薄弱点详情页

**路由**: `/m3/weak-points/:kpId`

#### 实现要点

- 趋势折线图（ECharts LineChart）：展示薄弱分变化曲线
- 错题预览列表：点击跳转 M2 讲题页
- 局部知识图谱：ECharts 力导向图展示前置/后置知识点关系

```typescript
// 趋势图实现
import * as echarts from 'echarts';
import { useEffect, useRef } from 'react';

function WeaknessTrendChart({ data }: { data: { date: string; score: number }[] }) {
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!ref.current) return;
    const chart = echarts.init(ref.current);
    chart.setOption({
      tooltip: { trigger: 'axis' },
      xAxis: {
        type: 'category',
        data: data.map(d => d.date),
      },
      yAxis: { type: 'value', min: 0, max: 1 },
      series: [{
        type: 'line',
        data: data.map(d => d.score),
        smooth: true,
        areaStyle: { opacity: 0.3 },
      }],
    });
    return () => chart.dispose();
  }, [data]);

  return <div ref={ref} style={{ width: '100%', height: '300px' }} />;
}
```

### 6.3 页面三：知识图谱页

**路由**: `/m3/knowledge-graph`

#### ECharts 力导向图实现

```typescript
// components/m3/KnowledgeGraphCanvas.tsx
import * as echarts from 'echarts';
import { useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';

interface KnowledgeGraphData {
  nodes: {
    id: number;
    name: string;
    mastery: number;       // 0-1 掌握度
    isWeakPoint: boolean;
  }[];
  links: {
    source: number;
    target: number;
    relation: string;
  }[];
}

export function KnowledgeGraphCanvas({ data }: { data: KnowledgeGraphData }) {
  const chartRef = useRef<HTMLDivElement>(null);
  const navigate = useNavigate();

  useEffect(() => {
    if (!chartRef.current) return;
    const chart = echarts.init(chartRef.current);

    // 根据掌握度映射颜色
    const getColor = (mastery: number) => {
      if (mastery > 0.8) return '#52c41a';  // 绿=掌握
      if (mastery > 0.5) return '#faad14';  // 黄=一般
      if (mastery > 0)   return '#ff4d4f';  // 红=薄弱
      return '#d9d9d9';                      // 灰=未学习
    };

    chart.setOption({
      tooltip: {
        formatter: (params: any) => {
          if (params.dataType === 'node') {
            return `${params.data.name}<br/>掌握度: ${(params.data.mastery * 100).toFixed(0)}%`;
          }
        },
      },
      series: [{
        type: 'graph',
        layout: 'force',
        roam: true,
        force: {
          repulsion: 200,
          edgeLength: 100,
          gravity: 0.1,
        },
        label: { show: true, fontSize: 12 },
        data: data.nodes.map(node => ({
          id: String(node.id),
          name: node.name,
          mastery: node.mastery,
          symbolSize: node.isWeakPoint ? 40 : 25,
          itemStyle: { color: getColor(node.mastery) },
        })),
        links: data.links.map(link => ({
          source: String(link.source),
          target: String(link.target),
        })),
        emphasis: { focus: 'adjacency' },
      }],
    });

    // 单击节点 → 跳转薄弱点详情
    chart.on('click', (params: any) => {
      if (params.dataType === 'node') {
        navigate(`/m3/weak-points/${params.data.id}`);
      }
    });

    // 双击节点 → 跳转做题
    chart.on('dblclick', (params: any) => {
      if (params.dataType === 'node') {
        navigate(`/m1/practice?kpId=${params.data.id}`);
      }
    });

    const handleResize = () => chart.resize();
    window.addEventListener('resize', handleResize);
    return () => {
      chart.dispose();
      window.removeEventListener('resize', handleResize);
    };
  }, [data, navigate]);

  return (
    <div>
      <div ref={chartRef} style={{ width: '100%', height: '600px' }} />
      {/* 图例 */}
      <div className="legend">
        <span style={{ color: '#52c41a' }}>● 掌握</span>
        <span style={{ color: '#faad14' }}>● 一般</span>
        <span style={{ color: '#ff4d4f' }}>● 薄弱</span>
        <span style={{ color: '#d9d9d9' }}>● 未学习</span>
      </div>
    </div>
  );
}
```

---

## 第七章 依赖协作与验收标准

### 7.1 前置依赖清单

开发前需要确认以下依赖是否就绪。如果未就绪，前端先用 Mock 数据开发。

| 依赖项 | 提供者 | 说明 | Mock 策略 |
|--------|--------|------|----------|
| Python AI 微服务框架 | 第 0 批 | FastAPI + AI 调用层基础 | — |
| M1 错题本接口 | M1 后端 | `GET /api/wrong-questions` | Mock 错题数据 |
| M6 用户画像接口 | M6 后端 | `GET /api/user-profile/{userId}/summary` | Mock 画像数据 |
| M3 薄弱点数据接口 | M3 后端 | `GET /api/weak-points` | Mock 薄弱点数据 |
| M7 ChatPanel 组件 | 孔维诚 | 共享对话组件 | 先用简单输入框 |
| M8 TTS 接口 | 欧俊濠 | `POST /api/virtual-teacher/tts` | 隐藏语音按钮 |
| SlideRenderer 组件 | 与孔维诚协商 | M4/M5 共用 PPT 渲染 | 先独立实现 |

### 7.2 你提供给其他模块的接口

| 接口 | 消费者 | 说明 |
|------|--------|------|
| `POST /api/internal/ai/ocr/process` | Java M2 后端 | OCR 后处理 |
| `POST /api/internal/ai/generate/stream`（答疑场景） | Java M2 后端 | AI 答疑流式生成 |
| `POST /api/internal/ai/generate/stream`（讲题场景） | Java M2 后端 | AI 讲题流式生成 |
| M2 前端页面 | 全平台 | 拍照答疑/讲题/历史 |
| M3 前端页面 | 全平台 | 薄弱点列表/详情/知识图谱 |
| M5 前端页面 | 全平台 | 备课创建/编辑/PPT/列表 |

### 7.3 各模块验收标准

#### M2 Python AI 后端

| 验收项 | 标准 |
|--------|------|
| OCR 识别准确率 | 印刷体题目识别率 > 95%，支持数学公式 |
| AI 答疑质量 | 解答步骤清晰、知识点标注正确、结论正确 |
| 流式讲题体验 | SSE 流式返回，首字延迟 < 2 秒 |
| 错题讲解个性化 | 讲解针对用户具体错误原因，非通用答案 |
| 讲解可回放 | 历史讲解可重新查看，内容完整 |

#### M2 前端

| 验收项 | 标准 |
|--------|------|
| 拍照答疑页 | 拍照/上传 → OCR 结果 → AI 答疑流式输出，全流程无阻断 |
| 讲题页 | 步骤导航、语音朗读、追问功能可用，Markdown + KaTeX 正确渲染 |
| 讲题历史页 | 列表展示、回放、删除功能正常 |

#### M5 前端

| 验收项 | 标准 |
|--------|------|
| 备课生成 | 输入知识点 → 生成完整教案 |
| PPT 生成 | 备课内容 → 可用 PPT 结构（>=8 页） |
| PPT 可编辑 | 增删改幻灯片页、调整顺序 |
| 导出功能 | 可导出为 PPTX 文件 |
| 与 M4 联动 | 备课 PPT 可在即时讲课中使用 |

#### M3 前端

| 验收项 | 标准 |
|--------|------|
| 薄弱点列表 | Top5 薄弱点与实际错题分布一致 |
| 知识图谱 | 知识点关联关系、掌握度颜色正确 |
| 推荐练习 | 推荐题目与薄弱知识点匹配 |

### 7.4 联调注意事项

1. **Mock 数据先行**：后端接口未就绪时，前端先用 Mock 数据开发和测试 UI，接口就绪后切换
2. **SSE 调试**：开发时可以用 `curl` 测试 SSE 接口：`curl -N -X POST http://localhost:8000/api/internal/ai/generate/stream -H "Content-Type: application/json" -d '{"module":"explain","scene":"photo_qa","params":{...}}'`
3. **跨域问题**：前端 (5173) 调用后端 (8080) 需要配置 Vite 代理。在 `vite.config.ts` 中：
   ```typescript
   export default defineConfig({
     server: {
       proxy: {
         '/api': {
           target: 'http://localhost:8080',
           changeOrigin: true,
         },
       },
     },
   });
   ```
4. **Python 服务仅内部调用**：前端不直接访问 Python 服务（端口 8000），所有请求通过 Java 后端转发
5. **SlideRenderer 协调**：尽早与孔维诚（M4）协商 SlideRenderer 组件接口定义，避免后期返工

### 7.5 建议开发顺序

1. **先做 M2 Python AI 后端**（OCR 后处理 → 答疑 Prompt → 讲题 Prompt）
   - 这是你所有前端页面的数据来源
   - 先确保 API 能返回数据，前端才能联调

2. **再做 M2 前端**（拍照答疑页 → 讲题页 → 讲题历史页）
   - 拍照答疑页是入口，优先完成
   - 讲题页依赖答疑 Prompt，可复用组件

3. **然后做 M3 前端**（薄弱点列表 → 薄弱点详情 → 知识图谱）
   - 难度较低，主要是列表和数据可视化
   - 知识图谱页可独立开发，依赖较少

4. **最后做 M5 前端**（备课创建 → 备课编辑 → PPT 编辑 → 备课列表）
   - 复杂度最高，涉及富文本编辑器和 PPT 渲染
   - PPT 编辑页需要与 M4 协调 SlideRenderer

### 7.6 接口需求矩阵（你需要哪些接口 + 找谁要文档）

> 以下按你的 **4 大任务板块** 逐一列出：每个板块/页面需要调用哪些接口、接口路径、以及**找谁要接口文档**。

#### 7.6.1 M2 Python AI 后端（你自己开发的 3 个接口）

这部分是你**提供给别人**的接口，不需要找别人要文档，但你的接口依赖以下数据源：

| 你需要的数据 | 数据来源 | 接口/方式 | 找谁要文档 | 用途 |
|-------------|---------|----------|-----------|------|
| 用户画像摘要 | M6 后端 | `GET /api/user-profile/{userId}/summary` | **张梓鸿**（M6 Java，事件采集+画像查询API）或 **张伟涛**（M6 Python 接口人） | 注入 AI Prompt，实现个性化答疑/讲题 |
| 错题列表 | M1 后端 | `GET /api/wrong-questions?userId={userId}&status=unresolved` | **杜恩泽**（M1 Java，错题本+练习统计） | 讲题时获取用户错题，生成针对性讲解 |
| Python AI 微服务框架 | 第 0 批基础设施 | FastAPI + AI 调用层 | 确认是否已搭建，如未搭建需先完成 | 你的 OCR/答疑/讲题接口都跑在这个框架上 |

**你提供给别人的接口（别人会找你要文档）：**

| 你的接口 | 消费者 | 找你要文档的人 | 说明 |
|---------|--------|--------------|------|
| `POST /api/internal/ai/ocr/process` | Java M2 后端 | **王圳**（M2 Java，图片接收存储）、**孔维诚**（M2 Java，SSE 透传） | OCR 文本去噪、题干/选项/公式结构识别 |
| `POST /api/internal/ai/generate/stream`（答疑场景） | Java M2 后端 | **王圳**、**孔维诚** | AI 答疑流式生成（SSE） |
| `POST /api/internal/ai/generate/stream`（讲题场景） | Java M2 后端 | **王圳**、**孔维诚** | AI 讲题流式生成（SSE） |

> **行动项**：主动把以上 3 个 Python 接口的入参/返回值/SSE 事件格式文档发给王圳和孔维诚，他们需要知道怎么调你的接口。

---

#### 7.6.2 M2 前端（3 个页面）

##### 拍照答疑页

| 需要的接口 | 方法 | 找谁要文档 | 说明 |
|-----------|------|-----------|------|
| `POST /api/ocr/recognize-question` | POST | **王圳**（M2 Java） | 上传图片 → 返回 OCR 识别结果（Java 接收图片 → 调你的 Python OCR 接口） |
| `POST /api/explain/photo-qa` | POST (SSE) | **王圳** / **孔维诚**（M2 Java SSE 透传） | 拍照答疑流式输出（Java 透传 Python SSE） |

##### 讲题页

| 需要的接口 | 方法 | 找谁要文档 | 说明 |
|-----------|------|-----------|------|
| `GET /api/wrong-questions?userId={userId}&status=unresolved` | GET | **杜恩泽**（M1 Java） | 获取待讲解的错题列表 |
| `POST /api/explain/generate` | POST (SSE) | **王圳**（M2 Java） | 生成讲题内容（流式输出） |
| `POST /api/conversation/ask` | POST (SSE) | **周贺浚**（M7 Java 接口人） | 讲题中追问，走 M7 对话链路 |
| M7 ChatPanel 组件 | 组件 props | **孔维诚**（M7 ChatPanel 共享组件） | 嵌入讲题页的对话组件，需确认 props 接口（sceneType、context 等） |

##### 讲题历史页

| 需要的接口 | 方法 | 找谁要文档 | 说明 |
|-----------|------|-----------|------|
| `GET /api/explain/history` | GET | **王圳**（M2 Java） | 讲题历史列表查询/删除 |

> **行动项**：找 **王圳** 要 M2 Java 侧全部 4 个接口文档（recognize-question、photo-qa、generate、history），找 **杜恩泽** 要错题本接口文档，找 **周贺浚** 要 M7 对话接口文档，找 **孔维诚** 要 ChatPanel 组件 props 定义。

---

#### 7.6.3 M5 前端（4 个页面）

##### 备课创建页

| 需要的接口 | 方法 | 找谁要文档 | 说明 |
|-----------|------|-----------|------|
| `POST /api/lesson-prep/generate` | POST (SSE) | **王佳雯**（M5 Java，SSE 透传 + TTS） | 生成备课内容（流式输出） |
| `GET /api/weak-points` | GET | **曾俊桥**（M3 Java） | 获取薄弱点列表，用于备课内容针对性生成 |

##### 备课内容编辑页

| 需要的接口 | 方法 | 找谁要文档 | 说明 |
|-----------|------|-----------|------|
| `GET /api/lesson-prep/contents` | GET | **苏杰**（M5 Java，备课内容 CRUD） | 获取备课内容列表/详情 |
| `POST /api/lesson-prep/generate-ppt` | POST | **王佳雯**（M5 Java） | 备课内容 → PPT 结构生成 |

##### PPT 预览编辑页

| 需要的接口 | 方法 | 找谁要文档 | 说明 |
|-----------|------|-----------|------|
| `GET /api/lesson-prep/templates` | GET | **苏杰**（M5 Java，PPT 模板管理） | PPT 模板列表（系统默认 + 用户自定义） |
| PPTX 导出 | POST | **王佳雯**（M5 Java，PPTX 导出） | 导出为 .pptx 文件（Apache POI → MinIO → 下载 URL） |
| SlideRenderer 组件 | 组件 props | **孔维诚**（M4 前端） | M4/M5 共用 PPT 渲染组件，需协商组件接口定义 |

##### 备课内容列表页

| 需要的接口 | 方法 | 找谁要文档 | 说明 |
|-----------|------|-----------|------|
| `GET /api/lesson-prep/contents` | GET | **苏杰**（M5 Java） | 备课内容列表（分页、筛选、状态流转） |

> **行动项**：找 **苏杰** 要备课内容 CRUD + PPT 模板接口文档，找 **王佳雯** 要备课生成(SSE) + PPT 生成 + PPTX 导出接口文档，找 **曾俊桥** 要薄弱点查询接口文档，找 **孔维诚** 协商 SlideRenderer 组件接口。

---

#### 7.6.4 M3 前端（3 个页面）

##### 薄弱点列表页

| 需要的接口 | 方法 | 找谁要文档 | 说明 |
|-----------|------|-----------|------|
| `GET /api/weak-points` | GET | **曾俊桥**（M3 Java） | 获取用户薄弱点列表（Top N，含薄弱度分数、趋势） |

##### 薄弱点详情页

| 需要的接口 | 方法 | 找谁要文档 | 说明 |
|-----------|------|-----------|------|
| `GET /api/weak-points/recommend-questions` | GET | **曾俊桥**（M3 Java） | 根据薄弱点推荐练习题（去重、优先已解决错题） |

##### 知识图谱页

| 需要的接口 | 方法 | 找谁要文档 | 说明 |
|-----------|------|-----------|------|
| `GET /api/weak-points/knowledge-graph` | GET | **曾俊桥**（M3 Java） | 知识图谱数据（知识点关联关系、掌握度，用于 ECharts 力导向图） |

> **行动项**：找 **曾俊桥** 要 M3 全部 3 个接口文档（weak-points、recommend-questions、knowledge-graph）。如曾俊桥尚未给出，可先找 M3 接口人 **刘金华** 确认接口进度。

---

#### 7.6.5 接口文档对接联系人速查表

| 姓名 | 模块 | 角色 | 你需要找他要什么 |
|------|------|------|----------------|
| **王圳** | M2 | Java（图片接收+讲题 CRUD） | M2 Java 侧 4 个接口文档（recognize-question、photo-qa、generate、history） |
| **孔维诚** | M2/M4/M7 | Java SSE 透传 + 前端 | ChatPanel 组件 props 定义、SlideRenderer 组件接口、SSE 透传格式确认 |
| **杜恩泽** | M1 | Java（错题本+统计） | `GET /api/wrong-questions` 错题本接口文档 |
| **曾俊桥** | M3 | Java + Python（查询/推荐 API） | M3 全部 3 个接口文档（weak-points、recommend-questions、knowledge-graph） |
| **刘金华** | M3 | Python 接口人 | M3 接口进度确认（如曾俊桥未响应） |
| **苏杰** | M5 | Java（备课内容 CRUD+模板） | 备课内容 CRUD 接口、PPT 模板列表接口文档 |
| **王佳雯** | M5 | Java（SSE 透传+PPTX 导出+TTS） | 备课生成(SSE)、PPT 生成、PPTX 导出接口文档 |
| **周贺浚** | M7 | Java 接口人 | `POST /api/conversation/ask` 对话接口文档 |
| **张梓鸿** | M6 | Java（事件采集+画像查询） | `GET /api/user-profile/{userId}/summary` 画像摘要接口文档 |
| **张伟涛** | M6 | Python 接口人 | M6 画像数据格式确认（如需深入对接） |
| **欧俊濠** | M8 | Java + 前端 | `POST /api/virtual-teacher/tts` TTS 接口文档（M5 备课页可能用到语音预览） |

> **沟通建议**：
> 1. 在团队群里统一发起接口文档请求，格式：`@XXX 你好，我需要 M2 拍照答疑页的 POST /api/ocr/recognize-question 接口文档，包括入参、返回值、错误码，麻烦发一下`
> 2. 收到文档后对照本矩阵检查字段是否完整，有遗漏及时反馈
> 3. 接口未就绪时，按 7.1 节的 Mock 策略先用 Mock 数据开发，接口就绪后切换
> 4. **优先级**：M2 Java 接口（王圳）> M1 错题本（杜恩泽）> M3 接口（曾俊桥）> M5 Java 接口（苏杰/王佳雯）> M7 对话（周贺浚）> M6 画像（张梓鸿）

---

> 本指南基于项目任务文档和分工表编写。如果项目结构有变动或接口定义有调整，请以实际代码仓库为准。开发过程中遇到问题，及时在团队群沟通。
