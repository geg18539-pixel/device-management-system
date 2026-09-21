/**
 * JPA 实体。
 *
 * <h2>⚠️ 大文本 / 二进制列不能用 {@code @Lob}</h2>
 *
 * <p>这个包里的实体一度用 {@code @Lob} 标注自由文本字段（维修结论、AI 建议、
 * 消息正文、知识库文本块等）。**在 H2 上一切正常，在 MySQL 上会在插入时报错**：
 *
 * <pre>
 *   Data truncation: Data too long for column 'content' at row 1
 * </pre>
 *
 * <p>原因是两边的方言对 {@code @Lob} 的处理不同，而 Hibernate 6 的 MySQL 方言
 * **按声明长度挑 TEXT 家族的具体类型**，不写 length 时默认就是 255：
 *
 * <pre>
 *   H2     : content clob        / embedding blob          （无上限）
 *   MySQL  : content tinytext    / embedding tinyblob      （255 字节！）
 * </pre>
 *
 * <p>所以同一个实体在两边的建表语句**类型完全不同** ——
 * 测试环境永远发现不了这个问题，只有真的连上 MySQL 才会炸。
 * 实测：一个 500 字的文本块、一个 3072 字节的向量都会超限。
 *
 * <h3>正确写法</h3>
 *
 * <pre>
 *   // 文本：TEXT 是 64KB，对一段维修结论 / 一个知识块来说绰绰有余
 *   &#64;Column(name = "content", nullable = false, columnDefinition = "TEXT")
 *   private String content;
 *
 *   // 二进制：BLOB 是 64KB，按 float 算能放 16383 维的向量
 *   &#64;Column(name = "embedding", nullable = false, columnDefinition = "BLOB")
 *   private byte[] embedding;
 * </pre>
 *
 * <p>为什么用 {@code columnDefinition} 而不是给 {@code @Column(length = ...)}：
 * 后者仍然要依赖"方言按长度挑类型"这张表，换个 Hibernate 版本或换个数据库
 * 就可能又变回去；{@code columnDefinition} 是**原样写进建表语句**的，不会变。
 * 代价是绑定了 MySQL 的 SQL 方言（本项目的 docker-compose 和 application.yml
 * 都写死了 MySQL，所以这个代价不存在）。
 *
 * <h3>⚠️ 改完记得处理存量库</h3>
 *
 * <p>{@code ddl-auto=update} **只加列、不改已有列的类型**。已经建出来的
 * {@code tinytext} 列不会因为改了实体就自动放宽，必须手工 {@code ALTER}
 * （见 {@code sql/} 目录下的迁移脚本）。这和之前 {@code device_type} 那次
 * NOT NULL 的问题是同一个坑。
 */
package com.yan.backend.entity;
