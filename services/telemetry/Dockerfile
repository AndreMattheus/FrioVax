FROM golang:1.27-alpine AS build
WORKDIR /src
COPY go.mod ./
COPY . .
RUN CGO_ENABLED=0 GOOS=linux go build -o /out/telemetry ./cmd/server

FROM alpine:3.22
RUN adduser -D -u 10001 appuser
USER appuser
COPY --from=build /out/telemetry /telemetry
EXPOSE 8081
ENTRYPOINT ["/telemetry"]

