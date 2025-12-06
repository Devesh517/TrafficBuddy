FROM ubuntu:latest
LABEL authors="asus"
#
# Build stage
#
FROM maven:3.8.3-openjdk-17 AS build
COPY . .
RUN mvn clean install -DskipTests

#
# Package stage
#
FROM eclipse-temurin:17-jdk

# Copy your actual jar file from the build
COPY --from=build /target/trafficbuddy-0.0.1-SNAPSHOT.jar app.jar

# Render automatically injects PORT, so expose is optional
EXPOSE 8080

# Use the PORT variable provided by Render
ENV PORT=8080

ENTRYPOINT ["java","-jar","app.jar"]

ENTRYPOINT ["top", "-b"]