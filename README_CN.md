# ApiMocktle

一个 IntelliJ IDEA 插件，从源码提取 API 并同步到 YAPI。

## 功能

### API 导出到 YAPI

- 从 Java / Kotlin / Scala / Groovy 源码解析 API 端点
- 使用**个人令牌**认证，无需为每个模块单独配置
- 导出时下拉选择目标项目，支持搜索过滤
- 自动创建分类、检测重复接口、更新确认

### API 仪表盘

内置工具窗口，浏览、搜索、调用 API：

- 按模块和类浏览接口树
- 搜索接口路径、名称、方法
- 查看参数、请求头、请求体详情
- 直接从仪表盘发送 HTTP 请求
- 单击导航至源码

### 发送 API 请求

- 右键控制器方法 → **调用**（`Alt+Shift+C`）
- 编辑参数后发送，查看带语法高亮的响应

### API 全局搜索

双击 Shift → APIs 标签 → 按方法前缀（`GET /users`）或关键字搜索。

### 字段转换

- 类字段 → JSON / JSON5 / Properties

## 支持的框架

| 类别 | 支持 |
|------|------|
| 语言 | Java、Kotlin、Scala、Groovy |
| Web 框架 | Spring MVC、Spring Cloud OpenFeign、JAX-RS |
| RPC | gRPC |
| 校验 | javax.validation / Jakarta Validation |
| 序列化 | Jackson、Gson |
| API 注解 | Swagger 2 / OpenAPI 3 |

## 安装

1. 从 `build/distributions/` 获取 `easy-yapi-x.x.x.zip`
2. IDEA → Settings → Plugins → 齿轮 → Install Plugin from Disk
3. 选择 zip 文件，重启 IDEA

**兼容性**：IntelliJ IDEA 2023.3+ / JDK 17+

## 使用方法

### 导出到 YAPI

1. **配置个人令牌**：Settings → ApiMocktle → YAPI → 输入服务器地址和个人令牌
2. 右键控制器文件 / 类 / 方法 → **导出到YAPI**（`Alt+Shift+E`）
3. 在弹出的项目选择框中选择目标项目
4. API 自动同步

### 调用 API

右键控制器方法 → **调用**（`Alt+Shift+C`），编辑参数后发送。

### 打开仪表盘

Tools → 打开API仪表盘，或点击底部 **API Dashboard** 标签。

## 配置

插件使用分层配置系统：

| 优先级 | 来源 | 说明 |
|--------|------|------|
| 最高 | 本地文件 | 项目根目录 `.easy.api.config` |
| | 扩展 | 内置扩展配置 |
| | 远程 | 从 URL 加载的配置 |
| 最低 | 内置 | 默认配置 |

支持 Groovy 脚本、正则表达式、注解表达式等规则引擎语法。

## 开发

### 环境要求

- JDK 17+
- IntelliJ IDEA 2023.3+

### 构建

```bash
# 编译
./gradlew clean buildPlugin

# 运行 IDEA 实例
./gradlew runIde

# 运行测试
./gradlew clean test
```

## 架构

```
IDE 层 (Actions, Dashboard, Line Markers, Search)
    ↓
导出层 (ExportOrchestrator → ClassExporter → YapiExporter)
    ↓
核心服务 (RuleEngine, ConfigReader, ApiIndex, HttpClient)
    ↓
PSI 分析 (TypeResolver, DocHelper, AnnotationHelper)
```

- **ClassExporter** — 从 PSI 类提取 `ApiEndpoint` 模型
- **YapiExporter** — 格式化、上传至 YAPI
- **ExportOrchestrator** — 协调扫描到导出的完整流程
- **RuleEngine** — 评估规则表达式，自定义解析行为
