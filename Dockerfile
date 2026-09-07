# Construccion y ejecucion en dos etapas: la imagen final no lleva Maven, ni el
# codigo fuente, ni el repositorio de dependencias. Solo el JAR y un JRE.

FROM eclipse-temurin:21-jdk AS construccion
WORKDIR /build

# Las dependencias se resuelven en una capa aparte: mientras el pom no cambie,
# Docker reutiliza esta capa y la construccion no vuelve a descargarlas.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -B -q dependency:go-offline

COPY src/ src/
# Las pruebas necesitan Docker (Testcontainers), que no existe dentro de esta
# construccion. Se ejecutan en el equipo de desarrollo con ./mvnw verify.
RUN ./mvnw -B -q clean package -DskipTests


FROM eclipse-temurin:21-jre
WORKDIR /app

# Usuario sin privilegios: si algo se cuela, no se cuela como root.
RUN useradd --system --create-home --shell /usr/sbin/nologin sistema
USER sistema

COPY --from=construccion --chown=sistema:sistema /build/target/sistema-juridico.jar app.jar

# Render asigna el puerto por la variable PORT y no siempre es el mismo.
ENV PORT=8080
EXPOSE 8080

# El perfil se pasa por entorno; ProfileGuard impide arrancar sin uno, de modo
# que un despliegue mal configurado falla en vez de tomar valores por defecto.
ENTRYPOINT ["sh", "-c", "exec java -XX:MaxRAMPercentage=75 -jar app.jar --server.port=${PORT}"]
