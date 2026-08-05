# 阶段一环境配置问题解决方案

## 问题现象

执行 `mvn -version` 时显示 `Java version: 1.8.0_341`，但 Spring Boot 3.x 要求 JDK 17+。系统 `JAVA_HOME` 指向 `D:\dev\jdk`（JDK 8），PATH 中的 `java` 命令是 TRAE 自带的 JDK 21 JRE（无 `javac`）。

## 复现步骤

1. 执行 `java -version`，返回 `openjdk version "21.0.10"`（TRAE JRE）
2. 执行 `mvn -version`，返回 `Java version: 1.8.0_341`（使用 JAVA_HOME 的 JDK 8）
3. 执行 `javac -version`，报错命令不存在（TRAE 的 java 是 JRE 非 JDK）

## 根因分析

- 系统 `JAVA_HOME` 环境变量指向 `D:\dev\jdk`（JDK 8）
- Maven 优先使用 `JAVA_HOME` 指定的 JDK，而非 PATH 中的 java
- TRAE 自带的 JDK 21 仅是 JRE，不含 `javac`，无法编译 Java 代码

## 解决方案

用户手动下载了完整的 JDK 17.0.2 到 `F:\jdk17\jdk-17.0.2`。在执行 Maven 命令前，临时设置 `JAVA_HOME`：

```powershell
$env:JAVA_HOME = 'F:\jdk17\jdk-17.0.2'
$env:PATH = "$env:JAVA_HOME\bin" + [System.IO.Path]::PathSeparator + $env:PATH
mvn clean test
```

## 预防措施

- 建议永久设置系统环境变量 `JAVA_HOME=F:\jdk17\jdk-17.0.2`，避免每次都要临时设置
- 或者在项目根目录创建 `.mvn/jvm.config` 配置文件指定 JDK 路径
- 后续 Agent 执行 Maven 命令时，必须先设置 `JAVA_HOME` 指向 JDK 17+
- Docker 未安装，任务7 的 MySQL/Redis 暂用 docker-compose.yml 描述，用户可自行安装 Docker Desktop 或本地直接安装数据库
