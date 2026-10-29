#!/usr/bin/env python3
"""Accuracy report from the CSV the app (or the server) exports.

    python3 tools/accuracy_report.py accuracy.csv [more.csv ...]

Prints p50 / p90 error and correct-aisle %, overall, per position source, per phone and
per marker, and lists the worst markers. Uses only the Python standard library.
"""
import csv
import math
import sys
from collections import defaultdict


def percentile(sorted_vals, pct):
    """Nearest-rank percentile, same as the app and the server."""
    if not sorted_vals:
        return 0.0
    rank = min(max(math.ceil(pct / 100.0 * len(sorted_vals)), 1), len(sorted_vals))
    return sorted_vals[rank - 1]


def stats(rows):
    errs = sorted(float(r["error_m"]) for r in rows)
    ok = sum(r["aisle_ok"] == "1" for r in rows)
    return {"n": len(rows), "p50": percentile(errs, 50), "p90": percentile(errs, 90),
            "mean": sum(errs) / len(errs), "max": errs[-1], "aisle": 100.0 * ok / len(rows)}


def line(name, s):
    return f"  {name:<18} {s['n']:>6} {s['p50']:>7.2f} {s['p90']:>7.2f} {s['mean']:>7.2f} {s['max']:>7.2f} {s['aisle']:>7.1f}%"


def table(title, groups):
    print(f"\n{title}\n  {'':<18} {'n':>6} {'p50 m':>7} {'p90 m':>7} {'mean m':>7} {'max m':>7} {'aisle':>8}")
    for k in sorted(groups):
        print(line(k or "-", stats(groups[k])))


def main(paths):
    rows = []
    for p in paths:
        with open(p, newline="") as f:
            rows += list(csv.DictReader(f))
    if not rows:
        sys.exit("No samples in the file(s).")
    s = stats(rows)
    gate = s["aisle"] >= 95.0 and s["p90"] <= 2.0
    print(f"Samples: {s['n']}   markers: {len({r['marker'] for r in rows})}   phones: {len({r['device'] for r in rows})}")
    print(f"Median error (p50): {s['p50']:.2f} m   p90: {s['p90']:.2f} m   correct aisle: {s['aisle']:.1f}%")
    print(("PASS" if gate else "NOT YET") + ": target is 95% correct aisle and p90 of 2 m or less (blueprint gate 1)")
    by = lambda key: {k: [r for r in rows if r[key] == k] for k in {r[key] for r in rows}}
    table("By position source", by("source"))
    table("By phone", by("device"))
    markers = by("marker")
    table("By marker", markers)
    worst = sorted(markers, key=lambda m: -stats(markers[m])["mean"])[:5]
    print("\nWorst markers (check AP placement or add fingerprints there): " + ", ".join(worst))


if __name__ == "__main__":
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    main(sys.argv[1:])
