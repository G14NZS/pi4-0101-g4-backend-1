# Build com Maven + JDK 21; imagem final só com o JRE e o jar.
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/backend1.jar backend1.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "backend1.jar"]
