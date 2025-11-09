FROM openjdk:25-ea-25-jdk-slim
WORKDIR /app
COPY target/GatewayAPI-0.0.1-SNAPSHOT.jar gateway-service.jar
ENTRYPOINT ["java", "-jar", "gateway-service.jar"]
EXPOSE 8081