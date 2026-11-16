FROM python:3.12-slim
ENV PYTHONUNBUFFERED=1
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY main.py version.json ./
COPY api ./api
# serviceAccountKey.json NO se copia: usar la variable FIREBASE_SERVICE_ACCOUNT en el hosting.
RUN useradd --create-home appuser
USER appuser
CMD ["sh", "-c", "uvicorn main:app --host 0.0.0.0 --port ${PORT:-8000}"]
