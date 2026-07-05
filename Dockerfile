FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN sh mvnw -q dependency:go-offline
COPY src src
RUN sh mvnw -q package -DskipTests

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
