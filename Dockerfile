# ---------- 构建阶段 ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
# 国内构建加速（可在公网构建时忽略，镜像只是 mirrorOf=* 的一个镜像源）
COPY docker/settings.xml /root/.m2/settings.xml
COPY pom.xml .
RUN mvn -B -q dependency:go-offline -DskipTests || true
COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ---------- 运行阶段 ----------
FROM eclipse-temurin:17-jre
WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd -r uvend && useradd -r -g uvend uvend
COPY --from=build /build/target/uvend-service-1.0.0.jar /app/app.jar
USER uvend
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=10 \
  CMD ["sh","-c","curl -fsS http://localhost:8080/v3/api-docs >/dev/null || exit 1"]
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75.0","-jar","/app/app.jar"]
