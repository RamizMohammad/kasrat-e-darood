FROM python:3.12-slim

ENV PYTHONUNBUFFERED=1 PYTHONDONTWRITEBYTECODE=1
WORKDIR /code

RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY . .

# Run as an unprivileged user, not root.
RUN useradd --system --uid 10001 --no-create-home app && chown -R app /code
USER app

EXPOSE 8010
HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
    CMD curl -f http://localhost:8010/health || exit 1

# Apply any pending Alembic migrations (no-op if already at head), then start the API.
# (docker-compose.yml sets the same command.)
CMD ["sh", "-c", "alembic upgrade head && exec uvicorn app.main:app --host 0.0.0.0 --port 8010 --no-server-header --no-access-log"]
