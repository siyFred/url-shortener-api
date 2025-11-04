# stage 1
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline

COPY src ./src
RUN mvn package -DskipTests -B

# stage 2

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

EXPOSE 8080

COPY --from=build /app/target/url-shortener-*.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]