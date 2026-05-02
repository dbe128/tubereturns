FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
COPY gradlew gradlew
COPY gradle gradle
RUN chmod +x gradlew
COPY build.gradle.kts settings.gradle.kts ./
RUN ./gradlew dependencies --no-daemon -q 2>/dev/null || true
COPY src src
RUN ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:25-jdk
WORKDIR /app

RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        python3 python3-pip \
        wget gnupg ca-certificates \
        libnss3 libatk-bridge2.0-0 libgtk-3-0 libx11-xcb1 \
        libasound2t64 libgbm1 libxshmfence1 \
        fonts-liberation xdg-utils && \
    wget -q -O - https://dl.google.com/linux/linux_signing_key.pub \
        | gpg --dearmor -o /usr/share/keyrings/google-linux-signing-keyring.gpg && \
    echo "deb [arch=amd64 signed-by=/usr/share/keyrings/google-linux-signing-keyring.gpg] http://dl.google.com/linux/chrome/deb/ stable main" \
        > /etc/apt/sources.list.d/google-chrome.list && \
    apt-get update && \
    apt-get install -y --no-install-recommends google-chrome-stable && \
    apt-get clean && rm -rf /var/lib/apt/lists/*

RUN pip3 install --break-system-packages \
    youtube-transcript-api \
    yt-dlp \
    requests \
    selenium \
    webdriver-manager \
    rich

COPY scripts/ytbsd.py /app/scripts/ytbsd.py
COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
