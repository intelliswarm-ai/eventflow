FROM maven:3.9-eclipse-temurin-11 AS builder

WORKDIR /build

# Copy parent POM
COPY pom.xml ./pom.xml

# Copy all module POMs (Maven needs to see all modules)
COPY producer/pom.xml ./producer/pom.xml
COPY spark-streaming/pom.xml ./spark-streaming/pom.xml
COPY spark-batch/pom.xml ./spark-batch/pom.xml

# Copy only this module's source
COPY producer/src ./producer/src

# Build the specific module
RUN mvn clean package -DskipTests -pl producer -am

FROM eclipse-temurin:11-jre

WORKDIR /app

COPY --from=builder /build/producer/target/producer-*.jar /app/producer.jar

ENV KAFKA_BOOTSTRAP_SERVERS=kafka:9092
ENV EVENT_DELAY_MS=100

ENTRYPOINT ["java", "-jar", "/app/producer.jar"]
