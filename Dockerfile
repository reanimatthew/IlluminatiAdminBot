# Runtime-образ (glibc, не Alpine — полезно для native-библиотек)
FROM eclipse-temurin:24-jre

# Рабочая папка
WORKDIR /app

# Параметры JVM (опционально)
ENV JAVA_OPTS="-Xms256m -Xmx512m" \
    TZ=Europe/Moscow \
    SPRING_PROFILES_ACTIVE=prod

# Кладём заранее собранный fatjar (например, target/app.jar)
COPY target/IlluminatiAdminBot-0.0.1-SNAPSHOT.jar /app/app.jar

# Непривилегированный пользователь
RUN useradd -r -s /bin/false appuser && chown appuser:appuser /app/app.jar
USER appuser

EXPOSE 8080
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar /app/app.jar"]