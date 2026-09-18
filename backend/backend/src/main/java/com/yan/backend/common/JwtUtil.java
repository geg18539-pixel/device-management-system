package com.yan.backend.common;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

/**
 * JWT 生成与解析。
 *
 * <p>用的是 jjwt 0.13.0 的**新 API**。0.12 起 jjwt 做过一次破坏性重写，
 * 网上大量教程还是 0.11 的写法（setSubject / setExpiration / parserBuilder /
 * parseClaimsJws），那些方法在 0.13 里已经不存在了，照抄编译不过。
 *
 * <p>对应关系：
 * <pre>
 *   0.11 老写法                          0.13 新写法
 *   setSubject(x)                       subject(x)
 *   setExpiration(d)                    expiration(d)
 *   setIssuedAt(d)                      issuedAt(d)
 *   signWith(SignatureAlgorithm, str)   signWith(SecretKey)
 *   Jwts.parserBuilder()                Jwts.parser()
 *   .setSigningKey(key)                 .verifyWith(key)
 *   .parseClaimsJws(t).getBody()        .parseSignedClaims(t).getPayload()
 * </pre>
 */
@Component
public class JwtUtil {

    private final SecretKey key;
    private final long expireMillis;
    private final String issuer;

    public JwtUtil(@Value("${app.jwt.secret}") String secret,
                   @Value("${app.jwt.expire-minutes:120}") long expireMinutes,
                   @Value("${app.jwt.issuer:device-management-system}") String issuer) {

        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        // HS256 要求密钥长度 >= 256 位（32 字节），短了 hmacShaKeyFor 会直接抛 WeakKeyException。
        // 这里提前检查并给出可读的报错，免得运行时一脸茫然。
        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                    "app.jwt.secret 太短：HS256 要求至少 32 字节（256 位），当前只有 "
                            + keyBytes.length + " 字节");
        }

        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.expireMillis = expireMinutes * 60_000L;
        this.issuer = issuer;
    }

    /** 生成 token。roles 会作为自定义 claim 写进去，拦截器里直接读，不用查库。 */
    public String generateToken(Long userId, String username, Collection<String> roles) {
        Instant now = Instant.now();

        return Jwts.builder()
                .issuer(issuer)
                .subject(username)
                .claim("userId", userId)
                .claim("roles", roles == null ? Set.of() : roles)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(expireMillis)))
                .signWith(key)
                .compact();
    }

    /**
     * 解析并校验 token。
     *
     * <p>签名不对、格式破损、已过期都会抛 JwtException 的子类
     * （ExpiredJwtException / SignatureException / MalformedJwtException 等），
     * 由拦截器分类处理。
     */
    public LoginUser parseToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return new LoginUser(
                readUserId(claims),
                claims.getSubject(),
                readRoles(claims)
        );
    }

    public long getExpireSeconds() {
        return expireMillis / 1000;
    }

    /**
     * 取 userId。
     *
     * <p>这里故意先取 Object 再手工转 Number，而不是 claims.get("userId", Long.class)。
     * 因为 JSON 里的数字反序列化成 Integer 还是 Long 取决于数值大小，
     * 直接按 Long 取在某些情况下会抛 RequiredTypeException 而不是返回 null。
     */
    private Long readUserId(Claims claims) {
        Object raw = claims.get("userId");
        return raw instanceof Number number ? number.longValue() : null;
    }

    private Set<String> readRoles(Claims claims) {
        Set<String> roles = new HashSet<>();
        Object raw = claims.get("roles");
        if (raw instanceof Collection<?> collection) {
            for (Object item : collection) {
                if (item != null) {
                    roles.add(String.valueOf(item));
                }
            }
        }
        return roles;
    }
}
