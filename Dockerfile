# Multi-stage build for JMusicBot
FROM maven:3.9-eclipse-temurin-25 AS builder

ARG BUILD_TIMESTAMP
ENV BUILD_TIMESTAMP=$BUILD_TIMESTAMP

WORKDIR /build

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src

RUN if [ -n "$BUILD_TIMESTAMP" ]; then \
      mvn clean package -DskipTests -B -Dproject.build.outputTimestamp="$BUILD_TIMESTAMP"; \
    else \
      mvn clean package -DskipTests -B; \
    fi


# Stage 2: Runtime image
# Using Ubuntu Noble (24.04) for libraries required by jdave/udpqueue native libraries
FROM eclipse-temurin:25-jre-noble

WORKDIR /app

COPY --from=builder /build/target/JMusicBot-*-All.jar /app/app.jar

RUN mkdir -p /config

COPY docker/entrypoint.sh /app/entrypoint.sh
RUN chmod +x /app/entrypoint.sh

WORKDIR /config

ENTRYPOINT ["/app/entrypoint.sh"]
