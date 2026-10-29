"""Printable QR labels for the floor markers. The app reads "SWM:<marker id>"."""
import html
import io

import qrcode
import qrcode.image.svg

from .site import Site


def qr_svg(text: str) -> str:
    img = qrcode.make(text, image_factory=qrcode.image.svg.SvgPathImage, box_size=10, border=2)
    buf = io.BytesIO()
    img.save(buf)
    svg = buf.getvalue().decode()
    return svg[svg.index("<svg"):]


def labels_page(site: Site) -> str:
    cards = []
    for m in site.markers:
        cards.append(
            f'<div class="label">{qr_svg("SWM:" + m.id)}'
            f'<div class="id">{html.escape(m.id)}</div>'
            f'<div class="xy">x {m.x:.2f} m · y {m.y:.2f} m</div></div>')
    return f"""<!doctype html><html><head><meta charset="utf-8">
<title>Marker labels: {html.escape(site.name)}</title>
<style>
 body {{ font-family: system-ui, sans-serif; margin: 10mm; color: #000; background: #fff; }}
 .sheet {{ display: grid; grid-template-columns: repeat(3, 1fr); gap: 6mm; }}
 .label {{ border: 1px dashed #999; padding: 4mm; text-align: center; break-inside: avoid; }}
 .label svg {{ width: 45mm; height: 45mm; }}
 .id {{ font-size: 20pt; font-weight: 700; }}
 .xy {{ font-size: 9pt; color: #444; }}
 @media print {{ .noprint {{ display: none; }} }}
</style></head><body>
<p class="noprint">Site <b>{html.escape(site.name)}</b>, version {site.version}, {len(site.markers)} markers.
Print on A4, cut, and stick each label on the floor exactly at its marker point. <button onclick="print()">Print</button></p>
<div class="sheet">{''.join(cards)}</div></body></html>"""
