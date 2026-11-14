package com.github.codex.answer;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;

/**
 * 浏览器导航边界策略：决定哪些地址留在工具窗口内、哪些交给操作系统浏览器。
 *
 * <p>该类保持无状态且不依赖 IDE 运行时，既被浏览器请求回调调用，也被单元测试直接覆盖。
 * 内嵌范围固定为 {@code https://gin.hk.cn/answer}，目的是避免同主机其他应用被带进 IDE；
 * 外部地址只允许 https，防止 {@code javascript:}、{@code file:} 等协议进入系统 shell。</p>
 */
public final class AnswerNavigationPolicy {

    private AnswerNavigationPolicy() {
    }

    /**
     * 判断地址是否属于答题站，可以在工具窗口内继续加载。
     *
     * @param target 已解析的目标地址；Chromium 可能传入无法解析的输入，因此允许为 {@code null}
     * @return 仅当协议为 https、主机为站点主机、端口为默认 443 且路径落在 {@code /answer} 下时返回 {@code true}
     */
    public static boolean isAnswerPage(@Nullable URI target) {
        // 空地址与带用户信息的地址都不属于可信站点：前者无法判定，后者可能被用来伪装主机。
        if (target == null || target.getUserInfo() != null) {
            return false;
        }

        // 站点只以 HTTPS 提供服务；明文 http 一律不内嵌，避免登录凭据走明文链路。
        if (!"https".equalsIgnoreCase(target.getScheme())) {
            return false;
        }

        // 主机比较忽略大小写；String#equalsIgnoreCase 对 null 入参返回 false，无需额外判空。
        if (!AnswerSite.HOST.equalsIgnoreCase(target.getHost())) {
            return false;
        }

        // 只接受默认端口（-1 表示未显式指定）：同主机的其他端口可能是另一套环境，不放行。
        int port = target.getPort();
        if (port != -1 && port != 443) {
            return false;
        }

        // 路径必须是指向前缀本身或其子路径，避免 /answerxxx 这类同前缀的其他应用被误判为答题站。
        String path = target.getPath();
        return path != null
                && (path.equals(AnswerSite.PATH_PREFIX) || path.startsWith(AnswerSite.PATH_PREFIX + "/"));
    }

    /**
     * 解析候选地址，仅当其是可安全交给系统浏览器打开的站外 HTTPS 地址时返回。
     *
     * @param address 浏览器回调提供的原始地址字符串
     * @return 站外 https 地址；站内地址、明文、相对路径或畸形输入返回 {@code null}
     */
    @Nullable
    public static URI externalHttpsTarget(@NotNull String address) {
        try {
            URI target = URI.create(address);

            // 必须是带主机的绝对 https 地址：相对路径没有可打开的目标，明文与伪协议不能交给系统 shell。
            if (!target.isAbsolute() || target.getHost() == null || target.getUserInfo() != null
                    || !"https".equalsIgnoreCase(target.getScheme())) {
                return null;
            }

            // 站内地址继续留在内嵌浏览器，不作为外链重复打开，避免一次点击打开两个页面。
            return isAnswerPage(target) ? null : target;
        } catch (IllegalArgumentException exception) {
            // Chromium 可能给出包含非法字符的地址；解析失败时按拒绝处理，交由调用方取消导航。
            return null;
        }
    }
}
