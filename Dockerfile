# =====================================================================
# Imagem do backend Searchoteca (usada pelo Railway).
# Etapa 1 compila com o JDK; etapa 2 só roda o .jar com o JRE (imagem menor).
# Os testes rodam no GitHub Actions, não aqui.
# =====================================================================

# ---------- etapa 1: build ----------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Maven Wrapper + pom primeiro: se só o código mudar, as dependências vêm do cache do Docker
COPY mvnw pom.xml ./
COPY .mvn .mvn
# o mvnw pode chegar com quebra de linha do Windows (CRLF) e sem permissão de execução
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw -B -ntp dependency:go-offline

COPY src src
RUN ./mvnw -B -ntp -Dmaven.test.skip=true package \
    && cp target/*.jar app.jar

# ---------- etapa 2: execução ----------
FROM eclipse-temurin:21-jre
WORKDIR /app

# não roda como root dentro do container
RUN useradd --system --uid 1001 --no-create-home spring
USER spring

COPY --from=build /app/app.jar app.jar

# O Railway define PORT; o application.yaml usa ${PORT:8080}
EXPOSE 8080

# MaxRAMPercentage: a JVM respeita o limite de memória do plano (em vez de achar que tem a máquina toda)
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Duser.timezone=America/Sao_Paulo"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
