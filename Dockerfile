# Build stage
FROM maven:3.9.6-eclipse-temurin-21 AS build
COPY src /home/app/src
COPY pom.xml /home/app
RUN mvn -f /home/app/pom.xml clean package -DskipTests

# Package stage
FROM eclipse-temurin:21-jre
COPY --from=build /home/app/target/*.jar /usr/local/lib/cns-app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/usr/local/lib/cns-app.jar"]
