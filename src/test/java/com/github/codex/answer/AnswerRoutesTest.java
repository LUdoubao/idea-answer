package com.github.codex.answer;

import org.junit.Test;

import java.net.URI;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * 验证 Web 与移动端地址互换规则。
 *
 * <p>用例只依赖 {@link AnswerRoutes} 与 {@link AnswerSite} 常量，不启动 IDE：
 * 覆盖路径互换、query/fragment 保留、路径段边界与站外地址回退，防止按钮把用户带到站外或丢失页面上下文。</p>
 */
public final class AnswerRoutesTest {

    /** 桌面端与移动端的对应路径必须双向互换，且 URL 其余部分保持一致。 */
    @Test
    public void togglesBetweenDesktopAndMobilePaths() {
        assertEquals(URI.create("https://gin.hk.cn/answer/m/dashboard"),
                AnswerRoutes.mobileUrl(URI.create("https://gin.hk.cn/answer/dashboard")));
        assertEquals(URI.create("https://gin.hk.cn/answer/dashboard"),
                AnswerRoutes.desktopUrl(URI.create("https://gin.hk.cn/answer/m/dashboard")));
    }

    /** 切换时 query 与 fragment 必须原样保留，包括已编码的查询字符。 */
    @Test
    public void preservesQueryAndFragment() {
        assertEquals(URI.create("https://gin.hk.cn/answer/history?x=1#y"),
                AnswerRoutes.desktopUrl(URI.create("https://gin.hk.cn/answer/m/history?x=1#y")));
        assertEquals(URI.create("https://gin.hk.cn/answer/m/history?x=1#y"),
                AnswerRoutes.mobileUrl(URI.create("https://gin.hk.cn/answer/history?x=1#y")));
        assertEquals(URI.create("https://gin.hk.cn/answer/search?q=a%20b"),
                AnswerRoutes.desktopUrl(URI.create("https://gin.hk.cn/answer/m/search?q=a%20b")));
    }

    /** 前缀本身、尾斜杠与嵌套路径都要正确处理，不能产生双斜杠或丢失路径段。 */
    @Test
    public void handlesPathBoundaries() {
        assertEquals(URI.create("https://gin.hk.cn/answer/m"),
                AnswerRoutes.mobileUrl(URI.create("https://gin.hk.cn/answer")));
        assertEquals(URI.create("https://gin.hk.cn/answer/m/"),
                AnswerRoutes.mobileUrl(URI.create("https://gin.hk.cn/answer/")));
        assertEquals(URI.create("https://gin.hk.cn/answer"),
                AnswerRoutes.desktopUrl(URI.create("https://gin.hk.cn/answer/m")));
        assertEquals(URI.create("https://gin.hk.cn/answer/"),
                AnswerRoutes.desktopUrl(URI.create("https://gin.hk.cn/answer/m/")));
    }

    /** 移动端判定按路径段边界匹配，/answer/mobile 等相似前缀不能误判。 */
    @Test
    public void detectsMobilePathBySegmentBoundary() {
        assertTrue(AnswerRoutes.isMobilePath(URI.create("https://gin.hk.cn/answer/m")));
        assertTrue(AnswerRoutes.isMobilePath(URI.create("https://gin.hk.cn/answer/m/")));
        assertTrue(AnswerRoutes.isMobilePath(URI.create("https://gin.hk.cn/answer/m/history")));
        assertFalse(AnswerRoutes.isMobilePath(URI.create("https://gin.hk.cn/answer/mobile")));
        assertFalse(AnswerRoutes.isMobilePath(URI.create("https://gin.hk.cn/answer")));
        assertFalse(AnswerRoutes.isMobilePath(null));
    }

    /** 已是目标模式时原样返回同一地址，避免无意义的加载。 */
    @Test
    public void keepsUrlWhenAlreadyInTargetMode() {
        URI mobile = URI.create("https://gin.hk.cn/answer/m/dashboard");
        URI desktop = URI.create("https://gin.hk.cn/answer/dashboard");
        assertSame(mobile, AnswerRoutes.mobileUrl(mobile));
        assertSame(desktop, AnswerRoutes.desktopUrl(desktop));
    }

    /** 站外地址、非 https 与无法解析的输入都回退到对应模式的首页，不能交给内嵌浏览器。 */
    @Test
    public void fallsBackToModeHomeForNonAnswerPages() {
        assertEquals(AnswerSite.MOBILE_HOME, AnswerRoutes.mobileUrl(URI.create("https://gin.hk.cn/")));
        assertEquals(AnswerSite.HOME, AnswerRoutes.desktopUrl(URI.create("https://gin.hk.cn/")));
        assertEquals(AnswerSite.HOME,
                AnswerRoutes.desktopUrl(URI.create("http://gin.hk.cn/answer/m/dashboard")));
        assertEquals(AnswerSite.MOBILE_HOME, AnswerRoutes.mobileUrl(null));
        assertEquals(AnswerSite.HOME, AnswerRoutes.desktopUrl(null));
    }
}
