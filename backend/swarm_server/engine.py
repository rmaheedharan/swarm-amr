"""Density Matrix Engine (copied from the swarm-demo simulation; keep the two in step): time-window reservations on narrow aisles.

Every tick (1 s) the engine:
  1. predicts where each worker will be for the next HORIZON seconds,
  2. turns that into one booking per narrow aisle (time in, time out, direction, pick spots),
  3. books workers into a reservation table in priority order,
  4. gives the cheapest cue to any worker whose booking clashes in the next WINDOW seconds:
     Warning (slow down), Hold (wait at the aisle entrance), Reroute, or Resequence
     (do the next pick first).

Two bookings in the same narrow aisle clash when their times overlap AND
  - they walk in opposite directions (head-on), or
  - one of them stops to pick inside the stretch the other walks through.
Walking behind someone in the same direction is fine.

This follows section 3 of the blueprint. Wide aisles and cross-aisles hold two or
more people, so the PoC engine only books narrow aisles.
"""
from dataclasses import dataclass, field

NORMAL, WARNING, HOLD, REROUTE, RESEQ = "normal", "warning", "hold", "reroute", "resequence"

WINDOW = 10         # s: act on conflicts this close
HORIZON = 30        # s: how far ahead we predict
MARGIN = 1          # s: safety margin around each booking
NUDGE_MIN = 2       # s: a delay this small needs no cue (people adjust by themselves)
SLOW_MAX = 4        # s: a delay this small is solved by slowing down
HOLD_MAX = 15       # s: never hold longer than this; above it a hold costs more than the jam it avoids
REROUTE_GAIN = 5    # s: reroute / resequence must beat holding by this much
MIN_HOLD = 8        # s: keep a cue at least this long (no flip-flop)
RATE_LIMIT = 15     # s: no new Warning within this long of the last cue
IGNORE_AFTER = 5    # s: a cue not followed after this long -> plan around the worker


@dataclass
class Directive:
    state: str = NORMAL
    since: int = 0
    aisle: int | None = None
    until: float = 0.0
    path: list | None = None
    swap_to: int | None = None      # resequence: which later pick to do first


@dataclass
class Booking:
    """One worker's use of one narrow aisle."""
    worker: int
    start: float
    end: float
    lo: int            # lowest y walked in the aisle
    hi: int            # highest y walked
    direction: int     # +1 up, -1 down, 0 in and back out the same end
    picks: set = field(default_factory=set)   # y of pick stops

    def shifted(self, dt):
        return Booking(self.worker, self.start + dt, self.end + dt, self.lo, self.hi, self.direction, self.picks)

    def clash(self, other):
        """None, "head_on" (people meet face to face: costly) or "behind" (one waits behind a picker)."""
        if not (self.start - MARGIN < other.end and other.start < self.end + MARGIN):
            return None
        if self.direction == 0 or other.direction == 0 or self.direction != other.direction:
            return "head_on"
        if any(self.lo <= y <= self.hi for y in other.picks) or any(other.lo <= y <= other.hi for y in self.picks):
            return "behind"
        return None


@dataclass
class EngineStats:
    cues: int = 0                 # cues the worker feels (Warning, Hold, Reroute)
    reorders: int = 0             # silent pick-list reorders (Resequence)
    by_state: dict = field(default_factory=lambda: {WARNING: 0, HOLD: 0, REROUTE: 0, RESEQ: 0})
    releases: int = 0


def timeline(wh, w, now):
    """Yield (cell, time, picking) along the worker's planned route, up to the horizon."""
    t = float(max(now, w.dwell_until))
    cur = w.pos
    yield cur, t, w.dwell_until > now
    for i, tgt in enumerate(w.upcoming(4)):
        path = w.path if i == 0 else wh.path(cur, tgt)
        for c in path:
            t += 1.0 / w.speed
            if t > now + HORIZON:
                return
            yield c, t, False
            cur = c
        yield cur, t, True
        t += w.dwell_at(tgt)
        if t > now + HORIZON:
            return


def bookings(wh, w, now):
    """{aisle: Booking} for every narrow aisle the worker will use in the horizon."""
    seen = {}
    for cell, t, picking in timeline(wh, w, now):
        a = wh.aisle_of(cell)
        if not wh.is_narrow(a):
            continue
        y = cell[1]
        if a not in seen:
            seen[a] = {"start": t, "end": t, "ys": [y], "picks": set()}
        s = seen[a]
        s["end"] = t + (w.dwell_at(cell) if picking else 1.0 / w.speed)
        s["ys"].append(y)
        if picking:
            s["picks"].add(y)
    out = {}
    for a, s in seen.items():
        ys = s["ys"]
        if ys == sorted(ys) and ys[-1] != ys[0]:
            direction = 1
        elif ys == sorted(ys, reverse=True) and ys[-1] != ys[0]:
            direction = -1
        else:
            direction = 0          # turns around inside the aisle
        out[a] = Booking(w.id, s["start"], s["end"], min(ys), max(ys), direction, s["picks"])
    return out


class Engine:
    def __init__(self, wh, hold_max=HOLD_MAX):
        self.wh = wh
        self.hold_max = hold_max
        self.directives = {}
        self.last_cue = {}
        self.stats = EngineStats()

    def tick(self, now, workers):
        info = {w.id: (bookings(self.wh, w, now), self._committed(w)) for w in workers}

        def priority(w):
            books, committed = info[w.id]
            first = min((b.start for b in books.values()), default=now + HORIZON)
            score = 100.0 if committed else 0.0
            score += 200.0 if w.ignoring else 0.0     # not following cues: plan around them
            score += 0.5 * w.recent_wait               # fairness: whoever waited most goes first
            score -= 0.1 * (first - now)               # earlier arrival goes first
            return score

        table = {}    # aisle -> list of Booking
        changes = {}
        for w in sorted(workers, key=priority, reverse=True):
            books, committed = info[w.id]
            clash = self._first_clash(books, committed, table, now)
            d = self._decide(w, clash, now)
            changes[w.id] = self._stabilise(w, d, now)
            self._book(changes[w.id], books, table, now)
        return changes

    def _committed(self, w):
        """Aisles the worker is already inside. Someone standing at the mouth can still be held."""
        here = self.wh.aisle_of(w.pos)
        return {here} if self.wh.is_narrow(here) else set()

    def _first_clash(self, books, committed, table, now):
        for a, b in sorted(books.items(), key=lambda kv: kv[1].start):
            if b.start > now + WINDOW or a in committed:
                continue
            for other in table.get(a, []):
                kind = b.clash(other)
                if kind:
                    return a, b, other.end, kind
        return None

    def _decide(self, w, clash, now):
        if clash is None:
            return Directive(NORMAL, now)
        a, mine, free_at, kind = clash
        delay = free_at + MARGIN - mine.start
        if kind == "behind":
            # Waiting behind a picker costs the same as waiting at the aisle mouth,
            # so only act if doing another pick first saves time.
            if self.wh.aisle_of(w.target) == a:
                swap = self._resequence(w, a, delay)
                if swap is not None:
                    return Directive(RESEQ, now, a, swap_to=swap)
            return Directive(NORMAL, now)
        if delay <= NUDGE_MIN:
            return Directive(NORMAL, now)
        if delay <= SLOW_MAX:
            return Directive(WARNING, now, a, until=now + 2 * delay)
        target = w.target
        if self.wh.aisle_of(target) != a and a in {self.wh.aisle_of(c) for c in w.path}:
            alt = self.wh.path(w.pos, target, avoid_aisle=a)
            if alt is not None:
                extra = (len(alt) - len(w.path)) / w.speed
                if extra + REROUTE_GAIN <= delay:
                    return Directive(REROUTE, now, a, path=alt)
        elif self.wh.aisle_of(target) == a:
            swap = self._resequence(w, a, delay)
            if swap is not None:
                return Directive(RESEQ, now, a, swap_to=swap)
        if delay > self.hold_max:
            return Directive(NORMAL, now)         # too long to wait: let people sort it out
        return Directive(HOLD, now, a, until=free_at + MARGIN)

    def _resequence(self, w, a, delay):
        """Do one of the next picks first, if that costs less than holding."""
        picks = w.upcoming(4)
        d = self.wh.distance
        p1 = picks[0]
        if w.postponed.get(p1, 0) >= 2:
            return None                       # do not push the same pick back forever
        for j in range(1, len(picks)):
            p2 = picks[j]
            if self.wh.aisle_of(p2) == a or p2 == self.wh.depot:
                continue
            after = picks[j + 1] if j + 1 < len(picks) else None
            before = picks[j - 1]
            # old order: pos -> p1 ... before -> p2 -> after
            # new order: pos -> p2 -> p1 ... before -> after
            old = d(w.pos, p1) + d(before, p2) + (d(p2, after) if after else 0)
            new = d(w.pos, p2) + d(p2, p1) + (d(before, after) if after else 0)
            extra = (new - old) / w.speed
            if extra + REROUTE_GAIN <= delay:
                return j
        return None

    def _stabilise(self, w, new, now):
        """Apply minimum hold time and rate limit, and count cues."""
        cur = self.directives.get(w.id, Directive())
        if new.state == WARNING and cur.state == NORMAL and now - self.last_cue.get(w.id, -999) < RATE_LIMIT:
            new = Directive(NORMAL, now)          # too soon for another nudge
        if new.state == NORMAL:
            if cur.state == HOLD:
                self.stats.releases += 1          # "Go" cue, sent at once
            out = new
        elif cur.state == new.state and cur.aisle == new.aisle:
            cur.until = new.until                 # same cue, refresh timing
            out = cur
        elif cur.state in (HOLD, WARNING) and now - cur.since < MIN_HOLD:
            cur.until = max(cur.until, new.until)
            out = cur
        else:
            self.stats.by_state[new.state] += 1
            if new.state == RESEQ:
                self.stats.reorders += 1          # the app just shows a different next pick
            else:
                self.stats.cues += 1
                self.last_cue[w.id] = now
            out = new
        self.directives[w.id] = out
        return out

    def _book(self, d, books, table, now):
        if d.state in (REROUTE, RESEQ):
            books = {a: b for a, b in books.items() if a != d.aisle}
        shift = 0.0
        if d.state == HOLD and d.aisle in books:
            shift = max(0.0, d.until - books[d.aisle].start)
        elif d.state == WARNING:
            shift = max(0.0, (d.until - now) / 2)
        for a, b in books.items():
            table.setdefault(a, []).append(b.shifted(shift))
