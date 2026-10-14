# =============================================================
#  Mercado Express - imagem para deploy (Render, Fly.io, etc.)
#  Etapa 1 compila com Maven; etapa 2 roda só com o JRE.
# =============================================================

# ---------- Etapa 1: build ----------
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /app

# Baixa as dependências primeiro (aproveita o cache do Docker)
COPY pom.xml .
RUN mvn -B -q dependency:go-offline -DskipTests || true

COPY src ./src
RUN mvn -B -q -DskipTests package

# ---------- Etapa 2: execução ----------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Não roda como root
RUN addgroup -S app && adduser -S app -G app
COPY --from=build /app/target/mercado-express.jar app.jar
USER app

# A plataforma informa a porta pela variável PORT (padrão 8080)
EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
