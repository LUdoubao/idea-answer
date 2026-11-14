package com.github.codex.answer;

import com.intellij.ide.BrowserUtil;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import com.intellij.ui.jcef.JBCefApp;
import com.intellij.ui.jcef.JBCefBrowser;
import com.intellij.ui.jcef.JBCefClient;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.handler.CefRequestHandlerAdapter;
import org.cef.network.CefRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.net.URI;

/**
 * Answer System 工具窗口的浏览器容器，负责 JCEF 浏览器组装、导航边界安装与资源释放。
 *
 * <p>实例生命周期与工具窗口内容一致：由 {@link AnswerToolWindowFactory} 创建，并由
 * {@code Content} 的 disposer 在项目关闭或插件卸载时调用 {@link #dispose()}。浏览器在实例化时按项目记忆的
 * 模式加载 Web 或移动端首页，站点自身的登录态与页面数据全部由 IDE 托管的 JCEF profile 保存，本类不读写任何凭据。</p>
 *
 * <p>支持 JCEF 时，浏览器上方挂 {@link AnswerBrowserToolbar}：刷新保持当前地址，切换在当前路径上互换
 * {@code /answer} 与 {@code /answer/m} 并记忆到项目。运行时不支持 JCEF 的平台（部分受限发行版或禁用了浏览器的环境）
 * 不会构造浏览器与工具条，改为展示说明与外部打开入口，保证工具窗口仍可用而不影响 IDE 启动。</p>
 */
public final class AnswerBrowserPanel {

    /** 工具窗口根组件；无论是否支持 JCEF，都返回同一实例，便于工厂直接挂载。 */
    private final JPanel component = new JPanel(new BorderLayout());

    /** JCEF 浏览器；当前运行时不支持 JCEF 时为 {@code null}，用于区分是否需要释放原生资源。 */
    private final JBCefBrowser browser;

    /** 浏览器上方操作条；JCEF 不可用时为 {@code null}，刷新与切换都以此判断是否可用。 */
    private final AnswerBrowserToolbar toolbar;

    /** 当前项目；用于读写项目级的展示模式记忆，不参与浏览器导航。 */
    private final Project project;

    /** 当前展示模式；由切换按钮改变，是按钮文案与下次打开加载地址的唯一依据。 */
    private AnswerDisplayMode mode;

    /**
     * 按当前运行时能力创建内嵌浏览器或降级面板。
     *
     * <p>必须在构造内完成 {@link JBCefApp#isSupported()} 判断：不支持时创建 JCEF 组件会直接抛错，
     * 而不是返回一个不可用的浏览器。</p>
     *
     * @param project 持有该工具窗口的项目；用于读取与保存展示模式记忆
     */
    public AnswerBrowserPanel(@NotNull Project project) {
        this.project = project;
        // 先读记忆再决定初始地址：首次打开或记忆值非法时默认移动端。
        this.mode = AnswerDisplayMode.load(PropertiesComponent.getInstance(project));

        // JCEF 依赖 IDE 自带的 Chromium 运行时，部分发行版或禁用浏览器的环境不提供，先判断再构造。
        if (JBCefApp.isSupported()) {
            browser = new JBCefBrowser();
            // 导航策略必须在首次加载前安装，否则登录跳转或站外链接可能先被内嵌浏览器处理。
            installNavigationPolicy(browser);
            toolbar = new AnswerBrowserToolbar(this::reload, this::toggleMode);
            toolbar.setMode(mode);
            component.add(toolbar, BorderLayout.NORTH);
            component.add(browser.getComponent(), BorderLayout.CENTER);
            // 首页由站点自行决定是否跳转登录页；此处只负责打开对应模式的入口地址。
            browser.loadURL(homeUrl(mode).toString());
        } else {
            browser = null;
            toolbar = null;
            installUnsupportedMessage();
        }
    }

    /** 返回挂载到工具窗口内容的 Swing 根组件。 */
    @NotNull
    public JComponent getComponent() {
        return component;
    }

    /**
     * 释放浏览器持有的原生资源。
     *
     * <p>JCEF 浏览器在 Swing 组件之外还持有 Chromium 进程与渲染资源，仅移除组件不会回收，
     * 因此由工具窗口内容的 disposer 显式调用；未创建浏览器时不做任何事。</p>
     */
    public void dispose() {
        if (browser != null) {
            browser.dispose();
        }
    }

    /** 重新加载当前页面，地址与展示模式保持不变；工具条不存在或浏览器尚未就绪时不做任何事。 */
    private void reload() {
        if (toolbar == null) {
            return;
        }
        CefBrowser cefBrowser = browser.getCefBrowser();
        if (cefBrowser == null) {
            return;
        }

        // 用浏览器原生刷新保留当前地址（含页面内部路由与 query），无需重新解析展示模式映射。
        cefBrowser.reload();
    }

    /**
     * 在当前页面地址上切换 Web 与移动端，并把新模式写入项目记忆。
     */
    private void toggleMode() {
        if (toolbar == null) {
            return;
        }
        CefBrowser cefBrowser = browser.getCefBrowser();
        if (cefBrowser == null) {
            return;
        }

        AnswerDisplayMode target = mode == AnswerDisplayMode.MOBILE
                ? AnswerDisplayMode.DESKTOP
                : AnswerDisplayMode.MOBILE;
        URI current = null;
        try {
            current = URI.create(cefBrowser.getURL());
        } catch (IllegalArgumentException exception) {
            // 地址为空或含非法字符时无法解析，交给路由方法回退到目标模式的首页。
        }

        // 保留当前路径、query 与 fragment，只互换 /answer 与 /answer/m 前缀，避免切换后丢失页面上下文。
        URI targetUrl = target == AnswerDisplayMode.MOBILE
                ? AnswerRoutes.mobileUrl(current)
                : AnswerRoutes.desktopUrl(current);

        // 先更新模式、记忆与按钮文案再发起加载：即使加载失败，用户选择仍保持一致且可立即切回。
        mode = target;
        target.save(PropertiesComponent.getInstance(project));
        toolbar.setMode(target);

        browser.loadURL(targetUrl.toString());
    }

    /**
     * 返回指定模式的首页地址。
     *
     * @param displayMode 要打开的展示模式
     * @return 移动端首页或桌面端首页
     */
    @NotNull
    private static URI homeUrl(@NotNull AnswerDisplayMode displayMode) {
        return displayMode == AnswerDisplayMode.MOBILE ? AnswerSite.MOBILE_HOME : AnswerSite.HOME;
    }

    /**
     * 安装请求拦截：站内地址留在内嵌浏览器，站外 https 地址交给系统浏览器，其余导航直接取消。
     *
     * @param targetBrowser 需要约束导航的浏览器实例
     */
    private void installNavigationPolicy(@NotNull JBCefBrowser targetBrowser) {
        JBCefClient client = targetBrowser.getJBCefClient();
        client.addRequestHandler(new CefRequestHandlerAdapter() {
            @Override
            public boolean onBeforeBrowse(CefBrowser cefBrowser, CefFrame frame, CefRequest request,
                                          boolean userGesture, boolean isRedirect) {
                URI target;
                try {
                    target = URI.create(request.getURL());
                } catch (IllegalArgumentException exception) {
                    // 无法解析的地址不允许继续加载，返回 true 即取消本次导航。
                    return true;
                }

                // 站内页面继续由内嵌浏览器加载，登录、组卷、答题等路由都保持不变。
                if (AnswerNavigationPolicy.isAnswerPage(target)) {
                    return false;
                }

                // 站外 https 地址只交给系统浏览器；取消内嵌导航，避免工具窗口离开答题站。
                URI externalTarget = AnswerNavigationPolicy.externalHttpsTarget(request.getURL());
                if (externalTarget != null) {
                    BrowserUtil.browse(externalTarget);
                }
                return true;
            }
        }, targetBrowser.getCefBrowser());
    }

    /**
     * 构造 JCEF 不可用时的降级界面：说明原因并提供在系统浏览器中打开站点的入口。
     */
    private void installUnsupportedMessage() {
        JPanel message = new JPanel(new BorderLayout(0, 12));
        message.setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));
        message.add(new JLabel("当前 IDEA 运行环境不支持内嵌浏览器。", SwingConstants.CENTER), BorderLayout.CENTER);

        JButton openInBrowser = new JButton("在默认浏览器中打开 Answer System");
        // 降级后仍保留可用路径：用系统浏览器打开同一首页，不依赖 JCEF。
        openInBrowser.addActionListener(event -> BrowserUtil.browse(AnswerSite.HOME));
        JPanel action = new JPanel();
        action.add(openInBrowser);
        message.add(action, BorderLayout.SOUTH);

        component.add(message, BorderLayout.CENTER);
    }
}
