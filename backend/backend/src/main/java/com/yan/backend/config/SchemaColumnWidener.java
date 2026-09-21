package com.yan.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 启动时检查并**放宽**几个大文本/二进制列。
 *
 * <h3>为什么需要这个</h3>
 *
 * <p>{@code ddl-auto=update} 有一条很容易踩的边界：**它只加列，不会放宽已有列的类型**。
 * 项目里因此栽过两次 —— {@code device_type} 的 NOT NULL 一次，
 * 大文本列一次（下面详述）。两次的表现都是"代码看着没问题，
 * 数据就是写不进去"，排查起来很绕。
 *
 * <h3>大文本列那次是怎么回事</h3>
 *
 * <p>实体上用了 {@code @Lob} 标注自由文本字段。Hibernate 6 的 **MySQL 方言
 * 按声明长度挑 TEXT 家族的具体类型**，而 {@code @Lob} 不写 length 时默认 255，
 * 于是建出来的是 {@code tinytext}（255 字节）。同一个实体在 H2 上生成的是
 * {@code clob}（无上限）—— 所以**测试环境永远发现不了**，只有连上 MySQL 才炸：
 *
 * <pre>
 *   Data truncation: Data too long for column 'content' at row 1
 * </pre>
 *
 * <p>实体那边已经改成显式的 {@code columnDefinition = "TEXT"/"BLOB"}
 * （见 {@code entity/package-info.java}），新建的库不会再有问题。
 * 但**已经建出来的库**不会因为改了实体就自动变宽，这个类就是来补这一刀的。
 *
 * <h3>安全边界（刻意收得很窄）</h3>
 *
 * <ul>
 *   <li>只处理下面 {@link #TARGETS} 里**明确列出**的列，其它一律不碰；</li>
 *   <li>**只放宽、不收缩**（当前宽度已经够就直接跳过）；</li>
 *   <li>改之前还确认一次当前类型确实属于文本/二进制family ——
 *       万一有人把某个列改成了别的用途，这里会跳过并告警，而不是把 INT 改成 TEXT；</li>
 *   <li>整段 try/catch，失败只打 WARN，**绝不影响启动**（和种子数据一个口径）。</li>
 * </ul>
 *
 * <p>从宽度判断而不是从类型名判断，是因为宽度是**跨数据库一致**的
 * （实测：H2 和 MySQL 通过 {@code DatabaseMetaData} 报的 COLUMN_SIZE 都准），
 * 类型名则各家叫法完全不同（{@code tinytext} / {@code CHARACTER VARYING} /
 * {@code CHARACTER LARGE OBJECT}…）。
 *
 * <p>执行时机：{@code HIGHEST_PRECEDENCE} 让它排在 {@link DataInitializer}
 * 的种子数据**之前** —— 否则种子数据往一个 255 字节的列里写长文本时会先失败。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SchemaColumnWidener implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SchemaColumnWidener.class);

    /**
     * 宽度至少要到这个值才算够。
     *
     * <p>65535 就是 MySQL 的 {@code text} / {@code blob}（64KB）。
     * 知识库最大的一个文本块也就 500 字（约 1.5KB），
     * 最大的向量是 4096 维 float（16KB）—— 都远在 64KB 之内。
     */
    private static final int MIN_SIZE = 65535;

    /** 一个需要保证宽度的列 */
    private record Target(String table, String column, String sqlType, boolean binary) {
    }

    /**
     * 需要保证宽度的列。
     *
     * <p>这张表是**故意写死的**：从数据库里反推"哪些列本该是大字段"不可靠，
     * 而漏掉一个列的代价只是它保持现状（不会更糟）。
     */
    private static final List<Target> TARGETS = List.of(
            new Target("knowledge_chunk", "content", "TEXT", false),
            new Target("knowledge_chunk", "embedding", "BLOB", true),
            new Target("device_repair", "repair_result", "TEXT", false),
            new Target("device_repair", "ai_possible_causes", "TEXT", false),
            new Target("device_repair", "ai_suggestion", "TEXT", false),
            new Target("device_repair", "ai_error", "TEXT", false),
            new Target("device_repair_log", "content", "TEXT", false),
            new Target("device_maintenance_record", "content", "TEXT", false),
            new Target("sys_message", "content", "TEXT", false),
            new Target("sys_oper_log", "error_msg", "TEXT", false),
            new Target("sys_login_log", "fail_reason", "TEXT", false));

    private final DataSource dataSource;

    public SchemaColumnWidener(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(String... args) {
        try (Connection connection = dataSource.getConnection()) {
            // 一次性把全库列读进来再在内存里查，而不是逐列调 getColumns：
            // H2 会把表名存成大写、MySQL 在 Linux 上区分大小写，
            // 传进去的名字对不上就查不到。全量读 + 忽略大小写匹配是跨库一致的。
            Map<String, ColumnInfo> all = loadAllColumns(connection);

            List<String> widened = new ArrayList<>();
            for (Target target : TARGETS) {
                String fix = widenIfNeeded(connection, all, target);
                if (fix != null) {
                    widened.add(fix);
                }
            }

            if (widened.isEmpty()) {
                log.info("数据库列宽检查完成：{} 个目标列都达标", TARGETS.size());
            } else {
                log.warn("数据库列宽检查：放宽了 {} 个列（原来的类型太窄，长文本会写不进去）",
                        widened.size());
                widened.forEach(item -> log.warn("    {}", item));
                log.warn("这就是 ddl-auto=update 只加列、不放宽已有列的表现。"
                        + "新建的库不会有这个问题（实体上已写死 columnDefinition）。");
            }
        } catch (Exception e) {
            // 和种子数据一个口径：这个检查不该有让应用起不来的权力
            log.warn("数据库列宽检查失败，已跳过（不影响应用启动）。原因: {}", e.getMessage());
            log.debug("列宽检查失败明细", e);
        }
    }

    /**
     * @return 放宽成功时返回一句人话描述，否则 null
     */
    private String widenIfNeeded(Connection connection, Map<String, ColumnInfo> all, Target target) {
        ColumnInfo info = all.get(key(target.table(), target.column()));
        if (info == null) {
            // 表或列还不存在（比如这一批功能还没启动过），跳过
            log.debug("列 {}.{} 不存在，跳过", target.table(), target.column());
            return null;
        }
        if (info.size() <= 0) {
            // 有些驱动对 CLOB/BLOB 报 0 或 null，判断不了就不动它
            log.debug("列 {}.{} 的宽度读不到（{}），跳过", target.table(), target.column(), info.typeName());
            return null;
        }
        if (info.size() >= MIN_SIZE) {
            return null;
        }
        if (!matchesFamily(info.typeName(), target.binary())) {
            // 兜底：万一这个列被挪作他用，宁可不动也不能把它改成 TEXT
            log.warn("列 {}.{} 现在是 {}({} 字节)，但它不像是{}字段，为安全起见不改动",
                    target.table(), target.column(), info.typeName(), info.size(),
                    target.binary() ? "二进制" : "文本");
            return null;
        }

        String nullable = info.nullable() ? "" : " NOT NULL";
        String sql = "ALTER TABLE " + target.table() + " MODIFY COLUMN "
                + target.column() + " " + target.sqlType() + nullable;
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
            return String.format("%s.%s：%s(%d 字节) → %s%s",
                    target.table(), target.column(), info.typeName(), info.size(),
                    target.sqlType(), nullable);
        } catch (Exception e) {
            log.warn("放宽列 {}.{} 失败: {}", target.table(), target.column(), e.getMessage());
            return null;
        }
    }

    /**
     * 当前类型是否属于预期的类型family。
     *
     * <p>用"包含关键字"而不是精确匹配：各家的类型名差别太大
     * （{@code TINYTEXT} / {@code TEXT} / {@code MEDIUMTEXT} / {@code LONGTEXT} /
     * {@code VARCHAR} / {@code CHARACTER VARYING} / {@code CLOB} /
     * {@code CHARACTER LARGE OBJECT}…），逐个列举必然漏。
     * 不精确没关系 —— 外面还有一层"只处理写死的这些列"兜着。
     */
    private boolean matchesFamily(String typeName, boolean binary) {
        if (typeName == null) {
            return false;
        }
        String upper = typeName.toUpperCase(Locale.ROOT);
        if (binary) {
            return upper.contains("BLOB") || upper.contains("BINARY");
        }
        return upper.contains("TEXT") || upper.contains("CHAR") || upper.contains("CLOB");
    }

    private Map<String, ColumnInfo> loadAllColumns(Connection connection) throws Exception {
        Map<String, ColumnInfo> map = new HashMap<>();
        DatabaseMetaData meta = connection.getMetaData();
        // 不传表名/列名过滤，全量读 —— 本项目的表也就二三十张，几百个列
        try (ResultSet rs = meta.getColumns(connection.getCatalog(), null, null, null)) {
            while (rs.next()) {
                String table = rs.getString("TABLE_NAME");
                String column = rs.getString("COLUMN_NAME");
                if (table == null || column == null) {
                    continue;
                }
                int size = rs.getInt("COLUMN_SIZE");
                boolean sizeKnown = !rs.wasNull();
                boolean nullable = rs.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls;
                map.put(key(table, column),
                        new ColumnInfo(rs.getString("TYPE_NAME"), sizeKnown ? size : -1, nullable));
            }
        }
        return map;
    }

    private String key(String table, String column) {
        return table.toUpperCase(Locale.ROOT) + "." + column.toUpperCase(Locale.ROOT);
    }

    private record ColumnInfo(String typeName, int size, boolean nullable) {
    }
}
