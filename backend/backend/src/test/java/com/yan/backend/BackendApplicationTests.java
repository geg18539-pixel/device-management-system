package com.yan.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 上下文能不能启动起来。
 *
 * <p>看着像个空测试，但它抓的是**只有真启动一次才会暴露**的一整类问题：
 *
 * <ul>
 *   <li>实体映射错了（字段类型对不上列、@ManyToMany 两边打架）</li>
 *   <li>Bean 装配不起来（少个 @Component、构造器注入的参数找不到）</li>
 *   <li>配置项读不到（yml 里名字写错）</li>
 *   <li>种子数据跑不完（唯一约束冲突、往 NOT NULL 列插 null）</li>
 * </ul>
 *
 * <p>这些 {@code mvn compile} 一个都发现不了 —— 编译只检查语法和类型。
 *
 * <p><b>{@code @ActiveProfiles("test")}</b> 是让它跑得起来的关键：
 * Spring 会去读 {@code src/test/resources/application-test.yml}，
 * 把数据源换成 H2 内存库。不加这个的话，它会去连真实的 MySQL，
 * 本地没起库就报连接失败、CI 里必挂 —— 那样它就从"一道检查"退化成了"一个红灯"。
 */
@SpringBootTest
@ActiveProfiles("test")
class BackendApplicationTests {

    @Test
    void contextLoads() {
        // 能跑到这一行，就说明上面那四类问题一个都没有。
        // 不需要额外断言 —— 启动失败本身就会让测试红掉。
    }
}
