import LocateCore
import SwiftUI

struct ContentView: View {
    @EnvironmentObject var model: AppModel
    @ObservedObject var location: LocationGate
    @State private var showServer = false

    var body: some View {
        HSplitView {
            Group {
                if let site = model.site {
                    FloorMapView(site: site, plan: model.plan, fix: model.fix, selected: model.selectedMarker) { model.tap($0) }
                } else {
                    VStack(spacing: 14) {
                        Image(systemName: "map").font(.system(size: 44)).foregroundColor(.secondary)
                        Text("Open the place file from the phone").font(.title3)
                        Text("On the phone: Setup > ⋮ > Share place file. Send the .json file to this Mac (AirDrop, mail, USB).")
                            .multilineTextAlignment(.center).foregroundColor(.secondary).frame(maxWidth: 380)
                        HStack {
                            Button("Open place file…") { model.chooseSiteFile() }.keyboardShortcut("o")
                            Button("Get from server…") { showServer = true }
                        }
                    }.padding()
                }
            }
            .frame(minWidth: 420, maxWidth: .infinity, maxHeight: .infinity)

            ScrollView { SidePanel(location: location, showServer: $showServer).padding(14) }
                .frame(minWidth: 320, idealWidth: 340, maxWidth: 420)
        }
        .frame(minWidth: 820, minHeight: 560)
        .sheet(isPresented: $showServer) { ServerSheet(isPresented: $showServer) }
        .onAppear { model.start() }
    }
}

struct SidePanel: View {
    @EnvironmentObject var model: AppModel
    @ObservedObject var location: LocationGate
    @Binding var showServer: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            whereBox
            if location.state != .allowed || model.hidden > 0 { permissionBox }
            if model.site != nil { correctionBox; testBox }
            placeBox
            wifiBox
        }
    }

    // MARK: Where am I

    private var whereBox: some View {
        GroupBox {
            VStack(alignment: .leading, spacing: 6) {
                if let site = model.site, let fix = model.fix {
                    Text(placeName(site, fix)).font(.system(size: 22, weight: .semibold))
                    if fix.runnerUp != nil {
                        Text("Two spots look alike to Wi-Fi. Orange ring = the other one.").font(.callout).foregroundColor(.orange)
                    }
                    Text(String(format: "x %.1f m, y %.1f m · about ±%.1f m · %@", fix.pos.x, fix.pos.y, fix.sigmaM, site.grid.area(fix.pos)))
                        .font(.callout).foregroundColor(.secondary)
                    Text(quality(fix)).font(.callout).foregroundColor(.secondary)
                } else if model.site == nil {
                    Text("No place file yet").font(.title3)
                } else if model.site?.fingerprints.isEmpty == true {
                    Text("This place file has no Wi-Fi recordings").font(.title3)
                    Text("Record some spots with the phone first (Setup > Record Wi-Fi), then share the file again.")
                        .font(.callout).foregroundColor(.secondary)
                } else {
                    Text(model.scanCount == 0 ? "Scanning Wi-Fi…" : "Not sure where you are").font(.title3)
                    if model.scanCount > 0 {
                        Text("Heard \(model.heard.count) access points, \(model.knownHeard) of them are in the phone recordings. At least 3 are needed.")
                            .font(.callout).foregroundColor(.secondary)
                    }
                }
                if let p = model.problem { Text(p).font(.callout).foregroundColor(.red) }
                if let n = model.note { Text(n).font(.callout).foregroundColor(.secondary) }
                HStack {
                    Text(scanLine).font(.caption).foregroundColor(.secondary)
                    Spacer()
                    Button(model.paused ? "Resume" : "Pause") { model.paused.toggle() }.controlSize(.small)
                    Button("I moved") { model.freshStart() }.controlSize(.small)
                        .help("Forget the averaged scans and start again at the new place.")
                }
            }.frame(maxWidth: .infinity, alignment: .leading)
        } label: { Label("Where am I", systemImage: "location") }
    }

    private func placeName(_ site: Site, _ fix: Fix) -> String {
        let here = name(site, fix.pos, fallback: fix.best.markerId)
        guard let other = fix.runnerUp else { return "Near \(here)" }
        let there = name(site, other.pos, fallback: other.markerId)
        return there == here ? "Near \(here)" : "Near \(here) or \(there)"
    }

    private func name(_ site: Site, _ p: Vec2, fallback: String) -> String {
        if let m = site.nearestMarker(p, withinM: 3) { return m.name }
        return fallback.isEmpty ? String(format: "%.0f, %.0f m", p.x, p.y) : site.displayName(fallback)
    }

    private func quality(_ fix: Fix) -> String {
        let d = fix.bestDistance
        let word = d < 4 ? "good" : d < 7 ? "fair" : "weak"
        let unit = model.mode == .rank ? "" : " dB"
        return String(format: "Wi-Fi match: %@ (%.1f%@), %d scans averaged", word, d, unit, model.scansAveraged)
    }

    private var scanLine: String {
        guard let t = model.lastScan else { return "Waiting for first scan" }
        let f = DateFormatter(); f.timeStyle = .medium
        return "Last scan \(f.string(from: t)) · \(model.heard.count) APs"
    }

    // MARK: Permission

    private var permissionBox: some View {
        GroupBox {
            VStack(alignment: .leading, spacing: 8) {
                Text("macOS shows which access point is which only to apps with Location permission. SwarmLocate does not use your GPS position; it only needs the Wi-Fi details.")
                    .font(.callout)
                HStack {
                    if location.state == .notAsked { Button("Allow Location…") { location.ask() } }
                    Button("Open Location settings") { location.openSettings() }
                }
                Text("In settings, turn on Location Services and SwarmLocate, then come back.").font(.caption).foregroundColor(.secondary)
            }.frame(maxWidth: .infinity, alignment: .leading)
        } label: { Label("Location permission needed", systemImage: "exclamationmark.triangle").foregroundColor(.orange) }
    }

    // MARK: Correction

    private var correctionBox: some View {
        GroupBox {
            VStack(alignment: .leading, spacing: 8) {
                Text("A MacBook hears Wi-Fi a little differently from the phone. Pick how to correct it.")
                    .font(.callout).foregroundColor(.secondary)
                Picker("", selection: $model.mode) {
                    ForEach(MatchMode.allCases) { Text($0.title).tag($0) }
                }.pickerStyle(.radioGroup).labelsHidden()
                Text(modeHelp).font(.caption).foregroundColor(.secondary)
                spotPicker
                Button("Calibrate: I am at this spot") { model.calibrate() }
                    .disabled(model.selectedMarker.isEmpty || model.heard.isEmpty)
                    .help("Stand (or put the laptop) on the chosen spot, wait for 3 scans, then press.")
            }.frame(maxWidth: .infinity, alignment: .leading)
        } label: { Label("Mac vs phone signal", systemImage: "dial.medium") }
    }

    private var modeHelp: String {
        switch model.mode {
        case .offset:
            return model.calibratedAt.isEmpty
                ? String(format: "Adds %+.1f dB to every access point. Not calibrated yet: put the laptop on a recorded spot, choose it below, and press Calibrate.", model.offsetDb)
                : String(format: "Adds %+.1f dB to every access point (measured at %@).", model.offsetDb, model.calibratedAt)
        case .auto:
            return "Works out the gap again for every recorded spot. No calibration needed. Good to start with."
        case .rank:
            return "Only looks at which access points are louder than others. Use it if the other two keep giving the wrong spot."
        }
    }

    private var spotPicker: some View {
        Picker("Spot", selection: $model.selectedMarker) {
            if model.selectedMarker.isEmpty { Text("Choose…").tag("") }
            ForEach(model.site?.recordedMarkers ?? []) { m in Text(m.name).tag(m.id) }
        }
        .help("Or click the spot on the map.")
    }

    // MARK: Accuracy test

    private var testBox: some View {
        GroupBox {
            VStack(alignment: .leading, spacing: 8) {
                Text("Put the laptop on a recorded spot, choose it (or click it on the map), wait for 3 scans, then press the button. Do this at several spots.")
                    .font(.callout).foregroundColor(.secondary)
                spotPicker
                HStack {
                    Button("Test: I am at this spot") { model.recordTest() }
                        .disabled(model.selectedMarker.isEmpty || model.fix == nil)
                    Spacer()
                }
                let s = Accuracy.summarise(model.samples)
                if s.samples > 0 {
                    Text(String(format: "%d checks · typical error %.1f m · 9 in 10 within %.1f m · right area %.0f%%",
                                s.samples, s.p50M, s.p90M, s.correctAreaPct))
                        .font(.callout)
                    HStack {
                        Button("Save CSV…") { model.saveCsv() }
                        Button("Clear") { model.samples = [] }
                    }.controlSize(.small)
                }
            }.frame(maxWidth: .infinity, alignment: .leading)
        } label: { Label("Accuracy check", systemImage: "scope") }
    }

    // MARK: Place

    private var placeBox: some View {
        GroupBox {
            VStack(alignment: .leading, spacing: 6) {
                if let site = model.site {
                    Text(site.name).font(.headline)
                    Text("\(model.siteFile) · \(site.fingerprints.count) Wi-Fi recordings · \(site.markers.count) markers · \(site.knownBssids.count) access points")
                        .font(.caption).foregroundColor(.secondary)
                    if site.image != nil && model.plan == nil {
                        Text("This place uses a floor plan picture. Add the same picture to see it here.")
                            .font(.caption).foregroundColor(.secondary)
                    }
                }
                HStack {
                    Button("Open file…") { model.chooseSiteFile() }
                    Button("From server…") { showServer = true }
                    if model.site?.image != nil { Button("Picture…") { model.choosePlanPicture() } }
                }.controlSize(.small)
            }.frame(maxWidth: .infinity, alignment: .leading)
        } label: { Label("Place", systemImage: "building.2") }
    }

    // MARK: Heard Wi-Fi

    private var wifiBox: some View {
        GroupBox {
            DisclosureGroup("Access points heard (\(model.heard.count))") {
                let known = model.site?.knownBssids ?? []
                VStack(alignment: .leading, spacing: 3) {
                    ForEach(model.heard) { ap in
                        HStack {
                            Image(systemName: known.contains(ap.bssid) ? "checkmark.circle.fill" : "circle")
                                .foregroundColor(known.contains(ap.bssid) ? .teal : .secondary)
                            Text(ap.ssid.isEmpty ? "(hidden)" : ap.ssid).lineLimit(1)
                            Spacer()
                            Text(ap.bssid).font(.system(.caption, design: .monospaced)).foregroundColor(.secondary)
                            Text("\(Int(ap.rssi))").font(.system(.caption, design: .monospaced)).frame(width: 30, alignment: .trailing)
                        }.font(.caption)
                    }
                    Text("Ticked = also in the phone recordings.").font(.caption2).foregroundColor(.secondary)
                    Stepper(value: $model.interval, in: 2...30, step: 1) {
                        Text("Scan every \(Int(model.interval)) s").font(.caption)
                    }
                }.padding(.top, 4)
            }
        } label: { Label("Wi-Fi", systemImage: "wifi") }
    }
}

struct ServerSheet: View {
    @EnvironmentObject var model: AppModel
    @Binding var isPresented: Bool
    @AppStorage("server") private var server = "http://192.168.1.10:8000"
    @AppStorage("siteId") private var siteId = "demo"
    @State private var busy = false

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Get the place from the swarm server").font(.headline)
            Text("The phone sends it there with Setup > Send to server.").font(.callout).foregroundColor(.secondary)
            TextField("Server address", text: $server).textFieldStyle(.roundedBorder)
            TextField("Place id", text: $siteId).textFieldStyle(.roundedBorder)
            HStack {
                Spacer()
                Button("Cancel") { isPresented = false }
                Button(busy ? "Getting…" : "Get") {
                    busy = true
                    Task {
                        await model.downloadSite(server: server, id: siteId)
                        busy = false
                        isPresented = false
                    }
                }.keyboardShortcut(.defaultAction).disabled(busy)
            }
        }
        .padding(20).frame(width: 420)
    }
}
