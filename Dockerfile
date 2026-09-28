# ------------------------------------------------------------------
# Un solo Dockerfile para ambos microservicios (build multi-etapa).
#   docker build --build-arg SERVICE=card-vault-service -t card-vault-service .
#   docker build --build-arg SERVICE=payment-service    -t payment-service .
# ------------------------------------------------------------------

# Etapa 1: compilar con Maven (esta imagen pesada NO llega a producción)
FROM maven:3.9-eclipse-temurin-21 AS build
ARG SERVICE
WORKDIR /src
# Copiar primero solo los pom.xml aprovecha la caché de Docker:
# si no cambian las dependencias, no se vuelven a descargar.
COPY pom.xml .
COPY card-vault-service/pom.xml card-vault-service/
COPY payment-service/pom.xml payment-service/
RUN mvn -B -q -pl ${SERVICE} dependency:go-offline
COPY . .
RUN mvn -B -q -pl ${SERVICE} package -DskipTests

# Etapa 2: imagen final mínima (solo JRE + el JAR)
FROM eclipse-temurin:21-jre-alpine
ARG SERVICE
# PCI-DSS req. 2 / buenas prácticas: nunca correr como root dentro del contenedor
RUN addgroup -S -g 10001 app && adduser -S -u 10001 -G app app
WORKDIR /app
COPY --from=build /src/${SERVICE}/target/${SERVICE}-1.0.0.jar app.jar
USER app
# MaxRAMPercentage: la JVM respeta el límite de memoria del contenedor/pod
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
