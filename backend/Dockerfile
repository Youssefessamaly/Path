# Pathora Spring Boot 4 / Java 21 production image (build context = backend/).
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml ./
COPY src ./src
RUN mvn -B -ntp -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /build/target/*.jar /app/pathora-backend.jar
EXPOSE 10000
ENTRYPOINT ["java", "-jar", "/app/pathora-backend.jar"]
