# 知序云

知序云是面向高校教学场景的前后端分离项目。当前目录是重构后的新项目根目录，源项目 `xiangmu` 保持不变。

目录：`clients/web` 网页端，`server` 后端 API，`contracts` 接口契约，`infrastructure` 部署配置，`docs` 文档，`scripts/jcodemunch_server.py` 本地只读代码索引 MCP。

## 本地启动

后端使用 H2 文件数据库，首次启动只会自动建立表结构，不会创建任何用户或业务演示数据：

```powershell
cd server
mvn package
java -jar target/zhixuyun-server-0.1.0.jar
```

前端：

```powershell
cd clients/web
npm install
npm run dev
```

默认地址：前端 `http://localhost:5173`，后端 `http://localhost:8080`。若端口已占用，可通过 Vite 的 `--port` 参数调整前端端口。

本地数据库位于 `server/.local/`，该目录不会提交到版本库。新数据库需要由运维人员直接写入首个管理员账户；之后的教师和学生账户均由管理员端创建。教师端后续新上传的资料文件默认保存到 `server/.local/resource-files/`；如需切到服务器目录，可通过环境变量 `ZHIXUYUN_RESOURCE_STORAGE_BASE_DIR` 覆盖。

AI 教学助手与学生提交批改均通过服务端的 OpenAI 兼容客户端访问模型，并通过环境变量配置地址、模型和密钥。

## Kimi AI 配置

学生提交文本报告或 `.txt`、`.md`、`.docx` 文件后，后端会将任务要求和报告正文发送给 Kimi 生成分数与评语。启动后端前配置：

```powershell
$env:ZHIXUYUN_AI_API_KEY="你的 Kimi API Key"
$env:ZHIXUYUN_AI_BASE_URL="https://api.moonshot.cn/v1"
$env:ZHIXUYUN_AI_MODEL="kimi-k2.6"
$env:ZHIXUYUN_AI_TEMPERATURE="1"
$env:ZHIXUYUN_AI_TIMEOUT="180s"
$env:ZHIXUYUN_AI_MAX_TOKENS="4096"
```

模型调用失败或返回无效评分时，本次提交不会覆盖数据库中的旧报告和旧评分。API Key 不应写入项目文件。

项目内 `.mcp.json` 注册了只读 `jcodemunch` 服务，支持仓库结构、文件树、符号、文本搜索、引用和影响范围分析。
