FROM maven:3.9-eclipse-temurin-11 AS builder

WORKDIR /build

# Copy parent POM
COPY pom.xml ./pom.xml

# Copy all module POMs (Maven needs to see all modules)
COPY producer/pom.xml ./producer/pom.xml
COPY spark-streaming/pom.xml ./spark-streaming/pom.xml
COPY spark-batch/pom.xml ./spark-batch/pom.xml

# Copy only this module's source
COPY spark-batch/src ./spark-batch/src

# Build the specific module
RUN mvn clean package -DskipTests -pl spark-batch -am

FROM eclipse-temurin:11-jre

WORKDIR /app

COPY --from=builder /build/spark-batch/target/spark-batch-*.jar /app/spark-batch.jar

ENV POSTGRES_URL=jdbc:postgresql://postgres:5432/analytics
ENV POSTGRES_USER=analytics
ENV POSTGRES_PASSWORD=secret

ENTRYPOINT ["java", "-Xmx2g", "-jar", "/app/spark-batch.jar"]
