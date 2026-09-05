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

# Tuned for a 512 MB container. A 75% heap leaves too little for metaspace,
# code cache and thread stacks, so the container gets OOM-killed without the
# JVM reporting anything useful. SerialGC costs less overhead than G1 at this
# size, and smaller stacks matter with Tomcat, Quartz and AMQP listener threads.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=55", "-XX:+UseSerialGC", "-Xss512k", "-XX:MaxMetaspaceSize=128m", "-jar", "/app/app.jar"]
