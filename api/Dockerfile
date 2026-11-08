FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace/api
COPY api/pom.xml .
RUN mvn --batch-mode dependency:go-offline
COPY api/src src
COPY protos /workspace/protos
RUN mvn --batch-mode -DskipTests package

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /workspace/api/target/quarkus-app/lib/ /app/lib/
COPY --from=build /workspace/api/target/quarkus-app/*.jar /app/
COPY --from=build /workspace/api/target/quarkus-app/app/ /app/app/
COPY --from=build /workspace/api/target/quarkus-app/quarkus/ /app/quarkus/
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/quarkus-run.jar"]

