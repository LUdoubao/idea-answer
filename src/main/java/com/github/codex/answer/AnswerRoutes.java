package com.github.codex.answer;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Web 与移动端页面的地址互换规则。
 *
 * <p>站点把同一份前端渲染在两条路由上：桌面端为 {@code /answer/**}，移动端为 {@code /answer/m/**}。
 * 本类只做纯地址换算，不依赖 IDE 与 JCEF 运行时，因此可被单元测试直接覆盖。</p>
 *
 * <p>换算时只替换模式路径段，scheme、host、port、query 与 fragment 全部沿用原值，
 * 保证在带查询参数或锚点的页面上切换时不会丢失上下文。传入非答题站地址（含 {@code null}）时，
 * 回退到目标模式的首页，避免把站外地址或相对路径交给内嵌浏览器。</p>
 */
public final class AnswerRoutes {

    /** 移动端路径段；站点通过 {@code /answer/m} 前缀区分移动端视图。 */
    private static final String MOBILE_SEGMENT = "/m";

    private AnswerRoutes() {
    }

    /**
     * 判断地址是否落在移动端路由下。
     *
     * @param target 待判定的地址，允许为 {@code null}
     * @return 路径等于 {@code /answer/m} 或以 {@code /answer/m/} 开头时为 {@code true}
     */
    public static boolean isMobilePath(@Nullable URI target) {
        if (target == null) {
            return false;
        }

        // 按路径段边界匹配，避免 /answer/mobile 这类同前缀页面被误判为移动端。
        String path = target.getPath();
        return path != null
                && (path.equals(AnswerSite.PATH_PREFIX + MOBILE_SEGMENT)
                || path.startsWith(AnswerSite.PATH_PREFIX + MOBILE_SEGMENT + "/"));
    }

    /**
     * 把当前地址换算成同一页面的移动端地址。
     *
     * @param current 浏览器当前地址；允许为 {@code null} 或非答题站地址
     * @return 移动端地址；已是移动端路径时原样返回，无法换算时回退 {@link AnswerSite#MOBILE_HOME}
     */
    @NotNull
    public static URI mobileUrl(@Nullable URI current) {
        if (!AnswerNavigationPolicy.isAnswerPage(current)) {
            return AnswerSite.MOBILE_HOME;
        }
        if (isMobilePath(current)) {
            return current;
        }

        // 在 /answer 前缀后插入 /m，保留前缀之后的原有路径（如 /dashboard、/history）。
        String restOfPath = current.getPath().substring(AnswerSite.PATH_PREFIX.length());
        URI mobile = rebuild(current, AnswerSite.PATH_PREFIX + MOBILE_SEGMENT + restOfPath);
        return mobile != null ? mobile : AnswerSite.MOBILE_HOME;
    }

    /**
     * 把当前地址换算成同一页面的桌面端地址。
     *
     * @param current 浏览器当前地址；允许为 {@code null} 或非答题站地址
     * @return 桌面端地址；已是桌面端路径时原样返回，无法换算时回退 {@link AnswerSite#HOME}
     */
    @NotNull
    public static URI desktopUrl(@Nullable URI current) {
        if (!AnswerNavigationPolicy.isAnswerPage(current)) {
            return AnswerSite.HOME;
        }
        if (!isMobilePath(current)) {
            return current;
        }

        // 去掉 /answer/m 中的 /m 段，其余路径原样保留，得到对应的桌面端页面。
        String mobilePrefix = AnswerSite.PATH_PREFIX + MOBILE_SEGMENT;
        URI desktop = rebuild(current, AnswerSite.PATH_PREFIX + current.getPath().substring(mobilePrefix.length()));
        return desktop != null ? desktop : AnswerSite.HOME;
    }

    /**
     * 用新路径重建地址，同时沿用原地址的 scheme、host、port、query 与 fragment。
     *
     * @param current 提供其余组件的原地址，调用方已确认其是答题站地址
     * @param path    替换后的路径
     * @return 重建后的地址；重新编码失败时返回 {@code null}，由调用方回退到模式首页
     */
    @Nullable
    private static URI rebuild(@NotNull URI current, @NotNull String path) {
        try {
            // 用 URI 多参构造器而不是字符串拼接，让查询串与路径按 URI 规则重新编码。
            // 用户信息固定为 null：答题站地址不含凭据，导航策略也拒绝带 userInfo 的地址。
            return new URI(current.getScheme(), null, current.getHost(), current.getPort(), path,
                    current.getQuery(), current.getFragment());
        } catch (URISyntaxException exception) {
            // 已解析组件的重新编码理论上不会失败；真出现畸形值时按失败处理，不把半成品地址交给浏览器。
            return null;
        }
    }
}
