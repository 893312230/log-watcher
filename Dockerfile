# syntax=docker/dockerfile:1

# 阶段 1：Maven 构建（服务器端构建，无需本地 JDK17/Maven）
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# 阿里云 Maven 中央仓库镜像，加速国内依赖拉取
COPY deploy/maven-settings.xml /root/.m2/settings.xml

# 先单独拷贝各模块 pom：pom 不变时 Docker 层缓存可复用
COPY pom.xml .
COPY smart-ops-common/pom.xml smart-ops-common/
COPY smart-ops-domain/pom.xml smart-ops-domain/
COPY smart-ops-infrastructure/pom.xml smart-ops-infrastructure/
COPY smart-ops-agent-core/pom.xml smart-ops-agent-core/
COPY smart-ops-api/pom.xml smart-ops-api/
COPY smart-ops-bootstrap/pom.xml smart-ops-bootstrap/

# 拷贝全部源码并打包（测试在 CI/本地已跑，镜像构建跳过）
COPY . .
RUN mvn -B package -DskipTests

# 阶段 2：运行时（仅 JRE，减小镜像体积）
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /build/smart-ops-bootstrap/target/smart-ops-bootstrap-*.jar app.jar
EXPOSE 8080
# JVM 参数经 JAVA_OPTS 注入（小内存服务器必须限制堆，compose 中配置）
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar --spring.profiles.active=prod"]
