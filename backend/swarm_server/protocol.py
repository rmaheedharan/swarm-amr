"""MQTT message format. Mirror of core/src/main/kotlin/com/swarmamr/core/Protocol.kt; change both together.

Topics, all under swarm/v1/{site}/:
  dev/{id}/pose       device -> server  QoS 0, 2 Hz
  dev/{id}/status     device -> server  QoS 1, retained, also the Last Will ("online": false)
  dev/{id}/directive  server -> device  QoS 1
  engine/heartbeat    server -> all     QoS 0, retained, 1 Hz
"""
from enum import Enum

from pydantic import BaseModel, ConfigDict


def root(site: str) -> str:
    return f"swarm/v1/{site}"


def pose_topic(site: str, dev: str) -> str:
    return f"{root(site)}/dev/{dev}/pose"


def status_topic(site: str, dev: str) -> str:
    return f"{root(site)}/dev/{dev}/status"


def directive_topic(site: str, dev: str) -> str:
    return f"{root(site)}/dev/{dev}/directive"


def heartbeat_topic(site: str) -> str:
    return f"{root(site)}/engine/heartbeat"


def parse_dev_topic(site: str, topic: str):
    """'swarm/v1/demo/dev/tc52-01/pose' -> ('tc52-01', 'pose'), or None for other topics."""
    prefix = f"{root(site)}/dev/"
    if not topic.startswith(prefix):
        return None
    parts = topic[len(prefix):].split("/")
    if len(parts) != 2 or not parts[0]:
        return None
    return parts[0], parts[1]


class _Msg(BaseModel):
    model_config = ConfigDict(extra="ignore")     # like ignoreUnknownKeys on the app side


class Point(_Msg):
    x: float
    y: float


class PoseMsg(_Msg):
    dev: str
    ts: int                 # device clock, ms since epoch
    x: float
    y: float
    sigma: float            # 1-sigma position error, m
    speed: float            # m/s
    source: str             # RTT, FINGERPRINT, RSSI_CENTROID, QR
    aisle: str              # "A3" or "cross"
    picking: bool = False
    targets: list[Point] = []
    battery: int = -1


class StatusMsg(_Msg):
    dev: str
    online: bool
    ts: int = 0
    app: str = ""
    mode: str = ""


class CueState(str, Enum):
    NORMAL = "normal"
    WARNING = "warning"
    HOLD = "hold"
    GO = "go"
    REROUTE = "reroute"
    RESEQUENCE = "resequence"


class DirectiveMsg(_Msg):
    dev: str
    seq: int
    ts: int                 # server clock, ms
    state: CueState
    text: str = ""
    aisle: int | None = None
    holdS: int = 0
    expiresMs: int = 0
    swapTo: int | None = None


class HeartbeatMsg(_Msg):
    ts: int
    siteVersion: int = 0
    workers: int = 0
    ghosts: int = 0


def encode(msg: BaseModel) -> bytes:
    """Same rules as WireJson: defaults included, nulls left out."""
    return msg.model_dump_json(exclude_none=True).encode()
