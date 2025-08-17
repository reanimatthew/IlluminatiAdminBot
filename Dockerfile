FROM eclipse-temurin:24-jre-jammy
WORKDIR /app

# имя jar можно переопределить при сборке
ARG JAR_FILE=IlluminatiAdminBot-0.0.1-SNAPSHOT.jar
COPY ${JAR_FILE} /app/app.jar

ENV JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75" \
    TZ=Etc/UTC
# нерутовый пользователь
RUN useradd -r -u 10001 appuser
USER appuser

EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]