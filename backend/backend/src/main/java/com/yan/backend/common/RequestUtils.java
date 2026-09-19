package com.yan.backend.common;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 从当前请求里取客户端信息的工具。
 *
 * <p>抽出来是因为取 IP 的逻辑在多个地方要用（操作日志切面、登录日志），
 * 之前那份写在 LogAspect 里是私有的，再抄一遍必然会两边不一致。
 */
public final class RequestUtils {

    private static final int MAX_IP_LENGTH = 50;
    private static final int MAX_UA_LENGTH = 500;

    private RequestUtils() {
    }

    /** 拿不到请求（比如异步线程、定时任务）时返回 null，调用方自行处理 */
    public static HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return attrs.getRequest();
        }
        return null;
    }

    /**
     * 解析真实客户端 IP。
     *
     * <p>经过 Nginx 反向代理后，request.getRemoteAddr() 拿到的是 Nginx 容器的 IP，
     * 真实来源在 X-Forwarded-For 里。那个头是逗号分隔的链（客户端, 代理1, 代理2...），
     * **第一个才是客户端**。项目里的 frontend/nginx.conf 已经配了
     * proxy_set_header X-Real-IP / X-Forwarded-For。
     */
    public static String getClientIp() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return null;
        }

        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            String first = comma > 0 ? forwarded.substring(0, comma) : forwarded;
            return truncate(first.trim(), MAX_IP_LENGTH);
        }

        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return truncate(realIp.trim(), MAX_IP_LENGTH);
        }

        return truncate(request.getRemoteAddr(), MAX_IP_LENGTH);
    }

    public static String getUserAgent() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return null;
        }
        return truncate(request.getHeader("User-Agent"), MAX_UA_LENGTH);
    }

    /**
     * 把 User-Agent 粗略归类成「操作系统 / 浏览器」。
     *
     * <p>这是**启发式**判断，不是精确解析。真要准确识别得引入
     * user-agent-utils 之类的库，但为了页面上显示个大概，不值得引依赖。
     * 原始 UA 也一起存了，以后需要更准的分析有原始数据可查。
     *
     * <p>判断顺序有讲究：Edge 的 UA 里同时含 Chrome 和 Safari，
     * Chrome 的 UA 里含 Safari。所以必须**从最具体的往最宽泛的判断**，
     * 顺序写反了所有浏览器都会被识别成 Safari。
     */
    public static String describeDevice(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "未知";
        }
        String ua = userAgent.toLowerCase();

        String os;
        if (ua.contains("windows nt")) {
            os = "Windows";
        } else if (ua.contains("android")) {
            os = "Android";
        } else if (ua.contains("iphone") || ua.contains("ipad")) {
            os = "iOS";
        } else if (ua.contains("mac os x")) {
            os = "macOS";
        } else if (ua.contains("linux")) {
            os = "Linux";
        } else {
            os = "未知系统";
        }

        String browser;
        if (ua.contains("edg/")) {
            browser = "Edge";
        } else if (ua.contains("chrome/") && !ua.contains("chromium")) {
            browser = "Chrome";
        } else if (ua.contains("firefox/")) {
            browser = "Firefox";
        } else if (ua.contains("safari/")) {
            browser = "Safari";
        } else if (ua.contains("msie") || ua.contains("trident")) {
            browser = "IE";
        } else if (ua.contains("curl/") || ua.contains("httpclient") || ua.contains("okhttp")) {
            // 脚本/接口调用，不是浏览器
            browser = "程序调用";
        } else {
            browser = "未知浏览器";
        }

        return os + " / " + browser;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
