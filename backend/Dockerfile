FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace
COPY gradle gradle
COPY gradlew settings.gradle build.gradle ./
COPY src src
RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar app.jar
COPY conf conf
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
