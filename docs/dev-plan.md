# [开发实现] idea-answer 插件内嵌答题站工具窗口

> 计划类型：需求开发实现（[开发实现]）
> 关联：未提供
> 影响范围：新增 `settings.gradle`、`build.gradle`、`gradle.properties`、`gradlew`、`gradlew.bat`、`gradle/wrapper/*`、`.gitignore`、`src/main/resources/META-INF/plugin.xml`、`src/main/resources/icons/answer.svg`、`src/main/java/com/github/codex/answer/{AnswerSite,AnswerNavigationPolicy,AnswerBrowserPanel,AnswerToolWindowFactory}.java`、`src/test/java/com/github/codex/answer/AnswerNavigationPolicyTest.java`；设计依据 `docs/tech-design.md`
> 执行边界：可改文件，仅限上述新增文件与 `docs/` 下文档；不改 `README.md`；不执行 git 提交
> 风险等级：中
> 工作流 ID：AW-ANSWER-IMPL-20261114-EMBED-ANSWER-PANEL-FEA2251E
> 工作流 UUID：fea2251e-ef64-4d14-b704-eff8666e656d

## 目标与验收口径

**目标**：在 IDEA 右侧工具窗口内嵌 `https://gin.hk.cn/answer/dashboard`，登录后可直接答题；会话由 IDE 托管的 JCEF profile 保持；不改站点、不调用其接口。范围按已确认的最小内嵌执行：无下载处理、无工具栏、无快捷键转发。

**验收口径**：

| 编号 | 操作路径 | 通过标准 |
|---|---|---|
| AC-1 | `.\gradlew.bat test` | `AnswerNavigationPolicyTest` 4 个用例全部通过，无失败与跳过 |
| AC-2 | `.\gradlew.bat buildPlugin` | 生成 `build/distributions/idea-answer-0.1.0.zip`，内含 `Answer/lib/*.jar`、`Answer/META-INF/plugin.xml`、`Answer/icons/answer.svg` |
| AC-3 | 安装 zip 到 IC 2024.2.4 → 重启 → `View → Tool Windows → Answer` | 右侧出现 Answer 工具窗口，点击后加载站点；`idea.log` 无 JCEF 初始化异常 |
| AC-4 | 站点内登录 → 进入 `/answer/dashboard` | dashboard 完整渲染；重启 IDE 后打开仍为已登录状态 |
| AC-5 | 依次点击“开始答题”“学习统计”“历史记录” | 地址保持在 `/answer/**`，未跳转到系统浏览器 |
| AC-6 | 页面内触发站外 https 链接（如 `https://example.com`） | 内嵌页不离开 `/answer`；系统浏览器打开该地址 |
| AC-7 | 以 `-Dide.browser.jcef.enabled=false` 启动 | 显示“当前 IDEA 运行环境不支持内嵌浏览器。”与“在默认浏览器中打开 Answer System”按钮 |
| AC-8 | 关闭项目后退出 IDE | `idea.log` 无异常堆栈；任务管理器无该 IDE 残留的 Chromium 子进程 |

## 现状与影响面

- 本仓库：`README.md:1` 仅有 `# idea-answer` 一行；无构建脚本、无源码、无自定义代码区标记、无 `.gitignore`。git 仅一次提交 `fdd73d6`。
- 参考实现（只读，不在本仓库）：
  - `D:\soft\codex\flowoss-idea\build.gradle:1-3` 插件声明与 `2.10.5` 版本；`:25-33` IDE 依赖；`:43-46` `sinceBuild/untilBuild`；`:48-55` 编码与 `useJUnit()`
  - `D:\soft\codex\flowoss-idea\src\main\java\com\github\codex\flowoss\FlowOssToolWindowFactory.java:24-27` 创建 Content 并绑定 disposer
  - `...\FlowOssBrowserPanel.java:56-67` `JBCefApp.isSupported()` 与 `loadURL`；`:99-127` 请求拦截；`:85-92` 释放
  - `...\FlowOssNavigationPolicy.java:15` 首页常量；`:27-34` 同源判定；`:43-55` 外链边界
  - `D:\soft\codex\flowoss-idea\src\main\resources\META-INF\plugin.xml:11-17` toolWindow 注册
- 站点事实（证据）：`https://gin.hk.cn/answer/dashboard` 返回 SPA 骨架；bundle `https://gin.hk.cn/answer/assets/index-Ct5xSlUV.js` 内 `var Vn="/answer/api"`、请求使用 `credentials:"include"`；`GET /answer/api/auth/session` 匿名返回 `{"authenticated":false,"user":null,"initialized":true}`；`https://gin.hk.cn/` 是另一个页面（1457 字节），因此内嵌范围按 `/answer` 前缀限定。
- 影响面：仅新增插件工程与文档；不触碰站点、不改 IDE 现有插件、无上下游模块、无数据库。

## 方案设计

- **启动链**：`plugin.xml` 注册工具窗口 → 用户首次激活 → `AnswerToolWindowFactory.createToolWindowContent` → `AnswerBrowserPanel` 构造 → `JBCefBrowser` → `loadURL(AnswerSite.HOME)`。
- **导航链**：`CefRequestHandlerAdapter.onBeforeBrowse` → `AnswerNavigationPolicy.isAnswerPage` 命中则放行；未命中时 `externalHttpsTarget` 非空则 `BrowserUtil.browse` 交给系统浏览器；两条分支都 `return true` 取消内嵌跳转。
- **生命周期**：浏览器只在工具窗口内容创建时构造一次；`Content.setDisposer(panel::dispose)` 在项目关闭或插件卸载时释放 Chromium 与 Swing 组件。
- **降级**：`JBCefApp.isSupported()` 为 false 时不构造浏览器，改为说明文案 + 外部打开按钮，保证工具窗口仍可用。
- **关键取舍**：沿用参考插件的 `JBCefBrowser` + 请求拦截 + Content disposer 结构；不注入 JS、不读取页面 DOM、不调用站点接口；登录与数据全部由页面自身完成，Cookie 由 JCEF profile 持久化。
- **类职责边界**：站点常量（`AnswerSite`）与 URL 判定（`AnswerNavigationPolicy`）保持纯 Java，便于单测；Swing/JCEF 组装集中在 `AnswerBrowserPanel`；注册与生命周期集中在工厂类。

## 改动清单

| 文件 | 区域 | 变更类型 | 要点 |
|---|---|---|---|
| `settings.gradle` | 新增文件 | 新增 | `rootProject.name = 'idea-answer'`；`pluginManagement.repositories` 含 gradlePluginPortal、mavenCentral |
| `build.gradle` | 新增文件 | 新增 | `java` + `org.jetbrains.intellij.platform 2.10.5`；group `com.github.codex`、version `0.1.0`；Java 17 toolchain；仓库 aliyun public、mavenCentral、`intellijPlatform.defaultRepositories()`；依赖 `intellijIdeaCommunity('2024.2.4')` 与 `junit:junit:4.13.2`；`patchPluginXml` 设 `sinceBuild='233'`、`untilBuild='263.*'`；UTF-8 与 `release 17`；`useJUnit()`；不启用 `pluginVerifier`、`instrumentationTools` |
| `gradle.properties` | 新增文件 | 新增 | `org.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8`、parallel、caching |
| `gradlew`、`gradlew.bat`、`gradle/wrapper/gradle-wrapper.jar`、`gradle/wrapper/gradle-wrapper.properties` | 新增文件 | 新增 | 从 `D:\soft\codex\flowoss-idea` 原样复制，distributionUrl 保持 `gradle-8.14.4-bin` |
| `.gitignore` | 新增文件 | 新增 | `.gradle/`、`build/`、`.idea/`、`*.iml`、`out/` |
| `src/main/resources/META-INF/plugin.xml` | 新增文件 | 新增 | id `com.github.codex.answer-idea`、name `Answer System`、vendor `Codex`、version `0.1.0`、`idea-version since-build="233" until-build="263.*"`；`depends` 仅 `com.intellij.modules.platform`；`toolWindow id="Answer"`、`anchor="right"`、`factoryClass="com.github.codex.answer.AnswerToolWindowFactory"`、`icon="/icons/answer.svg"`、`canCloseContents="false"` |
| `src/main/resources/icons/answer.svg` | 新增文件 | 新增 | 16×16 viewBox；`#B23A2E` 印章方框描边 + 简化“答”笔画；仅用 `path`/`rect`/`circle`，不使用 `text` |
| `src/main/java/com/github/codex/answer/AnswerSite.java` | 新增文件 | 新增 | `HOST="gin.hk.cn"`、`PATH_PREFIX="/answer"`、`HOME=URI.create("https://gin.hk.cn/answer/dashboard")`，私有构造 |
| `.../AnswerNavigationPolicy.java` | 新增文件 | 新增 | `isAnswerPage(URI)`：https + host 忽略大小写 + 端口 -1/443 + 无 userInfo + path 为 `/answer` 或以 `/answer/` 开头；`externalHttpsTarget(String)`：仅绝对 https 且非本站 `/answer`，解析失败返回 null |
| `.../AnswerBrowserPanel.java` | 新增文件 | 新增 | `JPanel`(BorderLayout)；`JBCefApp.isSupported()` 分支；构造 `JBCefBrowser`；`JBCefClient.addRequestHandler` 安装 `onBeforeBrowse`；`loadURL(HOME)`；降级面板；`dispose()` 释放浏览器 |
| `.../AnswerToolWindowFactory.java` | 新增文件 | 新增 | `implements ToolWindowFactory, DumbAware`；`createToolWindowContent` 创建 Content、`setDisposer(panel::dispose)`、`addContent` |
| `src/test/java/com/github/codex/answer/AnswerNavigationPolicyTest.java` | 新增文件 | 新增 | 4 个用例：内嵌范围、外链范围、非法协议/相对路径/畸形地址、`/answer` 前缀边界 |
| `docs/tech-design.md` | 新增文件 | 新增（本轮已落地） | 技术方案与决策依据，不参与插件构建 |
| `docs/dev-plan.md` | 新增文件 | 新增（本文件） | 开发实现计划 |

## 数据与接口变更

**无**。不新增或修改站点接口、DTO、数据库表、插件配置项；插件不调用 `/answer/api`，全部请求由页面自身发起。`plugin.xml` 的 `toolWindow` 扩展点属插件本地注册，不是对外契约。兼容策略：站点接口或路由变化不影响插件编译，只影响页面可用性（R-4）。

## 兼容、回滚与发布顺序

- **发布顺序**：`test` → `buildPlugin` → 安装 zip → 重启 IDE → 手工验收。单一构件，无前后端与数据库先后问题。
- **兼容**：`since-build=233`、`until-build=263.*`；仅依赖 `com.intellij.modules.platform`，IC 与 IU 通用；JCEF 需 IDE 运行时支持，本机 IC 2024.2.4 已核实 `JBCefApp`、`JBCefBrowser`、`CefDownloadHandlerAdapter` 存在。
- **回滚**：Settings → Plugins → Uninstall，或删除 `<IDE>/plugins/answer` 目录后重启；无插件配置与数据残留；如需清除 IDE 内登录态，关闭 IDE 后删除 JCEF cache（`ide.browser.jcef.cache.path`，默认在 IDE system 目录），不影响系统浏览器。
- **失败止损**：安装后若工具窗口异常，先卸载插件恢复 IDE；站点侧问题先用系统浏览器复现，按归属决定是否改 `AnswerSite`，不在本轮扩大改动。

## 测试与验证

**静态检查（默认执行）**：
- 核对包名、类名、`factoryClass`、`icon` 路径与文件实际位置一致。
- 核对 `AnswerNavigationPolicy` 不引用任何 IDE 类，`AnswerNavigationPolicyTest` 可直接在 JVM 运行。
- 核对 `.gitignore` 覆盖 `.gradle/` 与 `build/`，避免构建产物进入工作区。
- `git status` 只包含本清单内的新增文件，`README.md` 无改动。

**需授权执行**：
- `.\gradlew.bat test`（对应 AC-1）。
- `.\gradlew.bat buildPlugin`（对应 AC-2）。
- 安装到 `D:\soft\IDEAC2024\IntelliJ IDEA Community Edition 2024.2.4` 后手工执行 AC-3 至 AC-8。
- 失败处理：IDE 依赖下载失败时改用 `intellijPlatform { local("D:/soft/IDEAC2024/IntelliJ IDEA Community Edition 2024.2.4") }`，或按参考实现 `build/manual-classes` + `build/plugin-stage` 手工打包；运行期失败按 R-2、R-3 处置。

**明确不执行**：
- 不发布 Marketplace、不做 `pluginVerifier` 多版本矩阵（本轮不含签名与上架）。
- 不做站点接口联调（插件不调用接口）。
- 不修改站点代码，不引入下载处理器、工具栏、快捷键转发。

## 执行边界

1. **自定义区判定**：本仓库无自定义代码区标记（`README.md:1` 仅一行标题），本轮全部为新建文件，不存在区域外改动。`README.md` 与参考仓库 `D:\soft\codex\flowoss-idea` 均不改动。
2. **落地声明（7 字段）**：

| 文件 | 区域 | 锚点/行号 | 变更类型 | 摘要 | 业务逻辑承载位置 | 区域外影响 |
|---|---|---|---|---|---|---|
| `build.gradle` | 新增文件 | 第 1-55 行 | 新增 | 声明 IntelliJ Platform 插件、Java 17、仓库与 IDE 依赖 | 无业务逻辑，仅构建配置 | 无 |
| `settings.gradle` | 新增文件 | 第 1 行 | 新增 | 工程名与插件仓库 | 无 | 无 |
| `gradle.properties` | 新增文件 | 第 1-3 行 | 新增 | Gradle JVM 与缓存参数 | 无 | 无 |
| `gradlew`、`gradlew.bat`、`gradle/wrapper/*` | 新增文件 | 整文件复制 | 新增 | 固定 Gradle 8.14.4 | 无 | 无 |
| `.gitignore` | 新增文件 | 第 1-5 行 | 新增 | 忽略构建与 IDE 本地文件 | 无 | 无 |
| `plugin.xml` | 新增文件 | 第 1-18 行 | 新增 | 插件身份、平台依赖、工具窗口注册 | 无 | 无 |
| `answer.svg` | 新增文件 | 整文件 | 新增 | 16×16 工具窗口图标 | 无 | 无 |
| `AnswerSite.java` | 新增文件 | 整文件 | 新增 | 站点地址与路径前缀常量 | 同文件常量 | 无 |
| `AnswerNavigationPolicy.java` | 新增文件 | 整文件 | 新增 | 内嵌与外链的 URL 判定 | 同文件两个静态方法 | 无 |
| `AnswerBrowserPanel.java` | 新增文件 | 整文件 | 新增 | JCEF 浏览器组装、导航拦截、降级与释放 | 同文件私有方法 | 无 |
| `AnswerToolWindowFactory.java` | 新增文件 | 整文件 | 新增 | 工具窗口内容创建与生命周期绑定 | 同文件唯一公共方法 | 无 |
| `AnswerNavigationPolicyTest.java` | 新增文件 | 整文件 | 新增 | URL 边界单测 | 测试类 | 无 |
| `docs/tech-design.md`、`docs/dev-plan.md` | 新增文件 | 整文件 | 新增 | 设计与实现计划 | 无 | 无 |

3. **拆分策略**：站点常量、URL 策略、浏览器面板、工具窗口工厂各自独立文件；`AnswerBrowserPanel` 只做组装与生命周期，不承载业务规则；后续下载处理、工具栏、快捷键转发以独立类挂接，不塞进工厂。
4. **注释交付**：`AnswerSite` 类与常量 JavaDoc（含义、来源、修改点）；`AnswerNavigationPolicy` 类与两个公共方法 JavaDoc（参数、返回、拒绝条件）及分支行注释；`AnswerBrowserPanel` 类与构造方法 JavaDoc（职责、JCEF 判定原因、生命周期）、拦截回调与降级分支行注释；`AnswerToolWindowFactory` 类与方法 JavaDoc（项目、窗口参数、disposer 绑定原因）；测试类与每个测试方法 JavaDoc（边界意图）。注释必须出现在最终 diff；不注释显而易见的 getter、简单赋值与框架样板。
5. **验证授权与 diff 审查**：默认只做静态检查；编译、单测、打包、安装属需授权项，先说明原因由用户决定。完成后执行 `git status` 与 `git diff`，确认只有清单内新增文件、无区域外改动；越界立即回滚。

## 风险与应对

- **R-1 下载不落盘**：触发＝点击“导出题库/下载模板”；影响＝文件不生成（最小范围无下载处理器，IC 2024.2.4 无默认 JCEF 下载实现）；应对＝本版改用系统浏览器打开同站点完成导入导出；后续以独立 `AnswerDownloadHandler`（`JBCefClient.addDownloadHandler` + `CefDownloadHandlerAdapter` + 保存对话框）挂接，不改现有结构。
- **R-2 IDE 截获页面快捷键**（A–D、Enter、Space、S、Alt+1–4）：触发＝实机按键无响应；影响＝只能鼠标答题；应对＝按 flowoss 的 `IdeEventQueue` 预处理器方案新增独立类，仅在工具窗口激活且页面非输入态时转发按键，不改页面与 IDE 键位映射。
- **R-3 JCEF 不可用或被禁用**：触发＝`JBCefApp.isSupported()` 为 false；影响＝无法内嵌；应对＝降级面板 + 外部浏览器入口，不阻塞 IDE。
- **R-4 站点更新**：触发＝路由、登录方式或 CSP 变化；影响＝页面打不开或登录态失效；应对＝插件不依赖页面结构，只改 `AnswerSite` 与策略；`/answer` 外跳被交给系统浏览器即为可观察信号。
- **R-5 行为采集与自动暂停**：站点记录 focus/blur/visibility，并在页面隐藏时自动暂停计时；影响＝IDE 内切换窗口可能触发暂停与行为记录；应对＝不注入脚本、不伪造可见性，考试期间保持工具窗口在前台。
- **R-6 多项目内存占用**：每项目一个 Chromium 实例；影响＝多项目同时打开时内存上升；应对＝不做跨项目复用（避免会话串扰），项目关闭即释放。
- **R-7 登录态与系统浏览器隔离**：JCEF profile 独立；影响＝需在 IDE 内登录一次；应对＝文档提示，不导出 Cookie。
- **R-8 代理与证书**：JCEF 走 IDE 代理设置；影响＝企业代理下可能显示证书或连接错误页；应对＝由用户在 IDE 设置代理，插件不绕过校验。
- **R-9 构建依赖下载失败**：触发＝JetBrains 仓库或 Gradle 插件仓库不可达；影响＝`buildPlugin` 失败；应对＝改用本地 IDE 路径依赖，或按参考实现手工编译并组装插件目录后压缩。
- **R-10 `until-build` 失效**：触发＝IDE 升级到 263 之后；影响＝插件被标记不兼容；应对＝升级时同步调整 `patchPluginXml` 并重新验证 AC-3 至 AC-8。
