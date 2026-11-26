# ==========================================
# ETAPA 1: Compilacion del proyecto
# ==========================================

FROM maven:3.9-eclipse-temurin-11 AS build

WORKDIR /app

COPY pom.xml .

RUN mvn -B dependency:go-offline

COPY src ./src

RUN mvn -B clean package -DskipTests

RUN mvn -B dependency:copy-dependencies \
    -DincludeScope=runtime \
    -DoutputDirectory=/app/lib


# ==========================================
# ETAPA 2: Entorno de ejecucion JADE
# ==========================================

FROM eclipse-temurin:11-jre

WORKDIR /app

# Bibliotecas graficas para Java Swing y AWT

RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        libx11-6 \
        libxext6 \
        libxi6 \
        libxrender1 \
        libxtst6 \
        libxrandr2 \
        libxfixes3 \
        libfreetype6 \
        fontconfig \
        fonts-dejavu-core && \
    rm -rf /var/lib/apt/lists/*

# Configuracion grafica Java

ENV JAVA_TOOL_OPTIONS="-Djava.awt.headless=false"

# Copiar aplicacion compilada

COPY --from=build \
    /app/target/sma-jade-docker-1.0.0-SNAPSHOT.jar \
    /app/app.jar

COPY --from=build /app/lib /app/lib

# Puertos de comunicacion JADE

EXPOSE 1099
EXPOSE 7778

# Inicio de la plataforma

ENTRYPOINT ["java", "-cp", "/app/app.jar:/app/lib/*", "jade.Boot"]
