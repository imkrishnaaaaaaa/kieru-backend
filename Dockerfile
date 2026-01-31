# --- STAGE 1: THE BUILDER (The Kitchen) ---
# We start with a heavy image that has Maven and Java installed.
# We call this stage "build".
FROM maven:3.9.6-eclipse-temurin-17 AS build

# Set the working directory inside the container to /app
WORKDIR /app

# 1. Copy only the pom.xml first
# Why? Docker caches layers. If you change code but not dependencies,
# Docker skips re-downloading the internet (mvn dependency:go-offline).
COPY pom.xml .
# (Optional optimization step: RUN mvn dependency:go-offline)

# 2. Copy the rest of your source code
COPY src ./src

# 3. Build the JAR file
# -DskipTests: We skip tests here because CI/CD usually handles them,
# and we want the build to be fast for deployment.
RUN mvn clean package -DskipTests


# --- STAGE 2: THE RUNNER (The Dining Room) ---
# We start FRESH with a tiny, lightweight Java Runtime image (Alpine Linux).
# We do NOT include Maven here. Why? Security and Size.
# A hacker can't use Maven to download malware if Maven isn't there.
FROM eclipse-temurin:17-jdk-alpine

# Set working directory
WORKDIR /app

# Copy ONLY the compiled JAR file from the "build" stage.
# We leave behind all the source code, Maven files, and junk.
COPY --from=build /app/target/*.jar app.jar

# Expose Port 8080 (Informational only, tells Render where to send traffic)
EXPOSE 8080

# The command to start the app when the container spins up
ENTRYPOINT ["java", "-jar", "app.jar"]