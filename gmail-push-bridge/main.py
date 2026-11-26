import base64
import json
import os
import threading
from pathlib import Path

from fastapi import FastAPI, HTTPException, Request

app = FastAPI(title="Walter Gmail Push Bridge")
TOKEN = os.environ.get("BRIDGE_TOKEN", "")
STATE_FILE = Path(os.environ.get("STATE_FILE", "/tmp/gmail-bridge.json"))
LOCK = threading.Lock()


def load_state():
    if STATE_FILE.exists():
        try:
            return json.loads(STATE_FILE.read_text(encoding="utf-8"))
        except Exception:
            return {}
    return {}


def save_state(state):
    STATE_FILE.write_text(json.dumps(state, ensure_ascii=False), encoding="utf-8")


def authorized(request: Request):
    if not TOKEN:
        return True
    supplied = request.headers.get("X-Bridge-Token", "")
    if supplied != TOKEN:
        raise HTTPException(status_code=401, detail="invalid bridge token")
    return True


@app.get("/healthz")
def healthz():
    return {"ok": True}


@app.post("/gmail/push")
async def gmail_push(request: Request):
    authorized(request)
    body = await request.json()
    message = body.get("message", {})
    encoded = message.get("data")
    if not encoded:
        return {"ok": True, "ignored": True}
    try:
        payload = json.loads(base64.b64decode(encoded + "==="))
        email = payload["emailAddress"]
        history_id = str(payload["historyId"])
    except Exception as exc:
        raise HTTPException(status_code=400, detail=f"bad Pub/Sub payload: {exc}")
    with LOCK:
        state = load_state()
        events = state.setdefault(email, [])
        if history_id not in events:
            events.append(history_id)
            state[email] = events[-100:]
            save_state(state)
    return {"ok": True}


@app.get("/gmail/events")
def gmail_events(email: str, after: str = "0", request: Request = None):
    authorized(request)
    try:
        after_id = int(after)
    except ValueError:
        after_id = 0
    state = load_state()
    events = [int(value) for value in state.get(email, []) if int(value) > after_id]
    return {"events": events}
