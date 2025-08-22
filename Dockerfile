# Runtime-образ (glibc, не Alpine — полезно для native-библиотек)
FROM eclipse-temurin:21-jre

# Рабочая папка
WORKDIR /app

# Параметры JVM (опционально)
ENV TZ=Europe/Moscow \
    LANG=C.UTF-8 \
    LC_ALL=C.UTF-8 \
    JAVA_TOOL_OPTIONS="-Duser.timezone=Europe/Moscow" \
    SPRING_PROFILES_ACTIVE=prod

RUN mkdir -p /data/tdlib && \
    useradd -r -s /bin/false appuser && \
    chown -R appuser:appuser /app /data/tdlib

VOLUME ["/data/tdlib"]

COPY --chown=appuser:appuser target/IlluminatiAdminBot-0.0.1-SNAPSHOT.jar /app/app.jar

USER appuser

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]