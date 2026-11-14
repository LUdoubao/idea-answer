package com.github.codex.answer;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

/**
 * 为每个 IDEA 项目创建 Answer System 工具窗口内容，并把浏览器释放绑定到内容生命周期。
 *
 * <p>工具窗口在用户首次打开时才创建内容，因此浏览器不会拖慢 IDE 启动；每个项目各自持有一个浏览器实例，
 * 项目关闭时由 {@code Content} 的 disposer 释放对应的 Chromium 资源。</p>
 *
 * <p>实现 {@link DumbAware}：页面与项目索引无关，索引构建期间也应能正常打开答题站。</p>
 */
public final class AnswerToolWindowFactory implements ToolWindowFactory, DumbAware {

    /**
     * 创建工具窗口内容并注册释放回调。
     *
     * @param project    持有该工具窗口的项目，用于 IDE 生命周期管理
     * @param toolWindow 由插件描述符注册的工具窗口
     */
    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        // 传入 project：工具窗口按项目读写 answer.displayMode 记忆，同一 IDE 的不同项目互不影响。
        AnswerBrowserPanel panel = new AnswerBrowserPanel(project);

        // 内容 disposer 跟随工具窗口与项目生命周期：关闭项目或卸载插件时释放 Chromium 原生资源。
        Content content = ContentFactory.getInstance().createContent(panel.getComponent(), "", false);
        content.setDisposer(panel::dispose);
        toolWindow.getContentManager().addContent(content);
    }
}
