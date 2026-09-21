package com.yan.backend.common;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 密码强度校验。
 *
 * <p>规则：长度 8-64，且至少包含下面四类中的**三类** ——
 * 小写字母、大写字母、数字、特殊符号。
 *
 * <p>为什么不要求"四类全有"：那会把 {@code Admin@2026} 这类其实很不错的密码
 * 之外的很多合理密码排除掉，而且用户为了凑规则往往写成 {@code Abc@1234} 这种
 * 反而更好猜的形式。要求三类是个折中。
 *
 * <p>只用在**新增用户**和**重置密码**上，登录接口不做强度校验 ——
 * 否则老用户会被自己的历史弱密码挡在门外，连改密码都进不去。
 */
public final class PasswordPolicy {

    private static final int MIN_LENGTH = 8;
    private static final int MAX_LENGTH = 64;
    private static final int REQUIRED_CATEGORIES = 3;

    private static final Pattern LOWER = Pattern.compile("[a-z]");
    private static final Pattern UPPER = Pattern.compile("[A-Z]");
    private static final Pattern DIGIT = Pattern.compile("[0-9]");
    private static final Pattern SPECIAL = Pattern.compile("[^a-zA-Z0-9]");

    private PasswordPolicy() {
    }

    /**
     * 校验密码强度。
     *
     * @return 通过返回 null；不通过返回给用户看的原因
     */
    public static String validate(String password) {
        if (password == null || password.isBlank()) {
            return "密码不能为空";
        }
        if (password.length() < MIN_LENGTH || password.length() > MAX_LENGTH) {
            return "密码长度需在 " + MIN_LENGTH + " 到 " + MAX_LENGTH + " 个字符之间";
        }

        List<String> missing = new ArrayList<>();
        int categories = 0;
        if (LOWER.matcher(password).find()) {
            categories++;
        } else {
            missing.add("小写字母");
        }
        if (UPPER.matcher(password).find()) {
            categories++;
        } else {
            missing.add("大写字母");
        }
        if (DIGIT.matcher(password).find()) {
            categories++;
        } else {
            missing.add("数字");
        }
        if (SPECIAL.matcher(password).find()) {
            categories++;
        } else {
            missing.add("特殊符号");
        }

        if (categories < REQUIRED_CATEGORIES) {
            return "密码强度不足：需要至少包含 小写字母、大写字母、数字、特殊符号 中的 "
                    + REQUIRED_CATEGORIES + " 类，当前缺少 " + String.join("、", missing);
        }
        return null;
    }

    /** 给前端展示用的规则说明，避免前后端各写一份文案导致不一致 */
    public static String describe() {
        return "长度 " + MIN_LENGTH + "-" + MAX_LENGTH
                + " 位，且至少包含小写字母、大写字母、数字、特殊符号中的 "
                + REQUIRED_CATEGORIES + " 类";
    }

    // ============================================================
    // 密码有效期
    // ============================================================

    /**
     * 密码还有多少天到期。负数表示已经过期。
     *
     * <p>两种情况返回 {@code null}（表示"不适用、不提醒"）：
     * <ul>
     *   <li>{@code validDays <= 0} —— 配置里关闭了过期策略</li>
     *   <li>{@code pwdUpdateTime} 为 null —— 加这个字段之前建的账号没有改密记录。
     *       **这种情况必须当作"还没到期"**，否则升级完所有老用户一登录就被判定过期，
     *       直接进不去系统，而且他们没有改密入口之外的地方可去。</li>
     * </ul>
     *
     * <p>按**日期**而不是精确到秒算：用户看到的是"还有 3 天"，
     * 用秒算的话同一天里这个数字会跳来跳去（今天下午看是 3、明早看还是 3，
     * 但中间的某个时刻会变成 2），看起来像在倒计时，很烦。
     */
    public static Long daysUntilExpire(LocalDateTime pwdUpdateTime, int validDays) {
        if (validDays <= 0 || pwdUpdateTime == null) {
            return null;
        }
        LocalDate deadline = pwdUpdateTime.toLocalDate().plusDays(validDays);
        return ChronoUnit.DAYS.between(LocalDate.now(), deadline);
    }

    /** 密码是否已过期。没有改密记录的账号一律算没过期，理由见上 */
    public static boolean isExpired(LocalDateTime pwdUpdateTime, int validDays) {
        Long days = daysUntilExpire(pwdUpdateTime, validDays);
        return days != null && days <= 0;
    }

    // ============================================================
    // 初始默认密码
    // ============================================================

    /**
     * 项目文档里公开的初始密码。
     *
     * <p>这几个账号的密码写在种子数据和 README 里，属于"公开的秘密"，
     * 所以仍在使用它们的账号必须被强制改密 —— 等保对"默认口令"有明确要求。
     *
     * <p>这不是新增泄露面：seedRbacData 本来就用这些值创建账号，
     * 信息已经在代码里了。
     */
    public static final java.util.Map<String, String> KNOWN_DEFAULT_PASSWORDS =
            java.util.Map.of(
                    "admin", "admin123",
                    "operator", "operator123");

    /**
     * 判断一次登录用的密码是不是该账号的公开初始密码。
     *
     * <p>登录接口用它做**即时判断**：只要还在用初始密码，这次登录就直接
     * 判定为"需要改密"。
     *
     * <p>为什么不只依赖启动时的那次数据修补？因为 Tomcat 在种子数据跑完之前
     * 就开始监听端口了，极短时间内登录的用户会穿过那个窗口，
     * 拿到一个"不需要改密"的 token，然后用默认密码正常使用两个小时。
     * 在登录路径上再判断一次，这个窗口就彻底没有了，而且不额外增加开销
     * （这里比的是明文，不需要再做一次 BCrypt）。
     */
    public static boolean isKnownDefaultPassword(String username, String rawPassword) {
        if (username == null || rawPassword == null) {
            return false;
        }
        return rawPassword.equals(KNOWN_DEFAULT_PASSWORDS.get(username));
    }
}
