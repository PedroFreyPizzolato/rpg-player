# Multi-stage build for JMusicBot
FROM maven:3.9-eclipse-temurin-25 AS builder

ARG BUILD_TIMESTAMP
ENV BUILD_TIMESTAMP=${BUILD_TIMESTAMP}

WORKDIR /build

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn clean package -DskipTests -B -Dproject.build.outputTimestamp=${BUILD_TIMESTAMP}

# Stage 2: Runtime image
FROM eclipse-temurin:25-jre-jammy

WORKDIR /app

COPY --from=builder /build/target/JMusicBot-*-All.jar /app/app.jar

RUN mkdir -p /config

COPY docker/entrypoint.sh /app/entrypoint.sh
RUN chmod +x /app/entrypoint.sh

WORKDIR /config

ENTRYPOINT ["/app/entrypoint.sh"]

