FROM python:3.12-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY load_vtex.py load_coto.py load_full_catalog.py ./
CMD ["python", "load_full_catalog.py"]
