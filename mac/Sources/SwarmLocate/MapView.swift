import AppKit
import LocateCore
import SwiftUI

// MARK: Camera

/** What part of the floor is on screen: the site point in the middle, and points per metre. */
struct Camera: Equatable {
    var center: Vec2
    var scale: Double

    static let minScale = 2.0
    static let maxScale = 400.0

    func toScreen(_ p: Vec2, _ size: CGSize) -> CGPoint {
        CGPoint(x: size.width / 2 + (p.x - center.x) * scale, y: size.height / 2 - (p.y - center.y) * scale)
    }

    func toSite(_ q: CGPoint, _ size: CGSize) -> Vec2 {
        Vec2(center.x + (Double(q.x) - size.width / 2) / scale, center.y - (Double(q.y) - size.height / 2) / scale)
    }

    /** Zoom by [k], keeping the site point under [q] where it is. */
    mutating func zoom(by k: Double, at q: CGPoint, _ size: CGSize) {
        let p = toSite(q, size)
        scale = min(max(scale * k, Self.minScale), Self.maxScale)
        center = Vec2(p.x - (Double(q.x) - size.width / 2) / scale, p.y + (Double(q.y) - size.height / 2) / scale)
    }

    /** Move the content by (dx, dy) screen points. */
    mutating func pan(_ dx: Double, _ dy: Double) {
        center = Vec2(center.x - dx / scale, center.y + dy / scale)
    }

    /** Show the whole box, leaving room for the sidebar on the left. */
    static func fit(_ b: CGRect, _ size: CGSize, leftInset: Double = 0) -> Camera {
        let w = max(Double(size.width) - leftInset - 60, 100), h = max(Double(size.height) - 60, 100)
        let s = min(max(min(w / max(b.width, 1), h / max(b.height, 1)), minScale), maxScale)
        let c = Vec2(Double(b.midX) - leftInset / 2 / s, Double(b.midY))
        return Camera(center: c, scale: s)
    }
}

/** Holds the camera and runs smooth "fly to" moves. Drawing reads [camera(at:)] every frame. */
final class MapController: ObservableObject {
    @Published var follow = true
    @Published private(set) var revision = 0
    private(set) var cam = Camera(center: Vec2(0, 0), scale: 20)
    private var from: Camera?
    private var to: Camera?
    private var start = Date()
    private let duration = 0.45
    var size: CGSize = .zero
    var fitted = false

    func camera(at t: Date) -> Camera {
        guard let from, let to else { return cam }
        let u = min(max(t.timeIntervalSince(start) / duration, 0), 1)
        let e = u < 0.5 ? 4 * u * u * u : 1 - pow(-2 * u + 2, 3) / 2       // ease in-out
        if u >= 1 { cam = to; self.from = nil; self.to = nil; return to }
        // Zoom in log space so it feels even.
        let s = exp(log(from.scale) + (log(to.scale) - log(from.scale)) * e)
        return Camera(center: Vec2(from.center.x + (to.center.x - from.center.x) * e,
                                   from.center.y + (to.center.y - from.center.y) * e), scale: s)
    }

    /** Stop any move and change the camera now (mouse and trackpad). */
    func change(_ f: (inout Camera) -> Void) {
        var c = camera(at: Date())
        from = nil; to = nil
        f(&c)
        cam = c
        revision += 1
    }

    func fly(to target: Camera) {
        from = camera(at: Date())
        to = target
        start = Date()
        revision += 1
    }

    func fly(center: Vec2, scale: Double? = nil) {
        var t = to ?? cam
        t.center = center
        if let scale { t.scale = min(max(scale, Camera.minScale), Camera.maxScale) }
        fly(to: t)
    }

    func zoomButton(_ k: Double) {
        var t = to ?? cam
        t.scale = min(max(t.scale * k, Camera.minScale), Camera.maxScale)
        fly(to: t)
    }
}

// MARK: Mouse and trackpad

/** Transparent view on top of the map that turns mouse and trackpad events into camera moves. */
struct MapInput: NSViewRepresentable {
    var onPan: (Double, Double) -> Void
    var onZoom: (Double, CGPoint) -> Void
    var onClick: (CGPoint) -> Void
    var onDoubleClick: (CGPoint) -> Void

    func makeNSView(context: Context) -> InputView { let v = InputView(); update(v); return v }
    func updateNSView(_ v: InputView, context: Context) { update(v) }

    private func update(_ v: InputView) {
        v.onPan = onPan; v.onZoom = onZoom; v.onClick = onClick; v.onDoubleClick = onDoubleClick
    }

    final class InputView: NSView {
        var onPan: (Double, Double) -> Void = { _, _ in }
        var onZoom: (Double, CGPoint) -> Void = { _, _ in }
        var onClick: (CGPoint) -> Void = { _ in }
        var onDoubleClick: (CGPoint) -> Void = { _ in }
        private var down: CGPoint?
        private var last = CGPoint.zero
        private var dragged = false

        override var isFlipped: Bool { true }
        override var acceptsFirstResponder: Bool { true }
        override func acceptsFirstMouse(for event: NSEvent?) -> Bool { true }

        private func point(_ e: NSEvent) -> CGPoint { convert(e.locationInWindow, from: nil) }

        override func mouseDown(with e: NSEvent) {
            let p = point(e)
            down = p; last = p; dragged = false
            if e.clickCount == 2 { onDoubleClick(p) }
        }

        override func mouseDragged(with e: NSEvent) {
            let p = point(e)
            if let d = down, hypot(p.x - d.x, p.y - d.y) > 3 { dragged = true }
            if dragged {
                NSCursor.closedHand.set()
                onPan(Double(p.x - last.x), Double(p.y - last.y))
            }
            last = p
        }

        override func mouseUp(with e: NSEvent) {
            if !dragged && e.clickCount == 1 { onClick(point(e)) }
            if dragged { NSCursor.arrow.set() }
            down = nil
        }

        override func scrollWheel(with e: NSEvent) {
            let p = point(e)
            if e.hasPreciseScrollingDeltas && !e.modifierFlags.contains(.command) && !e.modifierFlags.contains(.option) {
                onPan(Double(e.scrollingDeltaX), Double(e.scrollingDeltaY))          // two fingers: move
            } else {
                let dy = Double(e.scrollingDeltaY) * (e.hasPreciseScrollingDeltas ? 1 : 8)
                onZoom(exp(dy * 0.01), p)                                              // mouse wheel: zoom
            }
        }

        override func magnify(with e: NSEvent) { onZoom(1 + Double(e.magnification), point(e)) }
        override func smartMagnify(with e: NSEvent) { onZoom(2, point(e)) }
    }
}

// MARK: Colours (light and dark, like a map)

extension Color {
    static func dynamic(_ light: NSColor, _ dark: NSColor) -> Color {
        Color(nsColor: NSColor(name: nil) { a in
            a.bestMatch(from: [.darkAqua, .vibrantDark]) != nil ? dark : light
        })
    }
    static let mapLand = dynamic(NSColor(srgbRed: 0.965, green: 0.957, blue: 0.937, alpha: 1), NSColor(srgbRed: 0.13, green: 0.135, blue: 0.15, alpha: 1))
    static let mapFloor = dynamic(.white, NSColor(srgbRed: 0.19, green: 0.195, blue: 0.21, alpha: 1))
    static let mapBuilding = dynamic(NSColor(srgbRed: 0.88, green: 0.87, blue: 0.85, alpha: 1), NSColor(srgbRed: 0.27, green: 0.275, blue: 0.29, alpha: 1))
    static let mapEdge = dynamic(NSColor(srgbRed: 0.80, green: 0.79, blue: 0.77, alpha: 1), NSColor(srgbRed: 0.33, green: 0.335, blue: 0.35, alpha: 1))
    static let mapLabel = dynamic(NSColor(srgbRed: 0.30, green: 0.30, blue: 0.32, alpha: 1), NSColor(srgbRed: 0.82, green: 0.82, blue: 0.84, alpha: 1))
    static let meBlue = Color(red: 0.0, green: 0.48, blue: 1.0)
}

// MARK: The map

/** Full-window floor map: the plan picture or racks, spots, and the pulsing blue dot. */
struct MapView: View {
    @EnvironmentObject var model: AppModel
    @ObservedObject var map: MapController
    @Environment(\.colorScheme) private var scheme
    var leftInset: Double

    var body: some View {
        GeometryReader { geo in
            ZStack {
                TimelineView(.animation(minimumInterval: 1.0 / 60)) { tl in
                    Canvas { ctx, size in
                        let cam = map.camera(at: tl.date)
                        draw(&ctx, size, cam, tl.date)
                    }
                }
                MapInput(
                    onPan: { dx, dy in map.follow = false; map.change { $0.pan(dx, dy) } },
                    onZoom: { k, q in map.change { $0.zoom(by: k, at: q, geo.size) } },
                    onClick: { q in
                        let cam = map.camera(at: Date())
                        model.mapClick(cam.toSite(q, geo.size), pickRadiusM: 18 / cam.scale)
                        if !model.selectedMarker.isEmpty, model.scaleStep == 0 { map.follow = false }
                    },
                    onDoubleClick: { q in map.change { $0.zoom(by: 2, at: q, geo.size) } })
            }
            .onAppear { map.size = geo.size; fitIfNeeded(geo.size) }
            .onChange(of: geo.size) { s in map.size = s }
            .onChange(of: model.site?.id ?? "") { _ in map.fitted = false; fitIfNeeded(geo.size) }
        }
        .background(Color.mapLand)
    }

    private func fitIfNeeded(_ size: CGSize) {
        guard !map.fitted, model.site != nil, size.width > 0 else { return }
        map.fitted = true
        map.change { $0 = Camera.fit(bounds(), size, leftInset: leftInset) }
    }

    /** The floor in site metres. */
    func bounds() -> CGRect {
        guard let site = model.site else { return CGRect(x: 0, y: 0, width: 10, height: 10) }
        let g = site.grid
        var xs = [g.originX, g.originX + g.widthM], ys = [g.bottomY, g.topY]
        if let p = model.planPlacement, model.plan != nil { xs.append(g.originX + p.widthM); ys.append(g.originY + p.heightM) }
        xs += site.markers.map { $0.x } + site.fingerprints.map { $0.x }
        ys += site.markers.map { $0.y } + site.fingerprints.map { $0.y }
        let x0 = xs.min()!, x1 = xs.max()!, y0 = ys.min()!, y1 = ys.max()!
        return CGRect(x: x0, y: y0, width: x1 - x0, height: y1 - y0)
    }

    private func draw(_ ctx: inout GraphicsContext, _ size: CGSize, _ cam: Camera, _ now: Date) {
        guard let site = model.site else { return }
        let g = site.grid
        func pt(_ p: Vec2) -> CGPoint { cam.toScreen(p, size) }
        func rect(_ x: Double, _ y: Double, _ w: Double, _ h: Double) -> CGRect {
            let tl = cam.toScreen(Vec2(x, y + h), size)
            return CGRect(x: tl.x, y: tl.y, width: w * cam.scale, height: h * cam.scale)
        }

        // Floor: the plan picture, or a drawn floor with racks.
        if let plan = model.plan, let place = model.planPlacement {
            let r = rect(g.originX, g.originY, place.widthM, place.heightM)
            ctx.fill(Path(roundedRect: r.insetBy(dx: -2, dy: -2), cornerRadius: 4), with: .color(.mapFloor))
            ctx.drawLayer { l in
                if scheme == .dark && model.darkPlan {
                    // A dark floor plan, like a map at night: invert, keep the colours' hue.
                    l.addFilter(.colorInvert())
                    l.addFilter(.hueRotation(.degrees(180)))
                    l.addFilter(.brightness(-0.06))
                }
                l.draw(Image(nsImage: plan).interpolation(.high), in: r)
            }
        } else {
            let floor = rect(g.originX, g.bottomY, g.widthM, g.depthM)
            ctx.fill(Path(roundedRect: floor, cornerRadius: 6), with: .color(.mapFloor))
            ctx.stroke(Path(roundedRect: floor, cornerRadius: 6), with: .color(.mapEdge), lineWidth: 1)
            if !g.openFloor {
                for a in 0..<g.nAisles where g.narrowAisles.contains(a) {
                    let w = g.aislePitchM * 0.55
                    ctx.fill(Path(rect(g.aisleX(a) - w / 2, g.bottomY + g.cellM * 0.5, w, g.depthM - g.cellM)),
                             with: .color(.orange.opacity(0.12)))
                }
                for k in 0...g.nAisles {
                    let x = g.originX + Double(k) * g.aislePitchM, w = g.aislePitchM * 0.4
                    let r = rect(x - w / 2, g.bottomY + g.cellM * 0.6, w, g.depthM - g.cellM * 1.2)
                    ctx.fill(Path(roundedRect: r, cornerRadius: min(4, r.width / 3)), with: .color(.mapBuilding))
                }
                if cam.scale > 12 {
                    for a in 0..<g.nAisles {
                        ctx.draw(Text("A\(a)").font(.system(size: 10, weight: .semibold)).foregroundColor(.mapLabel.opacity(0.7)),
                                 at: pt(Vec2(g.aisleX(a), g.bottomY - 0.15)), anchor: .top)
                    }
                }
            }
        }

        // Wi-Fi access points (only when zoomed in).
        if cam.scale > 14 {
            var wifi = ctx.resolve(Image(systemName: "wifi"))
            wifi.shading = .color(.purple.opacity(0.75))
            for ap in site.accessPoints { ctx.draw(wifi, at: pt(ap.pos)) }
        }

        // Recordings: faint dots.
        if cam.scale > 8 {
            for fp in site.fingerprints {
                let c = pt(fp.pos)
                ctx.fill(Path(ellipseIn: CGRect(x: c.x - 2, y: c.y - 2, width: 4, height: 4)), with: .color(.teal.opacity(0.35)))
            }
        }

        // Spots: small round badges, names when there is room.
        let recorded = Set(site.fingerprints.map { $0.markerId })
        let spacing = minSpacing(site.markers)
        let labels = spacing * cam.scale > 46
        for m in site.markers where m.id != model.selectedMarker {
            let c = pt(m.pos)
            let rec = recorded.contains(m.id)
            let r: CGFloat = 5
            ctx.fill(Path(ellipseIn: CGRect(x: c.x - r - 1.5, y: c.y - r - 1.5, width: 2 * r + 3, height: 2 * r + 3)), with: .color(.white))
            ctx.fill(Path(ellipseIn: CGRect(x: c.x - r, y: c.y - r, width: 2 * r, height: 2 * r)),
                     with: .color(rec ? .teal : .gray))
            if labels {
                ctx.draw(Text(m.name).font(.system(size: 10, weight: .medium)).foregroundColor(.mapLabel),
                         at: CGPoint(x: c.x, y: c.y + r + 2), anchor: .top)
            }
        }

        // Me: accuracy circle, pulse, blue dot. A far look-alike spot gets a dashed orange ring.
        if let fix = model.fix {
            if let other = fix.runnerUp {
                let c = pt(other.pos)
                let r = max(14, fix.sigmaM * cam.scale)
                ctx.stroke(Path(ellipseIn: CGRect(x: c.x - r, y: c.y - r, width: 2 * r, height: 2 * r)),
                           with: .color(.orange), style: StrokeStyle(lineWidth: 2, dash: [6, 5]))
                ctx.draw(Text("?").font(.system(size: 13, weight: .bold)).foregroundColor(.orange), at: c)
            }
            let c = pt(fix.pos)
            let acc = fix.sigmaM * cam.scale
            let accRect = CGRect(x: c.x - acc, y: c.y - acc, width: 2 * acc, height: 2 * acc)
            ctx.fill(Path(ellipseIn: accRect), with: .color(.meBlue.opacity(0.13)))
            ctx.stroke(Path(ellipseIn: accRect), with: .color(.meBlue.opacity(0.35)), lineWidth: 1)
            let phase = now.timeIntervalSinceReferenceDate.truncatingRemainder(dividingBy: 2.0) / 2.0
            let pr = 9 + 22 * phase
            ctx.fill(Path(ellipseIn: CGRect(x: c.x - pr, y: c.y - pr, width: 2 * pr, height: 2 * pr)),
                     with: .color(.meBlue.opacity(0.35 * (1 - phase))))
            var shadow = ctx
            shadow.addFilter(.shadow(color: .black.opacity(0.3), radius: 3, y: 1))
            shadow.fill(Path(ellipseIn: CGRect(x: c.x - 10, y: c.y - 10, width: 20, height: 20)), with: .color(.white))
            ctx.fill(Path(ellipseIn: CGRect(x: c.x - 7, y: c.y - 7, width: 14, height: 14)), with: .color(.meBlue))
        }

        // Selected spot: a red pin, like a dropped pin.
        if let m = site.marker(model.selectedMarker) {
            let c = pt(m.pos)
            var pin = ctx.resolve(Image(systemName: "mappin.circle.fill"))
            pin.shading = .color(.red)
            var s = ctx
            s.addFilter(.shadow(color: .black.opacity(0.35), radius: 3, y: 2))
            s.fill(Path(ellipseIn: CGRect(x: c.x - 3, y: c.y - 3, width: 6, height: 6)), with: .color(.red))
            var stem = Path(); stem.move(to: c); stem.addLine(to: CGPoint(x: c.x, y: c.y - 14))
            s.stroke(stem, with: .color(.red), lineWidth: 2)
            s.draw(pin, in: CGRect(x: c.x - 15, y: c.y - 44, width: 30, height: 30))
            ctx.draw(Text(m.name).font(.system(size: 12, weight: .semibold)).foregroundColor(.primary),
                     at: CGPoint(x: c.x, y: c.y + 6), anchor: .top)
        }
    }

    private func minSpacing(_ ms: [Marker]) -> Double {
        guard ms.count > 1 else { return 10 }
        var best = Double.infinity
        let sample = ms.prefix(300)
        for (i, a) in sample.enumerated() {
            for b in sample.dropFirst(i + 1) { best = min(best, a.pos.dist(b.pos)) }
        }
        return max(best, 0.3)
    }
}

// MARK: Map controls

/** Zoom, "show me" and "show all" buttons, top right, like Maps. */
struct MapControls: View {
    @EnvironmentObject var model: AppModel
    @ObservedObject var map: MapController
    var fitAll: () -> Void

    var body: some View {
        VStack(spacing: 10) {
            VStack(spacing: 0) {
                iconButton("plus", "Zoom in") { map.zoomButton(1.6) }
                Divider().frame(width: 22)
                iconButton("minus", "Zoom out") { map.zoomButton(1 / 1.6) }
            }
            .glassPanel(radius: 10)
            iconButton(map.follow ? "location.fill" : "location", "Show my position") {
                map.follow = true
                if let f = model.fix { map.fly(center: f.pos, scale: max(map.camera(at: Date()).scale, 25)) }
            }
            .foregroundColor(map.follow ? .meBlue : .primary)
            .glassPanel(radius: 10)
            iconButton("arrow.up.left.and.arrow.down.right", "Show the whole floor") { map.follow = false; fitAll() }
                .glassPanel(radius: 10)
        }
    }

    private func iconButton(_ name: String, _ help: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: name).font(.system(size: 14, weight: .medium)).frame(width: 34, height: 32).contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .help(help)
    }
}

/** "0 — 5 m" bar, bottom left, sized to a round number of metres. */
struct ScaleBar: View {
    @ObservedObject var map: MapController

    var body: some View {
        TimelineView(.periodic(from: .now, by: 0.25)) { tl in
            let s = map.camera(at: tl.date).scale
            let (m, w) = nice(s)
            VStack(alignment: .leading, spacing: 2) {
                Text(m >= 1 ? "\(Int(m)) m" : String(format: "%.1f m", m)).font(.system(size: 10, weight: .medium))
                ZStack(alignment: .leading) {
                    Rectangle().fill(Color.primary.opacity(0.75)).frame(width: w, height: 2)
                    HStack { Rectangle().frame(width: 1.5, height: 6); Spacer(); Rectangle().frame(width: 1.5, height: 6) }
                        .frame(width: w).foregroundColor(.primary.opacity(0.75))
                }
            }
            .padding(.horizontal, 8).padding(.vertical, 5)
            .glassPanel(radius: 8)
        }
    }

    private func nice(_ scale: Double) -> (Double, CGFloat) {
        let target = 90 / scale
        let p = pow(10, floor(log10(target)))
        let m = [1.0, 2, 5, 10].map { $0 * p }.last { $0 <= target } ?? p
        return (m, CGFloat(m * scale))
    }
}

// MARK: Glass

extension View {
    /** Floating panel look: Liquid Glass on macOS 26 and newer, frosted material before. */
    @ViewBuilder
    func glassPanel(radius: CGFloat = 16) -> some View {
        #if compiler(>=6.2)
        if #available(macOS 26.0, *) {
            self.glassEffect(.regular, in: RoundedRectangle(cornerRadius: radius, style: .continuous))
        } else {
            materialPanel(radius)
        }
        #else
        materialPanel(radius)
        #endif
    }

    func materialPanel(_ radius: CGFloat) -> some View {
        self
            .background(.regularMaterial, in: RoundedRectangle(cornerRadius: radius, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: radius, style: .continuous).strokeBorder(Color.primary.opacity(0.08), lineWidth: 0.5))
            .shadow(color: .black.opacity(0.18), radius: 10, y: 3)
    }
}
