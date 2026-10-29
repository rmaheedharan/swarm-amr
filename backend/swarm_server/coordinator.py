"""Joins phones, ghosts and the engine. No network code here, so it is easy to test.

Feed it MQTT messages with on_message(); call tick() once a second. tick() returns the
messages to publish: changed directives for each phone and the heartbeat.
"""
import math
import random
import threading
from collections import deque
from dataclasses import dataclass

from . import protocol as P
from .engine import HOLD, NORMAL, REROUTE, RESEQ, WARNING, Directive, Engine
from .site import Site
from .warehouse import Warehouse
from .workers import GhostWorker, LiveWorker

RESEND_S = 4          # re-send an active cue this often (refreshes its expiry on the phone)
GO_AFTER_HOLD = True  # a hold that ends sends a short "Go"


@dataclass
class Outgoing:
    topic: str
    payload: bytes
    qos: int = 0
    retain: bool = False


class Coordinator:
    def __init__(self, site: Site, ghosts: int = 0, seed: int = 1):
        self.lock = threading.RLock()
        self.rng = random.Random(seed)
        self.live: dict[str, LiveWorker] = {}
        self.ghosts: list[GhostWorker] = []
        self.seq: dict[str, int] = {}
        self.sent: dict[str, tuple] = {}        # dev -> (state, aisle, swap_to, sent_at)
        self.events = deque(maxlen=200)          # recent cues, for the dashboard
        self.counts = {s: 0 for s in (WARNING, HOLD, "go", REROUTE, RESEQ)}
        self.last_tick = None
        self.set_site(site)
        self.set_ghosts(ghosts)

    # ---- configuration ------------------------------------------------------------
    def set_site(self, site: Site):
        with self.lock:
            self.site = site
            self.wh = Warehouse.from_grid(site.grid)
            self.engine = Engine(self.wh)
            for w in list(self.live.values()) + self.ghosts:
                w.wh = self.wh
                w.pos = self.wh.nearest_cell(w.pos)
                w.picks = [self.wh.nearest_cell(c) for c in w.picks]
                w.directive = Directive()
                w.replan()

    def set_ghosts(self, n: int):
        n = max(0, min(int(n), 30))
        with self.lock:
            while len(self.ghosts) > n:
                g = self.ghosts.pop()
                self.engine.directives.pop(g.id, None)
            hot = tuple(self.wh.narrow)[:2]
            starts = [(x, 0) for x in range(0, self.wh.width, 2)] + [(x, self.wh.top) for x in range(0, self.wh.width, 2)]
            while len(self.ghosts) < n:
                i = len(self.ghosts) + 1
                g = GhostWorker(id=f"ghost-{i}", wh=self.wh, pos=starts[(i * 3) % len(starts)],
                                rng=random.Random(self.rng.random()), hot=hot)
                g.ensure_picks()
                g.replan()
                self.ghosts.append(g)

    # ---- input ----------------------------------------------------------------------
    def on_message(self, topic: str, payload: bytes, now: float):
        parsed = P.parse_dev_topic(self.site.id, topic)
        if not parsed:
            return
        dev, kind = parsed
        try:
            if kind == "pose":
                msg = P.PoseMsg.model_validate_json(payload)
                with self.lock:
                    w = self.live.get(dev)
                    if w is None:
                        w = self.live[dev] = LiveWorker(id=dev, wh=self.wh)
                    w.update(msg, self.site.grid, now)
            elif kind == "status":
                if not payload:
                    return                          # retained message cleared
                msg = P.StatusMsg.model_validate_json(payload)
                if not msg.online:
                    self._drop(dev)
        except ValueError:
            pass                                    # bad message from a device: ignore it

    def _drop(self, dev):
        with self.lock:
            self.live.pop(dev, None)
            self.engine.directives.pop(dev, None)
            self.sent.pop(dev, None)

    # ---- the 1 Hz loop ----------------------------------------------------------------
    def tick(self, now: float) -> list[Outgoing]:
        with self.lock:
            step = 1.0 if self.last_tick is None else min(max(now - self.last_tick, 0.0), 5.0)
            self.last_tick = now
            for dev in [d for d, w in self.live.items() if w.stale(now)]:
                self._drop(dev)
            workers = list(self.live.values()) + self.ghosts
            for w in workers:
                w.before_tick(now, step)
            changes = self.engine.tick(now, workers) if workers else {}
            out = []
            for w in workers:
                d = changes.get(w.id, Directive(NORMAL, now))
                prev = w.directive
                w.directive = d
                if w.ghost:
                    if d.state != prev.state:
                        self._log(now, w.id, d.state if not (prev.state == HOLD and d.state == NORMAL) else "go", d)
                    w.apply(d, now)
                else:
                    msg = self._directive_for(w, prev, d, now)
                    if msg:
                        out.append(msg)
            occupied = {}
            for w in workers:
                if self.wh.is_narrow(self.wh.aisle_of(w.pos)):
                    occupied[w.pos] = w.id
            for g in self.ghosts:
                g.step(now, occupied)
            hb = P.HeartbeatMsg(ts=int(now * 1000), siteVersion=self.site.version,
                                workers=len(self.live), ghosts=len(self.ghosts))
            out.append(Outgoing(P.heartbeat_topic(self.site.id), P.encode(hb), qos=0, retain=True))
            return out

    def _directive_for(self, w, prev, d, now):
        """The message to send to a phone, or None when nothing changed."""
        state = d.state
        if prev.state == HOLD and state == NORMAL and GO_AFTER_HOLD:
            state = "go"
        key = (state, d.aisle, d.swap_to)
        last = self.sent.get(w.id)
        if last and last[:3] == key and (state in (NORMAL, "go") or now - last[3] < RESEND_S):
            return None
        if last is None and state == NORMAL:
            return None                             # nothing to say yet
        if state == "go" and last and last[0] == "go":
            return None
        if state == NORMAL and last and last[0] == "go":
            return None                             # the phone clears Go by itself
        self.sent[w.id] = (*key, now)
        if not last or last[:3] != key:
            self._log(now, w.id, state, d)
        seq = self.seq[w.id] = self.seq.get(w.id, 0) + 1
        ts = int(now * 1000)
        a = d.aisle
        if state == WARNING:
            ttl = max(d.until - now, 4.0) + 2
            text, hold_s = (f"Slow down: A{a} busy" if a is not None else "Slow down"), 0
        elif state == HOLD:
            hold_s = max(1, math.ceil(d.until - now))
            ttl, text = hold_s + 3, f"Wait {hold_s} s"
        elif state == REROUTE:
            ttl, text, hold_s = 20, f"Avoid A{a}" if a is not None else "Take another way", 0
        elif state == RESEQ:
            ttl, text, hold_s = 20, "Next pick changed", 0
        elif state == "go":
            ttl, text, hold_s = 3, "Go", 0
        else:
            ttl, text, hold_s = 0, "", 0
        msg = P.DirectiveMsg(dev=w.id, seq=seq, ts=ts, state=P.CueState(state), text=text, aisle=a,
                             holdS=hold_s, expiresMs=ts + int(ttl * 1000) if ttl else 0,
                             swapTo=d.swap_to if state == RESEQ else None)
        return Outgoing(P.directive_topic(self.site.id, w.id), P.encode(msg), qos=1)

    def _log(self, now, who, state, d):
        if state == NORMAL:
            return
        if state in self.counts:
            self.counts[state] += 1
        self.events.appendleft({"t": now, "who": who, "state": state, "aisle": d.aisle})

    # ---- dashboard ------------------------------------------------------------------
    def snapshot(self) -> dict:
        with self.lock:
            g = self.site.grid

            def view(w):
                x, y = (w.xy if not w.ghost else g.cell_centre(w.pos))
                state = w.directive.state
                return {
                    "id": w.id, "ghost": w.ghost, "x": round(x, 2), "y": round(y, 2),
                    "cell": list(w.pos), "state": state, "aisle": w.directive.aisle,
                    "target": list(g.cell_centre(w.target)) if w.picks else None,
                    "sigma": getattr(w, "sigma", None), "source": getattr(w, "source", "SIM"),
                    "battery": getattr(w, "battery", -1),
                }
            return {
                "site": {"id": self.site.id, "name": self.site.name, "version": self.site.version,
                         "grid": g.model_dump()},
                "workers": [view(w) for w in list(self.live.values()) + self.ghosts],
                "counts": dict(self.counts),
                "squeezes": sum(gh.squeezes for gh in self.ghosts),
                "events": list(self.events)[:30],
                "ts": self.last_tick,
            }
