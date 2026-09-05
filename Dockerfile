# One image serving both halves: the React app is built into the Spring Boot
# jar, so there is a single service, a single origin, and no CORS.

FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

COPY backend backend
COPY frontend frontend

# -Pwebapp runs the npm build and copies dist/ into the jar's static resources.
RUN mvn -f backend/pom.xml -B -Pwebapp clean package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app

# Run as a non-root user rather than root.
RUN useradd --system --create-home --uid 10001 lifelink
USER lifelink

COPY --from=build /build/backend/target/*.jar app.jar

EXPOSE 8080

# MaxRAMPercentage keeps the heap inside a small container's memory limit.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
