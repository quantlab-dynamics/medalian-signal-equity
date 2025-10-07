FROM openjdk:17-jdk-slim

RUN apt-get update && apt-get install -y dmidecode

WORKDIR /app

COPY app/build/libs/app-0.0.1-SNAPSHOT.jar /app/app-0.0.1-SNAPSHOT.jar

CMD ["java", "-jar", "/app/app-0.0.1-SNAPSHOT.jar"]
