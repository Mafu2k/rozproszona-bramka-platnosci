FROM eclipse-temurin:21.0.13_9-jdk-noble AS budowanie
WORKDIR /zrodla
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -q -B dependency:go-offline
COPY src src
RUN ./mvnw -q -B -DskipTests package

FROM eclipse-temurin:21.0.13_9-jre-noble
RUN useradd --system --uid 10001 --no-create-home bramka
WORKDIR /aplikacja
COPY --from=budowanie /zrodla/target/bramka-platnosci-*.jar aplikacja.jar
USER bramka
EXPOSE 8080 8583
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-Duser.timezone=UTC", "-jar", "aplikacja.jar"]
