package com.github.codex.answer;

import org.junit.Test;

import java.net.URI;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * 验证答题站的内嵌范围与外链边界。
 *
 * <p>策略类不依赖 IDE 运行时，因此这些用例可在普通 JVM 上直接执行；断言覆盖主机、端口、
 * 路径前缀与协议四类判定，避免后续调整 {@link AnswerSite} 常量时把站外地址放进工具窗口。</p>
 */
public final class AnswerNavigationPolicyTest {

    /** 站内地址（含默认端口、查询串、无尾斜杠前缀）应留在工具窗口内。 */
    @Test
    public void allowsAnswerPages() {
        assertTrue(AnswerNavigationPolicy.isAnswerPage(URI.create("https://gin.hk.cn/answer/dashboard")));
        assertTrue(AnswerNavigationPolicy.isAnswerPage(URI.create("https://GIN.HK.CN:443/answer/practice?category=1")));
        assertTrue(AnswerNavigationPolicy.isAnswerPage(URI.create("https://gin.hk.cn/answer")));
        assertTrue(AnswerNavigationPolicy.isAnswerPage(URI.create("https://gin.hk.cn/answer/")));
    }

    /** 同主机的其他路径与端口、其他主机、明文地址，以及同前缀的其他应用都不能内嵌。 */
    @Test
    public void rejectsOutsideAnswerPrefix() {
        assertFalse(AnswerNavigationPolicy.isAnswerPage(URI.create("https://gin.hk.cn/")));
        assertFalse(AnswerNavigationPolicy.isAnswerPage(URI.create("https://gin.hk.cn:8443/answer/dashboard")));
        assertFalse(AnswerNavigationPolicy.isAnswerPage(URI.create("http://gin.hk.cn/answer/dashboard")));
        assertFalse(AnswerNavigationPolicy.isAnswerPage(URI.create("https://example.com/answer")));
        // 前缀按路径段边界匹配，避免 /answer-dashboard 这类同前缀应用被误判为答题站。
        assertFalse(AnswerNavigationPolicy.isAnswerPage(URI.create("https://gin.hk.cn/answer-dashboard")));
        assertFalse(AnswerNavigationPolicy.isAnswerPage(URI.create("https://gin.hk.cn/answerx")));
    }

    /** 只有站外 https 地址可以交给系统浏览器；站内地址返回 null，避免一次点击打开两个页面。 */
    @Test
    public void externalHttpsMustBeOutsideAnswerPrefix() {
        assertEquals(URI.create("https://example.com/auth"),
                AnswerNavigationPolicy.externalHttpsTarget("https://example.com/auth"));
        assertEquals(URI.create("https://gin.hk.cn/"),
                AnswerNavigationPolicy.externalHttpsTarget("https://gin.hk.cn/"));
        assertNull(AnswerNavigationPolicy.externalHttpsTarget("https://gin.hk.cn/answer/dashboard"));
    }

    /** 非 https、伪协议、相对路径与畸形地址都必须拒绝，不能进入系统 shell。 */
    @Test
    public void rejectsUnsafeOrMalformedDestinations() {
        assertNull(AnswerNavigationPolicy.externalHttpsTarget("http://example.com"));
        assertNull(AnswerNavigationPolicy.externalHttpsTarget("javascript:alert(1)"));
        assertNull(AnswerNavigationPolicy.externalHttpsTarget("/relative/path"));
        assertNull(AnswerNavigationPolicy.externalHttpsTarget("https://[invalid"));
    }
}
