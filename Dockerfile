# Multi-stage build for JMusicBot
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /build

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn clean package -DskipTests -B

FROM eclipse-temurin:17-jre-slim

WORKDIR /app

COPY --from=builder /build/target/JMusicBot-*-All.jar /app/app.jar

RUN mkdir -p /config

COPY docker/entrypoint.sh /app/entrypoint.sh
RUN chmod +x /app/entrypoint.sh

WORKDIR /config

ENTRYPOINT ["/app/entrypoint.sh"]
