# Pathora backend image for blitz.cloud public Git repository builds.
# The build context is the Pathora repository root.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY backend/pom.xml ./pom.xml
COPY backend/src ./src
RUN mvn -B -ntp -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /build/target/*.jar /app/pathora-backend.jar
EXPOSE 10000
ENTRYPOINT ["java", "-jar", "/app/pathora-backend.jar"]
