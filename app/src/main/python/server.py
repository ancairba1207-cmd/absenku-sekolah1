import threading
import time

def start_server():
    from absenku_app import init_db, app
    init_db()
    def run():
        app.run(host="127.0.0.1", port=5000, debug=False,
                threaded=True, use_reloader=False)
    threading.Thread(target=run, daemon=True).start()
    time.sleep(1)
    return True
