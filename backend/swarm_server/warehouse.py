"""Warehouse floor model: vertical pick aisles joined by two cross-aisles.

Grid cells are about 1.2 m. A worker walks about one cell per second.
Copied from the swarm-demo simulation. Cell (column, row) matches GridSpec.toCell in the app.

    y = top  ===================================  cross-aisle (capacity 6)
             |   |   |   |   |   |   |   |
             | a0| a1| a2| ...                    pick aisles (wide = 2, narrow = 1)
             |   |   |   |   |   |   |   |
    y = 0    ===================================  cross-aisle, depot at (0, 0)
"""
from collections import deque

CROSS, WIDE, NARROW = "cross", "wide", "narrow"
CAPACITY = {CROSS: 6, WIDE: 2, NARROW: 1}   # people can step aside on a cross-aisle


class Warehouse:
    def __init__(self, n_aisles=8, aisle_len=12, narrow=(1, 2, 5, 6)):
        self.n_aisles = n_aisles
        self.aisle_len = aisle_len
        self.narrow = set(narrow)
        self.width = 2 * n_aisles + 1
        self.top = aisle_len + 1
        self.depot = (0, 0)
        self.cells = {}
        for x in range(self.width):
            self.cells[(x, 0)] = CROSS
            self.cells[(x, self.top)] = CROSS
        for a in range(n_aisles):
            kind = NARROW if a in self.narrow else WIDE
            for y in range(1, self.top):
                self.cells[(self.aisle_x(a), y)] = kind
        self._cache = {}

    def aisle_x(self, a):
        return 2 * a + 1

    def aisle_of(self, cell):
        """Aisle index of a cell, or None if the cell is on a cross-aisle."""
        x, y = cell
        if 1 <= y < self.top and x % 2 == 1:
            return (x - 1) // 2
        return None

    def is_narrow(self, a):
        return a is not None and a in self.narrow

    def capacity(self, cell):
        return CAPACITY[self.cells[cell]]

    def neighbours(self, cell):
        x, y = cell
        for c in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
            if c in self.cells:
                yield c

    def path(self, start, goal, avoid_aisle=None):
        """Shortest path (list of cells, start excluded). BFS on a unit-cost grid.

        avoid_aisle: do not walk through this aisle (start and goal are allowed).
        Returns None when no path exists.
        """
        key = (start, goal, avoid_aisle)
        if key in self._cache:
            return self._cache[key]
        prev = {start: None}
        queue = deque([start])
        while queue:
            cur = queue.popleft()
            if cur == goal:
                break
            for nb in self.neighbours(cur):
                if nb in prev:
                    continue
                if avoid_aisle is not None and nb != goal and self.aisle_of(nb) == avoid_aisle:
                    continue
                prev[nb] = cur
                queue.append(nb)
        if goal not in prev:
            self._cache[key] = None
            return None
        out = []
        cur = goal
        while cur != start:
            out.append(cur)
            cur = prev[cur]
        out.reverse()
        self._cache[key] = out
        return out

    def distance(self, start, goal):
        return len(self.path(start, goal))

    @classmethod
    def from_grid(cls, g):
        """Build from a site GridSpec (backend/swarm_server/site.py)."""
        return cls(n_aisles=g.nAisles, aisle_len=g.aisleLenCells,
                   narrow=tuple(a for a in g.narrowAisles if 0 <= a < g.nAisles))

    def nearest_cell(self, cell):
        """The walkable cell closest to `cell` (racking cells are not walkable)."""
        if cell in self.cells:
            return cell
        x, y = cell
        return min(self.cells, key=lambda c: (abs(c[0] - x) + abs(c[1] - y), c))

    def aisle_cells(self):
        return [c for c, kind in self.cells.items() if kind != CROSS]
