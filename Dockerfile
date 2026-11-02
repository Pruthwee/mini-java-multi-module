FROM maven:3.9.4-eclipse-temurin-17 AS builder

WORKDIR /workspace

COPY pom.xml .
COPY backend/pom.xml backend/
COPY ui/pom.xml ui/
COPY backend/src backend/src
COPY ui/src ui/src

RUN mvn -B -pl backend -am clean package -DskipTests

FROM amazoncorretto:17

ENV TZ=UTC \
    LANG=en_US.UTF-8 \
    LC_ALL=en_US.UTF-8 \
    JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

RUN addgroup --system appgroup && adduser --system --ingroup appgroup appuser

WORKDIR /app

COPY --from=builder /workspace/backend/target/minijava-ui-backend.jar app.jar

USER appuser

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
