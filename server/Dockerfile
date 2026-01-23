# Build stage
FROM golang:1.25-alpine AS builder

# Install build dependencies
RUN apk add --no-cache git gcc musl-dev

WORKDIR /build

# Copy go mod files first for better caching
COPY go.mod go.sum ./
RUN go mod download

# Copy the entire server directory structure
COPY . .

# Build the binary
RUN CGO_ENABLED=1 GOOS=linux go build -a -installsuffix cgo -o microsms .

# Runtime stage
FROM alpine:latest

# Install runtime dependencies including bash
RUN apk --no-cache add ca-certificates sqlite-libs bash

WORKDIR /app

# Copy the binary from builder
COPY --from=builder /build/microsms .

# Copy config files
COPY --from=builder /build/config.yaml ./config.yaml
COPY --from=builder /build/config ./config/

# Copy source files
COPY --from=builder /build/*.go ./
COPY --from=builder /build/config ./config/
COPY --from=builder /build/models ./models/
COPY --from=builder /build/routes ./routes/
COPY --from=builder /build/helpers ./helpers/
COPY --from=builder /build/go.mod ./go.mod
COPY --from=builder /build/go.sum ./go.sum

# Create directory for database
RUN mkdir -p /app/data

# Conditionally copy database from root of source directory if BUILD_WITH_DB is set
ARG BUILD_WITH_DB=""

# Try to copy the database file (the glob pattern makes it optional - won't fail if missing)
COPY --from=builder /build/smsrequest.d[b] /app/data/

# Validate: if BUILD_WITH_DB is set, ensure file was copied; otherwise remove it
RUN if [ -n "$BUILD_WITH_DB" ]; then \
        if [ -f /app/data/smsrequest.db ]; then \
            echo "Database copied from source directory"; \
        else \
            echo "ERROR: BUILD_WITH_DB set but smsrequest.db not found in source root!" >&2; \
            exit 1; \
        fi; \
    else \
        rm -f /app/data/smsrequest.db; \
        echo "Starting with fresh database"; \
    fi

# Expose the port
EXPOSE 8080

# Set environment variables with defaults
# Let these be set by config.yaml
#ENV MICROSMS_SERVER_PORT=8080
#ENV MICROSMS_SERVER_HOST=0.0.0.0
#ENV MICROSMS_DATABASE_PATH=/app/data/smsrequest.db

# Run the binary
CMD ["./microsms"]