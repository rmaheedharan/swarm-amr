import AppKit
import LocateCore
import SwiftUI

// A Wi-Fi fingerprint has many numbers (one per access point). The cards stay small: each shows
// a slim "signal strip", a tiny bar per access point. Click it and a popover opens beside the
// sidebar with every access point, the Mac and phone levels side by side, and the best matches.

private func level01(_ dbm: Double) -> CGFloat { CGFloat(min(max((dbm + 100) / 70, 0), 1)) }   // -100..-30 dBm

private func diffColor(_ d: Double) -> Color {
    let a = abs(d)
    return a < 4 ? .green : a < 8 ? .orange : .red
}

/** Tiny bar per access point: teal bar = phone recording, blue cap = Mac now. Caps on top of bars = good match. */
struct SignalStrip: View {
    let comparison: Comparison
    var width: CGFloat = 92
    var height: CGFloat = 16

    var body: some View {
        Canvas { ctx, size in
            let barW: CGFloat = 2.5, gap: CGFloat = 1.5
            let n = max(1, Int((size.width + gap) / (barW + gap)))
            for (i, r) in comparison.rows.prefix(n).enumerated() {
                let x = CGFloat(i) * (barW + gap)
                if let p = r.phone {
                    let h = max(1, level01(p) * size.height)
                    ctx.fill(Path(CGRect(x: x, y: size.height - h, width: barW, height: h)), with: .color(.teal.opacity(0.55)))
                }
                if let m = r.macCorrected {
                    let y = size.height - level01(m) * size.height
                    ctx.fill(Path(CGRect(x: x - 0.5, y: max(0, y - 1), width: barW + 1, height: 2)), with: .color(.meBlue))
                }
            }
        }
        .frame(width: width, height: height)
    }
}

/** The strip as a button that opens the full details. */
struct FingerprintButton: View {
    @EnvironmentObject var model: AppModel
    let title: String
    let subtitle: String
    let fp: Fingerprint?
    var showMatches = false
    @State private var open = false

    var body: some View {
        let c = model.compare(fp)
        Button { open.toggle() } label: {
            HStack(spacing: 4) {
                SignalStrip(comparison: c, width: 72, height: 14)
                Image(systemName: "chevron.right").font(.system(size: 8, weight: .bold)).foregroundColor(.secondary)
            }
            .padding(.horizontal, 6).padding(.vertical, 3)
            .background(Color.primary.opacity(open ? 0.14 : 0.07), in: Capsule())
            .contentShape(Capsule())
        }
        .buttonStyle(.plain)
        .help("Wi-Fi fingerprint: \(c.rows.count) access points. Click for every detail.")
        .popover(isPresented: $open, arrowEdge: .trailing) {
            FingerprintPopover(title: title, subtitle: subtitle, fp: fp, showMatches: showMatches)
                .environmentObject(model)
        }
    }
}

/** Every access point of one fingerprint, Mac and phone side by side, plus the best matches. */
struct FingerprintPopover: View {
    @EnvironmentObject var model: AppModel
    let title: String
    let subtitle: String
    let fp: Fingerprint?
    let showMatches: Bool
    @State private var tab = 0
    @State private var sortByDiff = false

    var body: some View {
        let c = model.compare(fp)
        VStack(alignment: .leading, spacing: 10) {
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(.headline)
                Text(subtitle).font(.caption).foregroundColor(.secondary)
            }
            chips(c)
            if showMatches {
                Picker("", selection: $tab) {
                    Text("Signals").tag(0)
                    Text("Best matches").tag(1)
                }.pickerStyle(.segmented).labelsHidden()
            }
            if tab == 0 || !showMatches { signals(c) } else { matches }
        }
        .padding(14)
        .frame(width: 470, height: 520, alignment: .top)
    }

    // MARK: Summary

    private func chips(_ c: Comparison) -> some View {
        let items: [(String, String, Color)] = [
            ("\(c.shared)", "shared", .teal),
            ("\(c.macOnly)", "Mac only", .meBlue),
            ("\(c.phoneOnly)", "phone only", .gray),
            (c.rmsDb.map { String(format: "±%.1f dB", $0) } ?? "–", "difference", c.rmsDb.map(diffColor) ?? .gray),
            (String(format: "%+.1f dB", c.correctionDb), model.mode == .rank ? "no correction" : "correction", .purple),
            (c.score.map { String(format: "%.1f", $0) } ?? "–", "match score", .secondary),
        ]
        return LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 6), count: 3), spacing: 6) {
            ForEach(items.indices, id: \.self) { i in
                VStack(spacing: 0) {
                    Text(items[i].0).font(.system(size: 14, weight: .semibold, design: .rounded)).foregroundColor(items[i].2)
                    Text(items[i].1).font(.system(size: 9)).foregroundColor(.secondary)
                }
                .frame(maxWidth: .infinity).padding(.vertical, 5)
                .background(Color.primary.opacity(0.05), in: RoundedRectangle(cornerRadius: 7, style: .continuous))
            }
        }
        .help("Shared = heard by both. Difference = how far the Mac (after correction) is from the phone, on average. Lower match score is better.")
    }

    // MARK: Signals

    private func signals(_ c: Comparison) -> some View {
        let rows = sortByDiff ? c.rows.sorted { abs($0.diff ?? 99) > abs($1.diff ?? 99) } : c.rows
        return VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 10) {
                legend(color: .meBlue, "Mac now")
                legend(color: .teal, "Phone recorded")
                legend(color: .gray.opacity(0.5), "Phone spread")
                Spacer()
                Toggle("Biggest gaps first", isOn: $sortByDiff).toggleStyle(.checkbox).font(.caption)
            }
            if rows.isEmpty {
                Text(fp == nil ? "No Wi-Fi heard yet." : "This spot has no Wi-Fi recording.")
                    .font(.callout).foregroundColor(.secondary).padding(.top, 20)
            }
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 0) {
                    ForEach(rows) { r in row(r); Divider().opacity(0.4) }
                }
            }
            HStack {
                Text(fp.map { "Phone recording: \($0.samples) scans at x \(String(format: "%.1f", $0.x)), y \(String(format: "%.1f", $0.y)) m" } ?? "")
                    .font(.system(size: 10)).foregroundColor(.secondary)
                Spacer()
                Button {
                    NSPasteboard.general.clearContents()
                    NSPasteboard.general.setString(model.csv(c, label: title), forType: .string)
                    model.flash("Fingerprint copied as CSV.")
                } label: { Label("Copy CSV", systemImage: "doc.on.doc") }.controlSize(.small)
            }
        }
    }

    private func legend(color: Color, _ text: String) -> some View {
        HStack(spacing: 4) {
            RoundedRectangle(cornerRadius: 1.5).fill(color).frame(width: 10, height: 5)
            Text(text).font(.system(size: 10)).foregroundColor(.secondary)
        }
    }

    private func row(_ r: SignalRow) -> some View {
        let info = model.apInfo[r.bssid]
        let ignored = model.ignored(r.bssid)
        let name = (info?.ssid).flatMap { $0.isEmpty ? nil : $0 } ?? (info == nil ? "Not heard by this Mac" : "(hidden network)")
        let where_ = [info?.band ?? "", info.map { $0.channel > 0 ? "ch \($0.channel)" : "" } ?? ""].filter { !$0.isEmpty }.joined(separator: " ")
        return HStack(spacing: 8) {
            VStack(alignment: .leading, spacing: 1) {
                HStack(spacing: 4) {
                    Text(name).font(.system(size: 11, weight: .medium)).lineLimit(1).strikethrough(ignored)
                    if ignored { Text("ignored").font(.system(size: 8, weight: .bold)).foregroundColor(.red) }
                }
                Text(r.bssid + (where_.isEmpty ? "" : " · " + where_))
                    .font(.system(size: 9, design: .monospaced)).foregroundColor(.secondary).lineLimit(1)
            }
            .frame(width: 168, alignment: .leading)
            DualBar(row: r).frame(height: 16)
            VStack(alignment: .trailing, spacing: 1) {
                Text(r.mac.map { String(format: "%.0f", $0) } ?? "–").foregroundColor(.meBlue)
                Text(r.phone.map { String(format: "%.0f", $0) } ?? "–").foregroundColor(.teal)
            }
            .font(.system(size: 9, design: .monospaced)).frame(width: 26, alignment: .trailing)
            Text(r.diff.map { String(format: "%+.0f", $0) } ?? "–")
                .font(.system(size: 11, weight: .semibold, design: .monospaced))
                .foregroundColor(r.diff.map(diffColor) ?? .secondary)
                .frame(width: 34, alignment: .trailing)
        }
        .padding(.vertical, 4)
        .opacity(ignored ? 0.5 : 1)
        .help(rowHelp(r, info))
    }

    private func rowHelp(_ r: SignalRow, _ info: HeardAP?) -> String {
        var t = [r.bssid]
        if let i = info { t.append("\(i.ssid.isEmpty ? "(hidden)" : i.ssid), \(i.band) channel \(i.channel)") }
        t.append(r.mac.map { String(format: "Mac now %.1f dBm (%.1f after correction)", $0, r.macCorrected ?? $0) } ?? "Mac does not hear it now")
        t.append(r.phone.map { String(format: "Phone recorded %.1f dBm", $0) + (r.phoneStd.map { String(format: ", moved ±%.1f dB", $0) } ?? "") } ?? "Phone did not record it here")
        if let d = r.diff { t.append(String(format: "Difference %+.1f dB", d)) }
        return t.joined(separator: "\n")
    }

    // MARK: Matches

    private var matches: some View {
        let list = model.ranked(limit: 8)
        let me = model.fix?.pos
        return VStack(alignment: .leading, spacing: 6) {
            Text("The recorded spots whose Wi-Fi looks most like what the Mac hears now. Lower score is a better match. Click one to see it.")
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
            // light scale marks every 10 dB
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
