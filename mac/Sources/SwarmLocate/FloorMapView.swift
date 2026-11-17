import AppKit
import LocateCore
import SwiftUI

/** Site metres to view points and back. y grows up in the site, down on screen. */
struct MapTransform {
    var minX: Double, minY: Double, maxX: Double, maxY: Double
    var scale: Double
    var ox: Double, oy: Double

    init(site: Site, plan: NSImage?, size: CGSize) {
        let g = site.grid
        var xs = [g.originX, g.originX + g.widthM]
        var ys = [g.bottomY, g.topY]
        if let img = site.image, plan != nil {
            xs += [g.originX + img.widthM]; ys += [g.originY + img.heightM]
        }
        xs += site.markers.map { $0.x } + site.fingerprints.map { $0.x } + site.accessPoints.map { $0.x }
        ys += site.markers.map { $0.y } + site.fingerprints.map { $0.y } + site.accessPoints.map { $0.y }
        let pad = 1.0
        minX = (xs.min() ?? 0) - pad; maxX = (xs.max() ?? 10) + pad
        minY = (ys.min() ?? 0) - pad; maxY = (ys.max() ?? 10) + pad
        let w = max(maxX - minX, 1), h = max(maxY - minY, 1)
        scale = max(0.01, min(Double(size.width) / w, Double(size.height) / h))
        ox = (Double(size.width) - w * scale) / 2
        oy = (Double(size.height) - h * scale) / 2
    }

    func pt(_ p: Vec2) -> CGPoint { pt(p.x, p.y) }
    func pt(_ x: Double, _ y: Double) -> CGPoint {
        CGPoint(x: ox + (x - minX) * scale, y: oy + (maxY - y) * scale)
    }
    func site(_ p: CGPoint) -> Vec2 {
        Vec2(minX + (Double(p.x) - ox) / scale, maxY - (Double(p.y) - oy) / scale)
    }
    /** A site rectangle (x0,y0 bottom-left) on screen. */
    func rect(_ x0: Double, _ y0: Double, _ w: Double, _ h: Double) -> CGRect {
        let tl = pt(x0, y0 + h)
        return CGRect(x: tl.x, y: tl.y, width: w * scale, height: h * scale)
    }
}

/** Top view: floor plan picture or racks, recorded spots, markers, and where the Mac is. */
struct FloorMapView: View {
    let site: Site
    let plan: NSImage?
    let fix: Fix?
    let selected: String
    let onTap: (Vec2) -> Void

    var body: some View {
        GeometryReader { geo in
            let t = MapTransform(site: site, plan: plan, size: geo.size)
            Canvas { ctx, _ in draw(&ctx, t) }
                .contentShape(Rectangle())
                .gesture(SpatialTapGesture().onEnded { v in onTap(t.site(v.location)) })
        }
        .background(Color(nsColor: .textBackgroundColor))
    }

    private func draw(_ ctx: inout GraphicsContext, _ t: MapTransform) {
        let g = site.grid
        let line = Color(nsColor: .separatorColor)
        let muted = Color(nsColor: .secondaryLabelColor)

        if let plan, let img = site.image {
            ctx.draw(Image(nsImage: plan), in: t.rect(g.originX, g.originY, img.widthM, img.heightM))
        } else if g.openFloor {
            ctx.stroke(Path(t.rect(g.originX, g.bottomY, g.widthM, g.depthM)), with: .color(line), lineWidth: 1.5)
        } else {
            // Racks sit between the aisle centrelines; narrow aisles are tinted.
            for a in 0..<g.nAisles where g.narrowAisles.contains(a) {
                let w = g.aislePitchM * 0.55
                ctx.fill(Path(t.rect(g.aisleX(a) - w / 2, g.bottomY + g.cellM * 0.5, w, g.depthM - g.cellM)),
                         with: .color(.orange.opacity(0.10)))
            }
            for k in 0...g.nAisles {
                let x = g.originX + Double(k) * g.aislePitchM
                let w = g.aislePitchM * 0.4
                ctx.fill(Path(t.rect(x - w / 2, g.bottomY + g.cellM * 0.5, w, g.depthM - g.cellM)),
                         with: .color(Color(nsColor: .tertiaryLabelColor).opacity(0.35)))
            }
            ctx.stroke(Path(t.rect(g.originX, g.bottomY, g.widthM, g.depthM)), with: .color(line), lineWidth: 1)
        }

        // Access points: small diamonds.
        for ap in site.accessPoints {
            let c = t.pt(ap.pos)
            var p = Path()
            p.move(to: CGPoint(x: c.x, y: c.y - 5)); p.addLine(to: CGPoint(x: c.x + 5, y: c.y))
            p.addLine(to: CGPoint(x: c.x, y: c.y + 5)); p.addLine(to: CGPoint(x: c.x - 5, y: c.y)); p.closeSubpath()
            ctx.fill(p, with: .color(.purple.opacity(0.7)))
        }

        // Recordings: grey dots. Markers: rings, filled when they have a recording.
        for fp in site.fingerprints {
            let c = t.pt(fp.pos)
            ctx.fill(Path(ellipseIn: CGRect(x: c.x - 2.5, y: c.y - 2.5, width: 5, height: 5)), with: .color(muted.opacity(0.6)))
        }
        let recorded = Set(site.fingerprints.map { $0.markerId })
        let showLabels = site.markers.count <= 80
        for m in site.markers {
            let c = t.pt(m.pos)
            let r: CGFloat = m.id == selected ? 7 : 4
            let box = CGRect(x: c.x - r, y: c.y - r, width: 2 * r, height: 2 * r)
            if m.id == selected {
                ctx.fill(Path(ellipseIn: box), with: .color(.green.opacity(0.35)))
                ctx.stroke(Path(ellipseIn: box), with: .color(.green), lineWidth: 2)
            } else {
                ctx.stroke(Path(ellipseIn: box), with: .color(recorded.contains(m.id) ? .teal : muted), lineWidth: 1.2)
            }
            if showLabels || m.id == selected {
                ctx.draw(Text(m.name).font(.system(size: 9)).foregroundColor(muted), at: CGPoint(x: c.x, y: c.y - r - 2), anchor: .bottom)
            }
        }

        guard let fix else { return }
        // A far spot that matches almost as well: orange dashed ring.
        if let other = fix.runnerUp {
            let c = t.pt(other.pos)
            let r = max(10, fix.sigmaM * t.scale * 0.6)
            ctx.stroke(Path(ellipseIn: CGRect(x: c.x - r, y: c.y - r, width: 2 * r, height: 2 * r)),
                       with: .color(.orange), style: StrokeStyle(lineWidth: 2, dash: [5, 4]))
        }
        let c = t.pt(fix.pos)
        let r = fix.sigmaM * t.scale
        ctx.fill(Path(ellipseIn: CGRect(x: c.x - r, y: c.y - r, width: 2 * r, height: 2 * r)), with: .color(.blue.opacity(0.15)))
        ctx.stroke(Path(ellipseIn: CGRect(x: c.x - r, y: c.y - r, width: 2 * r, height: 2 * r)), with: .color(.blue.opacity(0.5)), lineWidth: 1)
        ctx.fill(Path(ellipseIn: CGRect(x: c.x - 7, y: c.y - 7, width: 14, height: 14)), with: .color(.white))
        ctx.fill(Path(ellipseIn: CGRect(x: c.x - 5.5, y: c.y - 5.5, width: 11, height: 11)), with: .color(.blue))
    }
}
