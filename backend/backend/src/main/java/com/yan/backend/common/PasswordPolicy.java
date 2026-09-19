package com.yan.backend.common;

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
}
