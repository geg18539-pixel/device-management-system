# ============================================================
# 阶段一：构建
#
# 用完整的 Maven + JDK 镜像来编译。这个阶段体积很大，
# 但多阶段构建最后只会把产物拷进运行镜像，不会带过去。
# ============================================================
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /build

# 先只复制 pom.xml，单独跑一次依赖解析。
#
# 这是分层缓存的用法：Docker 会为每条指令生成一层，只要指令内容和
# 复制的文件都没变，这层就直接复用。把「拉依赖」和「复制源码」拆成两步，
# 以后只改 Java 代码时 pom.xml 没变，依赖层命中缓存，不用重新下载。
COPY pom.xml .
RUN mvn -B dependency:go-offline

# 再复制源码。放在依赖之后，是为了避免源码改动让上面的缓存层失效。
COPY src ./src

RUN mvn -B clean package -DskipTests

# ============================================================
# 阶段二：运行
#
# 只装 JRE 不装 JDK —— 运行编译好的 jar 不需要编译器，
# 镜像能小一大截。
# ============================================================
FROM eclipse-temurin:21-jre

WORKDIR /app

# 从构建阶段只拷走 jar。
# 用通配符是为了以后改 version 时不用同步改这里；
# *.jar 不会误匹配 Spring Boot 生成的 .jar.original 备份文件。
COPY --from=builder /build/target/*.jar /app/app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
