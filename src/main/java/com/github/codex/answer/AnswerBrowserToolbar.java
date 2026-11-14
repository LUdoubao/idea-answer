package com.github.codex.answer;

import com.intellij.icons.AllIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import java.awt.FlowLayout;

/**
 * 浏览器上方的操作条，提供刷新与 Web/移动端切换两个按钮。
 *
 * <p>工具条只负责按钮与文案，不接触 JCEF、地址换算或项目记忆：两个动作由
 * {@link AnswerBrowserPanel} 通过构造函数注入，模式文案由 {@link #setMode(AnswerDisplayMode)} 同步。
 * 这样在 JCEF 不可用的降级分支里不会创建工具条，也不存在无浏览器的按钮。</p>
 */
public final class AnswerBrowserToolbar extends JPanel {

    /** 切换按钮；文案随当前模式在“切换到 Web”和“切换到移动端”之间变化。 */
    private final JButton toggleButton;

    /**
     * 创建操作条并绑定两个动作。
     *
     * @param onRefresh    刷新当前页面的回调，由按钮在 EDT 上触发
     * @param onToggleMode 切换展示模式的回调；具体方向由点击时的当前模式决定
     */
    public AnswerBrowserToolbar(@NotNull Runnable onRefresh, @NotNull Runnable onToggleMode) {
        super(new FlowLayout(FlowLayout.LEFT, 8, 4));
        setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));

        JButton refreshButton = new JButton("刷新", AllIcons.Actions.Refresh);
        refreshButton.setToolTipText("重新加载当前页面，页面内未保存的状态会丢失");
        refreshButton.addActionListener(event -> onRefresh.run());

        toggleButton = new JButton();
        toggleButton.setToolTipText("在当前页面地址上切换 Web 与移动端，查询参数与锚点保持不变");
        toggleButton.addActionListener(event -> onToggleMode.run());

        add(refreshButton);
        add(toggleButton);
    }

    /**
     * 同步切换按钮文案到当前模式。
     *
     * @param mode 当前展示模式；移动端下按钮指向 Web 页面，桌面端下按钮指向移动端页面
     */
    public void setMode(@NotNull AnswerDisplayMode mode) {
        toggleButton.setText(mode == AnswerDisplayMode.MOBILE ? "切换到 Web" : "切换到移动端");
    }
}
