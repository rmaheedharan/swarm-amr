import json

import pytest
from fastapi.testclient import TestClient

from swarm_server import protocol as P
from swarm_server.coordinator import Coordinator
from swarm_server.main import Settings, create_app
from swarm_server.site import GridSpec, default_site
from swarm_server.store import AccuracySample, Store, percentile, summarise

T0 = 1_800_000_000.0
G = GridSpec()


def xy(cell):
    return G.cell_centre(cell)


def pose(dev, cell, targets=(), ts=0, picking=False):
    x, y = xy(cell)
    pts = [dict(zip("xy", xy(c))) for c in targets]
    return json.dumps({"dev": dev, "ts": ts, "x": x, "y": y, "sigma": 1.0, "speed": 1.0, "source": "RTT",
                       "aisle": "A1", "picking": picking, "targets": pts}).encode()


def directives(out, dev):
    topic = P.directive_topic("demo", dev)
    return [json.loads(m.payload) for m in out if m.topic == topic]


# ---- protocol ------------------------------------------------------------------------

def test_directive_json_matches_app_format():
    m = P.DirectiveMsg(dev="p1", seq=3, ts=10, state=P.CueState.HOLD, text="Wait 6 s", aisle=2, holdS=6, expiresMs=9000)
    d = json.loads(P.encode(m))
    assert d["state"] == "hold"            # lower case, like @SerialName in Protocol.kt
    assert "swapTo" not in d               # nulls left out, like explicitNulls = false
    assert d["holdS"] == 6 and d["aisle"] == 2


def test_pose_ignores_unknown_fields():
    raw = pose("p1", (3, 4))[:-1] + b',"newField":1}'
    assert P.PoseMsg.model_validate_json(raw).dev == "p1"


def test_parse_dev_topic():
    assert P.parse_dev_topic("demo", "swarm/v1/demo/dev/tc52-01/pose") == ("tc52-01", "pose")
    assert P.parse_dev_topic("demo", "swarm/v1/other/dev/x/pose") is None
    assert P.parse_dev_topic("demo", "swarm/v1/demo/engine/heartbeat") is None


# ---- site grid -----------------------------------------------------------------------

def test_to_cell_matches_app_rounding():
    assert G.to_cell(*G.cell_centre((3, 7))) == (3, 7)
    assert G.to_cell(0.75, 0.6) == (1, 1)     # .5 rounds up, as Kotlin roundToInt does
    assert G.to_cell(-5, 99) == (0, G.top_row)


def test_default_markers_named_like_app():
    ids = [m.id for m in default_site().markers]
    assert "A3-07" in ids and len(ids) == 8 * 6


# ---- engine with phones ----------------------------------------------------------------

def test_head_on_in_narrow_aisle_gives_hold_then_go():
    co = Coordinator(default_site(), ghosts=0)
    # B is inside narrow aisle 1 (column 3) walking down to the depot. A is about to walk up into it.
    co.on_message(P.pose_topic("demo", "B"), pose("B", (3, 8), targets=[(0, 0)]), T0)
    co.on_message(P.pose_topic("demo", "A"), pose("A", (1, 0), targets=[(3, 10)]), T0)
    out = co.tick(T0)
    a = directives(out, "A")
    assert a and a[0]["state"] == "hold"
    assert a[0]["aisle"] == 1 and a[0]["holdS"] > 0 and a[0]["expiresMs"] > a[0]["ts"]
    assert directives(out, "B") == []                     # B is committed: no cue
    assert any(m.topic == P.heartbeat_topic("demo") and m.retain for m in out)

    # B walks out of the aisle; A gets "Go".
    t = T0
    states = []
    for row in range(7, -1, -1):
        t += 1
        co.on_message(P.pose_topic("demo", "B"), pose("B", (3, row), targets=[(0, 0)]), t)
        co.on_message(P.pose_topic("demo", "A"), pose("A", (1, 0), targets=[(3, 10)]), t)
        states += [d["state"] for d in directives(co.tick(t), "A")]
    co.on_message(P.pose_topic("demo", "B"), pose("B", (0, 0), targets=[]), t + 1)
    co.on_message(P.pose_topic("demo", "A"), pose("A", (1, 0), targets=[(3, 10)]), t + 1)
    states += [d["state"] for d in directives(co.tick(t + 1), "A")]
    assert "go" in states


def test_seq_increases_and_offline_status_drops_phone():
    co = Coordinator(default_site(), ghosts=0)
    co.on_message(P.pose_topic("demo", "B"), pose("B", (3, 8), targets=[(0, 0)]), T0)
    co.on_message(P.pose_topic("demo", "A"), pose("A", (1, 0), targets=[(3, 10)]), T0)
    seqs = []
    for i in range(6):
        co.on_message(P.pose_topic("demo", "A"), pose("A", (1, 0), targets=[(3, 10)]), T0 + i)
        co.on_message(P.pose_topic("demo", "B"), pose("B", (3, 8), targets=[(0, 0)]), T0 + i)
        seqs += [d["seq"] for d in directives(co.tick(T0 + i), "A")]
    assert seqs == sorted(seqs) and len(set(seqs)) == len(seqs) and len(seqs) >= 2   # re-sent while active
    co.on_message(P.status_topic("demo", "A"), json.dumps({"dev": "A", "online": False}).encode(), T0 + 7)
    assert "A" not in co.live


def test_phone_dropped_after_silence_and_bad_messages_ignored():
    co = Coordinator(default_site(), ghosts=0)
    co.on_message(P.pose_topic("demo", "A"), pose("A", (1, 0)), T0)
    co.on_message(P.pose_topic("demo", "A"), b"not json", T0)
    co.on_message(P.pose_topic("demo", "C"), b"{}", T0)
    assert list(co.live) == ["A"]
    co.tick(T0 + 30)
    assert co.live == {}


# ---- ghosts -----------------------------------------------------------------------------

def test_ghosts_walk_and_get_cues():
    co = Coordinator(default_site(), ghosts=8, seed=3)
    start = [g.pos for g in co.ghosts]
    for i in range(600):
        co.tick(T0 + i)
    assert [g.pos for g in co.ghosts] != start
    snap = co.snapshot()
    assert len(snap["workers"]) == 8 and all(w["ghost"] for w in snap["workers"])
    assert sum(snap["counts"].values()) > 0, "8 ghosts for 10 minutes should need at least one cue"
    co.set_ghosts(2)
    assert len(co.ghosts) == 2


def test_site_change_keeps_running():
    co = Coordinator(default_site(), ghosts=3)
    for i in range(20):
        co.tick(T0 + i)
    s = default_site()
    s.grid = GridSpec(nAisles=4, aisleLenCells=8, narrowAisles=[1])
    co.set_site(s)
    for i in range(20, 60):
        co.tick(T0 + i)
    assert all(g.pos in co.wh.cells for g in co.ghosts)


# ---- store and accuracy ----------------------------------------------------------------

def sample(t, err, ok=True, dev="p1", marker="A1-03"):
    return AccuracySample(timeMs=t, markerId=marker, truthX=0, truthY=0, estX=err, estY=0, source="RTT",
                          apsUsed=4, sigmaM=1, truthAisle="A1", estAisle="A1" if ok else "A2", device=dev)


def test_percentile_nearest_rank():
    assert percentile([1, 2, 3, 4, 5, 6, 7, 8, 9, 10], 90) == 9
    assert percentile([1, 2, 3, 4, 5, 6, 7, 8, 9, 10], 50) == 5
    assert percentile([], 50) == 0


def test_summary_gate():
    good = [sample(i, 1.0) for i in range(20)]
    assert summarise(good)["passesGate1"]
    bad = good + [sample(100 + i, 1.0, ok=False) for i in range(2)]
    assert not summarise(bad)["passesGate1"]          # 20/22 = 91% right aisle


def test_store_dedupes_and_versions(tmp_path):
    st = Store(tmp_path)
    assert st.add_samples([sample(1, 1), sample(2, 1)]) == 2
    assert st.add_samples([sample(1, 1), sample(3, 1)]) == 1
    assert len(st.samples()) == 3
    v1 = st.put_site("demo", default_site()).version
    v2 = st.put_site("demo", st.get_site("demo")).version
    assert v2 == v1 + 1
    with pytest.raises(ValueError):
        st.get_site("../etc")


# ---- HTTP API --------------------------------------------------------------------------

@pytest.fixture
def client(tmp_path, monkeypatch):
    monkeypatch.setenv("DATA_DIR", str(tmp_path))
    monkeypatch.setenv("MQTT_ENABLED", "0")
    monkeypatch.setenv("GHOSTS", "2")
    with TestClient(create_app(Settings())) as c:
        yield c


def test_api_site_round_trip(client):
    s = client.get("/api/sites/demo").json()
    assert s["grid"]["nAisles"] == 8 and s["markers"]
    s["name"] = "Chennai DC"
    s["accessPoints"] = [{"bssid": "aa:bb:cc:dd:ee:ff", "x": 3, "y": 4}]
    r = client.put("/api/sites/demo", content=json.dumps(s))
    assert r.status_code == 200 and r.json()["version"] == s["version"] + 1
    assert client.get("/api/sites/demo").json()["name"] == "Chennai DC"
    assert client.put("/api/sites/demo", content=b"{bad").status_code == 400


def test_api_accuracy(client):
    body = [sample(i, 0.5 * i).model_dump() for i in range(1, 5)]
    r = client.post("/api/accuracy", content=json.dumps(body))
    assert r.json() == {"added": 4, "total": 4}
    acc = client.get("/api/accuracy").json()
    assert acc["summary"]["samples"] == 4 and acc["devices"] == ["p1"]
    csv = client.get("/api/accuracy.csv").text
    assert csv.startswith("time_ms,marker") and len(csv.strip().splitlines()) == 5
    assert client.post("/api/accuracy", content=b'{"x":1}').status_code == 400


def test_api_state_and_dashboard(client):
    assert client.post("/api/ghosts", json={"count": 4}).json() == {"ghosts": 4}
    st = client.get("/api/state").json()
    assert len(st["workers"]) == 4 and st["mqtt"] is False
    assert "<canvas" in client.get("/").text


def test_labels_page(client):
    page = client.get("/labels/demo").text
    assert page.count("<svg") == 48 and "A3-07" in page
