# Multi-stage build so the final image only carries the JRE + the fat jar,
# not the JDK/Maven toolchain used to build it. Railway auto-detects this
# file at the repo root and builds from it instead of Nixpacks - every push
# to the connected branch triggers a fresh build + deploy of this image.

# ---- Build stage ----
FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /app

# Dependencies first, on their own layer, so a source-only change doesn't
# force Docker to re-download the whole dependency tree on every build.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -q -B dependency:go-offline

COPY src/ src/
RUN ./mvnw -q -B clean package -DskipTests

# ---- Runtime stage ----
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

# Railway assigns $PORT at deploy time (it varies) and routes traffic to it -
# Spring Boot itself only understands --server.port/SERVER_PORT, so the
# substitution has to happen at container start, in shell form, not as a
# fixed ENV baked into the image. MaxRAMPercentage keeps the JVM heap sized
# sanely off the container's actual memory limit instead of the JVM's
# default (25%, too conservative for a small single-app container).
ENTRYPOINT ["sh", "-c", "java -XX:MaxRAMPercentage=75.0 -jar app.jar --server.port=${PORT:-8080}"]
