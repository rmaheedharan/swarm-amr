"""Workers the engine plans for: real phones (LiveWorker) and simulated "ghosts" (GhostWorker).

Both give the engine the same view as the simulation's Worker: pos (grid cell), path to
the current target, upcoming picks, dwell, speed, and fairness memory.
"""
import random
from dataclasses import dataclass, field

from .engine import HOLD, REROUTE, RESEQ, WARNING, Directive
from .warehouse import Warehouse

PICK_DWELL = 8        # s at a pick face (engine planning value)
DEPOT_DWELL = 15      # s at the pack station
WALK_SPEED = 1.0      # grid cells per second
LIVE_TIMEOUT_S = 10   # drop a phone after this long without a pose
IGNORE_FOR_S = 60
SQUEEZE_AFTER_S = 6   # ghosts stuck face to face this long squeeze past each other     # after a worker walks into a held aisle, plan around them this long


@dataclass
class _Base:
    id: str
    wh: Warehouse
    pos: tuple = (0, 0)
    picks: list = field(default_factory=list)
    path: list = field(default_factory=list)
    speed: float = WALK_SPEED
    dwell_until: float = 0.0
    ignoring: bool = False
    recent_wait: float = 0.0
    postponed: dict = field(default_factory=dict)
    directive: Directive = field(default_factory=Directive)

    @property
    def target(self):
        return self.picks[0] if self.picks else self.pos

    def upcoming(self, n):
        return self.picks[:n]

    def dwell_at(self, cell):
        return DEPOT_DWELL if cell == self.wh.depot else PICK_DWELL

    def replan(self, avoid_aisle=None):
        if not self.picks:
            self.path = []
            return
        p = self.wh.path(self.pos, self.target, avoid_aisle=avoid_aisle) if avoid_aisle is not None else None
        self.path = list(p if p is not None else self.wh.path(self.pos, self.target) or [])


@dataclass
class LiveWorker(_Base):
    """A real phone. Position and pick list come from its pose messages."""
    last_seen: float = 0.0
    xy: tuple = (0.0, 0.0)
    sigma: float = 0.0
    source: str = ""
    area: str = ""
    battery: int = -1
    picking: bool = False
    ignoring_until: float = 0.0
    held_aisle_entered: int | None = None

    @property
    def ghost(self):
        return False

    def update(self, pose, grid, now):
        self.last_seen = now
        self.xy = (pose.x, pose.y)
        self.sigma, self.source, self.area, self.battery = pose.sigma, pose.source, pose.aisle, pose.battery
        self.pos = self.wh.nearest_cell(grid.to_cell(pose.x, pose.y))
        self.picks = [self.wh.nearest_cell(grid.to_cell(p.x, p.y)) for p in pose.targets]
        if pose.picking and not self.picking:
            self.dwell_until = now + PICK_DWELL / 2      # we do not know how far into the pick they are
        elif not pose.picking:
            self.dwell_until = 0.0
        self.picking = pose.picking
        d = self.directive
        self.replan(d.aisle if d.state == REROUTE else None)

    def before_tick(self, now, step_s):
        """Fairness memory and compliance, once per engine tick."""
        d = self.directive
        here = self.wh.aisle_of(self.pos)
        if d.state == HOLD:
            if here == d.aisle and now - d.since > 1:
                self.ignoring_until = now + IGNORE_FOR_S     # walked into the aisle we asked them to wait for
            else:
                self.recent_wait += step_s
        self.ignoring = now < self.ignoring_until
        self.recent_wait *= 0.995 ** step_s

    def stale(self, now):
        return now - self.last_seen > LIVE_TIMEOUT_S


@dataclass
class GhostWorker(_Base):
    """A simulated picker on the backend floor. Walks pick lists and follows cues most of the time."""
    rng: random.Random = None
    compliance: float = 0.85
    hot: tuple = ()
    complying: bool = True
    cue_seen: str = "normal"
    blocked_s: int = 0
    squeezes: int = 0           # times two people had to squeeze past each other in a narrow aisle

    @property
    def ghost(self):
        return True

    def ensure_picks(self):
        if len(self.picks) >= 4:
            return
        wh, rng = self.wh, self.rng
        weights = [3.0 if a in self.hot else 1.0 for a in range(wh.n_aisles)]
        batch = []
        for _ in range(6):
            a = rng.choices(range(wh.n_aisles), weights)[0]
            batch.append((wh.aisle_x(a), rng.randint(1, wh.aisle_len)))
        batch.sort(key=lambda c: (c[0], c[1] if (c[0] // 2) % 2 == 0 else -c[1]))
        self.picks.extend(batch + [wh.depot])

    def before_tick(self, now, step_s):
        self.ensure_picks()
        if not self.path and self.pos != self.target:
            self.replan()
        self.recent_wait *= 0.995 ** step_s

    def apply(self, d, now):
        """React to a new cue like a person would (most of the time)."""
        if d.state != self.cue_seen:
            self.complying = self.rng.random() < self.compliance
            self.cue_seen = d.state
        self.ignoring = not self.complying and d.state in (HOLD, WARNING)
        if not self.complying:
            return
        if d.state == REROUTE and d.path:
            self.path = list(d.path)
        elif d.state == RESEQ and d.swap_to is not None and d.swap_to < len(self.picks):
            p1 = self.picks[0]
            self.postponed[p1] = self.postponed.get(p1, 0) + 1
            self.picks.insert(0, self.picks.pop(d.swap_to))
            self.replan()

    def step(self, now, occupied):
        """One second of walking. `occupied` maps narrow-aisle cells to the worker standing there."""
        if now < self.dwell_until:
            return
        if self.pos == self.target:
            self.dwell_until = now + self.dwell_at(self.pos)
            self.picks.pop(0)
            self.ensure_picks()
            self.replan()
            return
        if not self.path:
            self.replan()
            if not self.path:
                return
        nxt = self.path[0]
        d = self.directive
        if self.complying and d.state == HOLD and self.wh.aisle_of(nxt) == d.aisle \
                and self.wh.aisle_of(self.pos) != d.aisle:
            self.recent_wait += 1
            return
        if self.complying and d.state == WARNING and int(now) % 2:
            return
        if self.rng.random() > 0.92:
            return                                  # people do not walk at a perfectly even pace
        other = occupied.get(nxt)
        if other is not None and other != self.id:
            self.blocked_s += 1
            if self.blocked_s < SQUEEZE_AFTER_S:
                return                              # narrow aisle cell taken: wait
            self.squeezes += 1                      # stuck too long: squeeze past (a congestion incident)
        self.blocked_s = 0
        occupied.pop(self.pos, None)
        self.pos = self.path.pop(0)
        if self.wh.is_narrow(self.wh.aisle_of(self.pos)):
            occupied[self.pos] = self.id
