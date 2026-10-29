"""Swarm server: HTTP API + dashboard + the 1 Hz engine loop.

Settings (environment variables):
  MQTT_HOST   broker host (default localhost; "mosquitto" in docker compose)
  MQTT_PORT   default 1883
  SITE        site id the engine runs for (default demo)
  GHOSTS      number of simulated workers at start (default 3)
  DATA_DIR    where site files and accuracy samples are kept (default ./data)
"""
import asyncio
import json
import logging
import os
import time
from contextlib import asynccontextmanager
from pathlib import Path

from fastapi import FastAPI, HTTPException, Request
from fastapi.responses import FileResponse, HTMLResponse, PlainTextResponse
from pydantic import BaseModel, ValidationError

from .coordinator import Coordinator
from .labels import labels_page
from .site import Site
from .store import AccuracySample, Store, summarise, to_csv

log = logging.getLogger("swarm")
STATIC = Path(__file__).parent / "static"


class Settings:
    def __init__(self):
        self.mqtt_host = os.environ.get("MQTT_HOST", "localhost")
        self.mqtt_port = int(os.environ.get("MQTT_PORT", "1883"))
        self.site = os.environ.get("SITE", "demo")
        self.ghosts = int(os.environ.get("GHOSTS", "3"))
        self.data_dir = os.environ.get("DATA_DIR", "./data")
        self.mqtt_enabled = os.environ.get("MQTT_ENABLED", "1") != "0"


def create_app(settings: Settings | None = None) -> FastAPI:
    cfg = settings or Settings()
    store = Store(cfg.data_dir)
    co = Coordinator(store.get_site(cfg.site), ghosts=cfg.ghosts)
    state = {"bridge": None}

    async def engine_loop():
        while True:
            started = time.time()
            try:
                out = co.tick(started)
                if state["bridge"]:
                    state["bridge"].publish(out)
            except Exception:                       # keep the loop alive whatever happens
                log.exception("engine tick failed")
            await asyncio.sleep(max(0.05, 1.0 - (time.time() - started)))

    @asynccontextmanager
    async def lifespan(app):
        if cfg.mqtt_enabled:
            from .mqtt_bridge import MqttBridge
            state["bridge"] = MqttBridge(co, cfg.mqtt_host, cfg.mqtt_port)
            state["bridge"].start()
        task = asyncio.create_task(engine_loop())
        yield
        task.cancel()
        if state["bridge"]:
            state["bridge"].stop()

    app = FastAPI(title="Swarm AMR server", lifespan=lifespan)
    app.state.coordinator = co
    app.state.store = store

    @app.get("/")
    def dashboard():
        return FileResponse(STATIC / "index.html")

    @app.get("/api/health")
    def health():
        b = state["bridge"]
        return {"ok": True, "mqtt": bool(b and b.connected), "site": co.site.id, "version": co.site.version}

    @app.get("/api/state")
    def live_state():
        snap = co.snapshot()
        b = state["bridge"]
        snap["mqtt"] = bool(b and b.connected)
        return snap

    @app.get("/api/sites/{site_id}")
    def get_site(site_id: str):
        try:
            return store.get_site(site_id).model_dump()
        except ValueError as e:
            raise HTTPException(400, str(e))

    @app.put("/api/sites/{site_id}")
    async def put_site(site_id: str, request: Request):
        try:
            site = Site.model_validate_json(await request.body())
            saved = store.put_site(site_id, site)
        except (ValidationError, ValueError) as e:
            raise HTTPException(400, str(e))
        if saved.id == co.site.id:
            co.set_site(saved)
        return saved.model_dump()

    @app.get("/labels/{site_id}", response_class=HTMLResponse)
    def labels(site_id: str):
        try:
            return HTMLResponse(labels_page(store.get_site(site_id)))
        except ValueError as e:
            raise HTTPException(400, str(e))

    @app.post("/api/accuracy")
    async def post_accuracy(request: Request):
        try:
            raw = json.loads(await request.body())
            samples = [AccuracySample.model_validate(s) for s in raw]
        except (ValidationError, ValueError, TypeError) as e:
            raise HTTPException(400, f"expected a JSON list of accuracy samples: {e}")
        added = store.add_samples(samples)
        return {"added": added, "total": len(store.samples())}

    @app.get("/api/accuracy")
    def get_accuracy(device: str | None = None):
        samples = store.samples()
        devices = sorted({s.device for s in samples})
        if device:
            samples = [s for s in samples if s.device == device]
        per_marker = {}
        for s in samples:
            per_marker.setdefault(s.markerId, []).append(s)
        return {
            "summary": summarise(samples),
            "devices": devices,
            "markers": {k: summarise(v) for k, v in sorted(per_marker.items())},
        }

    @app.get("/api/accuracy.csv", response_class=PlainTextResponse)
    def accuracy_csv():
        return PlainTextResponse(to_csv(store.samples()), media_type="text/csv",
                                 headers={"Content-Disposition": "attachment; filename=accuracy.csv"})

    @app.delete("/api/accuracy")
    def clear_accuracy():
        store.clear_samples()
        return {"ok": True}

    class GhostCount(BaseModel):
        count: int

    @app.post("/api/ghosts")
    def set_ghosts(body: GhostCount):
        co.set_ghosts(body.count)
        return {"ghosts": len(co.ghosts)}

    return app


def main():
    """Run with: python -m swarm_server.main (or uvicorn swarm_server.main:create_app --factory)."""
    import uvicorn
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")
    uvicorn.run(create_app(), host="0.0.0.0", port=int(os.environ.get("PORT", "8000")))


if __name__ == "__main__":
    main()
