"""Runs full catalog scraper daily at 6AM Argentina time."""
import subprocess, time, sys
from datetime import datetime
import pytz

AR_TZ = pytz.timezone("America/Argentina/Buenos_Aires")

print("Cron runner started. Will run full catalog scraper daily at 6:00 AM Argentina time.")
print(f"Current time: {datetime.now(AR_TZ).strftime('%Y-%m-%d %H:%M:%S')}")

last_run_date = None
last_run_hour = None

while True:
    now = datetime.now(AR_TZ)
    today = now.date()

    # Run at 6AM and 20PM if not already run today
    if now.hour in (6, 20) and (last_run_date != today or last_run_hour != now.hour):
        print(f"\n=== Running full catalog scraper at {now.strftime('%Y-%m-%d %H:%M:%S')} ===")
        try:
            result = subprocess.run(
                [sys.executable, "load_full_catalog.py"],
                capture_output=True, text=True, timeout=14400  # 4 hour timeout
            )
            print(result.stdout)
            if result.stderr:
                print(f"STDERR: {result.stderr}")
        except subprocess.TimeoutExpired:
            print("WARNING: Scraper timed out after 4 hours")
        except Exception as e:
            print(f"Error: {e}")
        last_run_date = today
        last_run_hour = now.hour
        print(f"=== Done ===\n")

    time.sleep(60)  # Check every minute
