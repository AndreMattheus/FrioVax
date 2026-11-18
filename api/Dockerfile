FROM alpine:3.22 AS local-auth
RUN apk add --no-cache openssl
COPY docker/init-auth.sh /init-auth.sh
RUN sed -i 's/\r$//' /init-auth.sh
ENTRYPOINT ["sh", "/init-auth.sh"]

FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace/api
COPY api/pom.xml .
RUN mvn --batch-mode dependency:go-offline
COPY api/src src
COPY protos /workspace/protos
RUN mvn --batch-mode -DskipTests package

FROM eclipse-temurin:25-jre
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 appuser && useradd --uid 10001 --gid 10001 --no-create-home appuser
WORKDIR /app
COPY docker/api-entrypoint.sh /app/entrypoint.sh
RUN sed -i 's/\r$//' /app/entrypoint.sh
COPY --from=build /workspace/api/target/quarkus-app/lib/ /app/lib/
COPY --from=build /workspace/api/target/quarkus-app/*.jar /app/
COPY --from=build /workspace/api/target/quarkus-app/app/ /app/app/
COPY --from=build /workspace/api/target/quarkus-app/quarkus/ /app/quarkus/
EXPOSE 8080
USER 10001:10001
ENTRYPOINT ["sh", "/app/entrypoint.sh"]

