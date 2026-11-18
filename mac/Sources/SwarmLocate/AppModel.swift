import AppKit
import Foundation
import LocateCore
import UniformTypeIdentifiers

/** Everything the window shows. Scans Wi-Fi every few seconds and matches it to the place file. */
@MainActor
final class AppModel: ObservableObject {
    @Published var site: Site?
    @Published var siteFile: String = ""
    @Published var plan: NSImage?
    @Published var fix: Fix?
    @Published var heard: [HeardAP] = []
    @Published var hidden = 0
    @Published var lastFresh = 0
    @Published var lastKept = 0
    @Published var lastScan: Date?
    @Published var scanCount = 0
    @Published var problem: String?
    @Published var note: String?
    @Published var paused = false
    @Published var selectedMarker: String = ""
    @Published var samples: [AccuracySample] = []
    @Published var mode: MatchMode = .auto {
        didSet { if !loading { saveCorrection() } ; relocate() }
    }
    @Published var offsetDb: Double = 0
    @Published var calibratedAt: String = ""
    /** Mac-side change to the place file's access point filter ("office APs only"). */
    @Published var filterOverride = false
    @Published var filterText = ""
    @Published var dropRandom = true
    /** Wi-Fi names seen in scans, by BSSID. Recordings have no names, so the name rule needs these. */
    private var ssids: [String: String] = [:]
    /** Floor plan picture scale set on this Mac (when the place file has none, or it was redone here). */
    @Published var macPlan: FloorImage?
    /** Setting the picture scale: 0 off, 1 waiting for the first click, 2 for the second. */
    @Published var scaleStep = 0
    @Published var askLength = false
    private var scaleA: Vec2?
    private var scalePx = 0.0
    @Published var darkPlan: Bool = UserDefaults.standard.object(forKey: "darkPlan") as? Bool ?? true {
        didSet { prefs.set(darkPlan, forKey: "darkPlan") }
    }
    private var noteTask: Task<Void, Never>?
    @Published var interval: Double = 4 {
        didSet { prefs.set(interval, forKey: "interval") }
    }

    let location = LocationGate()
    private let scanner = WifiScanner()
    private let tracker = Tracker()
    private var loop: Task<Void, Never>?
    private var loading = false
    private let prefs = UserDefaults.standard

    init() {
        let i = prefs.double(forKey: "interval")
        if i >= 2 { interval = i }
        if let path = prefs.string(forKey: "lastSite"), FileManager.default.fileExists(atPath: path) {
            try? openSite(URL(fileURLWithPath: path))
        }
    }

    // MARK: Scanning

    func start() {
        guard loop == nil else { return }
        if location.state == .notAsked { location.ask() }
        loop = Task { [weak self] in
            while !Task.isCancelled {
                guard let self else { return }
                if !self.paused { await self.scanOnce() }
                let ns = UInt64(max(2, self.interval) * 1_000_000_000)
                try? await Task.sleep(nanoseconds: ns)
            }
        }
    }

    func scanOnce() async {
        let r = await scanner.scan()
        lastScan = Date()
        scanCount += 1
        hidden = r.hidden
        lastFresh = r.fresh
        if let e = r.error {
            problem = e
            return
        }
        heard = r.aps
        for ap in r.aps where !ap.ssid.isEmpty { ssids[ap.bssid] = ap.ssid }
        if r.aps.isEmpty && r.hidden > 0 {
            problem = "macOS hides the Wi-Fi details from this app. Allow Location for SwarmLocate (button below)."
            return
        }
        if r.aps.isEmpty {
            problem = "No Wi-Fi heard."
            return
        }
        let f = filter
        let kept = r.aps.filter { f.keeps($0.bssid, ssid: $0.ssid.isEmpty ? nil : $0.ssid) }
        lastKept = kept.count
        if kept.isEmpty {
            problem = "Every access point heard is filtered out. Check \"Which access points\"."
            return
        }
        problem = nil
        let use = Dictionary(kept.map { ($0.bssid, $0.rssi) }, uniquingKeysWith: max)
        if tracker.add(use) { flash("Wi-Fi changed a lot: looks like you moved. Starting fresh.") }
        relocate()
    }

    /** Match again with the current scans (after a change of setting). */
    func relocate() {
        guard let site = usedSite else { fix = nil; return }
        fix = tracker.locate(site, mode: mode, offsetDb: offsetDb)
    }

    /** Forget the averaged scans, for example after carrying the laptop to another desk. */
    func freshStart() {
        tracker.clear()
        fix = nil
        flash("Cleared. The next scan starts a new average.")
    }

    var scansAveraged: Int { tracker.scans.count }

    // MARK: Which access points

    /** The filter in use: the Mac's own setting if changed here, else the place file's. */
    var filter: ApFilter {
        if filterOverride { return ApFilter.parse(filterText, dropRandom: dropRandom) }
        return site?.apFilter ?? ApFilter()
    }

    /** The place with filtered-out access points removed from the recordings. */
    var usedSite: Site? { site?.filtered(filter, ssids: ssids) }

    func isIgnored(_ ap: HeardAP) -> Bool { !filter.keeps(ap.bssid, ssid: ap.ssid.isEmpty ? nil : ap.ssid) }

    /** Change the filter on this Mac. Old scans are dropped because they used the old rule. */
    func setFilter(text: String, dropRandom: Bool) {
        filterOverride = true
        filterText = text
        self.dropRandom = dropRandom
        if let site {
            prefs.set(true, forKey: "filterOverride." + site.id)
            prefs.set(text, forKey: "filterText." + site.id)
            prefs.set(dropRandom, forKey: "dropRandom." + site.id)
        }
        tracker.clear()
        relocate()
        flash("Access point filter changed. Starting a fresh average.")
    }

    /** Go back to the filter stored in the place file. */
    func useFileFilter() {
        filterOverride = false
        if let site {
            prefs.removeObject(forKey: "filterOverride." + site.id)
            filterText = site.apFilter.text
            dropRandom = site.apFilter.dropRandom
        }
        tracker.clear()
        relocate()
    }

    // MARK: Wi-Fi check

    /** Most access points the averaged scan shares with one recording. 3 or more are needed. */
    var bestCommon: Int { usedSite.map { tracker.bestCommon($0) } ?? 0 }
    var averagedCount: Int { tracker.averaged.count }

    /** First 3 bytes of BSSIDs, most common first: shows quickly whether Mac and phone hear the same APs. */
    static func prefixes(_ bssids: [String], top: Int = 3) -> String {
        var n: [String: Int] = [:]
        for b in bssids { n[String(b.prefix(8)), default: 0] += 1 }
        let l = n.sorted { $0.value > $1.value }.prefix(top).map { "\($0.key) ×\($0.value)" }
        return l.isEmpty ? "none" : l.joined(separator: ", ")
    }

    /** Plain text for the user to paste back when something does not work. */
    func report() -> String {
        let v = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "dev"
        let known = usedSite?.knownBssids ?? []
        var out = """
        SwarmLocate \(v) on macOS \(ProcessInfo.processInfo.operatingSystemVersionString)
        Location permission: \(location.state)
        Scans: \(scanCount), every \(Int(interval)) s, paused: \(paused)
        Last scan: \(lastFresh) networks fresh, \(heard.count) with address (incl. recent), \(hidden) without address
        Kept after filter: \(lastKept) (filter: \(filter.dropRandom ? "drop random" : "keep random"); only: \(filter.text.isEmpty ? "-" : filter.text))
        Averaged APs: \(averagedCount) from \(scansAveraged) scans
        Place: \(site?.name ?? "none"), \(site?.fingerprints.count ?? 0) recordings, \(site?.knownBssids.count ?? 0) APs (\(known.count) after filter)
        Recordings' APs start with: \(Self.prefixes(Array(site?.knownBssids ?? [])))
        Mac hears APs starting with: \(Self.prefixes(heard.map { $0.bssid }))
        Heard now that are in recordings: \(knownHeard)
        Best recording shares: \(bestCommon) APs (3 needed)
        Mode: \(mode.rawValue), offset \(String(format: "%+.1f", offsetDb)) dB
        Position: \(fix.map { String(format: "x %.1f y %.1f ±%.1f m, match %.1f", $0.pos.x, $0.pos.y, $0.sigmaM, $0.bestDistance) } ?? "none")
        Problem: \(problem ?? "-")
        Strongest heard:
        """
        for ap in heard.prefix(12) {
            let tag = isIgnored(ap) ? "ignored" : known.contains(ap.bssid) ? "in recordings" : "not in recordings"
            out += "\n  \(ap.bssid)  \(Int(ap.rssi)) dBm  ch \(ap.channel)  \(ap.ssid.isEmpty ? "(hidden)" : ap.ssid)  \(tag)"
        }
        return out
    }

    func copyReport() {
        NSPasteboard.general.clearContents()
        NSPasteboard.general.setString(report(), forType: .string)
        flash("Wi-Fi report copied. Paste it in the chat.")
    }

    /** How many APs heard now also appear in the (filtered) recordings. */
    var knownHeard: Int {
        guard let site = usedSite else { return 0 }
        let known = site.knownBssids
        return heard.filter { known.contains($0.bssid) }.count
    }

    // MARK: Place file

    func openSite(_ url: URL) throws {
        let s = try Site.load(Data(contentsOf: url))
        site = s
        siteFile = url.lastPathComponent
        prefs.set(url.path, forKey: "lastSite")
        loadCorrection(for: s)
        filterOverride = prefs.bool(forKey: "filterOverride." + s.id)
        filterText = filterOverride ? (prefs.string(forKey: "filterText." + s.id) ?? "") : s.apFilter.text
        dropRandom = filterOverride ? prefs.bool(forKey: "dropRandom." + s.id) : s.apFilter.dropRandom
        tracker.clear()
        fix = nil
        samples = []
        selectedMarker = ""
        scaleStep = 0
        macPlan = loadMacPlan(s.id)
        plan = nil
        if let p = prefs.string(forKey: "plan." + s.id), let img = NSImage(contentsOfFile: p) { plan = img }
        if plan == nil, s.image != nil {
            // The phone keeps the picture next to the place file under its own name; try that.
            let sibling = url.deletingLastPathComponent().appendingPathComponent(s.image!.file)
            plan = NSImage(contentsOf: sibling)
            if plan != nil { prefs.set(sibling.path, forKey: "plan." + s.id) }
        }
        problem = nil
    }

    func chooseSiteFile() {
        let p = NSOpenPanel()
        p.title = "Open the place file from the phone"
        p.allowedContentTypes = [.json]
        p.allowsMultipleSelection = false
        guard p.runModal() == .OK, let url = p.url else { return }
        do { try openSite(url) } catch { alert("Could not read this file", "\(error.localizedDescription)\n\nUse Setup > Share place file on the phone, and open that .json file.") }
    }

    func downloadSite(server: String, id: String) async {
        var base = server.trimmingCharacters(in: .whitespacesAndNewlines)
        if !base.lowercased().hasPrefix("http") { base = "http://" + base }
        while base.hasSuffix("/") { base.removeLast() }
        guard let url = URL(string: "\(base)/api/sites/\(id.trimmingCharacters(in: .whitespaces))") else {
            alert("That address does not look right", base); return
        }
        do {
            let (data, resp) = try await URLSession.shared.data(from: url)
            if let h = resp as? HTTPURLResponse, h.statusCode != 200 { throw URLError(.badServerResponse, userInfo: [NSLocalizedDescriptionKey: "Server answered \(h.statusCode)"]) }
            _ = try Site.load(data)
            let dir = try FileManager.default.url(for: .applicationSupportDirectory, in: .userDomainMask, appropriateFor: nil, create: true)
                .appendingPathComponent("SwarmLocate", isDirectory: true)
            try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
            let file = dir.appendingPathComponent("site-\(id).json")
            try data.write(to: file)
            try openSite(file)
        } catch {
            alert("Could not get the place from the server", error.localizedDescription)
        }
    }

    func choosePlanPicture() {
        guard let site else { return }
        let p = NSOpenPanel()
        p.title = "Choose the floor plan picture used on the phone"
        p.allowedContentTypes = [.image]
        guard p.runModal() == .OK, let url = p.url, let img = NSImage(contentsOf: url) else { return }
        plan = img
        prefs.set(url.path, forKey: "plan." + site.id)
        if site.image == nil && macPlan == nil {
            // No scale from the phone: guess one that fits the floor, then ask for the real one.
            let px = pixelSize(img)
            macPlan = FloorImage(file: url.lastPathComponent, widthPx: px.0, heightPx: px.1,
                                 metresPerPx: max(site.grid.widthM, 5) / Double(max(px.0, 1)))
            saveMacPlan()
            startScale()
        }
    }

    func removePlanPicture() {
        guard let site else { return }
        plan = nil
        prefs.removeObject(forKey: "plan." + site.id)
    }

    /** Where the picture sits: the phone's scale from the place file, unless it was redone on this Mac. */
    var planPlacement: FloorImage? {
        if let m = macPlan { return m }
        if let i = site?.image { return i }
        guard let plan, let site else { return nil }
        let px = pixelSize(plan)
        return FloorImage(file: "", widthPx: px.0, heightPx: px.1, metresPerPx: max(site.grid.widthM, 5) / Double(max(px.0, 1)))
    }

    private func pixelSize(_ img: NSImage) -> (Int, Int) {
        if let r = img.representations.first, r.pixelsWide > 0 { return (r.pixelsWide, r.pixelsHigh) }
        return (Int(img.size.width), Int(img.size.height))
    }

    // MARK: Picture scale (two clicks on a known length, like the phone)

    func startScale() {
        guard plan != nil else { return }
        scaleStep = 1
        scaleA = nil
    }

    func cancelScale() { scaleStep = 0; scaleA = nil; askLength = false }

    func finishScale(lengthM: Double) {
        askLength = false
        scaleStep = 0
        guard let site, let place = planPlacement, scalePx > 0, lengthM > 0 else { return }
        let m = lengthM / scalePx
        macPlan = FloorImage(file: place.file, widthPx: place.widthPx, heightPx: place.heightPx, metresPerPx: m)
        saveMacPlan()
        flash(String(format: "Picture scale set: %.1f m wide. Saved for %@.", macPlan!.widthM, site.name))
    }

    /** Go back to the picture scale from the phone. */
    func usePhoneScale() {
        guard let site else { return }
        macPlan = nil
        prefs.removeObject(forKey: "macPlan." + site.id)
    }

    private func saveMacPlan() {
        guard let site, let m = macPlan else { return }
        prefs.set(["w": m.widthPx, "h": m.heightPx, "m": m.metresPerPx, "f": m.file] as [String: Any], forKey: "macPlan." + site.id)
    }

    private func loadMacPlan(_ id: String) -> FloorImage? {
        guard let d = prefs.dictionary(forKey: "macPlan." + id), let w = d["w"] as? Int, let h = d["h"] as? Int,
              let m = d["m"] as? Double else { return nil }
        return FloorImage(file: d["f"] as? String ?? "", widthPx: w, heightPx: h, metresPerPx: m)
    }

    /**
     * A click on the map at site point [p]. [pickRadiusM] is how far (in metres, at this zoom)
     * a click may be from a spot to pick it.
     */
    func mapClick(_ p: Vec2, pickRadiusM: Double) {
        guard let site else { return }
        if scaleStep > 0, let place = planPlacement {
            let origin = Vec2(site.grid.originX, site.grid.originY)
            let px = place.toPx(p, origin: origin)
            if scaleStep == 3 { return }
            if scaleStep == 1 { scaleA = px; scaleStep = 2; return }
            if let a = scaleA { scalePx = a.dist(px); scaleStep = 3; askLength = scalePx >= 1 }
            if scalePx < 1 { scaleStep = 1; scaleA = nil }
            return
        }
        if let m = site.markers.min(by: { $0.pos.dist(p) < $1.pos.dist(p) }), m.pos.dist(p) <= pickRadiusM {
            selectedMarker = m.id
        } else {
            selectedMarker = ""
        }
    }

    /** A short message at the top of the map that goes away by itself. */
    func flash(_ text: String) {
        note = text
        noteTask?.cancel()
        noteTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: 6_000_000_000)
            if !Task.isCancelled { self?.note = nil }
        }
    }

    // MARK: Signal correction

    private func loadCorrection(for s: Site) {
        loading = true
        offsetDb = prefs.double(forKey: "offset." + s.id)
        calibratedAt = prefs.string(forKey: "calibratedAt." + s.id) ?? ""
        mode = MatchMode(rawValue: prefs.string(forKey: "mode." + s.id) ?? "") ?? (calibratedAt.isEmpty ? .auto : .offset)
        loading = false
    }

    private func saveCorrection() {
        guard let site else { return }
        prefs.set(offsetDb, forKey: "offset." + site.id)
        prefs.set(calibratedAt, forKey: "calibratedAt." + site.id)
        prefs.set(mode.rawValue, forKey: "mode." + site.id)
    }

    /** You stand at a recorded spot: measure how much weaker or stronger the Mac hears than the phone did. */
    func calibrate() {
        guard let site = usedSite, let m = site.marker(selectedMarker) else { return }
        guard let fp = site.fingerprints.first(where: { $0.markerId == m.id }) else {
            alert("No recording at \(m.name)", "Pick a spot that the phone recorded (shown with a ring on the map)."); return
        }
        guard tracker.scans.count > 0 else { alert("No scan yet", "Wait for a Wi-Fi scan, then try again."); return }
        guard let o = Matcher.offset(tracker.averaged, fp) else {
            alert("Too few access points in common", "The Mac and the phone recording share fewer than 3 access points here. Try another spot."); return
        }
        offsetDb = (o.db * 10).rounded() / 10
        calibratedAt = m.name
        loading = true; mode = .offset; loading = false
        saveCorrection()
        relocate()
        flash(String(format: "Correction set: %+.1f dB from %d access points at %@.", offsetDb, o.common, m.name))
    }

    // MARK: Accuracy check

    func recordTest() {
        guard let site, let m = site.marker(selectedMarker) else { return }
        guard let fix else { alert("No position yet", "Wait until the app shows where you are."); return }
        samples.append(AccuracySample(
            time: Date(), markerId: m.id, truth: m.pos, est: fix.pos, sigmaM: fix.sigmaM, apsUsed: knownHeard,
            truthArea: site.grid.area(m.pos), estArea: site.grid.area(fix.pos), mode: mode))
        flash(String(format: "Saved: %.1f m off at %@.", fix.pos.dist(m.pos), m.name))
    }

    func saveCsv() {
        let p = NSSavePanel()
        p.allowedContentTypes = [.commaSeparatedText]
        p.nameFieldStringValue = "mac-accuracy-\(Int(Date().timeIntervalSince1970)).csv"
        guard p.runModal() == .OK, let url = p.url else { return }
        do { try Accuracy.csv(samples, device: Host.current().localizedName ?? "mac").write(to: url, atomically: true, encoding: .utf8) }
        catch { alert("Could not save", error.localizedDescription) }
    }

    private func alert(_ title: String, _ text: String) {
        let a = NSAlert()
        a.messageText = title
        a.informativeText = text
        a.runModal()
    }
}
