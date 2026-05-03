FROM maven:3.9.9-eclipse-temurin-21-alpine AS build
WORKDIR /app

COPY pom.xml .
COPY lombok.config .
RUN mvn -B -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN apk add --no-cache curl

COPY --from=build /app/target/gateway-service-*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
