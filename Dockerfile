# Build stage
FROM maven:3.9.6-eclipse-temurin-21 AS build

# 1. Copy pom.xml first to leverage Docker cache for dependencies
COPY pom.xml /home/app/pom.xml
RUN mvn -f /home/app/pom.xml dependency:go-offline

# 2. Copy the source code
COPY src /home/app/src

# 3. Build the application (dependencies are already cached)
RUN mvn -f /home/app/pom.xml clean package -DskipTests

# Package stage
FROM eclipse-temurin:21-jre
COPY --from=build /home/app/target/*.jar /usr/local/lib/cns-app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/usr/local/lib/cns-app.jar"]
