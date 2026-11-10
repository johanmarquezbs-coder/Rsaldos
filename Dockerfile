# syntax=docker/dockerfile:1

FROM eclipse-temurin:17-jdk-jammy AS build

ARG CMDLINE_TOOLS_VERSION=11076708
ARG GRADLE_VERSION=8.7
ENV ANDROID_HOME=/opt/android-sdk \
    ANDROID_SDK_ROOT=/opt/android-sdk \
    GRADLE_USER_HOME=/root/.gradle
ENV PATH=${PATH}:${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools:/opt/gradle/bin

RUN apt-get update \
 && apt-get install -y --no-install-recommends unzip wget ca-certificates \
 && rm -rf /var/lib/apt/lists/*

RUN mkdir -p ${ANDROID_HOME}/cmdline-tools \
 && wget -q https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_VERSION}_latest.zip -O /tmp/cmdline.zip \
 && unzip -q /tmp/cmdline.zip -d ${ANDROID_HOME}/cmdline-tools \
 && mv ${ANDROID_HOME}/cmdline-tools/cmdline-tools ${ANDROID_HOME}/cmdline-tools/latest \
 && rm /tmp/cmdline.zip \
 && yes | sdkmanager --licenses > /dev/null \
 && sdkmanager "platforms;android-34" "build-tools;34.0.0" "platform-tools" > /dev/null

RUN wget -q https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip -O /tmp/gradle.zip \
 && unzip -q /tmp/gradle.zip -d /opt \
 && mv /opt/gradle-${GRADLE_VERSION} /opt/gradle \
 && rm /tmp/gradle.zip

WORKDIR /workspace

COPY settings.gradle.kts build.gradle.kts gradle.properties ./
COPY app/build.gradle.kts app/proguard-rules.pro app/
RUN gradle --no-daemon -q :app:dependencies > /dev/null || true

COPY app/src app/src
RUN gradle --no-daemon assembleDebug \
 && mkdir -p /out \
 && cp app/build/outputs/apk/debug/app-debug.apk /out/rsaldos-debug.apk

FROM scratch AS export
COPY --from=build /out/ /

FROM nginx:1.27-alpine AS web
LABEL org.opencontainers.image.title="R-Saldos APK" \
      org.opencontainers.image.description="Página de descarga del APK de la tienda virtual R-Saldos"
COPY docker/nginx.conf /etc/nginx/conf.d/default.conf
COPY docker/index.html /usr/share/nginx/html/index.html
COPY --from=build /out/rsaldos-debug.apk /usr/share/nginx/html/rsaldos.apk
EXPOSE 80
HEALTHCHECK --interval=30s --timeout=3s CMD wget -qO- http://127.0.0.1/health || exit 1
