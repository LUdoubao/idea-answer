package com.github.codex.answer;

import com.intellij.ide.util.PropertiesComponent;
import org.jetbrains.annotations.NotNull;

/**
 * 工具窗口的页面展示模式，以及按项目记忆该模式的唯一读写入口。
 *
 * <p>模式只由工具窗口的切换按钮改变；首次打开或记忆值非法时使用 {@link #MOBILE}。
 * 记忆写入项目级 {@link PropertiesComponent}（即项目 {@code workspace.xml}），
 * 每个项目独立保存，不进入版本库，也不影响其他打开同一项目的 IDE 进程之外的配置。</p>
 */
public enum AnswerDisplayMode {

    /** 移动端视图，对应站点 {@code /answer/m/**} 路由。 */
    MOBILE("mobile"),

    /** 桌面端视图，对应站点 {@code /answer/**} 路由。 */
    DESKTOP("desktop");

    /** 项目级属性键；值是 {@link #MOBILE} 或 {@link #DESKTOP} 的存储名。 */
    public static final String PROPERTY_KEY = "answer.displayMode";

    /** 写入项目属性的稳定值；与枚举名解耦，避免重命名枚举时破坏已保存的偏好。 */
    private final String storedValue;

    AnswerDisplayMode(@NotNull String storedValue) {
        this.storedValue = storedValue;
    }

    /**
     * 读取项目记忆的展示模式。
     *
     * @param properties 项目级属性组件
     * @return 已保存的模式；键缺失、为空或值非法时回退 {@link #MOBILE}
     */
    @NotNull
    public static AnswerDisplayMode load(@NotNull PropertiesComponent properties) {
        String stored = properties.getValue(PROPERTY_KEY);
        for (AnswerDisplayMode mode : values()) {
            if (mode.storedValue.equals(stored)) {
                return mode;
            }
        }

        // 首次打开或旧值无法识别时按移动端处理，与产品默认口径保持一致。
        return MOBILE;
    }

    /**
     * 把当前模式写入项目属性，供下次创建工具窗口时恢复。
     *
     * @param properties 项目级属性组件
     */
    public void save(@NotNull PropertiesComponent properties) {
        properties.setValue(PROPERTY_KEY, storedValue);
    }
}
