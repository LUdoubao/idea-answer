# [技术方案] idea-answer 内嵌答题站插件设计

> 计划类型：技术方案/架构设计（[技术方案]）
> 关联：未提供
> 影响范围：全部为新增文件，仓库当前仅 `README.md`（D:\workspace\doubao\idea-answer）
> 执行边界：仅方案设计；实现落点见 `docs/dev-plan.md`
> 风险等级：中

实现计划：`docs/dev-plan.md`（工作流 `AW-ANSWER-IMPL-20261114-EMBED-ANSWER-PANEL-FEA2251E`）。

## 问题与目标

**问题**：仓库为空，需要从零建立一个 IDEA 插件，把 `https://gin.hk.cn/answer/dashboard` 内嵌到 IDE 中并可正常登录、答题，而不是每次切换到系统浏览器。

**目标**：
- 在 IDEA 右侧工具窗口内嵌该站点，登录会话在 IDE 内持续有效。
- 站点自身功能（登录、组卷、考试、历史、统计）不经插件改造即可使用。
- 结构与参考实现 `D:\soft\codex\flowoss-idea` 一致，便于沿用其构建、安装与验证经验。

**约束**：
- 基线对齐参考插件：IC 2024.2.4、Java 17、IntelliJ Platform Gradle Plugin 2.10.5、`since-build=233` / `until-build=263.*`。
- 本机仅 `D:\soft\IDEAC2024\IntelliJ IDEA Community Edition 2024.2.4`（242.23726.103）可运行验证；`D:\soft\IDEA\IntelliJ IDEA 2026.1` 目录为空，无法作为验证目标。
- 本轮范围取“最小内嵌”：不含下载处理、工具栏、快捷键转发。
- 默认不编译、不构建、不发布。

**已核实事实**（证据来自站点资源与本地 IDE 安装）：

| 事实 | 证据 |
|---|---|
| 站点是 Vite + React SPA，入口 `/answer/assets/index-Ct5xSlUV.js` | `GET /answer/dashboard` 返回 200，`<div id="root">` + module 脚本 |
| API 基址 `/answer/api`，Cookie 会话（`credentials: include`） | bundle 中 `var Vn="/answer/api"`；请求以 `${Hn}${e}` 拼接地址并带 `credentials:"include"`；`GET /answer/api/auth/session` 匿名返回 `{"authenticated":false,...}` |
| 根路径 `https://gin.hk.cn/` 是另一个应用（1457 字节） | 与 `/answer`（1512 字节）内容长度与入口不同，必须按路径前缀限定内嵌范围 |
| 导出/模板下载依赖 `a[download]` + Blob | bundle 中 `URL.createObjectURL` + `i.download`；`href:"./templates/questions-template.csv",download:!0` |
| IC 2024.2.4 无 `JBCefDownloadHandler`，但有 `JBCefClient.addDownloadHandler(CefDownloadHandler, CefBrowser)` 与 `org.cef.handler.CefDownloadHandlerAdapter` | `javap` 检查 `lib/app-client.jar`、`lib/lib-client.jar` |
| 无 `window.open`、无 WebSocket/EventSource/ServiceWorker | bundle 关键字计数为 0；仅 `navigator.sendBeacon` |
| 窄栏可用：620px / 980px 响应式断点 | `/answer/assets/index-B66wQPfU.css` 的 `@media (width<=620px)` / `@media (width<=980px)` |

## 候选方案对比

| 方案 | 优点 | 代价 | 风险 | 结论 |
|---|---|---|---|---|
| A. JCEF 工具窗口（IDE 内置 Chromium） | 真正内嵌；复用 IDE 托管 profile，Cookie 持久；与参考插件同构，代码量最小 | 依赖 JCEF 运行时；占用 Chromium 进程内存 | JCEF 被禁用/裁剪时不可用 | 采用 |
| B. 插件只做入口，系统浏览器打开 | 零运行时风险，体验等同浏览器 | 不满足“嵌入”；与 IDE 割裂 | 无 | 否，仅作为 A 的降级路径 |
| C. 编辑器标签内嵌（FileEditor + 虚拟文件） | 宽度大、可与项目标签共存 | 需实现 `FileEditor`/`VirtualFile`/恢复逻辑，生命周期与 Content 释放更复杂 | 与项目文件标签混排、关闭语义复杂 | 否（后续可选演进） |
| D. 自绘 Swing / JavaFX WebView | 无 JCEF 依赖 | 不支持 ES Module、现代 CSS、`fetch` 同源会话 | 页面基本无法渲染 | 否，技术不可行 |

## 推荐方案与理由

采用 **方案 A**，产出 4 个 Java 类 + 1 个单测 + 描述符 + 图标：

- **满足“嵌入 + 正常使用”**：JCEF 是 IDE 自带 Chromium，页面按顶层文档加载，不受 `X-Frame-Options` 限制，Cookie/`localStorage` 由 IDE 托管的 JCEF profile 持久化。
- **对齐参考经验**：`JBCefApp.isSupported()` 兜底、`JBCefClient` 请求拦截、`Content.setDisposer` 释放 Chromium、无状态策略类 + 单测，均为 flowoss 已验证做法。
- **范围最小**：不做工具栏、注入脚本、接口调用；页面功能完全由站点提供。
- **可扩展**：下载处理、工具栏、快捷键转发都能以独立类挂到 `AnswerBrowserPanel`，不改描述符与工具窗口工厂。

### 可落地性评估

- 仓库当前只有 `README.md`（28 字节，git 单次提交），无自定义代码区、无生成代码、无模板托管代码，本轮全部为新增文件，不触发 P0“区域外零变更”约束。
- `README.md` 不改动；后续若需写入说明，另行确认。
- 实现阶段按 P0.5 补齐：每个新类/公共方法/字段的中文 JavaDoc（职责、参数、返回、生命周期），方法体内关键步骤写意图注释，不在 import 区加无关注释。

## 架构与数据流

### 组件职责

| 文件 | 职责 |
|---|---|
| `src/main/resources/META-INF/plugin.xml` | 插件元信息；依赖 `com.intellij.modules.platform`；注册右侧工具窗口 `Answer`，`canCloseContents=false`，图标 `/icons/answer.svg` |
| `AnswerToolWindowFactory.java` | `ToolWindowFactory` + `DumbAware`；首次激活创建内容；把 `panel::dispose` 绑到 `Content` 生命周期 |
| `AnswerBrowserPanel.java` | `JBCefApp.isSupported()` 判断；创建 `JBCefBrowser`；安装导航策略；`loadURL(HOME)`；不支持时展示降级面板；`dispose()` 释放浏览器 |
| `AnswerNavigationPolicy.java` | 无状态 URL 边界：`/answer` 前缀留在内嵌；其余 https 交给系统浏览器；http/相对/畸形地址一律拒绝 |
| `AnswerSite.java` | 站点常量：`HOME`、host、路径前缀，集中修改点 |
| `AnswerBrowserPanel` 内 fallback | 说明文案 + “在默认浏览器中打开”按钮（`BrowserUtil.browse`） |
| `AnswerNavigationPolicyTest.java` | 纯 Java 单测，不依赖 IDE 运行时可执行 |

### 调用链与时序

1. IDE 启动 → 读取 `plugin.xml` → 注册工具窗口，**不创建浏览器**（懒加载）。
2. 用户点击 Answer → `createToolWindowContent`。
3. `JBCefApp.isSupported()`：
   - `true` → `new JBCefBrowser()`，组件加入 `BorderLayout.CENTER`，安装 `CefRequestHandlerAdapter.onBeforeBrowse`，`loadURL(AnswerSite.HOME)`。
   - `false` → 降级面板，只保留外部浏览器入口。
4. 拦截规则：`isAnswerPage(uri)` 为真放行；否则 `externalHttpsTarget(url)` 非空则 `BrowserUtil.browse`；其余一律 `return true` 取消内嵌导航。
5. 项目关闭 / 插件卸载 → `Content` disposer → `browser.dispose()`。

### 数据流与存储

- 页面与静态资源：JCEF → `https://gin.hk.cn/answer/**`（同源，插件不介入）。
- 登录会话：`POST /answer/api/auth/login` → 服务器 `Set-Cookie` → JCEF 托管 profile（`ide.browser.jcef.cache.path`，默认在 IDE system 目录）→ 后续 `credentials: include` 自动带 Cookie。
- 页面状态：答题进度、侧边栏折叠（`as.v1.sidebar`）存于页面 `localStorage`，同样落在 JCEF profile。
- 插件侧：不写配置、不读 DOM、不代理请求，无自有存储。

## 接口与数据模型变更

**无**。不新增或修改站点接口、DTO、数据库表；插件不调用 `/answer/api`，所有请求由页面自身发起。站点接口或路由变更不影响插件编译，只表现为页面可用性变化（见 R-4）。

## 非功能设计

- **启动与性能**：懒创建，不拖慢 IDE 启动；每项目 1 个浏览器，工具窗口隐藏不销毁（保留登录与考试状态），项目关闭即释放；常驻内存与 Chromium 进程同量级（预期 100–300MB 级别，非本次实测值）。内存不足时的降级：只保留一个项目打开，或卸载插件。
- **安全**：内嵌范围固定为 `https://gin.hk.cn` + `/answer` 前缀；只允许 https 外链且交给系统浏览器；不注入 JS、不读页面内容、不保存密码；不开启远程调试；凭据全部由 IDE 的 JCEF profile 管理。
- **可用性**：JCEF 不可用 → 降级面板 + 外部打开；站点不可达 → Chromium 错误页，可用 JCEF 默认右键菜单刷新（不需插件代码）。
- **可观测性**：不自建日志、不埋点；运行时异常由 IDE 日志承载。
- **扩展性**：下载处理、工具栏、快捷键转发均作为独立类挂接，不改描述符与工厂。

## 迁移、兼容与回退

**兼容**：`since-build=233`、`until-build=263.*`，依赖 `com.intellij.modules.platform`，IC/IU 通用；JCEF 需 IDE 运行时支持，本机 2024.2.4 已确认 `JBCefApp`/`JBCefBrowser`/`CefDownloadHandlerAdapter` 存在。无存量数据、无旧版本插件、无数据库与配置迁移。

**回退**：Settings → Plugins → Uninstall，或删除 `<IDE>/plugins/answer` 目录；无插件配置残留；如需清除 IDE 内登录态，关闭 IDE 后删除 JCEF cache 目录（由 `ide.browser.jcef.cache.path` 决定，默认在 IDE system 目录），不影响系统浏览器。

**验证方法（需授权执行，默认不执行）**：

| 步骤 | 通过标准 | 失败处理 |
|---|---|---|
| `gradlew.bat test` | `AnswerNavigationPolicyTest` 4 项通过 | 检查 JDK17 与 Gradle wrapper 是否可用 |
| `gradlew.bat buildPlugin` | 产出 `build/distributions/idea-answer-0.1.0.zip` | 依赖下载失败时改用本地 IDE 路径，或按 flowoss `build/manual-classes` + `plugin-stage` 手工打包 |
| 安装 zip 到 IC 2024.2.4 并启动 | 右侧出现 Answer，点击后加载站点 | 用 `idea.log` 排查 JCEF 初始化错误 |
| 登录并进入 dashboard | 页面完整渲染，登录成功后重启 IDE 仍为已登录 | 检查 Cookie 是否被 IDE 代理/证书拦截 |
| 切到编辑器再切回 | 页面状态保留，不重新登录 | 检查是否误销毁了浏览器实例 |
| 关闭项目、退出 IDE | 无异常日志，退出后无残留 Chromium 进程 | 检查 `dispose()` 是否绑定到 `Content` |
| 禁用 JCEF（Registry `ide.browser.jcef.enabled=false`）启动 | 出现降级面板与外部打开按钮 | 检查 `JBCefApp.isSupported()` 判空分支 |

## 决策记录

- **D-1 内嵌技术选 JCEF**：需求是“嵌入”且要保留会话；代价是依赖 IDE 自带 Chromium，不可用时降级。否决 JavaFX WebView（无法渲染该 SPA）。
- **D-2 形态为右侧停靠工具窗口**：站点在 980px 有响应式断点，窄栏可用；IDE 允许用户拖动为浮窗或改停靠位置，无需插件代码。
- **D-3 会话复用 IDE 托管 JCEF profile**：不自管 Cookie/Token，登录一次长期有效；代价是与系统浏览器不共享会话。
- **D-4 导航边界按路径前缀 `/answer`**：根路径是另一个应用，整域内嵌会把无关站点带进 IDE；站点若迁出该前缀，只需改 `AnswerSite`。
- **D-5 范围取最小内嵌**：不含下载处理、工具栏、快捷键转发；代价见 R-1、R-2。
- **D-6 身份与坐标**：id `com.github.codex.answer-idea`，name `Answer System`，vendor `Codex`，version `0.1.0`，包名 `com.github.codex.answer`，工具窗口 id `Answer`。
- **D-7 构建基线**：IntelliJ Platform Gradle Plugin 2.10.5 + IC 2024.2.4 + Gradle 8.14.4 wrapper + Java 17；不启用 `pluginVerifier`/`instrumentationTools`（当前范围用不到，减少下载）。
- **D-8 工厂实现 `DumbAware`**：页面与项目索引无关，索引期间也应可用。

## 风险与应对

- **R-1 下载不落盘**：触发＝点击“导出题库/下载模板”；影响＝文件不生成（最小范围不含下载处理器，IC 2024.2.4 无默认 JCEF 下载实现）；应对＝本版改用系统浏览器打开同一站点完成导入导出；后续版本以独立 `AnswerDownloadHandler`（`JBCefClient.addDownloadHandler` + `CefDownloadHandlerAdapter` + 保存对话框 + 通知）挂接，不改动现有结构。
- **R-2 IDE 截获页面快捷键**（A–D、Enter、Space、S、Alt+1–4）：触发＝实机按键无响应；影响＝只能鼠标答题；应对＝按 flowoss 同款 `IdeEventQueue` 预处理器新增独立类，仅在工具窗口激活且页面处于非输入态时转发按键，不改页面、不改 IDE 键位映射。
- **R-3 JCEF 不可用或被禁用**：影响＝无法内嵌；应对＝降级面板 + 外部浏览器，不阻塞 IDE 启动。
- **R-4 站点更新**（路由、登录方式、CSP 变化）：影响＝页面打不开或登录态失效；应对＝插件不依赖页面结构，`AnswerSite` 与策略集中可改；`/answer` 外链接被交给系统浏览器即为可观察信号。
- **R-5 行为采集与自动暂停差异**：站点记录 focus/blur/visibility，并在 `visibilityState=hidden` 时自动暂停计时；IDE 内切换窗口可能触发暂停与行为记录；应对＝不注入脚本、不伪造可见性，考试时保持工具窗口在前台。
- **R-6 多项目内存占用**：每项目一个 Chromium 实例；应对＝不做跨项目复用（避免会话串扰），项目关闭即释放。
- **R-7 登录态与系统浏览器隔离**：需在 IDE 内登录一次；应对＝说明文档提示，不导出 Cookie。
- **R-8 代理与证书**：JCEF 走 IDE 代理设置，企业代理下证书错误显示错误页；应对＝由用户在 IDE 设置代理，插件不绕过校验。
