FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /workspace/target/demo-0.0.1-SNAPSHOT.jar app.jar

# Keep upload directories available in the container filesystem.
RUN mkdir -p /app/uploads/avatars /app/uploads/task-attachments

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]