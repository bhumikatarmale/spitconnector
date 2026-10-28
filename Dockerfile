# Stage 1: Build Java application
FROM eclipse-temurin:17-jdk-jammy AS builder

WORKDIR /build

# Copy source, libraries, and web assets
COPY MiniProject/src ./src
COPY MiniProject/lib ./lib
COPY MiniProject/web ./web

# Compile all Java sources recursively
RUN mkdir -p bin database && \
    javac -cp "lib/*:src" -d bin $(find src -name "*.java")

# Stage 2: Minimal runtime image
FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

# Copy compiled classes, libraries, and web assets from builder stage
COPY --from=builder /build/bin ./bin
COPY --from=builder /build/lib ./lib
COPY --from=builder /build/web ./web

# Ensure database directory exists
RUN mkdir -p /app/database

# Copy and prepare entrypoint script (normalize CRLF if built on Windows)
COPY docker-entrypoint.sh /app/docker-entrypoint.sh
RUN sed -i 's/\r$//' /app/docker-entrypoint.sh && chmod +x /app/docker-entrypoint.sh

# Default environment configuration
ENV PORT=8080 \
    PEER_ID=DOCKER_NODE

# Expose Web Dashboard (8080), TCP Streaming (5001), and Multicast UDP Beacon (5354)
EXPOSE 8080 5001 5354/udp

ENTRYPOINT ["/app/docker-entrypoint.sh"]
CMD ["web"]
