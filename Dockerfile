# Single image: the React app is built into the Spring Boot jar, so one service
# serves both and there is no CORS between them.

FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# Dependencies first, so a source-only change does not re-download them.
COPY backend/pom.xml backend/pom.xml
RUN mvn -f backend/pom.xml -B dependency:go-offline -DskipTests

COPY frontend frontend
COPY backend backend
RUN mvn -f backend/pom.xml -B -Pwebapp clean package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /build/backend/target/*.jar app.jar

# Hosts assign the port at runtime.
ENV SERVER_PORT=8080
EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java -XX:MaxRAMPercentage=75 -jar app.jar"]
