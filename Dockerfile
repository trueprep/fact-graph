# ============================================
# IRS Fact Graph - Multi-stage Docker Build
# ============================================

# Stage 1: Build the Fact Graph JVM artifact
FROM eclipse-temurin:21-jdk AS builder

# Install sbt (Scala Build Tool)
RUN apt-get update && apt-get install -y curl gnupg && \
    echo "deb https://repo.scala-sbt.org/scalasbt/debian all main" | tee /etc/apt/sources.list.d/sbt.list && \
    curl -sL "https://keyserver.ubuntu.com/pks/lookup?op=get&search=0x99E82A75642AC823" | apt-key add && \
    apt-get update && apt-get install -y sbt && \
    apt-get clean && rm -rf /var/lib/apt/lists/*

# Set working directory
WORKDIR /build

# Copy project files
COPY build.sbt .
COPY project/ project/
COPY shared/ shared/
COPY jvm/ jvm/
COPY js/ js/

# Build and publish locally (creates JAR in local ivy cache)
RUN sbt clean compile "factGraphJVM/package" "factGraphJVM/publishLocal"

# Stage 2: Build the REST API wrapper
FROM eclipse-temurin:21-jdk AS api-builder

# Install sbt
RUN apt-get update && apt-get install -y curl gnupg && \
    echo "deb https://repo.scala-sbt.org/scalasbt/debian all main" | tee /etc/apt/sources.list.d/sbt.list && \
    curl -sL "https://keyserver.ubuntu.com/pks/lookup?op=get&search=0x99E82A75642AC823" | apt-key add && \
    apt-get update && apt-get install -y sbt && \
    apt-get clean && rm -rf /var/lib/apt/lists/*

WORKDIR /api

# Copy the fact-graph build artifacts
COPY --from=builder /root/.ivy2 /root/.ivy2
COPY --from=builder /root/.cache /root/.cache

# Copy API wrapper project (see section 1.2)
COPY api/ . 

# Build fat JAR with assembly plugin
RUN sbt assembly

# Stage 3: Runtime
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# Install curl for container health checks
RUN apt-get update && apt-get install -y curl && \
    apt-get clean && rm -rf /var/lib/apt/lists/*

# Copy the assembled JAR
COPY --from=api-builder /api/target/scala-3.3.6/factgraph-api-assembly-*.jar ./factgraph-api.jar

# Copy any fact dictionary XML files you need
COPY dictionaries/ ./dictionaries/
COPY fact_dictionaries/ ./fact_dictionaries/

# Expose API port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \
    CMD curl -f http://localhost:8080/health || exit 1

# Run the API server
ENTRYPOINT ["java", "-jar", "factgraph-api.jar"]