FROM maven:3.9-eclipse-temurin-11 AS builder

WORKDIR /build

# Copy parent POM
COPY pom.xml ./pom.xml

# Copy all module POMs (Maven needs to see all modules)
COPY producer/pom.xml ./producer/pom.xml
COPY spark-streaming/pom.xml ./spark-streaming/pom.xml
COPY spark-batch/pom.xml ./spark-batch/pom.xml

# Copy only this module's source
COPY spark-streaming/src ./spark-streaming/src

# Build the specific module
RUN mvn clean package -DskipTests -pl spark-streaming -am

FROM eclipse-temurin:11-jre

WORKDIR /app

COPY --from=builder /build/spark-streaming/target/spark-streaming-*.jar /app/spark-streaming.jar

ENV KAFKA_BOOTSTRAP_SERVERS=kafka:9092
ENV POSTGRES_URL=jdbc:postgresql://postgres:5432/analytics
ENV POSTGRES_USER=analytics
ENV POSTGRES_PASSWORD=secret

RUN mkdir -p /tmp/checkpoint

ENTRYPOINT ["java", "-Xmx2g", "-jar", "/app/spark-streaming.jar"]
