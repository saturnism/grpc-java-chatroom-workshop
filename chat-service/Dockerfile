FROM openjdk:8u131-jre-alpine

COPY target/lib/* /app/lib/
COPY target/chat-service-1.0-SNAPSHOT.jar /app/app.jar

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
