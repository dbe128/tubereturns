FROM eclipse-temurin:25-jdk-noble AS build
WORKDIR /app
COPY gradlew gradlew
COPY gradle gradle
RUN chmod +x gradlew
COPY build.gradle.kts settings.gradle.kts ./
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew dependencies --no-daemon -q 2>/dev/null || true
COPY src src
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew bootJar --no-daemon -x test

FROM python:3.12-slim-bookworm
WORKDIR /app

ENV JAVA_HOME=/opt/java/openjdk
ENV PATH="${JAVA_HOME}/bin:${PATH}"

COPY --from=build /opt/java/openjdk /opt/java/openjdk

RUN apt-get update && \
    apt-get install -y --no-install-recommends chromium chromium-driver && \
    apt-get clean && rm -rf /var/lib/apt/lists/*

RUN pip install --no-cache-dir \
    youtube-transcript-api \
    yt-dlp \
    requests \
    selenium \
    webdriver-manager \
    rich

COPY scripts/ytbsd.py /app/scripts/ytbsd.py
COPY llm-models.txt /app/llm-models.txt
COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
