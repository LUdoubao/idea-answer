package com.github.codex.answer;

import java.net.URI;

/**
 * Answer System 站点的固定地址与内嵌路径边界。
 *
 * <p>站点是部署在 {@code https://gin.hk.cn/answer} 下的单页应用，同主机的根路径属于另一个应用，
 * 因此浏览器的内嵌范围必须以路径前缀而非整域来限定。所有地址只在这里维护，站点迁移时修改本类即可，
 * 不涉及浏览器组装与工具窗口生命周期代码。</p>
 */
public final class AnswerSite {

    /** 站点主机名；内嵌与外链判定都以此为准，不区分大小写。 */
    public static final String HOST = "gin.hk.cn";

    /**
     * 站点的 URL 路径前缀。
     *
     * <p>命中规则见 {@link AnswerNavigationPolicy#isAnswerPage(URI)}：等于该前缀或以该前缀加 {@code /} 开头，
     * 从而排除 {@code /answer-dashboard} 这类同前缀不同应用的地址。</p>
     */
    public static final String PATH_PREFIX = "/answer";

    /** 工具窗口打开时加载的首页，即答题站控制台；登录与后续路由由页面自身处理。 */
    public static final URI HOME = URI.create("https://" + HOST + PATH_PREFIX + "/dashboard");

    /**
     * 移动端首页；站点把移动端视图挂在 {@code /answer/m} 路由下，由同一份前端 bundle 渲染。
     *
     * <p>工具窗口默认加载该地址，切换按钮在 {@code /answer} 与 {@code /answer/m} 之间互换。</p>
     */
    public static final URI MOBILE_HOME = URI.create("https://" + HOST + PATH_PREFIX + "/m/dashboard");

    private AnswerSite() {
    }
}
