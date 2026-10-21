# Imagen del backend (Spring Boot). Sirve también la pantalla de Angular si está en src/main/resources/static
# (se copia ahí con el script de empaquetado; si no está, la imagen es solo API).
#
#   docker build -t planilla .
#   docker run -p 8081:8081 -e DB_URL=... -e DB_USER=... -e DB_PASSWORD=... -e JWT_SECRET=... -e ADMIN_PASSWORD=... planilla

FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/Planilla-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8081
# Los hostings (Render, Railway...) definen PORT; la aplicación lo lee sola
ENTRYPOINT ["java", "-jar", "app.jar"]
