import threading
import time

def start_server():
    from absenku_app import app, init_db

    # Start Flask first so the APK can open even when internet/Supabase
    # is temporarily unavailable. Database initialization happens after.
    def run():
        app.run(host="127.0.0.1", port=5000, debug=False,
                threaded=True, use_reloader=False)

    threading.Thread(target=run, daemon=True).start()
    time.sleep(1)

    def initialize():
        try:
            init_db()
        except Exception as e:
            print("Supabase init error:", e)

    threading.Thread(target=initialize, daemon=True).start()
    return True
