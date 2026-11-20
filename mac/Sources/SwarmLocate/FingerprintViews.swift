import AppKit
import LocateCore
import SwiftUI

// A Wi-Fi fingerprint has many numbers (one per access point). The cards stay small: each shows
// a slim "signal strip", a tiny bar per access point. Click it and a popover opens beside the
// sidebar with every detail.
//
// - Selected spot: only what the place file (the phone's JSON) recorded there. No live Mac data.
// - My position: what this Mac hears now, and how it was matched to pick the spot.

private func level01(_ dbm: Double) -> CGFloat { CGFloat(min(max((dbm + 100) / 70, 0), 1)) }   // -100..-30 dBm

private func diffColor(_ d: Double) -> Color {
    let a = abs(d)
    return a < 4 ? .green : a < 8 ? .orange : .red
}

private func dbm(_ v: Double?) -> String { v.map { String(format: "%.0f", $0) } ?? "–" }

/** Tiny bar per access point, with an optional cap mark per bar (for example the other device). */
struct SignalStrip: View {
    let bars: [Double?]
    var caps: [Double?] = []
    var barColor: Color = .teal
    var capColor: Color = .meBlue
    var width: CGFloat = 72
    var height: CGFloat = 14

    var body: some View {
        Canvas { ctx, size in
            let barW: CGFloat = 2.5, gap: CGFloat = 1.5
            let n = max(1, Int((size.width + gap) / (barW + gap)))
            for i in 0..<min(n, max(bars.count, caps.count)) {
                let x = CGFloat(i) * (barW + gap)
                if i < bars.count, let p = bars[i] {
                    let h = max(1, level01(p) * size.height)
                    ctx.fill(Path(CGRect(x: x, y: size.height - h, width: barW, height: h)), with: .color(barColor.opacity(0.6)))
                }
                if i < caps.count, let m = caps[i] {
                    let y = size.height - level01(m) * size.height
                    ctx.fill(Path(CGRect(x: x - 0.5, y: max(0, y - 1), width: barW + 1, height: 2)), with: .color(capColor))
                }
            }
        }
        .frame(width: width, height: height)
    }
}

/** The strip as a capsule button that opens a popover beside the sidebar. */
struct StripButton<Popup: View>: View {
    let strip: SignalStrip
    let help: String
    @ViewBuilder let popup: () -> Popup
    @State private var open = false

    var body: some View {
        Button { open.toggle() } label: {
            HStack(spacing: 4) {
                strip
                Image(systemName: "chevron.right").font(.system(size: 8, weight: .bold)).foregroundColor(.secondary)
            }
            .padding(.horizontal, 6).padding(.vertical, 3)
            .background(Color.primary.opacity(open ? 0.14 : 0.07), in: Capsule())
            .contentShape(Capsule())
        }
        .buttonStyle(.plain)
        .help(help)
        .popover(isPresented: $open, arrowEdge: .trailing) { popup() }
    }
}

// MARK: Shared pieces

private struct Chips: View {
    let items: [(String, String, Color)]
    let help: String

    var body: some View {
        LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 6), count: 3), spacing: 6) {
            ForEach(items.indices, id: \.self) { i in
                VStack(spacing: 0) {
                    Text(items[i].0).font(.system(size: 14, weight: .semibold, design: .rounded)).foregroundColor(items[i].2)
                        .lineLimit(1).minimumScaleFactor(0.7)
                    Text(items[i].1).font(.system(size: 9)).foregroundColor(.secondary).lineLimit(1)
                }
                .frame(maxWidth: .infinity).padding(.vertical, 5)
                .background(Color.primary.opacity(0.05), in: RoundedRectangle(cornerRadius: 7, style: .continuous))
            }
        }
        .help(help)
    }
}

private func legend(_ color: Color, _ text: String) -> some View {
    HStack(spacing: 4) {
        RoundedRectangle(cornerRadius: 1.5).fill(color).frame(width: 10, height: 5)
        Text(text).font(.system(size: 10)).foregroundColor(.secondary)
    }
}

private func apTag(_ text: String, _ color: Color) -> some View {
    Text(text).font(.system(size: 8, weight: .bold)).foregroundColor(color)
        .padding(.horizontal, 4).padding(.vertical, 1)
        .background(color.opacity(0.14), in: Capsule())
}

private func copyButton(_ model: AppModel, _ text: @escaping () -> String) -> some View {
    Button {
        NSPasteboard.general.clearContents()
        NSPasteboard.general.setString(text(), forType: .string)
        model.flash("Copied as CSV.")
    } label: { Label("Copy CSV", systemImage: "doc.on.doc") }.controlSize(.small)
}

/** One level on a -100..-30 dBm scale, with an optional spread band and a second mark. */
struct LevelBar: View {
    let value: Double?
    var spread: Double? = nil
    var mark: Double? = nil
    var color: Color = .teal
    var markColor: Color = .primary

    var body: some View {
        Canvas { ctx, size in
            let w = size.width, h = size.height
            for k in 1..<7 {
                let x = w * CGFloat(k) / 7
                ctx.fill(Path(CGRect(x: x, y: 0, width: 0.5, height: h)), with: .color(.primary.opacity(0.07)))
            }
            if let v = value {
                if let s = spread, s > 0 {
                    let a = level01(v - s) * w, b = level01(v + s) * w
                    ctx.fill(Path(CGRect(x: a, y: 1, width: max(1, b - a), height: h - 2)), with: .color(.gray.opacity(0.22)))
                }
                ctx.fill(Path(roundedRect: CGRect(x: 0, y: h * 0.3, width: max(2, level01(v) * w), height: h * 0.4), cornerRadius: 1.5),
                         with: .color(color.opacity(0.85)))
            }
            if let m = mark {
                let x = level01(m) * w
                ctx.fill(Path(CGRect(x: x - 1, y: 1, width: 2, height: h - 2)), with: .color(markColor.opacity(0.7)))
            }
        }
    }
}

// MARK: Selected spot: the recording only

/** Strip + popover for one spot: what the phone recorded there, straight from the place file. */
struct RecordingButton: View {
    @EnvironmentObject var model: AppModel
    let marker: Marker

    var body: some View {
        let fp = model.recording(marker.id)
        let values = (fp?.rssi.values.sorted(by: >) ?? []).map { Optional($0) }
        StripButton(strip: SignalStrip(bars: values),
                    help: fp == nil ? "No Wi-Fi recording at this spot." : "Wi-Fi recorded here: \(fp!.rssi.count) access points. Click for every detail.") {
            RecordingPopover(marker: marker, fp: fp).environmentObject(model)
        }
    }
}

struct RecordingPopover: View {
    @EnvironmentObject var model: AppModel
    let marker: Marker
    let fp: Fingerprint?
    @State private var steadyFirst = false

    private struct Row: Identifiable {
        let bssid: String
        let dbm: Double
        let std: Double?
        var id: String { bssid }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            VStack(alignment: .leading, spacing: 2) {
                Text(marker.name).font(.headline)
                Text("What the phone recorded at this spot, from the place file. Nothing from the Mac.")
                    .font(.caption).foregroundColor(.secondary)
            }
            if let fp { content(fp) } else {
                Text("This spot has no Wi-Fi recording. Record it with the phone, then open the new place file.")
                    .font(.callout).foregroundColor(.secondary).padding(.top, 20)
                Spacer()
            }
        }
        .padding(14)
        .frame(width: 470, height: 520, alignment: .top)
    }

    private func rows(_ fp: Fingerprint) -> [Row] {
        let r = fp.rssi.map { Row(bssid: $0.key, dbm: $0.value, std: fp.std[$0.key]) }
        return steadyFirst ? r.sorted { ($0.std ?? 99, -$0.dbm) < ($1.std ?? 99, -$1.dbm) }
                           : r.sorted { $0.dbm > $1.dbm }
    }

    @ViewBuilder private func content(_ fp: Fingerprint) -> some View {
        let all = rows(fp)
        let stds = fp.std.values
        let steady = all.filter { ($0.std ?? 99) <= 4 }.count
        let ignored = all.filter { model.ignored($0.bssid) }.count
        Chips(items: [
            ("\(all.count)", "access points", .teal),
            ("\(fp.samples)", fp.samples == 1 ? "scan" : "scans", .primary),
            (String(format: "%.1f, %.1f", fp.x, fp.y), "x, y (m)", .primary),
            (all.map(\.dbm).max().map { String(format: "%.0f dBm", $0) } ?? "–", "strongest", .teal),
            (stds.isEmpty ? "–" : String(format: "±%.1f dB", stds.reduce(0, +) / Double(stds.count)), "average spread", .gray),
            ("\(steady) / \(ignored)", "steady / ignored", .secondary),
        ], help: "Spread = how much a signal moved while the phone recorded. Steady = moved 4 dB or less; these count more in matching. Ignored = dropped by the access point filter.")
        HStack(spacing: 10) {
            legend(.teal, "Recorded level")
            legend(.gray.opacity(0.5), "Spread")
            Spacer()
            Toggle("Steadiest first", isOn: $steadyFirst).toggleStyle(.checkbox).font(.caption)
        }
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 0) {
                ForEach(all) { r in row(r); Divider().opacity(0.4) }
            }
        }
        HStack {
            Text("Levels are averages over the phone's \(fp.samples) scans.").font(.system(size: 10)).foregroundColor(.secondary)
            Spacer()
            copyButton(model) { model.recordingCsv(fp, label: marker.name) }
        }
    }

    private func row(_ r: Row) -> some View {
        let label = model.site?.accessPoints.first { $0.bssid == r.bssid }?.label ?? ""
        let ignored = model.ignored(r.bssid)
        return HStack(spacing: 8) {
            VStack(alignment: .leading, spacing: 1) {
                HStack(spacing: 4) {
                    Text(r.bssid).font(.system(size: 11, weight: .medium, design: .monospaced)).strikethrough(ignored)
                    if ApFilter.isRandom(r.bssid) { apTag("random", .orange) }
                    if ignored { apTag("ignored", .red) }
                }
                if !label.isEmpty { Text(label).font(.system(size: 9)).foregroundColor(.secondary).lineLimit(1) }
            }
            .frame(width: 190, alignment: .leading)
            LevelBar(value: r.dbm, spread: r.std).frame(height: 14)
            Text(String(format: "%.0f", r.dbm)).font(.system(size: 11, weight: .semibold, design: .monospaced))
                .foregroundColor(.teal).frame(width: 30, alignment: .trailing)
            Text(r.std.map { String(format: "±%.1f", $0) } ?? "")
                .font(.system(size: 9, design: .monospaced)).foregroundColor(.secondary).frame(width: 34, alignment: .trailing)
        }
        .padding(.vertical, 4)
        .opacity(ignored ? 0.5 : 1)
        .help("\(r.bssid)\nRecorded \(String(format: "%.1f", r.dbm)) dBm" + (r.std.map { String(format: ", moved ±%.1f dB", $0) } ?? "")
              + (ApFilter.isRandom(r.bssid) ? "\nRandom (private) address: often a phone hotspot." : "")
              + (ignored ? "\nIgnored by the access point filter." : ""))
    }
}

// MARK: My position: what the Mac hears and how it matched

/** Strip + popover for My position: blue bars = Mac now, teal marks = the best matching recording. */
struct LiveButton: View {
    @EnvironmentObject var model: AppModel
    let title: String

    var body: some View {
        let c = model.compare(model.fix?.best)
        let rows = c.rows.filter { $0.mac != nil }
        StripButton(strip: SignalStrip(bars: rows.map(\.macCorrected), caps: rows.map(\.phone), barColor: .meBlue, capColor: .teal),
                    help: "What this Mac hears: \(model.heard.count) access points. Click to see them and how the spot was picked.") {
            LivePopover(title: title).environmentObject(model)
        }
    }
}

struct LivePopover: View {
    @EnvironmentObject var model: AppModel
    let title: String
    @State private var tab = 0
    @State private var gapsFirst = false

    var body: some View {
        let best = model.fix?.best
        let c = model.compare(best)
        VStack(alignment: .leading, spacing: 10) {
            VStack(alignment: .leading, spacing: 2) {
                Text("My position: \(title)").font(.headline)
                Text("What this Mac hears now, and how it picked the spot.").font(.caption).foregroundColor(.secondary)
            }
            Chips(items: [
                ("\(model.heard.count)", "heard now", .meBlue),
                ("\(model.lastKept)", "used (after filter)", .meBlue),
                ("\(model.knownHeard)", "also in recordings", .teal),
                ("\(model.scansAveraged)", "scans averaged", .primary),
                (String(format: "%+.1f dB", c.correctionDb), model.mode == .rank ? "no correction" : "Mac → phone correction", .purple),
                (c.score.map { String(format: "%.1f", $0) } ?? "–", "best match score", .secondary),
            ], help: "Used = kept by the access point filter. Correction = added to the Mac's levels so they look like the phone's. Lower match score is better.")
            Picker("", selection: $tab) {
                Text("Heard now").tag(0)
                Text("Match").tag(1)
                Text("Best matches").tag(2)
            }.pickerStyle(.segmented).labelsHidden()
            switch tab {
            case 0: heardNow
            case 1: match(c, best)
            default: matches
            }
        }
        .padding(14)
        .frame(width: 470, height: 520, alignment: .top)
    }

    // MARK: Heard now

    private var heardNow: some View {
        let avg = model.averagedScan
        let known = model.usedSite?.knownBssids ?? []
        return VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 10) {
                legend(.meBlue, "Last scan")
                legend(.primary.opacity(0.6), "Average of recent scans")
                Spacer()
            }
            if model.heard.isEmpty {
                Text(model.hidden > 0 ? "macOS hides the Wi-Fi details. Allow Location for SwarmLocate." : "No Wi-Fi heard yet.")
                    .font(.callout).foregroundColor(.secondary).padding(.top, 20)
            }
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 0) {
                    ForEach(model.heard) { ap in heardRow(ap, avg: avg[ap.bssid], known: known.contains(ap.bssid)); Divider().opacity(0.4) }
                }
            }
            HStack {
                Text(model.hidden > 0 ? "\(model.hidden) more networks without address (Location permission)." : "Scans every \(Int(model.interval)) s.")
                    .font(.system(size: 10)).foregroundColor(.secondary)
                Spacer()
                copyButton(model) { model.heardCsv() }
            }
        }
    }

    private func heardRow(_ ap: HeardAP, avg: Double?, known: Bool) -> some View {
        let ignored = model.isIgnored(ap)
        let where_ = [ap.band, ap.channel > 0 ? "ch \(ap.channel)" : ""].filter { !$0.isEmpty }.joined(separator: " ")
        return HStack(spacing: 8) {
            VStack(alignment: .leading, spacing: 1) {
                HStack(spacing: 4) {
                    Text(ap.ssid.isEmpty ? "(hidden network)" : ap.ssid).font(.system(size: 11, weight: .medium)).lineLimit(1).strikethrough(ignored)
                    if ignored { apTag("ignored", .red) } else if known { apTag("in recordings", .teal) } else { apTag("not recorded", .gray) }
                }
                Text(ap.bssid + (where_.isEmpty ? "" : " · " + where_))
                    .font(.system(size: 9, design: .monospaced)).foregroundColor(.secondary).lineLimit(1)
            }
            .frame(width: 200, alignment: .leading)
            LevelBar(value: ap.rssi, mark: avg, color: .meBlue).frame(height: 14)
            VStack(alignment: .trailing, spacing: 1) {
                Text(dbm(ap.rssi)).foregroundColor(.meBlue)
                Text(avg.map { String(format: "%.0f", $0) } ?? "–").foregroundColor(.secondary)
            }
            .font(.system(size: 9, design: .monospaced)).frame(width: 28, alignment: .trailing)
        }
        .padding(.vertical, 4)
        .opacity(ignored ? 0.5 : 1)
        .help("\(ap.ssid.isEmpty ? "(hidden network)" : ap.ssid)\n\(ap.bssid) \(where_)\nLast scan \(Int(ap.rssi)) dBm"
              + (avg.map { String(format: ", average %.1f dBm", $0) } ?? "")
              + (ignored ? "\nIgnored by the access point filter." : known ? "\nThe phone recorded it: used for matching." : "\nThe phone never recorded it: not used."))
    }

    // MARK: Match with the best recording

    private func match(_ c: Comparison, _ best: Fingerprint?) -> some View {
        let rows = (gapsFirst ? c.rows.sorted { abs($0.diff ?? 99) > abs($1.diff ?? 99) } : c.rows).filter { !model.ignored($0.bssid) }
        return VStack(alignment: .leading, spacing: 6) {
            Text(explain(c, best))
                .font(.caption).foregroundColor(.secondary).fixedSize(horizontal: false, vertical: true)
            HStack(spacing: 10) {
                legend(.meBlue, "Mac (corrected)")
                legend(.teal, "Phone at \(best.map { model.recordingName($0) } ?? "–")")
                Spacer()
                Toggle("Biggest gaps first", isOn: $gapsFirst).toggleStyle(.checkbox).font(.caption)
            }
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 0) {
                    ForEach(rows) { r in matchRow(r); Divider().opacity(0.4) }
                }
            }
            HStack {
                Text("Green: close (under 4 dB). Orange: 4 to 8. Red: more.").font(.system(size: 10)).foregroundColor(.secondary)
                Spacer()
                copyButton(model) { model.csv(c, label: best.map { model.recordingName($0) } ?? "") }
            }
        }
    }

    private func explain(_ c: Comparison, _ best: Fingerprint?) -> String {
        guard let best else { return "No match yet: the Mac must share at least 3 access points with one recording." }
        let corr = String(format: "%+.1f", c.correctionDb)
        let gap = c.rmsDb.map { String(format: "%.1f dB", $0) } ?? "–"
        return "The Mac's average (after \(corr) dB correction) next to the phone's recording at \(model.recordingName(best)), "
            + "the closest match. \(c.shared) access points are heard by both, with a typical gap of \(gap)."
    }

    private func matchRow(_ r: SignalRow) -> some View {
        let info = model.apInfo[r.bssid]
        let name = (info?.ssid).flatMap { $0.isEmpty ? nil : $0 } ?? (r.mac == nil ? "Not heard by the Mac" : "(hidden network)")
        return HStack(spacing: 8) {
            VStack(alignment: .leading, spacing: 1) {
                Text(name).font(.system(size: 11, weight: .medium)).lineLimit(1)
                Text(r.bssid).font(.system(size: 9, design: .monospaced)).foregroundColor(.secondary).lineLimit(1)
            }
            .frame(width: 168, alignment: .leading)
            DualBar(row: r).frame(height: 16)
            VStack(alignment: .trailing, spacing: 1) {
                Text(dbm(r.macCorrected)).foregroundColor(.meBlue)
                Text(dbm(r.phone)).foregroundColor(.teal)
            }
            .font(.system(size: 9, design: .monospaced)).frame(width: 26, alignment: .trailing)
            Text(r.diff.map { String(format: "%+.0f", $0) } ?? "–")
                .font(.system(size: 11, weight: .semibold, design: .monospaced))
                .foregroundColor(r.diff.map(diffColor) ?? .secondary)
                .frame(width: 34, alignment: .trailing)
        }
        .padding(.vertical, 4)
        .help([r.bssid,
               r.mac.map { String(format: "Mac %.1f dBm (%.1f after correction)", $0, r.macCorrected ?? $0) } ?? "The Mac does not hear it",
               r.phone.map { String(format: "Phone recorded %.1f dBm", $0) + (r.phoneStd.map { String(format: ", moved ±%.1f dB", $0) } ?? "") } ?? "The phone did not record it there",
               r.diff.map { String(format: "Gap %+.1f dB", $0) } ?? ""].filter { !$0.isEmpty }.joined(separator: "\n"))
    }

    // MARK: Best matches

    private var matches: some View {
        let list = model.ranked(limit: 8)
        let me = model.fix?.pos
        return VStack(alignment: .leading, spacing: 6) {
            Text("Every recorded spot gets a score: how far its Wi-Fi is from what the Mac hears. The lowest wins. When two are close, the dot goes between them. Click one to see the spot.")
                .font(.caption).foregroundColor(.secondary).fixedSize(horizontal: false, vertical: true)
            if list.isEmpty { Text("No match yet: 3 shared access points are needed.").font(.callout).foregroundColor(.secondary) }
            let best = list.first?.1 ?? 0
            let worst = max(list.last?.1 ?? 1, best + 0.1)
            ScrollView {
                VStack(spacing: 4) {
                    ForEach(Array(list.enumerated()), id: \.offset) { i, item in
                        let fp = item.0
                        let score = item.1
                        Button {
                            if !fp.markerId.isEmpty { model.selectedMarker = fp.markerId }
                        } label: {
                            HStack(spacing: 8) {
                                Text("\(i + 1)").font(.system(size: 11, weight: .bold, design: .rounded))
                                    .frame(width: 20, height: 20)
                                    .background(i == 0 ? Color.meBlue : Color.primary.opacity(0.1), in: Circle())
                                    .foregroundColor(i == 0 ? .white : .primary)
                                VStack(alignment: .leading, spacing: 1) {
                                    Text(model.recordingName(fp)).font(.system(size: 12, weight: .medium))
                                    Text(me.map { String(format: "%.1f m from your dot", fp.pos.dist($0)) } ?? "")
                                        .font(.system(size: 10)).foregroundColor(.secondary)
                                }
                                Spacer()
                                GeometryReader { g in
                                    let f = 1 - (score - best) / (worst - best) * 0.8
                                    Capsule().fill(Color.meBlue.opacity(i == 0 ? 0.8 : 0.35))
                                        .frame(width: g.size.width * CGFloat(f), height: 6)
                                        .frame(maxHeight: .infinity, alignment: .center)
                                }
                                .frame(width: 90)
                                Text(String(format: "%.1f", score)).font(.system(size: 11, design: .monospaced)).frame(width: 36, alignment: .trailing)
                            }
                            .padding(6)
                            .background(fp.markerId == model.selectedMarker && !fp.markerId.isEmpty ? Color.accentColor.opacity(0.15) : Color.clear,
                                        in: RoundedRectangle(cornerRadius: 8, style: .continuous))
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
    }
}

/** Mac level (blue, lower half) and phone level with its spread (teal, upper half) on one -100..-30 dBm scale. */
struct DualBar: View {
    let row: SignalRow

    var body: some View {
        Canvas { ctx, size in
            let w = size.width, h = size.height
            for k in 1..<7 {
                let x = w * CGFloat(k) / 7
                ctx.fill(Path(CGRect(x: x, y: 0, width: 0.5, height: h)), with: .color(.primary.opacity(0.07)))
            }
            if let p = row.phone {
                if let s = row.phoneStd, s > 0 {
                    let a = level01(p - s) * w, b = level01(p + s) * w
                    ctx.fill(Path(CGRect(x: a, y: 1, width: max(1, b - a), height: h / 2 - 2)), with: .color(.gray.opacity(0.25)))
                }
                ctx.fill(Path(roundedRect: CGRect(x: 0, y: 2, width: max(2, level01(p) * w), height: h / 2 - 4), cornerRadius: 1.5),
                         with: .color(.teal.opacity(0.75)))
            }
            if let m = row.macCorrected {
                ctx.fill(Path(roundedRect: CGRect(x: 0, y: h / 2 + 2, width: max(2, level01(m) * w), height: h / 2 - 4), cornerRadius: 1.5),
                         with: .color(.meBlue))
            }
        }
    }
}
