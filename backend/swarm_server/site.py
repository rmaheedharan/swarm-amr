"""Site file: mirror of core/src/main/kotlin/com/swarmamr/core/Site.kt."""
from pydantic import BaseModel, ConfigDict, Field


class _Model(BaseModel):
    model_config = ConfigDict(extra="ignore")


class GridSpec(_Model):
    originX: float = 0.0
    originY: float = 0.0
    aislePitchM: float = 3.0
    cellM: float = 1.2
    nAisles: int = Field(8, ge=1, le=60)
    aisleLenCells: int = Field(12, ge=1, le=200)
    narrowAisles: list[int] = [1, 2, 5, 6]

    @property
    def half_pitch(self) -> float:
        return self.aislePitchM / 2

    @property
    def top_row(self) -> int:
        return self.aisleLenCells + 1

    @property
    def width_cells(self) -> int:
        return 2 * self.nAisles + 1

    def to_cell(self, x: float, y: float) -> tuple[int, int]:
        """Grid cell (column, row) of a site point, clamped to the grid. Same rounding as Kotlin."""
        cx = min(max(_round_half_up((x - self.originX) / self.half_pitch), 0), self.width_cells - 1)
        cy = min(max(_round_half_up((y - self.originY) / self.cellM), 0), self.top_row)
        return cx, cy

    def cell_centre(self, cell: tuple[int, int]) -> tuple[float, float]:
        return self.originX + cell[0] * self.half_pitch, self.originY + cell[1] * self.cellM


def _round_half_up(v: float) -> int:
    # Kotlin roundToInt rounds .5 up (towards positive infinity); Python round() is banker's rounding
    import math
    return math.floor(v + 0.5)


class AccessPoint(_Model):
    bssid: str
    x: float
    y: float
    label: str = ""
    rttBiasM: float = 0.0
    frequencyMhz: int = 0


class Marker(_Model):
    id: str
    x: float
    y: float
    note: str = ""


class Fingerprint(_Model):
    x: float
    y: float
    rssi: dict[str, float]
    markerId: str = ""
    samples: int = 1


class Site(_Model):
    id: str = "demo"
    name: str = "Demo site"
    grid: GridSpec = GridSpec()
    accessPoints: list[AccessPoint] = []
    markers: list[Marker] = []
    fingerprints: list[Fingerprint] = []
    version: int = 1


def default_markers(g: GridSpec) -> list[Marker]:
    """Markers every 2 cells along each aisle, named A<aisle>-<row>, e.g. "A3-07". Same as the app."""
    out = []
    for a in range(g.nAisles):
        for row in range(1, g.aisleLenCells + 1, 2):
            out.append(Marker(id=f"A{a}-{row:02d}", x=g.originX + (2 * a + 1) * g.half_pitch, y=g.originY + row * g.cellM))
    return out


def default_site(site_id: str = "demo") -> Site:
    g = GridSpec()
    return Site(id=site_id, name="Demo site (edit in the app: Survey tab)", grid=g, markers=default_markers(g))
