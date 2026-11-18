# HTTP API

The swarm server listens on port 8000. FastAPI also serves interactive docs at
`http://<server IP>:8000/docs`.

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/` | Dashboard. |
| `GET` | `/api/health` | Health check: `{"ok", "mqtt", "site", "version"}`. |
| `GET` | `/api/state` | Live state: workers, ghosts, cues, counters (used by the dashboard). |
| `GET` | `/api/sites/{site_id}` | Download a site file. |
| `PUT` | `/api/sites/{site_id}` | Upload a site file (the app's **Send place to server**). The server bumps `version`. |
| `GET` | `/labels/{site_id}` | Printable QR label sheet for the site's markers. |
| `POST` | `/api/accuracy` | Upload accuracy samples (the app's **Send results to server**). |
| `GET` | `/api/accuracy` | Summary of stored samples. Optional `?device=<id>`. |
| `GET` | `/api/accuracy.csv` | All stored samples as CSV. |
| `DELETE` | `/api/accuracy` | Clear stored samples. |
| `POST` | `/api/ghosts` | Set the number of ghost workers. Body: `{"count": 5}`. |

## Examples

```bash
# Is the server up?
curl http://localhost:8000/api/health

# Get the site the phones use
curl http://localhost:8000/api/sites/demo -o demo.json

# Five ghosts
curl -X POST http://localhost:8000/api/ghosts -H 'Content-Type: application/json' -d '{"count": 5}'

# Download all accuracy samples and make a report
curl http://localhost:8000/api/accuracy.csv -o accuracy.csv
python3 tools/accuracy_report.py accuracy.csv
```

!!! warning
    The demo API has no authentication. Keep it on a private network.
