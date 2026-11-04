FROM eclipse-temurin:25-jdk-noble AS build
WORKDIR /app
COPY gradlew settings.gradle build.gradle gradle.properties ./
COPY gradle ./gradle
COPY config ./config
COPY src/main ./src/main
RUN chmod +x gradlew && ./gradlew --no-daemon bootJar

FROM eclipse-temurin:25-jre-noble
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
COPY --from=build /app/build/libs/darkhold-*.jar app.jar
COPY start.sh ./start.sh
EXPOSE 8181
ENTRYPOINT ["sh", "/app/start.sh"]
