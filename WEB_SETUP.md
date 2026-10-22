# RupeyaWise web development

The web app is a Django project at the repository root. The Android module is separate and is not required to run the web app.

## First-time setup

The project uses the local `.venv` environment and `requirements.txt`:

```bash
source .venv/bin/activate
cp .env.example .env
```

The local defaults use SQLite (`db.sqlite3`), Django debug mode, and console email output. `.env` is ignored by Git.

## Run the web app

```bash
source .venv/bin/activate
python manage.py migrate
python manage.py runserver
```

Open <http://127.0.0.1:8000/>. The health endpoint is <http://127.0.0.1:8000/health/>.

## Static files

For a production-style static build, run:

```bash
python manage.py collectstatic --noinput
```

