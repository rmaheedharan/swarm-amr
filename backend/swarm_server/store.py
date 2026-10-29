"""Files on disk: site maps and accuracy samples. One folder, easy to back up or copy."""
import math
import os
import re
import threading
from pathlib import Path

from pydantic import BaseModel, ConfigDict

from .site import Site, default_site

SAFE_ID = re.compile(r"^[A-Za-z0-9_-]{1,64}$")


class AccuracySample(BaseModel):
    """Mirror of AccuracySample in core/.../Accuracy.kt."""
    model_config = ConfigDict(extra="ignore")
    timeMs: int
    markerId: str
    truthX: float
    truthY: float
    estX: float
    estY: float
    source: str
    apsUsed: int
    sigmaM: float
    truthAisle: str
    estAisle: str
    device: str = ""

    @property
    def error_m(self) -> float:
        return math.hypot(self.truthX - self.estX, self.truthY - self.estY)

    @property
    def aisle_ok(self) -> bool:
        return self.truthAisle == self.estAisle


def percentile(sorted_vals, pct):
    """Nearest-rank percentile, same as Accuracy.percentile in the app."""
    if not sorted_vals:
        return 0.0
    rank = min(max(math.ceil(pct / 100.0 * len(sorted_vals)), 1), len(sorted_vals))
    return sorted_vals[rank - 1]


def summarise(samples: list[AccuracySample]) -> dict:
    if not samples:
        return {"samples": 0, "markers": 0, "meanM": 0.0, "p50M": 0.0, "p90M": 0.0, "maxM": 0.0,
                "correctAislePct": 0.0, "bySource": {}, "passesGate1": False}
    errs = sorted(s.error_m for s in samples)
    by_source = {}
    for s in samples:
        by_source[s.source] = by_source.get(s.source, 0) + 1
    out = {
        "samples": len(samples),
        "markers": len({s.markerId for s in samples}),
        "meanM": sum(errs) / len(errs),
        "p50M": percentile(errs, 50),
        "p90M": percentile(errs, 90),
        "maxM": errs[-1],
        "correctAislePct": 100.0 * sum(s.aisle_ok for s in samples) / len(samples),
        "bySource": by_source,
    }
    out["passesGate1"] = out["correctAislePct"] >= 95.0 and out["p90M"] <= 2.0
    return out


CSV_HEADER = "time_ms,marker,truth_x,truth_y,est_x,est_y,error_m,source,aps_used,sigma_m,truth_aisle,est_aisle,aisle_ok,device"


def to_csv(samples: list[AccuracySample]) -> str:
    rows = [CSV_HEADER]
    for s in samples:
        rows.append(",".join(str(v) for v in (
            s.timeMs, s.markerId, f"{s.truthX:.3f}", f"{s.truthY:.3f}", f"{s.estX:.3f}", f"{s.estY:.3f}",
            f"{s.error_m:.3f}", s.source, s.apsUsed, f"{s.sigmaM:.3f}", s.truthAisle, s.estAisle,
            1 if s.aisle_ok else 0, s.device)))
    return "\n".join(rows) + "\n"


class Store:
    def __init__(self, folder: str | os.PathLike):
        self.dir = Path(folder)
        (self.dir / "sites").mkdir(parents=True, exist_ok=True)
        self.lock = threading.Lock()

    # ---- sites ----------------------------------------------------------------------
    def _site_path(self, site_id):
        if not SAFE_ID.match(site_id):
            raise ValueError("site id may use letters, digits, - and _ only")
        return self.dir / "sites" / f"{site_id}.json"

    def get_site(self, site_id: str) -> Site:
        p = self._site_path(site_id)
        if p.exists():
            return Site.model_validate_json(p.read_text())
        return default_site(site_id)

    def put_site(self, site_id: str, site: Site) -> Site:
        """Save a site. The version goes up by one so phones can tell they are out of date."""
        with self.lock:
            p = self._site_path(site_id)
            old = self.get_site(site_id) if p.exists() else None
            site = site.model_copy(update={"id": site_id, "version": max(site.version, old.version if old else 0) + 1})
            tmp = p.with_suffix(".tmp")
            tmp.write_text(site.model_dump_json(indent=2))
            tmp.replace(p)
            return site

    # ---- accuracy -------------------------------------------------------------------
    @property
    def _acc_path(self):
        return self.dir / "accuracy.jsonl"

    def samples(self) -> list[AccuracySample]:
        if not self._acc_path.exists():
            return []
        out = []
        for line in self._acc_path.read_text().splitlines():
            if line.strip():
                out.append(AccuracySample.model_validate_json(line))
        return out

    def add_samples(self, new: list[AccuracySample]) -> int:
        """Append samples, skipping ones already stored (the app sends all its samples each time)."""
        with self.lock:
            seen = {(s.device, s.timeMs, s.markerId) for s in self.samples()}
            fresh = [s for s in new if (s.device, s.timeMs, s.markerId) not in seen]
            with self._acc_path.open("a") as f:
                for s in fresh:
                    f.write(s.model_dump_json() + "\n")
            return len(fresh)

    def clear_samples(self):
        with self.lock:
            if self._acc_path.exists():
                self._acc_path.rename(self.dir / f"accuracy-cleared-{int(os.path.getmtime(self._acc_path))}.jsonl")
