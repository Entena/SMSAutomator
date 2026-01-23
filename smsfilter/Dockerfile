FROM python:3.12-slim-trixie AS base

FROM base AS builder_base

RUN python -m venv /opt/venv

ENV PATH="/opt/venv/bin:$PATH"

FROM builder_base AS builder

ARG UV_CACHE_DIR="/.cache/uv"

RUN apt-get update && \
    apt-get install -y --no-install-recommends \
    build-essential \
    cmake \
    git \
    && rm -rf /var/lib/apt/lists/*

RUN pip install uv==0.6.2

WORKDIR /build

# Copy all Python modules under backend
COPY backend ./backend

# Disable editable/development mode in all modules
RUN find ./backend -type f -name pyproject.toml -exec sed -i 's|develop[[:blank:]]*=[[:blank:]]*true|develop=false|g' {} \;

# Install settings module first (base dependency)
WORKDIR /build/backend/settings
ENV UV_COMPILE_BYTECODE=1
ENV UV_LINK_MODE=copy
RUN --mount=type=cache,target=${UV_CACHE_DIR} \
    uv sync --no-editable --no-dev

# Install predictor module (depends on settings)
WORKDIR /build/backend/predictor
RUN --mount=type=cache,target=${UV_CACHE_DIR} \
    uv sync --no-editable --no-dev

# Install api module (depends on predictor, which depends on settings)
WORKDIR /build/backend/api
RUN --mount=type=cache,target=${UV_CACHE_DIR} \
    uv sync --no-editable --no-dev

FROM base AS runtime

ARG APP_USERNAME=appuser
ARG APP_DIR=/app
ARG USER_UID=10001
ARG USER_GID=10001

# Install runtime dependencies
RUN apt-get update && \
    apt-get clean && \
    apt-get autoremove -y && \
    rm -rf /var/lib/apt/lists/*

# Create app user
RUN true \
    && addgroup --system --gid ${USER_GID} ${APP_USERNAME} \
    && adduser --system --uid ${USER_UID} --gid ${USER_GID} --no-create-home ${APP_USERNAME}

RUN mkdir ${APP_DIR} && chown -R ${APP_USERNAME}:${APP_USERNAME} ${APP_DIR}

# Copy venv from builder - use api's venv since it has all dependencies
COPY --from=builder --chown=${USER_UID}:${USER_GID} \
    /build/backend/api/.venv \
    /build/backend/api/.venv

# Copy all application source code from all modules
COPY --from=builder --chown=${USER_UID}:${USER_GID} \
    /build/backend/api/src \
    /build/backend/api/src

COPY --from=builder --chown=${USER_UID}:${USER_GID} \
    /build/backend/predictor/src \
    /build/backend/predictor/src

COPY --from=builder --chown=${USER_UID}:${USER_GID} \
    /build/backend/settings/src \
    /build/backend/settings/src

ENV PATH="/build/backend/api/.venv/bin:$PATH"
ENV PYTHONPATH="/build/backend/api/src:/build/backend/predictor/src:/build/backend/settings/src:$PYTHONPATH"

WORKDIR /build/backend/api

EXPOSE 8000

CMD uvicorn api.webapp:app --host 0.0.0.0 --port ${PORT:-8000} --timeout-keep-alive 1800

USER ${APP_USERNAME}
