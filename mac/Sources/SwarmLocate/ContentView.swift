import LocateCore
import SwiftUI

/**
 * One window, laid out like Apple Maps: the floor map fills the window, a floating glass
 * sidebar on the left (search, my position, spots), map controls on the right.
 */
struct ContentView: View {
    @EnvironmentObject var model: AppModel
    @ObservedObject var location: LocationGate
    @StateObject private var map = MapController()
    @State private var showSidebar = true
    @State private var showSettings = false
    @State private var showServer = false
    @State private var lengthText = ""

    private let sidebarWidth: CGFloat = 310

    var body: some View {
        ZStack(alignment: .topLeading) {
            if model.site != nil {
                MapView(map: map, leftInset: showSidebar ? Double(sidebarWidth) + 20 : 0)
                    .ignoresSafeArea()
            } else {
                Color.mapLand.ignoresSafeArea()
                welcome.frame(maxWidth: .infinity, maxHeight: .infinity)
            }

            if model.site != nil {
                HStack(alignment: .top, spacing: 0) {
                    if showSidebar {
                        Sidebar(map: map, location: location)
                            .frame(width: sidebarWidth)
                            .glassPanel(radius: 18)
                            .padding(10)
                            .transition(.move(edge: .leading).combined(with: .opacity))
                    }
                    VStack { banner; Spacer() }.frame(maxWidth: .infinity).padding(.top, 12)
                    MapControls(map: map) { fitAll() }.padding(.top, 12).padding(.trailing, 12)
                }
                VStack {
                    Spacer()
                    HStack { ScaleBar(map: map); Spacer() }
                        .padding(.leading, showSidebar ? sidebarWidth + 22 : 12).padding(.bottom, 12)
                }
            }
        }
        .frame(minWidth: 900, minHeight: 600)
        .toolbar {
            ToolbarItem(placement: .navigation) {
                Button { withAnimation(.spring(response: 0.35, dampingFraction: 0.85)) { showSidebar.toggle() } } label: {
                    Image(systemName: "sidebar.left")
                }.help("Show or hide the sidebar")
            }
            ToolbarItemGroup(placement: .primaryAction) {
                Button { model.chooseSiteFile() } label: { Image(systemName: "folder") }.help("Open a place file")
                Button { model.choosePlanPicture() } label: { Image(systemName: "photo") }
                    .help("Choose the floor plan picture").disabled(model.site == nil)
                Button { showSettings = true } label: { Image(systemName: "slider.horizontal.3") }.help("Settings")
            }
        }
        .navigationTitle(model.site?.name ?? "SwarmLocate")
        .sheet(isPresented: $showSettings) { SettingsView(location: location, showServer: $showServer) }
        .sheet(isPresented: $showServer) { ServerSheet(isPresented: $showServer) }
        .sheet(isPresented: $model.askLength) { lengthSheet }
        .onAppear { model.start() }
        .onReceive(model.$fix) { f in
            guard map.follow, let f else { return }
            map.fly(center: f.pos)
        }
        .onChange(of: model.selectedMarker) { id in
            guard let m = model.site?.marker(id) else { return }
            if !map.follow { map.fly(center: m.pos) }
        }
    }

    private func fitAll() {
        guard map.size.width > 0 else { return }
        map.fly(to: Camera.fit(floorBounds(), map.size, leftInset: showSidebar ? Double(sidebarWidth) + 20 : 0))
    }

    private func floorBounds() -> CGRect {
        guard let site = model.site else { return CGRect(x: 0, y: 0, width: 10, height: 10) }
        let g = site.grid
        var xs = [g.originX, g.originX + g.widthM], ys = [g.bottomY, g.topY]
        if let p = model.planPlacement, model.plan != nil { xs.append(g.originX + p.widthM); ys.append(g.originY + p.heightM) }
        xs += site.markers.map { $0.x }; ys += site.markers.map { $0.y }
        return CGRect(x: xs.min()!, y: ys.min()!, width: xs.max()! - xs.min()!, height: ys.max()! - ys.min()!)
    }

    // MARK: Top banner: problems, hints, short notes

    @ViewBuilder private var banner: some View {
        if model.scaleStep > 0 {
            pill(icon: "ruler", text: model.scaleStep == 1
                 ? "Click one end of a length you know on the picture (a wall, a door, a desk row)."
                 : "Now click the other end.", tint: .blue) {
                Button("Cancel") { model.cancelScale() }.controlSize(.small)
            }
        } else if location.state != .allowed || model.hidden > 0 && model.heard.isEmpty {
            pill(icon: "location.slash", text: "Allow Location so macOS shows which Wi-Fi is which. GPS is not used.", tint: .orange) {
                if location.state == .notAsked { Button("Allow…") { location.ask() }.controlSize(.small) }
                Button("Settings") { location.openSettings() }.controlSize(.small)
            }
        } else if let p = model.problem {
            pill(icon: "exclamationmark.triangle.fill", text: p, tint: .orange) { EmptyView() }
        } else if let n = model.note {
            pill(icon: "info.circle.fill", text: n, tint: .blue) { EmptyView() }
        }
    }

    private func pill<B: View>(icon: String, text: String, tint: Color, @ViewBuilder buttons: () -> B) -> some View {
        HStack(spacing: 10) {
            Image(systemName: icon).foregroundColor(tint)
            Text(text).font(.callout).fixedSize(horizontal: false, vertical: true)
            buttons()
        }
        .padding(.horizontal, 14).padding(.vertical, 9)
        .frame(maxWidth: 520)
        .glassPanel(radius: 20)
        .transition(.move(edge: .top).combined(with: .opacity))
    }

    // MARK: First start

    private var welcome: some View {
        VStack(spacing: 16) {
            Image(systemName: "map.fill").font(.system(size: 52)).foregroundStyle(.blue.gradient)
            Text("Open your place").font(.system(size: 26, weight: .semibold))
            Text("On the phone: Setup > ⋮ > Share place file. Send the files to this Mac (AirDrop is easiest), then open the .json file here. The floor plan picture comes along when it is in the same folder.")
                .multilineTextAlignment(.center).foregroundColor(.secondary).frame(maxWidth: 440)
            HStack(spacing: 12) {
                Button { model.chooseSiteFile() } label: { Label("Open place file…", systemImage: "folder") }
                    .controlSize(.large).buttonStyle(.borderedProminent)
                Button { showServer = true } label: { Label("Get from server…", systemImage: "network") }.controlSize(.large)
            }
        }
        .padding(36)
        .glassPanel(radius: 24)
    }

    // MARK: Picture scale: length

    private var lengthSheet: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("How long is it in real life?").font(.headline)
            Text("The distance between the two points you clicked, in metres.").font(.callout).foregroundColor(.secondary)
            TextField("for example 12.5", text: $lengthText).textFieldStyle(.roundedBorder)
            HStack {
                Spacer()
                Button("Cancel") { model.cancelScale() }
                Button("Set scale") {
                    let v = Double(lengthText.replacingOccurrences(of: ",", with: ".")) ?? 0
                    model.finishScale(lengthM: v)
                    lengthText = ""
                    map.fitted = false
                    fitAll()
                }
                .keyboardShortcut(.defaultAction)
                .disabled((Double(lengthText.replacingOccurrences(of: ",", with: ".")) ?? 0) <= 0)
            }
        }
        .padding(20).frame(width: 360)
    }
}

// MARK: Sidebar

struct Sidebar: View {
    @EnvironmentObject var model: AppModel
    @ObservedObject var map: MapController
    @ObservedObject var location: LocationGate
    @State private var search = ""

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 6) {
                Image(systemName: "magnifyingglass").foregroundColor(.secondary)
                TextField("Search spots", text: $search).textFieldStyle(.plain)
                if !search.isEmpty {
                    Button { search = "" } label: { Image(systemName: "xmark.circle.fill").foregroundColor(.secondary) }
                        .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, 10).padding(.vertical, 7)
            .background(Color.primary.opacity(0.07), in: RoundedRectangle(cornerRadius: 9, style: .continuous))
            .padding(12)

            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    if search.isEmpty {
                        MyPositionCard(map: map)
                        if let m = model.site?.marker(model.selectedMarker) { SpotCard(marker: m) }
                    }
                    spots
                }
                .padding(.horizontal, 12).padding(.bottom, 12)
            }
        }
    }

    private var spots: some View {
        let site = model.site
        let recorded = Set(site?.fingerprints.map { $0.markerId } ?? [])
        let all = (site?.markers ?? []).filter {
            search.isEmpty || $0.name.localizedCaseInsensitiveContains(search) || $0.id.localizedCaseInsensitiveContains(search)
        }
        let me = model.fix?.pos
        let list = me == nil ? all.sorted { $0.name.localizedStandardCompare($1.name) == .orderedAscending }
                             : all.sorted { $0.pos.dist(me!) < $1.pos.dist(me!) }
        return VStack(alignment: .leading, spacing: 2) {
            Text(search.isEmpty ? (me == nil ? "Spots" : "Spots near you") : "Results")
                .font(.system(size: 12, weight: .semibold)).foregroundColor(.secondary).padding(.leading, 4).padding(.bottom, 2)
            if list.isEmpty {
                Text(search.isEmpty ? "This place has no spots yet." : "No spot called \"\(search)\".")
                    .font(.callout).foregroundColor(.secondary).padding(4)
            }
            ForEach(list.prefix(200)) { m in
                Button {
                    map.follow = false
                    model.selectedMarker = m.id
                    map.fly(center: m.pos, scale: max(map.camera(at: Date()).scale, 30))
                } label: {
                    HStack(spacing: 10) {
                        ZStack {
                            Circle().fill(recorded.contains(m.id) ? Color.teal : Color.gray).frame(width: 26, height: 26)
                            Image(systemName: recorded.contains(m.id) ? "wifi" : "mappin").font(.system(size: 11, weight: .bold)).foregroundColor(.white)
                        }
                        VStack(alignment: .leading, spacing: 1) {
                            Text(m.name).font(.system(size: 13, weight: .medium)).lineLimit(1)
                            Text(subtitle(m, recorded.contains(m.id), me)).font(.system(size: 11)).foregroundColor(.secondary).lineLimit(1)
                        }
                        Spacer()
                    }
                    .padding(.vertical, 5).padding(.horizontal, 6)
                    .background(model.selectedMarker == m.id ? Color.accentColor.opacity(0.18) : Color.clear,
                                in: RoundedRectangle(cornerRadius: 8, style: .continuous))
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }
        }
    }

    private func subtitle(_ m: Marker, _ rec: Bool, _ me: Vec2?) -> String {
        var parts: [String] = []
        if let me { parts.append(String(format: "%.0f m away", m.pos.dist(me))) }
        if let a = model.site?.grid.area(m.pos), !(model.site?.grid.openFloor ?? true) { parts.append(a) }
        parts.append(rec ? "Wi-Fi recorded" : "no recording")
        return parts.joined(separator: " · ")
    }
}

/** "Near D12" card with the blue dot icon, like the current location card in Maps. */
struct MyPositionCard: View {
    @EnvironmentObject var model: AppModel
    @ObservedObject var map: MapController

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 10) {
                ZStack {
                    Circle().fill(Color.meBlue.opacity(0.18)).frame(width: 34, height: 34)
                    Circle().fill(Color.white).frame(width: 16, height: 16)
                    Circle().fill(Color.meBlue).frame(width: 11, height: 11)
                }
                VStack(alignment: .leading, spacing: 1) {
                    Text("My position").font(.system(size: 11, weight: .semibold)).foregroundColor(.secondary)
                    Text(title).font(.system(size: 17, weight: .semibold)).lineLimit(2)
                }
                Spacer(minLength: 0)
            }
            if let fix = model.fix, let site = model.site {
                if fix.runnerUp != nil {
                    Label("Two spots look alike to Wi-Fi. The orange ring is the other one.", systemImage: "questionmark.circle")
                        .font(.caption).foregroundColor(.orange)
                }
                Text(String(format: "About ±%.1f m · %@ · x %.1f, y %.1f", fix.sigmaM, site.grid.area(fix.pos), fix.pos.x, fix.pos.y))
                    .font(.caption).foregroundColor(.secondary)
                HStack(spacing: 6) {
                    qualityDot(fix.bestDistance)
                    Text(quality(fix)).font(.caption).foregroundColor(.secondary)
                }
            } else {
                Text(waiting).font(.caption).foregroundColor(.secondary)
            }
            HStack {
                Text(scanLine).font(.system(size: 10)).foregroundColor(.secondary)
                Spacer()
                Button(model.paused ? "Resume" : "Pause") { model.paused.toggle() }.controlSize(.mini)
                Button("I moved") { model.freshStart() }.controlSize(.mini)
                    .help("Forget the averaged scans and start again at the new place.")
            }
        }
        .padding(12)
        .background(Color.primary.opacity(0.05), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
        .onTapGesture {
            guard let f = model.fix else { return }
            map.follow = true
            map.fly(center: f.pos, scale: max(map.camera(at: Date()).scale, 25))
        }
    }

    private var title: String {
        guard let site = model.site, let fix = model.fix else { return model.scanCount == 0 ? "Looking…" : "Not sure yet" }
        let here = name(site, fix.pos, fallback: fix.best.markerId)
        guard let other = fix.runnerUp else { return "Near \(here)" }
        let there = name(site, other.pos, fallback: other.markerId)
        return there == here ? "Near \(here)" : "Near \(here) or \(there)"
    }

    private func name(_ site: Site, _ p: Vec2, fallback: String) -> String {
        if let m = site.nearestMarker(p, withinM: 3) { return m.name }
        return fallback.isEmpty ? String(format: "%.0f, %.0f m", p.x, p.y) : site.displayName(fallback)
    }

    private var waiting: String {
        guard let site = model.site else { return "" }
        if site.fingerprints.isEmpty { return "This place file has no Wi-Fi recordings. Record some spots with the phone first." }
        if model.scanCount == 0 { return "Scanning Wi-Fi…" }
        return "Heard \(model.heard.count) access points, \(model.knownHeard) of them are in the phone recordings. At least 3 are needed."
    }

    private func quality(_ fix: Fix) -> String {
        let d = fix.bestDistance
        let word = d < 4 ? "Good" : d < 7 ? "Fair" : "Weak"
        let unit = model.mode == .rank ? "" : " dB"
        return String(format: "%@ Wi-Fi match (%.1f%@), %d scans", word, d, unit, model.scansAveraged)
    }

    private func qualityDot(_ d: Double) -> some View {
        Circle().fill(d < 4 ? Color.green : d < 7 ? Color.yellow : Color.red).frame(width: 7, height: 7)
    }

    private var scanLine: String {
        guard let t = model.lastScan else { return "Waiting for first scan" }
        let f = DateFormatter(); f.timeStyle = .medium
        return "Scan \(f.string(from: t)) · \(model.heard.count) APs"
    }
}

/** The chosen spot, like a place card: calibrate or test here. */
struct SpotCard: View {
    @EnvironmentObject var model: AppModel
    let marker: Marker

    var body: some View {
        let recorded = model.site?.fingerprints.contains { $0.markerId == marker.id } ?? false
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Image(systemName: "mappin.circle.fill").font(.system(size: 22)).foregroundColor(.red)
                VStack(alignment: .leading, spacing: 1) {
                    Text(marker.name).font(.system(size: 15, weight: .semibold))
                    Text(String(format: "x %.1f m, y %.1f m%@", marker.x, marker.y, recorded ? " · Wi-Fi recorded" : " · no Wi-Fi recording"))
                        .font(.caption).foregroundColor(.secondary)
                }
                Spacer()
                Button { model.selectedMarker = "" } label: { Image(systemName: "xmark.circle.fill").foregroundColor(.secondary) }
                    .buttonStyle(.plain)
            }
            if let f = model.fix {
                Text(String(format: "You are %.1f m from here (by Wi-Fi).", f.pos.dist(marker.pos))).font(.caption).foregroundColor(.secondary)
            }
            Text("Is the laptop here now? Wait for 3 scans, then:").font(.caption).foregroundColor(.secondary)
            HStack {
                Button { model.calibrate() } label: { Label("Calibrate", systemImage: "dial.medium") }
                    .disabled(!recorded || model.heard.isEmpty)
                    .help("Measure how the Mac hears Wi-Fi compared with the phone, at this spot.")
                Button { model.recordTest() } label: { Label("Test accuracy", systemImage: "scope") }
                    .disabled(model.fix == nil)
                    .help("Save how far the position is from this spot.")
            }
            .controlSize(.small)
            if !model.samples.isEmpty {
                let s = Accuracy.summarise(model.samples)
                Text(String(format: "%d checks · typical %.1f m · 9 in 10 within %.1f m", s.samples, s.p50M, s.p90M))
                    .font(.caption).foregroundColor(.secondary)
            }
        }
        .padding(12)
        .background(Color.primary.opacity(0.05), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
    }
}

// MARK: Settings

struct SettingsView: View {
    @EnvironmentObject var model: AppModel
    @ObservedObject var location: LocationGate
    @Binding var showServer: Bool
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        VStack(spacing: 0) {
            TabView {
                ScrollView { signal.padding(18) }.tabItem { Label("Signal", systemImage: "dial.medium") }
                ScrollView { accessPoints.padding(18) }.tabItem { Label("Access points", systemImage: "wifi") }
                ScrollView { accuracy.padding(18) }.tabItem { Label("Accuracy", systemImage: "scope") }
                ScrollView { place.padding(18) }.tabItem { Label("Place", systemImage: "building.2") }
            }
            HStack { Spacer(); Button("Done") { dismiss() }.keyboardShortcut(.defaultAction) }.padding(12)
        }
        .frame(width: 560, height: 520)
    }

    private var signal: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Mac vs phone signal").font(.headline)
            Text("A MacBook hears Wi-Fi a little louder or quieter than the phone. Pick how to correct it.")
                .font(.callout).foregroundColor(.secondary)
            Picker("", selection: $model.mode) {
                ForEach(MatchMode.allCases) { Text($0.title).tag($0) }
            }.pickerStyle(.radioGroup).labelsHidden()
            Text(modeHelp).font(.caption).foregroundColor(.secondary).fixedSize(horizontal: false, vertical: true)
            Divider()
            Text("To calibrate: put the laptop on a spot the phone recorded, click that spot on the map, wait for 3 scans, then press Calibrate on its card.")
                .font(.callout).foregroundColor(.secondary).fixedSize(horizontal: false, vertical: true)
            Stepper(value: $model.interval, in: 2...30, step: 1) { Text("Scan Wi-Fi every \(Int(model.interval)) s") }
            if location.state != .allowed {
                Divider()
                Text("Location permission is needed so macOS shows which access point is which.").font(.callout)
                HStack {
                    if location.state == .notAsked { Button("Allow Location…") { location.ask() } }
                    Button("Open Location settings") { location.openSettings() }
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var modeHelp: String {
        switch model.mode {
        case .offset:
            return model.calibratedAt.isEmpty
                ? String(format: "Adds %+.1f dB to every access point. Not calibrated yet.", model.offsetDb)
                : String(format: "Adds %+.1f dB to every access point (measured at %@).", model.offsetDb, model.calibratedAt)
        case .auto: return "Works out the gap again for every recorded spot. No calibration needed. Good to start with."
        case .rank: return "Only looks at which access points are louder than others. Use it if the other two keep giving the wrong spot."
        }
    }

    private var accessPoints: some View {
        VStack(alignment: .leading, spacing: 12) {
            FilterBox()
            let known = model.usedSite?.knownBssids ?? []
            Text("Heard now (\(model.heard.count))").font(.headline)
            VStack(alignment: .leading, spacing: 3) {
                ForEach(model.heard) { ap in
                    let ignored = model.isIgnored(ap)
                    HStack {
                        Image(systemName: ignored ? "nosign" : known.contains(ap.bssid) ? "checkmark.circle.fill" : "circle")
                            .foregroundColor(ignored ? .red : known.contains(ap.bssid) ? .teal : .secondary)
                        Text(ap.ssid.isEmpty ? "(hidden)" : ap.ssid).lineLimit(1).strikethrough(ignored)
                        Spacer()
                        Text(ap.bssid).font(.system(.caption, design: .monospaced)).foregroundColor(.secondary)
                        Text("\(Int(ap.rssi))").font(.system(.caption, design: .monospaced)).frame(width: 30, alignment: .trailing)
                    }.font(.caption)
                }
            }
            Text("Ticked = also in the phone recordings. Crossed = ignored (hotspot or not an office access point).")
                .font(.caption2).foregroundColor(.secondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var accuracy: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Accuracy check").font(.headline)
            Text("Put the laptop on a recorded spot, click the spot on the map, wait for 3 scans, then press Test accuracy on its card. Do this at several spots.")
                .font(.callout).foregroundColor(.secondary).fixedSize(horizontal: false, vertical: true)
            let s = Accuracy.summarise(model.samples)
            if s.samples == 0 {
                Text("No checks yet.").foregroundColor(.secondary)
            } else {
                Grid(alignment: .leading, horizontalSpacing: 16, verticalSpacing: 6) {
                    GridRow { Text("Checks"); Text("\(s.samples)").bold() }
                    GridRow { Text("Typical error"); Text(String(format: "%.1f m", s.p50M)).bold() }
                    GridRow { Text("9 in 10 within"); Text(String(format: "%.1f m", s.p90M)).bold() }
                    GridRow { Text("Worst"); Text(String(format: "%.1f m", s.maxM)).bold() }
                    GridRow { Text("Right area"); Text(String(format: "%.0f%%", s.correctAreaPct)).bold() }
                }
                HStack {
                    Button("Save CSV…") { model.saveCsv() }
                    Button("Clear") { model.samples = [] }
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var place: some View {
        VStack(alignment: .leading, spacing: 10) {
            if let site = model.site {
                Text(site.name).font(.headline)
                Text("\(model.siteFile) · \(site.fingerprints.count) Wi-Fi recordings · \(site.markers.count) spots · \(model.usedSite?.knownBssids.count ?? 0) of \(site.knownBssids.count) access points used")
                    .font(.caption).foregroundColor(.secondary)
            }
            HStack {
                Button("Open place file…") { model.chooseSiteFile() }
                Button("Get from server…") { dismiss(); DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { showServer = true } }
            }
            Divider()
            Text("Floor plan picture").font(.headline)
            Text(planText).font(.callout).foregroundColor(.secondary).fixedSize(horizontal: false, vertical: true)
            HStack {
                Button(model.plan == nil ? "Choose picture…" : "Change picture…") { model.choosePlanPicture() }
                if model.plan != nil {
                    Button("Set scale…") { dismiss(); model.startScale() }
                        .help("Click both ends of a length you know, then type it in metres. Same as on the phone.")
                    if model.macPlan != nil && model.site?.image != nil { Button("Use the phone's scale") { model.usePhoneScale() } }
                    Button("Remove") { model.removePlanPicture() }
                }
            }
            Toggle("Dark floor plan in Dark Mode", isOn: $model.darkPlan)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var planText: String {
        guard let p = model.planPlacement else {
            return "Choose the same picture you use on the phone. The phone's \"Share place file\" sends it too; keep it in the same folder as the .json file and it loads by itself."
        }
        let from = model.macPlan != nil ? "scale set on this Mac" : (model.site?.image != nil ? "scale from the phone" : "scale not set yet: press Set scale")
        return String(format: "%.1f m × %.1f m, %@.", p.widthM, p.heightM, from)
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

/** Which access points count: drop phone hotspots, or keep only the office's own APs. */
struct FilterBox: View {
    @EnvironmentObject var model: AppModel
    @State private var text = ""
    @State private var dropRandom = true
    @State private var loadedFor = ""

    var body: some View {
        GroupBox {
            VStack(alignment: .leading, spacing: 8) {
                Toggle("Ignore phone and laptop hotspots", isOn: $dropRandom)
                    .help("Most hotspots use a random address (second hex digit is 2, 6, A or E). They move and switch off, so they spoil the match.")
                Text("Use only these (start of address or Wi-Fi name, comma between):").font(.caption).foregroundColor(.secondary)
                TextField("for example 7c:b6:8d, Office WiFi", text: $text).textFieldStyle(.roundedBorder)
                HStack {
                    Button("Apply") { model.setFilter(text: text, dropRandom: dropRandom) }
                        .disabled(text == model.filterText && dropRandom == model.dropRandom && model.filterOverride)
                    if model.filterOverride {
                        Button("Use the place file's setting") { model.useFileFilter(); sync() }
                    }
                }.controlSize(.small)
                Text(model.filterOverride ? "Changed on this Mac." : "From the place file.")
                    .font(.caption).foregroundColor(.secondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .onAppear(perform: sync)
            .onChange(of: model.site?.id ?? "") { _ in sync() }
        } label: { Label("Which access points", systemImage: "line.3.horizontal.decrease.circle") }
    }

    private func sync() {
        text = model.filterText
        dropRandom = model.dropRandom
        loadedFor = model.site?.id ?? ""
    }
}
